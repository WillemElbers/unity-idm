/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.server.registries;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import pl.edu.icm.unity.server.authn.CredentialRetrievalFactory;
import pl.edu.icm.unity.server.authn.CredentialVerificator;
import pl.edu.icm.unity.server.authn.CredentialVerificatorFactory;
import pl.edu.icm.unity.server.authn.LocalCredentialVerificatorFactory;
import pl.edu.icm.unity.server.utils.Log;
import pl.edu.icm.unity.types.authn.AuthenticatorInstance;
import pl.edu.icm.unity.types.authn.AuthenticatorTypeDescription;

/**
 * Registry of components which are used to create {@link AuthenticatorInstance}s and local credential handlers.
 * 
 * @author K. Benedyczak
 */
@Component
public class AuthenticatorsRegistry
{
	private static final Logger log = Log.getLogger(Log.U_SERVER, AuthenticatorsRegistry.class);
	
	private Map<String, CredentialRetrievalFactory> credentialRetrievalFactories;
	private Map<String, CredentialVerificatorFactory> credentialVerificatorFactories;
	
	private Map<String, Set<AuthenticatorTypeDescription>> authenticatorsByBinding;
	private Map<String, AuthenticatorTypeDescription> authenticatorsById;
	
	@Autowired
	public AuthenticatorsRegistry(List<CredentialRetrievalFactory> retrievalFactories, 
			List<CredentialVerificatorFactory> verificatorFactories)
	{
		authenticatorsByBinding = new HashMap<String, Set<AuthenticatorTypeDescription>>();
		authenticatorsById = new HashMap<String, AuthenticatorTypeDescription>();
		
		credentialRetrievalFactories = new HashMap<String, CredentialRetrievalFactory>();
		credentialVerificatorFactories = new HashMap<String, CredentialVerificatorFactory>();
		
		for (CredentialRetrievalFactory f: retrievalFactories)
			credentialRetrievalFactories.put(f.getName(), f);
		for (CredentialVerificatorFactory f: verificatorFactories)
			credentialVerificatorFactories.put(f.getName(), f);
		
		log.debug("The following authenticator types are available:");
		for (int j=0; j<verificatorFactories.size(); j++)
		{
			CredentialVerificatorFactory vf = verificatorFactories.get(j);
			CredentialVerificator verificator = vf.newInstance();
			for (int i=0; i<retrievalFactories.size(); i++)
			{
				CredentialRetrievalFactory rf = retrievalFactories.get(i);
				if (!rf.isCredentialExchangeSupported(verificator))
					continue;
				AuthenticatorTypeDescription desc = new AuthenticatorTypeDescription();
				desc.setId(vf.getName() + " with " + rf.getName());
				desc.setRetrievalMethod(rf.getName());
				desc.setRetrievalMethodDescription(rf.getDescription());
				desc.setSupportedBinding(rf.getSupportedBinding());
				desc.setVerificationMethod(vf.getName());
				desc.setVerificationMethodDescription(vf.getDescription());
				desc.setLocal(vf instanceof LocalCredentialVerificatorFactory);
				Set<AuthenticatorTypeDescription> existing = authenticatorsByBinding.get(
						rf.getSupportedBinding());
				if (existing == null)
				{
					existing = new HashSet<AuthenticatorTypeDescription>();
					authenticatorsByBinding.put(rf.getSupportedBinding(), existing);
				}
				existing.add(desc);
				log.debug(" - " + desc);
				authenticatorsById.put(desc.getId(), desc);
			}
		}
		
		authenticatorsByBinding = Collections.unmodifiableMap(authenticatorsByBinding);
	}

	public CredentialRetrievalFactory getCredentialRetrievalFactory(String id)
	{
		return credentialRetrievalFactories.get(id);
	}

	public CredentialVerificatorFactory getCredentialVerificatorFactory(String id)
	{
		return credentialVerificatorFactories.get(id);
	}

	public AuthenticatorTypeDescription getAuthenticatorsById(String id)
	{
		return authenticatorsById.get(id);
	}

	public Set<AuthenticatorTypeDescription> getAuthenticatorsByBinding(String binding)
	{
		return authenticatorsByBinding.get(binding);
	}
	
	public Set<AuthenticatorTypeDescription> getAuthenticators()
	{
		Set<AuthenticatorTypeDescription> ret = new HashSet<AuthenticatorTypeDescription>();
		for (Map.Entry<String, Set<AuthenticatorTypeDescription>> entry: authenticatorsByBinding.entrySet())
			ret.addAll(entry.getValue());
		return ret;
	}
	
	public Set<String> getAuthenticatorTypes()
	{
		return new HashSet<String>(authenticatorsById.keySet());
	}
	
}
