/*
 * Copyright (c) 2015 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.engine.internal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import pl.edu.icm.unity.db.DBAttributes;
import pl.edu.icm.unity.db.generic.cred.CredentialDB;
import pl.edu.icm.unity.db.resolvers.IdentitiesResolver;
import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.exceptions.IllegalAttributeTypeException;
import pl.edu.icm.unity.exceptions.IllegalAttributeValueException;
import pl.edu.icm.unity.exceptions.IllegalIdentityValueException;
import pl.edu.icm.unity.exceptions.IllegalTypeException;
import pl.edu.icm.unity.exceptions.WrongArgumentException;
import pl.edu.icm.unity.server.attributes.AttributeValueChecker;
import pl.edu.icm.unity.server.authn.LocalCredentialVerificator;
import pl.edu.icm.unity.server.registries.IdentityTypesRegistry;
import pl.edu.icm.unity.server.registries.LocalCredentialsRegistry;
import pl.edu.icm.unity.server.translation.form.TranslatedRegistrationRequest;
import pl.edu.icm.unity.types.authn.CredentialDefinition;
import pl.edu.icm.unity.types.basic.Attribute;
import pl.edu.icm.unity.types.basic.AttributeType;
import pl.edu.icm.unity.types.basic.EntityParam;
import pl.edu.icm.unity.types.basic.IdentityParam;
import pl.edu.icm.unity.types.registration.AttributeRegistrationParam;
import pl.edu.icm.unity.types.registration.CredentialParamValue;
import pl.edu.icm.unity.types.registration.CredentialRegistrationParam;
import pl.edu.icm.unity.types.registration.OptionalRegistrationParam;
import pl.edu.icm.unity.types.registration.RegistrationForm;
import pl.edu.icm.unity.types.registration.RegistrationRequest;

/**
 * Helper component with methods to validate registration requests. There are methods to validate both the request 
 * being submitted (i.e. whether it is valid wrt its form) and validating the translated request before it is 
 * accepted.
 *  
 * @author K. Benedyczak
 */
@Component
public class RegistrationRequestValidator
{
	@Autowired
	private CredentialDB credentialDB;
	@Autowired
	private DBAttributes dbAttributes;
	@Autowired
	private IdentitiesResolver idResolver;
	@Autowired
	private IdentityTypesRegistry identityTypesRegistry;
	@Autowired
	private LocalCredentialsRegistry authnRegistry;
	
	public void validateSubmittedRequest(RegistrationForm form, RegistrationRequest request,
			boolean doCredentialCheckAndUpdate, SqlSession sql) throws EngineException
	{
		validateRequestAgreements(form, request);
		validateRequestedAttributes(form, request);
		validateRequestCode(form, request);
		validateRequestCredentials(form, request, doCredentialCheckAndUpdate, sql);
		validateRequestedIdentities(form, request);

		if (!form.isCollectComments() && request.getComments() != null)
			throw new WrongArgumentException("This registration "
					+ "form doesn't allow for passing comments.");

		if (form.getGroupParams() == null)
			return;
		if (request.getGroupSelections().size() != form.getGroupParams().size())
			throw new WrongArgumentException(
					"Wrong amount of group selections, should be: "
							+ form.getGroupParams().size());
	}

	public void validateTranslatedRequest(RegistrationForm form, RegistrationRequest originalRequest, 
			TranslatedRegistrationRequest request, SqlSession sql) throws EngineException
	{
		validateFinalAttributes(request, sql);
		validateFinalCredentials(originalRequest, sql);
		validateFinalIdentities(request, sql);
	}

	private void validateRequestAgreements(RegistrationForm form, RegistrationRequest request)
			throws WrongArgumentException
	{
		if (form.getAgreements() == null)
			return;
		if (form.getAgreements().size() != request.getAgreements().size())
			throw new WrongArgumentException("Number of agreements in the"
					+ " request does not match the form agreements.");
		for (int i = 0; i < form.getAgreements().size(); i++)
		{
			if (form.getAgreements().get(i).isManatory()
					&& !request.getAgreements().get(i).isSelected())
				throw new WrongArgumentException(
						"Mandatory agreement is not accepted.");
		}
	}

	private void validateFinalAttributes(TranslatedRegistrationRequest request, SqlSession sql) 
			throws WrongArgumentException, IllegalAttributeValueException, IllegalAttributeTypeException
	{
		Map<String, AttributeType> atMap = dbAttributes.getAttributeTypes(sql);
		for (Attribute<?> attr: request.getAttributes())
		{
			AttributeType at = atMap.get(attr.getName());
			if (at == null)
				throw new WrongArgumentException("Attribute of the form "
						+ attr.getName() + " does not exist anymore");
			AttributeValueChecker.validate(attr, at);
		}
	}

