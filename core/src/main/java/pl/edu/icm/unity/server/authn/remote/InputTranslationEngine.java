/*
 * Copyright (c) 2014 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.server.authn.remote;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.exceptions.IllegalGroupValueException;
import pl.edu.icm.unity.exceptions.IllegalIdentityValueException;
import pl.edu.icm.unity.server.api.AttributesManagement;
import pl.edu.icm.unity.server.api.GroupsManagement;
import pl.edu.icm.unity.server.api.IdentitiesManagement;
import pl.edu.icm.unity.server.translation.ExecutionBreakException;
import pl.edu.icm.unity.server.translation.in.EntityChange;
import pl.edu.icm.unity.server.translation.in.GroupEffectMode;
import pl.edu.icm.unity.server.translation.in.IdentityEffectMode;
import pl.edu.icm.unity.server.translation.in.MappedAttribute;
import pl.edu.icm.unity.server.translation.in.MappedGroup;
import pl.edu.icm.unity.server.translation.in.MappedIdentity;
import pl.edu.icm.unity.server.translation.in.MappingResult;
import pl.edu.icm.unity.server.utils.GroupUtils;
import pl.edu.icm.unity.server.utils.Log;
import pl.edu.icm.unity.types.EntityState;
import pl.edu.icm.unity.types.basic.Attribute;
import pl.edu.icm.unity.types.basic.AttributeExt;
import pl.edu.icm.unity.types.basic.Entity;
import pl.edu.icm.unity.types.basic.EntityParam;
import pl.edu.icm.unity.types.basic.Group;
import pl.edu.icm.unity.types.basic.GroupMembership;
import pl.edu.icm.unity.types.basic.Identity;
import pl.edu.icm.unity.types.basic.IdentityParam;

/**
 * Applies all mappings which were recorded by profile's actions, taking into account the overall profile's settings.
 * <p>
 * Important: the instance is running without authorization, the object can not be exposed to direct operation.
 * @author K. Benedyczak
 */
@Component
public class InputTranslationEngine
{
	private static final Logger log = Log.getLogger(Log.U_SERVER_TRANSLATION, InputTranslationEngine.class);
	private IdentitiesManagement idsMan;
	private AttributesManagement attrMan;
	private GroupsManagement groupsMan;
	
	@Autowired
	public InputTranslationEngine(@Qualifier("insecure") IdentitiesManagement idsMan, 
			@Qualifier("insecure") AttributesManagement attrMan,
			@Qualifier("insecure") GroupsManagement groupsMan)
	{
		this.idsMan = idsMan;
		this.attrMan = attrMan;
		this.groupsMan = groupsMan;
	}

	
	/**
	 * Entry point.
	 * @param result
	 * @throws EngineException
	 */
	public void process(MappingResult result) throws EngineException
	{
		EntityParam entity = processIdentities(result);
		result.setMappedAtExistingEntity(entity);
		if (entity == null)
		{
			log.info("The mapped identity does not exist in database and was not created. "
					+ "The creation of groups and attributes is skipped, the mapped groups and attributes "
					+ "will be available for the registration form (if any)");
			return;
		}
		processGroups(result, entity);
		processAttributes(result, entity);
		processEntityChanges(result, entity);
	}
	
	/**
	 * Merges the information obtained after execution of an input translation profile with a manually specified
	 * entity.
	 * @param result
	 * @param baseEntity
	 * @throws EngineException 
	 */
	public void mergeWithExisting(MappingResult result, EntityParam baseEntity) throws EngineException
	{
		result.setCleanStaleAttributes(false);
		result.setCleanStaleIdentities(false);
		result.setCleanStaleGroups(false);
		
		processIdentitiesForMerge(result, baseEntity);
		processGroups(result, baseEntity);
		processAttributes(result, baseEntity);
		processEntityChanges(result, baseEntity);
	}
	
	/**
	 * 
	 * @param result
	 * @return true only if no one of mapped identities is present in db.
	 */
	public boolean identitiesNotPresentInDb(MappingResult result)
	{
		try
		{
			getIdentitiesToCreate(result);
			return true;
		} catch (EngineException e)
		{
			return false;
		}
	}

	public Entity resolveMappedIdentity(MappedIdentity checked) throws EngineException
	{
		return idsMan.getEntity(new EntityParam(checked.getIdentity()));
	}
	
	public MappedIdentity getExistingIdentity(MappingResult result)
	{
		for (MappedIdentity checked: result.getIdentities())
		{
			try
			{
				idsMan.getEntity(new EntityParam(checked.getIdentity()));
				return checked;
			} catch (IllegalIdentityValueException e)
			{
				//OK
			} catch (EngineException e)
			{
				log.error("Can't check the entity status, shouldn't happen", e);
			}			
		}
		return null;
	}
	
