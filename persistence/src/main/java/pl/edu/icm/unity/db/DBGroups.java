/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.db;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import pl.edu.icm.unity.db.generic.DependencyNotificationManager;
import pl.edu.icm.unity.db.json.GroupMembershipSerializer;
import pl.edu.icm.unity.db.json.GroupsSerializer;
import pl.edu.icm.unity.db.mapper.AttributesMapper;
import pl.edu.icm.unity.db.mapper.GroupsMapper;
import pl.edu.icm.unity.db.model.AttributeBean;
import pl.edu.icm.unity.db.model.DBLimits;
import pl.edu.icm.unity.db.model.GroupBean;
import pl.edu.icm.unity.db.model.GroupElementBean;
import pl.edu.icm.unity.db.resolvers.GroupResolver;
import pl.edu.icm.unity.db.resolvers.IdentitiesResolver;
import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.exceptions.IllegalAttributeTypeException;
import pl.edu.icm.unity.exceptions.IllegalGroupValueException;
import pl.edu.icm.unity.exceptions.IllegalIdentityValueException;
import pl.edu.icm.unity.exceptions.IllegalTypeException;
import pl.edu.icm.unity.exceptions.InternalException;
import pl.edu.icm.unity.types.basic.AttributesClass;
import pl.edu.icm.unity.types.basic.EntityParam;
import pl.edu.icm.unity.types.basic.Group;
import pl.edu.icm.unity.types.basic.GroupContents;
import pl.edu.icm.unity.types.basic.GroupMembership;


/**
 * Groups related DB operations.
 * @author K. Benedyczak
 */
@Component
public class DBGroups
{
	public static final String GROUPS_NOTIFICATION_ID = "groups";
	private DBShared dbShared;
	private GroupResolver groupResolver;
	private IdentitiesResolver idResolver;
	private DBLimits limits;
	private GroupsSerializer jsonS;
	private DependencyNotificationManager notificationsManager;
	private GroupMembershipSerializer groupMembershipSerializer;
	
	@Autowired
	public DBGroups(DBShared dbShared, GroupResolver groupResolver, IdentitiesResolver idResolver, 
			GroupsSerializer jsonS, DB db, DependencyNotificationManager notificationsManager,
			GroupMembershipSerializer groupMembershipSerializer)
	{
		this.groupResolver = groupResolver;
		this.idResolver = idResolver;
		this.groupMembershipSerializer = groupMembershipSerializer;
		this.limits = db.getDBLimits();
		this.jsonS = jsonS;
		this.dbShared = dbShared;
		this.notificationsManager = notificationsManager;
	}
	
	/**
	 * Adds a new group. Pass null parent to create top-level ROOT group.
	 * @param parent
	 * @param name
	 * @throws InternalException
	 * @throws EngineException 
	 * @throws IllegalArgumentException 
	 * @throws GroupNotKnownException
	 * @throws ElementAlreadyExistsException
	 */
	public void addGroup(Group toAdd, SqlSession sqlMap) 
		throws InternalException, EngineException
	{
		limits.checkNameLimit(toAdd.getName());
			
		GroupsMapper mapper = sqlMap.getMapper(GroupsMapper.class);
		AttributesMapper aMapper = sqlMap.getMapper(AttributesMapper.class);
		GroupBean pb = groupResolver.resolveGroup(toAdd.getParentPath(), mapper);

		GroupBean param = new GroupBean(pb.getId(), toAdd.getName());
		if (mapper.resolveGroup(param) != null)
			throw new IllegalGroupValueException("Group already exists");
		
		param.setContents(jsonS.toJson(toAdd, mapper, aMapper));
		if (param.getContents().length > limits.getContentsLimit())
			throw new IllegalGroupValueException("Group metadata size (description, rules, ...) is too big.");
		
		notificationsManager.firePreAddEvent(GROUPS_NOTIFICATION_ID, toAdd, sqlMap);
		
		mapper.insertGroup(param);
		
		sqlMap.clearCache();
	}
	
