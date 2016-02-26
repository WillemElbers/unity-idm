/**
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.engine;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import pl.edu.icm.unity.db.generic.credreq.CredentialRequirementDB;
import pl.edu.icm.unity.db.generic.reg.InvitationWithCodeDB;
import pl.edu.icm.unity.db.generic.reg.RegistrationFormDB;
import pl.edu.icm.unity.db.generic.reg.RegistrationRequestDB;
import pl.edu.icm.unity.engine.authz.AuthorizationManager;
import pl.edu.icm.unity.engine.authz.AuthzCapability;
import pl.edu.icm.unity.engine.events.InvocationEventProducer;
import pl.edu.icm.unity.engine.internal.BaseFormValidator;
import pl.edu.icm.unity.engine.internal.InternalRegistrationManagment;
import pl.edu.icm.unity.engine.internal.RegistrationRequestValidator;
import pl.edu.icm.unity.engine.registration.RegistrationConfirmationSupport;
import pl.edu.icm.unity.engine.transactions.SqlSessionTL;
import pl.edu.icm.unity.engine.transactions.Transactional;
import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.exceptions.SchemaConsistencyException;
import pl.edu.icm.unity.exceptions.WrongArgumentException;
import pl.edu.icm.unity.notifications.NotificationProducer;
import pl.edu.icm.unity.server.api.RegistrationsManagement;
import pl.edu.icm.unity.server.api.internal.LoginSession;
import pl.edu.icm.unity.server.api.internal.SharedEndpointManagement;
import pl.edu.icm.unity.server.api.internal.TransactionalRunner;
import pl.edu.icm.unity.server.api.registration.AcceptRegistrationTemplateDef;
import pl.edu.icm.unity.server.api.registration.BaseRegistrationTemplateDef;
import pl.edu.icm.unity.server.api.registration.InvitationTemplateDef;
import pl.edu.icm.unity.server.api.registration.PublicRegistrationURLSupport;
import pl.edu.icm.unity.server.api.registration.RejectRegistrationTemplateDef;
import pl.edu.icm.unity.server.api.registration.SubmitRegistrationTemplateDef;
import pl.edu.icm.unity.server.api.registration.UpdateRegistrationTemplateDef;
import pl.edu.icm.unity.server.authn.InvocationContext;
import pl.edu.icm.unity.server.registries.RegistrationActionsRegistry;
import pl.edu.icm.unity.server.translation.form.RegistrationTranslationProfile;
import pl.edu.icm.unity.server.utils.UnityMessageSource;
import pl.edu.icm.unity.types.registration.AdminComment;
import pl.edu.icm.unity.types.registration.RegistrationContext;
import pl.edu.icm.unity.types.registration.RegistrationForm;
import pl.edu.icm.unity.types.registration.RegistrationFormNotifications;
import pl.edu.icm.unity.types.registration.RegistrationRequest;
import pl.edu.icm.unity.types.registration.RegistrationRequestAction;
import pl.edu.icm.unity.types.registration.RegistrationRequestState;
import pl.edu.icm.unity.types.registration.RegistrationRequestStatus;
import pl.edu.icm.unity.types.registration.invite.InvitationParam;
import pl.edu.icm.unity.types.registration.invite.InvitationWithCode;

/**
 * Implementation of registrations subsystem.
 * 
 * @author K. Benedyczak
 */
@Component
@InvocationEventProducer
public class RegistrationsManagementImpl implements RegistrationsManagement
{
	private RegistrationFormDB formsDB;
	private RegistrationRequestDB requestDB;
	private CredentialRequirementDB credentialReqDB;
	private RegistrationConfirmationSupport confirmationsSupport;
	private AuthorizationManager authz;
	private NotificationProducer notificationProducer;

	private InternalRegistrationManagment internalManagment;
	private UnityMessageSource msg;
	private TransactionalRunner tx;
	private RegistrationRequestValidator registrationRequestValidator;
	private InvitationWithCodeDB invitationDB;
	private SharedEndpointManagement sharedEndpointMan;
	private RegistrationActionsRegistry registrationTranslationActionsRegistry;
	private BaseFormValidator baseValidator;

	@Autowired
	public RegistrationsManagementImpl(RegistrationFormDB formsDB,
			RegistrationRequestDB requestDB, 
			CredentialRequirementDB credentialReqDB, 
			RegistrationConfirmationSupport confirmationsSupport, AuthorizationManager authz,
			NotificationProducer notificationProducer,
			InternalRegistrationManagment internalManagment, UnityMessageSource msg,
			TransactionalRunner tx,
			RegistrationRequestValidator registrationRequestValidator,
			InvitationWithCodeDB invitationDB,
			SharedEndpointManagement endpointMan,
			RegistrationActionsRegistry registrationTranslationActionsRegistry,
			BaseFormValidator baseValidator)
	{
		this.formsDB = formsDB;
		this.requestDB = requestDB;
		this.credentialReqDB = credentialReqDB;
		this.confirmationsSupport = confirmationsSupport;
		this.authz = authz;
		this.notificationProducer = notificationProducer;
		this.internalManagment = internalManagment;
		this.msg = msg;
		this.tx = tx;
		this.registrationRequestValidator = registrationRequestValidator;
		this.invitationDB = invitationDB;
		this.sharedEndpointMan = endpointMan;
		this.registrationTranslationActionsRegistry = registrationTranslationActionsRegistry;
		this.baseValidator = baseValidator;
	}

