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

package com.liferay.referenceschecker.listener;

import com.liferay.referenceschecker.ReferencesChecker;
import com.liferay.referenceschecker.querieslistener.EventListener;
import com.liferay.referenceschecker.querieslistener.EventListenerRegistry;
import com.liferay.referenceschecker.querieslistener.Query;
import com.liferay.referenceschecker.querieslistener.Query.QueryType;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.Set;
import java.util.TreeSet;

import net.sf.jsqlparser.statement.drop.Drop;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * @author Jorge Díaz
 */
public class ReferencesCheckerInfrastructureListener implements EventListener {

	@Override
	public void afterCommit(
		int connectionId, Connection connection, SQLException e) {

		refreshReferencesChecker(
			connection, _updatedTables.get(), _deletedTables.get(),
			_regenerateModelUtil.get());
	}

	@Override
	public void afterConnectionClose(
		int connectionId, Connection connection, SQLException e) {
	}

	@Override
	public void afterGetConnection(
		int connectionId, Connection connection, SQLException sqle) {

		if ((referencesChecker != null) || (sqle != null)) {
			return;
		}

		try {
			initReferencesChecker(connection);
		}
		catch (Exception e) {
			_log.error(
				"Error creating ReferencesChecker instance: " + e.getMessage(),
				e);
		}
	}

	@Override
	public void afterQuery(
		int connectionId, Connection connection, Query query, SQLException e) {

		if (e != null) {
			return;
		}

		if (query.isReadOnly()) {
			return;
		}

		if ((query.getQueryType() == QueryType.CREATE_TABLE) ||
			(query.getQueryType() == QueryType.ALTER)) {

			Set<String> updatedTables = _updatedTables.get();

			updatedTables.addAll(query.getModifiedTables());

			_regenerateModelUtil.set(Boolean.TRUE);
		}
		else if (query.getQueryType() == QueryType.DROP) {
			Drop drop = (Drop)query.getStatement();

			if (StringUtils.equalsIgnoreCase("TABLE", drop.getType())) {
				Set<String> deletedTables = _deletedTables.get();

				deletedTables.addAll(query.getModifiedTables());

				_regenerateModelUtil.set(Boolean.TRUE);
			}
		}

		if (!_regenerateModelUtil.get()) {
			for (String table : query.getModifiedTables()) {
				if (StringUtils.equalsIgnoreCase("ClassName_", table)) {
					_regenerateModelUtil.set(Boolean.TRUE);

					break;
				}
			}
		}
	}

	@Override
	public void afterRollback(
		int connectionId, Connection connection, SQLException e) {
	}

	@Override
	public void beforeCommit(int connectionId, Connection connection) {
	}

	@Override
	public void beforeGetConnection(int connectionId, Connection connection) {
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
		_regenerateModelUtil.set(Boolean.FALSE);
		_updatedTables.set(new TreeSet<String>());
		_deletedTables.set(new TreeSet<String>());
	}

	protected static ReferencesChecker getReferencesChecker() {
		EventListenerRegistry eventListenerRegistry =
			EventListenerRegistry.getEventListenerRegistry();

		EventListener eventListener = eventListenerRegistry.getEventListener(
			ReferencesCheckerInfrastructureListener.class.getName());

		ReferencesCheckerInfrastructureListener
			referencesCheckerInfrastructureListener =
				(ReferencesCheckerInfrastructureListener)eventListener;

		return referencesCheckerInfrastructureListener.referencesChecker;
	}

	protected synchronized void initReferencesChecker(Connection connection)
		throws SQLException {

		if (referencesChecker != null) {
			return;
		}

		ReferencesChecker referencesCheckerAux = new ReferencesChecker(
			connection);

		referencesCheckerAux.initModelUtil(connection);
		referencesCheckerAux.initTableUtil(connection);

		referencesChecker = referencesCheckerAux;
	}

	protected void refreshReferencesChecker(
		Connection connection, Set<String> updatedTables,
		Set<String> deletedTables, boolean regenerateModelUtil) {

		try {
			referencesChecker.addTables(connection, updatedTables);
		}
		catch (SQLException sqle) {
			_log.error(
				"Error adding tables to tableUtil object" +
					sqle.getMessage(),
				sqle);
		}

		referencesChecker.removeTables(deletedTables);

		if (regenerateModelUtil) {
			try {
				referencesChecker.reloadModelUtil(connection);
			}
			catch (SQLException sqle) {
				_log.error(
					"Error updating modelUtil object" + sqle.getMessage(),
					sqle);
			}
		}
	}

	protected ReferencesChecker referencesChecker;

	private static Logger _log = LogManager.getLogger(
		ReferencesCheckerInfrastructureListener.class);

	private ThreadLocal<Set<String>> _deletedTables =
		new ThreadLocal<Set<String>>() {

			@Override protected Set<String> initialValue() {
				return new TreeSet<String>();
			}

		};

	private ThreadLocal<Boolean> _regenerateModelUtil =
		new ThreadLocal<Boolean>() {

			@Override protected Boolean initialValue() {
				return Boolean.FALSE;
			}

		};

	private ThreadLocal<Set<String>> _updatedTables =
		new ThreadLocal<Set<String>>() {

			@Override protected Set<String> initialValue() {
				return new TreeSet<String>();
			}

		};

}