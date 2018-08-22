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

import com.liferay.referenceschecker.querieslistener.DebugListener;
import com.liferay.referenceschecker.querieslistener.EventListener;
import com.liferay.referenceschecker.querieslistener.EventListenerRegistry;
import com.liferay.referenceschecker.querieslistener.Query;

import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author Jorge Díaz
 */
public class QueriesListenerAgent extends SimpleJdbcEventListener {

	public static final QueriesListenerAgent INSTANCE =
		new QueriesListenerAgent();

	@Override
	public void onAfterAnyAddBatch(
		StatementInformation statementInformation, long timeElapsedNanos,
		SQLException e) {

		afterQuery(
			statementInformation.getConnectionInformation(),
			statementInformation.getSqlWithValues(), e);
	}

	@Override
	public void onAfterAnyExecute(
		StatementInformation statementInformation, long timeElapsedNanos,
		SQLException e) {

		afterQuery(
			statementInformation.getConnectionInformation(),
			statementInformation.getSqlWithValues(), e);
	}

	@Override
	public void onAfterCommit(
		ConnectionInformation connectionInformation, long timeElapsedNanos,
		SQLException e) {

		int connectionId = connectionInformation.getConnectionId();

		Connection connection = connectionInformation.getConnection();

		for (EventListener eventListener :
				_eventListenerRegistry.getEventListeners()) {

			eventListener.afterCommit(connectionId, connection, e);
		}
	}

	@Override
	public void onAfterConnectionClose(
		ConnectionInformation connectionInformation, SQLException e) {

		int connectionId = connectionInformation.getConnectionId();

		Connection connection = connectionInformation.getConnection();

		for (EventListener eventListener :
				_eventListenerRegistry.getEventListeners()) {

			eventListener.afterConnectionClose(connectionId, connection, e);
		}
	}

	@Override
	public void onAfterExecuteBatch(
		StatementInformation statementInformation, long timeElapsedNanos,
		int[] updateCounts, SQLException e) {

		afterQuery(
			statementInformation.getConnectionInformation(),
			statementInformation.getSqlWithValues(), e);
	}

	@Override
	public void onAfterGetConnection(
		ConnectionInformation connectionInformation, SQLException e) {

		int connectionId = connectionInformation.getConnectionId();

		Connection connection = connectionInformation.getConnection();

		for (EventListener eventListener :
				_eventListenerRegistry.getEventListeners()) {

			eventListener.afterGetConnection(connectionId, connection, e);
		}
	}

	@Override
	public void onAfterRollback(
		ConnectionInformation connectionInformation, long timeElapsedNanos,
		SQLException e) {

		int connectionId = connectionInformation.getConnectionId();

		Connection connection = connectionInformation.getConnection();

		for (EventListener eventListener :
				_eventListenerRegistry.getEventListeners()) {

			eventListener.afterRollback(connectionId, connection, e);
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
		SQLException e) {

		int connectionId = connectionInformation.getConnectionId();

		Connection connection = connectionInformation.getConnection();

		Query query = new Query(sql);

		for (EventListener eventListener :
				_eventListenerRegistry.getEventListeners()) {

			eventListener.afterQuery(connectionId, connection, query, e);
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
		_eventListenerRegistry =
			EventListenerRegistry.getEventListenerRegistry();

		_eventListenerRegistry.register(new DebugListener());
	}

	private EventListenerRegistry _eventListenerRegistry;

}