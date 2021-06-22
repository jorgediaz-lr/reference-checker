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

package com.liferay.referencechecker;

import com.liferay.referencechecker.config.Configuration;
import com.liferay.referencechecker.config.ConfigurationUtil;
import com.liferay.referencechecker.dao.Query;
import com.liferay.referencechecker.dao.Table;
import com.liferay.referencechecker.dao.TableUtil;
import com.liferay.referencechecker.model.ModelUtil;
import com.liferay.referencechecker.model.ModelUtilImpl;
import com.liferay.referencechecker.ref.MissingReferences;
import com.liferay.referencechecker.ref.Reference;
import com.liferay.referencechecker.ref.ReferenceUtil;
import com.liferay.referencechecker.util.JDBCUtil;
import com.liferay.referencechecker.util.SQLUtil;

import java.io.IOException;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * @author Jorge Díaz
 */
public class ReferenceChecker {

	public static long getLiferayBuildNumber(Connection connection) {
		PreparedStatement ps = null;
		ResultSet rs = null;
		long buildNumber = 0;

		try {
			String sql =
				"select buildNumber from Release_ where servletContextName = " +
					"'portal'";

			if (_log.isDebugEnabled()) {
				_log.debug("SQL: " + sql);
			}

			ps = connection.prepareStatement(sql);

			ps.setQueryTimeout(SQLUtil.QUERY_TIMEOUT);

			rs = ps.executeQuery();

			while (rs.next()) {
				buildNumber = rs.getLong(1);
			}
		}
		catch (SQLException sqlException) {
			_log.warn(sqlException);
		}
		finally {
			JDBCUtil.cleanUp(ps);
			JDBCUtil.cleanUp(rs);
		}

		return buildNumber;
	}

	public static long getLiferayMaxCounter(Connection connection) {
		PreparedStatement ps = null;
		ResultSet rs = null;
		long maxCounter = Long.MAX_VALUE;

		try {
			String sql = "select max(currentId) from Counter";

			if (_log.isDebugEnabled()) {
				_log.debug("SQL: " + sql);
			}

			ps = connection.prepareStatement(sql);

			ps.setQueryTimeout(SQLUtil.QUERY_TIMEOUT);

			rs = ps.executeQuery();

			while (rs.next()) {
				maxCounter = rs.getLong(1);
			}
		}
		catch (SQLException sqlException) {
			_log.warn(sqlException);
		}
		finally {
			JDBCUtil.cleanUp(ps);
			JDBCUtil.cleanUp(rs);
		}

		return maxCounter;
	}

	// Replaces Executors.newWorkStealingPool() that doesn't exist in Java 7

	public static ExecutorService newWorkStealingPool() {
		Runtime runtime = Runtime.getRuntime();

		return new ForkJoinPool(
			runtime.availableProcessors(),
			ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);
	}

	public ReferenceChecker(Connection connection) {
		try {
			dbType = SQLUtil.getDBType(connection);
		}
		catch (SQLException sqlException) {
			_log.error(
				"Error getting database type: " + sqlException.getMessage(),
				sqlException);

			throw new RuntimeException(sqlException);
		}

		try {
			configuration = getConfiguration(connection);
		}
		catch (IOException ioException) {
			_log.error(
				"Error reading configuration_xx.yml file: " +
					ioException.getMessage(),
				ioException);

			throw new RuntimeException(ioException);
		}
	}

	public void addExcludeColumns(List<String> excludeColumns) {
		List<String> configurationIgnoreColumns =
			configuration.getIgnoreColumns();

		configurationIgnoreColumns.addAll(excludeColumns);
	}

	public void addTables(Connection connection, Collection<String> tableNames)
		throws SQLException {

		if (tableNames.isEmpty()) {
			return;
		}

		tableUtil.addTables(connection, tableNames);

		referencesCache = null;
	}

	public Collection<Reference> calculateReferences(
		Connection connection, boolean ignoreEmptyTables) {

		ReferenceUtil referenceUtil = new ReferenceUtil(tableUtil, modelUtil);

		referenceUtil.setCheckUndefinedTables(isCheckUndefinedTables());
		referenceUtil.setIgnoreEmptyTables(ignoreEmptyTables);

		return referenceUtil.calculateReferences(connection, configuration);
	}

