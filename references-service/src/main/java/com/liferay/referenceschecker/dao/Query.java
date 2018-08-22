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

import com.liferay.referenceschecker.util.SQLUtil;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Jorge Díaz
 */
public class Query implements Comparable<Query> {

	public static List<String> castColumnsToText(
		String dbType, String prefix, List<String> columns,
		List<Class<?>> columnTypes, List<Class<?>> castTypes) {

		List<String> castedColumns = new ArrayList<>();

		for (int i = 0; i < columns.size(); i++) {
			String column = columns.get(i);

			Class<?> columnType = columnTypes.get(i);
			Class<?> castType = castTypes.get(i);

			if (StringUtils.isNotBlank(prefix)) {
				column = prefix + "." + column;
			}

			if (!columnType.equals(castType) && String.class.equals(castType) &&
				!Object.class.equals(columnType)) {

				column = SQLUtil.castTextColumn(dbType, column);
			}

			castedColumns.add(column);
		}

		return castedColumns;
	}

	public Query(Table table, List<String> columns, String condition) {
		this.columns = _rewriteConstants(columns);
		columnsString = StringUtils.join(this.columns, ",");
		this.condition = condition;
		this.table = table;

		String tableName = table.getTableName();

		if (tableName.length() > 20) {
			tableName = tableName.substring(0, 20);
		}

		tableAlias = tableName + "_" + RandomStringUtils.randomAlphabetic(4);
	}

	@Override
	public int compareTo(Query query) {
		return toString().compareTo(query.toString());
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Query)) {
			return false;
		}

		Query query = (Query)obj;

		return toString().equals(query.toString());
	}

	public List<String> getColumns() {
		return columns;
	}

	public List<String> getColumnsWithCast(
		String dbType, Query destinationQuery) {

		List<String> destinationColumns = destinationQuery.getColumns();

		Table destinationTable = destinationQuery.getTable();

		List<Class<?>> columnTypes = table.getColumnTypesClass(columns);
		List<Class<?>> destinationTypes = destinationTable.getColumnTypesClass(
			destinationColumns);

		return castColumnsToText(
			dbType, tableAlias, columns, columnTypes, destinationTypes);
	}

	public String getCondition() {
		return condition;
	}

	public String getSQL() {
		return getSQL(true);
	}

	public String getSQL(boolean distinct) {
		return getSQL(distinct, columnsString);
	}

	public String getSQL(boolean distinct, String columnsString) {
		StringBuilder sb = new StringBuilder();

		sb.append("SELECT ");

		if (distinct) {
			sb.append("DISTINCT ");
		}

		sb.append(columnsString);
		sb.append(" FROM ");
		sb.append(table.getTableName());
		sb.append(" ");
		sb.append(tableAlias);
		sb.append(" WHERE ");

		String sqlCondition = condition;

		if (StringUtils.isBlank(sqlCondition)) {
			sqlCondition = "1=1";
		}

		sb.append(sqlCondition);

		return sb.toString();
	}

	public String getSQLCount() {
		return getSQL(false, " COUNT(DISTINCT " + columnsString + ")");
	}

	public Table getTable() {
		return table;
	}

	public String getTableAlias() {
		return tableAlias;
	}

	@Override
	public int hashCode() {
		return toString().hashCode();
	}

	@Override
	public String toString() {
		if (toString == null) {
			StringBuilder sb = new StringBuilder();

			sb.append(table.getTableName());

			if (condition != null) {
				sb.append("[");
				sb.append(condition);
				sb.append("]");
			}

			sb.append("#");
			sb.append(columnsString);

			toString = StringUtils.lowerCase(sb.toString());
		}

		return toString;
	}

	protected List<String> columns;
	protected String columnsString;
	protected String condition;
	protected Table table;
	protected String tableAlias;
	protected String toString;

	private static boolean _isNumeric(String str)
	{

		try
		{
			Double.parseDouble(str);
		}
		catch (NumberFormatException nfe)
		{
			return false;
		}

		return true;
	}

	private List<String> _rewriteConstants(List<String> columns) {
		List<String> newColumns = new ArrayList<>();

		for (String column : columns) {
			boolean constant = false;

			if ((column.charAt(0) == '\'') &&
				(column.charAt(column.length() - 1) == '\'')) {

				constant = true;
			}

			if (constant) {
				String value = column.substring(1, column.length() - 1);

				if (_isNumeric(value)) {
					column = value;
				}
			}

			newColumns.add(column);
		}

		return newColumns;
	}

}