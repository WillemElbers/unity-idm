/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.webadmin.tprofile.dryrun;

import pl.edu.icm.unity.server.utils.UnityMessageSource;

import com.vaadin.annotations.AutoGenerated;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.Label;
import com.vaadin.ui.VerticalLayout;

/**
 * UI Component used by DryRun - {@link IntroStep}.
 * 
 * @author Roman Krysinski
 */
public class IntroStepComponent extends CustomComponent 
{

	/*- VaadinEditorProperties={"grid":"RegularGrid,20","showGrid":true,"snapToGrid":true,"snapToObject":true,"movingGuides":false,"snappingDistance":10} */

	@AutoGenerated
	private VerticalLayout mainLayout;
	@AutoGenerated
	private Label introLabel;
	private static final long serialVersionUID = 6976972617938388430L;
	/**
	 * The constructor should first build the main layout, set the
	 * composition root and then do any custom initialization.
	 *
	 * The constructor will not be automatically regenerated by the
	 * visual editor.
	 * @param msg 
	 */
	public IntroStepComponent(UnityMessageSource msg) 
	{
		buildMainLayout();
		setCompositionRoot(mainLayout);

		introLabel.setValue(msg.getMessage("DryRun.IntroStepComponent.introLabel"));
	}

	@AutoGenerated
	private VerticalLayout buildMainLayout() {
		// common part: create layout
		mainLayout = new VerticalLayout();
		mainLayout.setImmediate(false);
		mainLayout.setWidth("100%");
		mainLayout.setHeight("100%");
		mainLayout.setMargin(true);
		
		// top-level component properties
		setWidth("100.0%");
		setHeight("100.0%");
		
		// introLabel
		introLabel = new Label();
		introLabel.setImmediate(false);
		introLabel.setWidth("100.0%");
		introLabel.setHeight("100.0%");
		introLabel.setValue("Label");
		introLabel.setContentMode(ContentMode.HTML);
		mainLayout.addComponent(introLabel);
		
		return mainLayout;
	}

}
