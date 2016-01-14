/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import pl.edu.icm.unity.engine.authz.AuthorizationManagerImpl;
import pl.edu.icm.unity.engine.endpoints.InternalEndpointManagement;
import pl.edu.icm.unity.engine.mock.MockEndpoint;
import pl.edu.icm.unity.engine.mock.MockEndpointFactory;
import pl.edu.icm.unity.engine.mock.MockPasswordVerificatorFactory;
import pl.edu.icm.unity.exceptions.IllegalCredentialException;
import pl.edu.icm.unity.exceptions.IllegalPreviousCredentialException;
import pl.edu.icm.unity.stdext.credential.PasswordToken;
import pl.edu.icm.unity.stdext.identity.UsernameIdentity;
import pl.edu.icm.unity.stdext.identity.X500Identity;
import pl.edu.icm.unity.types.EntityState;
import pl.edu.icm.unity.types.I18nString;
import pl.edu.icm.unity.types.authn.AuthenticationRealm;
import pl.edu.icm.unity.types.authn.AuthenticatorInstance;
import pl.edu.icm.unity.types.authn.AuthenticationOptionDescription;
import pl.edu.icm.unity.types.authn.AuthenticatorTypeDescription;
import pl.edu.icm.unity.types.authn.CredentialDefinition;
import pl.edu.icm.unity.types.authn.CredentialRequirements;
import pl.edu.icm.unity.types.authn.CredentialType;
import pl.edu.icm.unity.types.authn.LocalCredentialState;
import pl.edu.icm.unity.types.basic.Entity;
import pl.edu.icm.unity.types.basic.EntityParam;
import pl.edu.icm.unity.types.basic.Identity;
import pl.edu.icm.unity.types.basic.IdentityParam;
import pl.edu.icm.unity.types.basic.IdentityTaV;
import pl.edu.icm.unity.types.endpoint.EndpointConfiguration;
import pl.edu.icm.unity.types.endpoint.EndpointDescription;
import pl.edu.icm.unity.types.endpoint.EndpointTypeDescription;

public class TestAuthentication extends DBIntegrationTestBase
{
	@Autowired
	private InternalEndpointManagement internalEndpointMan;
	
	@Test
	public void testAuthentication() throws Exception
	{
		//create credential requirement and an identity with it 
		super.setupMockAuthn();
		AuthenticationRealm realm = new AuthenticationRealm("testr", "", 
				10, 10, -1, 600);
		realmsMan.addRealm(realm);
		
		Identity id = idsMan.addEntity(new IdentityParam(X500Identity.ID, "CN=foo"), 
				"crMock", EntityState.valid, false);

		//create authenticator and an endpoint with it
		Collection<AuthenticatorTypeDescription> authTypes = authnMan.getAuthenticatorTypes("web");
		AuthenticatorTypeDescription authType = authTypes.iterator().next();
		authnMan.createAuthenticator("auth1", authType.getId(), "6", "bbb", "credential1");
		
		AuthenticationOptionDescription authSet = new AuthenticationOptionDescription("auth1");
		EndpointConfiguration cfg = new EndpointConfiguration(new I18nString("endpoint1"), "desc", 
				Collections.singletonList(authSet), "", realm.getName());
		endpointMan.deploy(MockEndpointFactory.NAME, "endpoint1", "/foo", cfg);

		//set wrong password 
		EntityParam entityP = new EntityParam(id);
		idsMan.setEntityCredential(entityP, "credential1", "password");
		
		MockEndpoint endpoint = (MockEndpoint) internalEndpointMan.getDeployedEndpoints().iterator().next();
		try
		{
			endpoint.authenticate();
			fail("Authn with wrong cred succeeded");
		} catch (IllegalCredentialException e) {}
		
		//set correct password 
		idsMan.setEntityCredential(entityP, "credential1", "bar");
		long entityId = endpoint.authenticate();
		Entity entity = idsMan.getEntity(entityP);
		assertEquals(entityId, entity.getId().longValue());
	}
	
