/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.engine.internal;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.ibatis.session.SqlSession;
import org.apache.log4j.Logger;
import org.mvel2.MVEL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import pl.edu.icm.unity.confirmations.ConfirmationManager;
import pl.edu.icm.unity.confirmations.states.AttribiuteConfirmationState;
import pl.edu.icm.unity.confirmations.states.IdentityConfirmationState;
import pl.edu.icm.unity.confirmations.states.RegistrationConfirmationState;
import pl.edu.icm.unity.confirmations.states.RegistrationReqAttribiuteConfirmationState;
import pl.edu.icm.unity.confirmations.states.RegistrationReqIdentityConfirmationState;
import pl.edu.icm.unity.db.DBAttributes;
import pl.edu.icm.unity.db.DBGroups;
import pl.edu.icm.unity.db.DBIdentities;
import pl.edu.icm.unity.db.generic.cred.CredentialDB;
import pl.edu.icm.unity.db.generic.reg.RegistrationFormDB;
import pl.edu.icm.unity.db.generic.reg.RegistrationRequestDB;
import pl.edu.icm.unity.db.resolvers.IdentitiesResolver;
import pl.edu.icm.unity.engine.notifications.InternalFacilitiesManagement;
import pl.edu.icm.unity.engine.notifications.NotificationFacility;
import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.exceptions.IllegalAttributeTypeException;
import pl.edu.icm.unity.exceptions.IllegalAttributeValueException;
import pl.edu.icm.unity.exceptions.IllegalIdentityValueException;
import pl.edu.icm.unity.exceptions.IllegalTypeException;
import pl.edu.icm.unity.exceptions.WrongArgumentException;
import pl.edu.icm.unity.notifications.NotificationProducer;
import pl.edu.icm.unity.server.api.internal.Token;
import pl.edu.icm.unity.server.api.internal.TokensManagement;
import pl.edu.icm.unity.server.api.registration.BaseRegistrationTemplateDef;
import pl.edu.icm.unity.server.api.registration.RegistrationWithCommentsTemplateDef;
import pl.edu.icm.unity.server.attributes.AttributeValueChecker;
import pl.edu.icm.unity.server.authn.LocalCredentialVerificator;
import pl.edu.icm.unity.server.registries.IdentityTypesRegistry;
import pl.edu.icm.unity.server.registries.LocalCredentialsRegistry;
import pl.edu.icm.unity.server.utils.Log;
import pl.edu.icm.unity.server.utils.UnityMessageSource;
import pl.edu.icm.unity.types.authn.CredentialDefinition;
import pl.edu.icm.unity.types.basic.Attribute;
import pl.edu.icm.unity.types.basic.AttributeType;
import pl.edu.icm.unity.types.basic.EntityParam;
import pl.edu.icm.unity.types.basic.Group;
import pl.edu.icm.unity.types.basic.Identity;
import pl.edu.icm.unity.types.basic.IdentityParam;
import pl.edu.icm.unity.types.confirmation.VerifiableElement;
import pl.edu.icm.unity.types.registration.AdminComment;
import pl.edu.icm.unity.types.registration.AttributeClassAssignment;
import pl.edu.icm.unity.types.registration.AttributeRegistrationParam;
import pl.edu.icm.unity.types.registration.CredentialParamValue;
import pl.edu.icm.unity.types.registration.CredentialRegistrationParam;
import pl.edu.icm.unity.types.registration.GroupRegistrationParam;
import pl.edu.icm.unity.types.registration.OptionalRegistrationParam;
import pl.edu.icm.unity.types.registration.RegistrationForm;
import pl.edu.icm.unity.types.registration.RegistrationFormNotifications;
import pl.edu.icm.unity.types.registration.RegistrationRequest;
import pl.edu.icm.unity.types.registration.RegistrationRequestState;
import pl.edu.icm.unity.types.registration.RegistrationRequestStatus;
import pl.edu.icm.unity.types.registration.Selection;

