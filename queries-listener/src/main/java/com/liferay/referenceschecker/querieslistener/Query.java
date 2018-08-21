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
			return;
		}

		sql = StringUtils.trim(sql);

		try {
			_statement = CCJSqlParserUtil.parse(sql);
		}
		catch (JSQLParserException jsqlpe) {
			_statement = null;
			_sql = sql;

			_log.error("Error parsing query: " + sql, jsqlpe);
		}

		_queryType = getQueryType(_statement);
	}

	@Override
	public int compareTo(Query query) {
		int equals = ObjectUtils.compare(_queryType, query._queryType);

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

		return stream.map(table -> table.getName()).collect(
			Collectors.toList());
	}

	public QueryType getQueryType() {
		return _queryType;
	}

	public String getSQL() {
		if (_sql == null) {
			_sql = String.valueOf(_statement);
		}

		return _sql;
	}

	public Statement getStatement() {
		return _statement;
	}

	public String getWhere() {
		Expression expression = getWhereExpression();

		return String.valueOf(expression);
	}

	@Override
	public int hashCode() {
		if (_hashCode == -1) {
			_hashCode = Objects.hash(_queryType, getSQL());
		}

		return _hashCode;
	}

	@Override
	public String toString() {
		return getSQL();
	}

	public enum QueryType {

		ALTER, CREATE_INDEX, CREATE_TABLE, DELETE, DROP, INSERT, SELECT, UPDATE

	}

	protected List<Table> getModifiedTablesObject() {
		if (_statement == null) {
			return Collections.emptyList();
		}

		if (_statement instanceof Alter) {
			Alter alter = (Alter)_statement;

			Table table = alter.getTable();

			return Collections.singletonList(table);
		}

		if (_statement instanceof CreateTable) {
			CreateTable createTable = (CreateTable)_statement;

			Table table = createTable.getTable();

			return Collections.singletonList(table);
		}

		if (_statement instanceof Delete) {
			Delete delete = (Delete)_statement;

			Table table = delete.getTable();

			return Collections.singletonList(table);
		}

		if (_statement instanceof Drop) {
			Drop drop = (Drop)_statement;

			Table table = drop.getName();

			return Collections.singletonList(table);
		}

		if (_statement instanceof Insert) {
			Insert insert = (Insert)_statement;

			Table table = insert.getTable();

			return Collections.singletonList(table);
		}

		if (_statement instanceof Update) {
			Update update = (Update)_statement;

			return update.getTables();
		}

		return Collections.emptyList();
	}

	protected Expression getWhereExpression() {
		if (_statement == null) {
			return null;
		}

		if (_statement instanceof Delete) {
			Delete delete = (Delete)_statement;

			return delete.getWhere();
		}

		if (_statement instanceof Update) {
			Update update = (Update)_statement;

			return update.getWhere();
		}

		if (_statement instanceof Select) {
			Select select = (Select)_statement;

			SelectBody selectBody = select.getSelectBody();

			if (selectBody instanceof PlainSelect) {
				PlainSelect plainSelect = (PlainSelect)selectBody;

				return plainSelect.getWhere();
			}
		}

		return null;
	}

	private static Logger _log = LogManager.getLogger(Query.class);

	private int _hashCode = -1;
	private QueryType _queryType = null;
	private String _sql = null;
	private Statement _statement = null;

}