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

package com.liferay.referencechecker.dao;

import com.liferay.referencechecker.util.SQLUtil;

import java.sql.Types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * @author Jorge Díaz
 */
public class Table implements Comparable<Table> {

	public static String getColumnsWithTypes(
		Table table, List<String> columnList) {

		String allColumns = StringUtils.EMPTY;

		for (String column : columnList) {
			Class<?> type = table.getColumnTypeClass(column);

			if (StringUtils.isNotBlank(allColumns)) {
				allColumns += ",";
			}

			allColumns =
				allColumns + column + " (" + type.getSimpleName() + ")";
		}

		return allColumns;
	}

	public Table(
		String tableName, List<String> primaryKeys, List<String> columnNames,
		List<Integer> columnTypes, List<String> columnTypesSqlName,
		List<Integer> columnSizes, List<Boolean> columnNullables) {

		this.tableName = tableName;
		tableNameLowerCase = StringUtils.lowerCase(tableName);

		if (primaryKeys.size() == 1) {
			primaryKey = primaryKeys.get(0);
			compoundPrimaryKey = null;
		}
		else {
			primaryKey = null;
			compoundPrimaryKey = primaryKeys.toArray(new String[0]);
		}

		this.columnNames = columnNames.toArray(new String[0]);
		this.columnTypes = _toIntArray(columnTypes);

		columnTypesClass = new Class<?>[columnTypes.size()];

		for (int i = 0; i < columnTypes.size(); i++) {
			int type = columnTypes.get(i);

			columnTypesClass[i] = SQLUtil.getJdbcTypeClass(type);
		}

		this.columnTypesSqlName = columnTypesSqlName.toArray(new String[0]);
		this.columnSizes = _toIntArray(columnSizes);
		this.columnNullables = new boolean[columnNullables.size()];

		for (int i = 0; i < columnNullables.size(); i++) {
			this.columnNullables[i] = columnNullables.get(i);
		}
	}

