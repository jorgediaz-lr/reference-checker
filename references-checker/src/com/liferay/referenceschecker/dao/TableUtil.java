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

import com.liferay.portal.kernel.dao.jdbc.DataAccess;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.referenceschecker.model.ModelUtil;
import com.liferay.referenceschecker.ref.Reference;
import com.liferay.referenceschecker.util.SQLUtil;
import com.liferay.referenceschecker.util.StringUtil;

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
public class TableUtil {

	public static long countTable(Table table) {
		return countTable(table, null);
	}

	public static long countTable(Table table, String whereClause) {

		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		long count = 0;

		String sql = null;

		try {
			con = DataAccess.getConnection();

			String key = "*";

			if (!table.hasCompoundPrimKey()) {
				key = table.getPrimaryKey();
			}

			sql = "SELECT COUNT(" + key + ") FROM " + table.getTableName();

			if (whereClause != null) {
				sql = sql + " WHERE " + whereClause;
			}

			sql = SQLUtil.transformSQL(sql);

			if (_log.isDebugEnabled()) {
				_log.debug("SQL: " + sql);
			}

			ps = con.prepareStatement(sql);

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
			DataAccess.cleanUp(con, ps, rs);
		}

		return count;
	}

	public TableUtil(
			DatabaseMetaData databaseMetaData, String catalog, String schema,
			Map<String, String> tableToClassNameMapping,
			List<String> ignoreTables, List<String> ignoreColumns)
		throws SQLException {

		this.tableToClassNameMapping = new HashMap<String, String>(
			tableToClassNameMapping);

		this.classNameToTableMapping = initClassNameToTableMapping(
			tableToClassNameMapping);

		this.classNameToClassNameIdMapping = getClassNameIdsMapping();

		this.ignoreColumns = initIgnoreColumns(ignoreColumns);

		this.ignoreTables = new ArrayList<String>(ignoreTables);

		this.tableNames = getTableNames(databaseMetaData, catalog, schema, "%");

		this.modelUtil = new ModelUtil(classNameToClassNameIdMapping.keySet());

		this.tableMap = initTableMap(databaseMetaData, catalog, schema);
	}

	public String getClassNameFromTableName(String tableName) {
		return tableToClassNameMapping.get(StringUtil.toLowerCase(tableName));
	}

	public Long getClassNameId(String className) {
		return classNameToClassNameIdMapping.get(className);
	}

	public Set<String> getClassNames() {
		return Collections.unmodifiableSet(
			classNameToClassNameIdMapping.keySet());
	}

	public Set<String> getNotCheckedColumns(Collection<Reference> references) {
		Set<String> idColumns = new TreeSet<String>();

		for (Table table : getTables()) {
			String tableName = StringUtil.toLowerCase(table.getTableName());
			String primaryKey = table.getPrimaryKey();

			if (primaryKey != null) {
				primaryKey = StringUtil.toLowerCase(primaryKey);
			}

			for (String columnName : table.getColumnNames()) {
				columnName = StringUtil.toLowerCase(columnName);

				if (ignoreColumn(tableName, columnName) ||
					"uuid_".equals(columnName) ||
					columnName.equals(primaryKey)) {

					continue;
				}

				if (columnName.contains("id") || columnName.contains("pk") ||
					columnName.contains("key")) {

					idColumns.add(tableName+"."+columnName);
				}
			}
		}

		for (Reference reference : references) {
			if (reference.getDestinationQuery() == null) {
				continue;
			}

			Query query = reference.getOriginQuery();
			String tableName = query.getTable().getTableName();

			for (String columnName : query.getColumns()) {
				String idColumn = tableName + "." + columnName;
				idColumn = StringUtil.toLowerCase(idColumn);
				idColumns.remove(idColumn);
			}
		}

		return idColumns;
	}

