/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.saml.idp.web.filter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.bouncycastle.util.encoders.Base64;

import eu.unicore.samly2.SAMLConstants;
import eu.unicore.samly2.exceptions.SAMLServerException;

import pl.edu.icm.unity.Constants;
import pl.edu.icm.unity.saml.SAMLProcessingException;
import pl.edu.icm.unity.saml.idp.FreemarkerHandler;
import pl.edu.icm.unity.saml.idp.SamlProperties;
import pl.edu.icm.unity.saml.idp.ctx.SAMLAuthnContext;
import pl.edu.icm.unity.saml.idp.web.EopException;
import pl.edu.icm.unity.saml.validator.WebAuthRequestValidator;
import pl.edu.icm.unity.server.utils.Log;
import xmlbeans.org.oasis.saml2.protocol.AuthnRequestDocument;

/**
 * Filter which is invoked prior to authentication. 
 * It behaves in two modes:
 * <p>
 * If a request comes to the base path of the servlet, it is assumed it is a request with POST or GET SAML request.
 * The filter retrieves the SAML request, parses it, validates and if everything is correct proceeds stores it in 
 * the session and proceeds. In case of problems a SAML error is returned
 * to the requester or error page is displayed if the SAML requester can not be established (e.g. no request
 * or request can not be parsed). 
 * If a SAML Request is found in the HTTP request parameters and context is already set up, then a hold-on error 
 * page is displayed, to prevent simultaneous authentications. User may kill the previous authn session and it 
 * expires automatically after a configured amount of time.  
 * <p>
 * If a request comes to any other address then the base servlet path, then the filter checks if a SAML context 
 * is available in the session. If not - the request is banned and an error page displayed.
 * 
 * 
 * @author K. Benedyczak
 */
public class SamlParseFilter implements Filter
{
	private static final Logger log = Log.getLogger(Log.U_SERVER_SAML, SamlParseFilter.class);
	
	/**
	 * Under this key the SAMLContext object is stored in the session.
	 */
	public static final String SESSION_SAML_CONTEXT = "samlAuthnContextKey";
	
	/**
	 * key used by hold on form to mark that the new authn session should be started even 
	 * when an existing auth is in progress. 
	 */
	public static final String REQ_FORCE = "force";
	protected SamlProperties samlConfig;
	protected String endpointAddress;
	protected String samlConsumerPath;
	protected String samlServletPath;
	protected ErrorHandler errorHandler;
	
	public SamlParseFilter(SamlProperties samlConfig, FreemarkerHandler freemarker, String endpointAddress,
			String samlConsumerPath, String samlServletPath)
	{
		this.samlConfig = samlConfig;
		this.endpointAddress = endpointAddress;
		this.errorHandler = new ErrorHandler(freemarker);
		this.samlConsumerPath = samlConsumerPath;
		this.samlServletPath = samlServletPath;
	}

	@Override
	public void doFilter(ServletRequest requestBare, ServletResponse responseBare, FilterChain chain)
			throws IOException, ServletException
	{
		try
		{
			doFilterInterruptible(requestBare, responseBare, chain);
		} catch (EopException e)
		{
			//OK, that's fine, response was already committed
		}
	}
	
	protected void doFilterInterruptible(ServletRequest requestBare, ServletResponse responseBare, FilterChain chain)
			throws IOException, ServletException, EopException
	{
		if (!(requestBare instanceof HttpServletRequest))
			throw new ServletException("This filter can be used only for HTTP servlets");
		HttpServletRequest request = (HttpServletRequest) requestBare;
		HttpServletResponse response = (HttpServletResponse) responseBare;
		if (samlConsumerPath.equals(request.getServletPath()))
		{
			processSamlRequest(request, response, chain);
		} else
		{
			if (log.isTraceEnabled())
				log.trace("Request to not protected address: " + request.getRequestURI());
			chain.doFilter(request, response);
			return;
		}
		
	}