/**
 * Implementation of the internal registration management. This is used
 * internally and not exposed by the public interfaces.
 * 
 * @author P. Piernik
 */
@Component
public class InternalRegistrationManagment
{
	private static final Logger log = Log.getLogger(Log.U_SERVER,
			InternalRegistrationManagment.class);

	public static final String AUTO_ACCEPT_COMMENT = "System";

	private RegistrationFormDB formsDB;
	private RegistrationRequestDB requestDB;
	private CredentialDB credentialDB;
	private DBAttributes dbAttributes;
	private DBIdentities dbIdentities;
	private IdentitiesResolver idResolver;
	private DBGroups dbGroups;
	private TokensManagement tokensMan;
	private UnityMessageSource msg;
	private IdentityTypesRegistry identityTypesRegistry;
	private EngineHelper engineHelper;
	private AttributesHelper attributesHelper;
	private NotificationProducer notificationProducer;
	private LocalCredentialsRegistry authnRegistry;

	private InternalFacilitiesManagement facilitiesManagement;

	@Autowired
	public InternalRegistrationManagment(RegistrationFormDB formsDB,
			RegistrationRequestDB requestDB, CredentialDB credentialDB,
			DBAttributes dbAttributes, DBIdentities dbIdentities, DBGroups dbGroups,
			IdentityTypesRegistry identityTypesRegistry, EngineHelper engineHelper,
			AttributesHelper attributesHelper,
			NotificationProducer notificationProducer,
			LocalCredentialsRegistry authnRegistry, TokensManagement tokensMan,
			UnityMessageSource msg, IdentitiesResolver idResolver, 
			InternalFacilitiesManagement facilitiesManagement)
	{
		super();
		this.formsDB = formsDB;
		this.requestDB = requestDB;
		this.credentialDB = credentialDB;
		this.dbAttributes = dbAttributes;
		this.dbIdentities = dbIdentities;
		this.dbGroups = dbGroups;
		this.identityTypesRegistry = identityTypesRegistry;
		this.engineHelper = engineHelper;
		this.attributesHelper = attributesHelper;
		this.notificationProducer = notificationProducer;
		this.authnRegistry = authnRegistry;
		this.tokensMan = tokensMan;
		this.msg = msg;
		this.idResolver = idResolver;
		this.facilitiesManagement = facilitiesManagement;
	}

	public List<RegistrationForm> getForms(SqlSession sql) throws EngineException
	{
		return formsDB.getAll(sql);
	}

