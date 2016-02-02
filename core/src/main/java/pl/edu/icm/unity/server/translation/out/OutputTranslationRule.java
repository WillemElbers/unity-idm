/*
 * Copyright (c) 2013 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package pl.edu.icm.unity.server.translation.out;

import org.apache.log4j.Logger;

import pl.edu.icm.unity.exceptions.EngineException;
import pl.edu.icm.unity.server.translation.TranslationRuleInstance;
import pl.edu.icm.unity.server.translation.TranslationCondition;
import pl.edu.icm.unity.server.utils.Log;

/**
 * Invokes {@link OutputTranslationAction}.
 *  
 * @author K. Benedyczak
 */
public class OutputTranslationRule extends TranslationRuleInstance<OutputTranslationAction>
{
	private static final Logger log = Log.getLogger(Log.U_SERVER_TRANSLATION, OutputTranslationRule.class);
	
	public OutputTranslationRule(OutputTranslationAction action, TranslationCondition condition)
	{
		super(action, condition);
	}
	
	public void invoke(TranslationInput input, Object mvelCtx, String currentProfile,
			TranslationResult result) throws EngineException
	{
		if (conditionInstance.evaluate(mvelCtx))
		{
			log.debug("Condition OK");
			actionInstance.invoke(input, mvelCtx, currentProfile, result);
		} else
		{
			log.debug("Condition not met");			
		}
	}
}
