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

import com.liferay.referenceschecker.OutputUtil;
import com.liferay.referenceschecker.ReferencesChecker;
import com.liferay.referenceschecker.config.Configuration;
import com.liferay.referenceschecker.dao.Table;
import com.liferay.referenceschecker.querieslistener.EventListener;
import com.liferay.referenceschecker.querieslistener.EventListenerRegistry;
import com.liferay.referenceschecker.querieslistener.Query;
import com.liferay.referenceschecker.ref.MissingReferences;
import com.liferay.referenceschecker.ref.Reference;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * @author Jorge Díaz
 */
public class ReferencesCheckerInfrastructureListener implements EventListener {

	@Override
	public void afterCommit(
		int connectionId, Connection connection, SQLException sqle) {

		if (sqle != null) {
			return;
		}

		try {
			if (_regenerateReferencesChecker.get()) {
				forceInitReferencesChecker(connection);
			}

			if (referencesChecker == null) {
				initReferencesChecker(connection);
			}
		}
		catch (Exception e) {
			_log.error(
				"Error creating ReferencesChecker instance: " + e.getMessage(),
				e);

			return;
		}

		ReferencesChecker referencesChecker = this.referencesChecker;

		if (referencesChecker == null) {
			return;
		}

		refreshReferencesChecker(
			connection, referencesChecker, _modifiedTables.get(),
			_droppedTables.get(), _regenerateModelUtil.get());

		Set<String> insertedTablesLowerCase = _insertedTablesLowerCase.get();
		Set<String> updatedTablesLowerCase = _updatedTablesLowerCase.get();
		Set<String> deletedTablesLowerCase = _deletedTablesLowerCase.get();

		if (insertedTablesLowerCase.isEmpty() &&
			updatedTablesLowerCase.isEmpty() &&
			deletedTablesLowerCase.isEmpty()) {

			return;
		}

		for (String insertedTable : insertedTablesLowerCase) {
			referencesChecker.cleanEmptyTableCacheOnInsert(insertedTable);
		}

		for (String updatedTable : updatedTablesLowerCase) {
			referencesChecker.cleanEmptyTableCacheOnUpdate(updatedTable);
		}

		for (String deletedTable : deletedTablesLowerCase) {
			referencesChecker.cleanEmptyTableCacheOnDelete(deletedTable);
		}

		checkMissingReferences(
			connection, referencesChecker, insertedTablesLowerCase,
			updatedTablesLowerCase, _updatedTablesColumns.get(),
			deletedTablesLowerCase);
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

		if ((query.getQueryType() == Query.QueryType.CREATE_TABLE) ||
			(query.getQueryType() == Query.QueryType.ALTER)) {

			Set<String> updatedTables = _modifiedTables.get();

			updatedTables.addAll(query.getModifiedTables());

			_regenerateModelUtil.set(Boolean.TRUE);
		}
		else if (query.getQueryType() == Query.QueryType.DROP_TABLE) {
			Set<String> deletedTables = _droppedTables.get();

			deletedTables.addAll(query.getModifiedTables());

			_regenerateModelUtil.set(Boolean.TRUE);
		}
		else if (query.getQueryType() == Query.QueryType.INSERT) {
			Set<String> insertedTables = _insertedTablesLowerCase.get();

			insertedTables.addAll(query.getModifiedTablesLowerCase());
		}
		else if (query.getQueryType() == Query.QueryType.DELETE) {
			Set<String> deletedTables = _deletedTablesLowerCase.get();

			deletedTables.addAll(query.getModifiedTablesLowerCase());
		}
		else if (query.getQueryType() == Query.QueryType.UPDATE) {
			Set<String> updatedTables = _updatedTablesLowerCase.get();

			Map<String, Set<String>> updatedTablesColumns =
				_updatedTablesColumns.get();

			for (String modifiedTable : query.getModifiedTablesLowerCase()) {
				updatedTables.add(modifiedTable);

				Set<String> columnSet = updatedTablesColumns.get(modifiedTable);

				if (columnSet == null) {
					columnSet = new TreeSet<>();

					updatedTablesColumns.put(modifiedTable, columnSet);
				}

				columnSet.addAll(query.getModifiedColumns());
			}
		}

		if (!_regenerateReferencesChecker.get() &&
			hasUpdatedReleaseBuildNumber(query)) {

			_regenerateReferencesChecker.set(Boolean.TRUE);
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
		if (referencesChecker == null) {
			return;
		}

		for (String insertedTable : _insertedTablesLowerCase.get()) {
			referencesChecker.cleanEmptyTableCacheOnInsert(insertedTable);
		}

		for (String updatedTable : _updatedTablesLowerCase.get()) {
			referencesChecker.cleanEmptyTableCacheOnUpdate(updatedTable);
		}

		for (String deletedTable : _deletedTablesLowerCase.get()) {
			referencesChecker.cleanEmptyTableCacheOnDelete(deletedTable);
		}
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
		_regenerateReferencesChecker.set(Boolean.FALSE);
		_regenerateModelUtil.set(Boolean.FALSE);
		_modifiedTables.set(new TreeSet<String>());
		_droppedTables.set(new TreeSet<String>());
		_insertedTablesLowerCase.set(new TreeSet<String>());
		_deletedTablesLowerCase.set(new TreeSet<String>());
		_updatedTablesLowerCase.set(new TreeSet<String>());
		_updatedTablesColumns.set(new HashMap<String, Set<String>>());
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

	protected void checkMissingReferences(
		Connection connection, ReferencesChecker referencesChecker,
		Set<String> insertedTablesLowerCase, Set<String> updatedTablesLowerCase,
		Map<String, Set<String>> updatedTablesColumns,
		Set<String> deletedTablesLowerCase) {

		boolean abortCheck = false;
		boolean cleanUp = false;

		Thread currentThread = Thread.currentThread();

		for (StackTraceElement stackTraceElement :
				currentThread.getStackTrace()) {

			if (_cleanUpMethod(
					stackTraceElement.getClassName(),
					stackTraceElement.getMethodName())) {

				cleanUp = true;
			}

			if (_ignoreMethod(
					stackTraceElement.getClassName(),
					stackTraceElement.getMethodName())) {

				abortCheck = true;
			}

			if (cleanUp && abortCheck) {
				break;
			}
		}

		if (abortCheck && !cleanUp) {
			return;
		}

		checkMissingReferences(
			connection, referencesChecker, insertedTablesLowerCase,
			updatedTablesLowerCase, updatedTablesColumns,
			deletedTablesLowerCase, abortCheck, cleanUp, 0);
	}

	protected void checkMissingReferences(
		Connection connection, ReferencesChecker referencesChecker,
		Set<String> insertedTablesLowerCase, Set<String> updatedTablesLowerCase,
		Map<String, Set<String>> updatedTablesColumns,
		Set<String> deletedTablesLowerCase, boolean abortCheck, boolean cleanUp,
		int depth) {

		if (depth > _CHECK_MAX_DEPTH) {
			_log.error("Reached check max depth, aborting...");
		}

		Collection<Reference> references = referencesChecker.getReferences(
			connection, true);

		Collection<Reference> referencesToCheck = new HashSet<>();

		for (Reference reference : references) {
			com.liferay.referenceschecker.dao.Query destinationQuery =
				reference.getDestinationQuery();

			if (destinationQuery == null) {
				continue;
			}

			com.liferay.referenceschecker.dao.Query originQuery =
				reference.getOriginQuery();

			Table originTable = originQuery.getTable();

			Table destinationTable = destinationQuery.getTable();

			String originTableName = originTable.getTableNameLowerCase();
			String destinationTableName =
				destinationTable.getTableNameLowerCase();

			if (insertedTablesLowerCase.contains(originTableName) ||
				deletedTablesLowerCase.contains(destinationTableName)) {

				referencesToCheck.add(reference);
			}

			if (updatedTablesLowerCase.contains(originTableName)) {
				Set<String> updatedColumns = updatedTablesColumns.get(
					originTableName);

				if (_queryHasAnyUpdatedColumn(originQuery, updatedColumns)) {
					referencesToCheck.add(reference);
				}
			}

			if (updatedTablesLowerCase.contains(destinationTableName)) {
				Set<String> updatedColumns = updatedTablesColumns.get(
					destinationTableName);

				if (_queryHasAnyUpdatedColumn(
						destinationQuery, updatedColumns)) {

					referencesToCheck.add(reference);
				}
			}
		}

		Collection<MissingReferences> missingReferences =
			referencesChecker.execute(connection, referencesToCheck);

		if (cleanUp) {
			missingReferences = cleanUp(
				connection, referencesChecker, missingReferences, abortCheck,
				depth);
		}

		if (abortCheck || missingReferences.isEmpty()) {
			return;
		}

		List<String> outputList = OutputUtil.generateCSVOutputCheckReferences(
			missingReferences, -1);

		for (String output : outputList) {
			_log.warn(output);
		}

		Configuration.Listener listenerConfiguration = getListenerConfiguration(
			referencesChecker);

		if (listenerConfiguration.getPrintThreadDump()) {
			_log.warn("stacktrace", new Exception());
		}
	}

	protected Collection<MissingReferences> cleanUp(
		Connection connection, ReferencesChecker referencesChecker,
		Collection<MissingReferences> missingReferencesList, boolean abortCheck,
		int depth) {

		List<MissingReferences> notProcessed = new ArrayList<>();
		Set<String> insertedTablesLowerCase = new TreeSet<>();
		Set<String> deletedTablesLowerCase = new TreeSet<>();
		Set<String> updatedTablesLowerCase = new TreeSet<>();
		Map<String, Set<String>> updatedTablesColumns = new HashMap<>();

		for (MissingReferences missingReferences : missingReferencesList) {
			Reference reference = missingReferences.getReference();

			String fixAction = reference.getFixAction();

			com.liferay.referenceschecker.dao.Query query =
				reference.getOriginQuery();

			Table table = query.getTable();

			if (fixAction.equals("delete")) {
				deletedTablesLowerCase.add(table.getTableNameLowerCase());
			}
			else if (fixAction.equals("update")) {
				String modifiedTable = table.getTableNameLowerCase();

				updatedTablesLowerCase.add(modifiedTable);

				Set<String> columnSet = updatedTablesColumns.get(modifiedTable);

				if (columnSet == null) {
					columnSet = new TreeSet<>();

					updatedTablesColumns.put(modifiedTable, columnSet);
				}

				columnSet.addAll(query.getColumns());
			}
			else {
				notProcessed.add(missingReferences);

				continue;
			}

			try {
				referencesChecker.executeCleanUp(connection, missingReferences);
			}
			catch (SQLException sqlException) {
				MissingReferences missingReferencesError =
					new MissingReferences(reference, sqlException);

				notProcessed.add(missingReferencesError);
			}
		}

		if (insertedTablesLowerCase.isEmpty() &&
			updatedTablesLowerCase.isEmpty() &&
			deletedTablesLowerCase.isEmpty()) {

			return notProcessed;
		}

		checkMissingReferences(
			connection, referencesChecker, insertedTablesLowerCase,
			updatedTablesLowerCase, updatedTablesColumns,
			deletedTablesLowerCase, abortCheck, true, depth + 1);

		return notProcessed;
	}

	protected synchronized void forceInitReferencesChecker(
			Connection connection)
		throws SQLException {

		ReferencesChecker referencesCheckerAux = new ReferencesChecker(
			connection);

		if (referencesCheckerAux.getConfiguration() == null) {
			return;
		}

		referencesCheckerAux.initModelUtil(connection);
		referencesCheckerAux.initTableUtil(connection);

		referencesChecker = referencesCheckerAux;
	}

	protected Configuration.Listener getListenerConfiguration(
		ReferencesChecker referencesChecker) {

		Configuration configuration = referencesChecker.getConfiguration();

		return configuration.getListener();
	}

	protected boolean hasUpdatedReleaseBuildNumber(Query query) {
		if ((query.getQueryType() != Query.QueryType.INSERT) &&
			(query.getQueryType() != Query.QueryType.UPDATE)) {

			return false;
		}

		boolean releaseTableChanged = false;

		for (String table : query.getModifiedTables()) {
			if (StringUtils.equalsIgnoreCase("Release_", table)) {
				releaseTableChanged = true;

				break;
			}
		}

		if (!releaseTableChanged) {
			return false;
		}

		Map<String, Object> valuesMap = new TreeMap<>(
			String.CASE_INSENSITIVE_ORDER);

		valuesMap.putAll(query.getModifiedValues());

		if (query.getQueryType() == Query.QueryType.INSERT) {
			Object servletContextName = valuesMap.get("servletContextName");

			if (!StringUtils.equalsIgnoreCase(
					"'portal'", (String)servletContextName)) {

				return false;
			}
		}

		Object buildNumber = valuesMap.get("buildNumber");

		if (buildNumber instanceof Number) {
			Number number = (Number)buildNumber;

			if (number.longValue() > 6000L) {
				return true;
			}
		}

		return false;
	}

	protected synchronized void initReferencesChecker(Connection connection)
		throws SQLException {

		if (referencesChecker != null) {
			return;
		}

		forceInitReferencesChecker(connection);
	}

	protected void refreshReferencesChecker(
		Connection connection, ReferencesChecker referencesChecker,
		Set<String> updatedTables, Set<String> deletedTables,
		boolean regenerateModelUtil) {

		try {
			referencesChecker.addTables(connection, updatedTables);
		}
		catch (SQLException sqle) {
			_log.error(
				"Error adding tables to tableUtil object" + sqle.getMessage(),
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

	private boolean _cleanUpMethod(String className, String methodName) {
		Configuration.Listener listenerConfiguration = getListenerConfiguration(
			referencesChecker);

		for (String cleanUpClass : listenerConfiguration.getCleanUpClasses()) {
			if (className.endsWith(cleanUpClass)) {
				if (_log.isInfoEnabled()) {
					_log.info(className + " is in the cleanUp classes list");
				}

				return true;
			}
		}

		String fullMethodName = className + "." + methodName;

		for (String cleanUpMethods :
				listenerConfiguration.getCleanUpMethods()) {

			if (fullMethodName.endsWith(cleanUpMethods)) {
				if (_log.isInfoEnabled()) {
					_log.info(
						fullMethodName + " is in the cleanUp methods list");
				}

				return true;
			}
		}

		return false;
	}

	private boolean _ignoreMethod(String className, String methodName) {
		Configuration.Listener listenerConfiguration = getListenerConfiguration(
			referencesChecker);

		for (String ignoredClass : listenerConfiguration.getIgnoredClasses()) {
			if (className.endsWith(ignoredClass)) {
				if (_log.isInfoEnabled()) {
					_log.info(className + " is in the ignored classes list");
				}

				return true;
			}
		}

		String fullMethodName = className + "." + methodName;

		for (String ignoredMethods :
				listenerConfiguration.getIgnoredMethods()) {

			if (fullMethodName.endsWith(ignoredMethods)) {
				if (_log.isInfoEnabled()) {
					_log.info(
						fullMethodName + " is in the ignored methods list");
				}

				return true;
			}
		}

		return false;
	}

	private boolean _queryHasAnyUpdatedColumn(
		com.liferay.referenceschecker.dao.Query query, Set<String> columns) {

		if (columns == null) {
			return false;
		}

		Table table = query.getTable();

		for (String queryColumn : query.getColumns()) {
			for (String column : columns) {
				if (StringUtils.equalsIgnoreCase(column, queryColumn) &&
					!StringUtils.equalsIgnoreCase(
						column, table.getPrimaryKey())) {

					return true;
				}
			}
		}

		return false;
	}

	private static final int _CHECK_MAX_DEPTH = 4;

	private static Logger _log = LogManager.getLogger(
		ReferencesCheckerInfrastructureListener.class);

	private ThreadLocal<Set<String>> _deletedTablesLowerCase =
		new ThreadLocal<Set<String>>() {

			@Override
			protected Set<String> initialValue() {
				return new TreeSet<String>();
			}

		};

	private ThreadLocal<Set<String>> _droppedTables =
		new ThreadLocal<Set<String>>() {

			@Override
			protected Set<String> initialValue() {
				return new TreeSet<String>();
			}

		};

	private ThreadLocal<Set<String>> _insertedTablesLowerCase =
		new ThreadLocal<Set<String>>() {

			@Override
			protected Set<String> initialValue() {
				return new TreeSet<String>();
			}

		};

	private ThreadLocal<Set<String>> _modifiedTables =
		new ThreadLocal<Set<String>>() {

			@Override
			protected Set<String> initialValue() {
				return new TreeSet<String>();
			}

		};

	private ThreadLocal<Boolean> _regenerateModelUtil =
		new ThreadLocal<Boolean>() {

			@Override
			protected Boolean initialValue() {
				return Boolean.FALSE;
			}

		};

	private ThreadLocal<Boolean> _regenerateReferencesChecker =
		new ThreadLocal<Boolean>() {

			@Override
			protected Boolean initialValue() {
				return Boolean.FALSE;
			}

		};

	private ThreadLocal<Map<String, Set<String>>> _updatedTablesColumns =
		new ThreadLocal<Map<String, Set<String>>>() {

			@Override
			protected Map<String, Set<String>> initialValue() {
				return new HashMap<String, Set<String>>();
			}

		};

	private ThreadLocal<Set<String>> _updatedTablesLowerCase =
		new ThreadLocal<Set<String>>() {

			@Override
			protected Set<String> initialValue() {
				return new TreeSet<String>();
			}

		};

}