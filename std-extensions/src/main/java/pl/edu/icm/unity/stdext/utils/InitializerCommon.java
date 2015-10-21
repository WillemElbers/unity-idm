/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.stdext.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.exceptions.IllegalIdentityValueException;
import pl.edu.icm.unity.server.api.AttributesManagement;
import pl.edu.icm.unity.server.api.GroupsManagement;
import pl.edu.icm.unity.server.utils.UnityMessageSource;
import pl.edu.icm.unity.server.utils.UnityServerConfiguration;
import pl.edu.icm.unity.stdext.attr.EnumAttribute;
import pl.edu.icm.unity.stdext.attr.JpegImageAttributeSyntax;
import pl.edu.icm.unity.stdext.attr.StringAttribute;
import pl.edu.icm.unity.stdext.attr.StringAttributeSyntax;
import pl.edu.icm.unity.stdext.attr.VerifiableEmailAttributeSyntax;
import pl.edu.icm.unity.stdext.identity.UsernameIdentity;
import pl.edu.icm.unity.types.basic.AttributeStatement2;
import pl.edu.icm.unity.types.basic.AttributeType;
import pl.edu.icm.unity.types.basic.AttributeVisibility;
import pl.edu.icm.unity.types.basic.AttributesClass;
import pl.edu.icm.unity.types.basic.EntityParam;
import pl.edu.icm.unity.types.basic.Group;
import pl.edu.icm.unity.types.basic.GroupContents;
import pl.edu.icm.unity.types.basic.IdentityTaV;

/**
 * Code to initialize popular objects. Useful for various initializers. 
 * @author K. Benedyczak
 */
@Component
public class InitializerCommon
{
	public static final String JPEG_ATTR = "jpegPhoto";
	public static final String CN_ATTR = "cn";
	public static final String ORG_ATTR = "o";
	public static final String EMAIL_ATTR = "email";
	
	public static final String MAIN_AC = "Common attributes";
	public static final String NAMING_AC = "Common identification attributes";
	
	private AttributesManagement attrMan;
	private GroupsManagement groupsMan;
	private UnityServerConfiguration config;
	private UnityMessageSource msg;

	@Autowired
	public InitializerCommon(@Qualifier("insecure") AttributesManagement attrMan, 
			@Qualifier("insecure") GroupsManagement groupsMan, UnityServerConfiguration config,
			UnityMessageSource msg)
	{
		this.attrMan = attrMan;
		this.groupsMan = groupsMan;
		this.config = config;
		this.msg = msg;
	}

	public void initializeMainAttributeClass() throws EngineException
	{
		AttributesClass mainAC = new AttributesClass(MAIN_AC, 
				"General purpose attributes, should be enabled for everybody", 
				new HashSet<>(Arrays.asList("sys:AuthorizationRole")), 
				new HashSet<String>(), false, 
				new HashSet<String>());
		Map<String, AttributesClass> allAcs = attrMan.getAttributeClasses();
		if (!allAcs.containsKey(MAIN_AC))
			attrMan.addAttributeClass(mainAC);

		AttributesClass namingAC = new AttributesClass(NAMING_AC, 
				"Identification attributes, should be set for everybody to enable common system features", 
				new HashSet<String>(Arrays.asList(ORG_ATTR, JPEG_ATTR)), 
				new HashSet<String>(Arrays.asList(CN_ATTR, EMAIL_ATTR)), false, 
				new HashSet<String>());
		if (!allAcs.containsKey(NAMING_AC))
			attrMan.addAttributeClass(namingAC);
	}
	

	public void initializeCommonAttributeStatements() throws EngineException
	{
		AttributeStatement2 everybodyStmt = AttributeStatement2.getFixedEverybodyStatement(
				new EnumAttribute("sys:AuthorizationRole", 
				"/", AttributeVisibility.local,
				"Regular User")); 
		Group rootGroup = groupsMan.getContents("/", GroupContents.METADATA).getGroup();
		rootGroup.setAttributeStatements(new AttributeStatement2[]{everybodyStmt});
		groupsMan.updateGroup("/", rootGroup);
	}
	
	public void initializeCommonAttributeTypes() throws EngineException
	{
		Set<AttributeType> existingATs = new HashSet<>(attrMan.getAttributeTypes());
		
		AttributeType userPicture = new AttributeType(JPEG_ATTR, new JpegImageAttributeSyntax(), msg);
		((JpegImageAttributeSyntax)userPicture.getValueType()).setMaxSize(2000000);
		((JpegImageAttributeSyntax)userPicture.getValueType()).setMaxWidth(120);
		((JpegImageAttributeSyntax)userPicture.getValueType()).setMaxHeight(120);
		userPicture.setMinElements(1);
		if (!existingATs.contains(userPicture))
			attrMan.addAttributeType(userPicture);

		AttributeType cn = new AttributeType(CN_ATTR, new StringAttributeSyntax(), msg);
		cn.setMinElements(1);
		((StringAttributeSyntax)cn.getValueType()).setMaxLength(100);
		((StringAttributeSyntax)cn.getValueType()).setMinLength(2);
		cn.getMetadata().put(EntityNameMetadataProvider.NAME, "");
		if (!existingATs.contains(cn))
			attrMan.addAttributeType(cn);

		AttributeType org = new AttributeType(ORG_ATTR, new StringAttributeSyntax(), msg);
		org.setMinElements(1);
		org.setMaxElements(10);
		((StringAttributeSyntax)org.getValueType()).setMaxLength(33);
		((StringAttributeSyntax)org.getValueType()).setMinLength(2);
		if (!existingATs.contains(org))
			attrMan.addAttributeType(org);

		AttributeType verifiableEmail = new AttributeType(EMAIL_ATTR, 
				new VerifiableEmailAttributeSyntax(), msg);
		verifiableEmail.setMinElements(1);
		verifiableEmail.setMaxElements(5);
		verifiableEmail.getMetadata().put(ContactEmailMetadataProvider.NAME, "");
		
		if (!existingATs.contains(verifiableEmail))
			attrMan.addAttributeType(verifiableEmail);
		
		
	}
	
	public void assignCnToAdmin() throws EngineException
	{
		String adminU = config.getValue(UnityServerConfiguration.INITIAL_ADMIN_USER);
		StringAttribute cnA = new StringAttribute(CN_ATTR, "/", AttributeVisibility.full, 
				"Default Administrator");
		EntityParam entity = new EntityParam(new IdentityTaV(UsernameIdentity.ID, adminU));
		try
		{
			if (attrMan.getAttributes(entity, "/", CN_ATTR).isEmpty())
				attrMan.setAttribute(entity, cnA, false);
		} catch (IllegalIdentityValueException e)
		{
			//ok - no default admin, no default CN.
		}
	}
}
