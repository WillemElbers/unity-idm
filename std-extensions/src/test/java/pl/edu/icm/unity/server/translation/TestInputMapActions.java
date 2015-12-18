/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.server.translation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.server.api.AttributesManagement;
import pl.edu.icm.unity.server.authn.remote.RemoteAttribute;
import pl.edu.icm.unity.server.authn.remote.RemoteIdentity;
import pl.edu.icm.unity.server.authn.remote.RemotelyAuthenticatedInput;
import pl.edu.icm.unity.server.registries.IdentityTypesRegistry;
import pl.edu.icm.unity.server.translation.in.AttributeEffectMode;
import pl.edu.icm.unity.server.translation.in.EntityChange;
import pl.edu.icm.unity.server.translation.in.GroupEffectMode;
import pl.edu.icm.unity.server.translation.in.IdentityEffectMode;
import pl.edu.icm.unity.server.translation.in.InputTranslationAction;
import pl.edu.icm.unity.server.translation.in.InputTranslationProfile;
import pl.edu.icm.unity.server.translation.in.MappedGroup;
import pl.edu.icm.unity.server.translation.in.MappedIdentity;
import pl.edu.icm.unity.server.translation.in.MappingResult;
import pl.edu.icm.unity.server.translation.in.action.EntityChangeActionFactory;
import pl.edu.icm.unity.server.translation.in.action.MapAttributeActionFactory;
import pl.edu.icm.unity.server.translation.in.action.MapGroupActionFactory;
import pl.edu.icm.unity.server.translation.in.action.MapIdentityActionFactory;
import pl.edu.icm.unity.server.translation.in.action.MultiMapAttributeActionFactory;
import pl.edu.icm.unity.stdext.attr.FloatingPointAttributeSyntax;
import pl.edu.icm.unity.stdext.attr.IntegerAttributeSyntax;
import pl.edu.icm.unity.stdext.attr.StringAttributeSyntax;
import pl.edu.icm.unity.stdext.identity.UsernameIdentity;
import pl.edu.icm.unity.types.EntityScheduledOperation;
import pl.edu.icm.unity.types.basic.Attribute;
import pl.edu.icm.unity.types.basic.AttributeType;
import pl.edu.icm.unity.types.basic.AttributeVisibility;

public class TestInputMapActions
{
	@Test
	public void testMapAttribute() throws EngineException
	{
		AttributesManagement attrsMan = mock(AttributesManagement.class);
		
		Map<String, AttributeType> mockAts = new HashMap<String, AttributeType>();
		AttributeType sA = new AttributeType("stringA", new StringAttributeSyntax());
		mockAts.put(sA.getName(), sA);
		when(attrsMan.getAttributeTypesAsMap()).thenReturn(mockAts);
		
		MapAttributeActionFactory factory = new MapAttributeActionFactory(attrsMan);
		
		InputTranslationAction mapAction = factory.getInstance("stringA", "/A/B", 
				"attr['attribute'] + '-' + attr['other'] + '-' + id", 
				AttributeVisibility.full.toString(), AttributeEffectMode.CREATE_OR_UPDATE.toString());
				
		RemotelyAuthenticatedInput input = new RemotelyAuthenticatedInput("test");
		input.addIdentity(new RemoteIdentity("idd", "i1"));
		input.addAttribute(new RemoteAttribute("attribute", "a1"));
		input.addAttribute(new RemoteAttribute("other", "a2"));
		
		MappingResult result = mapAction.invoke(input, InputTranslationProfile.createMvelContext(input), "testProf");
		
		Attribute<?> a = result.getAttributes().get(0).getAttribute();
		assertEquals("stringA", a.getName());
		assertEquals("a1-a2-idd", a.getValues().get(0));
	}

	@Test
	public void testMultiMapAttribute() throws EngineException
	{
		AttributesManagement attrsMan = mock(AttributesManagement.class);
		
		Map<String, AttributeType> mockAts = new HashMap<String, AttributeType>();
		AttributeType sA = new AttributeType("stringA", new StringAttributeSyntax());
		mockAts.put(sA.getName(), sA);
		when(attrsMan.getAttributeTypesAsMap()).thenReturn(mockAts);
		
		MultiMapAttributeActionFactory factory = new MultiMapAttributeActionFactory(attrsMan);
		
		InputTranslationAction mapAction = factory.getInstance("attribute stringA /A/B\n"
				+ "other stringA /A\n"
				+ "missing stringA /A", 
				AttributeVisibility.full.toString(), AttributeEffectMode.CREATE_OR_UPDATE.toString());
				
		RemotelyAuthenticatedInput input = new RemotelyAuthenticatedInput("test");
		input.addIdentity(new RemoteIdentity("idd", "i1"));
		input.addAttribute(new RemoteAttribute("attribute", "a1", "a2"));
		input.addAttribute(new RemoteAttribute("other", "a2"));
		
		MappingResult result = mapAction.invoke(input, 
				InputTranslationProfile.createMvelContext(input), "testProf");
		
		Attribute<?> a = result.getAttributes().get(0).getAttribute();
		assertEquals("stringA", a.getName());
		assertEquals("a1", a.getValues().get(0));
		assertEquals("a2", a.getValues().get(1));

		Attribute<?> b = result.getAttributes().get(1).getAttribute();
		assertEquals("stringA", b.getName());
		assertEquals("a2", b.getValues().get(0));
	}

	
	@Test
	public void intAttributeMappingWorks() throws EngineException
	{
		AttributesManagement attrsMan = mock(AttributesManagement.class);
		
		Map<String, AttributeType> mockAts = new HashMap<String, AttributeType>();
		AttributeType iA = new AttributeType("intA", new IntegerAttributeSyntax());
		mockAts.put(iA.getName(), iA);
		when(attrsMan.getAttributeTypesAsMap()).thenReturn(mockAts);
		
		MapAttributeActionFactory factory = new MapAttributeActionFactory(attrsMan);
		
		InputTranslationAction intMapAction = factory.getInstance("intA", "/A/B", 
				"123", 
				AttributeVisibility.full.toString(), 
				AttributeEffectMode.CREATE_OR_UPDATE.toString());
				
		RemotelyAuthenticatedInput input = new RemotelyAuthenticatedInput("test");
		input.addIdentity(new RemoteIdentity("idd", "i1"));
		
		MappingResult result = intMapAction.invoke(input, 
				InputTranslationProfile.createMvelContext(input), "testProf");
		
		Attribute<?> a = result.getAttributes().get(0).getAttribute();
		assertEquals("intA", a.getName());
		((IntegerAttributeSyntax)a.getAttributeSyntax()).validate((Long) a.getValues().get(0));
		assertEquals(123l, a.getValues().get(0));
	}