	private void validateFinalIdentities(TranslatedRegistrationRequest request, SqlSession sql) 
			throws WrongArgumentException, IllegalIdentityValueException, IllegalTypeException
	{
		boolean identitiesFound = false;
		for (IdentityParam idParam: request.getIdentities())
		{
			if (idParam.getTypeId() == null || idParam.getValue() == null)
				throw new WrongArgumentException("Identity " + idParam + " contains null values");
			identityTypesRegistry.getByName(idParam.getTypeId()).validate(idParam.getValue());
			identitiesFound = true;
			checkIdentityIsNotPresent(idParam, sql);
		}
		if (!identitiesFound)
			throw new WrongArgumentException("At least one identity must be defined in the "
					+ "registration request.");
	}
	
	private void validateFinalCredentials(RegistrationRequest request, SqlSession sql) throws EngineException
	{
		for (CredentialParamValue credentialParam: request.getCredentials())
			credentialDB.get(credentialParam.getCredentialId(), sql);
	}
	
	private void validateRequestedAttributes(RegistrationForm form, RegistrationRequest request) 
			throws WrongArgumentException
	{
		validateParamsBase(form.getAttributeParams(), request.getAttributes(), "attributes");
		for (int i = 0; i < request.getAttributes().size(); i++)
		{
			Attribute<?> attr = request.getAttributes().get(i);
			if (attr == null)
				continue;
			AttributeRegistrationParam regParam = form.getAttributeParams().get(i);
			if (!regParam.getAttributeType().equals(attr.getName()))
				throw new WrongArgumentException("Attribute " + attr.getName()
						+ " in group " + attr.getGroupPath()
						+ " is not allowed for this form");
			if (!regParam.getGroup().equals(attr.getGroupPath()))
				throw new WrongArgumentException("Attribute " + attr.getName()
						+ " in group " + attr.getGroupPath()
						+ " is not allowed for this form");
		}
	}

	private void validateRequestedIdentities(RegistrationForm form, RegistrationRequest request) 
			throws WrongArgumentException
	{
		List<IdentityParam> requestedIds = request.getIdentities();
		validateParamsBase(form.getIdentityParams(), requestedIds, "identities");
		boolean identitiesFound = false;
		for (int i=0; i<requestedIds.size(); i++)
		{
			IdentityParam idParam = requestedIds.get(i);
			if (idParam == null)
				continue;
			if (idParam.getTypeId() == null || idParam.getValue() == null)
				throw new WrongArgumentException("Identity nr " + i + " contains null values");
			if (!form.getIdentityParams().get(i).getIdentityType().equals(idParam.getTypeId()))
				throw new WrongArgumentException("Identity nr " + i + " must be of " 
						+ idParam.getTypeId() + " type");
			identitiesFound = true;
		}
		if (!identitiesFound)
			throw new WrongArgumentException("At least one identity must be defined in the "
					+ "registration request.");
	}

	private void checkIdentityIsNotPresent(IdentityParam idParam, SqlSession sql) throws WrongArgumentException
	{
		try
		{
			idResolver.getEntityId(new EntityParam(idParam), sql);
		} catch (Exception e)
		{
			//OK
			return;
		}
		throw new WrongArgumentException("The user with the given identity is already present.");
	}
	
	private void validateRequestCredentials(RegistrationForm form, RegistrationRequest request,
			boolean doCredentialCheckAndUpdate, SqlSession sql) throws EngineException
	{
		List<CredentialParamValue> requestedCreds = request.getCredentials();
		List<CredentialRegistrationParam> formCreds = form.getCredentialParams();
		if (formCreds == null)
			return;
		if (formCreds.size() != requestedCreds.size())
			throw new WrongArgumentException("There should be " + formCreds.size()
					+ " credential parameters");
		for (int i = 0; i < formCreds.size(); i++)
		{
			String credential = formCreds.get(i).getCredentialName();
			CredentialDefinition credDef = credentialDB.get(credential, sql);
			if (doCredentialCheckAndUpdate)
			{
				LocalCredentialVerificator credVerificator = authnRegistry
						.createLocalCredentialVerificator(credDef);
				String updatedSecrets = credVerificator.prepareCredential(
						requestedCreds.get(i).getSecrets(), "");
				requestedCreds.get(i).setSecrets(updatedSecrets);
			}
		}
	}

	private void validateRequestCode(RegistrationForm form, RegistrationRequest request)
			throws WrongArgumentException
	{
		String formCode = form.getRegistrationCode();
		String code = request.getRegistrationCode();
		if (formCode == null && code != null)
			throw new WrongArgumentException("This registration "
					+ "form doesn't allow for passing registration code.");
		if (formCode != null && code == null)
			throw new WrongArgumentException("This registration "
					+ "form require a registration code.");
		if (formCode != null && code != null && !formCode.equals(code))
			throw new WrongArgumentException("The registration code is invalid.");
	}

	private void validateParamsBase(List<? extends OptionalRegistrationParam> paramDefinitions,
			List<?> params, String info) throws WrongArgumentException
	{
		if (paramDefinitions.size() != params.size())
			throw new WrongArgumentException("There should be "
					+ paramDefinitions.size() + " " + info + " parameters");
		for (int i = 0; i < paramDefinitions.size(); i++)
			if (!paramDefinitions.get(i).isOptional() && params.get(i) == null)
				throw new WrongArgumentException("The parameter nr " + (i + 1)
						+ " of " + info + " is required");
	}
}
