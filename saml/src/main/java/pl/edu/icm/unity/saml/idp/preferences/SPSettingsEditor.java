/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.saml.idp.preferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import pl.edu.icm.unity.saml.idp.preferences.SamlPreferences.SPSettings;
import pl.edu.icm.unity.server.utils.UnityMessageSource;
import pl.edu.icm.unity.types.basic.Attribute;
import pl.edu.icm.unity.types.basic.AttributeType;
import pl.edu.icm.unity.types.basic.AttributeVisibility;
import pl.edu.icm.unity.types.basic.Identity;
import pl.edu.icm.unity.types.basic.IdentityTypeDefinition;
import pl.edu.icm.unity.webui.common.AbstractDialog;
import pl.edu.icm.unity.webui.common.FormValidationException;
import pl.edu.icm.unity.webui.common.GenericElementsTable;
import pl.edu.icm.unity.webui.common.GenericElementsTable.GenericItem;
import pl.edu.icm.unity.webui.common.Images;
import pl.edu.icm.unity.webui.common.NotificationPopup;
import pl.edu.icm.unity.webui.common.SingleActionHandler;
import pl.edu.icm.unity.webui.common.attributes.AttributeHandlerRegistry;

import com.vaadin.data.util.BeanItem;
import com.vaadin.data.util.IndexedContainer;
import com.vaadin.shared.ui.combobox.FilteringMode;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.Component;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.OptionGroup;

/**
 * Allows to edit settings for a single SAML Service Provider.
 * 
 * @author K. Benedyczak
 */
public class SPSettingsEditor extends FormLayout
{
	protected UnityMessageSource msg;
	protected Identity[] identities;
	protected Collection<AttributeType> attributeTypes;
	
	protected ComboBox sp;
	protected Label spLabel;
	protected OptionGroup decision;
	protected OptionGroup identity;
	protected GenericElementsTable<TableEntry> hidden;
	private AttributeHandlerRegistry handlerReg;
	
	public SPSettingsEditor(UnityMessageSource msg, AttributeHandlerRegistry handlerReg, Identity[] identities, 
			Collection<AttributeType> atTypes, String sp, SPSettings initial)
	{
		this.msg = msg;
		this.handlerReg = handlerReg;
		this.identities = Arrays.copyOf(identities, identities.length);
		this.attributeTypes = atTypes;
		initUI(initial, sp, null);
	}

	public SPSettingsEditor(UnityMessageSource msg, AttributeHandlerRegistry handlerReg, 
			Identity[] identities, Collection<AttributeType> atTypes,
			Set<String> allSps)
	{
		this.msg = msg;
		this.handlerReg = handlerReg;
		this.identities = Arrays.copyOf(identities, identities.length);
		this.attributeTypes = atTypes;
		initUI(null, null, allSps);
	}
	
	public SPSettings getSPSettings()
	{
		SPSettings ret = new SPSettings();
		IndexedContainer decContainer = ((IndexedContainer)decision.getContainerDataSource());
		int idx = decContainer.indexOfId(decision.getValue());
		if (idx == 0)
		{
			ret.setDefaultAccept(true);
			ret.setDoNotAsk(true);
		} else if (idx == 1)
		{
			ret.setDefaultAccept(false);
			ret.setDoNotAsk(true);			
		} else
		{
			ret.setDefaultAccept(false);
			ret.setDoNotAsk(false);
		}
		
		String identityV = (String) identity.getValue();
		if (identityV != null)
		{
			IndexedContainer idContainer = ((IndexedContainer)identity.getContainerDataSource());
			Identity id = identities[idContainer.indexOfId(identityV)];
			IdentityTypeDefinition idType = id.getType().getIdentityTypeProvider();
			if (!idType.isDynamic() && !idType.isTargeted())
				ret.setSelectedIdentity(id.getComparableValue());
		}
		
		Map<String, Attribute<?>> hidden = getHidden();
		ret.setHiddenAttribtues(hidden);
		return ret;
	}
	
	public String getSP()
	{
		return sp == null ? spLabel.getValue() : (String) sp.getValue();
	}
	
	private Map<String, Attribute<?>> getHidden()
	{
		Collection<?> itemIds = hidden.getItemIds();
		Map<String, Attribute<?>> hiddenAttrs = new HashMap<>();
		for (Object itemId: itemIds)
		{
			@SuppressWarnings("unchecked")
			TableEntry hiddenAttr = ((BeanItem<GenericItem<TableEntry>>)hidden.getItem(itemId)).getBean().getElement();
			hiddenAttrs.put(hiddenAttr.name, hiddenAttr.hiddenValues);
		}
		return hiddenAttrs;
	}
	
