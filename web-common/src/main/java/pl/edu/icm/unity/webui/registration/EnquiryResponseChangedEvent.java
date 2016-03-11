/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.webui.registration;

import pl.edu.icm.unity.webui.bus.Event;

public class EnquiryResponseChangedEvent implements Event
{
	private String id;

	public EnquiryResponseChangedEvent(String id)
	{
		this.id = id;
	}

	public String getId()
	{
		return id;
	}

	public void setId(String id)
	{
		this.id = id;
	}
}
