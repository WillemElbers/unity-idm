/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.saml.idp.preferences;

import java.util.Collection;

import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.event.Action.Handler;
import com.vaadin.server.Sizeable.Unit;
import com.vaadin.ui.Component;
import com.vaadin.ui.HorizontalLayout;

import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.saml.idp.preferences.SamlPreferences.SPSettings;
import pl.edu.icm.unity.server.api.AttributesManagement;
import pl.edu.icm.unity.server.api.IdentitiesManagement;
import pl.edu.icm.unity.server.api.internal.LoginSession;
import pl.edu.icm.unity.server.authn.InvocationContext;
import pl.edu.icm.unity.server.utils.UnityMessageSource;
import pl.edu.icm.unity.types.basic.AttributeType;
import pl.edu.icm.unity.types.basic.EntityParam;
import pl.edu.icm.unity.types.basic.Identity;
import pl.edu.icm.unity.webui.common.NotificationPopup;
import pl.edu.icm.unity.webui.common.FormValidationException;
import pl.edu.icm.unity.webui.common.GenericElementsTable;
import pl.edu.icm.unity.webui.common.Images;
import pl.edu.icm.unity.webui.common.SingleActionHandler;
import pl.edu.icm.unity.webui.common.GenericElementsTable.GenericItem;
import pl.edu.icm.unity.webui.common.attributes.AttributeHandlerRegistry;
import pl.edu.icm.unity.webui.common.preferences.PreferencesEditor;

/**
 * Viewing and editing UI of {@link SamlPreferences}.
 * @author K. Benedyczak
 */
public class SamlPreferencesEditor implements PreferencesEditor
{
	protected UnityMessageSource msg;
	protected SamlPreferences preferences;
	protected IdentitiesManagement idsMan;
	protected AttributesManagement atsMan;
	protected ModificationListener listener;
	
	protected HorizontalLayout main;
	protected GenericElementsTable<String> table;
	protected SamlSPSettingsViewer viewer;
	
	protected Identity[] identities;
	protected Collection<AttributeType> atTypes;
	protected AttributeHandlerRegistry attributeHandlerRegistry;

	public SamlPreferencesEditor(UnityMessageSource msg, SamlPreferences preferences, IdentitiesManagement idsMan,
			AttributesManagement atsMan, AttributeHandlerRegistry attributeHandlerRegistry)
	{
		this.msg = msg;
		this.preferences = preferences;
		this.idsMan = idsMan;
		this.atsMan = atsMan;
		this.attributeHandlerRegistry = attributeHandlerRegistry;
		
		init();
	}

	protected void initStateData() throws EngineException
	{
		LoginSession auth = InvocationContext.getCurrent().getLoginSession();
		EntityParam entParam = new EntityParam(auth.getEntityId());
		identities = idsMan.getEntity(entParam).getIdentities();
		atTypes = atsMan.getAttributeTypes();
	}
	
	private void init()
	{
		main = new HorizontalLayout();
		
		table = new GenericElementsTable<String>(msg.getMessage("SAMLPreferences.spSettings"), 
				new GenericElementsTable.NameProvider<String>()
				{
					public Object toRepresentation(String element)
					{
						return element.equals("") ? 
								msg.getMessage("SAMLPreferences.defaultSP") : element;
					}
				});
		table.setWidth(90, Unit.PERCENTAGE);
		table.setHeight(300, Unit.PIXELS);
		main.addComponent(table);
		viewer = configureViewer();
		main.addComponent(viewer);
		for (Handler h: getHandlers())
			table.addActionHandler(h);
		main.setSizeFull();
		main.setSpacing(true);

		table.setInput(preferences.getKeys());
		viewer.setInput(null);
	}
	
	protected Handler[] getHandlers()
	{
		return new Handler[] {new AddActionHandler(),
					new EditActionHandler(),
					new DeleteActionHandler()};
	}
	
	protected SamlSPSettingsViewer configureViewer()
	{
		final SamlSPSettingsViewer viewer = new SamlSPSettingsViewer(msg, attributeHandlerRegistry);
		table.addValueChangeListener(new ValueChangeListener()
		{
			@Override
			public void valueChange(ValueChangeEvent event)
			{
				@SuppressWarnings("unchecked")
				GenericItem<String> item = (GenericItem<String>)table.getValue();
				if (item != null)
				{
					SPSettings sp = preferences.getSPSettings(item.getElement());
					viewer.setInput(sp);
				} else
					viewer.setInput(null);
			}
		});
		return viewer;
	}
	
	@Override
	public Component getComponent()
	{
		return main;
	}

	@Override
	public String getValue() throws FormValidationException
	{
		return preferences.getSerializedConfiguration();
	}
	
	protected class AddActionHandler extends SingleActionHandler
	{
		public AddActionHandler()
		{
			super(msg.getMessage("SAMLPreferences.addAction"), Images.add.getResource());
			setNeedsTarget(false);
		}

		@Override
		public void handleAction(Object sender, final Object target)
		{
			try
			{
				initStateData();
			} catch (EngineException e)
			{
				NotificationPopup.showError(msg, msg.getMessage("SAMLPreferences.errorLoadindSystemInfo"), e);
				return;
			}
			SPSettingsEditor editor = new SPSettingsEditor(msg, attributeHandlerRegistry, identities, 
					atTypes, preferences.getKeys());
			new SPSettingsDialog(msg, editor, new SPSettingsDialog.Callback()
			{
				@Override
				public void updatedSP(SPSettings spSettings, String sp)
				{
					preferences.setSPSettings(sp, spSettings);
					table.setInput(preferences.getKeys());
					listener.preferencesModified();
				}
			}).show();
		}
	}
	
	protected class EditActionHandler extends SingleActionHandler
	{
		public EditActionHandler()
		{
			super(msg.getMessage("SAMLPreferences.editAction"), Images.edit.getResource());
		}

		@Override
		public void handleAction(Object sender, final Object target)
		{
			try
			{
				initStateData();
			} catch (EngineException e)
			{
				NotificationPopup.showError(msg, msg.getMessage("SAMLPreferences.errorLoadindSystemInfo"), e);
				return;
			}
			@SuppressWarnings("unchecked")
			GenericItem<String> item = (GenericItem<String>)target;
			SPSettingsEditor editor = new SPSettingsEditor(msg, attributeHandlerRegistry, identities, 
					atTypes, item.getElement(), preferences.getSPSettings(item.getElement()));
			new SPSettingsDialog(msg, editor, new SPSettingsDialog.Callback()
			{
				@Override
				public void updatedSP(SPSettings spSettings, String sp)
				{
					preferences.setSPSettings(sp, spSettings);
					table.setInput(preferences.getKeys());
					listener.preferencesModified();
				}
			}).show();
		}
	}
	
	protected class DeleteActionHandler extends SingleActionHandler
	{
		public DeleteActionHandler()
		{
			super(msg.getMessage("SAMLPreferences.deleteAction"), 
					Images.delete.getResource());
		}
		
		@Override
		public void handleAction(Object sender, Object target)
		{
			
			GenericItem<?> item = (GenericItem<?>)target;
			preferences.removeSPSettings((String)item.getElement());
			table.setInput(preferences.getKeys());
			listener.preferencesModified();
		}
	}

	@Override
	public void setChangeListener(ModificationListener listener)
	{
		this.listener = listener;
	}
}
