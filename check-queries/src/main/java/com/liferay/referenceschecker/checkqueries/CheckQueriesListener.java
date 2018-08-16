
package com.liferay.referenceschecker.checkqueries;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;

import com.liferay.referenceschecker.checkqueries.ExecutedQuery.QueryType;
import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;

public class CheckQueriesListener extends SimpleJdbcEventListener {

	public static final CheckQueriesListener INSTANCE =
		new CheckQueriesListener();

	private boolean _debug = false;

	protected CheckQueriesListener() {
		String debugProperty = System.getProperty("debug");

		_debug = (StringUtils.equalsIgnoreCase(debugProperty, "true") ||
			StringUtils.equalsIgnoreCase(debugProperty, "1"));
	}

	private String getRequestInfo(
		int connectionId, String category, String query, SQLException e) {

		return "" + connectionId + "|" + category + "|" + query + "|" + e;
	}

	protected boolean isDebugEnabled() {

		return _debug;
	}

	protected void logDebug(String message) {

		if (!isDebugEnabled()) {
			return;
		}

		System.out.println(message);
	}

	@Override
	public void onAfterAnyAddBatch(
		StatementInformation statementInformation, long timeElapsedNanos,
		SQLException e) {

		processStatementInformation(
			statementInformation, "batch", e);
	}

	@Override
	public void onAfterAnyExecute(
		StatementInformation statementInformation, long timeElapsedNanos,
		SQLException e) {

		processStatementInformation(
			statementInformation, "statement", e);
	}

	@Override
	public void onAfterCommit(
		ConnectionInformation connectionInformation, long timeElapsedNanos,
		SQLException e) {

		executedQueryListThreadLocal.set(new TreeSet<ExecutedQuery>());

		processConnectionInformation(
			connectionInformation, "after-commit", e);
	}

	@Override
	public void onAfterConnectionClose(
		ConnectionInformation connectionInformation, SQLException e) {

		executedQueryListThreadLocal.set(new TreeSet<ExecutedQuery>());

		processConnectionInformation(connectionInformation, "connection-close", e);
	}

	@Override
	public void onAfterExecuteBatch(
		StatementInformation statementInformation, long timeElapsedNanos,
		int[] updateCounts, SQLException e) {

		processStatementInformation(statementInformation, "batch", e);
	}

	@Override
	public void onAfterGetConnection(
		ConnectionInformation connectionInformation, SQLException e) {

		executedQueryListThreadLocal.set(new TreeSet<ExecutedQuery>());

		processConnectionInformation(
			connectionInformation, "get-connection", e);
	}

	@Override
	public void onAfterRollback(
		ConnectionInformation connectionInformation, long timeElapsedNanos,
		SQLException e) {

		executedQueryListThreadLocal.set(new TreeSet<ExecutedQuery>());

		processConnectionInformation(
			connectionInformation, "rollback", e);
	}

	@Override
	public void onBeforeCommit(ConnectionInformation connectionInformation) {

		processConnectionInformation(
			connectionInformation, "before-commit", null);

		handleExecutedQueriesBeforeCommit(
			connectionInformation, executedQueryListThreadLocal.get());
	}

	protected void processConnectionInformation(
		ConnectionInformation connectionInformation, String category,
		SQLException e) {

		int connectionId = connectionInformation.getConnectionId();

		if (isDebugEnabled()) {
			String request = getRequestInfo(
				connectionId, category, null, e);

			logDebug("Operation: " + request);
		}
	}

	protected void handleExecutedQueriesBeforeCommit(
		ConnectionInformation connectionInformation,
		Set<ExecutedQuery> executedQueryList) {

		Set<ExecutedQuery> addValuesQueryList = new TreeSet<ExecutedQuery>();
		Set<ExecutedQuery> removeValuesQueryList = new TreeSet<ExecutedQuery>();
		Set<ExecutedQuery> otherQueryList = new TreeSet<ExecutedQuery>();

		for (ExecutedQuery executedQuery : executedQueryList) {
			if (executedQuery.getQueryType() == QueryType.INSERT) {
				addValuesQueryList.add(executedQuery);
			}
			else if (executedQuery.getQueryType() == QueryType.UPDATE) {
				addValuesQueryList.add(executedQuery);
				removeValuesQueryList.add(executedQuery);
			}
			else if (executedQuery.getQueryType() == QueryType.DELETE) {
				removeValuesQueryList.add(executedQuery);
			}
			else {
				otherQueryList.add(executedQuery);
			}
		}

		logQueries(
			connectionInformation, addValuesQueryList, "Add values queries");
		logQueries(
			connectionInformation, removeValuesQueryList,
			"Remove values queries");
		logQueries(connectionInformation, otherQueryList, "Other queries");
	}

	private void logQueries(
		ConnectionInformation connectionInformation,
		Set<ExecutedQuery> executedQueryList, String message) {

		if (executedQueryList.isEmpty()) {
			return;
		}

		int connectionId = connectionInformation.getConnectionId();

		System.out.println(message + " for connection " + connectionId);

		for (ExecutedQuery executedQuery : executedQueryList) {
			System.out.println("Query : " + connectionId + "|" + executedQuery);
		}
	}

	protected void processStatementInformation(
		StatementInformation statementInformation, String category,
		SQLException e) {

		ConnectionInformation connectionInformation =
			statementInformation.getConnectionInformation();

		int connectionId = connectionInformation.getConnectionId();

		String sql = statementInformation.getSqlWithValues();

		sql = StringUtils.trim(sql);

		if (StringUtils.isBlank(sql) || StringUtils.startsWithIgnoreCase(
			sql, "SELECT") || StringUtils.startsWithIgnoreCase(
				sql, "(SELECT")) {

			if (isDebugEnabled()) {
				String request = getRequestInfo(connectionId, category, sql, e);

				logDebug("Ignored Query: " + request);
			}

			return;
		}

		Statement statement = null;

		try {
			statement = CCJSqlParserUtil.parse(sql);
		}
		catch (JSQLParserException pe) {
			String request = getRequestInfo(
				connectionId, category, sql, e);

			System.err.println("Error parsing query: " + request);

			pe.printStackTrace(System.err);

			return;
		}

		if (statement instanceof Select) {
			if (isDebugEnabled()) {
				String request = getRequestInfo(
					connectionId, category, sql, e);

				logDebug("Ignored Query: " + request);
			}

			return;
		}

		ExecutedQuery executedQuery = new ExecutedQuery(statement, category, e);

		executedQueryListThreadLocal.get().add(executedQuery);
	}

	private ThreadLocal<Set<ExecutedQuery>> executedQueryListThreadLocal =
		new ThreadLocal<Set<ExecutedQuery>>() {

			@Override
			protected Set<ExecutedQuery> initialValue() {

				return new TreeSet<ExecutedQuery>();
			}
		};
}
