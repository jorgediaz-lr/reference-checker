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

package com.liferay.referenceschecker.querieslistener;

import com.liferay.referenceschecker.querieslistener.Query.QueryType;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * @author Jorge Díaz
 */
public class DebugListener implements EventListener {

	@Override
	public void afterCommit(
		int connectionId, Connection connection, SQLException e) {

		if (_log.isDebugEnabled()) {
			_log.debug("After commit. Connection-id=" + connectionId);
		};
	}

	@Override
	public void afterConnectionClose(
		int connectionId, Connection connection, SQLException e) {

		if (_log.isDebugEnabled()) {
			_log.debug("After connection close. Connection-id=" + connectionId);
		}
	}

	@Override
	public void afterGetConnection(
		int connectionId, Connection connection, SQLException e) {

		if (_log.isDebugEnabled()) {
			_log.debug("After get connection. Connection-id=" + connectionId);
		}
	}

	@Override
	public void afterQuery(
		int connectionId, Connection connection, Query query, SQLException e) {

		processQuery(connectionId, query, e);
	}

	@Override
	public void afterRollback(
		int connectionId, Connection connection, SQLException e) {

		if (_log.isDebugEnabled()) {
			_log.debug("After rollback. Connection-id=" + connectionId);
		};
	}

	@Override
	public void beforeCommit(int connectionId, Connection connection) {
		if (_log.isDebugEnabled()) {
			_log.debug("Before commit. Connection-id=" + connectionId);
		};

		logQueries(
			connectionId, _insertQueriesThreadLocal.get(), "Insert queries");
		logQueries(
			connectionId, _updateQueriesThreadLocal.get(), "Update queries");
		logQueries(
			connectionId, _deleteQueriesThreadLocal.get(), "Delete queries");
		logQueries(
			connectionId, _otherQueriesThreadLocal.get(), "Other queries");
	}

	@Override
	public void beforeGetConnection(int connectionId, Connection connection) {
		if (_log.isDebugEnabled()) {
			_log.debug("Before get connection. Connection-id=" + connectionId);
		};
	}

	@Override
	public void beforeQuery(
		int connectionId, Connection connection, Query query) {
	}

	@Override
	public void beforeRollback(int connectionId, Connection connection) {
	}

	@Override
	public void resetThreadLocals() {
		_insertQueriesThreadLocal.set(new TreeSet<Query>());
		_updateQueriesThreadLocal.set(new TreeSet<Query>());
		_deleteQueriesThreadLocal.set(new TreeSet<Query>());
		_otherQueriesThreadLocal.set(new TreeSet<Query>());
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
		int connectionId, Set<Query> queriesSet, String message) {

		if ((queriesSet == null) || queriesSet.isEmpty()) {
			return;
		}

		if (!_log.isInfoEnabled()) {
			return;
		}

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

	protected void processQuery(int connectionId, Query query, SQLException e) {
		if (e != null) {
			return;
		}

		if (query.isReadOnly()) {
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

	private static Logger _log = LogManager.getLogger(DebugListener.class);

	private ThreadLocal<Set<Query>> _deleteQueriesThreadLocal =
		new ThreadLocal<>();
	private ThreadLocal<Set<Query>> _insertQueriesThreadLocal =
		new ThreadLocal<>();
	private ThreadLocal<Set<Query>> _otherQueriesThreadLocal =
		new ThreadLocal<>();
	private ThreadLocal<Set<Query>> _updateQueriesThreadLocal =
		new ThreadLocal<>();

}