/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.webadmin.serverman;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.server.api.AuthenticationManagement;
import pl.edu.icm.unity.server.utils.Log;
import pl.edu.icm.unity.server.utils.UnityMessageSource;
import pl.edu.icm.unity.server.utils.UnityServerConfiguration;
import pl.edu.icm.unity.types.authn.AuthenticatorInstance;
import pl.edu.icm.unity.webui.common.ConfirmDialog;
import pl.edu.icm.unity.webui.common.ErrorPopup;

import com.vaadin.ui.HorizontalLayout;

/**
 * Show authenticator
 * 
 * @author P. Piernik
 */
public class SingleAuthenticatorComponent extends SingleComponent
{

	private static final Logger log = Log.getLogger(Log.U_SERVER_WEB,
			SingleAuthenticatorComponent.class);

	AuthenticatorInstance authenticator;
	AuthenticationManagement authMan;

	public SingleAuthenticatorComponent(AuthenticationManagement authMan,
			AuthenticatorInstance authenticator, UnityServerConfiguration config,
			UnityMessageSource msg, String status, String msgPrefix)
	{
		super(config, msg, status, msgPrefix);
		this.authenticator = authenticator;
		this.authMan = authMan;
		setStatus(status);
	}

	@Override
	protected void updateHeader()
	{
		super.updateHeader(authenticator.getId());
	}

	@Override
	protected void updateContent()
	{
		content.removeAllComponents();

		if (status.equals(STATUS_DEPLOYED))
		{
			addFieldWithLabel(content, msg.getMessage(msgPrefix + ".type"),
					authenticator.getTypeDescription().getId(), 19);

			HorizontalLayout vm = new HorizontalLayout();
			addFieldWithLabel(vm, msg.getMessage(msgPrefix + ".verificationMethod"),
					authenticator.getTypeDescription().getVerificationMethod(),
					35);
			addFieldWithLabel(vm, msg.getMessage(msgPrefix
					+ ".verificationMethodDescription"), authenticator
					.getTypeDescription().getVerificationMethodDescription(), 2);
			content.addComponent(vm);

			HorizontalLayout rm = new HorizontalLayout();
			addFieldWithLabel(rm, msg.getMessage(msgPrefix + ".retrievalMethod"),
					authenticator.getTypeDescription().getRetrievalMethod(), 35);
			addFieldWithLabel(rm, msg.getMessage(msgPrefix
					+ ".retrievalMethodDescription"), authenticator
					.getTypeDescription().getRetrievalMethodDescription(), 2);
			content.addComponent(rm);

			addFieldWithLabel(content, msg.getMessage(msgPrefix + ".supportedBinding"),
					authenticator.getTypeDescription().getSupportedBinding(),
					35);

			addFieldWithLabel(content, msg.getMessage(msgPrefix + ".localCredential"),
					authenticator.getLocalCredentialName(), 19);

			addFieldWithLabel(
					content,
					msg.getMessage(msgPrefix + ".verificatorJsonConfiguration"),
					authenticator.getVerificatorJsonConfiguration(), 19);

			addFieldWithLabel(content,
					msg.getMessage(msgPrefix + ".retrievalJsonConfiguration"),
					authenticator.getRetrievalJsonConfiguration(), 19);

		}
	}

	@Override
	protected boolean undeploy()
	{
		if (super.undeploy())
		{

			log.info("Remove " + authenticator.getId() + " authenticator");
			try
			{
				authMan.removeAuthenticator(authenticator.getId());
			} catch (Exception e)
			{
				log.error("Cannot remove authenticator", e);
				ErrorPopup.showError(msg,
						msg.getMessage(msgPrefix + "." + "cannotUndeploy"), e);
				return false;

			}

			boolean inConfig = false;

			Set<String> authenticatorsList = config
					.getStructuredListKeys(UnityServerConfiguration.AUTHENTICATORS);
			for (String authenticatorKey : authenticatorsList)
			{
				if (config.getValue(
						authenticatorKey
								+ UnityServerConfiguration.AUTHENTICATOR_NAME)
						.equals(authenticator.getId()))
				{
					inConfig = true;
				}

			}

			if (inConfig)
			{
				setStatus(STATUS_UNDEPLOYED);
			} else
			{
				setVisible(false);
			}

		}
		return true;
	}

