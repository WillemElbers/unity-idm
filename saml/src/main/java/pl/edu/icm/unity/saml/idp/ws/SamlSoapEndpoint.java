/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.saml.idp.ws;

import javax.servlet.Servlet;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import eu.unicore.samly2.SAMLConstants;
import eu.unicore.samly2.webservice.SAMLAuthnInterface;
import eu.unicore.samly2.webservice.SAMLQueryInterface;
import eu.unicore.util.configuration.ConfigurationException;
import pl.edu.icm.unity.saml.idp.SamlIdpProperties;
import pl.edu.icm.unity.saml.metadata.MetadataProvider;
import pl.edu.icm.unity.saml.metadata.MetadataProviderFactory;
import pl.edu.icm.unity.saml.metadata.MetadataServlet;
import pl.edu.icm.unity.server.api.AttributesManagement;
import pl.edu.icm.unity.server.api.IdentitiesManagement;
import pl.edu.icm.unity.server.api.PKIManagement;
import pl.edu.icm.unity.server.api.PreferencesManagement;
import pl.edu.icm.unity.server.utils.ExecutorsService;
import pl.edu.icm.unity.server.utils.UnityMessageSource;
import pl.edu.icm.unity.types.endpoint.EndpointTypeDescription;
import pl.edu.icm.unity.ws.CXFEndpoint;
import xmlbeans.org.oasis.saml2.metadata.EndpointType;

/**
 * Endpoint exposing SAML SOAP binding.
 * 
 * @author K. Benedyczak
 */
public class SamlSoapEndpoint extends CXFEndpoint
{
	protected SamlIdpProperties samlProperties;
	protected IdentitiesManagement identitiesMan;
	protected AttributesManagement attributesMan;
	protected PreferencesManagement preferencesMan;
	protected PKIManagement pkiManagement;
	protected ExecutorsService executorsService;
	protected String samlMetadataPath;
	
	public SamlSoapEndpoint(UnityMessageSource msg, EndpointTypeDescription type,
			String servletPath,  String metadataPath,
			IdentitiesManagement identitiesMan, AttributesManagement attributesMan,
			PreferencesManagement preferencesMan, PKIManagement pkiManagement, 
			ExecutorsService executorsService)
	{
		super(msg, type, servletPath);
		this.identitiesMan = identitiesMan;
		this.attributesMan = attributesMan;
		this.preferencesMan = preferencesMan;
		this.pkiManagement = pkiManagement;
		this.samlMetadataPath = metadataPath;
		this.executorsService = executorsService;
	}

	@Override
	public void setSerializedConfiguration(String config)
	{
		super.setSerializedConfiguration(config);
		try
		{
			samlProperties = new SamlIdpProperties(properties, pkiManagement);
		} catch (Exception e)
		{
			throw new ConfigurationException("Can't initialize the SAML SOAP" +
					" IdP endpoint's configuration", e);
		}
	}
	
	@Override
	public ServletContextHandler getServletContextHandler()
	{
		ServletContextHandler context = super.getServletContextHandler();
		
		String endpointURL = getServletUrl(servletPath);
		Servlet metadataServlet = getMetadataServlet(endpointURL);
		ServletHolder holder = new ServletHolder(metadataServlet);
		context.addServlet(holder, samlMetadataPath + "/*");
		
		return context;
	}
	
	@Override
	protected void configureServices()
	{
		String endpointURL = getServletUrl(servletPath);
		SAMLAssertionQueryImpl assertionQueryImpl = new SAMLAssertionQueryImpl(samlProperties, 
				endpointURL, attributesMan, identitiesMan, preferencesMan);
		addWebservice(SAMLQueryInterface.class, assertionQueryImpl);
		SAMLAuthnImpl authnImpl = new SAMLAuthnImpl(samlProperties, endpointURL, 
				identitiesMan, attributesMan, preferencesMan);
		addWebservice(SAMLAuthnInterface.class, authnImpl);		
	}
	
	protected Servlet getMetadataServlet(String samlEndpointURL)
	{
		EndpointType ssoSoap = EndpointType.Factory.newInstance();
		ssoSoap.setLocation(samlEndpointURL);
		ssoSoap.setBinding(SAMLConstants.BINDING_SOAP);
		EndpointType[] ssoEndpoints = new EndpointType[] {ssoSoap};

		EndpointType attributeSoap = EndpointType.Factory.newInstance();
		attributeSoap.setLocation(samlEndpointURL);
		attributeSoap.setBinding(SAMLConstants.BINDING_SOAP);
		EndpointType[] attributeQueryEndpoints = new EndpointType[] {attributeSoap};
		
		MetadataProvider provider = MetadataProviderFactory.newIdpInstance(samlProperties, 
				executorsService, ssoEndpoints, attributeQueryEndpoints);
		return new MetadataServlet(provider);
	}
}




