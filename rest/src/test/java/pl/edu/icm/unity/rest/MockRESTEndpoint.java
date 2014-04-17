/*
 * Copyright (c) 2014 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.rest;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;

import pl.edu.icm.unity.types.endpoint.EndpointTypeDescription;

public class MockRESTEndpoint extends RESTEndpoint
{
	public MockRESTEndpoint(EndpointTypeDescription type, String servletPath)
	{
		super(type, servletPath);
	}


	@Override
	protected Application getApplication()
	{
		return new JAXRSProvider();
	}

	@ApplicationPath("/test")
	private static class JAXRSProvider extends Application
	{
		@Override 
		public Set<Object> getSingletons() 
		{
			HashSet<Object> ret = new HashSet<>();
			ret.add(new MockResource());
			return ret;
		}
	}
	
	@Path("/r1/")
	@Produces("text/plain")
	private static class MockResource
	{
		@GET
		public String getDate() 
		{
			return new Date().toString();
		}
	}
}
