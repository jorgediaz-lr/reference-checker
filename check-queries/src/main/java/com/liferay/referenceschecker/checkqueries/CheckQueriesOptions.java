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

package com.liferay.referenceschecker.checkqueries;

import com.p6spy.engine.spy.P6LoadableOptions;
import com.p6spy.engine.spy.option.P6OptionsRepository;

import java.util.Collections;
import java.util.Map;

/**
 * @author Jorge Díaz
 */
public class CheckQueriesOptions implements P6LoadableOptions {

	public CheckQueriesOptions(final P6OptionsRepository optionsRepository) {
		_optionsRepository = optionsRepository;
	}

	@Override
	public Map<String, String> getDefaults() {
		return Collections.emptyMap();
	}

	@Override
	public void load(Map<String, String> options) {
	}

	private final P6OptionsRepository _optionsRepository;

}