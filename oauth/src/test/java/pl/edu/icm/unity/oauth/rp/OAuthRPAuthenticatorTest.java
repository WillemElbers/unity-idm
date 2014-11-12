/*
 * Copyright (c) 2014 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.oauth.rp;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import pl.edu.icm.unity.engine.DBIntegrationTestBase;
import pl.edu.icm.unity.oauth.as.OAuthTestUtils;
import pl.edu.icm.unity.oauth.as.token.OAuthTokenEndpointFactory;
import pl.edu.icm.unity.oauth.client.CustomHTTPSRequest;
import pl.edu.icm.unity.rest.jwt.endpoint.JWTManagementEndpointFactory;
import pl.edu.icm.unity.server.api.TranslationProfileManagement;
import pl.edu.icm.unity.server.api.internal.TokensManagement;
import pl.edu.icm.unity.server.registries.TranslationActionsRegistry;
import pl.edu.icm.unity.server.translation.in.InputTranslationProfile;
import pl.edu.icm.unity.stdext.identity.UsernameIdentity;
import pl.edu.icm.unity.types.EntityState;
import pl.edu.icm.unity.types.authn.AuthenticationRealm;
import pl.edu.icm.unity.types.authn.AuthenticatorSet;
import pl.edu.icm.unity.types.basic.IdentityParam;
import pl.edu.icm.unity.types.endpoint.EndpointDescription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.oauth2.sdk.AuthorizationSuccessResponse;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPRequest.Method;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.token.AccessToken;

import eu.emi.security.authn.x509.helpers.BinaryCertChainValidator;
import eu.unicore.util.httpclient.ServerHostnameCheckingMode;

/**
 * Somewhat complex integration test of the OAuth RP authenticator.
 * 
 * Unity JWTManagement endpoint (as an example) is deployed with the tested OAuth RP authenticator.
 * Unity OAuth endpoint is deployed with password authN.  
 * 
 * First an access token is generated and recorded in internal tokens store. Then this token is used to access the 
 * JWT management endpoint. The authenticator is configured to check it against the Unity AS endpoint. 
 * @author K. Benedyczak
 */
public class OAuthRPAuthenticatorTest extends DBIntegrationTestBase
{
	private static final String OAUTH_ENDP_CFG = 
			"unity.oauth2.as.issuerUri=https://localhost:52443/oauth2\n"
			+ "unity.oauth2.as.signingCredential=MAIN\n"
			+ "unity.oauth2.as.clientsGroup=/oauth-clients\n"
			+ "unity.oauth2.as.usersGroup=/oauth-users\n"
			+ "#unity.oauth2.as.translationProfile=\n"
			+ "unity.oauth2.as.scopes.1.name=foo\n"
			+ "unity.oauth2.as.scopes.1.description=Provides access to foo info\n"
			+ "unity.oauth2.as.scopes.1.attributes.1=stringA\n"
			+ "unity.oauth2.as.scopes.1.attributes.2=o\n"
			+ "unity.oauth2.as.scopes.1.attributes.3=email\n"
			+ "unity.oauth2.as.scopes.2.name=bar\n"
			+ "unity.oauth2.as.scopes.2.description=Provides access to bar info\n"
			+ "unity.oauth2.as.scopes.2.attributes.1=c\n";

	private static final String OAUTH_RP_CFG = 
			"unity.oauth2-rp.profileEndpoint=https://localhost:52443/oauth/userinfo\n"
			+ "unity.oauth2-rp.cacheTime=20\n"
			+ "unity.oauth2-rp.verificationProtocol=unity\n"
			+ "unity.oauth2-rp.verificationEndpoint=https://localhost:52443/oauth/tokeninfo\n"
			+ "unity.oauth2-rp.clientId=\n"
			+ "unity.oauth2-rp.clientSecret=\n"
			+ "#unity.oauth2-rp.clientAuthenticationMode=\n"
			+ "unity.oauth2-rp.opeinidConnectMode=false\n"
			+ "unity.oauth2-rp.httpClientTruststore=MAIN\n"
			+ "unity.oauth2-rp.httpClientHostnameChecking=NONE\n"
			+ "unity.oauth2-rp.translationProfile=tr-oauth\n";

	private static final String JWT_ENDP_CFG = 
			"unity.jwtauthn.credential=MAIN\n";

	
	public static final String REALM_NAME = "testr";
	
	
	@Autowired
	private TokensManagement tokensMan;
	@Autowired
	private TranslationProfileManagement profilesMan;
	@Autowired
	private TranslationActionsRegistry trActionReg;
	
	@Before
	public void setup()
	{
		try
		{
			setupPasswordAuthn();
			
			authnMan.createAuthenticator("a-rp", "oauth-rp with rest-oauth-bearer", OAUTH_RP_CFG, 
					"", null);
			authnMan.createAuthenticator("Apass", "password with rest-httpbasic", null, "", "credential1");
			
			idsMan.addEntity(new IdentityParam(UsernameIdentity.ID, "userA"), 
					"cr-pass", EntityState.valid, false);
			
			profilesMan.addProfile(new InputTranslationProfile(
					FileUtils.readFileToString(new File("src/test/resources/tr-local.json")), 
					new ObjectMapper(), trActionReg));
			
			AuthenticationRealm realm = new AuthenticationRealm(REALM_NAME, "", 
					10, 100, -1, 600);
			realmsMan.addRealm(realm);
			List<AuthenticatorSet> authnCfg = new ArrayList<AuthenticatorSet>();
			authnCfg.add(new AuthenticatorSet(Collections.singleton("Apass")));
			endpointMan.deploy(OAuthTokenEndpointFactory.NAME, "endpointIDP", "/oauth", "desc", 
					authnCfg, OAUTH_ENDP_CFG, REALM_NAME);
			
			List<AuthenticatorSet> authnCfg2 = new ArrayList<AuthenticatorSet>();
			authnCfg2.add(new AuthenticatorSet(Collections.singleton("a-rp")));
			endpointMan.deploy(JWTManagementEndpointFactory.NAME, "endpointJWT", "/jwt", "desc", 
					authnCfg2, JWT_ENDP_CFG, REALM_NAME);
			
			List<EndpointDescription> endpoints = endpointMan.getEndpoints();
			assertEquals(2, endpoints.size());

			httpServer.start();
		} catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
	

	@Test
	public void OauthRPAuthnWorksWithUnity() throws Exception
	{
		AuthorizationSuccessResponse resp1 = OAuthTestUtils.initOAuthFlowHybrid(tokensMan);
		AccessToken ac = resp1.getAccessToken();
		
		HTTPRequest httpReqRaw = new HTTPRequest(Method.GET, new URL("https://localhost:52443/jwt/token"));
		httpReqRaw.setAuthorization(ac.toAuthorizationHeader());
		HTTPRequest httpReq = new CustomHTTPSRequest(httpReqRaw, new BinaryCertChainValidator(true), 
				ServerHostnameCheckingMode.NONE);
		HTTPResponse response = httpReq.send();
		Assert.assertEquals(200, response.getStatusCode());
	}
	
}
