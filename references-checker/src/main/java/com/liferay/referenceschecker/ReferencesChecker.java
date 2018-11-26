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

package com.liferay.referenceschecker;

import com.liferay.referenceschecker.config.Configuration;
import com.liferay.referenceschecker.config.ConfigurationUtil;
import com.liferay.referenceschecker.dao.Query;
import com.liferay.referenceschecker.dao.Table;
import com.liferay.referenceschecker.dao.TableUtil;
import com.liferay.referenceschecker.model.ModelUtil;
import com.liferay.referenceschecker.model.ModelUtilImpl;
import com.liferay.referenceschecker.ref.MissingReferences;
import com.liferay.referenceschecker.ref.Reference;
import com.liferay.referenceschecker.ref.ReferenceUtil;
import com.liferay.referenceschecker.util.JDBCUtil;
import com.liferay.referenceschecker.util.SQLUtil;

import java.io.IOException;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * @author Jorge Díaz
 */
public class ReferencesChecker {

	public static long getLiferayBuildNumber(Connection connection) {
		PreparedStatement ps = null;
		ResultSet rs = null;
		long buildNumber = 0;

		try {
			String sql =
				"select buildNumber from Release_ where servletContextName = " +
					"'portal'";

			if (_log.isDebugEnabled()) {
				_log.debug("SQL: " + sql);
			}

			ps = connection.prepareStatement(sql);

			rs = ps.executeQuery();

			while (rs.next()) {
				buildNumber = rs.getLong(1);
			}
		}
		catch (SQLException sqle) {
			_log.warn(sqle);
		}
		finally {
			JDBCUtil.cleanUp(ps);
			JDBCUtil.cleanUp(rs);
		}

		return buildNumber;
	}

	public ReferencesChecker(Connection connection) {
		try {
			dbType = SQLUtil.getDBType(connection);
		}
		catch (SQLException sqle) {
			_log.error(
				"Error getting database type: " + sqle.getMessage(), sqle);

			throw new RuntimeException(sqle);
		}

		try {
			configuration = getConfiguration(connection);
		}
		catch (IOException ioe) {
			_log.error(
				"Error reading configuration_xx.yml file: " + ioe.getMessage(),
				ioe);

			throw new RuntimeException(ioe);
		}
	}

	public void addExcludeColumns(List<String> excludeColumns) {
		List<String> configurationIgnoreColumns =
			configuration.getIgnoreColumns();

		configurationIgnoreColumns.addAll(excludeColumns);
	}

	public void addTables(Connection connection, Collection<String> tableNames)
		throws SQLException {

		if (tableNames.isEmpty()) {
			return;
		}

		tableUtil.addTables(connection, tableNames);

		referencesCache = null;
	}

	public Collection<Reference> calculateReferences(
		Connection connection, boolean ignoreEmptyTables) {

		ReferenceUtil referenceUtil = new ReferenceUtil(tableUtil, modelUtil);

		referenceUtil.setCheckUndefinedTables(isCheckUndefinedTables());
		referenceUtil.setIgnoreEmptyTables(ignoreEmptyTables);

		return referenceUtil.calculateReferences(connection, configuration);
	}

	public Map<String, Long> calculateTableCount(Connection connection)
		throws IOException, SQLException {

		Map<String, Long> mapTableCount = new TreeMap<>();

		for (Table table : tableUtil.getTables()) {
			long count = TableUtil.countTable(connection, table);

			mapTableCount.put(table.getTableName(), count);
		}

		return mapTableCount;
	}

	public void cleanEmptyTableCacheOnDelete(String tableName) {
		tableUtil.cleanEmptyTableCacheOnDelete(tableName);
	}

	public void cleanEmptyTableCacheOnInsert(String tableName) {
		tableUtil.cleanEmptyTableCacheOnInsert(tableName);
	}

	public void cleanEmptyTableCacheOnUpdate(String tableName) {
		tableUtil.cleanEmptyTableCacheOnUpdate(tableName);
	}

