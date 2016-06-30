/*
 * Copyright (c) 2014 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.saml.metadata.cfg;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;

import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.saml.SamlProperties;
import pl.edu.icm.unity.saml.metadata.cfg.MetadataVerificator.MetadataValidationException;
import pl.edu.icm.unity.saml.sp.SAMLSPProperties.MetadataSignatureValidation;
import pl.edu.icm.unity.server.api.PKIManagement;
import pl.edu.icm.unity.server.utils.ExecutorsService;
import pl.edu.icm.unity.server.utils.Log;
import pl.edu.icm.unity.server.utils.UnityServerConfiguration;
import xmlbeans.org.oasis.saml2.metadata.EntitiesDescriptorDocument;

/**
 * Manages the retrieval, loading and update of runtime configuration based on the remote SAML metadata. 
 * @author K. Benedyczak
 */
public class RemoteMetaManager
{
	private static final Logger log = Log.getLogger(Log.U_SERVER_SAML, RemoteMetaManager.class);
	private PKIManagement pkiManagement;
	private SamlProperties configuration;
	private ExecutorsService executorsService;
	private RemoteMetadataProvider remoteMetaProvider;
	private AbstractMetaToConfigConverter converter;
	private MetadataVerificator verificator;
	private SamlProperties virtualConfiguration;
	private Date validationDate;
	private String metaPrefix;
	
	public RemoteMetaManager(SamlProperties configuration, UnityServerConfiguration mainConfig,
			ExecutorsService executorsService, PKIManagement pkiManagement,
			AbstractMetaToConfigConverter converter,
			MetaDownloadManager downloadManager, String metaPrefix)
	{
		this.configuration = configuration;
		this.executorsService = executorsService;
		this.converter = converter;
		this.remoteMetaProvider = new RemoteMetadataProvider(downloadManager);
		this.verificator = new MetadataVerificator();
		this.pkiManagement = pkiManagement;
		this.virtualConfiguration = configuration.clone();
		this.metaPrefix = metaPrefix;
	}

	public void start()
	{
		log.trace("Staring remote meta manager");
		long delay = getBaseConfiguration().getLongValue(SamlProperties.METADATA_REFRESH);
		executorsService.getService().scheduleWithFixedDelay(new Reloader(), 5, delay, TimeUnit.SECONDS);
	}
	
	public void reloadAll()
	{
		SamlProperties configuration = getBaseConfiguration();
		Set<String> keys = configuration.getStructuredListKeys(metaPrefix);
		Properties virtualConfigProps = configuration.getSourceProperties();
		for (String key: keys)
		{
			reloadSingle(key, virtualConfigProps, configuration);
		}
		setVirtualConfiguration(virtualConfigProps);
	}
	
	public synchronized SamlProperties getVirtualConfiguration()
	{
		return virtualConfiguration.clone();
	}

	private synchronized void setVirtualConfiguration(Properties virtualConfigurationProperties)
	{
		this.virtualConfiguration.setProperties(virtualConfigurationProperties);
	}
	
	public synchronized void setBaseConfiguration(SamlProperties configuration)
	{
		Properties oldP = this.configuration.getProperties();
		Properties newP = configuration.getProperties();
		boolean reload = !oldP.equals(newP);
		this.configuration = configuration;
		if (reload)
			executorsService.getService().schedule(new Reloader(), 500, TimeUnit.MILLISECONDS);
	}

	private synchronized SamlProperties getBaseConfiguration()
	{
		return configuration;
	}
	
	private void reloadSingle(String key, Properties virtualProps, SamlProperties configuration)
	{
		String url = configuration.getValue(key + SamlProperties.METADATA_URL);
		int refreshInterval = configuration.getIntValue(key + SamlProperties.METADATA_REFRESH);
		String customTruststore = configuration.getValue(key + SamlProperties.METADATA_HTTPS_TRUSTSTORE);
		EntitiesDescriptorDocument metadata;
		try
		{
			metadata = remoteMetaProvider.load(url, refreshInterval, customTruststore);
		} catch (XmlException e)
		{
			log.warn("Metadata from " + url + " was downloaded, but can not be parsed", e);
			return;
		} catch (IOException e)
		{
			log.warn("Problem fetching metadata from " + url, e);
			return;
		} catch (EngineException e)
		{
			log.error("Internal problem fetching metadata from " + url, e);
			return;
		}
		
		MetadataSignatureValidation sigCheckingMode = configuration.getEnumValue(
				key + SamlProperties.METADATA_SIGNATURE, MetadataSignatureValidation.class);
		String issuerCertificateName = configuration.getValue(key + SamlProperties.METADATA_ISSUER_CERT);
		
		try
		{
			X509Certificate issuerCertificate = issuerCertificateName != null ? 
					pkiManagement.getCertificate(issuerCertificateName) : null;
			verificator.validate(metadata, validationDate != null ? validationDate : new Date(),
					sigCheckingMode, issuerCertificate);
		} catch (MetadataValidationException e)
		{
			log.error("Metadata from " + url + " is invalid, won't be used", e);
			return;
		} catch (EngineException e)
		{
			log.error("Problem establishing certificate for metadata validation " + 
					issuerCertificateName, e);
			return;
		}
		
		converter.convertToProperties(metadata, virtualProps, configuration, key);
	}

	public void setValidationDate(Date validationDate)
	{
		this.validationDate = validationDate;
	}
	
	private class Reloader implements Runnable
	{		
		public void run()
		{
			try
			{
				reloadAll();
			} catch (Exception e)
			{
				log.error("Problem loading metadata of external saml 2 provider", e);
			}
		}
	}
}
