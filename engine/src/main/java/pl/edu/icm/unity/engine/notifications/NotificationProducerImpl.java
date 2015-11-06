/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.engine.notifications;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.PersistenceConfiguration;

import org.apache.ibatis.session.SqlSession;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import pl.edu.icm.unity.db.DBGroups;
import pl.edu.icm.unity.db.generic.msgtemplate.MessageTemplateDB;
import pl.edu.icm.unity.db.generic.notify.NotificationChannelDB;
import pl.edu.icm.unity.db.generic.notify.NotificationChannelHandler;
import pl.edu.icm.unity.engine.transactions.SqlSessionTL;
import pl.edu.icm.unity.engine.transactions.Transactional;
import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.exceptions.IllegalIdentityValueException;
import pl.edu.icm.unity.exceptions.WrongArgumentException;
import pl.edu.icm.unity.msgtemplates.MessageTemplate;
import pl.edu.icm.unity.msgtemplates.MessageTemplate.Message;
import pl.edu.icm.unity.notifications.NotificationProducer;
import pl.edu.icm.unity.notifications.NotificationStatus;
import pl.edu.icm.unity.server.utils.CacheProvider;
import pl.edu.icm.unity.server.utils.Log;
import pl.edu.icm.unity.server.utils.UnityMessageSource;
import pl.edu.icm.unity.types.basic.EntityParam;
import pl.edu.icm.unity.types.basic.GroupContents;
import pl.edu.icm.unity.types.basic.GroupMembership;
import pl.edu.icm.unity.types.basic.NotificationChannel;

/**
 * Internal (shouldn't be exposed directly to end-users) subsystem for sending notifications.
 * @author K. Benedyczak
 */
@Component
public class NotificationProducerImpl implements NotificationProducer, InternalFacilitiesManagement
{
	private static final Logger log = Log.getLogger(Log.U_SERVER, NotificationProducerImpl.class);
	private Ehcache channelsCache;
	private NotificationFacilitiesRegistry facilitiesRegistry;
	private NotificationChannelDB channelDB;
	private DBGroups dbGroups;
	private MessageTemplateDB mtDB;
	private UnityMessageSource msg;
	
	@Autowired
	public NotificationProducerImpl(
			CacheProvider cacheProvider, 
			NotificationFacilitiesRegistry facilitiesRegistry, NotificationChannelDB channelDB,
			DBGroups dbGroups, MessageTemplateDB mtDB, UnityMessageSource msg)
	{
		this.dbGroups = dbGroups;
		initCache(cacheProvider.getManager());
		this.facilitiesRegistry = facilitiesRegistry;
		this.channelDB = channelDB;
		this.mtDB = mtDB;
		this.msg = msg;
	}

	private void initCache(CacheManager cacheManager)
	{
		channelsCache = cacheManager.addCacheIfAbsent(NotificationChannelHandler.NOTIFICATION_CHANNEL_ID);
		CacheConfiguration config = channelsCache.getCacheConfiguration();
		config.setTimeToIdleSeconds(120);
		config.setTimeToLiveSeconds(120);
		PersistenceConfiguration persistCfg = new PersistenceConfiguration();
		persistCfg.setStrategy("none");
		config.persistence(persistCfg);
	}
	
	private NotificationChannelInstance loadChannel(String channelName, SqlSession sql) throws EngineException
	{
		Element cachedChannel = channelsCache.get(channelName);
		NotificationChannelInstance channel;
		if (cachedChannel == null)
		{
			channel = loadFromDb(channelName, sql);
		} else
			channel = (NotificationChannelInstance) cachedChannel.getObjectValue();
		
		if (channel == null)
			throw new WrongArgumentException("Channel " + channelName + " is not known");
		return channel;
	}
	
	private NotificationChannelInstance loadFromDb(String channelName, SqlSession sql) throws EngineException
	{
		NotificationChannel channelDesc = channelDB.get(channelName, sql);
		NotificationFacility facility = facilitiesRegistry.getByName(channelDesc.getFacilityId());
		return facility.getChannel(channelDesc.getConfiguration());
	}

	@Transactional(autoCommit=false)
	@Override
	public Future<NotificationStatus> sendNotification(EntityParam recipient,
			String channelName, String templateId, Map<String, String> params, String locale, 
			String preferredAddress)
			throws EngineException
	{
		recipient.validateInitialization();

		MessageTemplate template;
		NotificationChannelInstance channel;
		String recipientAddress;
		SqlSession sql = SqlSessionTL.get();
		template = loadTemplate(templateId, sql);
		channel = loadChannel(channelName, sql);
		NotificationFacility facility = facilitiesRegistry.getByName(channel.getFacilityId());
		recipientAddress = facility.getAddressForEntity(recipient, sql, preferredAddress);
		sql.commit();
		Message templateMsg = template.getMessage(locale, msg.getDefaultLocaleCode(), params);
		return channel.sendNotification(recipientAddress, templateMsg.getSubject(), templateMsg.getBody());
	}
	
	@Override
	@Transactional
	public void sendNotificationToGroup(String group, String channelName,
			String templateId, Map<String, String> params, String locale) throws EngineException
	{
		if (templateId == null)
			return;
		SqlSession sql = SqlSessionTL.get();

		MessageTemplate template = loadTemplate(templateId, sql);
		Message templateMsg = template.getMessage(locale, msg.getDefaultLocaleCode(), params);
		String subject = templateMsg.getSubject();
		String body = templateMsg.getBody();

		GroupContents contents = dbGroups.getContents(group, GroupContents.MEMBERS, sql);

		List<GroupMembership> memberships = contents.getMembers();
		NotificationChannelInstance channel = loadChannel(channelName, sql);
		NotificationFacility facility = facilitiesRegistry.getByName(channel.getFacilityId());

		for (GroupMembership membership: memberships)
		{
			try
			{
				String recipientAddress = facility.getAddressForEntity(
						new EntityParam(membership.getEntityId()), sql, null);
				channel.sendNotification(recipientAddress, subject, body);
			} catch (IllegalIdentityValueException e)
			{
				//OK - ignored
			}
		}
	}

	@Override
	@Transactional(autoCommit=false)
	public Future<NotificationStatus> sendNotification(String recipientAddress,
			String channelName, String templateId, Map<String, String> params, String locale)
			throws EngineException
	{
		NotificationChannelInstance channel;
		MessageTemplate template;
		SqlSession sql = SqlSessionTL.get();
		channel = loadChannel(channelName, sql);
		template = loadTemplate(templateId, sql);
		sql.commit();
		Message templateMsg = template.getMessage(locale, msg.getDefaultLocaleCode(), params);
		return channel.sendNotification(recipientAddress, templateMsg.getSubject(), templateMsg.getBody());
	}

	
	private MessageTemplate loadTemplate(String templateName, SqlSession sql) throws EngineException
	{
		try
		{
			return mtDB.get(templateName, sql);
		} catch (WrongArgumentException e)
		{
			log.error("Trying to use non-existing template: " + templateName, e);
			throw e;
		} catch (EngineException e)
		{
			log.error("Error loading template " + templateName, e);
			throw e;
		}
	}
	
	@Override
	public NotificationFacility getNotificationFacilityForChannel(String channelName, SqlSession sql) 
			throws EngineException
	{
		NotificationChannelInstance channel = loadChannel(channelName, sql);
		return facilitiesRegistry.getByName(channel.getFacilityId());
	}
}
