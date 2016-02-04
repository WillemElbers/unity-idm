/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.engine;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import pl.edu.icm.unity.types.I18nString;
import pl.edu.icm.unity.types.authn.AuthenticationOptionDescription;
import pl.edu.icm.unity.types.authn.AuthenticationRealm;
import pl.edu.icm.unity.types.endpoint.EndpointConfiguration;
import pl.edu.icm.unity.types.endpoint.EndpointDescription;
import pl.edu.icm.unity.types.endpoint.EndpointTypeDescription;

public class TestEndpoints extends DBIntegrationTestBase
{		
	@Test
	public void testEndpoints() throws Exception
	{
		AuthenticationRealm realm = new AuthenticationRealm("testr", "", 
				10, 10, -1, 600);
		realmsMan.addRealm(realm);
		
		List<EndpointTypeDescription> endpointTypes = endpointMan.getEndpointTypes();
		assertEquals(1, endpointTypes.size());
		EndpointTypeDescription type = endpointTypes.get(0);
		EndpointConfiguration cfg = new EndpointConfiguration(new I18nString("endpoint1"), 
				"desc", new ArrayList<AuthenticationOptionDescription>(), "",
				realm.getName());
		endpointMan.deploy(type.getName(), "endpoint1",	"/foo", cfg);
		List<EndpointDescription> endpoints = endpointMan.getEndpoints();
		assertEquals(1, endpoints.size());

		endpointMan.updateEndpoint(endpoints.get(0).getId(), new EndpointConfiguration(
				new I18nString("endpoint1I"), "ada", null, null, realm.getName()));
		endpoints = endpointMan.getEndpoints();
		assertEquals(1, endpoints.size());
		assertEquals("ada", endpoints.get(0).getDescription());
		assertEquals("endpoint1I", endpoints.get(0).getDisplayedName().getDefaultValue());

		endpointMan.undeploy(endpoints.get(0).getId());
		endpoints = endpointMan.getEndpoints();
		assertEquals(0, endpoints.size());

		
		//test initial loading from DB: create, remove from the server, load
		EndpointConfiguration cfg2 = new EndpointConfiguration(new I18nString("endpoint1"), 
				"desc", new ArrayList<AuthenticationOptionDescription>(), "", realm.getName());
		endpointMan.deploy(type.getName(), "endpoint1", "/foo", cfg2);
		EndpointConfiguration cfg3 = new EndpointConfiguration(new I18nString("endpoint2"), 
				"desc", new ArrayList<AuthenticationOptionDescription>(), "", realm.getName());
		endpointMan.deploy(type.getName(), "endpoint2", "/foo2", cfg3);
		endpoints = endpointMan.getEndpoints();
		assertEquals(2, endpoints.size());
		endpointMan.updateEndpoint(endpoints.get(0).getId(), new EndpointConfiguration(new I18nString("endp1"), 
				"endp1", null, null, realm.getName()));
		endpointMan.updateEndpoint(endpoints.get(1).getId(), new EndpointConfiguration(new I18nString("endp2"),
				"endp2", null, null, realm.getName()));

		internalEndpointMan.undeploy(endpoints.get(0).getId());
		internalEndpointMan.undeploy(endpoints.get(1).getId());

		endpoints = endpointMan.getEndpoints();
		assertEquals(0, endpoints.size());
		
		internalEndpointMan.loadPersistedEndpoints();
		
		endpoints = endpointMan.getEndpoints();
		assertEquals(2, endpoints.size());
		assertEquals("endp1", endpoints.get(0).getDescription());
		assertEquals("endp2", endpoints.get(1).getDescription());
		assertEquals("endp1", endpoints.get(0).getDisplayedName().getDefaultValue());
		assertEquals("endp2", endpoints.get(1).getDisplayedName().getDefaultValue());
		
		//finally test if removal from DB works
		internalEndpointMan.removeAllPersistedEndpoints();
		internalEndpointMan.loadPersistedEndpoints();
		endpoints = endpointMan.getEndpoints();
		assertEquals(0, endpoints.size());
	}
}
