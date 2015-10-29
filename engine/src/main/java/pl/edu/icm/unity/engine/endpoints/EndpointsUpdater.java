/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.engine.endpoints;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import pl.edu.icm.unity.db.generic.authn.AuthenticatorInstanceDB;
import pl.edu.icm.unity.engine.transactions.SqlSessionTL;
import pl.edu.icm.unity.engine.transactions.TransactionalRunner;
import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.server.endpoint.EndpointInstance;
import pl.edu.icm.unity.server.utils.Log;
import pl.edu.icm.unity.types.authn.AuthenticationOptionDescription;


/**
 * Allows for scanning the DB endpoints state. If it is detected during the scan that runtime configuration 
 * is outdated wrt DB contents, then the reconfiguration is done: existing endpoints are undeployed,
 * and redeployed from configuration.
 * @author K. Benedyczak
 */
@Component
public class EndpointsUpdater
{
	private static final Logger log = Log.getLogger(Log.U_SERVER, EndpointsUpdater.class);
	private long lastUpdate = 0;
	private InternalEndpointManagement endpointMan;
	private EndpointDB endpointDB;
	private AuthenticatorInstanceDB authnDB;
	private TransactionalRunner tx;
	
	@Autowired
	public EndpointsUpdater(TransactionalRunner tx,
			InternalEndpointManagement endpointMan, EndpointDB endpointDB,
			AuthenticatorInstanceDB authnDB)
	{
		this.tx = tx;
		this.endpointMan = endpointMan;
		this.endpointDB = endpointDB;
		this.authnDB = authnDB;
	}

	/**
	 * Invokes refresh of endpoints, ensuring that endpoints updated at the time of the call are
	 * included in update. The method invocation can wait up to 1s. 
	 * @throws EngineException
	 */
	public void updateEndpointsManual() throws EngineException
	{
		long start = roundToS(System.currentTimeMillis());
		while (roundToS(System.currentTimeMillis()) == start)
		{
			try
			{
				Thread.sleep(100);
			} catch (InterruptedException e)
			{
				//ok
			}
		}
		updateEndpoints();
	}
	
	public void updateEndpoints() throws EngineException
	{
		synchronized(endpointMan)
		{
			updateEndpointsInt();
		}
	}

	private long roundToS(long ts)
	{
		return (ts/1000)*1000;
	}
	
	public void setLastUpdate(long lastUpdate)
	{
		this.lastUpdate = roundToS(lastUpdate);
	}

	private void updateEndpointsInt() throws EngineException
	{
		List<EndpointInstance> deployedEndpoints = endpointMan.getDeployedEndpoints();
		Set<String> endpointsInDb = new HashSet<String>();
		Map<String, EndpointInstance> endpointsDeployed = new HashMap<>();
		for (EndpointInstance endpoint: deployedEndpoints)
			endpointsDeployed.put(endpoint.getEndpointDescription().getId(), endpoint);
		log.debug("Running periodic endpoints update task. There are " + deployedEndpoints.size() + 
				" deployed endpoints.");
		
		tx.runInTransaciton(() -> {
			SqlSession sql = SqlSessionTL.get();
			long roundedUpdateTime = roundToS(System.currentTimeMillis());
			Set<String> changedAuthenticators = getChangedAuthenticators(sql, roundedUpdateTime);

			List<Map.Entry<EndpointInstance, Date>> endpoints = endpointDB.getAllWithUpdateTimestamps(sql);
			log.debug("There are " + endpoints.size() + " endpoints in DB.");
			for (Map.Entry<EndpointInstance, Date> instanceWithDate: endpoints)
			{
				EndpointInstance instance = instanceWithDate.getKey();
				String name = instance.getEndpointDescription().getId();
				endpointsInDb.add(name);
				long endpointLastChange = roundToS(instanceWithDate.getValue().getTime());
				log.trace("Update timestampses: " + roundedUpdateTime + " " + 
						lastUpdate + " " + name + ": " + endpointLastChange);
				if (endpointLastChange >= lastUpdate)
				{
					if (endpointLastChange == roundedUpdateTime)
					{
						log.debug("Skipping update of a changed endpoint to the next round,"
								+ "to prevent doubled update");
						continue;
					}
					if (endpointsDeployed.containsKey(name))
					{
						log.info("Endpoint " + name + " will be re-deployed");
						endpointMan.undeploy(instance.getEndpointDescription().getId());
					} else
						log.info("Endpoint " + name + " will be deployed");

					endpointMan.deploy(instance);
				} else if (hasChangedAuthenticator(changedAuthenticators, instance))
				{
					updateEndpointAuthenticators(name, instance, endpointsDeployed);
				}
			}
			setLastUpdate(roundedUpdateTime);

			undeployRemoved(endpointsInDb, deployedEndpoints);
		});
	}
	
	private void updateEndpointAuthenticators(String name, EndpointInstance instance,
			Map<String, EndpointInstance> endpointsDeployed) throws EngineException
	{
		log.info("Endpoint " + name + " will have its authenticators updated");
		EndpointInstance toUpdate = endpointsDeployed.get(name);
		try
		{
			toUpdate.updateAuthenticationOptions(instance.getAuthenticationOptions());
		} catch (UnsupportedOperationException e)
		{
			log.info("Endpoint " + name + " doesn't support authenticators update so will be redeployed");
			endpointMan.undeploy(instance.getEndpointDescription().getId());
			endpointMan.deploy(instance);
		}
	}
	
	/**
	 * @param sql
	 * @return Set of those authenticators that were updated after the last update of endpoints.
	 * @throws EngineException 
	 */
	private Set<String> getChangedAuthenticators(SqlSession sql, long roundedUpdateTime) throws EngineException
	{
		Set<String> changedAuthenticators = new HashSet<String>();
		List<Map.Entry<String, Date>> authnNames = authnDB.getAllNamesWithUpdateTimestamps(sql);
		for (Map.Entry<String, Date> authn: authnNames)
		{
			long authenticatorChangedAt = roundToS(authn.getValue().getTime());
			log.trace("Authenticator update timestampses: " + roundedUpdateTime + " " + 
					lastUpdate + " " + authn.getKey() + ": " + authenticatorChangedAt);
			if (authenticatorChangedAt >= lastUpdate && roundedUpdateTime != authenticatorChangedAt)
				changedAuthenticators.add(authn.getKey());
		}
		log.trace("Changed authenticators" + changedAuthenticators);
		return changedAuthenticators;
	}
	
	/**
	 * @param changedAuthenticators
	 * @param instance
	 * @return true if endpoint has any of the authenticators in the parameter set
	 */
	private boolean hasChangedAuthenticator(Set<String> changedAuthenticators, EndpointInstance instance)
	{
		List<AuthenticationOptionDescription> auths = instance.getEndpointDescription().getAuthenticatorSets();
		for (String changed: changedAuthenticators)
		{
			for (AuthenticationOptionDescription as: auths)
			{
				if (as.contains(changed))
					return true;
			}
		}
		return false;
	}
	
	private void undeployRemoved(Set<String> endpointsInDb, Collection<EndpointInstance> deployedEndpoints) 
			throws EngineException
	{
		for (EndpointInstance endpoint: deployedEndpoints)
		{
			String name = endpoint.getEndpointDescription().getId();
			if (!endpointsInDb.contains(name))
			{
				log.info("Undeploying a removed endpoint: " + name);
				endpointMan.undeploy(endpoint.getEndpointDescription().getId());
			}
		}
	}
}