	public void updateGroup(String toUpdate, Group updated, SqlSession sqlMap) 
			throws InternalException, EngineException
	{
		limits.checkNameLimit(updated.getName());
		
		GroupsMapper mapper = sqlMap.getMapper(GroupsMapper.class);
		AttributesMapper aMapper = sqlMap.getMapper(AttributesMapper.class);
		GroupBean gb = groupResolver.resolveGroup(toUpdate, mapper);
		String newName = updated.getName();
		if (gb.getParent() == null)
			newName = GroupResolver.ROOT_GROUP_NAME;

		GroupBean param = new GroupBean(gb.getId(), newName);
		param.setId(gb.getId());
		param.setContents(jsonS.toJson(updated, mapper, aMapper));
		if (param.getContents().length > limits.getContentsLimit())
			throw new IllegalGroupValueException("Group metadata size (description, rules, ...) is too big.");
		
		Group old = new Group(toUpdate);
		jsonS.fromJson(gb.getContents(), old, mapper, aMapper);
		notificationsManager.firePreUpdateEvent(GROUPS_NOTIFICATION_ID, old, updated, sqlMap);
		mapper.updateGroup(param);

		sqlMap.clearCache();
	}

	
	
	public void removeGroup(String path, boolean recursive, SqlSession sqlMap) 
			throws InternalException, EngineException
	{
		GroupsMapper mapper = sqlMap.getMapper(GroupsMapper.class);
		GroupBean gb = groupResolver.resolveGroup(path, mapper);
		if (gb.getParent() == null)
			throw new IllegalGroupValueException("Can't remove the root group");
		if (!recursive)
		{
			if (mapper.getSubgroups(gb.getId()).size() > 0)
				throw new IllegalGroupValueException("The group contains subgroups");
		}
		Group removed = new Group(path);
		jsonS.fromJson(gb.getContents(), removed, mapper, sqlMap.getMapper(AttributesMapper.class));
		notificationsManager.firePreRemoveEvent(GROUPS_NOTIFICATION_ID, removed, sqlMap);
		mapper.deleteGroup(gb.getId());
	}
	
	
	public GroupContents getContents(String path, int filter, SqlSession sqlMap) 
			throws InternalException, IllegalGroupValueException
	{
		GroupsMapper mapper = sqlMap.getMapper(GroupsMapper.class);
		AttributesMapper aMapper = sqlMap.getMapper(AttributesMapper.class);
		GroupBean gb = groupResolver.resolveGroup(path, mapper);

		GroupContents ret = new GroupContents();
		try
		{
			if ((filter & GroupContents.GROUPS) != 0)
			{
				List<GroupBean> subGroupsRaw = mapper.getSubgroups(gb.getId());
				ret.setSubGroups(convertGroups(subGroupsRaw, mapper));
			}
			if ((filter & GroupContents.LINKED_GROUPS) != 0)
			{
				List<GroupBean> linkedGroupsRaw = mapper.getLinkedGroups(gb.getId());
				ret.setLinkedGroups(convertGroups(linkedGroupsRaw, mapper));
			}
			if ((filter & GroupContents.MEMBERS) != 0)
			{
				List<GroupElementBean> membersRaw = mapper.getMembers(gb.getId());
				ret.setMembers(convertEntities(membersRaw, path));
			}
			if ((filter & GroupContents.METADATA) != 0)
			{
				Group fullGroup = jsonS.resolveGroupBean(gb, mapper, aMapper);
				ret.setGroup(fullGroup);
			}
		} catch (PersistenceException e)
		{
			throw new InternalException("Can't retrieve contents of the " + path + " group", e);
		}
		return ret;
	}
	
	public void addMemberFromParent(String path, EntityParam entity, String idp, String translationProfile,
			Date creationTs, SqlSession sqlMap) 
				throws IllegalGroupValueException, IllegalIdentityValueException, IllegalTypeException
	{
		GroupsMapper mapper = sqlMap.getMapper(GroupsMapper.class);
		GroupBean gb = groupResolver.resolveGroup(path, mapper);
		long entityId = idResolver.getEntityId(entity, sqlMap);
		if (gb.getParent() != null)
		{
			GroupElementBean param = new GroupElementBean(gb.getParent(), entityId);
			if (mapper.isMember(param) == null)
				throw new IllegalGroupValueException("Can't add to the group " + path + 
						", as the entity is not a member of its parent group");
		}

		byte[] contents = groupMembershipSerializer.toJson(new GroupMembership(path, entityId, creationTs, 
				translationProfile, idp));
		GroupElementBean param = new GroupElementBean(gb.getId(), entityId);
		param.setContents(contents);
		if (mapper.isMember(param) != null)
			throw new IllegalGroupValueException("The entity is already a member of this group");
		mapper.insertMember(param);
	}
	