	private void initUI(SPSettings initial, String initialSp, Set<String> allSps)
	{
		if (initial == null)
		{
			sp = new ComboBox(msg.getMessage("SAMLPreferences.SP"));
			sp.setInputPrompt(msg.getMessage("SAMLPreferences.SPprompt"));
			sp.setDescription(msg.getMessage("SAMLPreferences.SPdesc"));
			sp.setWidth(100, Unit.PERCENTAGE);
			sp.setTextInputAllowed(true);
			sp.setFilteringMode(FilteringMode.OFF);
			sp.setNewItemsAllowed(true);
			sp.setNullSelectionAllowed(true);
			sp.setImmediate(true);
			for (String spName: allSps)
				sp.addItem(spName);
			addComponent(sp);
		} else
		{
			spLabel = new Label(initialSp);
			spLabel.setCaption(msg.getMessage("SAMLPreferences.SP"));
			addComponent(spLabel);
		}
		
		decision = new OptionGroup(msg.getMessage("SAMLPreferences.decision"));
		decision.setNullSelectionAllowed(false);
		decision.addItem(msg.getMessage("SAMLPreferences.autoAccept"));
		decision.addItem(msg.getMessage("SAMLPreferences.autoDeny"));
		decision.addItem(msg.getMessage("SAMLPreferences.noAuto"));

		identity = new OptionGroup(msg.getMessage("SAMLPreferences.identity"));
		identity.setNullSelectionAllowed(true);
		for (Identity id: identities)
			identity.addItem(id.toPrettyString());
		
		hidden = new GenericElementsTable<TableEntry>(msg.getMessage("SAMLPreferences.hidden"));
		hidden.setHeight(200, Unit.PIXELS);
		hidden.addActionHandler(new AddActionHandler());
		hidden.addActionHandler(new DeleteActionHandler());
		
		addComponents(decision, identity, hidden);
		
		if (initial != null)
			setValues(initial);
		else
			setDefaults();
	}
	
	private void setDefaults()
	{
		IndexedContainer decContainer = ((IndexedContainer)decision.getContainerDataSource());
		decision.select(decContainer.getIdByIndex(2));
		IndexedContainer idContainer = ((IndexedContainer)identity.getContainerDataSource());
		identity.select(idContainer.getIdByIndex(0));
	}
	
	private void setValues(SPSettings initial)
	{
		IndexedContainer decContainer = ((IndexedContainer)decision.getContainerDataSource());
		if (!initial.isDoNotAsk())
			decision.select(decContainer.getIdByIndex(2));
		else if (initial.isDefaultAccept())
			decision.select(decContainer.getIdByIndex(0));
		else
			decision.select(decContainer.getIdByIndex(1));
		
		String selId = initial.getSelectedIdentity();
		if (selId != null)
		{
			for (Identity i: identities)
			{
				if (i.getComparableValue().equals(selId))
				{
					identity.select(i.toPrettyString());
					break;
				}
			}
		}
			
		Map<String, Attribute<?>> hiddenAttribtues = initial.getHiddenAttribtues();
		Collection<TableEntry> converted = new ArrayList<>();
		for (Entry<String, Attribute<?>> entry : hiddenAttribtues.entrySet())
			converted.add(new TableEntry(entry.getKey(), entry.getValue()));
		hidden.setInput(converted);
	}
	
	private class DeleteActionHandler extends SingleActionHandler
	{
		public DeleteActionHandler()
		{
			super(msg.getMessage("SAMLPreferences.deleteAction"), 
					Images.delete.getResource());
		}
		
		@Override
		public void handleAction(Object sender, Object target)
		{
			hidden.removeItem(target);
		}
	}
	
	private class AddActionHandler extends SingleActionHandler
	{
		public AddActionHandler()
		{
			super(msg.getMessage("SAMLPreferences.addAction"), 
					Images.add.getResource());
			setNeedsTarget(false);
		}
		
		@Override
		public void handleAction(Object sender, Object target)
		{
			new SelectAttributeDialog(msg).show();
		}
	}

	private class SelectAttributeDialog extends AbstractDialog
	{
		private ComboBox selection;
		
		public SelectAttributeDialog(UnityMessageSource msg)
		{
			super(msg, msg.getMessage("SAMLPreferences.selectAttribute"));
		}

		@Override
		protected Component getContents() throws Exception
		{
			selection = new ComboBox(msg.getMessage("SAMLPreferences.selectAttribute"));
			selection.setNullSelectionAllowed(false);
			Set<String> alreadySelected = getHidden().keySet();
			for (AttributeType at: attributeTypes)
				if (at.getVisibility() != AttributeVisibility.local && 
						!alreadySelected.contains(at.getName()))
					selection.addItem(at.getName());
			if (selection.size() > 0)
			{
				IndexedContainer selContainer = ((IndexedContainer)selection.getContainerDataSource());
				selection.select(selContainer.getIdByIndex(0));
			} else
			{
				NotificationPopup.showNotice(msg, msg.getMessage("notice"), msg.getMessage("SAMLPreferences.allSelected"));
				throw new FormValidationException();
			}
			return selection;
		}

		@Override
		protected void onConfirm()
		{
			String result = (String) selection.getValue();
			hidden.addElement(new TableEntry(result, null));
			close();
		}
	}
	
	private class TableEntry
	{
		private String name;
		private Attribute<?> hiddenValues;
		
		public TableEntry(String name, Attribute<?> hiddenValues)
		{
			this.name = name;
			this.hiddenValues = hiddenValues;
		}



		@Override
		public String toString()
		{
			return hiddenValues == null ? name : handlerReg.getSimplifiedAttributeRepresentation(
					hiddenValues, 50);
		}
	}
}