	public Map<String, Long> calculateTableCount(Connection connection)
		throws IOException, SQLException {

		Map<String, Long> mapTableCount = new TreeMap<>();

		for (Table table : tableUtil.getTables()) {
			long count = TableUtil.countTable(connection, table);

			mapTableCount.put(table.getTableName(), count);
		}

		return mapTableCount;
	}

	public void cleanEmptyTableCacheOnDelete(String tableName) {
		tableUtil.cleanEmptyTableCacheOnDelete(tableName);
	}

	public void cleanEmptyTableCacheOnInsert(String tableName) {
		tableUtil.cleanEmptyTableCacheOnInsert(tableName);
	}

	public void cleanEmptyTableCacheOnUpdate(String tableName) {
		tableUtil.cleanEmptyTableCacheOnUpdate(tableName);
	}

	public List<String> dumpDatabaseInfo(Connection connection)
		throws IOException, SQLException {

		List<String> output = new ArrayList<>();

		long liferayBuildNumber = getLiferayBuildNumber(connection);

		DatabaseMetaData databaseMetaData = connection.getMetaData();

		String dbDriverName = databaseMetaData.getDriverName();
		String dbName = databaseMetaData.getDatabaseProductName();
		int dbMajorVersion = databaseMetaData.getDatabaseMajorVersion();
		int dbMinorVersion = databaseMetaData.getDatabaseMinorVersion();
		String dbUrl = databaseMetaData.getURL();

		output.add("Liferay build number: " + liferayBuildNumber);
		output.add("Database url: " + dbUrl);
		output.add("Database name: " + dbName);
		output.add(
			"Database version major: " + dbMajorVersion + ", minor: " +
				dbMinorVersion);
		output.add("Driver name: " + dbDriverName);

		output.add("");

		List<String> classNamesWithoutTable = new ArrayList<>();

		for (String className : modelUtil.getClassNames()) {
			if (!className.contains(".model.")) {
				continue;
			}

			String tableName = modelUtil.getTableName(className);

			if (tableName == null) {
				classNamesWithoutTable.add(className);
			}
		}

		if (!classNamesWithoutTable.isEmpty()) {
			output.add("ClassName without table information:");

			Collections.sort(classNamesWithoutTable);

			for (String className : classNamesWithoutTable) {
				output.add(
					className + "=" + modelUtil.getClassNameId(className));
			}

			output.add("");
		}

		List<String> tablesWithoutClassName = new ArrayList<>();
		List<String> tablesWithClassName = new ArrayList<>();

		for (Table table : tableUtil.getTables()) {
			String tableName = table.getTableName();

			String className = modelUtil.getClassName(tableName);

			Long classNameId = modelUtil.getClassNameId(className);

			if ((className == null) || (classNameId == null)) {
				tablesWithoutClassName.add(tableName);
			}
			else if (!StringUtils.isEmpty(className)) {
				tablesWithClassName.add(tableName);
			}
		}

		if (!tablesWithoutClassName.isEmpty()) {
			output.add("Tables without className information:");

			for (String tableName : tablesWithoutClassName) {
				output.add(tableName);
			}

			output.add("");
		}

		if (!tablesWithClassName.isEmpty()) {
			output.add("Table-className mapping information:");

			for (String tableName : tablesWithClassName) {
				String className = modelUtil.getClassName(tableName);

				Long classNameId = modelUtil.getClassNameId(className);

				output.add(tableName + "=" + className + "," + classNameId);
			}

			output.add("");
		}

		List<String> missingTables = new ArrayList<>();

		Map<String, String> tableToClassNameMapping =
			configuration.getTableToClassNameMapping();

		Set<String> configuredTables = tableToClassNameMapping.keySet();

		for (String configuredTable : configuredTables) {
			Table table = tableUtil.getTable(configuredTable);

			if (table == null) {
				missingTables.add(configuredTable);
			}
		}

		if (!missingTables.isEmpty()) {
			output.add("Configured tables that does not exist:");

			Collections.sort(missingTables);

			for (String missingTable : missingTables) {
				output.add(
					missingTable + "=" + modelUtil.getClassName(missingTable));
			}

			output.add("");
		}

		List<String> missingClassNames = new ArrayList<>(
			tableToClassNameMapping.values());

		missingClassNames.removeAll(modelUtil.getClassNames());

		List<String> missingClassNamesNoBlank = new ArrayList<>();

		for (String missingClassName : missingClassNames) {
			if (StringUtils.isNotBlank(missingClassName)) {
				missingClassNamesNoBlank.add(missingClassName);
			}
		}

		if (!missingClassNamesNoBlank.isEmpty()) {
			output.add("Configured classNames that does not exist:");

			Collections.sort(missingClassNamesNoBlank);

			for (String missingClassName : missingClassNamesNoBlank) {
				output.add(missingClassName);
			}

			output.add("");
		}

		return output;
	}

