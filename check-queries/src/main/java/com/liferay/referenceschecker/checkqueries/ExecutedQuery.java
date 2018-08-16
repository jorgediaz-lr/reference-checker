
package com.liferay.referenceschecker.checkqueries;

import java.sql.SQLException;

import org.apache.commons.lang3.ObjectUtils;

import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

public class ExecutedQuery implements Comparable<ExecutedQuery>{

	public enum QueryType {
			ALTER, CREATE_INDEX, CREATE_TABLE, DELETE, DROP, INSERT, UPDATE,
			SELECT
	}

	public ExecutedQuery(
		Statement statement, String category, SQLException sqlException) {

		this.queryType = getQueryType(statement);
		this.statement = statement;
		this.category = category;
		this.sqlException = sqlException;
	}

	public static QueryType getQueryType(Statement statement) {

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

	public Statement getStatement() {

		return statement;
	}

	public String getCategory() {

		return category;
	}

	public SQLException getSqlException() {

		return sqlException;
	}

	public QueryType getQueryType() {

		return queryType;
	}

	public String getSQL() {

		if (sql == null) {
			sql = statement.toString();
		}
		return sql;
	}

	@Override
	public String toString() {

		return category + "|" + queryType + "|" + getSQL() + "|" + sqlException;
	}

	@Override
	public int hashCode() {

		int result = 17;
		result = 31 * result + queryType.hashCode();
		result = 31 * result + sql.hashCode();
		return result;
	}

	@Override
	public int compareTo(ExecutedQuery query) {

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

		if (!(obj instanceof ExecutedQuery)) {
			return false;
		}

		ExecutedQuery query = (ExecutedQuery) obj;

		return (this.compareTo(query) == 0);
	}

	private String category;
	private QueryType queryType;
	private SQLException sqlException;
	private Statement statement;
	private String sql;

}
