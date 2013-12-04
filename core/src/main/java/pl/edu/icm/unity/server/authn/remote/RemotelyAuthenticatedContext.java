/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.server.authn.remote;

import java.util.ArrayList;
import java.util.Collection;

import pl.edu.icm.unity.types.basic.Attribute;
import pl.edu.icm.unity.types.basic.IdentityTaV;

/**
 * Holds information about a user which was obtained and verified by a remote upstream IdP.
 * The information in this class in in Unity format but need not to have counterparts in the local database.
 * 
 * @author K. Benedyczak
 */
public class RemotelyAuthenticatedContext
{
	private String remoteIdPName;
	private Collection<IdentityTaV> identities = new ArrayList<>();
	private IdentityTaV primaryIdentity;
	private Collection<Attribute<?>> attributes = new ArrayList<>();
	private Collection<String> groups = new ArrayList<>();

	public RemotelyAuthenticatedContext(String remoteIdPName)
	{
		this.remoteIdPName = remoteIdPName;
	}
	public Collection<IdentityTaV> getIdentities()
	{
		return identities;
	}
	public void setIdentities(Collection<IdentityTaV> identities)
	{
		this.identities = identities;
	}
	public Collection<Attribute<?>> getAttributes()
	{
		return attributes;
	}
	public void setAttributes(Collection<Attribute<?>> attributes)
	{
		this.attributes = attributes;
	}
	public Collection<String> getGroups()
	{
		return groups;
	}
	public void setGroups(Collection<String> groups)
	{
		this.groups = groups;
	}
	public IdentityTaV getPrimaryIdentity()
	{
		return primaryIdentity;
	}
	public void setPrimaryIdentity(IdentityTaV primaryIdentity)
	{
		this.primaryIdentity = primaryIdentity;
	}
	public String getRemoteIdPName()
	{
		return remoteIdPName;
	}
}