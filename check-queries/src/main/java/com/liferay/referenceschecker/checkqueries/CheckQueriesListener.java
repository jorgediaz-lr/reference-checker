package com.liferay.referenceschecker.checkqueries;

import java.sql.SQLException;

import org.apache.commons.lang3.StringUtils;

import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;

public class CheckQueriesListener extends SimpleJdbcEventListener {

	public static final CheckQueriesListener INSTANCE = new CheckQueriesListener();

	private boolean _debug = false;

	protected CheckQueriesListener() {
		String debugProperty = System.getProperty("debug");

		_debug = (StringUtils.equalsIgnoreCase(debugProperty, "true")
				|| StringUtils.equalsIgnoreCase(debugProperty, "1"));
	}

	private String getRequestInfo(int connectionId, String category, String query, SQLException e,
			long timeElapsedNanos) {

		String timeElapsedNanosString = "";

		if (timeElapsedNanos > 0) {
			timeElapsedNanosString = Long.toString(timeElapsedNanos);
		}

		return "" + connectionId + "|" + category + "|" + query + "|" + e + "|" + timeElapsedNanosString;
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

	protected void logDebugRequest(String message, int connectionId, String category, String query, SQLException e,
			long timeElapsedNanos) {

		if (!isDebugEnabled()) {
			return;
		}

		logRequest(message, connectionId, category, query, e, timeElapsedNanos);
	}

	protected void logRequest(String message, int connectionId, String category, String query, SQLException e,
			long timeElapsedNanos) {
		String request = getRequestInfo(connectionId, category, query, e, timeElapsedNanos);

		System.out.println(message + request);
	}

	@Override
	public void onAfterAnyAddBatch(StatementInformation statementInformation, long timeElapsedNanos, SQLException e) {
		processStatementInformation(statementInformation, timeElapsedNanos, "batch", e);
	}

	@Override
	public void onAfterAnyExecute(StatementInformation statementInformation, long timeElapsedNanos, SQLException e) {
		processStatementInformation(statementInformation, timeElapsedNanos, "statement", e);
	}

	@Override
	public void onAfterCommit(ConnectionInformation connectionInformation, long timeElapsedNanos, SQLException e) {
		processConnectionInformation(connectionInformation, timeElapsedNanos, "after-commit", e);
	}

	public void onAfterConnectionClose(ConnectionInformation connectionInformation, SQLException e) {
		processConnectionInformation(connectionInformation, -1, "connection-close", e);
	}

	@Override
	public void onAfterExecuteBatch(StatementInformation statementInformation, long timeElapsedNanos,
			int[] updateCounts, SQLException e) {
		processStatementInformation(statementInformation, timeElapsedNanos, "batch", e);
	}

	public void onAfterGetConnection(ConnectionInformation connectionInformation, SQLException e) {
		processConnectionInformation(connectionInformation, -1, "get-connection", e);
	}

	@Override
	public void onAfterRollback(ConnectionInformation connectionInformation, long timeElapsedNanos, SQLException e) {
		processConnectionInformation(connectionInformation, timeElapsedNanos, "rollback", e);
	}

	public void onBeforeCommit(ConnectionInformation connectionInformation) {
		processConnectionInformation(connectionInformation, -1, "before-commit", null);
	}

	protected void processConnectionInformation(ConnectionInformation connectionInformation, long timeElapsedNanos,
			String category, SQLException e) {
		int connectionId = connectionInformation.getConnectionId();

		logDebugRequest("Operation: ", connectionId, category, null, e, timeElapsedNanos);
	}

	protected void processStatementInformation(StatementInformation statementInformation, long timeElapsedNanos,
			String category, SQLException e) {

		ConnectionInformation connectionInformation = statementInformation.getConnectionInformation();

		int connectionId = connectionInformation.getConnectionId();

		String sql = statementInformation.getSqlWithValues();

		sql = StringUtils.trim(sql);

		if (StringUtils.isBlank(sql) || StringUtils.startsWithIgnoreCase(sql, "SELECT") || StringUtils.startsWithIgnoreCase(sql, "(SELECT")) {
			logDebugRequest("Ignored Query: ", connectionId, category, sql, e, timeElapsedNanos);

			return;
		}

		Statement statement = null;

		try {
			statement = CCJSqlParserUtil.parse(sql);
		} catch (JSQLParserException pe) {
			System.err.println("Error parsing query: " + sql);

			pe.printStackTrace(System.err);

			return;
		}

		if (statement instanceof Select) {
			logDebugRequest("Ignored Query: ", connectionId, category, sql, e, timeElapsedNanos);

			return;
		}

		logRequest("Query: ", connectionId, category, statement.toString(), e, timeElapsedNanos);
	}
}
