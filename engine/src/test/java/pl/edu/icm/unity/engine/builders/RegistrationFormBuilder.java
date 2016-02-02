/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.engine.builders;

import java.util.ArrayList;
import java.util.List;

import pl.edu.icm.unity.server.translation.form.RegistrationTranslationProfile;
import pl.edu.icm.unity.types.I18nString;
import pl.edu.icm.unity.types.registration.AgreementRegistrationParam;
import pl.edu.icm.unity.types.registration.AttributeRegistrationParam;
import pl.edu.icm.unity.types.registration.CredentialRegistrationParam;
import pl.edu.icm.unity.types.registration.GroupRegistrationParam;
import pl.edu.icm.unity.types.registration.IdentityRegistrationParam;
import pl.edu.icm.unity.types.registration.ParameterRetrievalSettings;
import pl.edu.icm.unity.types.registration.RegistrationForm;
import pl.edu.icm.unity.types.registration.RegistrationFormNotifications;
import pl.edu.icm.unity.types.translation.ProfileType;
import pl.edu.icm.unity.types.translation.TranslationProfile;

/**
 * Source code generated by Fluent Builders Generator Do not modify this file
 * See generator home page at:
 * http://code.google.com/p/fluent-builders-generator-eclipse-plugin/
 */
public class RegistrationFormBuilder extends RegistrationFormBuilderBase<RegistrationFormBuilder>
{
	public static RegistrationFormBuilder registrationForm()
	{
		return new RegistrationFormBuilder();
	}

	public RegistrationFormBuilder()
	{
		super(new RegistrationForm());
	}

	public RegistrationForm build()
	{
		return getInstance();
	}
}

class RegistrationFormBuilderBase<GeneratorT extends RegistrationFormBuilderBase<GeneratorT>>
{
	private RegistrationForm instance;

	protected RegistrationFormBuilderBase(RegistrationForm aInstance)
	{
		instance = aInstance;
		instance.setTranslationProfile(new TranslationProfile("registrationProfile", "", ProfileType.REGISTRATION,
				new ArrayList<>()));
	}