	public List<MissingReferences> execute(Connection connection) {
		Collection<Reference> references = calculateReferences(
			connection, true);

		return execute(connection, references);
	}

	public List<MissingReferences> execute(
		Connection connection, Collection<Reference> references) {

		ExecutorService executorService = newWorkStealingPool();

		Map<Reference, Future<MissingReferences>> futures =
			new LinkedHashMap<>();

		for (Reference reference : references) {
			CreateMissingReferences createMissingReferences =
				new CreateMissingReferences(connection, reference);

			Future<MissingReferences> future = executorService.submit(
				createMissingReferences);

			futures.put(reference, future);
		}

		List<MissingReferences> listMissingReferences = new ArrayList<>();

		for (Map.Entry<Reference, Future<MissingReferences>> entry :
				futures.entrySet()) {

			MissingReferences missingReferences = null;

			Reference reference = entry.getKey();
			Future<MissingReferences> future = entry.getValue();

			try {
				missingReferences = future.get();

				if (_log.isInfoEnabled()) {
					_log.info("Processed: " + reference);
				}
			}
			catch (Throwable t) {
				_log.error(
					"EXCEPTION: " + t.getClass() + " - " + t.getMessage(), t);

				missingReferences = new MissingReferences(reference, t);
			}

			if (missingReferences != null) {
				listMissingReferences.add(missingReferences);
			}
		}

		return listMissingReferences;
	}

	public void executeCleanUp(
			Connection connection, MissingReferences missingReferences)
		throws SQLException {

		List<String> cleanupSqls = generateCleanupSentences(missingReferences);

		_executeCleanUp(connection, cleanupSqls);
	}

	public List<String> generateCleanupSentences(
		Collection<MissingReferences> missingReferencesList) {

		List<String> cleanUpSentences = new ArrayList<>();

		for (MissingReferences missingReferences : missingReferencesList) {
			List<String> sqls = generateCleanupSentences(missingReferences);

			if (sqls.isEmpty()) {
				continue;
			}

			Reference reference = missingReferences.getReference();

			cleanUpSentences.add("/* " + reference.toString() + " */");

			for (String sql : sqls) {
				cleanUpSentences.add(sql + ";");
			}
		}

		return cleanUpSentences;
	}

	public List<String> generateSelectSentences(
		Collection<MissingReferences> missingReferencesList) {

		List<String> selectSentences = new ArrayList<>();

		for (MissingReferences missingReferences : missingReferencesList) {
			Collection<Object[]> values = missingReferences.getValues();

			if (values == null) {
				continue;
			}

			Reference reference = missingReferences.getReference();

			List<String> sqls = generateSelectSentences(
				reference, values, true, "*", 2000);

			selectSentences.add("/* " + reference.toString() + " */");

			for (String sql : sqls) {
				selectSentences.add(sql + ";");
			}
		}

		return selectSentences;
	}

	public Configuration getConfiguration() {
		return configuration;
	}

	public long getIgnoreGreaterValues() {
		return ignoreGreaterValues;
	}

	public long getIgnoreLowerValues() {
		return ignoreLowerValues;
	}

