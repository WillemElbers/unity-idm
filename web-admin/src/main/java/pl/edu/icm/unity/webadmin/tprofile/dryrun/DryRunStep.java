/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.webadmin.tprofile.dryrun;

import org.vaadin.teemu.wizards.WizardStep;

import pl.edu.icm.unity.sandbox.SandboxAuthnEvent;
import pl.edu.icm.unity.server.api.TranslationProfileManagement;
import pl.edu.icm.unity.server.utils.UnityMessageSource;

import com.vaadin.ui.Component;

/**
 * Fourth dryrun step with profile validation - used in {@link DryRunDialogComponent}.
 * 
 * @author Roman Krysinski
 */
public class DryRunStep implements WizardStep 
{

	private UnityMessageSource msg;
	private DryRunStepComponent dryRunComponent;

	
	public DryRunStep(UnityMessageSource msg, String sandboxURL, 
			TranslationProfileManagement tpMan) 
	{
		this.msg = msg;
		dryRunComponent = new DryRunStepComponent(msg, sandboxURL, tpMan);
	}

	public void handle(SandboxAuthnEvent event) 
	{
		dryRunComponent.handle(event);
	}

	@Override
	public String getCaption() 
	{
		return msg.getMessage("DryRun.DryRunStep.caption");
	}

	@Override
	public Component getContent() 
	{
		return dryRunComponent;
	}

	@Override
	public boolean onAdvance() 
	{
		return true;
	}

	@Override
	public boolean onBack() 
	{
		dryRunComponent.indicateProgress();
		return true;
	}
}
