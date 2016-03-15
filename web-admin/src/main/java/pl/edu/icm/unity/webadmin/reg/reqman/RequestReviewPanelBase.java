/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.webadmin.reg.reqman;

import java.util.ArrayList;

import pl.edu.icm.unity.exceptions.IllegalTypeException;
import pl.edu.icm.unity.server.registries.IdentityTypesRegistry;
import pl.edu.icm.unity.server.utils.UnityMessageSource;
import pl.edu.icm.unity.types.basic.Attribute;
import pl.edu.icm.unity.types.basic.IdentityParam;
import pl.edu.icm.unity.types.registration.AgreementRegistrationParam;
import pl.edu.icm.unity.types.registration.BaseForm;
import pl.edu.icm.unity.types.registration.BaseRegistrationInput;
import pl.edu.icm.unity.types.registration.GroupRegistrationParam;
import pl.edu.icm.unity.types.registration.Selection;
import pl.edu.icm.unity.types.registration.UserRequestState;
import pl.edu.icm.unity.webui.common.ListOfElements;
import pl.edu.icm.unity.webui.common.ListOfSelectableElements;
import pl.edu.icm.unity.webui.common.ListOfSelectableElements.DisableMode;
import pl.edu.icm.unity.webui.common.Styles;
import pl.edu.icm.unity.webui.common.attributes.AttributeHandlerRegistry;
import pl.edu.icm.unity.webui.common.attributes.WebAttributeHandler.RepresentationSize;
import pl.edu.icm.unity.webui.common.identities.IdentityFormatter;
import pl.edu.icm.unity.webui.common.safehtml.HtmlSimplifiedLabel;
import pl.edu.icm.unity.webui.common.safehtml.SafePanel;

import com.vaadin.ui.Component;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.Label;
import com.vaadin.ui.Layout;
import com.vaadin.ui.Panel;

/**
 * Shows request contents and provides a possibility to edit it. Base for extending by request type specific components.
 * 
 * @author K. Benedyczak
 */
public class RequestReviewPanelBase extends CustomComponent
{
	protected UnityMessageSource msg;
	private AttributeHandlerRegistry handlersRegistry;
	private UserRequestState<?> requestState;
	private IdentityTypesRegistry idTypesRegistry;
	
	private ListOfSelectableElements attributes;
	private ListOfSelectableElements groups;
	private ListOfElements<String> identities;
	private ListOfElements<String> agreements;
	private Label comment;
	private Panel attributesPanel;
	private Panel groupsPanel;
	private Panel agreementsP;
	private Panel commentP;
	private Panel identitiesP;
	
	public RequestReviewPanelBase(UnityMessageSource msg, AttributeHandlerRegistry handlersRegistry,
			IdentityTypesRegistry idTypesRegistry)
	{
		this.msg = msg;
		this.handlersRegistry = handlersRegistry;
		this.idTypesRegistry = idTypesRegistry;
	}
	
	protected void addStandardComponents(Layout main)
	{
		identities = new ListOfElements<>(msg, new ListOfElements.LabelConverter<String>()
		{
			@Override
			public Label toLabel(String value)
			{
				return new HtmlSimplifiedLabel(value);
			}
		});
		identities.setAddSeparatorLine(false);
		identities.setMargin(true);
		identitiesP = new SafePanel(msg.getMessage("RequestReviewPanel.requestedIdentities"), identities);
		
		attributes = new ListOfSelectableElements(null,
				new Label(msg.getMessage("RequestReviewPanel.requestedAttributeIgnore")), 
				DisableMode.WHEN_SELECTED);
		attributes.addStyleName(Styles.margin.toString());
		attributesPanel = new SafePanel(msg.getMessage("RequestReviewPanel.requestedAttributes"), 
				attributes);
		
		groups = new ListOfSelectableElements(null,
				new Label(msg.getMessage("RequestReviewPanel.requestedGroupsIgnore")), 
				DisableMode.WHEN_SELECTED);
		groups.addStyleName(Styles.margin.toString());
		groupsPanel = new SafePanel(msg.getMessage("RequestReviewPanel.requestedGroups"), groups);
		
		agreements = new ListOfElements<>(msg, new ListOfElements.LabelConverter<String>()
		{
			@Override
			public Label toLabel(String value)
			{
				return new Label(value);
			}
		});
		agreements.setAddSeparatorLine(false);
		agreements.setMargin(true);
		agreementsP = new SafePanel(msg.getMessage("RequestReviewPanel.agreements"), agreements);
		
		comment = new Label();
		comment.addStyleName(Styles.margin.toString());
		commentP = new SafePanel(msg.getMessage("RequestReviewPanel.comment"), comment);
		
		main.addComponents(identitiesP, attributesPanel, groupsPanel, commentP, agreementsP);
	}
	