	public Long acceptRequest(RegistrationForm form, RegistrationRequestState currentRequest,
			AdminComment publicComment, AdminComment internalComment,
			boolean rewriteConfirmationToken, SqlSession sql) throws EngineException
	{
		currentRequest.setStatus(RegistrationRequestStatus.accepted);

		validateRequestContents(form, currentRequest.getRequest(), false, false, sql);
		requestDB.update(currentRequest.getRequestId(), currentRequest, sql);

		RegistrationRequest req = currentRequest.getRequest();

		List<Attribute<?>> rootAttributes = new ArrayList<>(req.getAttributes().size()
				+ form.getAttributeAssignments().size());
		Map<String, List<Attribute<?>>> remainingAttributesByGroup = new HashMap<String, List<Attribute<?>>>();
		for (Attribute<?> a : form.getAttributeAssignments())
			addAttr(a, rootAttributes, remainingAttributesByGroup);
		for (Attribute<?> ap : req.getAttributes())
		{
			if (ap == null)
				continue;
			addAttr(ap, rootAttributes, remainingAttributesByGroup);
		}

		List<IdentityParam> identities = req.getIdentities();

		Identity initial = engineHelper.addEntity(identities.get(0),
				form.getCredentialRequirementAssignment(),
				form.getInitialEntityState(), false, rootAttributes, true, sql);

		for (int i = 1; i < identities.size(); i++)
		{
			IdentityParam idParam = identities.get(i);
			if (idParam == null)
				continue;
			dbIdentities.insertIdentity(idParam, initial.getEntityId(), false, sql);
		}

		Map<String, Selection> sortedGroups = new TreeMap<>();
		if (form.getGroupAssignments() != null)
		{
			for (String group : form.getGroupAssignments())
				sortedGroups.put(group, null);
		}
		if (form.getGroupParams() != null)
		{
			for (int i = 0; i < form.getGroupParams().size(); i++)
			{
				if (req.getGroupSelections().get(i).isSelected())
					sortedGroups.put(form.getGroupParams().get(i)
							.getGroupPath(), req.getGroupSelections().get(i));
			}
		}
		EntityParam entity = new EntityParam(initial.getEntityId());
		for (Map.Entry<String, Selection> entry : sortedGroups.entrySet())
		{
			List<Attribute<?>> attributes = remainingAttributesByGroup.get(entry.getKey());
			if (attributes == null)
				attributes = Collections.emptyList();
			attributesHelper.checkGroupAttributeClassesConsistency(attributes, entry.getKey(), sql);
			Selection sel = entry.getValue();
			String idp = sel == null ? null : sel.getExternalIdp();
			String profile = sel == null ? null : sel.getTranslationProfile();
			dbGroups.addMemberFromParent(entry.getKey(), entity,idp, profile, new Date(), sql);
			attributesHelper.addAttributesList(attributes, initial.getEntityId(),
					true, sql);
		}

		if (form.getAttributeClassAssignments() != null)
		{
			for (AttributeClassAssignment aca : form.getAttributeClassAssignments())
			{
				attributesHelper.setAttributeClasses(initial.getEntityId(),
						aca.getGroup(),
						Collections.singleton(aca.getAcName()), sql);
			}
		}
		if (req.getCredentials() != null)
		{
			for (CredentialParamValue c : req.getCredentials())
			{
				engineHelper.setPreviouslyPreparedEntityCredentialInternal(
						initial.getEntityId(), c.getSecrets(),
						c.getCredentialId(), sql);
			}
		}
		RegistrationFormNotifications notificationsCfg = form
				.getNotificationsConfiguration();
		sendProcessingNotification(notificationsCfg.getAcceptedTemplate(), currentRequest,
				currentRequest.getRequestId(), form.getName(), true, publicComment,
				internalComment, notificationsCfg, sql);
		if (rewriteConfirmationToken)
			rewriteRequestTokenInternal(currentRequest, initial.getEntityId(), sql);

		return initial.getEntityId();
	}

	private void addAttr(Attribute<?> a, List<Attribute<?>> rootAttributes,
			Map<String, List<Attribute<?>>> remainingAttributesByGroup)
	{
		String path = a.getGroupPath();
		if (path.equals("/"))
			rootAttributes.add(a);
		else
		{
			List<Attribute<?>> attrs = remainingAttributesByGroup.get(path);
			if (attrs == null)
			{
				attrs = new ArrayList<>();
				remainingAttributesByGroup.put(path, attrs);
			}
			attrs.add(a);
		}
	}

	public RegistrationRequestState getRequest(String requestId, SqlSession sql) throws EngineException
	{
		return requestDB.get(requestId, sql);
	}

	public void validateRequestContents(RegistrationForm form, RegistrationRequest request,
			boolean doCredentialCheckAndUpdate, boolean doCheckMandatoryAttr, SqlSession sql) throws EngineException
	{
		validateRequestAgreements(form, request);
		validateRequestAttributes(form, request, doCheckMandatoryAttr, sql);
		validateRequestCode(form, request);
		validateRequestCredentials(form, request, doCredentialCheckAndUpdate, sql);
		validateRequestIdentities(form, request, sql);

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

	private void validateRequestAttributes(RegistrationForm form, RegistrationRequest request,
			boolean doCheckMandatoryAttr, SqlSession sql) throws WrongArgumentException,
			IllegalAttributeValueException, IllegalAttributeTypeException
	{
		validateParamsBase(form.getAttributeParams(), request.getAttributes(), doCheckMandatoryAttr, "attributes");
		Map<String, AttributeType> atMap = dbAttributes.getAttributeTypes(sql);
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
			AttributeType at = atMap.get(attr.getName());
			if (at == null)
				throw new WrongArgumentException("Attribute of the form "
						+ attr.getName() + " does not exist anymore");
			AttributeValueChecker.validate(attr, at);
		}
	}

