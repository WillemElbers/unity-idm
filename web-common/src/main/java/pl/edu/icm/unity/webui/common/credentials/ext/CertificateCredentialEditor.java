/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.webui.common.credentials.ext;

import com.vaadin.ui.Component;
import com.vaadin.ui.Label;

import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.exceptions.IllegalCredentialException;
import pl.edu.icm.unity.server.utils.UnityMessageSource;
import pl.edu.icm.unity.webui.common.ComponentsContainer;
import pl.edu.icm.unity.webui.common.credentials.CredentialEditor;

/**
 * Allows to setup certificate credential. Currently no input needed.
 * 
 * @author K. Benedyczak
 */
public class CertificateCredentialEditor implements CredentialEditor
{
	private UnityMessageSource msg;

	public CertificateCredentialEditor(UnityMessageSource msg)
	{
		this.msg = msg;
	}

	@Override
	public ComponentsContainer getEditor(boolean askAboutCurrent, 
			String credentialConfiguration, boolean required)
	{
		Label label = new Label(msg.getMessage("CertificateCredentialEditor.info"));
		return new ComponentsContainer(label);
	}

	@Override
	public String getValue() throws IllegalCredentialException
	{
		return "";
	}

	@Override
	public Component getViewer(String credentialConfiguration)
	{
		return null;
	}

	@Override
	public String getCurrentValue() throws IllegalCredentialException
	{
		return "";
	}

	@Override
	public void setCredentialError(EngineException message)
	{
	}

	@Override
	public void setPreviousCredentialError(String message)
	{
	}
}
