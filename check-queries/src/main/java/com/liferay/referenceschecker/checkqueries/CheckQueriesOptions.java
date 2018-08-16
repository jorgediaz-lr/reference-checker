
package com.liferay.referenceschecker.checkqueries;

import java.util.Collections;
import java.util.Map;

import com.p6spy.engine.spy.P6LoadableOptions;
import com.p6spy.engine.spy.option.P6OptionsRepository;

public class CheckQueriesOptions implements P6LoadableOptions {

	private final P6OptionsRepository optionsRepository;

	public CheckQueriesOptions(final P6OptionsRepository optionsRepository) {
		this.optionsRepository = optionsRepository;
	}

	@Override
	public void load(Map<String, String> options) {

	}

	@Override
	public Map<String, String> getDefaults() {

		return Collections.emptyMap();
	}
}
