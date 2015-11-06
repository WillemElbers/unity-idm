/*
 * Copyright (c) 2014 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.engine.dbupdate;

import java.io.IOException;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import pl.edu.icm.unity.engine.DBIntegrationTestBase;
import pl.edu.icm.unity.server.api.GroupsManagement;
import pl.edu.icm.unity.server.api.IdentitiesManagement;
import pl.edu.icm.unity.server.api.internal.IdentityResolver;
import pl.edu.icm.unity.server.api.internal.SessionManagement;
import pl.edu.icm.unity.stdext.identity.PersistentIdentity;
import pl.edu.icm.unity.stdext.identity.TargetedPersistentIdentity;
import pl.edu.icm.unity.stdext.identity.TransientIdentity;
import pl.edu.icm.unity.types.basic.Entity;
import pl.edu.icm.unity.types.basic.EntityParam;
import pl.edu.icm.unity.types.basic.GroupContents;
import pl.edu.icm.unity.types.basic.GroupMembership;
import pl.edu.icm.unity.types.basic.Identity;
import pl.edu.icm.unity.types.basic.IdentityTaV;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"classpath*:META-INF/components.xml", "classpath:dbUpdate/to2_1_1/test-components.xml"})
@ActiveProfiles("test")
public class TestDatabaseUpdate2_1_1
{
	@Autowired
	private SessionManagement sessionMan;
	
	@Autowired
	protected IdentityResolver identityResolver;
	
	@Autowired
	protected IdentitiesManagement idsMan;

	@Autowired
	protected GroupsManagement groupsMan;

	@BeforeClass
	public static void copyDB() throws IOException
	{
		DBUpdateUtil.installTestDB("2_1_1");
	}
	
	@Test
	public void test() throws Exception
	{
		DBIntegrationTestBase.setupUserContext(sessionMan, identityResolver, "admin", false);
		GroupContents contents = groupsMan.getContents("/", GroupContents.MEMBERS);
		for (GroupMembership mem: contents.getMembers())
		{
			EntityParam entityP = new EntityParam(mem.getEntityId());
			Entity entity = idsMan.getEntityNoContext(entityP, "/");
			Identity[] ids = entity.getIdentities();
			boolean perFound = false;
			for (Identity id: ids)
			{
				if (id.getTypeId().equals(PersistentIdentity.ID))
					perFound = true;
				if (id.getTypeId().equals(TargetedPersistentIdentity.ID) || 
						id.getTypeId().equals(TransientIdentity.ID))
					Assert.fail("Transient/targeted not removed");
			}
			if (!perFound)
				Assert.fail("Persistent not preserved");
		}
		idsMan.getEntity(new EntityParam(new IdentityTaV(PersistentIdentity.ID, 
				"d112a078-a0b4-4ae5-8aa9-7e6744ededc5")));
	}
}
