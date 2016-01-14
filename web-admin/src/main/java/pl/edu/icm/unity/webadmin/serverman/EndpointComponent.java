/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.webadmin.serverman;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import pl.edu.icm.unity.server.api.EndpointManagement;
import pl.edu.icm.unity.server.api.ServerManagement;
import pl.edu.icm.unity.server.api.internal.NetworkServer;
import pl.edu.icm.unity.server.utils.Log;
import pl.edu.icm.unity.server.utils.UnityMessageSource;
import pl.edu.icm.unity.server.utils.UnityServerConfiguration;
import pl.edu.icm.unity.types.I18nString;
import pl.edu.icm.unity.types.authn.AuthenticationOptionDescription;
import pl.edu.icm.unity.types.endpoint.EndpointConfiguration;
import pl.edu.icm.unity.types.endpoint.EndpointDescription;
import pl.edu.icm.unity.webui.common.CompactFormLayout;
import pl.edu.icm.unity.webui.common.ConfirmDialog;
import pl.edu.icm.unity.webui.common.NotificationPopup;
import pl.edu.icm.unity.webui.common.i18n.I18nLabel;

import com.vaadin.ui.FormLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;

/**
 * Display endpoint fields with values
 * Allow deploy/undeploy/reload endpoint  
 * 
 * @author P. Piernik
 */
@Component
public class EndpointComponent extends DeployableComponentViewBase
{
	private static final Logger log = Log.getLogger(Log.U_SERVER_WEB,
			EndpointComponent.class);

	private EndpointManagement endpointMan;
	private EndpointDescription endpoint;
	private NetworkServer networkServer;

	public EndpointComponent(EndpointManagement endpointMan, ServerManagement serverMan, NetworkServer networkServer,
			EndpointDescription endpoint, UnityServerConfiguration config,
			UnityMessageSource msg, String status)
	{
		super(config, serverMan, msg, status);
		this.endpointMan = endpointMan;
		this.endpoint = endpoint;
		this.networkServer = networkServer;
		initUI();
		setStatus(status);
	}

	@Override
	public void undeploy()
	{
		if (!super.reloadConfig())
		{
			return;
		}
		String id = endpoint.getId();
		log.info("Undeploy " + id + " endpoint");
		try
		{
			endpointMan.undeploy(id);
		} catch (Exception e)
		{
			log.error("Cannot undeploy endpoint", e);
			NotificationPopup.showError(msg, msg.getMessage("Endpoints.cannotUndeploy", id), e);
			return;
		}

		if (getEndpointConfig(id) != null)
		{
			setStatus(Status.undeployed.toString());
		} else
		{
			setVisible(false);
		}

	}

	@Override
	public void deploy()
	{
		if (!super.reloadConfig())
		{
			return;
		}
		String id = endpoint.getId();
		log.info("Deploy " + id + " endpoint");
		if (!deployEndpoint(id))
		{
			NotificationPopup.showError(msg, msg.getMessage("Endpoints.cannotDeploy",
					endpoint.getId()), msg.getMessage(
					"Endpoints.cannotDeployRemovedConfig", id));
			setVisible(false);
			return;

		}else
		{
			setStatus(Status.deployed.toString());	
		}

	}

	private boolean deployEndpoint(String id)
	{
		EndpointConfigExt data = getEndpointConfig(id);
		if (data == null)
		{
			return false;
		}
		
		try
		{
			this.endpoint = endpointMan.deploy(data.type, id, data.address,	data.basicConfig);
		} catch (Exception e)
		{
			log.error("Cannot deploy endpoint", e);
			NotificationPopup.showError(msg, msg.getMessage("Endpoints.cannotDeploy", id), e);
			return false;
		}

		return true;
	}

	@Override
	public void reload(boolean showSuccess)
	{
		if (!super.reloadConfig())
		{
			return;
		}

		String id = endpoint.getId();
		log.info("Reload " + id + " endpoint");
		if (!reloadEndpoint(id))
		{
			new ConfirmDialog(msg, msg.getMessage("Endpoints.unDeployWhenRemoved",
					id), new ConfirmDialog.Callback()
			{
				@Override
				public void onConfirm()
				{
					undeploy();
				}
			}).show();

		}else
		{
			setStatus(Status.deployed.toString());
			if (showSuccess)
			{
				NotificationPopup.showSuccess(msg, "", msg.getMessage(
						"Endpoints.reloadSuccess", id));
			}
			
		}
		
		
		
	}
	
	private boolean reloadEndpoint(String id)
	{
		EndpointConfigExt data = getEndpointConfig(id);
		if (data == null)
		{
			return false;		
		}
		
		try
		{
			endpointMan.updateEndpoint(id, data.basicConfig);
		} catch (Exception e)
		{
			log.error("Cannot update endpoint", e);
			NotificationPopup.showError(msg,
					msg.getMessage("Endpoints.cannotUpdate", id),
					e);
			return false;
		}

		try
		{
			for (EndpointDescription en : endpointMan.getEndpoints())
			{
				if (id.equals(en.getId()))
				{
					this.endpoint = en;
				}
			}
		} catch (Exception e)
		{
			log.error("Cannot load endpoints", e);
			NotificationPopup.showError(msg, msg.getMessage("error"),
					msg.getMessage("Endpoints.cannotLoadList"));
			return false;
		}
		return true;
	}

