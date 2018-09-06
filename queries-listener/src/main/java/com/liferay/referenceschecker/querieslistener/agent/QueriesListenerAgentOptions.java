/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.referenceschecker.querieslistener.agent;

import com.p6spy.engine.spy.P6LoadableOptions;
import com.p6spy.engine.spy.P6ModuleManager;
import com.p6spy.engine.spy.option.P6OptionsRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.StandardMBean;

import org.apache.commons.lang3.StringUtils;

/**
 * @author Jorge Díaz
 */
public class QueriesListenerAgentOptions
	extends StandardMBean
	implements P6LoadableOptions, QueriesListenerAgentOptionsMBean {

	public static final String EVENT_LISTENERS = "eventlisteners";

	public static final String EVENT_LISTENERS_STR = "eventlisteners_string";

	public static final Map<String, String> defaults =
		new HashMap<String, String>() {
			{
				put(EVENT_LISTENERS, StringUtils.EMPTY);
			}
		};

	public static QueriesListenerAgentOptions getActiveInstance() {
		P6ModuleManager p6ModuleManager = P6ModuleManager.getInstance();

		return p6ModuleManager.getOptions(QueriesListenerAgentOptions.class);
	}

	public QueriesListenerAgentOptions(
		final P6OptionsRepository optionsRepository) {

		super(QueriesListenerAgentOptionsMBean.class, false);

		_optionsRepository = optionsRepository;
	}

	@Override
	public Map<String, String> getDefaults() {
		return defaults;
	}

	@Override
	public Set<String> getEventListeners() {
		return _optionsRepository.getSet(String.class, EVENT_LISTENERS);
	}

	@Override
	public String getEventListenersString() {
		return _optionsRepository.get(String.class, EVENT_LISTENERS_STR);
	}

	@Override
	public void load(Map<String, String> options) {
		setEventListenersString(options.get(EVENT_LISTENERS));
	}

	@Override
	public void setEventListenersString(String eventListeners) {
		_optionsRepository.set(
			String.class, EVENT_LISTENERS_STR, eventListeners);
		_optionsRepository.setSet(
			String.class, EVENT_LISTENERS, eventListeners);
	}

	private final P6OptionsRepository _optionsRepository;

}