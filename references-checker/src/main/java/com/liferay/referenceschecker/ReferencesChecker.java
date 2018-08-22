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
import java.util.HashMap;
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

	public static long getLiferayBuildNumber(Connection connection)
		throws SQLException {

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
		finally {
			JDBCUtil.cleanUp(ps);
			JDBCUtil.cleanUp(rs);
		}

		return buildNumber;
	}

	public ReferencesChecker(
			Connection connection, List<String> excludeColumns,
			boolean ignoreNullValues, boolean checkUndefinedTables,
			ModelUtil modelUtil)
		throws IOException, SQLException {

		this.checkUndefinedTables = checkUndefinedTables;
		dbType = SQLUtil.getDBType(connection);
		this.excludeColumns = excludeColumns;
		this.ignoreNullValues = ignoreNullValues;

		try {
			configuration = getConfiguration(connection);

			tableUtil = getTableUtil(connection, configuration, modelUtil);
		}
		catch (IOException ioe) {
			_log.error(
				"Error reading configuration_xx.yml file: " + ioe.getMessage(),
				ioe);

			throw new RuntimeException(ioe);
		}
	}

	public Collection<Reference> calculateReferences(
		Connection connection, boolean ignoreEmptyTables) {

		ReferenceUtil referenceUtil = new ReferenceUtil(tableUtil);

		referenceUtil.setCheckUndefinedTables(checkUndefinedTables);
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

		for (String className : tableUtil.getClassNames()) {
			if (!className.contains(".model.")) {
				continue;
			}

			String tableName = tableUtil.getTableNameFromClassName(className);

			if (tableName == null) {
				classNamesWithoutTable.add(className);
			}
		}

		if (!classNamesWithoutTable.isEmpty()) {
			output.add("ClassName without table information:");

			Collections.sort(classNamesWithoutTable);

			for (String className : classNamesWithoutTable) {
				output.add(
					className + "=" + tableUtil.getClassNameId(className));
			}

			output.add("");
		}

		List<Table> tablesWithoutClassName = new ArrayList<>();
		List<Table> tablesWithClassName = new ArrayList<>();

		for (Table table : tableUtil.getTables()) {
			if (table.getClassNameId() == -1) {
				tablesWithoutClassName.add(table);
			}
			else if (table.getClassNameId() != 0) {
				tablesWithClassName.add(table);
			}
		}

		if (!tablesWithoutClassName.isEmpty()) {
			output.add("Tables without className information:");

			for (Table table : tablesWithoutClassName) {
				output.add(table.getTableName());
			}

			output.add("");
		}

		if (!tablesWithClassName.isEmpty()) {
			output.add("Table-className mapping information:");

			for (Table t : tablesWithClassName) {
				output.add(
					t.getTableName() + "=" + t.getClassName() + "," +
						t.getClassNameId());
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
					missingTable + "=" +
						tableUtil.getClassNameFromTableName(missingTable));
			}

			output.add("");
		}

		List<String> missingClassNames = new ArrayList<>(
			tableToClassNameMapping.values());

		missingClassNames.removeAll(tableUtil.getClassNames());

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

	protected Map<String, Long> getClassNameIdsMapping(Connection connection)
		throws SQLException {

		Map<String, Long> mapping = new HashMap<>();

		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			ps = connection.prepareStatement(
				"select classNameId, value from ClassName_");

			rs = ps.executeQuery();

			while (rs.next()) {
				long classNameId = rs.getLong("classNameId");
				String value = rs.getString("value");

				mapping.put(value, classNameId);
			}
		}
		finally {
			JDBCUtil.cleanUp(ps, rs);
		}

		return mapping;
	}

	protected Configuration getConfiguration(Connection connection)
		throws IOException, SQLException {

		long liferayBuildNumber = getLiferayBuildNumber(connection);

		if (liferayBuildNumber == 0) {
			_log.error("Liferay build number could not be retrieved");

			return null;
		}

		if (_log.isInfoEnabled()) {
			_log.info("Liferay build number: " + liferayBuildNumber);
		}

		String configurationFile = ConfigurationUtil.getConfigurationFileName(
			liferayBuildNumber);

		Configuration configuration = ConfigurationUtil.readConfigurationFile(
			configurationFile);

		if (excludeColumns != null) {
			List<String> configurationIgnoreColumns =
				configuration.getIgnoreColumns();

			configurationIgnoreColumns.addAll(excludeColumns);
		}

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

	protected TableUtil getTableUtil(
			Connection connection, Configuration configuration,
			ModelUtil modelUtil)
		throws SQLException {

		Map<String, String> tableToClassNameMapping =
			configuration.getTableToClassNameMapping();

		Map<String, Long> classNameToClassNameIdMapping =
			getClassNameIdsMapping(connection);

		List<String> ignoreColumns = configuration.getIgnoreColumns();

		List<String> ignoreTables = configuration.getIgnoreTables();

		return TableUtil.getTableUtil(
			connection, tableToClassNameMapping, classNameToClassNameIdMapping,
			ignoreColumns, ignoreTables, modelUtil);
	}

	protected boolean isValidValue(Object[] result) {
		if (!ignoreNullValues) {
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

	protected boolean checkUndefinedTables;
	protected Configuration configuration;
	protected String dbType;
	protected List<String> excludeColumns;
	protected boolean ignoreNullValues;
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