	private void validateRequestIdentities(RegistrationForm form, RegistrationRequest request, SqlSession sql) 
			throws WrongArgumentException, IllegalIdentityValueException, IllegalTypeException
	{
		List<IdentityParam> requestedIds = request.getIdentities();
		validateParamsBase(form.getIdentityParams(), requestedIds, true, "identities");
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
			identityTypesRegistry.getByName(idParam.getTypeId()).validate(idParam.getValue());
			identitiesFound = true;
			checkIdentityIsNotPresent(idParam, sql);
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
			List<?> params, boolean doCheckRequiredParam, String info) throws WrongArgumentException
	{
		if (paramDefinitions.size() != params.size())
			throw new WrongArgumentException("There should be "
					+ paramDefinitions.size() + " " + info + " parameters");
		if (!doCheckRequiredParam)
			return;
		for (int i = 0; i < paramDefinitions.size(); i++)
			if (!paramDefinitions.get(i).isOptional() && params.get(i) == null)
				throw new WrongArgumentException("The parameter nr " + (i + 1)
						+ " of " + info + " is required");
	}

	public Map<String, String> getBaseNotificationParams(String formId, String requestId)
	{
		Map<String, String> ret = new HashMap<>();
		ret.put(BaseRegistrationTemplateDef.FORM_NAME, formId);
		ret.put(BaseRegistrationTemplateDef.REQUEST_ID, requestId);
		return ret;
	}

	/**
	 * Creates and sends notifications to the requester and admins in effect
	 * of request processing.
	 * 
	 * @param sendToRequester
	 *                if true then the notification is sent to requester if
	 *                only we have its address. If false, then notification
	 *                is sent to requester only if we have its address and
	 *                if a public comment was given.
	 * @throws EngineException
	 */
	public void sendProcessingNotification(String templateId,
			RegistrationRequestState currentRequest, String requestId, String formId,
			boolean sendToRequester, AdminComment publicComment,
			AdminComment internalComment,
			RegistrationFormNotifications notificationsCfg, SqlSession sql)
			throws EngineException
	{
		if (notificationsCfg.getChannel() == null || templateId == null)
			return;
		Map<String, String> notifyParams = getBaseNotificationParams(formId, requestId);
		notifyParams.put(RegistrationWithCommentsTemplateDef.PUBLIC_COMMENT,
				publicComment == null ? "" : publicComment.getContents());
		notifyParams.put(RegistrationWithCommentsTemplateDef.INTERNAL_COMMENT, "");
		String requesterAddress = getRequesterAddress(currentRequest, notificationsCfg, sql);
		if (requesterAddress != null)
		{
			if (sendToRequester || publicComment != null)
			{
				String userLocale = currentRequest.getRequest().getUserLocale();
				notificationProducer.sendNotification(requesterAddress,
						notificationsCfg.getChannel(), templateId,
						notifyParams, userLocale);
			}
		}

		if (notificationsCfg.getAdminsNotificationGroup() != null)
		{
			notifyParams.put(
					RegistrationWithCommentsTemplateDef.INTERNAL_COMMENT,
					internalComment == null ? "" : internalComment
							.getContents());
			notificationProducer.sendNotificationToGroup(
					notificationsCfg.getAdminsNotificationGroup(),
					notificationsCfg.getChannel(), templateId, notifyParams,
					msg.getDefaultLocaleCode());
		}
	}

