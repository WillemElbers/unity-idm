/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.webui.common.provider;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import pl.edu.icm.unity.Constants;
import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.exceptions.InternalException;
import pl.edu.icm.unity.server.api.PreferencesManagement;
import pl.edu.icm.unity.server.api.internal.LoginSession;
import pl.edu.icm.unity.server.authn.InvocationContext;
import pl.edu.icm.unity.server.utils.Log;
import pl.edu.icm.unity.types.JsonSerializable;
import pl.edu.icm.unity.types.basic.EntityParam;


/**
 * User's preferences for an IdP endpoints base class. This class is not UI related, can be moved to core module
 * in future. 
 * @author K. Benedyczak
 */
public abstract class IdPPreferences implements JsonSerializable
{
	private static final Logger log = Log.getLogger(Log.U_SERVER_WEB, IdPPreferences.class);
	protected final ObjectMapper mapper = Constants.MAPPER;

	@Override
	public String getSerializedConfiguration() throws InternalException
	{
		ObjectNode main = mapper.createObjectNode();
		serializeAll(main);
		try
		{
			return mapper.writeValueAsString(main);
		} catch (JsonProcessingException e)
		{
			throw new InternalException("Can't perform JSON serialization", e);
		}
	}

	protected abstract void serializeAll(ObjectNode main);
	
	@Override
	public void setSerializedConfiguration(String json) throws InternalException
	{
		if (json == null || json.equals(""))
			return;
		try
		{
			ObjectNode main = mapper.readValue(json, ObjectNode.class);
			deserializeAll(main);
		} catch (Exception e)
		{
			throw new InternalException("Can't perform JSON deserialization", e);
		}
	}

	protected abstract void deserializeAll(ObjectNode main);
	
	public static void initPreferencesGeneric(PreferencesManagement preferencesMan, JsonSerializable toInit, String id) 
	{
		LoginSession ae = InvocationContext.getCurrent().getLoginSession();
		EntityParam entity = new EntityParam(ae.getEntityId());
		initPreferencesGeneric(preferencesMan, toInit, id, entity);
	}

	public static void initPreferencesGeneric(PreferencesManagement preferencesMan, JsonSerializable toInit, 
			String id, EntityParam entity) 
	{
		try
		{
			String raw = preferencesMan.getPreference(entity, id);
			toInit.setSerializedConfiguration(raw);
		} catch (Exception e)
		{
			log.debug("It was impossible to establish preferences for " + entity + " will use defaults", e);
		}
	}
	
	public static void savePreferencesGeneric(PreferencesManagement preferencesMan, JsonSerializable preferences, String id) 
			throws EngineException
	{
		LoginSession ae = InvocationContext.getCurrent().getLoginSession();
		EntityParam entity = new EntityParam(ae.getEntityId());
		preferencesMan.setPreference(entity, id, preferences.getSerializedConfiguration());
	}
}