	/**
	 * performs identities mapping
	 * @param result
	 * @return an mapped identity - previously existing or newly created; or null if identity mapping was not successful
	 * @throws EngineException
	 */
	private EntityParam processIdentities(MappingResult result) throws EngineException
	{
		List<MappedIdentity> mappedMissingIdentitiesToCreate = new ArrayList<>();
		List<MappedIdentity> mappedMissingIdentities = new ArrayList<>();
		List<MappedIdentity> mappedMissingCreateOrUpdateIdentities = new ArrayList<>();
		Entity existing = null;
		for (MappedIdentity checked: result.getIdentities())
		{
			try
			{
				Entity found = idsMan.getEntity(new EntityParam(checked.getIdentity()));
				if (existing != null && !existing.getId().equals(found.getId()))
				{
					log.warn("Identity was mapped to two different entities: " + 
							existing + " and " + found + ". Skipping.");
					throw new ExecutionBreakException();
				}
				existing = found;
				result.addAuthenticatedWith(checked.getIdentity().getValue());
			} catch (IllegalIdentityValueException e)
			{
				if (checked.getMode() == IdentityEffectMode.REQUIRE_MATCH)
				{
					log.info("The identity " + checked.getIdentity() + " doesn't exists "
							+ "in local database, but the profile requires so. Skipping.");
					throw new ExecutionBreakException();
				} else if (checked.getMode() == IdentityEffectMode.CREATE_OR_MATCH)
				{
					mappedMissingIdentitiesToCreate.add(checked);
				} else if (checked.getMode() == IdentityEffectMode.MATCH)
				{
					mappedMissingIdentities.add(checked);
				} else
				{
					mappedMissingCreateOrUpdateIdentities.add(checked);
				}
			}			
		}
		
		if (existing != null)
		{
			mappedMissingIdentitiesToCreate.addAll(mappedMissingCreateOrUpdateIdentities);
			if (result.isCleanStaleIdentities())
				removeStaleIdentities(existing, result.getIdentities());
		} else
		{
			mappedMissingIdentities.addAll(mappedMissingCreateOrUpdateIdentities);
		}
		if (mappedMissingIdentitiesToCreate.isEmpty() && mappedMissingIdentities.isEmpty() && existing == null)
		{
			log.info("The translation profile didn't return any identity of the principal. "
					+ "We can't authenticate such anonymous principal.");
			throw new ExecutionBreakException();
		}
		
		if (mappedMissingIdentitiesToCreate.isEmpty())
		{
			log.debug("No identity needs to be added");
			return existing != null ? new EntityParam(existing.getId()) : null;
		}
		
		if (existing != null)
		{
			addEquivalents(mappedMissingIdentitiesToCreate, new EntityParam(existing.getId()), result);
			return new EntityParam(existing.getId());
		} else
		{
			Identity created = createNewEntity(result, mappedMissingIdentitiesToCreate);
			return new EntityParam(created.getEntityId());
		}

	}

	/**
	 * Identities created from this profile and idp which are not present are removed.
	 * @param existing
	 * @param allMapped
	 */
	private void removeStaleIdentities(Entity existing, List<MappedIdentity> allMapped)
	{
		IdentityParam exampleMapped = allMapped.get(0).getIdentity();
		String idp = exampleMapped.getRemoteIdp();
		if (idp == null)
			idp = "_____MISSING";
		String profile = exampleMapped.getTranslationProfile();
		if (profile == null)
			profile = "_____MISSING";
		for (Identity id: existing.getIdentities())
		{
			if (idp.equals(id.getRemoteIdp()) && profile.equals(id.getTranslationProfile()))
			{
				boolean has = allMapped.stream().anyMatch(mi -> 
					id.getValue().equals(mi.getIdentity().getValue()) &&
						id.getTypeId().equals(mi.getIdentity().getTypeId())
				);
				if (!has)
				{
					try
					{
						idsMan.removeIdentity(id);
					} catch (EngineException e)
					{
						log.error("Can not remove stale identity " + id, e);
					}
				}
			}
		}
	}
	
	/**
	 * Besides returning the list of identities to be created also ensures that no one from mapped
	 * identities does exist in the db.
	 * @param result
	 * @return list of mapped identities which should be created. 
	 * @throws EngineException
	 */
	private List<MappedIdentity> getIdentitiesToCreate(MappingResult result) throws EngineException
	{
		List<MappedIdentity> mappedMissingIdentitiesToCreate = new ArrayList<>();
		for (MappedIdentity checked: result.getIdentities())
		{
			try
			{
				idsMan.getEntity(new EntityParam(checked.getIdentity()));
				log.debug("Identity was mapped to existing identity.");
				throw new ExecutionBreakException();
			} catch (IllegalIdentityValueException e)
			{
				if (checked.getMode() == IdentityEffectMode.REQUIRE_MATCH)
				{
					log.info("The identity " + checked.getIdentity() + " doesn't exists "
							+ "in local database, but the profile requires so. Skipping.");
					throw new ExecutionBreakException();
				} else
				{
					mappedMissingIdentitiesToCreate.add(checked);
				}
			}			
		}
		return mappedMissingIdentitiesToCreate;
	}
	