	@Override
	@Transactional
	public void addForm(RegistrationForm form) throws EngineException
	{
		authz.checkAuthorization(AuthzCapability.maintenance);
		SqlSession sql = SqlSessionTL.get();
		validateFormContents(form, sql);
		formsDB.insert(form.getName(), form, sql);
	}

	@Override
	@Transactional
	public void removeForm(String formId, boolean dropRequests) throws EngineException
	{
		authz.checkAuthorization(AuthzCapability.maintenance);
		SqlSession sql = SqlSessionTL.get();
		List<RegistrationRequestState> requests = requestDB.getAll(sql);
		if (dropRequests)
		{
			for (RegistrationRequestState req: requests)
				if (formId.equals(req.getRequest().getFormId()))
					requestDB.remove(req.getRequestId(), sql);
		} else
		{
			for (RegistrationRequestState req: requests)
				if (formId.equals(req.getRequest().getFormId()))
					throw new SchemaConsistencyException("There are requests bound " +
							"to this form, and it was not chosen to drop them.");
		}
		formsDB.remove(formId, sql);
	}

	@Override
	@Transactional
	public void updateForm(RegistrationForm updatedForm, boolean ignoreRequests)
			throws EngineException
	{
		authz.checkAuthorization(AuthzCapability.maintenance);
		SqlSession sql = SqlSessionTL.get();
		validateFormContents(updatedForm, sql);
		String formId = updatedForm.getName();
		if (!ignoreRequests)
		{
			List<RegistrationRequestState> requests = requestDB.getAll(sql);
			for (RegistrationRequestState req: requests)
				if (formId.equals(req.getRequest().getFormId()) && 
						req.getStatus() == RegistrationRequestStatus.pending)
					throw new SchemaConsistencyException("There are requests bound to " +
							"this form, and it was not chosen to ignore them.");
		}
		formsDB.update(formId, updatedForm, sql);
	}

	@Override
	@Transactional(noTransaction=true)
	public List<RegistrationForm> getForms() throws EngineException
	{
		authz.checkAuthorization(AuthzCapability.readInfo);
		SqlSession sql = SqlSessionTL.get();
		return internalManagment.getForms(sql);
	}

	@Override
	public String submitRegistrationRequest(RegistrationRequest request, final RegistrationContext context) 
			throws EngineException
	{
		RegistrationRequestState requestFull = new RegistrationRequestState();
		requestFull.setStatus(RegistrationRequestStatus.pending);
		requestFull.setRequest(request);
		requestFull.setRequestId(UUID.randomUUID().toString());
		requestFull.setTimestamp(new Date());
		requestFull.setRegistrationContext(context);
		
		RegistrationForm form = recordRequestAndReturnForm(requestFull); 
		
		sendNotification(form, requestFull);
		
		Long entityId = tryAutoProcess(form, requestFull, context);
		
		confirmationsSupport.sendAttributeConfirmationRequest(requestFull, entityId, form);
		confirmationsSupport.sendIdentityConfirmationRequest(requestFull, entityId, form);	
		
		return requestFull.getRequestId();
	}

	private RegistrationForm recordRequestAndReturnForm(RegistrationRequestState requestFull) throws EngineException
	{
		return tx.runInTransactionRet(() -> {
			RegistrationRequest request = requestFull.getRequest();
			SqlSession sql = SqlSessionTL.get();
			RegistrationForm form = formsDB.get(request.getFormId(), sql);
			registrationRequestValidator.validateSubmittedRequest(form, request, true, sql);
			requestDB.insert(requestFull.getRequestId(), requestFull, sql);
			return form;
		});
	}

	private void sendNotification(RegistrationForm form, RegistrationRequestState requestFull) throws EngineException
	{
		RegistrationFormNotifications notificationsCfg = form.getNotificationsConfiguration();
		if (notificationsCfg.getChannel() != null && notificationsCfg.getSubmittedTemplate() != null
				&& notificationsCfg.getAdminsNotificationGroup() != null)
		{
			Map<String, String> params = internalManagment.getBaseNotificationParams(
					form.getName(), requestFull.getRequestId()); 
			notificationProducer.sendNotificationToGroup(
					notificationsCfg.getAdminsNotificationGroup(), 
					notificationsCfg.getChannel(), 
					notificationsCfg.getSubmittedTemplate(),
					params,
					msg.getDefaultLocaleCode());
		}
	}
	
