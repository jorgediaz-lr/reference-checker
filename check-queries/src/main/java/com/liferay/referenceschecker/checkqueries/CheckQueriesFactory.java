
package com.liferay.referenceschecker.checkqueries;

import com.p6spy.engine.event.JdbcEventListener;
import com.p6spy.engine.logging.P6LogOptions;
import com.p6spy.engine.spy.P6Factory;
import com.p6spy.engine.spy.P6LoadableOptions;
import com.p6spy.engine.spy.option.P6OptionsRepository;

public class CheckQueriesFactory implements P6Factory {

	@Override
	public JdbcEventListener getJdbcEventListener() {

		return CheckQueriesListener.INSTANCE;
	}

	@Override
	public P6LoadableOptions getOptions(P6OptionsRepository optionsRepository) {

		return new P6LogOptions(optionsRepository);
	}

}
