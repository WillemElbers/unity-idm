/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */

package pl.edu.icm.unity.webadmin.msgtemplate;

import java.util.ArrayList;
import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import pl.edu.icm.unity.msgtemplates.MessageTemplate;
import pl.edu.icm.unity.server.api.MessageTemplateManagement;
import pl.edu.icm.unity.server.registries.MessageTemplateConsumersRegistry;
import pl.edu.icm.unity.server.utils.UnityMessageSource;
import pl.edu.icm.unity.webadmin.utils.MessageUtils;
import pl.edu.icm.unity.webui.common.ComponentWithToolbar;
import pl.edu.icm.unity.webui.common.ConfirmDialog;
import pl.edu.icm.unity.webui.common.ErrorComponent;
import pl.edu.icm.unity.webui.common.NotificationPopup;
import pl.edu.icm.unity.webui.common.GenericElementsTable;
import pl.edu.icm.unity.webui.common.Styles;
import pl.edu.icm.unity.webui.common.GenericElementsTable.GenericItem;
import pl.edu.icm.unity.webui.common.Images;
import pl.edu.icm.unity.webui.common.SingleActionHandler;
import pl.edu.icm.unity.webui.common.Toolbar;

import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.shared.ui.Orientation;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.VerticalLayout;

/**
 * Responsible for message templates management
 * @author P. Piernik
 *
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MessageTemplatesComponent extends VerticalLayout
{
	private UnityMessageSource msg;
	private MessageTemplateManagement msgTempMan;
	private GenericElementsTable<MessageTemplate> table;
	private MessageTemplateViewer viewer;
	private com.vaadin.ui.Component main;
	private MessageTemplateConsumersRegistry consumersRegistry;
	
	@Autowired
	public MessageTemplatesComponent(UnityMessageSource msg,
			MessageTemplateManagement msgTempMan,
			MessageTemplateConsumersRegistry consumersRegistry)
	{
		this.msg = msg;
		this.msgTempMan = msgTempMan;
		this.consumersRegistry = consumersRegistry;

		addStyleName(Styles.visibleScroll.toString());
		HorizontalLayout hl = new HorizontalLayout();
		setCaption(msg.getMessage("MessageTemplatesComponent.capion"));
		table = new GenericElementsTable<MessageTemplate>(msg.getMessage("MessageTemplatesComponent.templatesTable"),
				new GenericElementsTable.NameProvider<MessageTemplate>()
				{
					@Override
					public Label toRepresentation(MessageTemplate element)
					{
						return new Label(element.getName());
					}
				});
		table.setMultiSelect(true);
		table.setWidth(90, Unit.PERCENTAGE);
		viewer = new MessageTemplateViewer(msg, consumersRegistry);
		viewer.setTemplateInput(null);
		table.addValueChangeListener(new ValueChangeListener()
		{
			
			@Override
			public void valueChange(ValueChangeEvent event)
			{
				Collection<MessageTemplate> items = getItems(table.getValue());
				if (items.size() > 1 || items.isEmpty())
				{
					viewer.setTemplateInput(null);
					return;	
				}	
				MessageTemplate item = items.iterator().next();	
				viewer.setTemplateInput(item);
			}
		});
		table.addActionHandler(new RefreshActionHandler());
		table.addActionHandler(new AddActionHandler());
		table.addActionHandler(new EditActionHandler());
		table.addActionHandler(new DeleteActionHandler());
		
		Toolbar toolbar = new Toolbar(table, Orientation.HORIZONTAL);
		toolbar.addActionHandlers(table.getActionHandlers());
		ComponentWithToolbar tableWithToolbar = new ComponentWithToolbar(table, toolbar);
		tableWithToolbar.setWidth(90, Unit.PERCENTAGE);
		
		hl.addComponents(tableWithToolbar, viewer);
		hl.setSizeFull();
		hl.setMargin(true);
		hl.setSpacing(true);
		hl.setMargin(new MarginInfo(true, false, true, false));
		main = hl;
		hl.setExpandRatio(tableWithToolbar, 0.3f);
		hl.setExpandRatio(viewer, 0.7f);
		refresh();
	}
	
	private void refresh()
	{
		try
		{
			Collection<MessageTemplate> templates = msgTempMan.listTemplates().values();
			table.setInput(templates);
			viewer.setTemplateInput(null);
			removeAllComponents();
			addComponent(main);
		} catch (Exception e)
		{
			ErrorComponent error = new ErrorComponent();
			error.setError(msg.getMessage("MessageTemplatesComponent.errorGetTemplates"), e);
			removeAllComponents();
			addComponent(error);
		}
		
	}
	
	private boolean updateTemplate(MessageTemplate updatedTemplate)
	{
		try
		{
			msgTempMan.updateTemplate(updatedTemplate);
			refresh();
			return true;
		} catch (Exception e)
		{
			NotificationPopup.showError(msg, msg.getMessage("MessageTemplatesComponent.errorUpdate"), e);
			return false;
		}
	}
	
	private boolean addTemplate(MessageTemplate template)
	{
		try
		{
			msgTempMan.addTemplate(template);
			refresh();
			return true;
		} catch (Exception e)
		{
			NotificationPopup.showError(msg, msg.getMessage("MessageTemplatesComponent.errorAdd"), e);
			return false;
		}
	}
	
	private boolean removeTemplate(String name)
	{
		try
		{
			msgTempMan.removeTemplate(name);
			refresh();
			return true;
		} catch (Exception e)
		{
			NotificationPopup.showError(msg, msg.getMessage("MessageTemplatesComponent.errorRemove"), e);
			return false;
		}
	}
	
	private Collection<MessageTemplate> getItems(Object target)
	{
		Collection<?> c = (Collection<?>) target;
		Collection<MessageTemplate> items = new ArrayList<MessageTemplate>();
		for (Object o: c)
		{
			GenericItem<?> i = (GenericItem<?>) o;
			items.add((MessageTemplate) i.getElement());	
		}	
		return items;
	}
	
	private class RefreshActionHandler extends SingleActionHandler
	{
		public RefreshActionHandler()
		{
			super(msg.getMessage("MessageTemplatesComponent.refreshAction"), Images.refresh.getResource());
			setNeedsTarget(false);
		}

		@Override
		public void handleAction(Object sender, final Object target)
		{
			refresh();
		}
	}
	
	private class AddActionHandler extends SingleActionHandler
	{
		public AddActionHandler()
		{
			super(msg.getMessage("MessageTemplatesComponent.addAction"), Images.add.getResource());
			setNeedsTarget(false);
		}

		@Override
		public void handleAction(Object sender, final Object target)
		{
			MessageTemplateEditor editor;			
			editor = new MessageTemplateEditor(msg, consumersRegistry, null);		
			MessageTemplateEditDialog dialog = new MessageTemplateEditDialog(msg, 
					msg.getMessage("MessageTemplatesComponent.addAction"), new MessageTemplateEditDialog.Callback()
					{
						@Override
						public boolean newTemplate(MessageTemplate template)
						{
							return addTemplate(template);
						}
					}, editor);
			dialog.show();
		}
	}
	
	private class EditActionHandler extends SingleActionHandler
	{
		public EditActionHandler()
		{
			super(msg.getMessage("MessageTemplatesComponent.editAction"), Images.edit.getResource());
		}

		@Override
		public void handleAction(Object sender, final Object target)
		{
			
			GenericItem<?> witem = (GenericItem<?>) target;
			MessageTemplate item = (MessageTemplate) witem.getElement();
			MessageTemplateEditor editor;
			
			editor = new MessageTemplateEditor(msg, consumersRegistry, item);
			
			MessageTemplateEditDialog dialog = new MessageTemplateEditDialog(msg, 
					msg.getMessage("MessageTemplatesComponent.editAction"), new MessageTemplateEditDialog.Callback()
					{
						@Override
						public boolean newTemplate(MessageTemplate template)
						{
							return updateTemplate(template);
						}
					}, editor);
			dialog.show();
		}
	}

	private class DeleteActionHandler extends SingleActionHandler
	{
		public DeleteActionHandler()
		{
			super(msg.getMessage("MessageTemplatesComponent.deleteAction"),
					Images.delete.getResource());
			setMultiTarget(true);
		}

		@Override
		public void handleAction(Object sender, Object target)
		{
			final Collection<MessageTemplate> items = getItems(target);		
			String confirmText = MessageUtils.createConfirmFromNames(msg, items);
			new ConfirmDialog(msg, msg.getMessage(
					"MessageTemplatesComponent.confirmDelete", confirmText),
					new ConfirmDialog.Callback()
					{

						@Override
						public void onConfirm()
						{
							for (MessageTemplate item : items)
							{
								removeTemplate(item.getName());
							}
						}
					}).show();
			
		}
	}
	
	
	
	
}
