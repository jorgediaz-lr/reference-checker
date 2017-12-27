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

import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.referenceschecker.util.SQLUtil;
import com.liferay.referenceschecker.util.StringUtil;

import java.sql.Types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
public class Table implements Comparable<Table> {

	public static String getColumnsWithTypes(
		Table table, List<String> columnList) {

		String allColumns = StringPool.BLANK;

		for (String column : columnList) {
			Class<?> type = table.getColumnTypeClass(column);

			if (Validator.isNotNull(allColumns)) {
				allColumns += StringPool.COMMA;
			}

			allColumns =
				allColumns + column + " (" + type.getSimpleName() + ")";
		}

		return allColumns;
	}

	public Table(
		String tableName, List<String> primaryKeys, List<String> columnNames,
		List<Integer> columnTypes, List<String> columnTypesSqlName,
		List<Integer> columnSizes, List<Boolean> columnNullables,
		String className, Long classNameId) {

		this.tableName = tableName;
		this.tableNameLowerCase = StringUtil.toLowerCase(tableName);

		if (primaryKeys.size() == 1) {
			this.primaryKey = primaryKeys.get(0);
			this.compoundPrimaryKey = null;
		}
		else {
			this.primaryKey = null;
			this.compoundPrimaryKey = primaryKeys.toArray(new String[0]);
		}

		this.columnNames = columnNames.toArray(new String[0]);
		this.columnTypes = toIntArray(columnTypes);
		this.columnTypesClass = new Class[columnTypes.size()];

		for (int i = 0; i<columnTypes.size(); i++) {
			int type = columnTypes.get(i);
			this.columnTypesClass[i] = SQLUtil.getJdbcTypeClass(type);
		}

		this.columnTypesSqlName = columnTypesSqlName.toArray(new String[0]);
		this.columnSizes = toIntArray(columnSizes);
		this.columnNullables = new boolean[columnNullables.size()];

		for (int i = 0; i<columnNullables.size(); i++) {
			this.columnNullables[i] = columnNullables.get(i);
		}

		if (Validator.isNull(className) || Validator.isNull(classNameId)) {
			this.className = null;
			this.classNameId = -1;
		}
		else {
			this.className = className;
			this.classNameId = classNameId;
		}
	}

	@Override
	public int compareTo(Table table) {
		return this.getTableName().compareTo(table.getTableName());
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Table)) {
			return false;
		}

		Table table = (Table)obj;
		return this.getTableName().equals(table.getTableName());
	}

	public String getClassName() {
		return className;
	}

	public long getClassNameId() {
		return classNameId;
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
		return ArrayUtil.clone(columnNames);
	}

	public int getColumnPosition(String columnName) {
		if (mapColumnPosition.containsKey(columnName)) {
			return mapColumnPosition.get(columnName);
		}

		int pos = -1;

		for (int i = 0; i < columnNames.length; i++) {
			if (StringUtil.equalsIgnoreCase(columnNames[i], columnName)) {
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

		List<Class<?>> types = new ArrayList<Class<?>>();

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
		return ArrayUtil.clone(compoundPrimaryKey);
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
		return (getColumnPosition(columnName) != -1);
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
		return (compoundPrimaryKey != null);
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

	protected String className;
	protected long classNameId;
	protected String[] columnNames;
	protected boolean[] columnNullables;
	protected int[] columnSizes;
	protected int[] columnTypes;
	protected Class<?>[] columnTypesClass;
	protected String[] columnTypesSqlName;
	protected String[] compoundPrimaryKey;
	protected Map<String, Integer> mapColumnPosition =
		new ConcurrentHashMap<String, Integer>();
	protected String primaryKey;
	protected String tableName;
	protected String tableNameLowerCase;

	protected String toString;

	/* Backward compatibility with 6.1.20: ArrayUtil.toIntArray doesn't
	 * exists */

	private int[] toIntArray(List<Integer> list) {
		int[] newArray = new int[list.size()];

		for (int i = 0; i < list.size(); i++) {
			Integer value = list.get(i);

			newArray[i] = value.intValue();
		}

		return newArray;
	}

}