	@Override
	public int compareTo(Table table) {
		return getTableNameLowerCase().compareTo(table.getTableNameLowerCase());
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Table)) {
			return false;
		}

		Table table = (Table)obj;

		return getTableNameLowerCase().equals(table.getTableNameLowerCase());
	}

	public boolean getColumnIsNullable(int i) {
		return columnNullables[i];
	}

	public boolean getColumnIsNullable(String columnName) {
		int i = getColumnPosition(columnName);

		if (i == -1) {
			return false;
		}

		return columnNullables[i];
	}

	public String getColumnName(int i) {
		return columnNames[i];
	}

	public String[] getColumnNames() {
		return ArrayUtils.clone(columnNames);
	}

	public List<String> getColumnNames(String regex) {
		if (Objects.equals(regex, ".*")) {
			return Arrays.asList(getColumnNames());
		}

		if (hasColumn(regex)) {
			return Collections.singletonList(regex);
		}

		Pattern pattern = patternCache.get(regex);

		if (pattern == null) {
			try {
				String lowerCaseRegex = StringUtils.lowerCase(regex);

				pattern = Pattern.compile(lowerCaseRegex);
			}
			catch (PatternSyntaxException patternSyntaxException) {
				_log.warn(patternSyntaxException, patternSyntaxException);

				return Collections.emptyList();
			}

			patternCache.put(regex, pattern);
		}

		List<String> list = new ArrayList<>();

		for (String columnName : getColumnNames()) {
			String columnNameLowerCase = StringUtils.lowerCase(columnName);

			Matcher matcher = pattern.matcher(columnNameLowerCase);

			if (matcher.matches()) {
				list.add(columnName);
			}
		}

		return list;
	}

	public int getColumnPosition(String columnName) {
		if (mapColumnPosition.containsKey(columnName)) {
			return mapColumnPosition.get(columnName);
		}

		int pos = -1;

		for (int i = 0; i < columnNames.length; i++) {
			if (StringUtils.equalsIgnoreCase(columnNames[i], columnName)) {
				pos = i;
			}
		}

		mapColumnPosition.put(columnName, pos);

		return pos;
	}

	public int getColumnSize(int i) {
		return columnSizes[i];
	}

	public int getColumnSize(String columnName) {
		int i = getColumnPosition(columnName);

		if (i == -1) {
			return -1;
		}

		return columnSizes[i];
	}

	public int getColumnType(int i) {
		return columnTypes[i];
	}

	public int getColumnType(String columnName) {
		int i = getColumnPosition(columnName);

		if (i == -1) {
			return Types.NULL;
		}

		return columnTypes[i];
	}

	public Class<?> getColumnTypeClass(int i) {
		return columnTypesClass[i];
	}

	public Class<?> getColumnTypeClass(String columnName) {
		int i = getColumnPosition(columnName);

		if (i == -1) {
			return Object.class;
		}

		return columnTypesClass[i];
	}

	public List<Class<?>> getColumnTypesClass(Collection<String> columns) {
		List<Class<?>> types = new ArrayList<>();

		for (String column : columns) {
			Class<?> type = getColumnTypeClass(column);

			types.add(type);
		}

		return types;
	}

	public String getColumnTypeSqlName(int i) {
		return columnTypesSqlName[i];
	}

	public String getColumnTypeSqlName(String columnName) {
		int i = getColumnPosition(columnName);

		if (i == -1) {
			return null;
		}

		return columnTypesSqlName[i];
	}

	public String[] getCompoundPrimaryKey() {
		return ArrayUtils.clone(compoundPrimaryKey);
	}

	public int getNumberOfColumns() {
		return columnNames.length;
	}

	public String getPrimaryKey() {
		return primaryKey;
	}

	public String getTableName() {
		return tableName;
	}

	public String getTableNameLowerCase() {
		return tableNameLowerCase;
	}

	public boolean hasColumn(String columnName) {
		if (getColumnPosition(columnName) != -1) {
			return true;
		}

		return false;
	}

	public boolean hasColumns(String[] columnNames) {
		for (String columnName : columnNames) {
			if (!hasColumn(columnName)) {
				return false;
			}
		}

		return true;
	}

	public boolean hasCompoundPrimKey() {
		if (compoundPrimaryKey != null) {
			return true;
		}

		return false;
	}

	@Override
	public int hashCode() {
		return getTableName().hashCode();
	}

	@Override
	public String toString() {
		if (toString == null) {
			toString = tableName + ": " + Arrays.toString(columnNames);
		}

		return toString;
	}

	public static class Raw extends Table {

		public Raw(String tableName) {
			this(
				tableName, new ArrayList<String>(), new ArrayList<String>(),
				new ArrayList<Integer>(), new ArrayList<String>(),
				new ArrayList<Integer>(), new ArrayList<Boolean>());
		}

		public Raw(
			String tableName, List<String> primaryKeys,
			List<String> columnNames, List<Integer> columnTypes,
			List<String> columnTypesSqlName, List<Integer> columnSizes,
			List<Boolean> columnNullables) {

			super(
				tableName, primaryKeys, columnNames, columnTypes,
				columnTypesSqlName, columnSizes, columnNullables);
		}

	}

	protected String[] columnNames;
	protected boolean[] columnNullables;
	protected int[] columnSizes;
	protected int[] columnTypes;
	protected Class<?>[] columnTypesClass;
	protected String[] columnTypesSqlName;
	protected String[] compoundPrimaryKey;
	protected Map<String, Integer> mapColumnPosition =
		new ConcurrentHashMap<>();
	protected Map<String, Pattern> patternCache = new ConcurrentHashMap<>();
	protected String primaryKey;
	protected String tableName;
	protected String tableNameLowerCase;
	protected String toString;

	/* Backward compatibility with 6.1.20: ArrayUtil.toIntArray does not
	 * exists */
	private int[] _toIntArray(List<Integer> list) {
		int[] newArray = new int[list.size()];

		for (int i = 0; i < list.size(); i++) {
			Integer value = list.get(i);

			newArray[i] = value.intValue();
		}

		return newArray;
	}

	private static Logger _log = LogManager.getLogger(Table.class);

}