	public List<String> dumpDatabaseInfo(Connection connection)
		throws IOException, SQLException {

		List<String> output = new ArrayList<>();

		long liferayBuildNumber = getLiferayBuildNumber(connection);

		DatabaseMetaData databaseMetaData = connection.getMetaData();

		String dbName = databaseMetaData.getDatabaseProductName();
		String driverName = databaseMetaData.getDriverName();
		int dbMajorVersion = databaseMetaData.getDatabaseMajorVersion();
		int dbMinorVersion = databaseMetaData.getDatabaseMinorVersion();

		output.add("Liferay build number: " + liferayBuildNumber);
		output.add("Database name: " + dbName);
		output.add(
			"Database version major: " + dbMajorVersion + ", minor: " +
				dbMinorVersion);
		output.add("Driver name: " + driverName);

		output.add("");

		List<String> classNamesWithoutTable = new ArrayList<>();

		for (String className : modelUtil.getClassNames()) {
			if (!className.contains(".model.")) {
				continue;
			}

			String tableName = modelUtil.getTableName(className);

			if (tableName == null) {
				classNamesWithoutTable.add(className);
			}
		}

		if (!classNamesWithoutTable.isEmpty()) {
			output.add("ClassName without table information:");

			Collections.sort(classNamesWithoutTable);

			for (String className : classNamesWithoutTable) {
				output.add(
					className + "=" + modelUtil.getClassNameId(className));
			}

			output.add("");
		}

		List<String> tablesWithoutClassName = new ArrayList<>();
		List<String> tablesWithClassName = new ArrayList<>();

		for (Table table : tableUtil.getTables()) {
			String tableName = table.getTableName();

			String className = modelUtil.getClassName(tableName);

			Long classNameId = modelUtil.getClassNameId(className);

			if ((className == null) || (classNameId == null)) {
				tablesWithoutClassName.add(tableName);
			}
			else if (!StringUtils.isEmpty(className)) {
				tablesWithClassName.add(tableName);
			}
		}

		if (!tablesWithoutClassName.isEmpty()) {
			output.add("Tables without className information:");

			for (String tableName : tablesWithoutClassName) {
				output.add(tableName);
			}

			output.add("");
		}

		if (!tablesWithClassName.isEmpty()) {
			output.add("Table-className mapping information:");

			for (String tableName : tablesWithClassName) {
				String className = modelUtil.getClassName(tableName);

				Long classNameId = modelUtil.getClassNameId(className);

				output.add(tableName + "=" + className + "," + classNameId);
			}

			output.add("");
		}

		List<String> missingTables = new ArrayList<>();

		Map<String, String> tableToClassNameMapping =
			configuration.getTableToClassNameMapping();

		Set<String> configuredTables = tableToClassNameMapping.keySet();

		for (String configuredTable : configuredTables) {
			Table table = tableUtil.getTable(configuredTable);

			if (table == null) {
				missingTables.add(configuredTable);
			}
		}

		if (!missingTables.isEmpty()) {
			output.add("Configured tables that does not exist:");

			Collections.sort(missingTables);

			for (String missingTable : missingTables) {
				output.add(
					missingTable + "=" + modelUtil.getClassName(missingTable));
			}

			output.add("");
		}

		List<String> missingClassNames = new ArrayList<>(
			tableToClassNameMapping.values());

		missingClassNames.removeAll(modelUtil.getClassNames());

		List<String> missingClassNamesNoBlank = new ArrayList<>();

		for (String missingClassName : missingClassNames) {
			if (StringUtils.isNotBlank(missingClassName)) {
				missingClassNamesNoBlank.add(missingClassName);
			}
		}

		if (!missingClassNamesNoBlank.isEmpty()) {
			output.add("Configured classNames that does not exist:");

			Collections.sort(missingClassNamesNoBlank);

			for (String missingClassName : missingClassNamesNoBlank) {
				output.add(missingClassName);
			}

			output.add("");
		}

		return output;
	}

	public List<MissingReferences> execute(Connection connection) {
		Collection<Reference> references = calculateReferences(
			connection, true);

		return execute(connection, references);
	}

