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

import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.referenceschecker.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
public class Query implements Comparable<Query> {

	public Query(Table table, List<String> columns, String condition) {
		this.columns = rewriteConstants(columns);
		this.columnsString = StringUtil.merge(this.columns);
		this.condition = condition;
		this.table = table;
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

	public String getCondition() {
		return condition;
	}

	public String getSQL() {
		return getSQL(true);
	}

	public String getSQL(boolean distinct) {
		return getSQL(distinct, this.columnsString);
	}

	public String getSQL(boolean distinct, String columnsString) {
		StringBundler sb = new StringBundler();

		sb.append("SELECT ");

		if (distinct) {
			sb.append("DISTINCT ");
		}

		sb.append(columnsString);
		sb.append(" FROM ");
		sb.append(table.getTableName());
		sb.append(" WHERE ");

		String sqlCondition = condition;

		if (Validator.isNull(sqlCondition)) {
			sqlCondition = "1=1";
		}

		sb.append(sqlCondition);

		return sb.toString();
	}

	public Table getTable() {
		return table;
	}

	@Override
	public int hashCode() {
		return toString().hashCode();
	}

	@Override
	public String toString() {
		if (toString == null) {
			StringBundler sb = new StringBundler();
			sb.append(table.getTableName());

			if (condition != null) {
				sb.append("[");
				sb.append(condition);
				sb.append("]");
			}

			sb.append("#");
			sb.append(columnsString);
			toString = StringUtil.toLowerCase(sb.toString());
		}

		return toString;
	}

	protected List<String> columns;
	protected String columnsString;
	protected String condition;
	protected Table table;
	protected String toString;

	private static boolean isNumeric(String str)
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

	private List<String> rewriteConstants(List<String> columns) {
		List<String> newColumns = new ArrayList<String>();

		for (String column : columns) {
			boolean isConstant =
				(column.charAt(0) == '\'') &&
				(column.charAt(column.length()-1) == '\'');

			if (isConstant) {
				String value = column.substring(1, column.length()-1);

				if (isNumeric(value)) {
					column = value;
				}
			}

			newColumns.add(column);
		}

		return newColumns;
	}

}