	@Test
	public void testAuthnManagement() throws Exception
	{
		//create credential definition
		super.setupMockAuthn();
		AuthenticationRealm realm = new AuthenticationRealm("testr", "", 
				10, 10, -1, 600);
		realmsMan.addRealm(realm);

		//get authn types
		Collection<AuthenticatorTypeDescription> authTypes = authnMan.getAuthenticatorTypes("web");
		assertEquals(1, authTypes.size());
		authTypes = authnMan.getAuthenticatorTypes(null);
		assertEquals(1, authTypes.size());
		AuthenticatorTypeDescription authType = authTypes.iterator().next();
		assertEquals(true, authType.isLocal());
		assertEquals("mockretrieval", authType.getRetrievalMethod());
		assertEquals(MockPasswordVerificatorFactory.ID, authType.getVerificationMethod());
		assertEquals("web", authType.getSupportedBinding());
		
		//create authenticator
		AuthenticatorInstance authInstance = authnMan.createAuthenticator(
				"auth1", authType.getId(), "8", "bbb", "credential1");

		//get authenticators
		Collection<AuthenticatorInstance> auths = authnMan.getAuthenticators("web");
		assertEquals(1, auths.size());
		AuthenticatorInstance authInstanceR = auths.iterator().next();
		assertEquals("auth1", authInstanceR.getId());
		assertEquals("bbb", authInstanceR.getRetrievalJsonConfiguration());
		assertNull(authInstanceR.getVerificatorJsonConfiguration());
		
		//update authenticator
		authnMan.updateAuthenticator("auth1", "9", "b", "credential1");

		auths = authnMan.getAuthenticators("web");
		assertEquals(1, auths.size());
		authInstanceR = auths.iterator().next();
		assertEquals("auth1", authInstanceR.getId());
		assertEquals("b", authInstanceR.getRetrievalJsonConfiguration());
		assertNull(authInstanceR.getVerificatorJsonConfiguration());
		
		//create endpoint
		List<EndpointTypeDescription> endpointTypes = endpointMan.getEndpointTypes();
		assertEquals(1, endpointTypes.size());
		EndpointTypeDescription type = endpointTypes.get(0);
		
		EndpointConfiguration cfg = new EndpointConfiguration(new I18nString("endpoint1"),
				"desc", new ArrayList<AuthenticationOptionDescription>(), "", realm.getName());
		endpointMan.deploy(type.getName(), "endpoint1", "/foo", cfg);
		List<EndpointDescription> endpoints = endpointMan.getEndpoints();
		assertEquals(1, endpoints.size());

		//and assign the authenticator to it
		AuthenticationOptionDescription authSet = new AuthenticationOptionDescription("auth1");
		endpointMan.updateEndpoint(endpoints.get(0).getId(), new EndpointConfiguration(new I18nString("ada"), 
				"ada", Collections.singletonList(authSet), "", realm.getName()));

		//check if is returned
		List<AuthenticationOptionDescription> authSets = endpointMan.getEndpoints().get(0).getAuthenticatorSets();
		assertEquals(1, authSets.size());
		assertEquals("auth1", authSets.get(0).getPrimaryAuthenticator());
		
		//remove a used authenticator
		try
		{
			authnMan.removeAuthenticator(authInstance.getId());
			fail("Was able to remove a used authenticator");
		} catch (IllegalArgumentException e) {}
		
		//remove it from endpoint
		endpointMan.updateEndpoint(endpoints.get(0).getId(), new EndpointConfiguration(new I18nString("ada"), 
				"ada", new ArrayList<AuthenticationOptionDescription>(), "",
				realm.getName()));
		
		//remove again
		authnMan.removeAuthenticator(authInstance.getId());
		auths = authnMan.getAuthenticators(null);
		assertEquals(0, auths.size());		
	}
	
