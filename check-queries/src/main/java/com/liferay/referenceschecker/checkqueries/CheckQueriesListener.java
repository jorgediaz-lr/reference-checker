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

import com.liferay.referenceschecker.checkqueries.Query.QueryType;

import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;

import java.sql.SQLException;

import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * @author Jorge Díaz
 */
public class CheckQueriesListener extends SimpleJdbcEventListener {

	public static final CheckQueriesListener INSTANCE =
		new CheckQueriesListener();

	@Override
	public void onAfterAnyAddBatch(
		StatementInformation statementInformation, long timeElapsedNanos,
		SQLException e) {

		if (e != null) {
			return;
		}

		processQuery(
			statementInformation.getConnectionInformation(),
			statementInformation.getSqlWithValues());
	}

	@Override
	public void onAfterAnyExecute(
		StatementInformation statementInformation, long timeElapsedNanos,
		SQLException e) {

		if (e != null) {
			return;
		}

		processQuery(
			statementInformation.getConnectionInformation(),
			statementInformation.getSqlWithValues());
	}

	@Override
	public void onAfterCommit(
		ConnectionInformation connectionInformation, long timeElapsedNanos,
		SQLException e) {

		resetThreadLocals();

		if (_log.isDebugEnabled()) {
			_log.debug(
				"After commit. Connection-id=" +
					connectionInformation.getConnectionId());
		};
	}

	@Override
	public void onAfterConnectionClose(
		ConnectionInformation connectionInformation, SQLException e) {

		resetThreadLocals();

		if (_log.isDebugEnabled()) {
			_log.debug(
				"After connection close. Connection-id=" +
					connectionInformation.getConnectionId());
		}
	}

	@Override
	public void onAfterExecuteBatch(
		StatementInformation statementInformation, long timeElapsedNanos,
		int[] updateCounts, SQLException e) {

		if (e != null) {
			return;
		}

		processQuery(
			statementInformation.getConnectionInformation(),
			statementInformation.getSqlWithValues());
	}

	@Override
	public void onAfterGetConnection(
		ConnectionInformation connectionInformation, SQLException e) {

		resetThreadLocals();

		if (_log.isDebugEnabled()) {
			_log.debug(
				"After get connection. Connection-id=" +
					connectionInformation.getConnectionId());
		}
	}

	@Override
	public void onAfterRollback(
		ConnectionInformation connectionInformation, long timeElapsedNanos,
		SQLException e) {

		resetThreadLocals();

		if (_log.isDebugEnabled()) {
			_log.debug(
				"After rollback. Connection-id=" +
					connectionInformation.getConnectionId());
		};
	}

	@Override
	public void onBeforeCommit(ConnectionInformation connectionInformation) {
		if (_log.isDebugEnabled()) {
			_log.debug(
				"Before commit. Connection-id=" +
					connectionInformation.getConnectionId());
		};

		logQueries(
			connectionInformation, _insertQueriesThreadLocal.get(),
			"Insert queries");
		logQueries(
			connectionInformation, _updateQueriesThreadLocal.get(),
			"Update queries");
		logQueries(
			connectionInformation, _deleteQueriesThreadLocal.get(),
			"Delete queries");
		logQueries(
			connectionInformation, _otherQueriesThreadLocal.get(),
			"Other queries");
	}

	protected void addQueryToThreadLocal(
		ThreadLocal<Set<Query>> queriesThreadLocal, Query query) {

		if (queriesThreadLocal.get() == null) {
			queriesThreadLocal.set(new TreeSet<Query>());
		}

		Set<Query> queries = queriesThreadLocal.get();

		queries.add(query);
	}

	protected void logQueries(
		ConnectionInformation connectionInformation, Set<Query> queriesSet,
		String message) {

		if ((queriesSet == null) || queriesSet.isEmpty()) {
			return;
		}

		if (!_log.isInfoEnabled()) {
			return;
		}

		int connectionId = connectionInformation.getConnectionId();

		_log.info(message + ". Connection-id=" + connectionId);

		for (Query query : queriesSet) {
			_log.info("Query: " + query);

			if (!_log.isDebugEnabled()) {
				continue;
			}

			_log.debug("\t modified tables: " + query.getModifiedTables());
			_log.debug("\t where: " + query.getWhere());
		}
	}

	protected void processQuery(
		ConnectionInformation connectionInformation, String sql) {

		int connectionId = connectionInformation.getConnectionId();

		Query query = new Query(sql);

		if ((query.getQueryType() == null) ||
			(query.getQueryType() == QueryType.SELECT)) {

			if (_log.isDebugEnabled()) {
				_log.debug(
					"Ignored Query. connectionId=" + connectionId + ", sql=" +
						query.getSQL());
			}

			return;
		}

		if (query.getQueryType() == QueryType.INSERT) {
			addQueryToThreadLocal(_insertQueriesThreadLocal, query);
		}
		else if (query.getQueryType() == QueryType.UPDATE) {
			addQueryToThreadLocal(_updateQueriesThreadLocal, query);
		}
		else if (query.getQueryType() == QueryType.DELETE) {
			addQueryToThreadLocal(_deleteQueriesThreadLocal, query);
		}
		else {
			addQueryToThreadLocal(_otherQueriesThreadLocal, query);
		}
	}

	protected void resetThreadLocals() {
		_insertQueriesThreadLocal.set(new TreeSet<Query>());
		_updateQueriesThreadLocal.set(new TreeSet<Query>());
		_deleteQueriesThreadLocal.set(new TreeSet<Query>());
		_otherQueriesThreadLocal.set(new TreeSet<Query>());
	}

	private static Logger _log = LogManager.getLogger(
		CheckQueriesListener.class);

	private ThreadLocal<Set<Query>> _deleteQueriesThreadLocal =
		new ThreadLocal<>();
	private ThreadLocal<Set<Query>> _insertQueriesThreadLocal =
		new ThreadLocal<>();
	private ThreadLocal<Set<Query>> _otherQueriesThreadLocal =
		new ThreadLocal<>();
	private ThreadLocal<Set<Query>> _updateQueriesThreadLocal =
		new ThreadLocal<>();

}