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

import java.sql.Connection;
import java.sql.SQLException;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

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
		}
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
		}
	}

	@Override
	public void beforeCommit(int connectionId, Connection connection) {
		if (_log.isDebugEnabled()) {
			_log.debug("Before commit. Connection-id=" + connectionId);
		}

		logQueries(
			connectionId, _insertQueriesMap.get(connectionId),
			"Insert queries");
		logQueries(
			connectionId, _updateQueriesMap.get(connectionId),
			"Update queries");
		logQueries(
			connectionId, _deleteQueriesMap.get(connectionId),
			"Delete queries");
		logQueries(
			connectionId, _otherQueriesMap.get(connectionId), "Other queries");
	}

	@Override
	public void beforeGetConnection(int connectionId, Connection connection) {
		if (_log.isDebugEnabled()) {
			_log.debug("Before get connection. Connection-id=" + connectionId);
		}
	}

	@Override
	public void beforeQuery(
		int connectionId, Connection connection, Query query) {
	}

	@Override
	public void beforeRollback(int connectionId, Connection connection) {
	}

	@Override
	public void deleteConnectionData(int connectionId) {
		_insertQueriesMap.remove(connectionId);
		_updateQueriesMap.remove(connectionId);
		_deleteQueriesMap.remove(connectionId);
		_otherQueriesMap.remove(connectionId);
	}

	@Override
	public void resetConnectionData(int connectionId) {
		_insertQueriesMap.put(connectionId, new TreeSet<Query>());
		_updateQueriesMap.put(connectionId, new TreeSet<Query>());
		_deleteQueriesMap.put(connectionId, new TreeSet<Query>());
		_otherQueriesMap.put(connectionId, new TreeSet<Query>());
	}

	protected void addQueryToConnectionMap(
		Integer connectionId, Map<Integer, Set<Query>> queriesMap,
		Query query) {

		if (queriesMap.get(connectionId) == null) {
			queriesMap.put(connectionId, new TreeSet<Query>());
		}

		Set<Query> queries = queriesMap.get(connectionId);

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

		if (query.getQueryType() == Query.QueryType.INSERT) {
			addQueryToConnectionMap(connectionId, _insertQueriesMap, query);
		}
		else if (query.getQueryType() == Query.QueryType.UPDATE) {
			addQueryToConnectionMap(connectionId, _updateQueriesMap, query);
		}
		else if (query.getQueryType() == Query.QueryType.DELETE) {
			addQueryToConnectionMap(connectionId, _deleteQueriesMap, query);
		}
		else {
			addQueryToConnectionMap(connectionId, _otherQueriesMap, query);
		}
	}

	private static Logger _log = LogManager.getLogger(DebugListener.class);

	private Map<Integer, Set<Query>> _deleteQueriesMap =
		new ConcurrentHashMap<>();
	private Map<Integer, Set<Query>> _insertQueriesMap =
		new ConcurrentHashMap<>();
	private Map<Integer, Set<Query>> _otherQueriesMap =
		new ConcurrentHashMap<>();
	private Map<Integer, Set<Query>> _updateQueriesMap =
		new ConcurrentHashMap<>();

}