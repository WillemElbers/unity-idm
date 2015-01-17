/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.webadmin.msgtemplate;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import pl.edu.icm.unity.exceptions.IllegalTypeException;
import pl.edu.icm.unity.msgtemplates.MessageTemplate;
import pl.edu.icm.unity.msgtemplates.MessageTemplate.Message;
import pl.edu.icm.unity.msgtemplates.MessageTemplateDefinition;
import pl.edu.icm.unity.msgtemplates.MessageTemplateValidator;
import pl.edu.icm.unity.msgtemplates.MessageTemplateValidator.MandatoryVariablesException;
import pl.edu.icm.unity.msgtemplates.MessageTemplateVariable;
import pl.edu.icm.unity.msgtemplates.MessageTemplateValidator.IllegalVariablesException;
import pl.edu.icm.unity.server.registries.MessageTemplateConsumersRegistry;
import pl.edu.icm.unity.server.utils.UnityMessageSource;
import pl.edu.icm.unity.webui.common.DescriptionTextArea;
import pl.edu.icm.unity.webui.common.ErrorPopup;
import pl.edu.icm.unity.webui.common.RequiredComboBox;
import pl.edu.icm.unity.webui.common.RequiredTextArea;
import pl.edu.icm.unity.webui.common.RequiredTextField;

import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.data.Validator;
import com.vaadin.event.FieldEvents.FocusEvent;
import com.vaadin.event.FieldEvents.FocusListener;
import com.vaadin.ui.AbstractTextField;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.themes.Reindeer;

/**
 * Component to edit or add message template
 * 
 * @author P. Piernik
 * 
 */
public class MessageTemplateEditor extends FormLayout
{
	private UnityMessageSource msg;
	private MessageTemplateConsumersRegistry registry;
	private AbstractTextField name;
	private TextArea description;
	private RequiredTextField subject;
	private TextArea body;
	private ComboBox consumer;
	private Label consumerDescription;
	private boolean editMode;
	private HorizontalLayout buttons;
	private boolean subjectEdited;
	private MessageValidator bodyValidator;
	private MessageValidator subjectValidator;

	public MessageTemplateEditor(UnityMessageSource msg,
			MessageTemplateConsumersRegistry registry, MessageTemplate toEdit)
	{
		super();
		editMode = toEdit != null;
		this.msg = msg;
		this.registry = registry;
		initUI(toEdit);

	}

	private void initUI(MessageTemplate toEdit)
	{
		buttons = new HorizontalLayout();
		buttons.setSpacing(false);
		buttons.setMargin(false);
		name = new RequiredTextField(msg);
		name.setCaption(msg.getMessage("MessageTemplatesEditor.name"));
		name.setSizeFull();
		name.setValidationVisible(false);
		description = new DescriptionTextArea(
				msg.getMessage("MessageTemplatesEditor.description"));
		consumer = new RequiredComboBox(msg.getMessage("MessageTemplatesEditor.consumer"), msg);
		consumer.setImmediate(true);
		consumer.setValidationVisible(false);
		consumer.setNullSelectionAllowed(false);
		Collection<MessageTemplateDefinition> consumers = registry.getAll();
		for (MessageTemplateDefinition c : consumers)
		{
			consumer.addItem(c.getName());
		}
		consumerDescription = new Label();
		consumerDescription.setReadOnly(true);
		subject = new RequiredTextField(msg.getMessage("MessageTemplatesEditor.subject"), msg);
		subject.setImmediate(true);
		subject.setWidth(100, Unit.PERCENTAGE);
		subject.setValidationVisible(false);
		body = new RequiredTextArea(msg.getMessage("MessageTemplatesEditor.body"), msg);
		body.setImmediate(true);
		body.setRows(17);
		body.setWidth(100, Unit.PERCENTAGE);
		body.setValidationVisible(false);
		subjectValidator = new MessageValidator(null, false);
		bodyValidator = new MessageValidator(null, true);
		subject.addValidator(subjectValidator);
		body.addValidator(bodyValidator);
		subjectEdited = true;
		subject.addFocusListener(new FocusListener()
		{
			@Override
			public void focus(FocusEvent event)
			{
				subjectEdited = true;
			}
		});
		body.addFocusListener(new FocusListener()
		{
			@Override
			public void focus(FocusEvent event)
			{
				subjectEdited = false;
			}
		});
		consumer.addValueChangeListener(new ValueChangeListener()
		{
			@Override
			public void valueChange(ValueChangeEvent event)
			{
				setMessageConsumerDesc();
				updateValidator();
				body.setComponentError(null);
				subject.setComponentError(null);
			}

		});

		if (editMode)
		{
			name.setValue(toEdit.getName());
			name.setReadOnly(true);
			consumer.setValue(toEdit.getConsumer());
			description.setValue(toEdit.getDescription());
			// Using empty locale!
			Message ms = toEdit.getAllMessages().get("");
			if (ms != null)
			{
				subject.setValue(ms.getSubject());
				body.setValue(ms.getBody());
			}
			setMessageConsumerDesc();
		} else
		{
			name.setValue(msg.getMessage("MessageTemplatesEditor.defaultName"));
			if (consumer.size() > 0)
			{
				consumer.setValue(consumer.getItemIds().toArray()[0]);
			}
		}

		addComponents(name, description, consumer, consumerDescription, buttons, subject,
				body);
		setSpacing(true);
	}

