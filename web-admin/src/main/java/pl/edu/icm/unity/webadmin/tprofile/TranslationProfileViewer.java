/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.webadmin.tprofile;

import pl.edu.icm.unity.exceptions.IllegalTypeException;
import pl.edu.icm.unity.server.registries.TranslationActionsRegistry;
import pl.edu.icm.unity.server.translation.AbstractTranslationRule;
import pl.edu.icm.unity.server.translation.ActionParameterDesc;
import pl.edu.icm.unity.server.translation.TranslationActionFactory;
import pl.edu.icm.unity.server.translation.TranslationProfile;
import pl.edu.icm.unity.server.utils.UnityMessageSource;
import pl.edu.icm.unity.webui.common.CompactFormLayout;
import pl.edu.icm.unity.webui.common.safehtml.HtmlLabel;

import com.vaadin.ui.FormLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.VerticalLayout;

/**
 * Component allowing to view all information about translation profile.
 * @author P. Piernik
 * 
 */
public class TranslationProfileViewer extends VerticalLayout
{	
	private UnityMessageSource msg;
	private TranslationActionsRegistry registry;
	protected Label name;
	protected Label description;
	private FormLayout rules;
	private FormLayout main;
	
	
	public TranslationProfileViewer(UnityMessageSource msg, TranslationActionsRegistry registry)
	{
		super();
		this.msg = msg;
		this.registry = registry;
		initUI();
	}

	protected void initUI()
	{	
		main = new CompactFormLayout();
		name = new Label();
		name.setCaption(msg.getMessage("TranslationProfileViewer.name"));
		description = new Label();
		description.setReadOnly(true);
		description.setCaption(msg.getMessage("TranslationProfileViewer.description"));
		rules = new CompactFormLayout();
		rules.setMargin(false);
		rules.setSpacing(false);
		Label rulesLabel = new Label();
		rulesLabel.setCaption(msg.getMessage("TranslationProfileViewer.rules"));
		main.addComponents(name, description, rulesLabel, rules);
		addComponent(main);
		setSizeFull();
	}

	public void setInput(TranslationProfile profile)
	{       
		setEmpty();
		if (profile == null)
		{
			main.setVisible(false);
			return;
		}
		main.setVisible(true);
		
		name.setValue(profile.getName());
		description.setValue(profile.getDescription());
		int i=0;
		for (AbstractTranslationRule<?> rule : profile.getRules())
		{
			ActionParameterDesc[] pd = null;
			try 
			{
				TranslationActionFactory f = registry.getByName(rule.getAction().
						getActionDescription().getName());
				pd = f.getParameters();
			} catch (IllegalTypeException e)
			{
				
			}
			i++;     
			addField(msg.getMessage("TranslationProfileViewer.ruleCondition", i),
					"TranslationProfileViewer.codeValue", 
					rule.getCondition().getCondition());
			addField(msg.getMessage("TranslationProfileViewer.ruleAction"),
					"TranslationProfileViewer.codeValue", 
					rule.getAction().getActionDescription().getName());
			String[] par = rule.getAction().getParameters();
			for (int j = 0; j < par.length; j++)
			{
				if (j == 0)
				{
					addField(msg.getMessage("TranslationProfileViewer.ruleActionParameters"),
						"TranslationProfileViewer.ruleActionParameter",
								pd[j].getName(), getParamValue(pd[j], par[j]));
				}else
				{
					addField("", "TranslationProfileViewer.ruleActionParameter",
							pd[j].getName(), getParamValue(pd[j], par[j]));
				}
			}		
		}

	}

	protected void addField(String name, String msgKey, Object... unsafeArgs)
	{
		HtmlLabel val = new HtmlLabel(msg);
		val.setCaption(name);
		val.setHtmlValue(msgKey, unsafeArgs);
		rules.addComponent(val);
	}
	
	protected void setEmpty()
	{
		rules.removeAllComponents();
		name.setValue("");
		description.setValue("");
	}

	private Object getParamValue(ActionParameterDesc desc, String value)
	{
		if (value == null)
			return "";
		return value.replace("\n", " | ");
	}
}