	private void processIdentitiesForMerge(MappingResult result, EntityParam baseEntity) throws EngineException
	{
		List<MappedIdentity> mappedMissingIdentitiesToCreate = getIdentitiesToCreate(result);
		if (mappedMissingIdentitiesToCreate.isEmpty())
		{
			log.info("The translation profile didn't return any identity of the principal. "
					+ "We can't merge such anonymous principal.");
			throw new ExecutionBreakException();
		}
		
		addEquivalents(mappedMissingIdentitiesToCreate, baseEntity, result);
	}

	
	private void addEquivalents(Collection<MappedIdentity> toAdd, EntityParam parentEntity, MappingResult result) 
			throws EngineException
	{
		for (MappedIdentity mi: toAdd)
		{
			idsMan.addIdentity(mi.getIdentity(), parentEntity, false);
			result.addAuthenticatedWith(mi.getIdentity().getValue());
		}
	}
	
	private Identity createNewEntity(MappingResult result,
			List<MappedIdentity> mappedMissingIdentities) throws EngineException
	{
		MappedIdentity first = mappedMissingIdentities.remove(0);
		
		Identity added;
		List<Attribute<?>> attributes = getAttributesInGroup("/", result);
		log.info("Adding entity " + first.getIdentity() + " to the local DB");
		added = idsMan.addEntity(first.getIdentity(), first.getCredentialRequirement(), 
				EntityState.valid, false, attributes);
		result.addAuthenticatedWith(first.getIdentity().getValue());
		
		addEquivalents(mappedMissingIdentities, new EntityParam(added), result);
		return added;
	}

	private Map<String, List<Attribute<?>>> getAttributesByGroup(MappingResult result) throws EngineException
	{
		Map<String, List<Attribute<?>>> ret = new HashMap<String, List<Attribute<?>>>();
		
		ret.put("/", getAttributesInGroup("/", result));
		for (MappedGroup mg: result.getGroups())
		{
			if ("/".equals(mg.getGroup()))
				continue;
			ret.put(mg.getGroup(), getAttributesInGroup(mg.getGroup(), result));
		}
		return ret;
	}

	private List<Attribute<?>> getAttributesInGroup(String group, MappingResult result) throws EngineException
	{
		List<Attribute<?>> ret = new ArrayList<>();
		for (MappedAttribute mappedAttr: result.getAttributes())
		{
			if (mappedAttr.getAttribute().getGroupPath().equals(group))
				ret.add(mappedAttr.getAttribute());
		}
		return ret;
	}
	
	private void processGroups(MappingResult result, EntityParam principal) throws EngineException
	{
		Map<String, List<Attribute<?>>> attributesByGroup = getAttributesByGroup(result);
		Map<String, GroupMembership> currentGroups = idsMan.getGroups(principal);
        	Set<String> currentSimple = new HashSet<>(currentGroups.keySet());
		for (MappedGroup gm: result.getGroups())
		{
		        if (!currentGroups.containsKey(gm.getGroup()))
			{
				Deque<String> missingGroups = 
						GroupUtils.getMissingGroups(gm.getGroup(), currentSimple);
				log.info("Adding to group " + gm);
				addToGroupRecursive(principal, missingGroups, currentSimple, gm.getIdp(), 
						gm.getProfile(), gm.getCreateIfMissing(),
						attributesByGroup);
			} else
			{
				log.debug("Entity already in the group " + gm + ", skipping");
			}
		}
		if (result.isCleanStaleGroups())
			removeStaleMemberships(currentGroups, result, principal);
		
	}
	
	private void removeStaleMemberships(Map<String, GroupMembership> currentGroups, MappingResult result,
			EntityParam principal)
	{
		IdentityParam exampleMapped = result.getIdentities().get(0).getIdentity();
		String idp = exampleMapped.getRemoteIdp();
		if (idp == null)
			idp = "_____MISSING";
		String profile = exampleMapped.getTranslationProfile();
		if (profile == null)
			profile = "_____MISSING";
		List<MappedGroup> mappedGroups = result.getGroups();
		for (GroupMembership membership: currentGroups.values())
		{
			if (!membership.getGroup().equals("/") &&
				idp.equals(membership.getRemoteIdp()) && profile.equals(membership.getTranslationProfile()))
			{
				if (!mappedGroups.stream().anyMatch(gm -> membership.getGroup().equals(gm.getGroup())))
					try
					{
						groupsMan.removeMember(membership.getGroup(), principal);
					} catch (EngineException e)
					{
						log.error("Can not remove stale group membership in " 
								+ membership.getGroup(), e);
					}
			}
		}
	}
	
