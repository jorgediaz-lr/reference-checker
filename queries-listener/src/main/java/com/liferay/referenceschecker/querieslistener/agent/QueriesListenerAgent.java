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

import com.liferay.referenceschecker.querieslistener.EventListener;
import com.liferay.referenceschecker.querieslistener.EventListenerRegistry;
import com.liferay.referenceschecker.querieslistener.Query;

import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.Set;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * @author Jorge Díaz
 */
public class QueriesListenerAgent extends SimpleJdbcEventListener {

	public static final QueriesListenerAgent INSTANCE =
		new QueriesListenerAgent();

	@Override
	public void onAfterAnyAddBatch(
		StatementInformation statementInformation, long timeElapsedNanos,
		SQLException sqle) {

		afterQuery(
			statementInformation.getConnectionInformation(),
			statementInformation.getSqlWithValues(), sqle);
	}

	@Override
	public void onAfterAnyExecute(
		StatementInformation statementInformation, long timeElapsedNanos,
		SQLException sqle) {

		afterQuery(
			statementInformation.getConnectionInformation(),
			statementInformation.getSqlWithValues(), sqle);
	}

	@Override
	public void onAfterCommit(
		ConnectionInformation connectionInformation, long timeElapsedNanos,
		SQLException sqle) {

		int connectionId = connectionInformation.getConnectionId();

		Connection connection = connectionInformation.getConnection();

		for (EventListener eventListener :
				_eventListenerRegistry.getEventListeners()) {

			try {
				eventListener.afterCommit(connectionId, connection, sqle);
			}
			catch (Exception e) {
				_log.error(e, e);
			}
			finally {
				eventListener.resetThreadLocals();
			}
		}
	}

	@Override
	public void onAfterConnectionClose(
		ConnectionInformation connectionInformation, SQLException sqle) {

		int connectionId = connectionInformation.getConnectionId();

		Connection connection = connectionInformation.getConnection();

		for (EventListener eventListener :
				_eventListenerRegistry.getEventListeners()) {

			try {
				eventListener.afterConnectionClose(
					connectionId, connection, sqle);
			}
			catch (Exception e) {
				_log.error(e, e);
			}
			finally {
				eventListener.resetThreadLocals();
			}
		}
	}

	@Override
	public void onAfterExecuteBatch(
		StatementInformation statementInformation, long timeElapsedNanos,
		int[] updateCounts, SQLException sqle) {

		afterQuery(
			statementInformation.getConnectionInformation(),
			statementInformation.getSqlWithValues(), sqle);
	}

	@Override
	public void onAfterGetConnection(
		ConnectionInformation connectionInformation, SQLException sqle) {

		int connectionId = connectionInformation.getConnectionId();

		Connection connection = connectionInformation.getConnection();

		for (EventListener eventListener :
				_eventListenerRegistry.getEventListeners()) {

			try {
				eventListener.afterGetConnection(
					connectionId, connection, sqle);
			}
			catch (Exception e) {
				_log.error(e, e);
			}
			finally {
				eventListener.resetThreadLocals();
			}
		}
	}

	@Override
	public void onAfterRollback(
		ConnectionInformation connectionInformation, long timeElapsedNanos,
		SQLException sqle) {

		int connectionId = connectionInformation.getConnectionId();

		Connection connection = connectionInformation.getConnection();

		for (EventListener eventListener :
				_eventListenerRegistry.getEventListeners()) {

			try {
				eventListener.afterRollback(connectionId, connection, sqle);
			}
			catch (Exception e) {
				_log.error(e, e);
			}
			finally {
				eventListener.resetThreadLocals();
			}
		}
	}

	@Override
	public void onBeforeAnyAddBatch(StatementInformation statementInformation) {
		beforeQuery(
			statementInformation.getConnectionInformation(),
			statementInformation.getSqlWithValues());
	}

	@Override
	public void onBeforeAnyExecute(StatementInformation statementInformation) {
		beforeQuery(
			statementInformation.getConnectionInformation(),
			statementInformation.getSqlWithValues());
	}

	@Override
	public void onBeforeCommit(ConnectionInformation connectionInformation) {
		int connectionId = connectionInformation.getConnectionId();

		Connection connection = connectionInformation.getConnection();

		for (EventListener eventListener :
				_eventListenerRegistry.getEventListeners()) {

			eventListener.beforeCommit(connectionId, connection);
		}
	}

	@Override
	public void onBeforeExecuteBatch(
		StatementInformation statementInformation) {

		beforeQuery(
			statementInformation.getConnectionInformation(),
			statementInformation.getSqlWithValues());
	}

	@Override
	public void onBeforeGetConnection(
		ConnectionInformation connectionInformation) {

		int connectionId = connectionInformation.getConnectionId();

		Connection connection = connectionInformation.getConnection();

		for (EventListener eventListener :
				_eventListenerRegistry.getEventListeners()) {

			eventListener.beforeGetConnection(connectionId, connection);
		}
	}

	@Override
	public void onBeforeRollback(ConnectionInformation connectionInformation) {
		int connectionId = connectionInformation.getConnectionId();

		Connection connection = connectionInformation.getConnection();

		for (EventListener eventListener :
				_eventListenerRegistry.getEventListeners()) {

			eventListener.beforeRollback(connectionId, connection);
		}
	}

	protected void afterQuery(
		ConnectionInformation connectionInformation, String sql,
		SQLException sqle) {

		int connectionId = connectionInformation.getConnectionId();

		Connection connection = connectionInformation.getConnection();

		Query query = new Query(sql);

		for (EventListener eventListener :
				_eventListenerRegistry.getEventListeners()) {

			eventListener.afterQuery(connectionId, connection, query, sqle);
		}
	}

	protected void beforeQuery(
		ConnectionInformation connectionInformation, String sql) {

		int connectionId = connectionInformation.getConnectionId();

		Connection connection = connectionInformation.getConnection();

		Query query = new Query(sql);

		for (EventListener eventListener :
				_eventListenerRegistry.getEventListeners()) {

			eventListener.beforeQuery(connectionId, connection, query);
		}
	}

	private QueriesListenerAgent() {
		if (_log == null) {
			_log = LogManager.getLogger(QueriesListenerAgent.class);
		}

		QueriesListenerAgentOptions queriesListenerAgentOptions =
			QueriesListenerAgentOptions.getActiveInstance();

		Set<String> eventListenerList =
			queriesListenerAgentOptions.getEventListeners();

		if (eventListenerList == null) {
			return;
		}

		_eventListenerRegistry =
			EventListenerRegistry.getEventListenerRegistry();

		for (String eventListenerClassName : eventListenerList) {
			try {
				Class<?> eventListenerClass = Class.forName(
					eventListenerClassName);

				EventListener eventListener =
					(EventListener)eventListenerClass.newInstance();

				_eventListenerRegistry.register(eventListener);
			}
			catch (Throwable t) {
				_log.error("Error loading class: " + eventListenerClassName, t);
			}
		}
	}

	private static Logger _log = LogManager.getLogger(
		QueriesListenerAgent.class);

	private EventListenerRegistry _eventListenerRegistry;

}