/*
 * Copyright (c) 2015 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.webui.common.attributes;

import java.util.ArrayList;
import java.util.List;

import pl.edu.icm.unity.server.utils.UnityMessageSource;
import pl.edu.icm.unity.types.I18nString;
import pl.edu.icm.unity.types.basic.Attribute;
import pl.edu.icm.unity.types.basic.AttributeType;
import pl.edu.icm.unity.types.basic.AttributeValueSyntax;
import pl.edu.icm.unity.webui.common.attributes.WebAttributeHandler.RepresentationSize;
import pl.edu.icm.unity.webui.common.safehtml.HtmlConfigurableLabel;

import com.vaadin.ui.AbstractField;
import com.vaadin.ui.AbstractOrderedLayout;
import com.vaadin.ui.Component;
import com.vaadin.ui.Image;

/**
 * Shows an attribute values in read only mode. The look and feel is similar to the {@link FixedAttributeEditor}
 * but the value is shown in read only mode.
 * @author K. Benedyczak
 */
public class AttributeViewer
{
	private UnityMessageSource msg;
	private AttributeHandlerRegistry registry;
	private AttributeType attributeType;
	private Attribute<?> attribute;
	private boolean showGroup;
	private List<Component> values;
	
	public AttributeViewer(UnityMessageSource msg, AttributeHandlerRegistry registry,
			AttributeType attributeType, Attribute<?> attribute, boolean showGroup)
	{
		this.msg = msg;
		this.registry = registry;
		this.attributeType = attributeType;
		this.attribute = attribute;
		this.showGroup = showGroup;
	}

	public void removeFromLayout(AbstractOrderedLayout parent)
	{
		for (Component c: values)
			parent.removeComponent(c);
	}
	
	public void addToLayout(AbstractOrderedLayout parent)
	{
		String caption = attributeType.getDisplayedName().getValue(msg);
		I18nString description = attributeType.getDescription();
		
		if (showGroup && !attribute.getGroupPath().equals("/"))
			caption = caption + " @" + attribute.getGroupPath(); 

		int i = 1;
		values = new ArrayList<>();
		for (Object o: attribute.getValues())
		{
			Component valueRepresentation = getRepresentation(o);
			String captionWithNum = (attribute.getValues().size() == 1) ? caption + ":" :
				caption + " (" + i + "):";
			valueRepresentation.setCaption(captionWithNum);
			if (description != null)
			{
				String descriptionRaw = description.getValue(msg);
				if (descriptionRaw != null)
				{
					String descSafe = HtmlConfigurableLabel.conditionallyEscape(descriptionRaw);
					if (valueRepresentation instanceof AbstractField<?>)
						((AbstractField<?>)valueRepresentation).setDescription(descSafe);
					if (valueRepresentation instanceof Image)
						((Image)valueRepresentation).setDescription(descSafe);
				}
			}
			
			valueRepresentation.addStyleName("u-baseline");
			values.add(valueRepresentation);
			parent.addComponent(valueRepresentation);
			i++;

		}
	}
	
	private Component getRepresentation(Object value)
	{
		@SuppressWarnings("unchecked")
		AttributeValueSyntax<Object> syntax = (AttributeValueSyntax<Object>) 
				attribute.getAttributeSyntax();
		@SuppressWarnings("unchecked")
		WebAttributeHandler<Object> handler = (WebAttributeHandler<Object>) 
				registry.getHandler(syntax.getValueSyntaxId());
		Component ret = handler.getRepresentation(value, syntax, RepresentationSize.MEDIUM);
		return ret;
	}
}
