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

import com.liferay.referencechecker.OutputUtil;
import com.liferay.referencechecker.ReferenceChecker;
import com.liferay.referencechecker.config.Configuration;
import com.liferay.referencechecker.dao.Table;
import com.liferay.referencechecker.ref.MissingReferences;
import com.liferay.referencechecker.ref.Reference;
import com.liferay.referenceschecker.querieslistener.EventListener;
import com.liferay.referenceschecker.querieslistener.EventListenerRegistry;
import com.liferay.referenceschecker.querieslistener.Query;

import java.lang.reflect.Method;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * @author Jorge Díaz
 */
public class ReferencesCheckerInfrastructureListener implements EventListener {

	@Override
	public void afterCommit(
		int connectionId, Connection connection, SQLException sqlException) {

		if (sqlException != null) {
			return;
		}

		try {
			if (_regenerateReferencesChecker.get(connectionId)) {
				forceInitReferencesChecker(connection, false);
			}

			if (referenceChecker == null) {
				initReferencesChecker(connection);
			}
		}
		catch (Exception exception) {
			_log.error(
				"Error creating ReferenceChecker instance: " +
					exception.getMessage(),
				exception);

			return;
		}

		ReferenceChecker referenceChecker = this.referenceChecker;

		if (referenceChecker == null) {
			return;
		}

		refreshReferencesChecker(
			connection, referenceChecker, _modifiedTables.get(connectionId),
			_droppedTables.get(connectionId),
			_regenerateModelUtil.get(connectionId));

		Set<String> insertedTablesLowerCase = _insertedTablesLowerCase.get(
			connectionId);
		Set<String> updatedTablesLowerCase = _updatedTablesLowerCase.get(
			connectionId);
		Set<String> deletedTablesLowerCase = _deletedTablesLowerCase.get(
			connectionId);

		if (insertedTablesLowerCase.isEmpty() &&
			updatedTablesLowerCase.isEmpty() &&
			deletedTablesLowerCase.isEmpty()) {

			return;
		}

		for (String insertedTable : insertedTablesLowerCase) {
			referenceChecker.cleanEmptyTableCacheOnInsert(insertedTable);
		}

		for (String updatedTable : updatedTablesLowerCase) {
			referenceChecker.cleanEmptyTableCacheOnUpdate(updatedTable);
		}

		for (String deletedTable : deletedTablesLowerCase) {
			referenceChecker.cleanEmptyTableCacheOnDelete(deletedTable);
		}

		checkMissingReferences(
			connection, referenceChecker, insertedTablesLowerCase,
			updatedTablesLowerCase, _updatedTablesColumns.get(connectionId),
			deletedTablesLowerCase);
	}

	@Override
	public void afterConnectionClose(
		int connectionId, Connection connection, SQLException sqlException) {
	}

	@Override
	public void afterGetConnection(
		int connectionId, Connection connection, SQLException sqlException) {

		if ((referenceChecker != null) || (sqlException != null)) {
			return;
		}

		try {
			initReferencesChecker(connection);
		}
		catch (Exception exception) {
			_log.error(
				"Error creating ReferenceChecker instance: " +
					exception.getMessage(),
				exception);
		}
	}

