
package com.liferay.referenceschecker.checkqueries;

import java.sql.SQLException;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.liferay.referenceschecker.checkqueries.Query.QueryType;
import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;

public class CheckQueriesListener extends SimpleJdbcEventListener {

	public static final CheckQueriesListener INSTANCE =
		new CheckQueriesListener();

	@Override
	public void onAfterAnyAddBatch(
		StatementInformation statementInformation, long timeElapsedNanos,
		SQLException e) {

		if (e != null) {
			return;
		}

		processQuery(
			statementInformation.getConnectionInformation(),
			statementInformation.getSqlWithValues());
	}

	@Override
	public void onAfterAnyExecute(
		StatementInformation statementInformation, long timeElapsedNanos,
		SQLException e) {

		if (e != null) {
			return;
		}

		processQuery(
			statementInformation.getConnectionInformation(),
			statementInformation.getSqlWithValues());
	}

	@Override
	public void onAfterCommit(
		ConnectionInformation connectionInformation, long timeElapsedNanos,
		SQLException e) {

		resetThreadLocals();

		if (_log.isDebugEnabled()) {
			_log.debug(
				"After commit. Connection-id=" +
					connectionInformation.getConnectionId());
		} ;
	}

	@Override
	public void onAfterConnectionClose(
		ConnectionInformation connectionInformation, SQLException e) {

		resetThreadLocals();

		if (_log.isDebugEnabled()) {
			_log.debug(
				"After connection close. Connection-id=" +
					connectionInformation.getConnectionId());
		}
	}

	@Override
	public void onAfterExecuteBatch(
		StatementInformation statementInformation, long timeElapsedNanos,
		int[] updateCounts, SQLException e) {

		if (e != null) {
			return;
		}

		processQuery(
			statementInformation.getConnectionInformation(),
			statementInformation.getSqlWithValues());
	}

	@Override
	public void onAfterGetConnection(
		ConnectionInformation connectionInformation, SQLException e) {

		resetThreadLocals();

		if (_log.isDebugEnabled()) {
			_log.debug(
				"After get connection. Connection-id=" +
					connectionInformation.getConnectionId());
		}
	}

	@Override
	public void onAfterRollback(
		ConnectionInformation connectionInformation, long timeElapsedNanos,
		SQLException e) {

		resetThreadLocals();

		if (_log.isDebugEnabled()) {
			_log.debug(
				"After rollback. Connection-id=" +
					connectionInformation.getConnectionId());
		} ;;
	}

	@Override
	public void onBeforeCommit(ConnectionInformation connectionInformation) {

		if (_log.isDebugEnabled()) {
			_log.debug(
				"Before commit. Connection-id=" +
					connectionInformation.getConnectionId());
		} ;

		logQueries(
			connectionInformation, insertQueryListThreadLocal.get(),
			"Insert queries");
		logQueries(
			connectionInformation, updateQueryListThreadLocal.get(),
			"Update queries");
		logQueries(
			connectionInformation, deleteQueryListThreadLocal.get(),
			"Delete queries");
		logQueries(
			connectionInformation, otherQueryListThreadLocal.get(),
			"Other queries");
	}

	private void logQueries(
		ConnectionInformation connectionInformation, Set<Query> queryList,
		String message) {

		if ((queryList == null) || queryList.isEmpty()) {

			return;
		}

		if (!_log.isInfoEnabled()) {

			return;
		}

		int connectionId = connectionInformation.getConnectionId();

		_log.info(message + ". Connection-id=" + connectionId);

		for (Query query : queryList) {
			_log.info("Query: " + query);

			if (!_log.isDebugEnabled()) {
				continue;
			}

			_log.debug("\t modified tables: " + query.getModifiedTables());
			_log.debug("\t where: " + query.getWhere());
		}
	}

	protected void processQuery(
		ConnectionInformation connectionInformation, String sql) {

		int connectionId = connectionInformation.getConnectionId();

		Query query = new Query(sql);

		if ((query.getQueryType() == null) ||
			(query.getQueryType() == QueryType.SELECT)) {

			if (_log.isDebugEnabled()) {
				_log.debug(
					"Ignored Query. connectionId=" + connectionId + ", sql=" +
						query.getSQL());
			}

			return;
		}

		if (query.getQueryType() == QueryType.INSERT) {
			addQueryToThreadLocal(insertQueryListThreadLocal, query);
		}
		else if (query.getQueryType() == QueryType.UPDATE) {
			addQueryToThreadLocal(updateQueryListThreadLocal, query);
		}
		else if (query.getQueryType() == QueryType.DELETE) {
			addQueryToThreadLocal(deleteQueryListThreadLocal, query);
		}
		else {
			addQueryToThreadLocal(otherQueryListThreadLocal, query);
		}
	}

	private void addQueryToThreadLocal(
		ThreadLocal<Set<Query>> queryListThreadLocal, Query query) {

		if (queryListThreadLocal.get() == null) {
			queryListThreadLocal.set(new TreeSet<Query>());
		}

		queryListThreadLocal.get().add(query);
	}

	protected void resetThreadLocals() {

		insertQueryListThreadLocal.set(new TreeSet<Query>());
		updateQueryListThreadLocal.set(new TreeSet<Query>());
		deleteQueryListThreadLocal.set(new TreeSet<Query>());
		otherQueryListThreadLocal.set(new TreeSet<Query>());
	}

	private ThreadLocal<Set<Query>> insertQueryListThreadLocal =
		new ThreadLocal<Set<Query>>();

	private ThreadLocal<Set<Query>> updateQueryListThreadLocal =
		new ThreadLocal<Set<Query>>();

	private ThreadLocal<Set<Query>> deleteQueryListThreadLocal =
		new ThreadLocal<Set<Query>>();

	private ThreadLocal<Set<Query>> otherQueryListThreadLocal =
		new ThreadLocal<Set<Query>>();

	private static Logger _log = LogManager.getLogger(
		CheckQueriesListener.class);
}
