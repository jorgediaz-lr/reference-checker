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

package com.liferay.referenceschecker.querieslistener;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.update.Update;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * @author Jorge Díaz
 */
public class Query implements Comparable<Query> {

	public static QueryType getQueryType(Statement statement) {
		if (statement == null) {
			return null;
		}

		if (statement instanceof Alter) {
			return QueryType.ALTER;
		}

		if (statement instanceof CreateIndex) {
			return QueryType.CREATE_INDEX;
		}

		if (statement instanceof CreateTable) {
			return QueryType.CREATE_TABLE;
		}

		if (statement instanceof Delete) {
			return QueryType.DELETE;
		}

		if (statement instanceof Drop) {
			return QueryType.DROP;
		}

		if (statement instanceof Insert) {
			return QueryType.INSERT;
		}

		if (statement instanceof Select) {
			return QueryType.SELECT;
		}

		if (statement instanceof Update) {
			return QueryType.UPDATE;
		}

		return null;
	}

	public Query(String sql) {
		if (StringUtils.isBlank(sql)) {
			_sql = StringUtils.EMPTY;

			return;
		}

		_sql = StringUtils.trim(sql);
	}

	@Override
	public int compareTo(Query query) {
		int equals = ObjectUtils.compare(getQueryType(), query.getQueryType());

		if (equals == 0) {
			equals = ObjectUtils.compare(getSQL(), query.getSQL());
		}

		return equals;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}

		if (obj == null) {
			return false;
		}

		if (!(obj instanceof Query)) {
			return false;
		}

		Query query = (Query)obj;

		if (compareTo(query) == 0) {
			return true;
		}

		return false;
	}

	public List<String> getModifiedTables() {
		List<Table> tableList = getModifiedTablesObject();

		Stream<Table> stream = tableList.stream();

		return stream.map(
			table -> table.getName()
		).collect(
			Collectors.toList()
		);
	}

	public QueryType getQueryType() {
		if (_queryType != null) {
			return _queryType;
		}

		String sqlAux = _sql;

		while (sqlAux.charAt(0) == '(') {
			sqlAux = sqlAux.substring(1);

			sqlAux = sqlAux.trim();
		}

		if (StringUtils.startsWithIgnoreCase(sqlAux, "SELECT")) {
			_queryType = QueryType.SELECT;

			return _queryType;
		}

		Statement statement = getStatement();

		_queryType = getQueryType(statement);

		return _queryType;
	}

	public String getSQL() {
		return _sql;
	}

	public Statement getStatement() {
		if (_cannot_parse_sql) {
			return null;
		}

		if (_statement != null) {
			return _statement;
		}

		try {
			_statement = CCJSqlParserUtil.parse(_sql);
		}
		catch (JSQLParserException jsqlpe) {
			_cannot_parse_sql = true;

			Throwable rootCause = jsqlpe.getCause();

			String rootCauseMsg = String.valueOf(rootCause);

			_log.error(
				"Error parsing query: " + _sql + ", root cause: " +
					rootCauseMsg);

			if (_log.isDebugEnabled()) {
				_log.debug(rootCauseMsg, rootCause);
			}
		}

		return _statement;
	}

	public String getWhere() {
		Expression expression = getWhereExpression();

		return String.valueOf(expression);
	}

	@Override
	public int hashCode() {
		if (_hashCode == -1) {
			_hashCode = Objects.hash(getQueryType(), getSQL());
		}

		return _hashCode;
	}

	public boolean isReadOnly() {
		QueryType queryType = getQueryType();

		if ((queryType == null) || (queryType == QueryType.SELECT)) {
			return true;
		}

		return false;
	}

	@Override
	public String toString() {
		return getSQL();
	}

	public enum QueryType {

		ALTER, CREATE_INDEX, CREATE_TABLE, DELETE, DROP, INSERT, SELECT, UPDATE

	}

	protected List<Table> getModifiedTablesObject() {
		if (isReadOnly()) {
			return Collections.emptyList();
		}

		Statement statement = getStatement();

		if (statement == null) {
			return Collections.emptyList();
		}

		if (statement instanceof Alter) {
			Alter alter = (Alter)statement;

			Table table = alter.getTable();

			return Collections.singletonList(table);
		}

		if (statement instanceof CreateTable) {
			CreateTable createTable = (CreateTable)statement;

			Table table = createTable.getTable();

			return Collections.singletonList(table);
		}

		if (statement instanceof Delete) {
			Delete delete = (Delete)statement;

			Table table = delete.getTable();

			return Collections.singletonList(table);
		}

		if (statement instanceof Drop) {
			Drop drop = (Drop)statement;

			Table table = drop.getName();

			return Collections.singletonList(table);
		}

		if (statement instanceof Insert) {
			Insert insert = (Insert)statement;

			Table table = insert.getTable();

			return Collections.singletonList(table);
		}

		if (statement instanceof Update) {
			Update update = (Update)statement;

			return update.getTables();
		}

		return Collections.emptyList();
	}

	protected Expression getWhereExpression() {
		Statement statement = getStatement();

		if (statement == null) {
			return null;
		}

		if (statement instanceof Delete) {
			Delete delete = (Delete)statement;

			return delete.getWhere();
		}

		if (statement instanceof Update) {
			Update update = (Update)statement;

			return update.getWhere();
		}

		if (statement instanceof Select) {
			Select select = (Select)statement;

			SelectBody selectBody = select.getSelectBody();

			if (selectBody instanceof PlainSelect) {
				PlainSelect plainSelect = (PlainSelect)selectBody;

				return plainSelect.getWhere();
			}
		}

		return null;
	}

	private static Logger _log = LogManager.getLogger(Query.class);

	private boolean _cannot_parse_sql = false;
	private int _hashCode = -1;
	private QueryType _queryType = null;
	private String _sql = null;
	private Statement _statement = null;

}