	public Collection<Reference> getReferences(
		Connection connection, boolean ignoreEmptyTables) {

		if (referencesCache == null) {
			referencesCache = calculateReferences(connection, false);
		}

		List<Reference> referencesList = new ArrayList<>();

		for (Reference reference : referencesCache) {
			Query destinationQuery = reference.getDestinationQuery();

			if (destinationQuery == null) {
				continue;
			}

			if (ignoreEmptyTables) {
				if (reference.isRaw()) {
					continue;
				}

				Query originQuery = reference.getOriginQuery();

				Table originTable = originQuery.getTable();

				String whereClause = originQuery.getCondition();

				if (tableUtil.isTableEmpty(
						connection, originTable, whereClause)) {

					continue;
				}
			}

			referencesList.add(reference);
		}

		return referencesList;
	}

	public boolean ignoreColumn(String tableName, String columnName) {
		return tableUtil.ignoreColumn(tableName, columnName);
	}

	public boolean ignoreTable(String tableName) {
		return tableUtil.ignoreTable(tableName);
	}

	public void initModelUtil(Connection connection) throws SQLException {
		initModelUtil(connection, new ModelUtilImpl());
	}

	public void initModelUtil(Connection connection, ModelUtil modelUtil)
		throws SQLException {

		Map<String, String> tableNameToClassNameMapping =
			configuration.getTableToClassNameMapping();

		modelUtil.init(
			connection, tableNameToClassNameMapping,
			configuration.getTableRank());

		this.modelUtil = modelUtil;
	}

	public synchronized void initTableUtil(Connection connection)
		throws SQLException {

		List<String> ignoreColumns = configuration.getIgnoreColumns();

		List<String> ignoreTables = configuration.getIgnoreTables();

		tableUtil = new TableUtil();

		tableUtil.init(connection, ignoreColumns, ignoreTables, modelUtil);
	}

	public boolean isCheckUndefinedTables() {
		return checkUndefinedTables;
	}

	public boolean isIgnoreNullValues() {
		return ignoreNullValues;
	}

	public Collection<Object[]> queryInvalidValues(
			Connection connection, Query originQuery, Query destinationQuery)
		throws SQLException {

		Set<Object[]> invalidValuesSet = new LinkedHashSet<>();

		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			String sql = getSQLSelect(originQuery, destinationQuery);

			if (_log.isInfoEnabled()) {
				_log.info("SQL: " + sql);
			}

			ps = connection.prepareStatement(sql);

			ps.setQueryTimeout(SQLUtil.HEAVY_QUERY_TIMEOUT);

			rs = ps.executeQuery();

			ResultSetMetaData rsmd = rs.getMetaData();

			int columnsNumber = rsmd.getColumnCount();

			while (rs.next()) {
				Object[] result = new Object[columnsNumber];

				for (int i = 0; i < columnsNumber; i++) {
					result[i] = rs.getObject(i + 1);
				}

				if (_isValidValue(result)) {
					continue;
				}

				invalidValuesSet.add(result);
			}
		}
		finally {
			JDBCUtil.cleanUp(ps, rs);
		}