	private String getRequesterAddress(RegistrationRequestState currentRequest,
			RegistrationFormNotifications notificationsCfg, SqlSession sql)
			throws EngineException
	{
		NotificationFacility notificationFacility = facilitiesManagement.getNotificationFacilityForChannel(
				notificationsCfg.getChannel(), sql);
		return notificationFacility.getAddressForRegistrationRequest(currentRequest, sql);
	}

	public boolean checkAutoAcceptCondition(RegistrationRequest request, SqlSession sql) throws EngineException
	{
		RegistrationForm form = null;

		for (RegistrationForm f : getForms(sql))
		{
			if (f.getName().equals(request.getFormId()))
			{
				form = f;
				break;
			}
		}

		if (form == null)
			return false;

		Boolean result = new Boolean(false);
		try
		{
			result = (Boolean) MVEL.eval(form.getAutoAcceptCondition(),
					createMvelContext(request, form));

		} catch (Exception e)
		{
			log.warn("Invalid MVEL condition", e);
		}
		return result.booleanValue();
	}

	private Map<String, Object> createMvelContext(RegistrationRequest request,
			RegistrationForm form) throws IllegalTypeException
	{
		HashMap<String, Object> ctx = new HashMap<String, Object>();

		List<IdentityParam> identities = request.getIdentities();
		Map<String, List<Object>> idsByType = new HashMap<String, List<Object>>();
		Map<String, List<Object>> idsByTypeObj = new HashMap<String, List<Object>>();
		for (IdentityParam id : identities)
		{
			if (id == null)
				continue;
			List<Object> vals = idsByType.get(id.getTypeId());
			List<Object> valsObj = idsByTypeObj.get(id.getTypeId());
			if (vals == null)
			{
				vals = new ArrayList<Object>();
				idsByType.put(id.getTypeId(), vals);
			}
			if (valsObj == null)
			{
				valsObj = new ArrayList<Object>();
				idsByTypeObj.put(id.getTypeId(), valsObj);
			}
			vals.add(id.getValue());
			valsObj.add(id);
		}
		ctx.put("idsByType", idsByType);
		ctx.put("idsByTypeObj", idsByTypeObj);

		Map<String, Object> attr = new HashMap<String, Object>();
		Map<String, List<?>> attrs = new HashMap<String, List<?>>();

		List<Attribute<?>> attributes = request.getAttributes();
		for (Attribute<?> atr : attributes)
		{
			if (atr == null)
				continue;
			if (atr.getValues() == null || atr.getName() == null)
				continue;
			Object v = atr.getValues().isEmpty() ? "" : atr.getValues().get(0);
			attr.put(atr.getName(), v);

			List<Object> ctxObj = new ArrayList<Object>();
			for (Object val : atr.getValues())
			{
				ctxObj.add(val);
			}
			attrs.put(atr.getName(), ctxObj);
		}
		ctx.put("attr", attr);
		ctx.put("attrs", attrs);

		List<Selection> groupSelections = request.getGroupSelections();
		Map<String, Group> groups = new HashMap<String, Group>();
		if (form.getGroupParams() != null)
		{
			for (int i = 0; i < form.getGroupParams().size(); i++)
			{
				if (groupSelections.get(i).isSelected())
				{
					GroupRegistrationParam gr = form.getGroupParams().get(i);
					groups.put(gr.getGroupPath(), new Group(gr.getGroupPath()));
				}
			}
		}
		ctx.put("groups", new ArrayList<String>(groups.keySet()));

		ArrayList<String> agr = new ArrayList<String>();
		for (Selection a : request.getAgreements())
		{
			agr.add(Boolean.toString(a.isSelected()));
		}
		ctx.put("agrs", agr);

		return ctx;
	}

