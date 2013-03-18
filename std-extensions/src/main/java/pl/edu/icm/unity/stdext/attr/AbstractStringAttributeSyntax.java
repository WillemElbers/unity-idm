/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.stdext.attr;

import pl.edu.icm.unity.Constants;
import pl.edu.icm.unity.types.basic.AttributeValueSyntax;

/**
 * Common code for string based attribute syntax classes.
 * @author K. Benedyczak
 */
public abstract class AbstractStringAttributeSyntax implements AttributeValueSyntax<String>
{
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean areEqual(String value, Object another)
	{
		return value == null ? null == another : value.equals(another);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode(Object value)
	{
		return value.hashCode();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] serialize(String value)
	{
		return value.getBytes(Constants.UTF);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String deserialize(byte[] raw)
	{
		return new String(raw, Constants.UTF);
	}
}