	protected void updateHeader()
	{
		updateHeader(endpoint.getId());
	}

	protected void updateContent()
	{
		content.removeAllComponents();

		if (status.equals(Status.undeployed.toString()))
			return;

		addFieldToContent(msg.getMessage("Endpoints.type"), endpoint.getType().getName());
		addFieldToContent(msg.getMessage("Endpoints.typeDescription"), endpoint.getType()
				.getDescription());
		I18nLabel displayedName = new I18nLabel(msg, msg.getMessage("displayedNameF"));
		displayedName.setValue(endpoint.getDisplayedName());
		addCustomFieldToContent(displayedName);
		addFieldToContent(msg.getMessage("Endpoints.paths"), "");
		
		HorizontalLayout hp = new HorizontalLayout();
		FormLayout pa = new CompactFormLayout();
		pa.setSpacing(false);
		pa.setMargin(false);

		FormLayout pad = new CompactFormLayout();
		pad.setSpacing(false);
		pad.setMargin(false);
		int i = 0;
		for (Map.Entry<String, String> entry : endpoint.getType().getPaths().entrySet())
		{
			i++;
			addField(pa, String.valueOf(i), networkServer.getAdvertisedAddress()
					+ endpoint.getContextAddress() + entry.getKey());
			addField(pad, msg.getMessage("Endpoints.pathDescription"), entry.getValue());

		}
		Label space = new Label();
		space.setWidth(15, Unit.PIXELS);
		hp.addComponents(pa, space, pad);
		content.addComponent(hp);

		StringBuilder bindings = new StringBuilder();
		for (String s : endpoint.getType().getSupportedBindings())
		{
			if (bindings.length() > 0)
				bindings.append(",");
			bindings.append(s);
		}
		addFieldToContent(msg.getMessage("Endpoints.binding"), bindings.toString());
		
		if (endpoint.getDescription() != null && endpoint.getDescription().length() > 0)
		{
			addFieldToContent(msg.getMessage("Endpoints.description"),
					endpoint.getDescription());

		}
		addFieldToContent(msg.getMessage("Endpoints.contextAddress"),
				endpoint.getContextAddress());

		addFieldToContent(msg.getMessage("Endpoints.authenticatorsSet"), "");
		FormLayout au = new CompactFormLayout();
		au.setSpacing(false);
		au.setMargin(false);
		i = 0;
		for (AuthenticationOptionDescription s : endpoint.getAuthenticatorSets())
		{
			i++;
			addField(au, String.valueOf(i), s.toString());
		}
		content.addComponent(au);
	}
	
	private EndpointConfigExt getEndpointConfig(String name)
	{
		String endpointKey = null;
		Set<String> endpointsList = config.getStructuredListKeys(UnityServerConfiguration.ENDPOINTS);
		for (String endpoint: endpointsList)
		{

			String cname = config.getValue(endpoint + UnityServerConfiguration.ENDPOINT_NAME);
			if (name.equals(cname))
			{
				endpointKey = endpoint;
			}	
		}
		if (endpointKey == null)
		{
			return null;
		}

		EndpointConfigExt ret = new EndpointConfigExt();
		
		
		String description = config.getValue(endpointKey
				+ UnityServerConfiguration.ENDPOINT_DESCRIPTION);
		List<AuthenticationOptionDescription> authn = config.getEndpointAuth(endpointKey);
		ret.type = config.getValue(endpointKey
						+ UnityServerConfiguration.ENDPOINT_TYPE);
		ret.address = config.getValue(endpointKey
				+ UnityServerConfiguration.ENDPOINT_ADDRESS);
		String realm = config.getValue(endpointKey
				+ UnityServerConfiguration.ENDPOINT_REALM);
		I18nString displayedName = config.getLocalizedString(msg, endpointKey
				+ UnityServerConfiguration.ENDPOINT_DISPLAYED_NAME);
		if (displayedName.isEmpty())
			displayedName.setDefaultValue(name);
		String jsonConfig;
		try
		{
			jsonConfig = serverMan.loadConfigurationFile(config.getValue(endpointKey
					                           + UnityServerConfiguration.ENDPOINT_CONFIGURATION));
		} catch (Exception e)
		{
			log.error("Cannot read json file", e);
			NotificationPopup.showError(msg, msg.getMessage("Endpoints.cannotReadJsonConfig"),
					e);
			return null;
		}
		
		ret.basicConfig = new EndpointConfiguration(displayedName, description, authn, jsonConfig, realm);
		return ret;
		
	}
	
	private static class EndpointConfigExt
	{
		private String type;
		private String address;
		private EndpointConfiguration basicConfig;
	}
}