	protected RegistrationForm getInstance()
	{
		return instance;
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withNotificationsConfiguration(RegistrationFormNotifications aValue)
	{
		instance.setNotificationsConfiguration(aValue);

		return (GeneratorT) this;
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withTranslationProfile(RegistrationTranslationProfile profile)
	{
		instance.setTranslationProfile(profile);
		return (GeneratorT) this;
	}
	
	public NotificationsConfigurationRegistrationFormNotificationsBuilder withNotificationsConfiguration()
	{
		RegistrationFormNotifications obj = new RegistrationFormNotifications();

		withNotificationsConfiguration(obj);

		return new NotificationsConfigurationRegistrationFormNotificationsBuilder(obj);
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withIdentityParams(List<IdentityRegistrationParam> aValue)
	{
		instance.setIdentityParams(aValue);

		return (GeneratorT) this;
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withAddedIdentityParam(IdentityRegistrationParam aValue)
	{
		if (instance.getIdentityParams() == null)
		{
			instance.setIdentityParams(new ArrayList<IdentityRegistrationParam>());
		}

		((ArrayList<IdentityRegistrationParam>) instance.getIdentityParams()).add(aValue);

		return (GeneratorT) this;
	}

	public AddedIdentityParamIdentityRegistrationParamBuilder withAddedIdentityParam()
	{
		IdentityRegistrationParam obj = new IdentityRegistrationParam();

		withAddedIdentityParam(obj);

		return new AddedIdentityParamIdentityRegistrationParamBuilder(obj);
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withAttributeParams(List<AttributeRegistrationParam> aValue)
	{
		instance.setAttributeParams(aValue);

		return (GeneratorT) this;
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withAddedAttributeParam(AttributeRegistrationParam aValue)
	{
		if (instance.getAttributeParams() == null)
		{
			instance.setAttributeParams(new ArrayList<AttributeRegistrationParam>());
		}

		((ArrayList<AttributeRegistrationParam>) instance.getAttributeParams()).add(aValue);

		return (GeneratorT) this;
	}

	public AddedAttributeParamAttributeRegistrationParamBuilder withAddedAttributeParam()
	{
		AttributeRegistrationParam obj = new AttributeRegistrationParam();

		withAddedAttributeParam(obj);

		return new AddedAttributeParamAttributeRegistrationParamBuilder(obj);
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withGroupParams(List<GroupRegistrationParam> aValue)
	{
		instance.setGroupParams(aValue);

		return (GeneratorT) this;
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withAddedGroupParam(GroupRegistrationParam aValue)
	{
		if (instance.getGroupParams() == null)
		{
			instance.setGroupParams(new ArrayList<GroupRegistrationParam>());
		}

		((ArrayList<GroupRegistrationParam>) instance.getGroupParams()).add(aValue);

		return (GeneratorT) this;
	}

	public AddedGroupParamGroupRegistrationParamBuilder withAddedGroupParam()
	{
		GroupRegistrationParam obj = new GroupRegistrationParam();

		withAddedGroupParam(obj);

		return new AddedGroupParamGroupRegistrationParamBuilder(obj);
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withCredentialParams(List<CredentialRegistrationParam> aValue)
	{
		instance.setCredentialParams(aValue);

		return (GeneratorT) this;
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withAddedCredentialParam(CredentialRegistrationParam aValue)
	{
		if (instance.getCredentialParams() == null)
		{
			instance.setCredentialParams(new ArrayList<CredentialRegistrationParam>());
		}

		((ArrayList<CredentialRegistrationParam>) instance.getCredentialParams())
				.add(aValue);

		return (GeneratorT) this;
	}

	public AddedCredentialParamCredentialRegistrationParamBuilder withAddedCredentialParam()
	{
		CredentialRegistrationParam obj = new CredentialRegistrationParam();

		withAddedCredentialParam(obj);

		return new AddedCredentialParamCredentialRegistrationParamBuilder(obj);
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withAgreements(List<AgreementRegistrationParam> aValue)
	{
		instance.setAgreements(aValue);

		return (GeneratorT) this;
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withAddedAgreement(AgreementRegistrationParam aValue)
	{
		if (instance.getAgreements() == null)
		{
			instance.setAgreements(new ArrayList<AgreementRegistrationParam>());
		}

		((ArrayList<AgreementRegistrationParam>) instance.getAgreements()).add(aValue);

		return (GeneratorT) this;
	}

	public AddedAgreementAgreementRegistrationParamBuilder withAddedAgreement()
	{
		AgreementRegistrationParam obj = new AgreementRegistrationParam();

		withAddedAgreement(obj);

		return new AddedAgreementAgreementRegistrationParamBuilder(obj);
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withCollectComments(boolean aValue)
	{
		instance.setCollectComments(aValue);

		return (GeneratorT) this;
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withFormInformation(I18nString aValue)
	{
		instance.setFormInformation(aValue);

		return (GeneratorT) this;
	}

	public FormInformationI18nStringBuilder withFormInformation()
	{
		I18nString obj = new I18nString();

		withFormInformation(obj);

		return new FormInformationI18nStringBuilder(obj);
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withDisplayedName(I18nString aValue)
	{
		instance.setDisplayedName(aValue);

		return (GeneratorT) this;
	}

	public DisplayedNameI18nStringBuilder withDisplayedName()
	{
		I18nString obj = new I18nString();

		withDisplayedName(obj);

		return new DisplayedNameI18nStringBuilder(obj);
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withRegistrationCode(String aValue)
	{
		instance.setRegistrationCode(aValue);

		return (GeneratorT) this;
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withPubliclyAvailable(boolean aValue)
	{
		instance.setPubliclyAvailable(aValue);

		return (GeneratorT) this;
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withDefaultCredentialRequirement(String aValue)
	{
		instance.setDefaultCredentialRequirement(aValue);

		return (GeneratorT) this;
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withCaptchaLength(int aValue)
	{
		instance.setCaptchaLength(aValue);

		return (GeneratorT) this;
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withName(String aValue)
	{
		instance.setName(aValue);

		return (GeneratorT) this;
	}

	@SuppressWarnings("unchecked")
	public GeneratorT withDescription(String aValue)
	{
		instance.setDescription(aValue);

		return (GeneratorT) this;
	}

	public class NotificationsConfigurationRegistrationFormNotificationsBuilder
			extends
			RegistrationFormNotificationsBuilderBase<NotificationsConfigurationRegistrationFormNotificationsBuilder>
	{
		public NotificationsConfigurationRegistrationFormNotificationsBuilder(
				RegistrationFormNotifications aInstance)
		{
			super(aInstance);
		}

		@SuppressWarnings("unchecked")
		public GeneratorT endNotificationsConfiguration()
		{
			return (GeneratorT) RegistrationFormBuilderBase.this;
		}
	}

	public class AddedIdentityParamIdentityRegistrationParamBuilder
			extends
			IdentityRegistrationParamBuilderBase<AddedIdentityParamIdentityRegistrationParamBuilder>
	{
		public AddedIdentityParamIdentityRegistrationParamBuilder(
				IdentityRegistrationParam aInstance)
		{
			super(aInstance);
		}

		@SuppressWarnings("unchecked")
		public GeneratorT endIdentityParam()
		{
			return (GeneratorT) RegistrationFormBuilderBase.this;
		}
	}

	public class AddedAttributeParamAttributeRegistrationParamBuilder
			extends
			AttributeRegistrationParamBuilderBase<AddedAttributeParamAttributeRegistrationParamBuilder>
	{
		public AddedAttributeParamAttributeRegistrationParamBuilder(
				AttributeRegistrationParam aInstance)
		{
			super(aInstance);
		}

		@SuppressWarnings("unchecked")
		public GeneratorT endAttributeParam()
		{
			return (GeneratorT) RegistrationFormBuilderBase.this;
		}
	}

	public class AddedGroupParamGroupRegistrationParamBuilder
			extends
			GroupRegistrationParamBuilderBase<AddedGroupParamGroupRegistrationParamBuilder>
	{
		public AddedGroupParamGroupRegistrationParamBuilder(GroupRegistrationParam aInstance)
		{
			super(aInstance);
		}

		@SuppressWarnings("unchecked")
		public GeneratorT endGroupParam()
		{
			return (GeneratorT) RegistrationFormBuilderBase.this;
		}
	}

	public class AddedCredentialParamCredentialRegistrationParamBuilder
			extends
			CredentialRegistrationParamBuilderBase<AddedCredentialParamCredentialRegistrationParamBuilder>
	{
		public AddedCredentialParamCredentialRegistrationParamBuilder(
				CredentialRegistrationParam aInstance)
		{
			super(aInstance);
		}

		@SuppressWarnings("unchecked")
		public GeneratorT endCredentialParam()
		{
			return (GeneratorT) RegistrationFormBuilderBase.this;
		}
	}

	public class AddedAgreementAgreementRegistrationParamBuilder
			extends
			AgreementRegistrationParamBuilderBase<AddedAgreementAgreementRegistrationParamBuilder>
	{
		public AddedAgreementAgreementRegistrationParamBuilder(
				AgreementRegistrationParam aInstance)
		{
			super(aInstance);
		}

		@SuppressWarnings("unchecked")
		public GeneratorT endAgreement()
		{
			return (GeneratorT) RegistrationFormBuilderBase.this;
		}
	}

	public class FormInformationI18nStringBuilder extends
			I18nStringBuilderBase<FormInformationI18nStringBuilder>
	{
		public FormInformationI18nStringBuilder(I18nString aInstance)
		{
			super(aInstance);
		}

		@SuppressWarnings("unchecked")
		public GeneratorT endFormInformation()
		{
			return (GeneratorT) RegistrationFormBuilderBase.this;
		}
	}

	public class DisplayedNameI18nStringBuilder extends
			I18nStringBuilderBase<DisplayedNameI18nStringBuilder>
	{
		public DisplayedNameI18nStringBuilder(I18nString aInstance)
		{
			super(aInstance);
		}

		@SuppressWarnings("unchecked")
		public GeneratorT endDisplayedName()
		{
			return (GeneratorT) RegistrationFormBuilderBase.this;
		}
	}

	public static class RegistrationFormNotificationsBuilderBase<GeneratorT extends RegistrationFormNotificationsBuilderBase<GeneratorT>>
	{
		private RegistrationFormNotifications instance;

		protected RegistrationFormNotificationsBuilderBase(
				RegistrationFormNotifications aInstance)
		{
			instance = aInstance;
		}

		protected RegistrationFormNotifications getInstance()
		{
			return instance;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withSubmittedTemplate(String aValue)
		{
			instance.setSubmittedTemplate(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withUpdatedTemplate(String aValue)
		{
			instance.setUpdatedTemplate(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withRejectedTemplate(String aValue)
		{
			instance.setRejectedTemplate(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withAcceptedTemplate(String aValue)
		{
			instance.setAcceptedTemplate(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withChannel(String aValue)
		{
			instance.setChannel(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withAdminsNotificationGroup(String aValue)
		{
			instance.setAdminsNotificationGroup(aValue);

			return (GeneratorT) this;
		}
	}

	public static class GroupRegistrationParamBuilderBase<GeneratorT extends GroupRegistrationParamBuilderBase<GeneratorT>>
	{
		private GroupRegistrationParam instance;

		protected GroupRegistrationParamBuilderBase(GroupRegistrationParam aInstance)
		{
			instance = aInstance;
		}

		protected GroupRegistrationParam getInstance()
		{
			return instance;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withGroupPath(String aValue)
		{
			instance.setGroupPath(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withLabel(String aValue)
		{
			instance.setLabel(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withDescription(String aValue)
		{
			instance.setDescription(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withRetrievalSettings(ParameterRetrievalSettings aValue)
		{
			instance.setRetrievalSettings(aValue);

			return (GeneratorT) this;
		}
	}

	public static class AttributeRegistrationParamBuilderBase<GeneratorT extends AttributeRegistrationParamBuilderBase<GeneratorT>>
	{
		private AttributeRegistrationParam instance;

		protected AttributeRegistrationParamBuilderBase(AttributeRegistrationParam aInstance)
		{
			instance = aInstance;
		}

		protected AttributeRegistrationParam getInstance()
		{
			return instance;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withAttributeType(String aValue)
		{
			instance.setAttributeType(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withGroup(String aValue)
		{
			instance.setGroup(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withShowGroups(boolean aValue)
		{
			instance.setShowGroups(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withOptional(boolean aValue)
		{
			instance.setOptional(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withLabel(String aValue)
		{
			instance.setLabel(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withDescription(String aValue)
		{
			instance.setDescription(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withRetrievalSettings(ParameterRetrievalSettings aValue)
		{
			instance.setRetrievalSettings(aValue);

			return (GeneratorT) this;
		}
	}

	public static class CredentialRegistrationParamBuilderBase<GeneratorT extends CredentialRegistrationParamBuilderBase<GeneratorT>>
	{
		private CredentialRegistrationParam instance;

		protected CredentialRegistrationParamBuilderBase(
				CredentialRegistrationParam aInstance)
		{
			instance = aInstance;
		}

		protected CredentialRegistrationParam getInstance()
		{
			return instance;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withCredentialName(String aValue)
		{
			instance.setCredentialName(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withLabel(String aValue)
		{
			instance.setLabel(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withDescription(String aValue)
		{
			instance.setDescription(aValue);

			return (GeneratorT) this;
		}
	}

	public static class IdentityRegistrationParamBuilderBase<GeneratorT extends IdentityRegistrationParamBuilderBase<GeneratorT>>
	{
		private IdentityRegistrationParam instance;

		protected IdentityRegistrationParamBuilderBase(IdentityRegistrationParam aInstance)
		{
			instance = aInstance;
		}

		protected IdentityRegistrationParam getInstance()
		{
			return instance;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withIdentityType(String aValue)
		{
			instance.setIdentityType(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withOptional(boolean aValue)
		{
			instance.setOptional(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withLabel(String aValue)
		{
			instance.setLabel(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withDescription(String aValue)
		{
			instance.setDescription(aValue);

			return (GeneratorT) this;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withRetrievalSettings(ParameterRetrievalSettings aValue)
		{
			instance.setRetrievalSettings(aValue);

			return (GeneratorT) this;
		}
	}

	public static class I18nStringBuilderBase<GeneratorT extends I18nStringBuilderBase<GeneratorT>>
	{
		private I18nString instance;

		protected I18nStringBuilderBase(I18nString aInstance)
		{
			instance = aInstance;
		}

		protected I18nString getInstance()
		{
			return instance;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withDefaultValue(String aValue)
		{
			instance.setDefaultValue(aValue);

			return (GeneratorT) this;
		}
	}

	public static class AgreementRegistrationParamBuilderBase<GeneratorT extends AgreementRegistrationParamBuilderBase<GeneratorT>>
	{
		private AgreementRegistrationParam instance;

		protected AgreementRegistrationParamBuilderBase(AgreementRegistrationParam aInstance)
		{
			instance = aInstance;
		}

		protected AgreementRegistrationParam getInstance()
		{
			return instance;
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withText(I18nString aValue)
		{
			instance.setText(aValue);

			return (GeneratorT) this;
		}

		public TextI18nStringBuilder withText()
		{
			I18nString obj = new I18nString();

			withText(obj);

			return new TextI18nStringBuilder(obj);
		}

		@SuppressWarnings("unchecked")
		public GeneratorT withManatory(boolean aValue)
		{
			instance.setManatory(aValue);

			return (GeneratorT) this;
		}

		public class TextI18nStringBuilder extends
				I18nStringBuilderBase<TextI18nStringBuilder>
		{
			public TextI18nStringBuilder(I18nString aInstance)
			{
				super(aInstance);
			}

			@SuppressWarnings("unchecked")
			public GeneratorT endText()
			{
				return (GeneratorT) AgreementRegistrationParamBuilderBase.this;
			}
		}
	}
}
