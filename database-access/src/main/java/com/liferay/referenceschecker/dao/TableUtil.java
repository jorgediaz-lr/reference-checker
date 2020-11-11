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

package com.liferay.referenceschecker.dao;

import com.liferay.referenceschecker.model.ModelUtil;
import com.liferay.referenceschecker.util.JDBCUtil;
import com.liferay.referenceschecker.util.SQLUtil;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * @author Jorge Díaz
 */
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

			sql = SQLUtil.transform(SQLUtil.getDBType(connection), sql);

			ps = connection.prepareStatement(sql);

			ps.setQueryTimeout(SQLUtil.QUERY_TIMEOUT);

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

	public static Map<String, Long> countTableClassName(
		Connection connection, Table table, String classNameAttr,
		String classNamePkAttr) {

		PreparedStatement ps = null;
		ResultSet rs = null;

		Map<String, Long> map = new HashMap<>();

		String sql = null;

		try {
			String key = "*";

			if (!table.hasCompoundPrimKey()) {
				key = table.getPrimaryKey();
			}

			sql =
				"SELECT " + classNameAttr + ", COUNT(" + key + ") FROM " +
					table.getTableName();

			if (classNamePkAttr != null) {
				sql += " WHERE " + classNamePkAttr + "<>0 ";
			}

			sql += " GROUP BY " + classNameAttr;

			if (_log.isDebugEnabled()) {
				_log.debug("SQL: " + sql);
			}

			sql = SQLUtil.transform(SQLUtil.getDBType(connection), sql);

			ps = connection.prepareStatement(sql);

			ps.setQueryTimeout(SQLUtil.QUERY_TIMEOUT);

			rs = ps.executeQuery();

			while (rs.next()) {
				String className = rs.getString(1);
				long count = rs.getLong(2);

				map.put(className, count);
			}
		}
		catch (SQLException sqle) {
			_log.error(
				"Error executing sql: " + sql + " EXCEPTION: " + sqle, sqle);

			return null;
		}
		finally {
			JDBCUtil.cleanUp(ps, rs);
		}

		return map;
	}

	public static Map<Long, Long> countTableClassNameId(
		Connection connection, Table table, String classNameIdAttr,
		String classNamePkAttr) {

		PreparedStatement ps = null;
		ResultSet rs = null;

		Map<Long, Long> map = new HashMap<>();

		String sql = null;

		try {
			String key = "*";

			if (!table.hasCompoundPrimKey()) {
				key = table.getPrimaryKey();
			}

			sql =
				"SELECT " + classNameIdAttr + ", COUNT(" + key + ") FROM " +
					table.getTableName();

			if (classNamePkAttr != null) {
				sql += " WHERE " + classNamePkAttr + " IS NOT NULL ";
			}

			sql += " GROUP BY " + classNameIdAttr;

			if (_log.isDebugEnabled()) {
				_log.debug("SQL: " + sql);
			}

			sql = SQLUtil.transform(SQLUtil.getDBType(connection), sql);

			ps = connection.prepareStatement(sql);

			ps.setQueryTimeout(SQLUtil.QUERY_TIMEOUT);

			rs = ps.executeQuery();

			while (rs.next()) {
				long classNameId = rs.getLong(1);
				long count = rs.getLong(2);

				map.put(classNameId, count);
			}
		}
		catch (SQLException sqle) {
			_log.error(
				"Error executing sql: " + sql + " EXCEPTION: " + sqle, sqle);

			return null;
		}
		finally {
			JDBCUtil.cleanUp(ps, rs);
		}

		return map;
	}

	public void addTable(
		DatabaseMetaData databaseMetaData, String catalog, String schema,
		String tableName) {

		addTables(
			databaseMetaData, catalog, schema,
			Collections.singleton(tableName));
	}

	public void addTables(Connection connection, Collection<String> tableNames)
		throws SQLException {

		if (tableNames.isEmpty()) {
			return;
		}

		String dbType = SQLUtil.getDBType(connection);

		DatabaseMetaData databaseMetaData = connection.getMetaData();

		String catalog = connection.getCatalog();
		String schema = null;

		if ((catalog == null) && dbType.equals(SQLUtil.TYPE_ORACLE)) {
			catalog = databaseMetaData.getUserName();

			schema = catalog;
		}

		addTables(databaseMetaData, catalog, schema, tableNames);
	}

	public synchronized void addTables(
		DatabaseMetaData databaseMetaData, String catalog, String schema,
		Collection<String> tableNames) {

		Map<String, Table> tableMap = initTableMap(
			databaseMetaData, catalog, schema, tableNames);

		Collection<Table> tables = tableMap.values();

		tableNames = _getTableNames(tables);

		this.tableMap.putAll(tableMap);

		this.tableNames.addAll(tableNames);
	}

	public void cleanEmptyTableCacheOnDelete(String tableName) {
		cleanEmptyTableCache(tableName, Boolean.FALSE);
	}

	public void cleanEmptyTableCacheOnInsert(String tableName) {
		cleanEmptyTableCache(tableName, Boolean.TRUE);
	}

	public void cleanEmptyTableCacheOnUpdate(String tableName) {
		cleanEmptyTableCache(tableName, null);
	}

	public Table getTable(String tableName) {
		String key = StringUtils.lowerCase(tableName);

		return tableMap.get(key);
	}

	public Set<String> getTableNames() {
		return Collections.unmodifiableSet(tableNames);
	}

	public List<Table> getTables() {
		return new ArrayList<>(tableMap.values());
	}

	public List<Table> getTables(String regex) {
		if (StringUtils.isBlank(regex) || Objects.equals(regex, ".*")) {
			return getTables();
		}

		Table singleTable = getTable(regex);

		if (singleTable != null) {
			return Collections.singletonList(singleTable);
		}

		Pattern pattern = patternCache.get(regex);

		if (pattern == null) {
			try {
				String lowerCaseFilter = StringUtils.lowerCase(regex);

				pattern = Pattern.compile(lowerCaseFilter);
			}
			catch (PatternSyntaxException pse) {
				_log.warn(pse, pse);

				return Collections.emptyList();
			}

			patternCache.put(regex, pattern);
		}

		List<Table> tableList = new ArrayList<>();

		for (Table table : getTables()) {
			Matcher matcher = pattern.matcher(table.getTableNameLowerCase());

			if (matcher.matches()) {
				tableList.add(table);
			}
		}

		return tableList;
	}

	public boolean ignoreColumn(String tableName, String columnName) {
		if (StringUtils.isBlank(columnName)) {
			return true;
		}

		columnName = StringUtils.lowerCase(columnName);

		for (Pattern[] ignoreColumnArray : ignoreColumns) {
			Pattern ignoreTable = ignoreColumnArray[0];

			if ((ignoreTable != null) &&
				!matchesRegex(tableName, ignoreTable)) {

				continue;
			}

			Pattern ignoreColumn = ignoreColumnArray[1];

			if (matchesRegex(columnName, ignoreColumn)) {
				return true;
			}
		}

		return false;
	}

	public boolean ignoreTable(String tableName) {
		if (StringUtils.isBlank(tableName)) {
			return true;
		}

		tableName = StringUtils.lowerCase(tableName);

		for (Pattern ignoreTable : ignoreTables) {
			Matcher matcher = ignoreTable.matcher(tableName);

			if (matcher.matches()) {
				return true;
			}
		}

		return false;
	}

	public void init(
			Connection connection, List<String> ignoreColumns,
			List<String> ignoreTables, ModelUtil modelUtil)
		throws SQLException {

		initIgnore(ignoreTables, ignoreColumns);

		String dbType = SQLUtil.getDBType(connection);

		DatabaseMetaData databaseMetaData = connection.getMetaData();

		String catalog = connection.getCatalog();
		String schema = null;

		if ((catalog == null) && dbType.equals(SQLUtil.TYPE_ORACLE)) {
			catalog = databaseMetaData.getUserName();

			schema = catalog;
		}

		Set<String> tableNames = getTableNames(
			databaseMetaData, catalog, schema, "%");

		addTables(databaseMetaData, catalog, schema, tableNames);

		this.modelUtil = modelUtil;
	}

	public boolean isTableEmpty(Connection connection, Table table) {
		return isTableEmpty(connection, table, null);
	}

	public boolean isTableEmpty(
		Connection connection, Table table, String whereClause) {

		String key = table.getTableNameLowerCase();

		if (whereClause != null) {
			key = key.concat(
				"_"
			).concat(
				whereClause
			);
		}

		Boolean tableEmpty = emptyTableCache.get(key);

		if (tableEmpty != null) {
			return tableEmpty;
		}

		if (whereClause == null) {
			tableEmpty = countTable(connection, table, null) <= 0;
		}
		else {
			Matcher matcher = classNameIdPattern.matcher(whereClause);

			if (matcher.matches()) {
				return isTableEmptyClassNameId(connection, table, matcher);
			}

			matcher = classNamePattern.matcher(whereClause);

			if (matcher.matches()) {
				return isTableEmptyClassName(connection, table, matcher);
			}

			tableEmpty = isTableEmpty(connection, table);

			if (!tableEmpty) {
				tableEmpty = countTable(connection, table, whereClause) <= 0;
			}
		}

		Set<String> keySet = emptyTableKeys.get(table.getTableNameLowerCase());

		if (keySet == null) {
			keySet = new HashSet<>();

			emptyTableKeys.put(table.getTableNameLowerCase(), keySet);
		}

		keySet.add(key);

		emptyTableCache.put(key, tableEmpty);

		return tableEmpty;
	}

	public boolean isTableEmptyClassName(
		Connection connection, Table table, Matcher matcher) {

		String classNameAttr = matcher.group(1);
		String classNameValue = matcher.group(2);
		String classNamePkAttr = matcher.group(3);

		String key = table.getTableNameLowerCase();

		key = key.concat(
			"_"
		).concat(
			classNameAttr
		);

		if (classNamePkAttr != null) {
			key = key.concat(
				"_"
			).concat(
				classNamePkAttr
			);
		}

		Set<String> keySet = emptyTableKeysClassName.get(
			table.getTableNameLowerCase());

		if (keySet == null) {
			keySet = new HashSet<>();

			emptyTableKeysClassName.put(table.getTableNameLowerCase(), keySet);
		}

		keySet.add(key);

		Map<String, Long> map = emptyTableCacheClassName.get(key);

		if (map == null) {
			map = countTableClassName(
				connection, table, classNameAttr, classNamePkAttr);

			emptyTableCacheClassName.put(key, map);
		}

		Long count = map.get(classNameValue);

		if (count == null) {
			return true;
		}

		if (count == 0) {
			return true;
		}

		return false;
	}

	public boolean isTableEmptyClassNameId(
		Connection connection, Table table, Matcher matcher) {

		String classNameIdAttr = matcher.group(1);
		String classNameValue = matcher.group(2);
		String classNamePkAttr = matcher.group(4);

		String key = table.getTableNameLowerCase();

		key = key.concat(
			"_"
		).concat(
			classNameIdAttr
		);

		if (classNamePkAttr != null) {
			key = key.concat(
				"_"
			).concat(
				classNamePkAttr
			);
		}

		Set<String> keySet = emptyTableKeysClassNameId.get(
			table.getTableNameLowerCase());

		if (keySet == null) {
			keySet = new HashSet<>();

			emptyTableKeysClassNameId.put(
				table.getTableNameLowerCase(), keySet);
		}

		keySet.add(key);

		Map<Long, Long> map = emptyTableCacheClassNameId.get(key);

		if (map == null) {
			map = countTableClassNameId(
				connection, table, classNameIdAttr, classNamePkAttr);

			emptyTableCacheClassNameId.put(key, map);
		}

		Long count = map.get(modelUtil.getClassNameId(classNameValue));

		if (count == null) {
			return true;
		}

		if (count == 0) {
			return true;
		}

		return false;
	}

	public void removeTable(String tableName) {
		Table table = getTable(tableName);

		tableNames.remove(table.getTableName());
		tableMap.remove(table.getTableNameLowerCase());
	}

	public void removeTables(Collection<String> tableNames) {
		for (String tableName : tableNames) {
			removeTable(tableName);
		}
	}

	protected void cleanEmptyTableCache(
		String tableName, Boolean valueToClean) {

		String tableNameLowerCase = StringUtils.lowerCase(tableName);

		Set<String> keySet = emptyTableKeys.get(tableNameLowerCase);

		if (keySet == null) {
			keySet = Collections.emptySet();
		}

		for (String tableKey : keySet) {
			if (valueToClean != null) {
				Boolean empty = emptyTableCache.get(tableKey);

				if ((empty == null) || (empty != valueToClean)) {
					continue;
				}
			}

			emptyTableCache.remove(tableKey);
		}

		keySet = emptyTableKeysClassName.get(tableNameLowerCase);

		if (keySet == null) {
			keySet = Collections.emptySet();
		}

		for (String tableKey : keySet) {
			emptyTableCacheClassName.remove(tableKey);
		}

		keySet = emptyTableKeysClassNameId.get(tableNameLowerCase);

		if (keySet == null) {
			keySet = Collections.emptySet();
		}

		for (String tableKey : keySet) {
			emptyTableCacheClassNameId.remove(tableKey);
		}
	}

	protected Table createTable(
			DatabaseMetaData databaseMetaData, String catalog, String schema,
			String tableName)
		throws SQLException {

		List<String> primaryKeys = new ArrayList<>();

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

		if (primaryKeys.size() > 1) {
			primaryKeys = removeBlacklistedPrimaryKeys(
				primaryKeys, _BLACKLISTED_PRIMARY_KEYS);
		}

		List<String> columnNames = new ArrayList<>();
		List<Integer> columnDataTypes = new ArrayList<>();
		List<String> columnTypeNames = new ArrayList<>();
		List<Integer> columnSizes = new ArrayList<>();
		List<Boolean> columnNullables = new ArrayList<>();

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

		return new Table(
			tableName, primaryKeys, columnNames, columnDataTypes,
			columnTypeNames, columnSizes, columnNullables);
	}

	protected String getSanitizedTableName(
			DatabaseMetaData databaseMetaData, String catalog, String schema,
			String tableName)
		throws SQLException {

		if (_log.isDebugEnabled()) {
			_log.debug("getting sanitized name of " + tableName);
		}

		String[] tableNameChecks = {
			tableName, StringUtils.lowerCase(tableName),
			StringUtils.upperCase(tableName)
		};

		for (String tableNameCheck : tableNameChecks) {
			Set<String> tableNamesAux = getTableNames(
				databaseMetaData, catalog, schema, tableNameCheck);

			if (tableNamesAux.isEmpty() || (tableNamesAux.size() > 1)) {
				continue;
			}

			Iterator<String> iterator = tableNamesAux.iterator();

			return iterator.next();
		}

		_log.warn(tableName + " was not found");

		return null;
	}

	protected Set<String> getTableNames(
			DatabaseMetaData databaseMetaData, String catalog, String schema,
			String tableNamePattern)
		throws SQLException {

		Set<String> tableNames = new TreeSet<>();

		ResultSet rs = null;

		try {
			rs = databaseMetaData.getTables(
				catalog, schema, tableNamePattern, null);

			while (rs.next()) {
				String tableName = rs.getString("TABLE_NAME");

				if (ignoreTable(tableName)) {
					if (_log.isDebugEnabled()) {
						_log.debug("Ignoring table: " + tableName);
					}

					continue;
				}

				String tableType = rs.getString("TABLE_TYPE");

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

	protected void initIgnore(
		List<String> ignoreTables, List<String> ignoreColumns) {

		List<Pattern[]> ignoreColumnsArray = new ArrayList<>();

		for (String ignoreColumn : ignoreColumns) {
			ignoreColumn = StringUtils.lowerCase(ignoreColumn);

			int pos = ignoreColumn.indexOf('#');

			Pattern[] ignoreColumnArray = new Pattern[2];

			if (pos == -1) {
				ignoreColumnArray[0] = null;
				ignoreColumnArray[1] = Pattern.compile(ignoreColumn);
			}
			else {
				String table = ignoreColumn.substring(0, pos);

				ignoreColumnArray[0] = Pattern.compile(table);

				String column = ignoreColumn.substring(pos + 1);

				ignoreColumnArray[1] = Pattern.compile(column);
			}

			ignoreColumnsArray.add(ignoreColumnArray);
		}

		this.ignoreColumns = ignoreColumnsArray;

		List<Pattern> ignoreTablePatterns = new ArrayList<>();

		for (String table : ignoreTables) {
			table = StringUtils.lowerCase(table);

			ignoreTablePatterns.add(Pattern.compile(table));
		}

		this.ignoreTables = ignoreTablePatterns;
	}

	protected Map<String, Table> initTableMap(
		DatabaseMetaData databaseMetaData, String catalog, String schema,
		Collection<String> tableNames) {

		Map<String, Table> tableMap = new TreeMap<>();

		for (String tableName : tableNames) {
			if (ignoreTable(tableName)) {
				if (_log.isDebugEnabled()) {
					_log.debug("Ignoring table: " + tableName);
				}

				continue;
			}

			try {
				tableName = getSanitizedTableName(
					databaseMetaData, catalog, schema, tableName);

				if (tableName == null) {
					continue;
				}

				Table table = createTable(
					databaseMetaData, catalog, schema, tableName);

				if (table != null) {
					tableMap.put(table.getTableNameLowerCase(), table);
				}
			}
			catch (SQLException sqle) {
				_log.error(sqle, sqle);
			}
		}

		return tableMap;
	}

	protected boolean isBlacklistedPrimaryKey(
		String primaryKey, String[] blacklistedPrimaryKeys) {

		for (String blacklistedPrimaryKey : blacklistedPrimaryKeys) {
			if (StringUtils.equalsIgnoreCase(
					blacklistedPrimaryKey, primaryKey)) {

				return true;
			}
		}

		return false;
	}

	protected boolean matchesRegex(String value, Pattern pattern) {
		Matcher matcher = pattern.matcher(value);

		return matcher.matches();
	}

	protected List<String> removeBlacklistedPrimaryKeys(
		List<String> primaryKey, String[] blacklistedPrimaryKeyColumns) {

		List<String> newPrimaryKey = new ArrayList<>();

		for (String primaryKeyColumn : primaryKey) {
			if (isBlacklistedPrimaryKey(
					primaryKeyColumn, blacklistedPrimaryKeyColumns)) {

				if (_log.isDebugEnabled()) {
					_log.debug(
						"Removing " + primaryKeyColumn + " from primaryKey");
				}

				continue;
			}

			newPrimaryKey.add(primaryKeyColumn);
		}

		if (newPrimaryKey.isEmpty()) {
			return primaryKey;
		}

		return newPrimaryKey;
	}

	protected Pattern classNameIdPattern = Pattern.compile(
		"\\s*(\\w*)\\s*in\\s*\\(\\s*select\\s*classNameId\\s*from\\s*" +
			"ClassName_\\s*where\\s*value='(.*)'\\)" +
				"(\\s*and\\s*(\\w*)\\s*IS\\s*NOT\\s*NULL)?",
		Pattern.CASE_INSENSITIVE);
	protected Pattern classNamePattern = Pattern.compile(
		"(\\w*)\\s*=\\s*'(.*)'\\s*and\\s*(\\w*)\\s*<>\\s*0",
		Pattern.CASE_INSENSITIVE);
	protected Map<String, Boolean> emptyTableCache = new ConcurrentHashMap<>();
	protected Map<String, Map<String, Long>> emptyTableCacheClassName =
		new ConcurrentHashMap<>();
	protected Map<String, Map<Long, Long>> emptyTableCacheClassNameId =
		new ConcurrentHashMap<>();
	protected Map<String, Set<String>> emptyTableKeys =
		new ConcurrentHashMap<>();
	protected Map<String, Set<String>> emptyTableKeysClassName =
		new ConcurrentHashMap<>();
	protected Map<String, Set<String>> emptyTableKeysClassNameId =
		new ConcurrentHashMap<>();
	protected List<Pattern[]> ignoreColumns;
	protected List<Pattern> ignoreTables;
	protected ModelUtil modelUtil;
	protected Map<String, Pattern> patternCache = new ConcurrentHashMap<>();
	protected Map<String, Table> tableMap = new TreeMap<>();
	protected Set<String> tableNames = new TreeSet<>();

	private Collection<String> _getTableNames(Collection<Table> tables) {
		List<String> list = new ArrayList<>();

		for (Table table : tables) {
			list.add(table.getTableName());
		}

		return list;
	}

	private static final String[] _BLACKLISTED_PRIMARY_KEYS = {
		"CTCollectionId"
	};

	private static Logger _log = LogManager.getLogger(TableUtil.class);

}