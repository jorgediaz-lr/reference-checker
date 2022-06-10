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

package com.liferay.referencechecker.querieslistener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.TimeValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.ItemsList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.view.CreateView;
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

		if (statement instanceof CreateView) {
			return QueryType.CREATE_OTHER;
		}

		if (statement instanceof Delete) {
			return QueryType.DELETE;
		}

		if (statement instanceof Drop) {
			Drop drop = (Drop)statement;

			String type = drop.getType();

			if (StringUtils.equalsIgnoreCase(type, "INDEX")) {
				return QueryType.DROP_INDEX;
			}

			if (StringUtils.equalsIgnoreCase(type, "TABLE")) {
				return QueryType.DROP_TABLE;
			}

			return QueryType.DROP_OTHER;
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

	public List<String> getModifiedColumns() {
		List<Column> columnsList = getModifiedColumnsObject();

		List<String> list = new ArrayList<>();

		for (Column column : columnsList) {
			list.add(column.getColumnName());
		}

		return list;
	}

	public List<String> getModifiedTables() {
		List<Table> tableList = getModifiedTablesObject();

		List<String> list = new ArrayList<>();

		for (Table table : tableList) {
			list.add(table.getName());
		}

		return list;
	}

	public List<String> getModifiedTablesLowerCase() {
		List<Table> tableList = getModifiedTablesObject();

		List<String> list = new ArrayList<>();

		for (Table table : tableList) {
			String tableName = table.getName();

			list.add(tableName.toLowerCase());
		}

		return list;
	}

	public Map<String, Object> getModifiedValues() {
		List<String> modifiedColumns = getModifiedColumns();
		List<Expression> modifiedValues = getModifiedValuesObject();

		if (modifiedColumns.size() != modifiedValues.size()) {
			return Collections.emptyMap();
		}

		Map<String, Object> valuesMap = new HashMap<>();

		for (int i = 0; i < modifiedColumns.size(); i++) {
			Object value = convertExpressionToObject(modifiedValues.get(i));

			valuesMap.put(modifiedColumns.get(i), value);
		}

		return valuesMap;
	}

	public QueryType getQueryType() {
		if (_queryType == null) {
			_queryType = getQueryTypeStartsWith();
		}

		if (_queryType != null) {
			return _queryType;
		}

		_queryType = getQueryType(getStatement());

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

		String sqlAux = normalizeSql(_sql);

		if (sqlAux == null) {
			_cannot_parse_sql = true;

			return null;
		}

		try {
			_statement = CCJSqlParserUtil.parse(sqlAux);
		}
		catch (JSQLParserException jsqlParserException) {
			_cannot_parse_sql = true;

			Throwable rootCause = jsqlParserException.getCause();

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

	public boolean isChangingData() {
		QueryType queryType = getQueryType();

		if ((queryType == QueryType.INSERT) ||
			(queryType == QueryType.UPDATE) ||
			(queryType == QueryType.DELETE)) {

			return true;
		}

		return false;
	}

	public boolean isChangingTableDefinition() {
		QueryType queryType = getQueryType();

		if ((queryType == QueryType.CREATE_TABLE) ||
			(queryType == QueryType.ALTER) ||
			(queryType == QueryType.DROP_TABLE)) {

			return true;
		}

		return false;
	}

	public boolean isReadOnly() {
		QueryType queryType = getQueryType();

		if ((queryType == null) || (queryType == QueryType.SELECT) ||
			(queryType == QueryType.SHOW)) {

			return true;
		}

		return false;
	}

	@Override
	public String toString() {
		return getSQL();
	}

	public enum QueryType {

		ALTER, CREATE_INDEX, CREATE_OTHER, CREATE_TABLE, DELETE, DROP_INDEX,
		DROP_OTHER, DROP_TABLE, INSERT, SELECT, SHOW, UPDATE

	}

	protected Object convertExpressionToObject(Expression expression) {
		if (expression instanceof DateValue) {
			DateValue value = (DateValue)expression;

			return value.getValue();
		}

		if (expression instanceof TimeValue) {
			TimeValue value = (TimeValue)expression;

			return value.getValue();
		}

		if (expression instanceof DoubleValue) {
			DoubleValue value = (DoubleValue)expression;

			return value.getValue();
		}

		if (expression instanceof LongValue) {
			LongValue value = (LongValue)expression;

			return value.getValue();
		}

		if (expression instanceof Parenthesis) {
			Parenthesis parenthesis = (Parenthesis)expression;

			convertExpressionToObject(parenthesis.getExpression());
		}

		if (expression instanceof NullValue) {
			return null;
		}

		return expression.toString();
	}

	protected List<Column> getModifiedColumnsObject() {
		if (isReadOnly()) {
			return Collections.emptyList();
		}

		Statement statement = getStatement();

		if (statement == null) {
			return Collections.emptyList();
		}

		if (statement instanceof Insert) {
			Insert insert = (Insert)statement;

			return insert.getColumns();
		}

		if (statement instanceof Update) {
			Update update = (Update)statement;

			return update.getColumns();
		}

		return Collections.emptyList();
	}

	protected List<Table> getModifiedTablesObject() {
		if (!isChangingData() && !isChangingTableDefinition()) {
			return Collections.emptyList();
		}

		Statement statement = getStatement();

		if (statement == null) {
			return Collections.emptyList();
		}

		if (statement instanceof Alter) {
			Alter alter = (Alter)statement;

			return Collections.singletonList(alter.getTable());
		}

		if (statement instanceof CreateTable) {
			CreateTable createTable = (CreateTable)statement;

			return Collections.singletonList(createTable.getTable());
		}

		if (statement instanceof Delete) {
			Delete delete = (Delete)statement;

			return Collections.singletonList(delete.getTable());
		}

		if (statement instanceof Drop) {
			Drop drop = (Drop)statement;

			return Collections.singletonList(drop.getName());
		}

		if (statement instanceof Insert) {
			Insert insert = (Insert)statement;

			return Collections.singletonList(insert.getTable());
		}

		if (statement instanceof Update) {
			Update update = (Update)statement;

			return Collections.singletonList(update.getTable());
		}

		return Collections.emptyList();
	}

	protected List<Expression> getModifiedValuesObject() {
		if (isReadOnly()) {
			return Collections.emptyList();
		}

		Statement statement = getStatement();

		if (statement == null) {
			return Collections.emptyList();
		}

		if (statement instanceof Insert) {
			Insert insert = (Insert)statement;

			ItemsList itemsList = insert.getItemsList();

			if (itemsList instanceof ExpressionList) {
				ExpressionList expressionList = (ExpressionList)itemsList;

				return expressionList.getExpressions();
			}
		}

		if (statement instanceof Update) {
			Update update = (Update)statement;

			return update.getExpressions();
		}

		return Collections.emptyList();
	}

	protected QueryType getQueryTypeStartsWith() {
		if (startsWithSelect()) {
			return QueryType.SELECT;
		}

		if (startsWithShow()) {
			return QueryType.SHOW;
		}

		if (StringUtils.startsWithIgnoreCase(_sql, "ALTER")) {
			return QueryType.ALTER;
		}

		if (StringUtils.startsWithIgnoreCase(_sql, "CREATE INDEX") ||
			StringUtils.startsWithIgnoreCase(_sql, "CREATE OR REPLACE INDEX") ||
			StringUtils.startsWithIgnoreCase(_sql, "CREATE UNIQUE INDEX")) {

			return QueryType.CREATE_INDEX;
		}

		if (StringUtils.startsWithIgnoreCase(_sql, "CREATE TABLE") ||
			StringUtils.startsWithIgnoreCase(_sql, "CREATE OR REPLACE TABLE") ||
			StringUtils.startsWithIgnoreCase(
				_sql, "CREATE TABLE IF NOT EXISTS")) {

			return QueryType.CREATE_TABLE;
		}

		if (StringUtils.startsWithIgnoreCase(_sql, "CREATE VIEW") ||
			StringUtils.startsWithIgnoreCase(_sql, "CREATE OR REPLACE VIEW") ||
			StringUtils.startsWithIgnoreCase(_sql, "CREATE ROLE") ||
			StringUtils.startsWithIgnoreCase(_sql, "CREATE OR REPLACE ROLE") ||
			StringUtils.startsWithIgnoreCase(_sql, "CREATE RULE") ||
			StringUtils.startsWithIgnoreCase(_sql, "CREATE OR REPLACE RULE")) {

			return QueryType.CREATE_OTHER;
		}

		if (StringUtils.startsWithIgnoreCase(_sql, "DELETE")) {
			return QueryType.DELETE;
		}

		if (StringUtils.startsWithIgnoreCase(_sql, "DROP INDEX")) {
			return QueryType.DROP_INDEX;
		}

		if (StringUtils.startsWithIgnoreCase(_sql, "DROP TABLE")) {
			return QueryType.DROP_TABLE;
		}

		if (StringUtils.startsWithIgnoreCase(_sql, "INSERT")) {
			return QueryType.INSERT;
		}

		if (StringUtils.startsWithIgnoreCase(_sql, "UPDATE")) {
			return QueryType.UPDATE;
		}

		return null;
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

	protected String normalizeSql(String sql) {
		if (StringUtils.containsIgnoreCase(sql, "IGNORE")) {
			sql = StringUtils.replaceIgnoreCase(sql, "INSERT IGNORE", "INSERT");
			sql = StringUtils.replaceIgnoreCase(sql, "UPDATE IGNORE", "UPDATE");
			sql = StringUtils.replaceIgnoreCase(sql, "DELETE IGNORE", "DELETE");
		}

		if (StringUtils.containsIgnoreCase(sql, "JSON")) {
			sql = StringUtils.replaceIgnoreCase(sql, "JSON ", " JSON_ ");
			sql = StringUtils.replaceIgnoreCase(sql, "JSON,", " JSON_,");
			sql = StringUtils.replaceIgnoreCase(sql, "JSON=", " JSON_=");
		}

		if (StringUtils.startsWithIgnoreCase(
				sql, "CREATE SCHEMA IF NOT EXISTS ")) {

			sql = StringUtils.replaceIgnoreCase(
				sql, "CREATE SCHEMA IF NOT EXISTS ", "CREATE SCHEMA ");
		}

		/* alter table @table@ change column @old-column@ @new-column@ @type@;
		alter table @table@ rename column @old-column@ to @new-column@;
		alter table @table@ rename @old-column@ to @new-column@;
		alter table @table@ drop primary key;*/
		if (StringUtils.startsWithIgnoreCase(sql, "ALTER TABLE")) {
			String[] sqlArr = sql.split(" ");

			if (StringUtils.startsWithIgnoreCase(sqlArr[3], "CHANGE") ||
				StringUtils.startsWithIgnoreCase(sqlArr[3], "RENAME")) {

				sql = "ALTER TABLE " + sqlArr[2] + " ADD dummy INTEGER";
			}
			else if (StringUtils.equalsIgnoreCase(sqlArr[3], "DROP") &&
					 StringUtils.equalsIgnoreCase(sqlArr[4], "PRIMARY") &&
					 StringUtils.equalsIgnoreCase(sqlArr[5], "KEY")) {

				sql = "ALTER TABLE " + sqlArr[2] + " DROP CONSTRAINT dummy";
			}
		}

		/* update @table@ inner join @join-clause@ set @set-clause@ where @where@ */
		if (StringUtils.startsWithIgnoreCase(sql, "UPDATE") &&
			StringUtils.containsIgnoreCase(sql, "INNER")) {

			Matcher matcher = updateInnerJoinPattern.matcher(sql);

			if (matcher.matches()) {
				sql =
					"UPDATE " + matcher.group(1) + " SET" + matcher.group(2) +
						"WHERE dummy = 1";
			}
		}

		/*exec sp_rename '@table@.@old-column@', '@new-column@', 'column';*/
		if (StringUtils.startsWithIgnoreCase(sql, "exec sp_rename")) {
			String tableName = StringUtils.substringBefore(sql, ".");

			tableName = StringUtils.substringAfter(tableName, "'");

			sql = "ALTER TABLE " + tableName + " ADD dummy INTEGER";
		}

		return sql;
	}

	protected boolean startsWithSelect() {
		String sqlAux = _sql;

		while (sqlAux.charAt(0) == '(') {
			sqlAux = sqlAux.substring(1);

			sqlAux = sqlAux.trim();
		}

		if (StringUtils.startsWithIgnoreCase(sqlAux, "SELECT")) {
			return true;
		}

		return false;
	}

	protected boolean startsWithShow() {
		String sqlAux = _sql;

		while (sqlAux.charAt(0) == '(') {
			sqlAux = sqlAux.substring(1);

			sqlAux = sqlAux.trim();
		}

		if (StringUtils.startsWithIgnoreCase(sqlAux, "SHOW")) {
			return true;
		}

		return false;
	}

	protected Pattern updateInnerJoinPattern = Pattern.compile(
		"update\\W+(\\w+)\\W+inner\\W+join.*set(.*?)where.*",
		Pattern.CASE_INSENSITIVE);

	private static Logger _log = LogManager.getLogger(Query.class);

	private boolean _cannot_parse_sql = false;
	private int _hashCode = -1;
	private QueryType _queryType = null;
	private String _sql = null;
	private Statement _statement = null;

}