		return invalidValuesSet;
	}

	public void reloadModelUtil(Connection connection) throws SQLException {
		initModelUtil(connection, modelUtil);
	}

	public void removeTables(Collection<String> tableNames) {
		if (tableNames.isEmpty()) {
			return;
		}

		tableUtil.removeTables(tableNames);

		referencesCache = null;
	}

	public void setCheckUndefinedTables(boolean checkUndefinedTables) {
		this.checkUndefinedTables = checkUndefinedTables;
	}

	public void setIgnoreGreaterValues(long ignoreGreaterValues) {
		this.ignoreGreaterValues = ignoreGreaterValues;
	}

	public void setIgnoreLowerValues(long ignoreLowerValues) {
		this.ignoreLowerValues = ignoreLowerValues;
	}

	public void setIgnoreNullValues(boolean ignoreNullValues) {
		this.ignoreNullValues = ignoreNullValues;
	}

	protected List<String> generateCleanupSentences(
		MissingReferences missingReferences) {

		Reference reference = missingReferences.getReference();

		Collection<Object[]> values = missingReferences.getValues();

		String fixAction = reference.getFixAction();

		if (Objects.equals(fixAction, "delete")) {
			return generateDeleteSentences(reference, values, 2000);
		}

		if (Objects.equals(fixAction, "update")) {
			return generateUpdateSentences(reference, values, 2000);
		}

		return Collections.emptyList();
	}

	protected String generateDeleteSentence(
		Reference reference, Collection<Object[]> values) {

		Query originQuery = reference.getOriginQuery();

		StringBuilder sb = new StringBuilder();

		sb.append(originQuery.getSQLDelete());

		sb.append(" AND (");

		_appendInClause(sb, originQuery, values);

		sb.append(")");

		return SQLUtil.transform(dbType, sb.toString());
	}

	protected List<String> generateDeleteSentences(
		Reference reference, Collection<Object[]> values, int batchSize) {

		List<String> sentences = new ArrayList<>();

		List<Object[]> list = new ArrayList<>(values);

		List<List<Object[]>> sublists = ListUtils.partition(list, batchSize);

		for (List<Object[]> sublist : sublists) {
			sentences.add(generateDeleteSentence(reference, sublist));
		}

		return sentences;
	}

	protected String generateSelectSentence(
		Reference reference, Collection<Object[]> values, boolean distinct,
		String columnsString) {

		Query originQuery = reference.getOriginQuery();

		StringBuilder sb = new StringBuilder();

		sb.append(originQuery.getSQLSelect(distinct, columnsString));

		sb.append(" AND (");

		_appendInClause(sb, originQuery, values);

		sb.append(")");

		return SQLUtil.transform(dbType, sb.toString());
	}

	protected List<String> generateSelectSentences(
		Reference reference, Collection<Object[]> values, boolean distinct,
		String columnsString, int batchSize) {

		List<String> sentences = new ArrayList<>();

		List<Object[]> list = new ArrayList<>(values);

		List<List<Object[]>> sublists = ListUtils.partition(list, batchSize);

		for (List<Object[]> sublist : sublists) {
			sentences.add(
				generateSelectSentence(
					reference, sublist, distinct, columnsString));
		}

		return sentences;
	}

	protected String generateUpdateSentence(
		Reference reference, Collection<Object[]> values) {

		Query originQuery = reference.getOriginQuery();

		StringBuilder sb = new StringBuilder();

		sb.append(originQuery.getSQLUpdateToNull());

		sb.append(" AND (");

		_appendInClause(sb, originQuery, values);

		sb.append(")");

		return SQLUtil.transform(dbType, sb.toString());
	}

	protected List<String> generateUpdateSentences(
		Reference reference, Collection<Object[]> values, int batchSize) {

		List<String> sentences = new ArrayList<>();

		List<Object[]> list = new ArrayList<>(values);

		List<List<Object[]>> sublists = ListUtils.partition(list, batchSize);

		for (List<Object[]> sublist : sublists) {
			sentences.add(generateUpdateSentence(reference, sublist));
		}

		return sentences;
	}

	protected Configuration getConfiguration(Connection connection)
		throws IOException {

		long liferayBuildNumber = getLiferayBuildNumber(connection);

		if (liferayBuildNumber == 0) {
			_log.warn("Liferay build number could not be retrieved");

			return null;
		}

		if (_log.isInfoEnabled()) {
			_log.info("Liferay build number: " + liferayBuildNumber);
		}

		String configurationFile = ConfigurationUtil.getConfigurationFileName(
			liferayBuildNumber);

		Class<?> clazz = getClass();

		return ConfigurationUtil.readConfigurationFile(
			clazz.getClassLoader(), configurationFile);
	}

	protected String getSQLDelete(Query originQuery, Query destinationQuery) {
		return _getSQL(originQuery, destinationQuery, "delete");
	}

	protected String getSQLSelect(Query originQuery, Query destinationQuery) {
		return _getSQL(originQuery, destinationQuery, "select");
	}

	protected String getSQLSelectCount(
		Query originQuery, Query destinationQuery) {

		return _getSQL(originQuery, destinationQuery, "count");
	}

	protected long queryCount(
			Connection connection, Reference reference,
			Collection<Object[]> invalidValues)
		throws SQLException {

		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			Query originQuery = reference.getOriginQuery();

			Table originTable = originQuery.getTable();

			String key = "*";

			if (!originTable.hasCompoundPrimKey()) {
				key = originTable.getPrimaryKey();
			}

			String attributesSqlCount = " COUNT(" + key + ")";

			List<String> sqlCounts = generateSelectSentences(
				reference, invalidValues, false, attributesSqlCount, 2000);

			int count = 0;

			for (String sqlCount : sqlCounts) {
				if (_log.isDebugEnabled()) {
					_log.debug("SQL count: " + sqlCount);
				}

				ps = connection.prepareStatement(sqlCount);

				ps.setQueryTimeout(SQLUtil.HEAVY_QUERY_TIMEOUT);

				rs = ps.executeQuery();

				while (rs.next()) {
					count += rs.getLong(1);
				}
			}

			return count;
		}
		catch (SQLException sqlException) {
			_log.warn(sqlException);
		}
		finally {
			JDBCUtil.cleanUp(ps, rs);
		}

		return -1;
	}

	protected boolean checkUndefinedTables = false;
	protected Configuration configuration;
	protected String dbType;
	protected long ignoreGreaterValues = Long.MAX_VALUE;
	protected long ignoreLowerValues = Long.MIN_VALUE;
	protected boolean ignoreNullValues = true;
	protected ModelUtil modelUtil;
	protected Collection<Reference> referencesCache = null;
	protected TableUtil tableUtil;

	protected class CreateMissingReferences
		implements Callable<MissingReferences> {

		public CreateMissingReferences(
			Connection connection, Reference reference) {

			this.connection = connection;
			this.reference = reference;
		}

		@Override
		public MissingReferences call() {
			try {
				if (_log.isInfoEnabled()) {
					_log.info("Processing: " + reference);
				}

				if (reference.isRaw()) {
					return null;
				}

				Query destinationQuery = reference.getDestinationQuery();

				if (destinationQuery == null) {
					return null;
				}

				Collection<Object[]> invalidValues = queryInvalidValues(
					connection, reference.getOriginQuery(), destinationQuery);

				if (invalidValues.isEmpty()) {
					return null;
				}

				long affectedRows = queryCount(
					connection, reference, invalidValues);

				return new MissingReferences(
					reference, invalidValues, affectedRows);
			}
			catch (Throwable t) {
				_log.error(
					"EXCEPTION: " + t.getClass() + " - " + t.getMessage(), t);

				return new MissingReferences(reference, t);
			}
		}

		protected Connection connection;
		protected Reference reference;

	}

	private void _appendInClause(
		StringBuilder sb, List<String> columns, Collection<Object[]> rows) {

		sb.append("(");
		sb.append(StringUtils.join(columns, ","));
		sb.append(") IN (");

		boolean first = true;

		for (Object[] row : rows) {
			if (!first) {
				sb.append(",");
			}

			first = false;

			if (row.length == 1) {
				sb.append(_castValue(row[0]));

				continue;
			}

			sb.append("(");

			for (int i = 0; i < row.length; i++) {
				if (i > 0) {
					sb.append(",");
				}

				sb.append(_castValue(row[i]));
			}

			sb.append(")");
		}

		sb.append(")");
	}

	private void _appendInClause(
		StringBuilder sb, Query query, Collection<Object[]> values) {

		List<Object[]> rows = new ArrayList<>(values);

		List<String> columns = query.getColumns();

		/* Split in sublists of 1000 elements as Oracle doesn't support
		 * bigger IN clauses */
		List<List<Object[]>> sublists = ListUtils.partition(rows, 1000);

		boolean first = true;

		for (List<Object[]> sublist : sublists) {
			if (!first) {
				sb.append(" OR ");
			}

			_appendInClause(sb, columns, sublist);

			first = false;
		}
	}

	private String _castValue(Object value) {
		if (value instanceof Number) {
			return value.toString();
		}

		if ((value instanceof String) &&
			StringUtils.equalsIgnoreCase("null", (String)value)) {

			return "NULL";
		}

		StringBuilder sb = new StringBuilder();

		sb.append("'");
		sb.append(value);
		sb.append("'");

		return sb.toString();
	}

	private void _executeCleanUp(
			Connection connection, List<String> cleanupSqls)
		throws SQLException {

		for (String cleanupSql : cleanupSqls) {
			if (_log.isDebugEnabled()) {
				_log.debug("Cleanup sql: " + cleanupSql);
			}

			PreparedStatement ps = null;

			try {
				ps = connection.prepareStatement(cleanupSql);

				ps.setQueryTimeout(SQLUtil.HEAVY_QUERY_TIMEOUT);

				int rows = ps.executeUpdate();

				if (_log.isDebugEnabled()) {
					_log.debug("Procesed " + rows + " rows");
				}
			}
			catch (SQLException sqlException) {
				_log.error(
					"SQL: " + cleanupSql + " - EXCEPTION: " +
						sqlException.getMessage(),
					sqlException);

				throw sqlException;
			}
			finally {
				JDBCUtil.cleanUp(ps);
			}
		}
	}

	private String _getSQL(
		Query originQuery, Query destinationQuery, String type) {

		if (dbType.equals(SQLUtil.TYPE_POSTGRESQL) ||
			dbType.equals(SQLUtil.TYPE_SQLSERVER)) {

			String sql = _getSQLNotExists(originQuery, destinationQuery, type);

			return SQLUtil.transform(dbType, sql);
		}

		String sql = _getSQLNotIn(originQuery, destinationQuery, type);

		return SQLUtil.transform(dbType, sql);
	}

	private String _getSQL(Query query, String type) {
		if (Objects.equals(type, "count")) {
			return query.getSQLSelectCount();
		}

		if (Objects.equals(type, "delete")) {
			return query.getSQLDelete();
		}

		if (Objects.equals(type, "select")) {
			return query.getSQLSelect();
		}

		throw new IllegalArgumentException(type);
	}

	private String _getSQLNotExists(
		Query originQuery, Query destinationQuery, String type) {

		List<String> conditionColumns = originQuery.getColumnsWithCast(
			dbType, destinationQuery);

		List<String> destinationColumns = destinationQuery.getColumnsWithCast(
			dbType, originQuery);

		StringBuilder sb = new StringBuilder();

		sb.append(_getSQL(originQuery, type));

		sb.append(" AND NOT EXISTS (");
		sb.append(
			destinationQuery.getSQLSelect(
				false, StringUtils.join(destinationColumns, ",")));

		for (int i = 0; i < conditionColumns.size(); i++) {
			sb.append(" AND ");
			sb.append(conditionColumns.get(i));
			sb.append("=");
			sb.append(destinationColumns.get(i));
		}

		sb.append(")");

		return sb.toString();
	}

	private String _getSQLNotIn(
		Query originQuery, Query destinationQuery, String type) {

		List<String> conditionColumns = originQuery.getColumnsWithCast(
			dbType, destinationQuery);

		List<String> destinationColumns = destinationQuery.getColumnsWithCast(
			dbType, originQuery);

		StringBuilder sb = new StringBuilder();

		sb.append(_getSQL(originQuery, type));

		sb.append(" AND (");
		sb.append(StringUtils.join(conditionColumns, ","));
		sb.append(") NOT IN (");
		sb.append(
			destinationQuery.getSQLSelect(
				false, StringUtils.join(destinationColumns, ",")));
		sb.append(")");

		return sb.toString();
	}

	private boolean _isNull(Object obj) {
		if (obj == null) {
			return true;
		}

		if (obj instanceof Number) {
			Number n = (Number)obj;

			if (n.longValue() == 0L) {
				return true;
			}

			return false;
		}

		if (obj instanceof String) {
			return StringUtils.isBlank((String)obj);
		}

		return false;
	}

	private boolean _isValidValue(Object[] result) {
		if (!isIgnoreNullValues()) {
			return false;
		}

		for (Object o : result) {
			if (o == null) {
				continue;
			}

			if (o instanceof Number) {
				Number n = (Number)o;

				if ((n.longValue() == 0) ||
					(n.longValue() > ignoreGreaterValues) ||
					(n.longValue() < ignoreLowerValues)) {

					continue;
				}
			}

			if (!_isNull(o) && !Objects.equals(o, "0")) {
				return false;
			}
		}

		return true;
	}

	private static Logger _log = LogManager.getLogger(ReferenceChecker.class);

}