	public List<MissingReferences> execute(
		Connection connection, Collection<Reference> references) {

		List<MissingReferences> listMissingReferences = new ArrayList<>();

		for (Reference reference : references) {
			try {
				if (_log.isInfoEnabled()) {
					_log.info("Processing: " + reference);
				}

				if (reference.isRaw()) {
					continue;
				}

				Query originQuery = reference.getOriginQuery();

				Query destinationQuery = reference.getDestinationQuery();

				if (destinationQuery == null) {
					continue;
				}

				Collection<Object[]> missingReferences = queryInvalidValues(
					connection, originQuery, destinationQuery);

				if ((missingReferences == null) ||
					!missingReferences.isEmpty()) {

					listMissingReferences.add(
						new MissingReferences(reference, missingReferences));
				}
			}
			catch (Throwable t) {
				_log.error(
					"EXCEPTION: " + t.getClass() + " - " + t.getMessage(), t);

				listMissingReferences.add(new MissingReferences(reference, t));
			}
		}

		return listMissingReferences;
	}

	public Configuration getConfiguration() {
		return configuration;
	}

	public Collection<Reference> getReferences(
		Connection connection, boolean ignoreEmptyTables) {

		if (referencesCache == null) {
			referencesCache = calculateReferences(connection, false);
		}

		List<Reference> referencesList = new ArrayList<>();

		for (Reference reference : referencesCache) {
			Query destinationQuery = reference.getDestinationQuery();

			if (destinationQuery == null) {
				continue;
			}

			if (ignoreEmptyTables) {
				if (reference.isRaw()) {
					continue;
				}

				Query originQuery = reference.getOriginQuery();

				Table originTable = originQuery.getTable();

				String whereClause = originQuery.getCondition();

				if (tableUtil.isTableEmpty(
						connection, originTable, whereClause)) {

					continue;
				}
			}

			referencesList.add(reference);
		}

		return referencesList;
	}

	public void initModelUtil(Connection connection) throws SQLException {
		ModelUtil modelUtil = new ModelUtilImpl();

		initModelUtil(connection, modelUtil);
	}

	public void initModelUtil(Connection connection, ModelUtil modelUtil)
		throws SQLException {

		Map<String, String> tableNameToClassNameMapping =
			configuration.getTableToClassNameMapping();

		modelUtil.init(connection, tableNameToClassNameMapping);

		this.modelUtil = modelUtil;
	}

	public synchronized void initTableUtil(Connection connection)
		throws SQLException {

		List<String> ignoreColumns = configuration.getIgnoreColumns();

		List<String> ignoreTables = configuration.getIgnoreTables();

		tableUtil = new TableUtil();

		tableUtil.init(connection, ignoreColumns, ignoreTables);
	}

	public boolean isCheckUndefinedTables() {
		return checkUndefinedTables;
	}

	public boolean isIgnoreNullValues() {
		return ignoreNullValues;
	}

	public Collection<Object[]> queryInvalidValues(
			Connection connection, Query originQuery, Query destinationQuery)
		throws SQLException {

		Set<Object[]> invalidValuesSet = new LinkedHashSet<>();

		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			String sql = getSQL(originQuery, destinationQuery);

			if (_log.isInfoEnabled()) {
				_log.info("SQL: " + sql);
			}

			ps = connection.prepareStatement(sql);

			rs = ps.executeQuery();

			ResultSetMetaData rsmd = rs.getMetaData();

			int columnsNumber = rsmd.getColumnCount();

			while (rs.next()) {
				Object[] result = new Object[columnsNumber];

				for (int i = 0; i < columnsNumber; i++) {
					result[i] = rs.getObject(i + 1);
				}

				if (isValidValue(result)) {
					continue;
				}

				invalidValuesSet.add(result);
			}
		}
		finally {
			JDBCUtil.cleanUp(ps, rs);
		}

