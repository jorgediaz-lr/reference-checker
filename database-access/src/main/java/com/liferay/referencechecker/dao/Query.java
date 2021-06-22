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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Jorge Díaz
 */
public class Query implements Comparable<Query> {

	public Query(
		Table table, List<String> columns, List<String> casting,
		String condition) {

		this.table = table;
		this.casting = casting;

		castingString = null;

		if ((casting != null) && !casting.isEmpty()) {
			castingString = StringUtils.join(this.casting, ",");
		}

		this.columns = _rewriteConstants(columns);
		columnsString = StringUtils.join(this.columns, ",");
		this.condition = condition;

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

	public List<String> getCasting() {
		return casting;
	}

	public List<String> getColumns() {
		return columns;
	}

	public List<String> getColumnsWithCast(
		String dbType, Query destinationQuery) {

		List<String> castedColumns = casting;

		if ((castedColumns == null) || castedColumns.isEmpty()) {
			List<String> destinationColumns = destinationQuery.getColumns();

			Table destinationTable = destinationQuery.getTable();

			List<Class<?>> columnTypes = table.getColumnTypesClass(columns);
			List<Class<?>> destinationTypes =
				destinationTable.getColumnTypesClass(destinationColumns);

			castedColumns = SQLUtil.castColumnsToText(
				columns, columnTypes, destinationTypes);
		}

		return SQLUtil.addPrefixToSqlList(castedColumns, tableAlias, columns);
	}

	public String getCondition() {
		return condition;
	}

	public String getSQLDelete() {
		StringBuilder sb = new StringBuilder();

		sb.append("DELETE FROM ");
		sb.append(table.getTableName());
		sb.append(" ");
		sb.append(tableAlias);
		sb.append(" WHERE ");

		if (StringUtils.isBlank(condition)) {
			sb.append("1=1");
		}
		else {
			sb.append(condition);
		}

		return sb.toString();
	}

	public String getSQLSelect() {
		return getSQLSelect(true);
	}

	public String getSQLSelect(boolean distinct) {
		return getSQLSelect(distinct, columnsString);
	}

	public String getSQLSelect(boolean distinct, String columnsString) {
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

		if (StringUtils.isBlank(condition)) {
			sb.append("1=1");
		}
		else {
			sb.append(condition);
		}

		return sb.toString();
	}

	public String getSQLSelectCount() {
		return getSQLSelect(false, " COUNT(DISTINCT (" + columnsString + "))");
	}

	public String getSQLUpdate(String setClause) {
		StringBuilder sb = new StringBuilder();

		sb.append("UPDATE ");
		sb.append(table.getTableName());
		sb.append(" ");
		sb.append(tableAlias);
		sb.append(" SET ");
		sb.append(setClause);
		sb.append(" WHERE ");

		if (StringUtils.isBlank(condition)) {
			sb.append("1=1");
		}
		else {
			sb.append(condition);
		}

		return sb.toString();
	}

	public String getSQLUpdateToNull() {
		StringBuilder sb = new StringBuilder();

		boolean first = true;

		for (String column : columns) {
			if (!first) {
				sb.append(",");
			}

			Class<?> clazz = table.getColumnTypeClass(column);

			String nullValue = _getSQLEmptyValue(clazz);

			sb.append(column);
			sb.append("=");
			sb.append(nullValue);
			first = false;
		}

		return getSQLUpdate(sb.toString());
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

			if (castingString != null) {
				sb.append("[");
				sb.append(castingString);
				sb.append("]");
			}

			toString = StringUtils.lowerCase(sb.toString());
		}

		return toString;
	}

	protected List<String> casting;
	protected String castingString;
	protected List<String> columns;
	protected String columnsString;
	protected String condition;
	protected Table table;
	protected String tableAlias;
	protected String toString;

	private String _getSQLEmptyValue(Class<?> clazz) {
		String nullValue;

		if (String.class.isAssignableFrom(clazz)) {
			nullValue = "''";
		}
		else if (Number.class.isAssignableFrom(clazz)) {
			nullValue = "0";
		}
		else {
			nullValue = "NULL";
		}

		return nullValue;
	}

	private boolean _isNumeric(String str) {
		try {
			Double.parseDouble(str);
		}
		catch (NumberFormatException numberFormatException) {
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