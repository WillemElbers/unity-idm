/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.webadmin.identities;

import java.util.Collection;

import com.vaadin.ui.CheckBox;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.Label;

import pl.edu.icm.unity.server.api.AttributesManagement;
import pl.edu.icm.unity.server.utils.UnityMessageSource;
import pl.edu.icm.unity.types.basic.AttributeType;
import pl.edu.icm.unity.webui.common.AbstractDialog;
import pl.edu.icm.unity.webui.common.ErrorPopup;

/**
 * Allows to choose an attribute type to be added as identities table column.
 * @author K. Benedyczak
 */
public class AddAttributeColumnDialog extends AbstractDialog
{
	private AttributesManagement attrsMan;
	protected Callback callback;
	
	private ComboBox attributeType;
	private CheckBox useRootGroup;
	
	
	public AddAttributeColumnDialog(UnityMessageSource msg, AttributesManagement attrsMan, 
			Callback callback)
	{
		super(msg, msg.getMessage("AddAttributeColumnDialog.caption"));
		this.attrsMan = attrsMan;
		this.callback = callback;
		this.defaultSizeUndfined = true;
	}

	@Override
	protected FormLayout getContents()
	{
		Label info = new Label(msg.getMessage("AddAttributeColumnDialog.info"));
		Label info2 = new Label(msg.getMessage("AddAttributeColumnDialog.info2"));
		attributeType = new ComboBox(msg.getMessage("AddAttributeColumnDialog.attribute"));
		Collection<AttributeType> attrTypes;
		try
		{
			attrTypes = attrsMan.getAttributeTypes();
		} catch (Exception e)
		{
			ErrorPopup.showError(msg, msg.getMessage("error"),
					msg.getMessage("AddAttributeColumnDialog.cantGetAttrTypes"));
			throw new IllegalStateException();
		}
		String sel = null;
		for (AttributeType at: attrTypes)
		{
			attributeType.addItem(at.getName());
			if (sel == null)
				sel = at.getName();
		}
		if (sel != null)
			attributeType.select(sel);
		attributeType.setNullSelectionAllowed(false);
		
		useRootGroup = new CheckBox(msg.getMessage("AddAttributeColumnDialog.useRootGroup"), true);
		useRootGroup.setDescription(msg.getMessage("AddAttributeColumnDialog.useRootGroupTooltip"));
		
		FormLayout main = new FormLayout();
		main.addComponents(info, info2, attributeType, useRootGroup);
		main.setSizeFull();
		return main;
	}

	@Override
	protected void onConfirm()
	{
		String group = useRootGroup.getValue() ? "/" : null;
		callback.onChosen((String)attributeType.getValue(), group);
		close();
	}
	
	public interface Callback 
	{
		public void onChosen(String attributeType, String group);
	}
}