	public void removeMember(String path, EntityParam entity, SqlSession sqlMap) 
			throws IllegalGroupValueException, IllegalIdentityValueException, 
			IllegalTypeException
	{
		GroupsMapper mapper = sqlMap.getMapper(GroupsMapper.class);
		AttributesMapper aMapper = sqlMap.getMapper(AttributesMapper.class);
		GroupBean gb = groupResolver.resolveGroup(path, mapper);
		if (gb.getParent() == null)
			throw new IllegalGroupValueException("The entity can not be removed from the root group");
		long entityId = idResolver.getEntityId(entity, sqlMap);
		
		Set<String> groups = dbShared.getAllGroups(entityId, mapper);
		if (!groups.contains(path))
			throw new IllegalGroupValueException("The entity is not a member of the group");
		
		for (String group: groups)
		{
			if (group.startsWith(path))
			{
				GroupBean gb2 = groupResolver.resolveGroup(group, mapper);
				GroupElementBean param = new GroupElementBean(gb2.getId(), entityId);
				mapper.deleteMember(param);
				AttributeBean ab = new AttributeBean();
				ab.setEntityId(entityId);
				ab.setGroupId(gb2.getId());
				aMapper.deleteAttributesInGroup(ab);
			}
		}
	}
	
	/**
	 * Loads all groups, deserializes their contents what removes the outdated entries and update it
	 * if something was changed.
	 * @param sqlMap
	 * @throws IllegalAttributeTypeException 
	 * @throws IllegalGroupValueException 
	 */
	public int updateAllGroups(SqlSession sqlMap) 
			throws IllegalGroupValueException, IllegalAttributeTypeException
	{
		GroupsMapper mapper = sqlMap.getMapper(GroupsMapper.class);
		AttributesMapper aMapper = sqlMap.getMapper(AttributesMapper.class);
		List<GroupBean> allGroups = mapper.getAllGroups();
		int modified = 0;
		for (GroupBean gb: allGroups)
		{
			String path = groupResolver.resolveGroupPath(gb, mapper); 
			Group fullGroup = new Group(path);
			int changed = jsonS.fromJson(gb.getContents(), fullGroup, mapper, aMapper);
			if (changed > 0)
			{
				byte[] updatedJson = jsonS.toJson(fullGroup, mapper, aMapper);
				GroupBean param = new GroupBean(gb.getId(), gb.getName());
				param.setContents(updatedJson);
				mapper.updateGroup(param);
				modified++;
			}
		}
		return modified;
	}
	
	/**
	 * @param sqlMap
	 * @return set of all groups which use the given {@link AttributesClass}
	 */
	public Set<String> getGroupsUsingAc(String acName, SqlSession sqlMap)
	{
		GroupsMapper mapper = sqlMap.getMapper(GroupsMapper.class);
		List<GroupBean> allGroups = mapper.getAllGroups();
		Set<String> ret = new HashSet<>();
		for (GroupBean gb: allGroups)
		{
			Group fullGroup = new Group("/dummy");
			jsonS.fromJson(gb.getContents(), fullGroup, null, null);
			if (fullGroup.getAttributesClasses().contains(acName))
				ret.add(groupResolver.resolveGroupPath(gb, mapper));
		}
		return ret;
	}
	
	private List<String> convertGroups(List<GroupBean> src, GroupsMapper mapper)
	{
		List<String> ret = new ArrayList<String>(src.size());
		for (int i=0; i<src.size(); i++)
			ret.add(groupResolver.resolveGroupPath(src.get(i), mapper));
		return ret;
	}
	
	private List<GroupMembership> convertEntities(List<GroupElementBean> src, String group)
	{
		List<GroupMembership> ret = new ArrayList<GroupMembership>(src.size());
		for (GroupElementBean geb: src)
			ret.add(groupMembershipSerializer.fromJson(geb.getContents(), geb.getElementId(), group));
		return ret;
	}
	
	
	public Set<String> getAllGroups(SqlSession sqlMap)
	{
		GroupsMapper mapper = sqlMap.getMapper(GroupsMapper.class);
		List<GroupBean> allGroups = mapper.getAllGroups();
		Set<String> ret = new HashSet<>();
		for (GroupBean gb: allGroups)
			ret.add(groupResolver.resolveGroupPath(gb, mapper));
		return ret;
	}
}
