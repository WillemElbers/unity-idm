/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE file for licensing information.
 */
package pl.edu.icm.unity.engine;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import pl.edu.icm.unity.confirmations.ConfirmationManager;
import pl.edu.icm.unity.db.DBAttributes;
import pl.edu.icm.unity.db.DBGroups;
import pl.edu.icm.unity.db.DBIdentities;
import pl.edu.icm.unity.db.DBSessionManager;
import pl.edu.icm.unity.db.generic.ac.AttributeClassDB;
import pl.edu.icm.unity.db.generic.ac.AttributeClassUtil;
import pl.edu.icm.unity.db.resolvers.IdentitiesResolver;
import pl.edu.icm.unity.engine.authz.AuthorizationManager;
import pl.edu.icm.unity.engine.authz.AuthzCapability;
import pl.edu.icm.unity.engine.events.InvocationEventProducer;
import pl.edu.icm.unity.engine.internal.AttributesHelper;
import pl.edu.icm.unity.engine.transactions.SqlSessionTL;
import pl.edu.icm.unity.engine.transactions.Transactional;
import pl.edu.icm.unity.exceptions.AuthorizationException;
import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.exceptions.IllegalAttributeTypeException;
import pl.edu.icm.unity.exceptions.IllegalAttributeValueException;
import pl.edu.icm.unity.exceptions.IllegalGroupValueException;
import pl.edu.icm.unity.exceptions.IllegalTypeException;
import pl.edu.icm.unity.exceptions.SchemaConsistencyException;
import pl.edu.icm.unity.exceptions.WrongArgumentException;
import pl.edu.icm.unity.server.api.AttributesManagement;
import pl.edu.icm.unity.server.attributes.AttributeClassHelper;
import pl.edu.icm.unity.server.attributes.AttributeMetadataProvider;
import pl.edu.icm.unity.server.attributes.AttributeValueSyntaxFactory;
import pl.edu.icm.unity.server.registries.AttributeMetadataProvidersRegistry;
import pl.edu.icm.unity.server.registries.AttributeSyntaxFactoriesRegistry;
import pl.edu.icm.unity.sysattrs.SystemAttributeTypes;
import pl.edu.icm.unity.types.basic.Attribute;
import pl.edu.icm.unity.types.basic.AttributeExt;
import pl.edu.icm.unity.types.basic.AttributeType;
import pl.edu.icm.unity.types.basic.AttributeVisibility;
import pl.edu.icm.unity.types.basic.AttributesClass;
import pl.edu.icm.unity.types.basic.EntityParam;
import pl.edu.icm.unity.types.basic.IdentityType;

/**
 * Implements attributes operations.
 * @author K. Benedyczak
 */
