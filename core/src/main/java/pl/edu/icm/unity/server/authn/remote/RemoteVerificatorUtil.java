/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.server.authn.remote;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import eu.unicore.util.configuration.ConfigurationException;
import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.exceptions.IllegalIdentityValueException;
import pl.edu.icm.unity.server.api.AttributesManagement;
import pl.edu.icm.unity.server.api.TranslationProfileManagement;
import pl.edu.icm.unity.server.api.internal.IdentityResolver;
import pl.edu.icm.unity.server.authn.AuthenticatedEntity;
import pl.edu.icm.unity.server.authn.AuthenticationException;
import pl.edu.icm.unity.server.authn.AuthenticationResult;
import pl.edu.icm.unity.server.authn.AuthenticationResult.Status;
import pl.edu.icm.unity.server.authn.remote.translation.TranslationProfile;
import pl.edu.icm.unity.types.basic.Attribute;
import pl.edu.icm.unity.types.basic.AttributeType;
import pl.edu.icm.unity.types.basic.AttributeVisibility;
import pl.edu.icm.unity.types.basic.IdentityTaV;

/**
 * Processes {@link RemotelyAuthenticatedInput} by applying a translation profile to it and 
 * returns {@link RemotelyAuthenticatedContext} or {@link AuthenticationResult} depending whether 
 * caller wants to have a possibility to postprocess the translation profile output or not.
 * 
 * @author K. Benedyczak
 */
public class RemoteVerificatorUtil
{
	private TranslationProfileManagement profileManagement;
	private AttributesManagement attrMan;
	private IdentityResolver identityResolver;
	
	public RemoteVerificatorUtil(IdentityResolver identityResolver,
			TranslationProfileManagement profileManagement, AttributesManagement attrMan)
	{
		this.identityResolver = identityResolver;
		this.profileManagement = profileManagement;
		this.attrMan = attrMan;
	}

	/**
	 * This method is calling {@link #processRemoteInput(RemotelyAuthenticatedInput)} and then
	 * {@link #assembleAuthenticationResult(RemotelyAuthenticatedContext)}.
	 * Usually it is the only one that is used, when {@link RemotelyAuthenticatedInput} 
	 * is obtained in an implementation specific way.
	 * 
	 * @param input
	 * @return
	 * @throws EngineException 
	 */
	public AuthenticationResult getResult(RemotelyAuthenticatedInput input, String profile) 
			throws AuthenticationException
	{
		RemotelyAuthenticatedContext context;
		try
		{
			context = processRemoteInput(input, profile);
		} catch (EngineException e)
		{
			throw new AuthenticationException("The mapping of the remtely authenticated " +
					"principal to a local representation failed", e);
		}
		return assembleAuthenticationResult(context);
	}
	
	/**
	 * Tries to resolve the primary identity from the previously created {@link RemotelyAuthenticatedContext}
	 * (usually via {@link #processRemoteInput(RemotelyAuthenticatedInput)}) and returns a 
	 * final {@link AuthenticationResult} depending on the success of this action.
	 * 
	 * @param remoteContext
	 * @return
	 * @throws EngineException 
	 */
	public AuthenticationResult assembleAuthenticationResult(RemotelyAuthenticatedContext remoteContext) 
			throws AuthenticationException
	{
		IdentityTaV remoteIdentityMapped = remoteContext.getPrimaryIdentity();
		if (remoteIdentityMapped == null)
			throw new AuthenticationException("The remotely authenticated principal " +
					"was not mapped to a local representation.");
		
		try
		{
			long resolved = identityResolver.resolveIdentity(remoteIdentityMapped.getValue(), 
					new String[] {remoteIdentityMapped.getTypeId()});
			AuthenticatedEntity authenticatedEntity = new AuthenticatedEntity(resolved, 
					remoteIdentityMapped.getValue(), false);
			return new AuthenticationResult(Status.success, remoteContext, authenticatedEntity);
		} catch (IllegalIdentityValueException ie)
		{
			AuthenticationResult r = new AuthenticationResult(Status.unknownRemotePrincipal, 
					remoteContext, null);
			throw new AuthenticationException(r, "The mapped identity is not present in the local " +
					"user store.");
		} catch (EngineException e)
		{
			throw new AuthenticationException("Problem occured when searching for the " +
					"mapped, remotely authenticated identity in the local user store", e);
		}
	}
	