	protected void fillRequest(BaseRegistrationInput ret)
	{
		BaseRegistrationInput orig = requestState.getRequest();
		ret.setAgreements(orig.getAgreements());
		ret.setComments(orig.getComments());
		ret.setCredentials(orig.getCredentials());
		ret.setFormId(orig.getFormId());
		ret.setIdentities(orig.getIdentities());
		ret.setUserLocale(orig.getUserLocale());
		
		ret.setGroupSelections(new ArrayList<Selection>(orig.getGroupSelections().size()));
		for (int i=0, j=0; i<orig.getGroupSelections().size(); i++)
		{
			Selection origSelection = orig.getGroupSelections().get(i);
			if (origSelection.isSelected())
			{
				boolean ignore = groups.getSelection().size() > j && 
						groups.getSelection().get(j).getValue();
				origSelection.setSelected(!ignore);
				ret.getGroupSelections().add(origSelection);
				j++;
			} else
			{
				ret.getGroupSelections().add(new Selection(false));
			}
		}
		
		ret.setAttributes(new ArrayList<Attribute<?>>(attributes.getSelection().size()));
		for (int i=0, j=0; i<orig.getAttributes().size(); i++)
		{
			if (orig.getAttributes().get(i) == null)
				ret.getAttributes().add(null);
			else if (!attributes.getSelection().get(j++).getValue())
				ret.getAttributes().add(orig.getAttributes().get(i));
			else
				ret.getAttributes().add(null);
		}
	}
	
	protected void setInput(UserRequestState<?> requestState, BaseForm form)
	{
		this.requestState = requestState;
		BaseRegistrationInput request = requestState.getRequest();
		String comments = request.getComments();
		if (comments != null && !comments.equals(""))
		{
			commentP.setVisible(true);
			comment.setValue(comments);
		} else
			commentP.setVisible(false);
		
		identities.clearContents();
		for (IdentityParam idParam: request.getIdentities())
		{
			if (idParam == null)
				continue;
			try
			{
				identities.addEntry(IdentityFormatter.toString(msg, idParam, 
						idTypesRegistry.getByName(idParam.getTypeId())));
			} catch (IllegalTypeException e)
			{
				throw new IllegalStateException("Ups, have request in DB with unsupported id type.", e);
			}
		}
		identitiesP.setVisible(identities.getComponentCount() > 0);
		
		agreements.clearContents();
		for (int i=0; i<request.getAgreements().size(); i++)
		{
			Selection selection = request.getAgreements().get(i);
			if (form.getAgreements().size() <= i)
				break;
			AgreementRegistrationParam agreementText = form.getAgreements().get(i);
			String info = (selection.isSelected()) ? msg.getMessage("RequestReviewPanel.accepted") : 
				msg.getMessage("RequestReviewPanel.notAccepted");
			String agreementTextStr = agreementText.getText().getValue(msg);
			String aText = (agreementTextStr.length() > 100) ? 
					agreementTextStr.substring(0, 100) + "[...]" : agreementTextStr;
			agreements.addEntry(info + ": " +  aText);
		}
		agreementsP.setVisible(agreements.getComponentCount() > 0);
		
		attributes.clearEntries();
		for (Attribute<?> ap: request.getAttributes())
		{
			if (ap == null)
				continue;
			Component rep = handlersRegistry.getRepresentation(ap, RepresentationSize.MEDIUM);
			attributes.addEntry(rep, false);
		}
		attributesPanel.setVisible(!attributes.isEmpty());

		groups.clearEntries();
		for (int i=0; i<request.getGroupSelections().size(); i++)
		{
			Selection selection = request.getGroupSelections().get(i);
			if (!selection.isSelected())
				continue;
			if (form.getGroupParams().size() <= i)
				break;
			GroupRegistrationParam groupParam = form.getGroupParams().get(i);
			String groupEntry = selection.getExternalIdp() == null ? groupParam.getGroupPath() :
				"[from: " + selection.getExternalIdp() + "] " + groupParam.getGroupPath();
			groups.addEntry(new Label(groupEntry), false);
		}
		groupsPanel.setVisible(!groups.isEmpty());
	}
}