	private Long tryAutoProcess(RegistrationForm form, RegistrationRequestState requestFull, 
			RegistrationContext context) throws EngineException
	{
		if (!context.tryAutoAccept)
			return null;
		return tx.runInTransactionRet(() -> {
			return internalManagment.autoProcess(form, requestFull, 
						"Automatic processing of the request  " + 
						requestFull.getRequestId() + " invoked, action: {0}", 
						SqlSessionTL.get());
		});
	}
	
	@Override
	@Transactional
	public List<RegistrationRequestState> getRegistrationRequests() throws EngineException
	{
		authz.checkAuthorization(AuthzCapability.read);
		return requestDB.getAll(SqlSessionTL.get());
	}

	@Override
	@Transactional
	public void processRegistrationRequest(String id, RegistrationRequest finalRequest,
			RegistrationRequestAction action, String publicCommentStr,
			String internalCommentStr) throws EngineException
	{
		authz.checkAuthorization(AuthzCapability.credentialModify, AuthzCapability.attributeModify,
				AuthzCapability.identityModify, AuthzCapability.groupModify);
		SqlSession sql = SqlSessionTL.get();
		RegistrationRequestState currentRequest = requestDB.get(id, sql);
		if (finalRequest != null)
		{
			finalRequest.setCredentials(currentRequest.getRequest().getCredentials());
			currentRequest.setRequest(finalRequest);
		}
		InvocationContext authnCtx = InvocationContext.getCurrent();
		LoginSession client = authnCtx.getLoginSession();

		if (client == null)
		{
			client = new LoginSession();
			client.setEntityId(0);
		}

		AdminComment publicComment = null;
		AdminComment internalComment = null;
		if (publicCommentStr != null)
		{
			publicComment = new AdminComment(publicCommentStr, client.getEntityId(), true);
			currentRequest.getAdminComments().add(publicComment);
		}
		if (internalCommentStr != null)
		{
			internalComment = new AdminComment(internalCommentStr, client.getEntityId(), false);
			currentRequest.getAdminComments().add(internalComment);
		}

		if (currentRequest.getStatus() != RegistrationRequestStatus.pending && 
				(action == RegistrationRequestAction.accept || 
				action == RegistrationRequestAction.reject))
			throw new WrongArgumentException("The request was already processed. " +
					"It is only possible to drop it or to modify its comments.");
		if (currentRequest.getStatus() != RegistrationRequestStatus.pending && 
				action == RegistrationRequestAction.update && finalRequest != null)
			throw new WrongArgumentException("The request was already processed. " +
					"It is only possible to drop it or to modify its comments.");
		RegistrationForm form = formsDB.get(currentRequest.getRequest().getFormId(), sql);

		switch (action)
		{
		case drop:
			internalManagment.dropRequest(id, sql);
			break;
		case reject:
			internalManagment.rejectRequest(form, currentRequest, publicComment, internalComment, sql);
			break;
		case update:
			updateRequest(form, currentRequest, publicComment, internalComment, sql);
			break;
		case accept:
			internalManagment.acceptRequest(form, currentRequest, publicComment, 
					internalComment, true, sql);
			break;
		}
	}

	private void updateRequest(RegistrationForm form, RegistrationRequestState currentRequest,
			AdminComment publicComment, AdminComment internalComment, SqlSession sql) 
			throws EngineException
	{
		registrationRequestValidator.validateSubmittedRequest(form, currentRequest.getRequest(), false, sql);
		requestDB.update(currentRequest.getRequestId(), currentRequest, sql);
		RegistrationFormNotifications notificationsCfg = form.getNotificationsConfiguration();
		internalManagment.sendProcessingNotification(notificationsCfg.getUpdatedTemplate(),
				currentRequest, currentRequest.getRequestId(), form.getName(), false, 
				publicComment, internalComment,	notificationsCfg, sql);
	}
	
