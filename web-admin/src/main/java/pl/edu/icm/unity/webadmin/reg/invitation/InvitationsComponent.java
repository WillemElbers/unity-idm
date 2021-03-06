/*
 * Copyright (c) 2015, Jirav All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.webadmin.reg.invitation;

import java.util.List;
import java.util.Optional;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.server.api.AttributesManagement;
import pl.edu.icm.unity.server.api.NotificationsManagement;
import pl.edu.icm.unity.server.api.RegistrationsManagement;
import pl.edu.icm.unity.server.utils.Log;
import pl.edu.icm.unity.server.utils.UnityMessageSource;
import pl.edu.icm.unity.types.registration.RegistrationForm;
import pl.edu.icm.unity.types.registration.invite.InvitationWithCode;
import pl.edu.icm.unity.webui.common.CompositeSplitPanel;
import pl.edu.icm.unity.webui.common.Styles;
import pl.edu.icm.unity.webui.common.attributes.AttributeHandlerRegistry;
import pl.edu.icm.unity.webui.common.identities.IdentityEditorRegistry;

import com.vaadin.ui.CustomComponent;

/**
 * Management of registration invitations.
 * @author Krzysztof Benedyczak
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class InvitationsComponent extends CustomComponent
{
	private static final Logger log = Log.getLogger(Log.U_SERVER_WEB, InvitationsComponent.class);

	private UnityMessageSource msg;
	private RegistrationsManagement registrationManagement;
	private NotificationsManagement notificationsManagement;
	private AttributeHandlerRegistry attrHandlersRegistry;
	private IdentityEditorRegistry identityEditorRegistry;

	private AttributesManagement attributesManagement;
	
	@Autowired
	public InvitationsComponent(UnityMessageSource msg,
			RegistrationsManagement registrationManagement,
			NotificationsManagement notificationsManagement,
			AttributesManagement attributesManagement,
			AttributeHandlerRegistry attrHandlersRegistry,
			IdentityEditorRegistry identityEditorRegistry)
	{
		this.msg = msg;
		this.registrationManagement = registrationManagement;
		this.notificationsManagement = notificationsManagement;
		this.attributesManagement = attributesManagement;
		this.attrHandlersRegistry = attrHandlersRegistry;
		this.identityEditorRegistry = identityEditorRegistry;
		initUI();
	}

	private void initUI()
	{
		addStyleName(Styles.visibleScroll.toString());
		InvitationsTable invitationsTable = new InvitationsTable(msg, registrationManagement, 
				notificationsManagement, attributesManagement, identityEditorRegistry, 
				attrHandlersRegistry);
		InvitationViewer viewer = new InvitationViewer(msg, attrHandlersRegistry);
		
		invitationsTable.addValueChangeListener(invitation -> 
			viewer.setInput(invitation, getForm(invitation))
		);
		
		CompositeSplitPanel hl = new CompositeSplitPanel(false, true, invitationsTable, viewer, 40);
		setCompositionRoot(hl);
		setCaption(msg.getMessage("InvitationsComponent.caption"));
	}

	private RegistrationForm getForm(InvitationWithCode invitation)
	{
		if (invitation == null)
			return null;
		List<RegistrationForm> forms;
		try
		{
			forms = registrationManagement.getForms();
		} catch (EngineException e)
		{
			log.warn("Unable to list registration forms for invitations", e);
			return null;
		}
		String id = invitation.getFormId();
		Optional<RegistrationForm> found = forms.stream().filter(form -> form.getName().equals(id)).findAny();
		if (found.isPresent())
			return found.get();
		return null;
	}
}