	/**
	 * Invokes the configured translation profile on the remotely obtained authentication input. Then assembles  
	 * the {@link RemotelyAuthenticatedContext} from the processed input containing the information about what 
	 * from the remote data is or can be meaningful in the local DB.
	 * 
	 * @param input
	 * @return
	 * @throws EngineException
	 */
	public final RemotelyAuthenticatedContext processRemoteInput(RemotelyAuthenticatedInput input, 
			String profile)	throws EngineException
	{
		TranslationProfile translationProfile = profileManagement.listProfiles().get(profile);
		if (translationProfile == null)
			throw new ConfigurationException("The translation profile '" + profile + 
					"' configured for the authenticator does not exist");
		translationProfile.translate(input);
	
		RemotelyAuthenticatedContext ret = new RemotelyAuthenticatedContext(input.getIdpName());
		ret.addAttributes(extractAttributes(input));
		ret.addIdentities(extractIdentities(input));
		ret.addGroups(extractGroups(input));
		ret.setPrimaryIdentity(extractPrimaryIdentity(input));
		return ret;
	}
	
	private IdentityTaV extractPrimaryIdentity(RemotelyAuthenticatedInput input)
	{
		RemoteIdentity ri = input.getPrimaryIdentity();
		if (ri == null)
			return null;
		String unityIdentity = ri.getMetadata().get(RemoteInformationBase.UNITY_IDENTITY);
		String unityType = ri.getMetadata().get(RemoteInformationBase.UNITY_IDENTITY_TYPE);
		if (unityIdentity == null || unityType == null)
			return null;
		return new IdentityTaV(unityType, unityIdentity);
	}
	
	private List<IdentityTaV> extractIdentities(RemotelyAuthenticatedInput input)
	{
		Map<String, RemoteIdentity> identities = input.getIdentities();
		List<IdentityTaV> ret = new ArrayList<>();
		if (identities == null)
			return ret;

		for (RemoteIdentity ri: identities.values())
		{
			String unityIdentity = ri.getMetadata().get(RemoteInformationBase.UNITY_IDENTITY);
			String unityType = ri.getMetadata().get(RemoteInformationBase.UNITY_IDENTITY_TYPE);
			if (unityIdentity == null || unityType == null)
				continue;
			IdentityTaV toAdd = new IdentityTaV(unityType, unityIdentity);
			ret.add(toAdd);
		}
		return ret;
	}
	
	private List<String> extractGroups(RemotelyAuthenticatedInput input)
	{
		List<String> ret = new ArrayList<>();
		Map<String, RemoteGroupMembership> groups = input.getGroups();
		for (RemoteGroupMembership rg: groups.values())
		{
			String group = rg.getMetadata().get(RemoteInformationBase.UNITY_GROUP);
			if (group == null)
				continue;
			ret.add(group);
		}
		return ret;
	}
	
	private List<Attribute<?>> extractAttributes(RemotelyAuthenticatedInput input) throws EngineException
	{
		return extractAttributes(input, attrMan);
	}
	
	public static List<Attribute<?>> extractAttributes(RemotelyAuthenticatedInput input,
			AttributesManagement attrMan) throws EngineException
	{
		Map<String, RemoteAttribute> attributes = input.getAttributes();
		Map<String, AttributeType> atMap = attrMan.getAttributeTypesAsMap();
		
		List<Attribute<?>> ret = new ArrayList<>();
		for (Map.Entry<String, RemoteAttribute> ra: attributes.entrySet())
		{
			Map<String, String> metadata = ra.getValue().getMetadata();
			String scope = metadata.get(RemoteInformationBase.UNITY_GROUP);
			if (scope == null)
				continue;
			String unityName = metadata.get(RemoteInformationBase.UNITY_ATTRIBUTE);
			if (unityName == null)
				continue;
			if (!atMap.containsKey(unityName))
				continue;

			String visibilityM = metadata.get(RemoteInformationBase.UNITY_ATTRIBUTE_VISIBILITY);
			AttributeVisibility visibility = visibilityM == null ? AttributeVisibility.full : 
				AttributeVisibility.valueOf(visibilityM);
			@SuppressWarnings({ "unchecked", "rawtypes" })
			Attribute<?> mapped = new Attribute(unityName, atMap.get(unityName).getValueType(), 
					scope, visibility, ra.getValue().getValues());
			ret.add(mapped);
		}
		return ret;
	}
}