	private void validateFormContents(RegistrationForm form, SqlSession sql) throws EngineException
	{
		baseValidator.validateBaseFormContents(form, sql);
		
		if (form.isByInvitationOnly())
		{
			if (!form.isPubliclyAvailable())
				throw new WrongArgumentException("Registration form which "
						+ "is by invitation only must be public");
			if (form.getRegistrationCode() != null)
				throw new WrongArgumentException("Registration form which "
						+ "is by invitation only must not have a static registration code");
		}
		
		if (form.getDefaultCredentialRequirement() == null)
			throw new WrongArgumentException("Credential requirement must be set for the form");
		if (credentialReqDB.get(form.getDefaultCredentialRequirement(), sql) == null)
			throw new WrongArgumentException("Credential requirement " + 
					form.getDefaultCredentialRequirement() + " does not exist");

		RegistrationFormNotifications notCfg = form.getNotificationsConfiguration();
		if (notCfg == null)
			throw new WrongArgumentException("NotificationsConfiguration must be set in the form.");
		baseValidator.checkTemplate(notCfg.getAcceptedTemplate(), AcceptRegistrationTemplateDef.NAME,
				sql, "accepted registration request");
		baseValidator.checkTemplate(notCfg.getRejectedTemplate(), RejectRegistrationTemplateDef.NAME,
				sql, "rejected registration request");
		baseValidator.checkTemplate(notCfg.getSubmittedTemplate(), SubmitRegistrationTemplateDef.NAME,
				sql, "submitted registration request");
		baseValidator.checkTemplate(notCfg.getUpdatedTemplate(), UpdateRegistrationTemplateDef.NAME,
				sql, "updated registration request");
		baseValidator.checkTemplate(notCfg.getInvitationTemplate(), InvitationTemplateDef.NAME,
				sql, "invitation");
	}
	
	@Override
	@Transactional
	public String addInvitation(InvitationParam invitation) throws EngineException
	{
		authz.checkAuthorization(AuthzCapability.maintenance);
		SqlSession sql = SqlSessionTL.get();
		RegistrationForm form = formsDB.get(invitation.getFormId(), sql);
		if (!form.isPubliclyAvailable())
			throw new WrongArgumentException("Invitations can be attached to public forms only");
		if (form.getRegistrationCode() != null)
			throw new WrongArgumentException("Invitations can not be attached to forms with a fixed registration code");
		String randomUUID = UUID.randomUUID().toString();
		InvitationWithCode withCode = new InvitationWithCode(invitation, randomUUID, null, 0);
		invitationDB.insert(randomUUID, withCode, sql);
		return randomUUID;
	}

	@Override
	@Transactional
	public void sendInvitation(String code) throws EngineException
	{
		authz.checkAuthorization(AuthzCapability.maintenance);
		String userLocale = msg.getDefaultLocaleCode();
		SqlSession sql = SqlSessionTL.get();
		
		InvitationWithCode invitation = invitationDB.get(code, sql);
		if (invitation.getContactAddress() == null || invitation.getChannelId() == null)
			throw new WrongArgumentException("The invitation has no contact address configured");
		if (invitation.getExpiration().isBefore(Instant.now()))
			throw new WrongArgumentException("The invitation is expired");
		
		RegistrationForm form = formsDB.get(invitation.getFormId(), sql);
		if (form.getNotificationsConfiguration().getInvitationTemplate() == null)
			throw new WrongArgumentException("The form of the invitation has no invitation message template configured");
		
		Map<String, String> notifyParams = new HashMap<>();
		notifyParams.put(BaseRegistrationTemplateDef.FORM_NAME, form.getDisplayedName().getValue(
				userLocale, msg.getDefaultLocaleCode()));
		notifyParams.put(InvitationTemplateDef.CODE, invitation.getRegistrationCode());
		notifyParams.put(InvitationTemplateDef.URL, 
				PublicRegistrationURLSupport.getPublicRegistrationLink(invitation.getFormId(), code, sharedEndpointMan));
		ZonedDateTime expiry = invitation.getExpiration().atZone(ZoneId.systemDefault());
		notifyParams.put(InvitationTemplateDef.EXPIRES, expiry.format(DateTimeFormatter.RFC_1123_DATE_TIME));
		
		Instant sentTime = Instant.now();
		notificationProducer.sendNotification(invitation.getContactAddress(),
				invitation.getChannelId(), form.getNotificationsConfiguration().getInvitationTemplate(),
				notifyParams, userLocale);
		
		invitation.setLastSentTime(sentTime);
		invitation.setNumberOfSends(invitation.getNumberOfSends() + 1);
		invitationDB.update(code, invitation, sql);
	}

	@Override
	@Transactional
	public void removeInvitation(String code) throws EngineException
	{
		authz.checkAuthorization(AuthzCapability.maintenance);
		invitationDB.remove(code, SqlSessionTL.get());
	}

	@Override
	@Transactional
	public List<InvitationWithCode> getInvitations() throws EngineException
	{
		authz.checkAuthorization(AuthzCapability.maintenance);
		return invitationDB.getAll(SqlSessionTL.get());
	}

	@Override
	@Transactional
	public InvitationWithCode getInvitation(String code) throws EngineException
	{
		authz.checkAuthorization(AuthzCapability.maintenance);
		return invitationDB.get(code, SqlSessionTL.get());
	}

	@Override
	public RegistrationTranslationProfile getProfileInstance(RegistrationForm form)
	{
		return new RegistrationTranslationProfile(form.getTranslationProfile().getName(), 
			form.getTranslationProfile().getRules(), registrationTranslationActionsRegistry);
	}
}