		return invalidValuesSet;
	}

	public void reloadModelUtil(Connection connection) throws SQLException {
		initModelUtil(connection, modelUtil);
	}

	public void removeTables(Collection<String> tableNames) {
		if (tableNames.isEmpty()) {
			return;
		}

		tableUtil.removeTables(tableNames);

		referencesCache = null;
	}

	public void setCheckUndefinedTables(boolean checkUndefinedTables) {
		this.checkUndefinedTables = checkUndefinedTables;
	}

	public void setIgnoreNullValues(boolean ignoreNullValues) {
		this.ignoreNullValues = ignoreNullValues;
	}

	protected Configuration getConfiguration(Connection connection)
		throws IOException {

		long liferayBuildNumber = getLiferayBuildNumber(connection);

		if (liferayBuildNumber == 0) {
			_log.warn("Liferay build number could not be retrieved");

			return null;
		}

		if (_log.isInfoEnabled()) {
			_log.info("Liferay build number: " + liferayBuildNumber);
		}

		String configurationFile = ConfigurationUtil.getConfigurationFileName(
			liferayBuildNumber);

		Class<?> clazz = getClass();

		Configuration configuration = ConfigurationUtil.readConfigurationFile(
			clazz.getClassLoader(), configurationFile);

		return configuration;
	}

	protected String getSQL(Query originQuery, Query destinationQuery) {
		return getSQL(originQuery, destinationQuery, false);
	}

	protected String getSQL(
		Query originQuery, Query destinationQuery, boolean count) {

		if (dbType.equals(SQLUtil.TYPE_POSTGRESQL) ||
			dbType.equals(SQLUtil.TYPE_SQLSERVER)) {

			return getSQLNotExists(count, originQuery, destinationQuery);
		}

		return getSQLNotIn(count, originQuery, destinationQuery);
	}

	protected String getSQLNotExists(
		boolean count, Query originQuery, Query destinationQuery) {

		List<String> conditionColumns = originQuery.getColumnsWithCast(
			dbType, destinationQuery);

		List<String> destinationColumns = destinationQuery.getColumnsWithCast(
			dbType, originQuery);

		StringBuilder sb = new StringBuilder();

		if (count) {
			sb.append(originQuery.getSQLCount());
		}
		else {
			sb.append(originQuery.getSQL());
		}

		sb.append(" AND NOT EXISTS (");
		sb.append(
			destinationQuery.getSQL(
				false, StringUtils.join(destinationColumns, ",")));

		for (int i = 0; i < conditionColumns.size(); i++) {
			sb.append(" AND ");
			sb.append(conditionColumns.get(i));
			sb.append("=");
			sb.append(destinationColumns.get(i));
		}

		sb.append(")");

		return sb.toString();
	}

	protected String getSQLNotIn(
		boolean count, Query originQuery, Query destinationQuery) {

		List<String> conditionColumns = originQuery.getColumnsWithCast(
			dbType, destinationQuery);

		List<String> destinationColumns = destinationQuery.getColumnsWithCast(
			dbType, originQuery);

		StringBuilder sb = new StringBuilder();

		if (count) {
			sb.append(originQuery.getSQLCount());
		}
		else {
			sb.append(originQuery.getSQL());
		}

		sb.append(" AND (");
		sb.append(StringUtils.join(conditionColumns, ","));
		sb.append(") NOT IN (");
		sb.append(
			destinationQuery.getSQL(
				false, StringUtils.join(destinationColumns, ",")));
		sb.append(")");

		return sb.toString();
	}

	protected boolean isValidValue(Object[] result) {
		if (!isIgnoreNullValues()) {
			return false;
		}

		for (Object o : result) {
			if (o == null) {
				continue;
			}

			if (o instanceof Number) {
				Number n = (Number)o;

				if (n.longValue() == 0) {
					continue;
				}
			}

			if (!_isNull(o) && !"0".equals(o.toString())) {
				return false;
			}
		}

		return true;
	}

	protected boolean checkUndefinedTables = false;
	protected Configuration configuration;
	protected String dbType;
	protected boolean ignoreNullValues = true;
	protected ModelUtil modelUtil;
	protected Collection<Reference> referencesCache = null;
	protected TableUtil tableUtil;

	private boolean _isNull(Object obj) {
		if (obj == null) {
			return true;
		}

		if (obj instanceof Number) {
			Number n = (Number)obj;

			if (n.longValue() == 0L) {
				return true;
			}

			return false;
		}

		if (obj instanceof String) {
			return StringUtils.isBlank((String)obj);
		}

		return false;
	}

	private static Logger _log = LogManager.getLogger(ReferencesChecker.class);

}