/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.ws;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jws.WebService;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.servlet.CXFNonSpringServlet;
import org.apache.cxf.xmlbeans.XmlBeansDataBinding;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import pl.edu.icm.unity.server.endpoint.AbstractEndpoint;
import pl.edu.icm.unity.server.endpoint.BindingAuthn;
import pl.edu.icm.unity.server.endpoint.WebAppEndpointInstance;
import pl.edu.icm.unity.server.utils.UnityMessageSource;
import pl.edu.icm.unity.types.endpoint.EndpointTypeDescription;
import pl.edu.icm.unity.ws.authn.AuthenticationInterceptor;
import pl.edu.icm.unity.ws.authn.CXFAuthentication;

/**
 * Web service endpoint based on CXF
 * @author K. Benedyczak
 */
public abstract class CXFEndpoint extends AbstractEndpoint implements WebAppEndpointInstance
{
	protected UnityMessageSource msg;
	protected String servletPath;
	private Map<Class<?>, Object> services; 
	
	public CXFEndpoint(UnityMessageSource msg, EndpointTypeDescription type, String servletPath)
	{
		super(type);
		this.msg = msg;
		this.servletPath = servletPath;
		services = new HashMap<Class<?>, Object>();
	}

	@Override
	public String getSerializedConfiguration()
	{
		return "";
	}

	@Override
	protected void setSerializedConfiguration(String serializedState)
	{
	}

	protected void addWebservice(Class<?> iface, Object impl)
	{
		services.put(iface, impl);
	}
	
	private void deployWebservice(Bus bus, Class<?> iface, Object impl)
	{
		JaxWsServerFactoryBean factory=new JaxWsServerFactoryBean();
		factory.getServiceFactory().setDataBinding(new XmlBeansDataBinding());
		factory.setServiceBean(impl);
		factory.setServiceClass(impl.getClass());
		factory.setBus(bus);
		String name = iface.getAnnotation(WebService.class).name();
		factory.setAddress("/"+name);
		Server server = factory.create();
		Endpoint cxfEndpoint = server.getEndpoint();
		addInterceptors(cxfEndpoint.getInInterceptors(), cxfEndpoint.getOutInterceptors());
	}
	
	private void addInterceptors(List<Interceptor<? extends Message>> inInterceptors,
			List<Interceptor<? extends Message>> outInterceptors)
	{
		outInterceptors.add(new XmlBeansNsHackOutHandler());
		inInterceptors.add(new AuthenticationInterceptor(msg, authenticators));
		installAuthnInterceptors(inInterceptors);
	}
	
	protected abstract void configureServices();
	
	@Override
	public ServletContextHandler getServletContextHandler()
	{
		configureServices();
		
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath(description.getContextAddress());
		CXFNonSpringServlet cxfServlet = new CXFNonSpringServlet();
		Bus bus = BusFactory.newInstance().createBus();
		cxfServlet.setBus(bus);
		ServletHolder holder = new ServletHolder(cxfServlet);
		context.addServlet(holder, servletPath + "/*");
		
		for (Map.Entry<Class<?>, Object> service: services.entrySet())
			deployWebservice(bus, service.getKey(), service.getValue());
		
		return context;
	}

	private void installAuthnInterceptors(List<Interceptor<? extends Message>> interceptors)
	{
		Set<String> added = new HashSet<String>();
		for (Map<String, BindingAuthn> authenticatorSet: authenticators)
		{
			for (Map.Entry<String, BindingAuthn> authenticator: authenticatorSet.entrySet())
			{
				if (!added.contains(authenticator.getKey()))
				{
					CXFAuthentication a = (CXFAuthentication) authenticator.getValue();
					Interceptor<? extends Message> in = a.getInterceptor();
					if (in != null)
						interceptors.add(in);
					added.add(authenticator.getKey());
				}
			}
		}
	}
}