	@Test
	public void doubleAttributeMappingWorks() throws EngineException
	{
		AttributesManagement attrsMan = mock(AttributesManagement.class);
		
		Map<String, AttributeType> mockAts = new HashMap<String, AttributeType>();
		AttributeType doubleA = new AttributeType("doubleA", new FloatingPointAttributeSyntax());
		mockAts.put(doubleA.getName(), doubleA);
		when(attrsMan.getAttributeTypesAsMap()).thenReturn(mockAts);
		
		MapAttributeActionFactory factory = new MapAttributeActionFactory(attrsMan);
		
		InputTranslationAction doubleMapAction = factory.getInstance("doubleA", "/A/B", 
				"1234.44", 
				AttributeVisibility.full.toString(), 
				AttributeEffectMode.CREATE_OR_UPDATE.toString());
				
		RemotelyAuthenticatedInput input = new RemotelyAuthenticatedInput("test");
		input.addIdentity(new RemoteIdentity("idd", "i1"));
		
		MappingResult result = doubleMapAction.invoke(input, 
				InputTranslationProfile.createMvelContext(input), "testProf");
		
		Attribute<?> a = result.getAttributes().get(0).getAttribute();
		assertEquals("doubleA", a.getName());
		((FloatingPointAttributeSyntax)a.getAttributeSyntax()).validate((Double) a.getValues().get(0));
		assertEquals(1234.44, a.getValues().get(0));
	}


	@Test
	public void testMapGroup() throws EngineException
	{
		MapGroupActionFactory factory = new MapGroupActionFactory();
		InputTranslationAction mapAction = factory.getInstance("'/A/B/' + attr['attribute']", 
				GroupEffectMode.CREATE_GROUP_IF_MISSING.name());
		RemotelyAuthenticatedInput input = new RemotelyAuthenticatedInput("test");
		input.addAttribute(new RemoteAttribute("attribute", "a1"));
		
		MappingResult result = mapAction.invoke(input, InputTranslationProfile.createMvelContext(input), "testProf");
		
		assertEquals(1, result.getGroups().size());
		MappedGroup mg = result.getGroups().iterator().next();
		assertEquals("/A/B/a1", mg.getGroup());
		assertEquals(GroupEffectMode.CREATE_GROUP_IF_MISSING, mg.getCreateIfMissing());
	}
	
	@Test
	public void testMapIdentity() throws EngineException
	{
		IdentityTypesRegistry idTypesReg = mock(IdentityTypesRegistry.class);
		when(idTypesReg.getByName("userName")).thenReturn(new UsernameIdentity());
		
		MapIdentityActionFactory factory = new MapIdentityActionFactory(idTypesReg);
		InputTranslationAction mapAction = factory.getInstance("userName", 
				"attr['attribute:colon'] + '-' + attr['other'] + '-' + id", 
				"CR", IdentityEffectMode.REQUIRE_MATCH.toString());
		RemotelyAuthenticatedInput input = new RemotelyAuthenticatedInput("test");
		input.addIdentity(new RemoteIdentity("idvalue", "idtype"));
		input.addAttribute(new RemoteAttribute("attribute:colon", "a1"));
		input.addAttribute(new RemoteAttribute("other", "a2"));
		
		MappingResult result = mapAction.invoke(input, InputTranslationProfile.createMvelContext(input), "testProf");
		
		MappedIdentity mi = result.getIdentities().get(0);
		assertEquals("CR", mi.getCredentialRequirement());
		assertEquals(IdentityEffectMode.REQUIRE_MATCH, mi.getMode());
		assertEquals("userName", mi.getIdentity().getTypeId());
		assertEquals("a1-a2-idvalue", mi.getIdentity().getValue());
	}
	
	@Test
	public void testEntityChange() throws EngineException
	{
		EntityChangeActionFactory factory = new EntityChangeActionFactory();
		InputTranslationAction mapAction = factory.getInstance(
				EntityScheduledOperation.REMOVE.toString(), 
				"1");
		RemotelyAuthenticatedInput input = new RemotelyAuthenticatedInput("test");
		
		MappingResult result = mapAction.invoke(input, InputTranslationProfile.createMvelContext(input), 
				"testProf");
		
		EntityChange mi = result.getEntityChanges().get(0);
		assertEquals(EntityScheduledOperation.REMOVE, mi.getScheduledOperation());
		Date nextDay = new Date(System.currentTimeMillis() + 3600L*24*1000); 
		assertTrue(nextDay.getTime() >= mi.getScheduledTime().getTime());
		assertTrue(nextDay.getTime()-1000 < mi.getScheduledTime().getTime());
	}
}