	private void rewriteRequestTokenInternal(RegistrationRequestState finalReguest, long entityId, 
			Object transaction) throws EngineException
	{

		List<Token> tks = tokensMan.getAllTokens(ConfirmationManager.CONFIRMATION_TOKEN_TYPE, transaction);
		for (Token tk : tks)
		{
			RegistrationConfirmationState state;
			try
			{
				state = new RegistrationConfirmationState(tk.getContentsString());
			} catch (WrongArgumentException e)
			{
				//OK - not a registration token
				continue;
			}
			if (state.getRequestId().equals(finalReguest.getRequestId()))
			{
				if (state.getFacilityId().equals(
						RegistrationReqAttribiuteConfirmationState.FACILITY_ID))
				{
					rewriteSingleAttributeToken(finalReguest, tk, transaction, entityId);
				} else if (state.getFacilityId().equals(
						RegistrationReqIdentityConfirmationState.FACILITY_ID))
				{
					rewriteSingleIdentityToken(finalReguest, tk, transaction, entityId);
				}
			}
		}
	}

	private void rewriteSingleIdentityToken(RegistrationRequestState finalReguest, Token tk, 
			Object transaction, long entityId) throws EngineException
	{
		RegistrationReqIdentityConfirmationState oldState = new RegistrationReqIdentityConfirmationState(
				new String(tk.getContents(), StandardCharsets.UTF_8));
		boolean inRequest = false;
		for (IdentityParam id : finalReguest.getRequest().getIdentities())
		{
			if (id == null)
				continue;
			
			if (id.getTypeId().equals(oldState.getType())
					&& id.getValue().equals(oldState.getValue()))
			{
				inRequest = true;
				break;
			}
		}

		tokensMan.removeToken(ConfirmationManager.CONFIRMATION_TOKEN_TYPE, tk.getValue(), transaction);
		if (inRequest)
		{
			IdentityConfirmationState newstate = new IdentityConfirmationState(
					entityId, oldState.getType(), oldState.getValue(),
					oldState.getLocale(), oldState.getRedirectUrl());
			log.debug("Update confirmation token " + tk.getValue()
					+ " change facility to " + newstate.getFacilityId());
			tokensMan.addToken(ConfirmationManager.CONFIRMATION_TOKEN_TYPE, tk
					.getValue(), newstate.getSerializedConfiguration()
					.getBytes(StandardCharsets.UTF_8), tk.getCreated(), tk
					.getExpires(), transaction);
		}

	}

	private void rewriteSingleAttributeToken(RegistrationRequestState finalReguest, Token tk, 
			Object transaction, long entityId) throws EngineException
	{

		RegistrationReqAttribiuteConfirmationState oldState = new RegistrationReqAttribiuteConfirmationState(
				new String(tk.getContents(), StandardCharsets.UTF_8));
		boolean inRequest = false;
		for (Attribute<?> attribute : finalReguest.getRequest().getAttributes())
		{
			if (attribute == null || attribute.getAttributeSyntax() == null)
				continue;
			if (inRequest)
				break;
			
			if (attribute.getAttributeSyntax().isVerifiable()
					&& attribute.getName().equals(oldState.getType())
					&& attribute.getValues() != null)

			{
				for (Object o : attribute.getValues())
				{
					VerifiableElement val = (VerifiableElement) o;
					if (val.getValue().equals(oldState.getValue()))
					{
						inRequest = true;
						break;
					}
				}
			}
		}
		tokensMan.removeToken(ConfirmationManager.CONFIRMATION_TOKEN_TYPE, tk.getValue(), transaction);
		if (inRequest)
		{
			AttribiuteConfirmationState newstate = new AttribiuteConfirmationState(
					entityId, oldState.getType(), oldState.getValue(),
					oldState.getLocale(), oldState.getGroup(),
					oldState.getRedirectUrl());
			log.debug("Update confirmation token " + tk.getValue()
					+ " change facility to " + newstate.getFacilityId());
			tokensMan.addToken(ConfirmationManager.CONFIRMATION_TOKEN_TYPE, tk
					.getValue(), newstate.getSerializedConfiguration()
					.getBytes(StandardCharsets.UTF_8), tk.getCreated(), tk
					.getExpires(), transaction);
		}
	}
}