	protected void processSamlRequest(HttpServletRequest request, HttpServletResponse response, FilterChain chain) 
			throws IOException, ServletException, EopException
	{
		HttpSession session = request.getSession();
		SAMLAuthnContext context = (SAMLAuthnContext) session.getAttribute(SESSION_SAML_CONTEXT); 
		//is there processing in progress?
		if (context != null)
		{
			String samlRequest = request.getParameter(SAMLConstants.REQ_SAML_REQUEST);
			//do we have a new request?
			if (samlRequest == null)
			{
				if (log.isTraceEnabled())
					log.trace("Request to SAML endpoint address, without SAML input, error: " + 
							request.getRequestURI());
				errorHandler.showErrorPage(new SAMLProcessingException("No SAML request"), 
						(HttpServletResponse) response);
				return;
			}
			
			//ok, we do have a new request. 
			//We can have the old session expired or order to forcefully close it.
			String force = request.getParameter(REQ_FORCE);
			if ((force == null || force.equals("false")) && !context.isExpired())
			{
				if (log.isTraceEnabled())
					log.trace("Request to protected address, with SAML input and we have " +
							"SAML login in progress, redirecting to hold on page: " + 
							request.getRequestURI());
				errorHandler.showHoldOnPage(samlRequest, 
						request.getParameter(SAMLConstants.RELAY_STATE),
						request.getMethod(), response);
				return;
			} else
			{
				if (log.isTraceEnabled())
					log.trace("Request to protected address, with SAML input and we are " +
							"forced to break the previous SAML login: " + 
							request.getRequestURI());
				session.removeAttribute(SESSION_SAML_CONTEXT);
			}
		}
		
		if (log.isTraceEnabled())
			log.trace("Request to protected address, with SAML input, will be processed: " + 
					request.getRequestURI());
		try
		{
			AuthnRequestDocument samlRequest = parse(request);
			context = createSamlContext(request, samlRequest);
			validate(context, response);
		} catch (SAMLProcessingException e)
		{
			if (log.isDebugEnabled())
				log.debug("Processing of SAML input failed", e);
			errorHandler.showErrorPage(e, (HttpServletResponse) response);
			return;
		}
		
		session.setAttribute(SESSION_SAML_CONTEXT, context);
		if (log.isTraceEnabled())
			log.trace("Request with SAML input handled successfully");
		request.getRequestDispatcher(samlServletPath).forward(request, response);
	}
	
	protected SAMLAuthnContext createSamlContext(HttpServletRequest request, AuthnRequestDocument samlRequest)
	{
		SAMLAuthnContext ret = new SAMLAuthnContext(samlRequest, samlConfig);
		String rs = request.getParameter(SAMLConstants.RELAY_STATE);
		if (rs != null)
			ret.setRelayState(rs);
		return ret;
	}
	
	protected AuthnRequestDocument parse(HttpServletRequest req) throws SAMLProcessingException
	{
		String samlRequest = req.getParameter(SAMLConstants.REQ_SAML_REQUEST);
		if (samlRequest == null)
		{
			throw new SAMLProcessingException("Received an HTTP request, without SAML request (no " + 
					SAMLConstants.REQ_SAML_REQUEST + " parameter)");
		}
		String decodedReq;
		try
		{
			if (req.getMethod().equals("POST"))
				decodedReq = new String(Base64.decode(samlRequest));
			else if (req.getMethod().equals("GET"))
				decodedReq = inflateSAMLRequest(samlRequest);
			else
				throw new SAMLProcessingException("Received a request which is neither POST nor GET");
		} catch (Exception e)
		{
			throw new SAMLProcessingException("Received a request which can't be translated into XML form", e);
		}
		
		AuthnRequestDocument reqDoc;
		try
		{
			reqDoc = AuthnRequestDocument.Factory.parse(decodedReq);
		} catch (XmlException e)
		{
			throw new SAMLProcessingException("Received a nonparseable SAML request", e);
		}
		
		return reqDoc;
	}

	protected void validate(SAMLAuthnContext context, HttpServletResponse servletResponse) 
			throws SAMLProcessingException, IOException, EopException
	{
		WebAuthRequestValidator validator = new WebAuthRequestValidator(endpointAddress, 
				samlConfig.getAuthnTrustChecker(), samlConfig.getRequestValidity(), 
				samlConfig.getReplayChecker());
		
		try
		{
			validator.validate(context.getRequestDocument());
		} catch (SAMLServerException e)
		{
			errorHandler.commitErrorResponse(context, e, servletResponse);
		}
	}
	
	protected String inflateSAMLRequest(String samlRequest) throws Exception
	{
		byte[] third = Base64.decode(samlRequest);
		Inflater decompressor = new Inflater(true);
		decompressor.setInput(third, 0, third.length);
		ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
		InflaterOutputStream os = new InflaterOutputStream(baos, decompressor);
		os.write(third);
		os.finish();
		os.close();
		return new String(baos.toByteArray(), Constants.UTF);
	}
	
	@Override
	public void init(FilterConfig filterConfig) throws ServletException
	{
	}

	@Override
	public void destroy()
	{
	}
}