@Component
@InvocationEventProducer
public class AttributesManagementImpl implements AttributesManagement
{
	private AttributeSyntaxFactoriesRegistry attrValueTypesReg;
	private DBSessionManager db;
	private AttributeClassDB acDB;
	private DBAttributes dbAttributes;
	private DBIdentities dbIdentities;
	private DBGroups dbGroups;
	private IdentitiesResolver idResolver;
	private AttributeMetadataProvidersRegistry atMetaProvidersRegistry;
	private AuthorizationManager authz;
	private AttributesHelper attributesHelper;
	private ConfirmationManager confirmationManager;

	
	@Autowired
	public AttributesManagementImpl(AttributeSyntaxFactoriesRegistry attrValueTypesReg,
			DBSessionManager db, AttributeClassDB acDB, 
			DBAttributes dbAttributes, DBIdentities dbIdentities, DBGroups dbGroups,
			IdentitiesResolver idResolver,
			AttributeMetadataProvidersRegistry atMetaProvidersRegistry,
			AuthorizationManager authz, AttributesHelper attributesHelper,
			ConfirmationManager confirmationManager)
	{
		this.attrValueTypesReg = attrValueTypesReg;
		this.db = db;
		this.acDB = acDB;
		this.dbAttributes = dbAttributes;
		this.dbIdentities = dbIdentities;
		this.dbGroups = dbGroups;
		this.idResolver = idResolver;
		this.atMetaProvidersRegistry = atMetaProvidersRegistry;
		this.authz = authz;
		this.attributesHelper = attributesHelper;
		this.confirmationManager = confirmationManager;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String[] getSupportedAttributeValueTypes() throws EngineException
	{
		authz.checkAuthorization(AuthzCapability.readInfo);
		Collection<AttributeValueSyntaxFactory<?>> all = attrValueTypesReg.getAll();
		String[] ret = new String[all.size()];
		Iterator<AttributeValueSyntaxFactory<?>> it = all.iterator();
		for (int i=0; it.hasNext(); i++)
			ret[i] = it.next().getId();
		return ret;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Transactional
	public void addAttributeType(AttributeType toAdd) throws EngineException
	{
		toAdd.validateInitialization();
		if (toAdd.getFlags() != 0)
			throw new IllegalAttributeTypeException("Custom attribute types must not have any flags set");
		authz.checkAuthorization(AuthzCapability.maintenance);
		SqlSession sql = SqlSessionTL.sqlSession.get();
		Collection<AttributeType> existingAts = dbAttributes.getAttributeTypes(sql).values();
		verifyATMetadata(toAdd, existingAts);
		dbAttributes.addAttributeType(toAdd, sql);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Transactional
	public void updateAttributeType(AttributeType at) throws EngineException
	{
		at.validateInitialization();
		if (at.getFlags() != 0)
			throw new IllegalAttributeTypeException("Custom attribute types must not have any flags set");
		authz.checkAuthorization(AuthzCapability.maintenance);
		SqlSession sql = SqlSessionTL.sqlSession.get();
		AttributeType atExisting = dbAttributes.getAttributeType(at.getName(), sql);
		if ((atExisting.getFlags() & AttributeType.TYPE_IMMUTABLE_FLAG) != 0)
		{
			updateImmutableAttributeType(sql, at, atExisting);
			return;
		}
		Collection<AttributeType> existingAts = dbAttributes.getAttributeTypes(sql).values();
		verifyATMetadata(at, existingAts);

		dbAttributes.updateAttributeType(at, sql);
		if (!at.getValueType().getValueSyntaxId().equals(atExisting.getValueType().getValueSyntaxId()))
			clearAttributeExtractionFromIdentities(at.getName(), sql);
	}

	/**
	 * Attribute types marked as immutable can have only displayed name and description modified.
	 * Also metadata is not set on them.
	 * @param at
	 * @throws EngineException 
	 */
	private void updateImmutableAttributeType(SqlSession sql, AttributeType at, AttributeType existing) 
			throws EngineException
	{
		existing.setDisplayedName(at.getDisplayedName());
		existing.setDescription(at.getDescription());
		existing.setSelfModificable(at.isSelfModificable());
		existing.setVisibility(at.getVisibility());
		dbAttributes.updateAttributeType(existing, sql);
	}
	
	private void verifyATMetadata(AttributeType at, Collection<AttributeType> existingAts) 
			throws IllegalAttributeTypeException
	{
		Map<String, String> meta = at.getMetadata();
		if (meta.isEmpty())
			return;
		Map<String, String> existing = new HashMap<String, String>();
		for (AttributeType eat: existingAts)
			if (!eat.getName().equals(at.getName()))
			{
				for (String eatm: eat.getMetadata().keySet())
					existing.put(eatm, eat.getName());
			}
		
		for (Map.Entry<String, String> metaE: meta.entrySet())
		{
			AttributeMetadataProvider provider;
			try
			{
				provider = atMetaProvidersRegistry.getByName(metaE.getKey());
			} catch (IllegalTypeException e)
			{
				throw new IllegalAttributeTypeException("The attribute type metadata " + 
						metaE.getKey() + " is not known.");
			}
			
			if (provider.isSingleton())
			{
				if (existing.containsKey(metaE.getKey()) && !existing.get(metaE.getKey()).equals(at.getName()))
					throw new IllegalAttributeTypeException("The attribute type metadata " + 
							metaE.getKey() + " can be assigned to a single attribute type only" +
							" and it is already assigned to attribute type " + 
							existing.get(metaE.getKey()));
			}
			
			provider.verify(metaE.getValue(), at);
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	@Transactional
	public void removeAttributeType(String id, boolean deleteInstances) throws EngineException
	{
		authz.checkAuthorization(AuthzCapability.maintenance);
		SqlSession sql = SqlSessionTL.sqlSession.get();

		AttributeType at = dbAttributes.getAttributeType(id, sql);
		if ((at.getFlags() & (AttributeType.TYPE_IMMUTABLE_FLAG | 
				AttributeType.INSTANCES_IMMUTABLE_FLAG)) != 0)
			throw new IllegalAttributeTypeException("The attribute type with name " + id + 
					" can not be manually removed");

		dbAttributes.removeAttributeType(id, deleteInstances, sql);
		clearAttributeExtractionFromIdentities(id, sql);
	}

	private void clearAttributeExtractionFromIdentities(String id, SqlSession sql)
	{
		Collection<IdentityType> identityTypes = dbIdentities.getIdentityTypes(sql).values();
		for (IdentityType idType: identityTypes)
		{
			Map<String, String> extractedMap = idType.getExtractedAttributes();
			Iterator<Map.Entry<String, String>> entries = extractedMap.entrySet().iterator();
			boolean updateIdType = false;
			while (entries.hasNext())
			{
				Map.Entry<String, String> extracted = entries.next();
				if (extracted.getValue().equals(id))
				{
					entries.remove();
					updateIdType = true;
				}
			}
			if (updateIdType)
			{
				dbIdentities.updateIdentityType(sql, idType);
			}
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	@Transactional(noTransaction=true)
	public Collection<AttributeType> getAttributeTypes() throws EngineException
	{
		authz.checkAuthorization(AuthzCapability.readInfo);
		SqlSession sql = SqlSessionTL.sqlSession.get();
		return dbAttributes.getAttributeTypes(sql).values();
	}

	@Override
	@Transactional(noTransaction=true)
	public Map<String, AttributeType> getAttributeTypesAsMap() throws EngineException
	{
		authz.checkAuthorization(AuthzCapability.readInfo);
		SqlSession sql = SqlSessionTL.sqlSession.get();
		return dbAttributes.getAttributeTypes(sql);
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	@Transactional
	public void addAttributeClass(AttributesClass clazz) throws EngineException
	{
		authz.checkAuthorization(AuthzCapability.maintenance);
		String addedName = clazz.getName();
		Set<String> missingParents = new HashSet<>(clazz.getParentClasses());
		SqlSession sql = SqlSessionTL.sqlSession.get();
		Map<String, AttributesClass> allClasses = AttributeClassUtil.resolveAttributeClasses(acDB, sql);
		for (String c: allClasses.keySet())
		{
			if (addedName.equals(c))
				throw new WrongArgumentException("The attribute class " + addedName + " already exists");
			missingParents.remove(c);
		}
		if (!missingParents.isEmpty())
			throw new WrongArgumentException("The attribute class parent(s): " + missingParents + 
					" do(es) not exist");

		AttributeClassHelper.cleanupClass(clazz, allClasses);
		acDB.insert(clazz.getName(), clazz, sql);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Transactional
	public void removeAttributeClass(String id) throws EngineException
	{
		authz.checkAuthorization(AuthzCapability.maintenance);
		SqlSession sql = SqlSessionTL.sqlSession.get();
		Map<String, AttributesClass> allClasses = AttributeClassUtil.resolveAttributeClasses(acDB, sql);
		for (AttributesClass ac: allClasses.values())
		{
			if (ac.getParentClasses().contains(id))
				throw new SchemaConsistencyException("Can not remove attribute class " + id + 
						" as it is a parent of the attribute class " + ac.getName());
		}
		Set<Long> entities = dbAttributes.getEntitiesWithStringAttribute(
				SystemAttributeTypes.ATTRIBUTE_CLASSES, id, sql);
		if (entities.size() > 0)
		{
			String info = String.valueOf(entities.iterator().next());
			if (entities.size() > 1)
				info += " and " + (entities.size()-1) + " more";
			throw new SchemaConsistencyException("The attribute class " + id + 
					" can not be removed as there are entities with this class set. " +
					"Ids of entities: " + info);
		}

		Set<String> groupsUsing = dbGroups.getGroupsUsingAc(id, sql);
		if (groupsUsing.size() > 0)
			throw new SchemaConsistencyException("The attribute class " + id + 
					" can not be removed as there are groups with have this class set: " +
					groupsUsing.toString());

		acDB.remove(id, sql);
	}

	@Override
	@Transactional
	public void updateAttributeClass(AttributesClass updated) throws EngineException
	{
		authz.checkAuthorization(AuthzCapability.maintenance);
		SqlSession sql = SqlSessionTL.sqlSession.get();
		Map<String, AttributesClass> allClasses = AttributeClassUtil.resolveAttributeClasses(acDB, sql);
		AttributesClass original = allClasses.get(updated.getName());
		if (original == null)
			throw new WrongArgumentException("There is no attribute class '" + 
					updated.getName() + "'");
		String acName = original.getName();

		AttributeClassHelper originalEffective = new AttributeClassHelper(allClasses, 
				Collections.singleton(acName));

		allClasses.put(acName, updated);
		AttributeClassHelper updatedEffective = new AttributeClassHelper(allClasses, 
				Collections.singleton(acName));

		boolean restrictiveChange = updatedEffective.isRestricting(originalEffective);
		if (restrictiveChange)
			checkIfUnused(acName, allClasses, sql);

		acDB.update(acName, updated, sql);
	}

	private void checkIfUnused(String acName, Map<String, AttributesClass> allClasses, SqlSession sql) 
			throws EngineException
	{
		for (AttributesClass ac: allClasses.values())
		{
			if (ac.getParentClasses().contains(acName))
				throw new SchemaConsistencyException("Can not perform a restrictive "
						+ "change of an attribute class " + acName + 
						" as it is a parent of another attribute class " + ac.getName()
						+ ". Only non restrictive changes are allowed for used classes.");
		}
		Set<Long> entities = dbAttributes.getEntitiesWithStringAttribute(
				SystemAttributeTypes.ATTRIBUTE_CLASSES, acName, sql);
		if (entities.size() > 0)
		{
			String info = String.valueOf(entities.iterator().next());
			if (entities.size() > 1)
				info += " and " + (entities.size()-1) + " more";
			throw new SchemaConsistencyException("Can not perform a restrictive change of an "
					+ "attribute class " + acName + 
					" as there are entities with this class set. " +
					"Ids of entities: " + info + 
					". Only non restrictive changes are allowed for used classes.");
		}
		
		Set<String> groupsUsing = dbGroups.getGroupsUsingAc(acName, sql);
		if (groupsUsing.size() > 0)
			throw new SchemaConsistencyException("Can not perform a restrictive change of an "
					+ "attribute class " + acName + 
					" as there are groups with have this class set: " +
					groupsUsing.toString() + 
					". Only non restrictive changes are allowed for used classes.");
	}
	
	@Override
	@Transactional
	public Map<String, AttributesClass> getAttributeClasses() throws EngineException
	{
		authz.checkAuthorization(AuthzCapability.readInfo);
		SqlSession sql = SqlSessionTL.sqlSession.get();
		return AttributeClassUtil.resolveAttributeClasses(acDB, sql);
	}

	@Override
	@Transactional
	public void setEntityAttributeClasses(EntityParam entity, String group, Collection<String> classes)
			throws EngineException
	{
		entity.validateInitialization();
		authz.checkAuthorization(group, AuthzCapability.attributeModify);
		SqlSession sql = SqlSessionTL.sqlSession.get();
		long entityId = idResolver.getEntityId(entity, sql);
		attributesHelper.setAttributeClasses(entityId, group, classes, sql);
	}
	

	@Override
	@Transactional
	public Collection<AttributesClass> getEntityAttributeClasses(EntityParam entity,
			String group) throws EngineException
	{
		entity.validateInitialization();
		SqlSession sql = SqlSessionTL.sqlSession.get();
		long entityId = idResolver.getEntityId(entity, sql);
		authz.checkAuthorization(group, AuthzCapability.read);
		Collection<AttributeExt<?>> attrs = dbAttributes.getAllAttributes(entityId, group, false, 
				SystemAttributeTypes.ATTRIBUTE_CLASSES, sql);
		if (attrs.size() == 0)
			return Collections.emptySet();
		AttributeExt<?> attr = attrs.iterator().next();
		@SuppressWarnings("unchecked")
		List<String> classes = (List<String>) attr.getValues();

		Map<String, AttributesClass> allClasses = AttributeClassUtil.resolveAttributeClasses(acDB, sql);
		Set<AttributesClass> ret = new HashSet<>(classes.size());
		for (String clazz: classes)
		{
			AttributesClass ac = allClasses.get(clazz);
			if (ac != null)
				ret.add(ac);
		}
		return ret;
	}
	
	/**
	 * Verifies if the attribute is allowed wrt attribute classes defined for the entity in the respective group.
	 * @param entityId
	 * @param attribute
	 * @param sql
	 * @throws EngineException
	 */
	private void checkIfAllowed(long entityId, String groupPath, String attributeTypeId, SqlSession sql) 
			throws EngineException
	{
		AttributeClassHelper acHelper = AttributeClassUtil.getACHelper(entityId, groupPath, 
				dbAttributes, acDB, dbGroups, sql);
		if (!acHelper.isAllowed(attributeTypeId))
			throw new SchemaConsistencyException("The attribute with name " + attributeTypeId + 
					" is not allowed by the entity's attribute classes in the group " + groupPath);
	}

	/**
	 * Verifies if the attribute is allowed wrt attribute classes defined for the entity in the respective group.
	 * @param entityId
	 * @param attribute
	 * @param sql
	 * @throws EngineException
	 */
	private void checkIfMandatory(long entityId, String groupPath, String attributeTypeId, SqlSession sql) 
			throws EngineException
	{
		AttributeClassHelper acHelper = AttributeClassUtil.getACHelper(entityId, groupPath, 
				dbAttributes, acDB, dbGroups, sql);
		if (acHelper.isMandatory(attributeTypeId))
			throw new SchemaConsistencyException("The attribute with name " + attributeTypeId + 
					" is required by the entity's attribute classes in the group " + groupPath);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public <T> void setAttribute(EntityParam entity, Attribute<T> attribute, boolean update)
			throws EngineException
	{
		attribute.validateInitialization();
		entity.validateInitialization(); 
		SqlSession sql = db.getSqlSession(true);
		boolean fullAuthz;
		try
		{
			//Important - attributes can be also set as a result of addMember and addEntity.
			//  when changing this method, verify if those needs an update too.
			long entityId = idResolver.getEntityId(entity, sql);
			AttributeType at = dbAttributes.getAttributeType(attribute.getName(), sql);
			fullAuthz = checkSetAttributeAuthz(entityId, at, attribute);
			checkIfAllowed(entityId, attribute.getGroupPath(), attribute.getName(), sql);

			attributesHelper.addAttribute(sql, entityId, update, at, fullAuthz, attribute);
			
			sql.commit();
		} finally
		{
			db.releaseSqlSession(sql);
		}
		
		confirmationManager.sendVerificationQuiet(entity, attribute, false);
	}
	
	private <T> boolean checkSetAttributeAuthz(long entityId, AttributeType at, Attribute<T> attribute) 
			throws AuthorizationException
	{
		Set<AuthzCapability> nonSelfCapabilities = authz.getCapabilities(false, 
				attribute.getGroupPath());
		boolean fullAuthz = nonSelfCapabilities.contains(AuthzCapability.attributeModify);

		//even if we have fullAuthz we need to check authZ (e.g. to get outdated credential error)
		authz.checkAuthorization(at.isSelfModificable() && authz.isSelf(entityId), 
				attribute.getGroupPath(), AuthzCapability.attributeModify);
		return fullAuthz;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	@Transactional
	public void removeAttribute(EntityParam entity, String groupPath, String attributeTypeId)
			throws EngineException
	{
		if (groupPath == null)
			throw new IllegalGroupValueException("Group must not be null");
		if (attributeTypeId == null)
			throw new IllegalAttributeValueException("Attribute name must not be null");
		entity.validateInitialization();

		SqlSession sql = SqlSessionTL.sqlSession.get();
		long entityId = idResolver.getEntityId(entity, sql);
		AttributeType at = dbAttributes.getAttributeType(attributeTypeId, sql);
		if (at.isInstanceImmutable())
			throw new SchemaConsistencyException("The attribute with name " + at.getName() + 
					" can not be manually modified");
		authz.checkAuthorization(at.isSelfModificable() && authz.isSelf(entityId),
				groupPath, AuthzCapability.attributeModify);
		
		checkIfMandatory(entityId, groupPath, attributeTypeId, sql);
		
		dbAttributes.removeAttribute(entityId, groupPath, attributeTypeId, sql);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Transactional
	public Collection<AttributeExt<?>> getAttributes(EntityParam entity, String groupPath,
			String attributeTypeId) throws EngineException
	{
		Collection<AttributeExt<?>> ret = getAllAttributesInternal(entity, true, groupPath, attributeTypeId, 
				new AuthzCapability[] {AuthzCapability.read}, false);
		filterLocal(ret);
		return ret;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Transactional
	public Collection<AttributeExt<?>> getAllAttributes(EntityParam entity, boolean effective, String groupPath,
			String attributeTypeId, boolean allowDegrade) throws EngineException
	{
		try
		{
			return getAllAttributesInternal(entity, effective, groupPath, attributeTypeId, 
					new AuthzCapability[] {AuthzCapability.readHidden, AuthzCapability.read}, true);
		} catch (AuthorizationException e)
		{
			if (allowDegrade)
			{
				Collection<AttributeExt<?>> ret = getAllAttributesInternal(entity, effective, 
						groupPath, attributeTypeId, 
						new AuthzCapability[] {AuthzCapability.read}, false);
				filterLocal(ret);
				return ret;
			} else
				throw e;
		}
	}


	private void filterLocal(Collection<AttributeExt<?>> unfiltered)
	{
		Iterator<AttributeExt<?>> it = unfiltered.iterator();
		while (it.hasNext())
		{
			AttributeExt<?> attr = it.next();
			if (attr.getVisibility() == AttributeVisibility.local)
				it.remove();
		}
	}
	
	private Collection<AttributeExt<?>> getAllAttributesInternal(EntityParam entity, boolean effective, String groupPath,
			String attributeTypeName, AuthzCapability[] requiredCapability, boolean allowDisabled) throws EngineException
	{
		entity.validateInitialization();
		SqlSession sql = SqlSessionTL.sqlSession.get();
		long entityId = idResolver.getEntityId(entity, sql);
		authz.checkAuthorization(authz.isSelf(entityId), groupPath, requiredCapability);
		Collection<AttributeExt<?>> ret = attributesHelper.getAllAttributesInternal(sql, entityId, 
				effective, groupPath, attributeTypeName, allowDisabled);
		return ret;
	}
}