	public Table getTable(String tableName) {
		String key = StringUtil.toLowerCase(tableName);

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

		if (Validator.isNull(filter) || "*".equals(filter)) {
			return this.getTables();
		}

		if (filter.endsWith("*")) {
			String tablePrefix = filter.substring(0, filter.indexOf("*"));

			tablePrefix = StringUtil.toLowerCase(tablePrefix);

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

			tableSuffix = StringUtil.toLowerCase(tableSuffix);

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
		if (Validator.isNull(columnName)) {
			return true;
		}

		columnName = StringUtil.lowerCase(columnName);

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
		if (Validator.isNull(tableName)) {
			return true;
		}

		for (String ignoreTable : ignoreTables) {
			if (StringUtil.equalsIgnoreCase(ignoreTable, tableName)) {
				return true;
			}
		}

		return false;
	}

	public boolean isTableEmpty(Table table) {
		return isTableEmpty(table, null);
	}

	public boolean isTableEmpty(Table table, String whereClause) {

		String key = table.getTableNameLowerCase();

		if (whereClause != null) {
			key = key.concat("_").concat(whereClause);
		}

		Boolean isTableEmpty = emptyTableCache.get(key);

		if (isTableEmpty != null) {
			return isTableEmpty;
		}

		if (whereClause == null) {
			isTableEmpty = (TableUtil.countTable(table, null) <= 0);
		}
		else {
			isTableEmpty = isTableEmpty(table);

			if (!isTableEmpty) {
				isTableEmpty = (TableUtil.countTable(table, whereClause) <= 0);
			}
		}

		emptyTableCache.put(key, isTableEmpty);

		return isTableEmpty;
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
			DataAccess.cleanUp(rsPK);
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
			DataAccess.cleanUp(rsCols);
		}

		if (columnNames.isEmpty()) {
			return null;
		}

		String className = this.getClassNameFromTableName(tableName);

		if (className == null) {
			className = modelUtil.getClassName(tableName);

			if (_log.isWarnEnabled()) {
				if (className == null) {
					_log.warn(tableName + " has no className");
				}
				else {
					_log.warn(
						"Mapping " + tableName + " => " + className +
						" was retrieved from model");
				}
			}
		}

		return new Table(
			tableName, primaryKeys, columnNames, columnDataTypes,
			columnTypeNames, columnSizes, columnNullables, className,
			getClassNameId(className));
	}

	protected Map<String, Long> getClassNameIdsMapping() throws SQLException {

		Map<String, Long> mapping = new HashMap<String, Long>();

		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			con = DataAccess.getConnection();

			ps = con.prepareStatement(
				"select classNameId, value from ClassName_");

			rs = ps.executeQuery();

			while (rs.next()) {
				long classNameId = rs.getLong("classNameId");
				String value = rs.getString("value");

				mapping.put(value, classNameId);
			}
		}
		finally {
			DataAccess.cleanUp(con, ps, rs);
		}

		return mapping;
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
			DataAccess.cleanUp(rs);
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

			if (Validator.isNull(entry.getValue())) {
				continue;
			}

			classNameToTableMapping.put(
				entry.getValue(), StringUtil.toLowerCase(entry.getKey()));
		}

		return classNameToTableMapping;
	}

	protected List<String[]> initIgnoreColumns(List<String> ignoreColumns) {
		List<String[]> ignoreColumnsArray = new ArrayList<String[]>();

		for (String ignoreColumn : ignoreColumns) {
			if (ignoreColumn == null) {
				continue;
			}

			ignoreColumn = StringUtil.lowerCase(ignoreColumn);

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
		DatabaseMetaData databaseMetaData, String catalog, String schema) {

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
			return Validator.equals(match, value);
		}

		return false;
	}

	protected Map<String, Boolean> emptyTableCache =
		new ConcurrentHashMap<String, Boolean>();
	protected ModelUtil modelUtil;
	protected Map<String, Table> tableMap;
	protected Set<String> tableNames;

	private static Log _log = LogFactoryUtil.getLog(TableUtil.class);

	private Map<String, Long> classNameToClassNameIdMapping;
	private Map<String, String> classNameToTableMapping;
	private List<String[]> ignoreColumns;
	private List<String> ignoreTables;
	private Map<String, String> tableToClassNameMapping;

}