	@Override
	public void afterQuery(
		int connectionId, Connection connection, Query query,
		SQLException sqlException) {

		if ((referenceChecker == null) || (sqlException != null)) {
			return;
		}

		if (query.isReadOnly()) {
			return;
		}

		if ((query.getQueryType() == Query.QueryType.CREATE_TABLE) ||
			(query.getQueryType() == Query.QueryType.ALTER)) {

			Set<String> updatedTables = _modifiedTables.get(connectionId);

			updatedTables.addAll(
				filterIgnoredTables(
					referenceChecker, query.getModifiedTables()));

			_regenerateModelUtil.put(connectionId, Boolean.TRUE);
		}
		else if (query.getQueryType() == Query.QueryType.DROP_TABLE) {
			Set<String> deletedTables = _droppedTables.get(connectionId);

			deletedTables.addAll(
				filterIgnoredTables(
					referenceChecker, query.getModifiedTables()));

			_regenerateModelUtil.put(connectionId, Boolean.TRUE);
		}
		else if (query.getQueryType() == Query.QueryType.INSERT) {
			Set<String> insertedTables = _insertedTablesLowerCase.get(
				connectionId);

			insertedTables.addAll(
				filterIgnoredTables(
					referenceChecker, query.getModifiedTablesLowerCase()));
		}
		else if (query.getQueryType() == Query.QueryType.DELETE) {
			Set<String> deletedTables = _deletedTablesLowerCase.get(
				connectionId);

			deletedTables.addAll(
				filterIgnoredTables(
					referenceChecker, query.getModifiedTablesLowerCase()));
		}
		else if (query.getQueryType() == Query.QueryType.UPDATE) {
			Set<String> updatedTables = _updatedTablesLowerCase.get(
				connectionId);

			Map<String, Set<String>> updatedTablesColumns =
				_updatedTablesColumns.get(connectionId);

			for (String modifiedTable :
					filterIgnoredTables(
						referenceChecker, query.getModifiedTablesLowerCase())) {

				List<String> modifiedColumns = filterIgnoredColumns(
					referenceChecker, modifiedTable,
					query.getModifiedColumns());

				if (modifiedColumns.isEmpty()) {
					continue;
				}

				updatedTables.add(modifiedTable);

				Set<String> columnSet = updatedTablesColumns.get(modifiedTable);

				if (columnSet == null) {
					columnSet = new TreeSet<>();

					updatedTablesColumns.put(modifiedTable, columnSet);
				}

				columnSet.addAll(modifiedColumns);
			}
		}

		if (!_regenerateReferencesChecker.get(connectionId) &&
			hasUpdatedReleaseBuildNumber(query)) {

			_regenerateReferencesChecker.put(connectionId, Boolean.TRUE);
		}

		if (!_regenerateModelUtil.get(connectionId)) {
			for (String table : query.getModifiedTables()) {
				if (StringUtils.equalsIgnoreCase("ClassName_", table)) {
					_regenerateModelUtil.put(connectionId, Boolean.TRUE);

					break;
				}
			}
		}
	}

	@Override
	public void afterRollback(
		int connectionId, Connection connection, SQLException sqlException) {
	}