	private void addToGroupRecursive(EntityParam who, Deque<String> missingGroups, 
			Set<String> currentGroups, String idp, String profile, 
			GroupEffectMode createMissingGroups, Map<String, List<Attribute<?>>> attributesByGroup) 
					throws EngineException
	{
		String group = missingGroups.pollLast();
		List<Attribute<?>> attributes = attributesByGroup.get(group);
		if (attributes == null)
			attributes = new ArrayList<Attribute<?>>();
		try
		{
			groupsMan.addMemberFromParent(group, who, attributes, idp, profile);
		} catch (IllegalGroupValueException missingGroup)
		{
			if (createMissingGroups == GroupEffectMode.CREATE_GROUP_IF_MISSING)
			{
				log.info("Group " + group + " doesn't exist, "
						+ "will be created to fullfil translation profile rule");
				groupsMan.addGroup(new Group(group));
				groupsMan.addMemberFromParent(group, who, attributes, idp, profile);
			} else if (createMissingGroups == GroupEffectMode.REQUIRE_EXISTING_GROUP)
			{
				log.debug("Entity should be added to a group " + group + " which is missing, failing.");
				throw missingGroup;
			} else
			{
				log.debug("Entity should be added to a group " + group + " which is missing, ignoring.");
				return;
			}
		}
		
		currentGroups.add(group);
		if (!missingGroups.isEmpty())
		{
			addToGroupRecursive(who, missingGroups, currentGroups, 
					idp, profile, createMissingGroups, attributesByGroup);
		}
	}
	
	private void processAttributes(MappingResult result, EntityParam principal) throws EngineException
	{
		Set<String> existingANames = new HashSet<>();
		Collection<AttributeExt<?>> existingAttrs = attrMan.getAllAttributes(principal, 
				false, null, null, false);
		for (AttributeExt<?> a: existingAttrs)
			existingANames.add(a.getGroupPath()+"///" + a.getName());

		List<MappedAttribute> attrs = result.getAttributes();
		
		for (MappedAttribute attr: attrs)
		{
			Attribute<?> att = attr.getAttribute();
			switch (attr.getMode())
			{
			case CREATE_ONLY:
				if (!existingANames.contains(att.getGroupPath()+"///"+att.getName()))
				{
					log.info("Creating attribute " + att);
					attrMan.setAttribute(principal, att, false);					
				} else
				{
					log.debug("Skipping attribute which is already present: " + att);
				}
				break;
			case CREATE_OR_UPDATE:
				log.info("Updating attribute " + att);
				attrMan.setAttribute(principal, att, true);
				break;
			case UPDATE_ONLY:
				if (existingANames.contains(att.getGroupPath()+"///"+att.getName()))
				{
					log.info("Updating attribute " + att);
					attrMan.setAttribute(principal, att, true);					
				} else
				{
					log.debug("Skipping attribute to be updated as there is no one defined: " + att);
				}
				break;
			}
		}
		
		if (result.isCleanStaleAttributes())
			removeStaleAttributes(result, existingAttrs, principal);
	}
	
	private void removeStaleAttributes(MappingResult result, Collection<AttributeExt<?>> existingAttrs,
			EntityParam principal)
	{
		IdentityParam exampleMapped = result.getIdentities().get(0).getIdentity();
		String idp = exampleMapped.getRemoteIdp();
		if (idp == null)
			idp = "_____MISSING";
		String profile = exampleMapped.getTranslationProfile();
		if (profile == null)
			profile = "_____MISSING";
		List<MappedAttribute> mappedAttributes = result.getAttributes();
		for (AttributeExt<?> a: existingAttrs)
		{
			if (idp.equals(a.getRemoteIdp()) && profile.equals(a.getTranslationProfile()))
			{
				if (!mappedAttributes.stream().anyMatch(ma -> 
						a.getName().equals(ma.getAttribute().getName()) &&
						a.getGroupPath().equals(ma.getAttribute().getGroupPath())))
					try
					{
						attrMan.removeAttribute(principal, a.getGroupPath(), a.getName());
					} catch (EngineException e)
					{
						log.error("Can not remove stale attribute " + a, e);
					}
			}
		}
	}
	
	private void processEntityChanges(MappingResult result, EntityParam principal) throws EngineException
	{
		List<EntityChange> changes = result.getEntityChanges();
		
		for (EntityChange change: changes)
		{
			if (change.getScheduledOperation() != null)
				log.info("Changing entity scheduled operation to " + 
					change.getScheduledOperation() + " on " + change.getScheduledTime());
			else
				log.info("Clearing entity scheduled change operation");
			idsMan.scheduleEntityChange(principal, change.getScheduledTime(), 
					change.getScheduledOperation());
		}
	}
}
