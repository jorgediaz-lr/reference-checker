
package com.liferay.referenceschecker.checkqueries;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import net.sf.jsqlparser.JSQLParserException;
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

public class Query implements Comparable<Query> {

	public enum QueryType {
			ALTER, CREATE_INDEX, CREATE_TABLE, DELETE, DROP, INSERT, UPDATE,
			SELECT
	}

	public Query(String sql) {

		if (!StringUtils.isBlank(sql)) {
			sql = StringUtils.trim(sql);

			try {
				this.statement = CCJSqlParserUtil.parse(sql);
			}
			catch (JSQLParserException pe) {
				_log.error("Error parsing query: " + sql, pe);

				this.statement = null;
				this.sql = sql;
			}

		}

		this.queryType = getQueryType(statement);
	}

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

	public List<String> getModifiedTables() {

		if (statement == null) {
			return Collections.emptyList();
		}

		if (statement instanceof Alter) {
			Alter alter = (Alter) statement;

			return Collections.singletonList(alter.getTable().getName());
		}

		if (statement instanceof CreateTable) {
			CreateTable createTable = (CreateTable) statement;

			return Collections.singletonList(createTable.getTable().getName());
		}

		if (statement instanceof Delete) {
			Delete delete = (Delete) statement;

			return Collections.singletonList(delete.getTable().getName());
		}

		if (statement instanceof Drop) {
			Drop drop = (Drop) statement;

			return Collections.singletonList(drop.getName().getName());
		}

		if (statement instanceof Insert) {
			Insert insert = (Insert) statement;

			return Collections.singletonList(insert.getTable().getName());
		}

		if (statement instanceof Update) {
			Update update = (Update) statement;

			List<Table> tableList = update.getTables();

			return tableList.stream().map(table -> table.getName()).collect(
				Collectors.toList());
		}

		return Collections.emptyList();
	}

	public String getWhere() {

		if (statement == null) {
			return null;
		}

		if (statement instanceof Delete) {
			Delete delete = (Delete) statement;

			return delete.getWhere().toString();
		}

		if (statement instanceof Update) {
			Update update = (Update) statement;

			update.getWhere().toString();
		}

		if (statement instanceof Select) {
			Select select = (Select) statement;

			SelectBody selectBody = select.getSelectBody();

			if (selectBody instanceof PlainSelect) {
				PlainSelect plainSelect = (PlainSelect) selectBody;

				return plainSelect.getWhere().toString();
			}
		}

		return null;
	}

	public Statement getStatement() {

		return statement;
	}

	public QueryType getQueryType() {

		return queryType;
	}

	public String getSQL() {

		if (sql == null) {
			sql = Objects.toString(statement);
		}
		return sql;
	}

	@Override
	public String toString() {

		return getSQL();
	}

	@Override
	public int hashCode() {

		if (hashCode == -1) {
			hashCode = Objects.hash(queryType, getSQL());
		}

		return hashCode;
	}

	@Override
	public int compareTo(Query query) {

		int equals = ObjectUtils.compare(this.queryType, query.queryType);

		if (equals == 0) {
			equals = ObjectUtils.compare(this.getSQL(), query.getSQL());
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

		Query query = (Query) obj;

		return (this.compareTo(query) == 0);
	}

	private int hashCode = -1;
	private QueryType queryType = null;
	private Statement statement = null;
	private String sql = null;

	private static Logger _log = LogManager.getLogger(Query.class);
}
