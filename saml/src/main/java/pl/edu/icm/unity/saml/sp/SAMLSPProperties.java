/*
 * Copyright (c) 2014 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.saml.sp;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;

import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.saml.SamlProperties;
import pl.edu.icm.unity.saml.ecp.SAMLECPProperties;
import pl.edu.icm.unity.server.api.PKIManagement;
import pl.edu.icm.unity.server.utils.Log;
import eu.emi.security.authn.x509.X509Credential;
import eu.unicore.samly2.SAMLBindings;
import eu.unicore.samly2.SAMLConstants;
import eu.unicore.samly2.trust.SamlTrustChecker;
import eu.unicore.samly2.trust.StrictSamlTrustChecker;
import eu.unicore.util.configuration.ConfigurationException;
import eu.unicore.util.configuration.DocumentationReferenceMeta;
import eu.unicore.util.configuration.DocumentationReferencePrefix;
import eu.unicore.util.configuration.PropertyMD;
import eu.unicore.util.configuration.PropertyMD.DocumentationCategory;

/**
 * Configuration of a SAML requester (or SAML SP).
 * @author K. Benedyczak
 */
public class SAMLSPProperties extends SamlProperties
{
	private static final Logger log = Log.getLogger(Log.U_SERVER_CFG, SAMLSPProperties.class);
	
	/**
	 * Note: it is intended that {@link SAMLBindings} is not used here: we want to have only the 
	 * supported bindings here. However the names here must be exactly the same as in {@link SAMLBindings}.
	 */
	public enum Binding {HTTP_REDIRECT, HTTP_POST};
	
	@DocumentationReferencePrefix
	public static final String P = "unity.saml.requester.";
	
	@DocumentationReferenceMeta
	public final static Map<String, PropertyMD> META = new HashMap<String, PropertyMD>();
	
	public static final String REQUESTER_ID = "requesterEntityId";
	public static final String CREDENTIAL = "requesterCredential";
	public static final String ACCEPTED_NAME_FORMATS = "acceptedNameFormats.";
	public static final String DISPLAY_NAME = "displayName";
	public static final String METADATA_PATH = "metadataPath";
	
	public static final String DEF_SIGN_REQUEST = "defaultSignRequest";
	public static final String DEF_REQUESTED_NAME_FORMAT = "defaultRequestedNameFormat";
	
	public static final String IDP_PREFIX = "remoteIdp.";
	public static final String IDP_NAME = "name";
	public static final String IDP_ID = "samlId";
	public static final String IDP_ADDRESS = "address";
	public static final String IDP_BINDING = "binding";
	public static final String IDP_CERTIFICATE = "certificate";
	public static final String IDP_SIGN_REQUEST = "signRequest";
	public static final String IDP_REQUESTED_NAME_FORMAT = "requestedNameFormat";
	public static final String IDP_GROUP_MEMBERSHIP_ATTRIBUTE = "groupMembershipAttribute";
	public static final String IDP_TRANSLATION_PROFILE = "translationProfile";
	public static final String IDP_REGISTRATION_FORM = "registrationFormForUnknown";
	