	@Test
	public void testCredentialsManagement() throws Exception
	{
		int automaticCredTypes = 2;
		int automaticCreds = 1;
		int automaticCredReqs = 1;
		//check if credential types are returned
		Collection<CredentialType> credTypes = authnMan.getCredentialTypes();
		assertEquals(credTypes.toString(), 1+automaticCredTypes, credTypes.size());
		CredentialType credType = getDescObjectByName(credTypes, MockPasswordVerificatorFactory.ID);
		assertEquals(MockPasswordVerificatorFactory.ID, credType.getName());
		
		//add credential definition
		CredentialDefinition credDef = new CredentialDefinition(
				MockPasswordVerificatorFactory.ID, "credential1", 
				new I18nString("cred disp name"),
				new I18nString("cred req desc"));
		credDef.setJsonConfiguration("8");
		authnMan.addCredentialDefinition(credDef);
		
		//check if is correctly returned
		Collection<CredentialDefinition> credDefs = authnMan.getCredentialDefinitions();
		assertEquals(1+automaticCreds, credDefs.size());
		CredentialDefinition credDefRet = getDescObjectByName(credDefs, "credential1");
		assertEquals("credential1", credDefRet.getName());
		assertEquals(new I18nString("cred req desc"), credDefRet.getDescription());
		assertEquals(MockPasswordVerificatorFactory.ID, credDefRet.getTypeId());
		assertEquals("8", credDefRet.getJsonConfiguration());
		
		//update it and check
		credDefRet.setDescription(new I18nString("d2"));
		credDefRet.setJsonConfiguration("9");
		authnMan.updateCredentialDefinition(credDefRet, LocalCredentialState.correct);
		credDefs = authnMan.getCredentialDefinitions();
		assertEquals(1+automaticCreds, credDefs.size());
		credDefRet = getDescObjectByName(credDefs, "credential1");
		assertEquals("credential1", credDefRet.getName());
		assertEquals("d2", credDefRet.getDescription().getDefaultValue());
		assertEquals(MockPasswordVerificatorFactory.ID, credDefRet.getTypeId());
		assertEquals("9", credDefRet.getJsonConfiguration());
		
		//remove
		authnMan.removeCredentialDefinition("credential1");
		credDefs = authnMan.getCredentialDefinitions();
		assertEquals(automaticCreds, credDefs.size());

		//add it again
		authnMan.addCredentialDefinition(credDef);

		//add authenticator with it and try to remove
		Collection<AuthenticatorTypeDescription> authTypes = authnMan.getAuthenticatorTypes("web");
		AuthenticatorTypeDescription authType = authTypes.iterator().next();
		AuthenticatorInstance authInstance = authnMan.createAuthenticator(
				"auth1", authType.getId(), "6", "bbb", "credential1");
		try
		{
			authnMan.removeCredentialDefinition("credential1");
			fail("Managed to remove credential used by authenticator");
		} catch (IllegalCredentialException e) {}
		authnMan.removeAuthenticator(authInstance.getId());
		
		
		//add credential requirements
		CredentialRequirements cr = new CredentialRequirements("crMock", "mock cred req", 
				Collections.singleton(credDef.getName()));
		authnMan.addCredentialRequirement(cr);
		
		//check if are correctly returned
		Collection<CredentialRequirements> credReqs = authnMan.getCredentialRequirements();
		assertEquals(1+automaticCredReqs, credReqs.size());
		CredentialRequirements credReq1 = getDescObjectByName(credReqs, "crMock");
		assertEquals("crMock", credReq1.getName());
		assertEquals("mock cred req", credReq1.getDescription());
		assertEquals(1, credReq1.getRequiredCredentials().size());
		
		//update credential requirements and check
		credReq1.setDescription("changed");
		authnMan.updateCredentialRequirement(credReq1);
		credReqs = authnMan.getCredentialRequirements();
		assertEquals(1+automaticCredReqs, credReqs.size());
		credReq1 = getDescObjectByName(credReqs, "crMock");
		assertEquals("crMock", credReq1.getName());
		assertEquals("changed", credReq1.getDescription());
		
		//try to remove credential - now with cred req
		try
		{
			authnMan.removeCredentialDefinition("credential1");
			fail("Managed to remove credential used by cred req");
		} catch (IllegalCredentialException e) {}
		
		//add identity with cred requirements with notSet state
		Identity id = idsMan.addEntity(new IdentityParam(X500Identity.ID, "CN=test"), 
				"crMock", EntityState.valid, false);
		EntityParam entityP = new EntityParam(id);
		Entity entity = idsMan.getEntity(entityP);
		assertEquals(LocalCredentialState.notSet, entity.getCredentialInfo().
				getCredentialsState().get("credential1").getState());
		
		//set entity credential and check if status notSet was changed to valid
		idsMan.setEntityCredential(entityP, "credential1", "password");
		entity = idsMan.getEntity(entityP);
		assertEquals(LocalCredentialState.correct, entity.getCredentialInfo().getCredentialsState().
				get("credential1").getState());

		//update credential requirements and check if the entity has its authN status still fine
		credReq1.setDescription("changed2");
		authnMan.updateCredentialRequirement(credReq1);
		entity = idsMan.getEntity(entityP);
		assertEquals(LocalCredentialState.correct, entity.getCredentialInfo().getCredentialsState().
				get("credential1").getState());

		//update credential definition now with identity using it via credential requirements
		credDefRet.setDescription(new I18nString("d3"));
		credDefRet.setJsonConfiguration("119");
		authnMan.updateCredentialDefinition(credDefRet, LocalCredentialState.correct);
		entity = idsMan.getEntity(entityP);
		assertEquals(LocalCredentialState.correct, entity.getCredentialInfo().getCredentialsState().
				get("credential1").getState());
		
		try
		{
			authnMan.removeCredentialRequirement(credReq1.getName(), null);
			fail("Managed to remove used requirements without replacement");
		} catch (IllegalCredentialException e) {}

		CredentialDefinition credDef2 = new CredentialDefinition(
				MockPasswordVerificatorFactory.ID, "credential2");
		credDef2.setJsonConfiguration("10");
		authnMan.addCredentialDefinition(credDef2);
		
		Set<String> set2 = new HashSet<String>();
		Collections.addAll(set2, credDef.getName(), credDef2.getName());
		authnMan.addCredentialRequirement(new CredentialRequirements("crMock2", "mock cred req2", 
				set2));
		
		idsMan.setEntityCredentialRequirements(entityP, "crMock2");
		
		entity = idsMan.getEntity(entityP);
		assertEquals(LocalCredentialState.correct, entity.getCredentialInfo().getCredentialsState().
				get("credential1").getState());
		assertEquals(LocalCredentialState.notSet, entity.getCredentialInfo().getCredentialsState().
				get("credential2").getState());
		idsMan.setEntityCredential(entityP, "credential2", "password2");
		entity = idsMan.getEntity(entityP);
		assertEquals(LocalCredentialState.correct, entity.getCredentialInfo().getCredentialsState().
				get("credential1").getState());
		assertEquals(LocalCredentialState.correct, entity.getCredentialInfo().getCredentialsState().
				get("credential2").getState());
		
		authnMan.removeCredentialRequirement("crMock2", "crMock");
		credReqs = authnMan.getCredentialRequirements();
		assertEquals(1+automaticCredReqs, credReqs.size());
		entity = idsMan.getEntity(entityP);
		assertEquals(LocalCredentialState.correct, entity.getCredentialInfo().getCredentialsState().
				get("credential1").getState());
	}
	
