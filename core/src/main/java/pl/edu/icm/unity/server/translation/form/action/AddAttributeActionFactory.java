/*
 * Copyright (c) 2015 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.server.translation.form.action;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;
import org.mvel2.MVEL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.exceptions.IllegalAttributeValueException;
import pl.edu.icm.unity.exceptions.WrongArgumentException;
import pl.edu.icm.unity.server.api.AttributesManagement;
import pl.edu.icm.unity.server.translation.ActionParameterDesc;
import pl.edu.icm.unity.server.translation.ActionParameterDesc.Type;
import pl.edu.icm.unity.server.translation.TranslationAction;
import pl.edu.icm.unity.server.translation.TranslationActionDescription;
import pl.edu.icm.unity.server.translation.form.TranslatedRegistrationRequest;
import pl.edu.icm.unity.server.utils.Log;
import pl.edu.icm.unity.types.basic.Attribute;
import pl.edu.icm.unity.types.basic.AttributeType;
import pl.edu.icm.unity.types.basic.AttributeValueSyntax;
import pl.edu.icm.unity.types.basic.AttributeVisibility;

/**
 * Allows for adding an additional attribute to for the requester
 * 
 * @author K. Benedyczak
 */
@Component
public class AddAttributeActionFactory extends AbstractTranslationActionFactory
{
	public static final String NAME = "addAttribute";
	private AttributesManagement attrsMan;
	
	@Autowired
	public AddAttributeActionFactory(AttributesManagement attrsMan)
	{
		super(NAME, new ActionParameterDesc[] {
				new ActionParameterDesc(
						"attributeName",
						"TranslationAction.addAttribute.paramDesc.attributeName",
						1, 1, Type.UNITY_ATTRIBUTE),
				new ActionParameterDesc(
						"group",
						"TranslationAction.addAttribute.paramDesc.group",
						1, 1, Type.UNITY_GROUP),
				new ActionParameterDesc(
						"expression",
						"TranslationAction.addAttribute.paramDesc.expression",
						1, 1, Type.EXPRESSION),
				new ActionParameterDesc(
						"visibility",
						"TranslationAction.addAttribute.paramDesc.visibility",
						1, 1, AttributeVisibility.class)
		});
		this.attrsMan = attrsMan;
	}

	@Override
	public TranslationAction getInstance(String... parameters) throws EngineException
	{
		return new AddAttributeAction(this, parameters, attrsMan);
	}
	
	public static class AddAttributeAction extends AbstractRegistrationTranslationAction
	{
		private static final Logger log = Log.getLogger(Log.U_SERVER_TRANSLATION,
				AddAttributeActionFactory.AddAttributeAction.class);
		private String unityAttribute;
		private String group;
		private AttributeVisibility visibility;
		private Serializable expressionCompiled;
		private AttributeType at;
		
		public AddAttributeAction(TranslationActionDescription description, String[] parameters, 
				AttributesManagement attrsMan) throws EngineException
		{
			super(description, parameters);
			setParameters(parameters);
			at = attrsMan.getAttributeTypesAsMap().get(unityAttribute);
			if (at == null)
				throw new WrongArgumentException("Attribute type " + unityAttribute + " is not known");
		}

		@Override
		protected void invokeWrapped(TranslatedRegistrationRequest state, Object mvelCtx,
				String currentProfile) throws EngineException
		{
			Object value = MVEL.executeExpression(expressionCompiled, mvelCtx);
			if (value == null)
			{
				log.debug("Attribute value evaluated to null, skipping");
				return;
			}
			
			List<Object> typedValues;
			try
			{
				typedValues = convertValues(value, at.getValueType());
			} catch (IllegalAttributeValueException e)
			{
				log.debug("Can't convert attribute values returned by the action's expression "
						+ "to the type of attribute " + unityAttribute + " , skipping it", e);
				return;
			}
			
			@SuppressWarnings({ "unchecked", "rawtypes" })
			Attribute<?> attribute = new Attribute(unityAttribute, at.getValueType(), group, 
					visibility, typedValues, null, currentProfile);
			log.debug("Mapped attribute: " + attribute);
			state.addAttribute(attribute);
		}

		public static List<Object> convertValues(Object value, AttributeValueSyntax<?> syntax) 
				throws IllegalAttributeValueException
		{
			List<?> aValues = value instanceof List ? (List<?>)value : Collections.singletonList(value);
			List<Object> ret = new ArrayList<Object>(aValues.size());
			for (Object o: aValues)
			{
				Object converted = syntax.convertFromString(o.toString());
				ret.add(converted);
			}
			return ret;
		}
		
		private void setParameters(String[] parameters)
		{
			if (parameters.length != 4)
				throw new IllegalArgumentException("Action requires exactly 4 parameters");
			unityAttribute = parameters[0];
			group = parameters[1];
			expressionCompiled = MVEL.compileExpression(parameters[2]);
			visibility = AttributeVisibility.valueOf(parameters[3]);
		}
	}
}