	@Override
	protected boolean deploy()
	{
		if (super.deploy())
		{
			log.info("Add " + authenticator.getId() + "authenticator");
			boolean added = false;

			Set<String> authenticatorsList = config
					.getStructuredListKeys(UnityServerConfiguration.AUTHENTICATORS);
			for (String authenticatorKey : authenticatorsList)
			{

				String name = config.getValue(authenticatorKey
						+ UnityServerConfiguration.AUTHENTICATOR_NAME);
				if (authenticator.getId().equals(name))
				{
					String type = config
							.getValue(authenticatorKey
									+ UnityServerConfiguration.AUTHENTICATOR_TYPE);
					File vConfigFile = config
							.getFileValue(authenticatorKey
									+ UnityServerConfiguration.AUTHENTICATOR_VERIFICATOR_CONFIG,
									false);
					File rConfigFile = config
							.getFileValue(authenticatorKey
									+ UnityServerConfiguration.AUTHENTICATOR_RETRIEVAL_CONFIG,
									false);
					String credential = config
							.getValue(authenticatorKey
									+ UnityServerConfiguration.AUTHENTICATOR_CREDENTIAL);

					String vJsonConfiguration = null;
					String rJsonConfiguration = null;
					try
					{
						vJsonConfiguration = vConfigFile == null ? null
								: FileUtils.readFileToString(vConfigFile);
						rJsonConfiguration = FileUtils
								.readFileToString(rConfigFile);
					} catch (IOException e)
					{
						log.error("Cannot read json file", e);
						ErrorPopup.showError(msg, msg.getMessage(msgPrefix
								+ ".cannotReadJsonConfig"), e);
						return false;
					}

					log.info("Add " + name + " [" + type + "]");
					try
					{
						this.authenticator = authMan.createAuthenticator(
								name, type, vJsonConfiguration,
								rJsonConfiguration, credential);
					} catch (EngineException e)
					{
						log.error("Cannot add authenticator", e);
						ErrorPopup.showError(
								msg,
								msg.getMessage(msgPrefix
										+ ".cannotDeploy"),
								e);
						return false;
					}
					setStatus(STATUS_DEPLOYED);
					added = true;
				}

			}

			if (!added)
			{
				ErrorPopup.showError(
						msg,
						msg.getMessage(msgPrefix + ".cannotDeploy"),
						msg.getMessage(msgPrefix
								+ ".cannotDeployRemovedConfig"));
				setVisible(false);
				return false;

			}
		}
		return true;
	}

	@Override
	protected boolean reload()
	{
		if (super.reload())
		{
			log.info("Reload " + authenticator.getId() + " authenticator");
			boolean updated = false;

			Set<String> authenticatorsList = config
					.getStructuredListKeys(UnityServerConfiguration.AUTHENTICATORS);
			for (String authenticatorKey : authenticatorsList)
			{

				String name = config.getValue(authenticatorKey
						+ UnityServerConfiguration.AUTHENTICATOR_NAME);
				if (authenticator.getId().equals(name))
				{
					String type = config
							.getValue(authenticatorKey
									+ UnityServerConfiguration.AUTHENTICATOR_TYPE);
					File vConfigFile = config
							.getFileValue(authenticatorKey
									+ UnityServerConfiguration.AUTHENTICATOR_VERIFICATOR_CONFIG,
									false);
					File rConfigFile = config
							.getFileValue(authenticatorKey
									+ UnityServerConfiguration.AUTHENTICATOR_RETRIEVAL_CONFIG,
									false);
					
					String vJsonConfiguration = null;
					String rJsonConfiguration = null;
					try
					{
						vJsonConfiguration = vConfigFile == null ? null
								: FileUtils.readFileToString(vConfigFile);
						rJsonConfiguration = FileUtils
								.readFileToString(rConfigFile);
					} catch (IOException e)
					{
						log.error("Cannot read json file", e);
						ErrorPopup.showError(msg, msg.getMessage(msgPrefix
								+ ".cannotReadJsonConfig"), e);
						return false;
					}

					log.info("Add " + name + " [" + type + "]");
					try
					{
						authMan.updateAuthenticator(name,
								vJsonConfiguration,
								rJsonConfiguration);
					} catch (EngineException e)
					{
						log.error("Cannot add authenticator", e);
						ErrorPopup.showError(
								msg,
								msg.getMessage(msgPrefix
										+ ".cannotDeploy"),
								e);
						return false;
					}

					try
					{
						for (AuthenticatorInstance au : authMan
								.getAuthenticators(null))
						{
							if (au.getId()
									.equals(authenticator
											.getId()))
							{
								this.authenticator = au;
							}
						}
					} catch (EngineException e)
					{
						log.error("Cannot load authenticators", e);
						ErrorPopup.showError(
								msg,
								msg.getMessage("error"),
								msg.getMessage(msgPrefix
										+ ".cannotLoadList"));
					}

					setStatus(STATUS_DEPLOYED);
					updated = true;
				}

			}
			if (!updated)
			{
				new ConfirmDialog(msg, msg.getMessage(msgPrefix
						+ ".unDeployWhenRemoved"),
						new ConfirmDialog.Callback()

						{

							@Override
							public void onConfirm()
							{

								undeploy();

							}
						}).show();

			}
		}

		return true;
	}

}