	@Override
	public void beforeCommit(int connectionId, Connection connection) {
		if (referenceChecker == null) {
			return;
		}

		for (String insertedTable :
				_insertedTablesLowerCase.get(connectionId)) {

			referenceChecker.cleanEmptyTableCacheOnInsert(insertedTable);
		}

		for (String updatedTable : _updatedTablesLowerCase.get(connectionId)) {
			referenceChecker.cleanEmptyTableCacheOnUpdate(updatedTable);
		}

		for (String deletedTable : _deletedTablesLowerCase.get(connectionId)) {
			referenceChecker.cleanEmptyTableCacheOnDelete(deletedTable);
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
	public void deleteConnectionData(int connectionId) {
		_regenerateReferencesChecker.remove(connectionId);
		_regenerateModelUtil.remove(connectionId);
		_modifiedTables.remove(connectionId);
		_droppedTables.remove(connectionId);
		_insertedTablesLowerCase.remove(connectionId);
		_deletedTablesLowerCase.remove(connectionId);
		_updatedTablesLowerCase.remove(connectionId);
		_updatedTablesColumns.remove(connectionId);
	}

	@Override
	public void resetConnectionData(int connectionId) {
		_regenerateReferencesChecker.put(connectionId, Boolean.FALSE);
		_regenerateModelUtil.put(connectionId, Boolean.FALSE);
		_modifiedTables.put(connectionId, new ConcurrentSkipListSet<String>());
		_droppedTables.put(connectionId, new ConcurrentSkipListSet<String>());
		_insertedTablesLowerCase.put(connectionId, new TreeSet<String>());
		_deletedTablesLowerCase.put(connectionId, new TreeSet<String>());
		_updatedTablesLowerCase.put(connectionId, new TreeSet<String>());
		_updatedTablesColumns.put(
			connectionId, new HashMap<String, Set<String>>());
	}

	protected static List<String> filterIgnoredColumns(
		ReferenceChecker referenceChecker, String table, List<String> columns) {

		if (CollectionUtils.isEmpty(columns)) {
			return Collections.emptyList();
		}

		List<String> filteredColumns = new ArrayList<>(columns.size());

		for (String column : columns) {
			if (referenceChecker.ignoreColumn(table, column)) {
				continue;
			}

			filteredColumns.add(column);
		}

		return filteredColumns;
	}

	protected static List<String> filterIgnoredTables(
		ReferenceChecker referenceChecker, List<String> tables) {

		if (CollectionUtils.isEmpty(tables)) {
			return Collections.emptyList();
		}

		List<String> filteredTables = new ArrayList<>(tables.size());

		for (String table : tables) {
			if (referenceChecker.ignoreTable(table)) {
				continue;
			}

			filteredTables.add(table);
		}

		return filteredTables;
	}

	protected static Configuration.Listener getListenerConfiguration(
		ReferenceChecker referenceChecker) {

		Configuration configuration = referenceChecker.getConfiguration();

		return configuration.getListener();
	}

	protected static ReferenceChecker getReferencesChecker() {
		EventListenerRegistry eventListenerRegistry =
			EventListenerRegistry.getEventListenerRegistry();

		EventListener eventListener = eventListenerRegistry.getEventListener(
			ReferencesCheckerInfrastructureListener.class.getName());

		ReferencesCheckerInfrastructureListener
			referencesCheckerInfrastructureListener =
				(ReferencesCheckerInfrastructureListener)eventListener;

		return referencesCheckerInfrastructureListener.referenceChecker;
	}

	protected void checkMissingReferences(
		Connection connection, ReferenceChecker referenceChecker,
		Set<String> insertedTablesLowerCase, Set<String> updatedTablesLowerCase,
		Map<String, Set<String>> updatedTablesColumns,
		Set<String> deletedTablesLowerCase) {

		boolean abortCheck = false;
		boolean cleanUp = false;

		Thread currentThread = Thread.currentThread();

		for (StackTraceElement stackTraceElement :
				currentThread.getStackTrace()) {

			if (_cleanUpMethod(
					referenceChecker, stackTraceElement.getClassName(),
					stackTraceElement.getMethodName())) {

				cleanUp = true;
			}

			if (_ignoreMethod(
					referenceChecker, stackTraceElement.getClassName(),
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
			connection, referenceChecker, insertedTablesLowerCase,
			updatedTablesLowerCase, updatedTablesColumns,
			deletedTablesLowerCase, abortCheck, cleanUp, 0);
	}

	protected void checkMissingReferences(
		Connection connection, ReferenceChecker referenceChecker,
		Set<String> insertedTablesLowerCase, Set<String> updatedTablesLowerCase,
		Map<String, Set<String>> updatedTablesColumns,
		Set<String> deletedTablesLowerCase, boolean abortCheck, boolean cleanUp,
		int depth) {

		if (depth > _CHECK_MAX_DEPTH) {
			_log.error("Reached check max depth, aborting...");
		}

		Collection<Reference> references = referenceChecker.getReferences(
			connection, true);

		Collection<Reference> referencesToCheck = new HashSet<>();

		for (Reference reference : references) {
			com.liferay.referencechecker.dao.Query destinationQuery =
				reference.getDestinationQuery();

			if (destinationQuery == null) {
				continue;
			}

			com.liferay.referencechecker.dao.Query originQuery =
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

		referenceChecker.setIgnoreLowerValues(0);

		referenceChecker.setIgnoreGreaterValues(
			ReferenceChecker.getLiferayMaxCounter(connection));

		Collection<MissingReferences> missingReferences =
			referenceChecker.execute(connection, referencesToCheck);

		if (cleanUp) {
			missingReferences = cleanUp(
				connection, referenceChecker, missingReferences, abortCheck,
				depth);
		}

		if (abortCheck || missingReferences.isEmpty()) {
			return;
		}

		List<String> outputList = OutputUtil.generateCSVOutputCheckReferences(
			missingReferences, -1);

		for (String output : outputList) {
			if (cleanUp) {
				_log.info(output);
			}
			else {
				_log.warn(output);
			}
		}

		Configuration.Listener listenerConfiguration = getListenerConfiguration(
			referenceChecker);

		if (listenerConfiguration.getPrintThreadDump()) {
			if (cleanUp) {
				_log.info("stacktrace", new Exception());
			}
			else {
				_log.warn("stacktrace", new Exception());
			}
		}
	}

	protected Collection<MissingReferences> cleanUp(
		Connection connection, ReferenceChecker referenceChecker,
		Collection<MissingReferences> missingReferencesList, boolean abortCheck,
		int depth) {

		if (missingReferencesList.isEmpty()) {
			return Collections.emptyList();
		}

		List<MissingReferences> notProcessed = new ArrayList<>();
		Set<String> insertedTablesLowerCase = new TreeSet<>();
		Set<String> deletedTablesLowerCase = new TreeSet<>();
		Set<String> updatedTablesLowerCase = new TreeSet<>();
		Map<String, Set<String>> updatedTablesColumns = new HashMap<>();

		for (MissingReferences missingReferences : missingReferencesList) {
			Reference reference = missingReferences.getReference();

			String fixAction = reference.getFixAction();

			com.liferay.referencechecker.dao.Query query =
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
				referenceChecker.executeCleanUp(connection, missingReferences);
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
			connection, referenceChecker, insertedTablesLowerCase,
			updatedTablesLowerCase, updatedTablesColumns,
			deletedTablesLowerCase, abortCheck, true, depth + 1);

		_cleanUpCache(referenceChecker);

		return notProcessed;
	}

	protected synchronized void forceInitReferencesChecker(
			Connection connection, boolean cleanUp)
		throws SQLException {

		ReferenceChecker referencesCheckerAux = new ReferenceChecker(
			connection);

		if (referencesCheckerAux.getConfiguration() == null) {
			return;
		}

		referencesCheckerAux.initModelUtil(connection);
		referencesCheckerAux.initTableUtil(connection);

		referenceChecker = referencesCheckerAux;

		if (!cleanUp) {
			return;
		}

		Collection<Reference> references = referenceChecker.getReferences(
			connection, true);

		Set<String> allTablesLowerCase = getAllTablesLowerCase(references);

		checkMissingReferences(
			connection, referenceChecker, allTablesLowerCase,
			new HashSet<String>(), null, allTablesLowerCase, false, true, 0);
	}

	protected Set<String> getAllTablesLowerCase(
		Collection<Reference> references) {

		Set<String> tablesLowerCase = new HashSet<>();

		for (Reference reference : references) {
			com.liferay.referencechecker.dao.Query destinationQuery =
				reference.getDestinationQuery();

			if (destinationQuery == null) {
				continue;
			}

			com.liferay.referencechecker.dao.Query originQuery =
				reference.getOriginQuery();

			Table originTable = originQuery.getTable();

			Table destinationTable = destinationQuery.getTable();

			tablesLowerCase.add(originTable.getTableNameLowerCase());
			tablesLowerCase.add(destinationTable.getTableNameLowerCase());
		}

		return tablesLowerCase;
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

		if (referenceChecker != null) {
			return;
		}

		forceInitReferencesChecker(connection, true);
	}

	protected void refreshReferencesChecker(
		Connection connection, ReferenceChecker referenceChecker,
		Set<String> updatedTables, Set<String> deletedTables,
		boolean regenerateModelUtil) {

		try {
			referenceChecker.addTables(connection, updatedTables);
		}
		catch (SQLException sqlException) {
			_log.error(
				"Error adding tables to tableUtil object" +
					sqlException.getMessage(),
				sqlException);
		}

		referenceChecker.removeTables(deletedTables);

		if (regenerateModelUtil) {
			try {
				referenceChecker.reloadModelUtil(connection);
			}
			catch (SQLException sqlException) {
				_log.error(
					"Error updating modelUtil object" +
						sqlException.getMessage(),
					sqlException);
			}
		}
	}

	protected ReferenceChecker referenceChecker;

	private void _cleanUpCache(ReferenceChecker referenceChecker) {
		Configuration.Listener listenerConfiguration = getListenerConfiguration(
			referenceChecker);

		String cleanUpCache = listenerConfiguration.getCleanUpCache();

		String className = cleanUpCache.substring(
			0, cleanUpCache.lastIndexOf("."));

		String methodName = cleanUpCache.substring(
			cleanUpCache.lastIndexOf(".") + 1);

		try {
			Class<?> clazz = Class.forName(className);

			Method method = clazz.getMethod(methodName);

			method.invoke(null);
		}
		catch (Throwable throwable) {
			throw new RuntimeException(throwable);
		}
	}

	private boolean _cleanUpMethod(
		ReferenceChecker referenceChecker, String className,
		String methodName) {

		Configuration.Listener listenerConfiguration = getListenerConfiguration(
			referenceChecker);

		for (String cleanUpClass : listenerConfiguration.getCleanUpClasses()) {
			if (className.endsWith(cleanUpClass)) {
				if (_log.isDebugEnabled()) {
					_log.debug(className + " is in the cleanUp classes list");
				}

				return true;
			}
		}

		String fullMethodName = className + "." + methodName;

		for (String cleanUpMethods :
				listenerConfiguration.getCleanUpMethods()) {

			if (fullMethodName.endsWith(cleanUpMethods)) {
				if (_log.isDebugEnabled()) {
					_log.debug(
						fullMethodName + " is in the cleanUp methods list");
				}

				return true;
			}
		}

		return false;
	}

	private boolean _ignoreMethod(
		ReferenceChecker referenceChecker, String className,
		String methodName) {

		Configuration.Listener listenerConfiguration = getListenerConfiguration(
			referenceChecker);

		for (String ignoredClass : listenerConfiguration.getIgnoredClasses()) {
			if (className.endsWith(ignoredClass)) {
				if (_log.isDebugEnabled()) {
					_log.debug(className + " is in the ignored classes list");
				}

				return true;
			}
		}

		String fullMethodName = className + "." + methodName;

		for (String ignoredMethods :
				listenerConfiguration.getIgnoredMethods()) {

			if (fullMethodName.endsWith(ignoredMethods)) {
				if (_log.isDebugEnabled()) {
					_log.debug(
						fullMethodName + " is in the ignored methods list");
				}

				return true;
			}
		}

		return false;
	}

	private boolean _queryHasAnyUpdatedColumn(
		com.liferay.referencechecker.dao.Query query, Set<String> columns) {

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

	private Map<Integer, Set<String>> _deletedTablesLowerCase =
		new ConcurrentHashMap<>();
	private Map<Integer, Set<String>> _droppedTables =
		new ConcurrentHashMap<>();
	private Map<Integer, Set<String>> _insertedTablesLowerCase =
		new ConcurrentHashMap<>();
	private Map<Integer, Set<String>> _modifiedTables =
		new ConcurrentHashMap<>();
	private Map<Integer, Boolean> _regenerateModelUtil =
		new ConcurrentHashMap<>();
	private Map<Integer, Boolean> _regenerateReferencesChecker =
		new ConcurrentHashMap<>();
	private Map<Integer, Map<String, Set<String>>> _updatedTablesColumns =
		new ConcurrentHashMap<>();
	private Map<Integer, Set<String>> _updatedTablesLowerCase =
		new ConcurrentHashMap<>();

}