	@Test
	public void isAdminAllowedToChangeCredential() throws Exception
	{
		setupAdmin();
		setupPasswordAuthn();
		createUsernameUser(AuthorizationManagerImpl.USER_ROLE);
		EntityParam user = new EntityParam(new IdentityTaV(UsernameIdentity.ID, "user1")); 
		assertFalse(idsMan.isCurrentCredentialRequiredForChange(user, "credential1"));
		idsMan.setEntityCredential(user, "credential1", new PasswordToken("qw!Erty").toJson());
	}
	
	@Test
	public void isUserRequiredToProvideCurrentCredentialUponChange() throws Exception
	{
		setupPasswordAuthn();
		setupPasswordAndCertAuthn();
		createCertUserNoPassword(AuthorizationManagerImpl.USER_ROLE); //Has no password set, but password is allowed
		setupUserContext("user2", false);
		
		EntityParam user = new EntityParam(new IdentityTaV(UsernameIdentity.ID, "user2")); 
		assertFalse(idsMan.isCurrentCredentialRequiredForChange(user, "credential1"));
		idsMan.setEntityCredential(user, "credential1", new PasswordToken("qw!Erty").toJson());
		try
		{
			idsMan.setEntityCredential(user, "credential1", new PasswordToken("qw!Erty2").toJson());
			fail("Managed to change the password without giving the old one");
		} catch (IllegalPreviousCredentialException e)
		{
			//OK - expected
		}
		try
		{
			idsMan.setEntityCredential(user, "credential1", new PasswordToken("qw!Erty2").toJson(),
					new PasswordToken("INVALID").toJson());
			fail("Managed to change the password with invalid old one");
		} catch (IllegalPreviousCredentialException e)
		{
			//OK - expected
		}
		idsMan.setEntityCredential(user, "credential1", new PasswordToken("qw!Erty2").toJson(),
				new PasswordToken("qw!Erty").toJson());
	}
}
