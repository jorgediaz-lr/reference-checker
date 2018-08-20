/**
 * Copyright (c) 2017-present Liferay, Inc. All rights reserved.
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

package com.liferay.referenceschecker.dao;

import com.liferay.referenceschecker.model.ModelUtil;
import com.liferay.referenceschecker.util.JDBCUtil;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
public class TableUtil {

	public static long countTable(Connection connection, Table table) {
		return countTable(connection, table, null);
	}

	public static long countTable(
		Connection connection, Table table, String whereClause) {

		PreparedStatement ps = null;
		ResultSet rs = null;
		long count = 0;

		String sql = null;

		try {
			String key = "*";

			if (!table.hasCompoundPrimKey()) {
				key = table.getPrimaryKey();
			}

			sql = "SELECT COUNT(" + key + ") FROM " + table.getTableName();

			if (whereClause != null) {
				sql = sql + " WHERE " + whereClause;
			}

			if (_log.isDebugEnabled()) {
				_log.debug("SQL: " + sql);
			}

			ps = connection.prepareStatement(sql);

			rs = ps.executeQuery();

			while (rs.next()) {
				count = rs.getLong(1);
			}
		}
		catch (SQLException sqle) {
			_log.error(
				"Error executing sql: " + sql + " EXCEPTION: " + sqle, sqle);

			return -1;
		}
		finally {
			JDBCUtil.cleanUp(ps, rs);
		}

		return count;
	}

	public TableUtil(
			DatabaseMetaData databaseMetaData, String catalog, String schema,
			Map<String, String> tableToClassNameMapping,
			Map<String, Long> classNameToClassNameIdMapping,
			List<String> ignoreTables, List<String> ignoreColumns,
			ModelUtil modelUtil)
		throws SQLException {

		this.tableToClassNameMapping = new HashMap<String, String>(
			tableToClassNameMapping);

		this.classNameToTableMapping = initClassNameToTableMapping(
			tableToClassNameMapping);

		this.classNameToClassNameIdMapping = new HashMap<String, Long>(
			classNameToClassNameIdMapping);

		this.ignoreColumns = initIgnoreColumns(ignoreColumns);

		this.ignoreTables = new ArrayList<String>(ignoreTables);

		this.modelUtil = modelUtil;

		if (this.modelUtil != null) {
			this.modelUtil.initModelMappings(
				classNameToClassNameIdMapping.keySet());
		}

		Set<String> tableNames = getTableNames(
			databaseMetaData, catalog, schema, "%");

		addTables(databaseMetaData, catalog, schema, tableNames);
	}

	public void addTable(
		DatabaseMetaData databaseMetaData, String catalog, String schema,
		String tableName) {

		addTables(
			databaseMetaData, catalog, schema,
			Collections.singleton(tableName));
	}

	public void addTables(
		DatabaseMetaData databaseMetaData, String catalog, String schema,
		Collection<String> tableNames) {

		this.tableMap.putAll(
			initTableMap(databaseMetaData, catalog, schema, tableNames));
		this.tableNames.addAll(tableNames);
	}

	public String getClassNameFromTableName(String tableName) {
		return tableToClassNameMapping.get(StringUtils.lowerCase(tableName));
	}

	public Long getClassNameId(String className) {
		return classNameToClassNameIdMapping.get(className);
	}

	public Set<String> getClassNames() {
		return Collections.unmodifiableSet(
			classNameToClassNameIdMapping.keySet());
	}

	public Table getTable(String tableName) {
		String key = StringUtils.lowerCase(tableName);

		return tableMap.get(key);
	}

	public String getTableNameFromClassName(String className) {
		return classNameToTableMapping.get(className);
	}

	public Set<String> getTableNames() {
		return Collections.unmodifiableSet(tableNames);
	}

	public List<Table> getTables() {
		return new ArrayList<Table>(tableMap.values());
	}

	public List<Table> getTables(String filter) {

		if (StringUtils.isBlank(filter) || "*".equals(filter)) {
			return this.getTables();
		}

		if (filter.endsWith("*")) {
			String tablePrefix = filter.substring(0, filter.indexOf("*"));

			tablePrefix = StringUtils.lowerCase(tablePrefix);

			List<Table> tableList = new ArrayList<Table>();

			for (Table table : getTables()) {
				String tableNameLowerCase = table.getTableNameLowerCase();

				if (tableNameLowerCase.startsWith(tablePrefix)) {
					tableList.add(table);
				}
			}

			return tableList;
		}

		if (filter.startsWith("*")) {
			String tableSuffix = filter.substring(
				filter.indexOf("*") + 1, filter.length());

			tableSuffix = StringUtils.lowerCase(tableSuffix);

			List<Table> tableList = new ArrayList<Table>();

			for (Table table : getTables()) {
				String tableNameLowerCase = table.getTableNameLowerCase();

				if (tableNameLowerCase.endsWith(tableSuffix)) {
					tableList.add(table);
				}
			}

			return tableList;
		}

		Table table = this.getTable(filter);

		if (table == null) {
			return Collections.emptyList();
		}

		return Collections.singletonList(table);
	}

	public boolean ignoreColumn(String tableName, String columnName) {
		if (StringUtils.isBlank(columnName)) {
			return true;
		}

		columnName = StringUtils.lowerCase(columnName);

		for (String[] ignoreColumnArray : ignoreColumns) {
			String ignoreTable = ignoreColumnArray[0];
			String ignoreTableType = ignoreColumnArray[1];
			String ignoreColumn = ignoreColumnArray[2];
			String ignoreColumnType = ignoreColumnArray[3];

			if ((ignoreTable != null) &&
				!matchesValue(tableName, ignoreTable, ignoreTableType)) {

				continue;
			}

			if (matchesValue(columnName, ignoreColumn, ignoreColumnType)) {
				return true;
			}
		}

		return false;
	}

	public boolean ignoreTable(String tableName) {
		if (StringUtils.isBlank(tableName)) {
			return true;
		}

		for (String ignoreTable : ignoreTables) {
			if (StringUtils.equalsIgnoreCase(ignoreTable, tableName)) {
				return true;
			}
		}

		return false;
	}

	public boolean isTableEmpty(Connection connection, Table table) {
		return isTableEmpty(connection, table, null);
	}

	public boolean isTableEmpty(
			Connection connection, Table table, String whereClause) {

		String key = table.getTableNameLowerCase();

		if (whereClause != null) {
			key = key.concat("_").concat(whereClause);
		}

		Boolean isTableEmpty = emptyTableCache.get(key);

		if (isTableEmpty != null) {
			return isTableEmpty;
		}

		if (whereClause == null) {
			isTableEmpty = (TableUtil.countTable(connection, table, null) <= 0);
		}
		else {
			isTableEmpty = isTableEmpty(connection, table);

			if (!isTableEmpty) {
				isTableEmpty =
					(TableUtil.countTable(connection, table, whereClause) <= 0);
			}
		}

		emptyTableCache.put(key, isTableEmpty);

		return isTableEmpty;
	}

	public void removeTable(String tableName) {
		Table table = getTable(tableName);

		this.tableNames.remove(table.getTableName());
		this.tableMap.remove(table.getTableNameLowerCase());
	}

	public void removeTables(Collection<String> tableNames) {
		for (String tableName : tableNames) {
			removeTable(tableName);
		}
	}

	protected Table createTable(
			DatabaseMetaData databaseMetaData, String catalog, String schema,
			String tableName)
		throws SQLException {

		List<String> primaryKeys = new ArrayList<String>();

		ResultSet rsPK = null;

		try {
			if (_log.isDebugEnabled()) {
				_log.debug("getting primaryKeys of " + tableName);
			}

			rsPK = databaseMetaData.getPrimaryKeys(catalog, schema, tableName);

			while (rsPK.next()) {
				String columnName = rsPK.getString("COLUMN_NAME");

				primaryKeys.add(columnName);

				if (_log.isDebugEnabled()) {
					short keySeq = rsPK.getShort("KEY_SEQ");
					String primaryKeyName = rsPK.getString("PK_NAME");

					_log.debug(
						columnName + " " + keySeq + " " + primaryKeyName);
				}
			}
		}
		finally {
			JDBCUtil.cleanUp(rsPK);
		}

		List<String> columnNames = new ArrayList<String>();
		List<Integer> columnDataTypes = new ArrayList<Integer>();
		List<String> columnTypeNames = new ArrayList<String>();
		List<Integer> columnSizes = new ArrayList<Integer>();
		List<Boolean> columnNullables = new ArrayList<Boolean>();

		ResultSet rsCols = null;

		try {
			if (_log.isDebugEnabled()) {
				_log.debug("getting columns of " + tableName);
			}

			rsCols = databaseMetaData.getColumns(
				catalog, schema, tableName, null);

			while (rsCols.next()) {
				String columnName = rsCols.getString("COLUMN_NAME");

				if (ignoreColumn(tableName, columnName)) {
					if (_log.isDebugEnabled()) {
						_log.debug("Ignoring column: " + columnName);
					}

					continue;
				}

				int dataType = rsCols.getInt("DATA_TYPE");
				String typeName = rsCols.getString("TYPE_NAME");
				int columnSize = rsCols.getInt("COLUMN_SIZE");
				boolean nullable = rsCols.getBoolean("NULLABLE");

				columnNames.add(columnName);
				columnDataTypes.add(dataType);
				columnTypeNames.add(typeName);
				columnSizes.add(columnSize);
				columnNullables.add(nullable);

				if (_log.isDebugEnabled()) {
					_log.debug(
						columnName + " " + dataType + " " + typeName + " " +
						columnSize + " " + nullable);
				}
			}
		}
		finally {
			JDBCUtil.cleanUp(rsCols);
		}

		if (columnNames.isEmpty()) {
			return null;
		}

		String className = this.getClassNameFromTableName(tableName);

		if (className == null) {
			if (modelUtil != null) {
				className = modelUtil.getClassName(tableName);
			}

			if (className == null) {
				_log.warn(tableName + " has no className");
			}
			else {
				_log.warn(
					"Mapping " + tableName + " => " + className +
					" was retrieved from model");
			}
		}

		return new Table(
			tableName, primaryKeys, columnNames, columnDataTypes,
			columnTypeNames, columnSizes, columnNullables, className,
			getClassNameId(className));
	}

	protected Set<String> getTableNames(
			DatabaseMetaData databaseMetaData, String catalog, String schema,
			String tableNamePattern)
		throws SQLException {

		Set<String> tableNames = new TreeSet<String>();

		ResultSet rs = null;

		try {
			rs = databaseMetaData.getTables(
				catalog, schema, tableNamePattern, null);

			while (rs.next()) {
				String tableName = rs.getString("TABLE_NAME");
				String tableType = rs.getString("TABLE_TYPE");

				if (ignoreTable(tableName)) {
					if (_log.isDebugEnabled()) {
						_log.debug("Ignoring table: " + tableName);
					}

					continue;
				}

				if ((tableType == null) || !tableType.equals("TABLE")) {
					continue;
				}

				tableNames.add(tableName);
			}
		}
		finally {
			JDBCUtil.cleanUp(rs);
		}

		return tableNames;
	}

	protected Map<String, String> initClassNameToTableMapping(
		Map<String, String> tableToClassNameMapping) {

		Map<String, String> classNameToTableMapping =
			new HashMap<String, String>();

		for (
			Map.Entry<String, String> entry :
				tableToClassNameMapping.entrySet()) {

			if (StringUtils.isBlank(entry.getValue())) {
				continue;
			}

			classNameToTableMapping.put(
				entry.getValue(), StringUtils.lowerCase(entry.getKey()));
		}

		return classNameToTableMapping;
	}

	protected List<String[]> initIgnoreColumns(List<String> ignoreColumns) {
		List<String[]> ignoreColumnsArray = new ArrayList<String[]>();

		for (String ignoreColumn : ignoreColumns) {
			if (ignoreColumn == null) {
				continue;
			}

			ignoreColumn = StringUtils.lowerCase(ignoreColumn);

			int pos = ignoreColumn.indexOf('.');
			String[] ignoreColumnArray = new String[4];

			if (pos == -1) {
				ignoreColumnArray[0] = null;
				ignoreColumnArray[2] = ignoreColumn.trim();
			}
			else {
				ignoreColumnArray[0] = ignoreColumn.substring(0, pos).trim();
				ignoreColumnArray[2] = ignoreColumn.substring(pos+1).trim();
			}

			manageStar(ignoreColumnArray, 0);
			manageStar(ignoreColumnArray, 2);

			ignoreColumnsArray.add(ignoreColumnArray);
		}

		return ignoreColumnsArray;
	}

	protected Map<String, Table> initTableMap(
		DatabaseMetaData databaseMetaData, String catalog, String schema,
		Collection<String> tableNames) {

		Map<String, Table> tableMap = new TreeMap<String, Table>();

		for (String tableName : tableNames) {
			try {
				Table table = createTable(
					databaseMetaData, catalog, schema, tableName);

				if (table != null) {
					tableMap.put(table.getTableNameLowerCase(), table);
				}
			}
			catch (SQLException e) {
				_log.error(e, e);
			}
		}

		return tableMap;
	}

	protected void manageStar(String[] ignoreColumnArray, int i) {
		String column = ignoreColumnArray[i];

		if (column == null) {
			return;
		}

		int pos = column.indexOf("*");

		if (pos == -1) {
			return;
		}

		if ("*".equals(column)) {
			ignoreColumnArray[i] = "*";
			ignoreColumnArray[i + 1] = null;

			return;
		}

		if (column.endsWith("*")) {
			ignoreColumnArray[i] = column.substring(0, pos);
			ignoreColumnArray[i+1] = "prefix";
		}
		else if (column.startsWith("*")) {
			ignoreColumnArray[i] = column.substring(pos + 1, column.length());
			ignoreColumnArray[i+1] = "suffix";
		}
	}

	protected boolean matchesValue(
		String value, String match, String matchType) {

		if ("*".equals(match)) {
			return true;
		}

		if ("prefix".equals(matchType)) {
			return value.startsWith(match);
		}

		if ("suffix".equals(matchType)) {
			return value.endsWith(match);
		}

		if (matchType == null) {
			return StringUtils.equals(match, value);
		}

		return false;
	}

	protected Map<String, Boolean> emptyTableCache =
		new ConcurrentHashMap<String, Boolean>();
	protected ModelUtil modelUtil;
	protected Map<String, Table> tableMap = new TreeMap<String, Table>();
	protected Set<String> tableNames = new TreeSet<String>();;

	private static Logger _log = LogManager.getLogger(TableUtil.class);

	private Map<String, Long> classNameToClassNameIdMapping;
	private Map<String, String> classNameToTableMapping;
	private List<String[]> ignoreColumns;
	private List<String> ignoreTables;
	private Map<String, String> tableToClassNameMapping;

}