	static
	{
		DocumentationCategory common = new DocumentationCategory(
				"Common settings", "01");
		DocumentationCategory verificator = new DocumentationCategory(
				"SAML validator specific settings", "02");
		DocumentationCategory webRetrieval = new DocumentationCategory(
				"SAML web retrieval specific settings", "03");

		META.put(IDP_PREFIX, new PropertyMD().setStructuredList(true).setMandatory().setCategory(common).setDescription(
				"With this prefix configuration of trusted and enabled remote SAML IdPs is stored. " +
				"There must be at least one IdP defined. If there are multiple ones defined, then the user can choose which one to use."));
		META.put(IDP_ADDRESS, new PropertyMD().setStructuredListEntry(IDP_PREFIX).setMandatory().setCategory(common).setDescription(
				"Address of the IdP endpoint."));
		META.put(IDP_BINDING, new PropertyMD(Binding.HTTP_REDIRECT).setStructuredListEntry(IDP_PREFIX).setCategory(common).setDescription(
				"SAML binding to be used to send a request to the IdP."));
		META.put(IDP_NAME, new PropertyMD().setStructuredListEntry(IDP_PREFIX).setCategory(common).setDescription(
				"Displayed name of the IdP. If not defined then the name is created " +
				"from the IdP address (what is rather not user friendly)."));
		META.put(IDP_ID, new PropertyMD().setStructuredListEntry(IDP_PREFIX).setMandatory().setCategory(common).setDescription(
				"SAML entity identifier of the IdP."));
		META.put(IDP_CERTIFICATE, new PropertyMD().setStructuredListEntry(IDP_PREFIX).setMandatory().setCategory(common).setDescription(
				"Certificate name (as used in centralized PKI store) of the IdP. This certificate is used to verify signature of SAML " +
				"response and included assertions. Therefore it is of highest importance for the whole system security."));
		META.put(IDP_SIGN_REQUEST, new PropertyMD("false").setCategory(common).setStructuredListEntry(IDP_PREFIX).setDescription(
				"Controls whether the requests for this IdP should be signed."));
		META.put(IDP_REQUESTED_NAME_FORMAT, new PropertyMD().setCategory(common).setStructuredListEntry(IDP_PREFIX).setDescription(
				"If defined then specifies what SAML name format should be requested from the IdP." +
				" If undefined then IdP is free to choose, however see the " + ACCEPTED_NAME_FORMATS +
				" property. Value is arbitrary string, meaningful for the IdP. SAML specifies several standard formats:" +
				" +urn:oasis:names:tc:SAML:2.0:nameid-format:persistent+," +
				" +urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress+," +
				" +urn:oasis:names:tc:SAML:1.1:nameid-format:X509SubjectName+ and " +
				" +urn:oasis:names:tc:SAML:2.0:nameid-format:transient+ are the most popular."));
		META.put(IDP_GROUP_MEMBERSHIP_ATTRIBUTE, new PropertyMD().setCategory(common).setStructuredListEntry(IDP_PREFIX).setDescription(
				"Defines a SAML attribute name which will be treated as an attribute carrying group" +
				" membership information."));
		META.put(IDP_TRANSLATION_PROFILE, new PropertyMD().setMandatory().setCategory(common).setStructuredListEntry(IDP_PREFIX).
				setDescription("Name of a translation" +
				" profile, which will be used to map remotely obtained attributes and identity" +
				" to the local counterparts. The profile should at least map the remote identity."));
		META.put(IDP_REGISTRATION_FORM, new PropertyMD().setCategory(common).setStructuredListEntry(IDP_PREFIX).setDescription(
				"Name of a registration form to be shown for a remotely authenticated principal who " +
				"has no local account. If unset such users will be denied."));
		
		META.put(REQUESTER_ID, new PropertyMD().setMandatory().setCategory(verificator).setDescription(
				"SAML entity ID (must be a URI) of the lcoal SAML requester (or service provider)."));
		META.put(CREDENTIAL, new PropertyMD().setCategory(verificator).setDescription(
				"Local credential, used to sign requests. If signing is disabled it is not used."));
		META.put(METADATA_PATH, new PropertyMD().setCategory(SamlProperties.samlCat).setDescription(
				"Last element of the URL, under which the SAML metadata should be published for this SAML authenticator." +
				"Used only if metadata publication is enabled. See the SAML Metadata section for more details."));
		META.put(ACCEPTED_NAME_FORMATS, new PropertyMD().setList(false).setCategory(verificator).setDescription(
				"If defined then specifies what SAML name formatd are accepted from IdP. " +
				"Useful when the property " + IDP_REQUESTED_NAME_FORMAT + " is undefined for at least one IdP. "));

		META.put(DEF_SIGN_REQUEST, new PropertyMD("false").setCategory(common).setDescription(
				"Default setting of request signing. Used for those IdPs, for which the setting is not set explicitly."));
		META.put(DEF_REQUESTED_NAME_FORMAT, new PropertyMD().setCategory(common).setDescription(
				"Default setting of requested identity format. Used for those IdPs, for which the setting is not set explicitly."));

		
		META.put(DISPLAY_NAME, new PropertyMD("SAML authentication").setCategory(webRetrieval).setDescription(
				"Name of the SAML authentication GUI component"));
		
		META.put(SAMLECPProperties.JWT_P, new PropertyMD().setCanHaveSubkeys().setHidden());
		
		META.putAll(SamlProperties.defaults);
	}
	
