/*
 * Copyright (c) 2014 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.saml.metadata;

import java.util.Date;

import xmlbeans.org.oasis.saml2.metadata.EntityDescriptorDocument;

/**
 * Implementation provides access to generated metadata
 * @author K. Benedyczak
 */
public interface MetadataProvider
{
	EntityDescriptorDocument getMetadata() throws Exception;
	Date getLastmodification();
	void stop();
}