	public MessageTemplate getTemplate()
	{
		if (!validate())
			return null;
		String n = name.getValue();
		String desc = description.getValue();
		String cons = getConsumer().getName();
		Map<String, Message> m = new HashMap<String, Message>();
		Message ms = new Message(subject.getValue(), body.getValue());
		// Using empty locale!
		m.put("", ms);
		return new MessageTemplate(n, desc, m, cons);
	}

	private void setMessageConsumerDesc()
	{
		MessageTemplateDefinition consumer = getConsumer();
		if (consumer == null)
			return;
		consumerDescription.setValue(msg.getMessage(consumer.getDescriptionKey()));
		updateVarButtons(consumer);
	}

	private void updateVarButtons(MessageTemplateDefinition consumer)
	{
		buttons.removeAllComponents();
		for (Map.Entry<String, MessageTemplateVariable> var : consumer.getVariables().entrySet())
		{
			final Button b = new Button();
			b.addStyleName(Reindeer.BUTTON_SMALL);
			b.setCaption(var.getKey());
			b.setDescription(msg.getMessage(var.getValue().getDescriptionKey()));
			b.addClickListener(new Button.ClickListener()
			{
				@Override
				public void buttonClick(ClickEvent event)
				{
					if (subjectEdited)
					{
						addVar(subject, b.getCaption());
					} else
					{
						addVar(body, b.getCaption());
					}
				}
			});
			buttons.addComponent(b);
		}

	}

	private MessageTemplateDefinition getConsumer()
	{

		String c = (String) consumer.getValue();
		MessageTemplateDefinition consumer = null;
		try
		{
			consumer = registry.getByName(c);
		} catch (IllegalTypeException e)
		{
			ErrorPopup.showError(msg,
					msg.getMessage("MessageTemplatesEditor.errorConsumers"), e);
			return null;
		}
		return consumer;
	}

	private void addVar(AbstractTextField f, String val)
	{
		String v = f.getValue();
		String st = v.substring(0, f.getCursorPosition());
		String fi = v.substring(f.getCursorPosition());
		f.setValue(st + "${" + val + "}" + fi);
	}

	private void updateValidator()
	{
		MessageTemplateDefinition c = getConsumer();
		if (c != null)
		{
			subjectValidator.setConsumer(c);
			bodyValidator.setConsumer(c);
		}
	}

	private boolean validate()
	{
		updateValidator();
		name.setValidationVisible(true);
		consumer.setValidationVisible(true);
		subject.setValidationVisible(true);
		subject.setValidationVisible(true);
		body.setValidationVisible(true);
		return name.isValid() && consumer.isValid() && subject.isValid() && body.isValid();

	}

	private class MessageValidator implements Validator
	{
		private MessageTemplateDefinition c;
		private boolean checkMandatory;
		
		public MessageValidator(MessageTemplateDefinition c, boolean checkMandatory)
		{
			this.c = c;
			this.checkMandatory = checkMandatory;
		}

		public void setConsumer(MessageTemplateDefinition c)
		{
			this.c = c;
		}

		@Override
		public void validate(Object value) throws InvalidValueException
		{
			try
			{
				MessageTemplateValidator.validateText(c, value.toString(), checkMandatory);
			} catch (IllegalVariablesException e)
			{
				throw new InvalidValueException(msg.getMessage("MessageTemplatesEditor.errorUnknownVars", 
						e.getUnknown().toString()));
			} catch (MandatoryVariablesException e)
			{
				throw new InvalidValueException(msg.getMessage("MessageTemplatesEditor.errorMandatoryVars", 
						e.getMandatory().toString()));
			}
		}
	}
}