	private PKIManagement pkiManagement;
	
	public SAMLSPProperties(Properties properties, PKIManagement pkiMan) throws ConfigurationException
	{
		super(P, properties, META, log);
		this.pkiManagement = pkiMan;
		Set<String> idpKeys = getStructuredListKeys(IDP_PREFIX);
		boolean sign = false;
		for (String idpKey: idpKeys)
		{
			boolean s = isSignRequest(idpKey);  
			sign |= s;
			if (s && getEnumValue(idpKey+IDP_BINDING, Binding.class) == Binding.HTTP_REDIRECT)
			{
				String name = getValue(idpKey+IDP_NAME);  
				throw new ConfigurationException("IdP " + name + " is configured to use " +
						"HTTP Redirect binding and sign requests. This is unsupported " +
						"currently and against SAML interoperability specification.");
			}
			
		}
		if (sign)
		{
			String credential = getValue(CREDENTIAL);
			if (credential == null)
				throw new ConfigurationException("Credential must be defined when " +
						"request signing is enabled for at least one IdP.");
			try
			{
				if (!pkiMan.getCredentialNames().contains(credential))
					throw new ConfigurationException("Credential name is invalid - there is no such " +
							"credential available '" + credential + "'.");
			} catch (EngineException e)
			{
				throw new ConfigurationException("Can't esablish a list of known credentials", e);
			}
		}
		//test drive
		getTrustChecker();
		
		if (getBooleanValue(PUBLISH_METADATA) && !isSet(METADATA_PATH))
			throw new ConfigurationException("Metadata path " + getKeyDescription(METADATA_PATH) + 
					" must be set if metadata publication is enabled.");
	}
	
	public X509Credential getRequesterCredential()
	{
		String credential = getValue(SAMLSPProperties.CREDENTIAL);
		if (credential == null)
			return null;
		try
		{
			return pkiManagement.getCredential(credential);
		} catch (EngineException e)
		{
			return null;
		}
	}
	
	public SamlTrustChecker getTrustChecker() throws ConfigurationException
	{
		Set<String> idpKeys = getStructuredListKeys(IDP_PREFIX);
		StrictSamlTrustChecker trustChecker = new StrictSamlTrustChecker();
		for (String idpKey: idpKeys)
		{
			String idpId = getValue(idpKey+IDP_ID);
			String idpCertName = getValue(idpKey+IDP_CERTIFICATE);
			X509Certificate idpCert;
			try
			{
				idpCert = pkiManagement.getCertificate(idpCertName);
			} catch (EngineException e)
			{
				throw new ConfigurationException("Remote SAML IdP certificate can not be loaded " 
						+ idpCertName, e);
			}
			trustChecker.addTrustedIssuer(idpId, SAMLConstants.NFORMAT_ENTITY, idpCert.getPublicKey());
		}
		return trustChecker;
	}
	
	public boolean isSignRequest(String idpKey)
	{
		return isSet(idpKey + IDP_SIGN_REQUEST) ? 
				getBooleanValue(idpKey + IDP_SIGN_REQUEST) : 
				getBooleanValue(idpKey + DEF_SIGN_REQUEST);
	}
	
	public String getRequestedNameFormat(String idpKey)
	{
		return isSet(idpKey + IDP_REQUESTED_NAME_FORMAT) ? 
				getValue(idpKey + IDP_REQUESTED_NAME_FORMAT) : 
				getValue(idpKey + DEF_REQUESTED_NAME_FORMAT);
	}
}
