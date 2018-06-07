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

package com.liferay.referenceschecker;

import com.liferay.portal.kernel.dao.jdbc.DataAccess;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.referenceschecker.config.Configuration;
import com.liferay.referenceschecker.config.ConfigurationUtil;
import com.liferay.referenceschecker.dao.Query;
import com.liferay.referenceschecker.dao.Table;
import com.liferay.referenceschecker.dao.TableUtil;
import com.liferay.referenceschecker.ref.MissingReferences;
import com.liferay.referenceschecker.ref.Reference;
import com.liferay.referenceschecker.ref.ReferenceUtil;
import com.liferay.referenceschecker.util.SQLUtil;
import com.liferay.referenceschecker.util.StringUtil;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
public class ReferencesChecker {

	public static long getLiferayBuildNumber(Connection connection)
		throws SQLException {

		PreparedStatement ps = null;
		ResultSet rs = null;
		long buildNumber = 0;

		try {
			String sql =
				"select buildNumber from Release_ where servletContextName = " +
					"'portal'";

			sql = SQLUtil.transformSQL(sql);

			if (_log.isDebugEnabled()) {
				_log.debug("SQL: " + sql);
			}

			ps = connection.prepareStatement(sql);

			rs = ps.executeQuery();

			while (rs.next()) {
				buildNumber = rs.getLong(1);
			}
		}
		finally {
			DataAccess.cleanUp(ps);
			DataAccess.cleanUp(rs);
		}

		return buildNumber;
	}

	public ReferencesChecker(
			String dbType, List<String> excludeColumns,
			boolean ignoreNullValues, boolean checkUndefinedTables)
		throws IOException, SQLException {

		this.checkUndefinedTables = checkUndefinedTables;
		this.dbType = dbType;
		this.excludeColumns = excludeColumns;
		this.ignoreNullValues = ignoreNullValues;

		Connection connection = null;

		try {
			connection = DataAccess.getConnection();

			this.configuration = getConfiguration(connection);
			this.tableUtil = getTableUtil(connection, configuration);
		}
		catch (IOException e) {
			_log.error(
				"Error reading configuration_xx.yml file: " + e.getMessage(),
				e);
			throw new RuntimeException(e);
		}
		finally {
			DataAccess.cleanUp(connection);
		}
	}

	public Collection<Reference> calculateReferences(
		boolean ignoreEmptyTables) {

		ReferenceUtil referenceUtil = new ReferenceUtil(
			tableUtil, ignoreEmptyTables, checkUndefinedTables);

		return referenceUtil.calculateReferences(configuration);
	}

	public Map<String, Long> calculateTableCount()
		throws IOException, SQLException {

		Map<String, Long> mapTableCount = new TreeMap<String, Long>();

		for (Table table : tableUtil.getTables()) {
			long count = TableUtil.countTable(table);

			mapTableCount.put(table.getTableName(), count);
		}

		return mapTableCount;
	}

	public List<String> dumpDatabaseInfo() throws IOException, SQLException {
		List<String> output = new ArrayList<String>();

		long liferayBuildNumber;
		Connection connection = null;

		try {
			connection = DataAccess.getConnection();

			liferayBuildNumber = getLiferayBuildNumber(connection);
		}
		finally {
			DataAccess.cleanUp(connection);
		}

		output.add("Liferay build number: " + liferayBuildNumber);
		output.add("Database type: " + SQLUtil.getDBType());
		output.add("");

		List<String> classNamesWithoutTable = new ArrayList<String>();

		for (String className : tableUtil.getClassNames()) {
			String tableName = tableUtil.getTableNameFromClassName(className);

			if (tableName == null) {
				classNamesWithoutTable.add(className);
			}
		}

		if (!classNamesWithoutTable.isEmpty()) {
			output.add("ClassName without table information:");

			Collections.sort(classNamesWithoutTable);

			for (String className : classNamesWithoutTable) {
				output.add(
					className + "=" + tableUtil.getClassNameId(className));
			}

			output.add("");
		}

		List<Table> tablesWithoutClassName = new ArrayList<Table>();
		List<Table> tablesWithClassName = new ArrayList<Table>();

		for (Table table : tableUtil.getTables()) {
			if (table.getClassNameId() == -1) {
				tablesWithoutClassName.add(table);
			}
			else if (table.getClassNameId() != 0) {
				tablesWithClassName.add(table);
			}
		}

		if (!tablesWithoutClassName.isEmpty()) {
			output.add("Tables without className information:");

			for (Table table : tablesWithoutClassName) {
				output.add(table.getTableName());
			}

			output.add("");
		}

		if (!tablesWithClassName.isEmpty()) {
			output.add("Table-className mapping information:");

			for (Table t : tablesWithClassName) {
				output.add(t.getTableName() + "=" + t.getClassName());
			}

			output.add("");
			output.add("Table-className mapping information:");

			for (Table t : tablesWithClassName) {
				output.add(t.getTableName() + "=" + t.getClassNameId());
			}

			output.add("");
		}

		return output;
	}

	public List<MissingReferences> execute() {

		Collection<Reference> references = calculateReferences(true);

		List<MissingReferences> listMissingReferences =
			new ArrayList<MissingReferences>();

		for (Reference reference : references) {
			try {
				if (_log.isInfoEnabled()) {
					_log.info("Processing: " + reference);
				}

				if (reference.isRaw()) {
					continue;
				}

				Query originQuery = reference.getOriginQuery();
				Query destinationQuery = reference.getDestinationQuery();

				if (destinationQuery == null) {
					continue;
				}

				Collection<Object[]> missingReferences = queryInvalidValues(
					originQuery, destinationQuery);

				if ((missingReferences == null) || missingReferences.size()>0) {
					listMissingReferences.add(
						new MissingReferences(reference, missingReferences));
				}
			}
			catch (Throwable t) {
				_log.error(
					"EXCEPTION: " + t.getClass() + " - " + t.getMessage(), t);

				listMissingReferences.add(new MissingReferences(reference, t));
			}
		}

		return listMissingReferences;
	}

	public Collection<Object[]> queryInvalidValues(
			Query originQuery, Query destinationQuery)
		throws SQLException {

		Set<Object[]> invalidValuesSet = new LinkedHashSet<Object[]>();

		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			con = DataAccess.getConnection();

			String sql = getSQL(originQuery, destinationQuery);

			if (_log.isInfoEnabled()) {
				_log.info("SQL: " + sql);
			}

			ps = con.prepareStatement(sql);

			rs = ps.executeQuery();

			ResultSetMetaData rsmd = rs.getMetaData();

			int columnsNumber = rsmd.getColumnCount();

			while (rs.next()) {
				Object[] result = new Object[columnsNumber];

				for (int i = 0; i<columnsNumber; i++) {
					result[i] = rs.getObject(i+1);
				}

				if (isValidValue(result)) {
					continue;
				}

				invalidValuesSet.add(result);
			}
		}
		finally {
			DataAccess.cleanUp(con, ps, rs);
		}

		return invalidValuesSet;
	}

	protected Configuration getConfiguration(Connection connection)
		throws IOException, SQLException {

		long liferayBuildNumber = getLiferayBuildNumber(connection);

		if (liferayBuildNumber == 0) {
			_log.error("Liferay build number couldn't be retrieved");

			return null;
		}

		if (_log.isInfoEnabled()) {
			_log.info("Liferay build number: " + liferayBuildNumber);
		}

		String configurationFile = ConfigurationUtil.getConfigurationFileName(
			liferayBuildNumber);

		Configuration configuration = ConfigurationUtil.readConfigurationFile(
			configurationFile);

		if (excludeColumns != null) {
			configuration.getIgnoreColumns().addAll(excludeColumns);
		}

		return configuration;
	}

	protected String getSQL(Query originQuery, Query destinationQuery) {
		return getSQL(originQuery, destinationQuery, false);
	}

	protected String getSQL(
		Query originQuery, Query destinationQuery, boolean count) {

		if (dbType.equals(SQLUtil.TYPE_POSTGRESQL) ||
			dbType.equals(SQLUtil.TYPE_SQLSERVER)) {

			return getSQLNotExists(count, originQuery, destinationQuery);
		}

		return getSQLNotIn(count, originQuery, destinationQuery);
	}

	protected String getSQLNotExists(
		boolean count, Query originQuery, Query destinationQuery) {

		List<String> conditionColumns = originQuery.getColumnsWithCast(
			destinationQuery);

		List<String> destinationColumns = destinationQuery.getColumnsWithCast(
			originQuery);

		StringBundler sb = new StringBundler();

		if (count) {
			sb.append(originQuery.getSQLCount());
		}
		else {
			sb.append(originQuery.getSQL());
		}

		sb.append(" AND NOT EXISTS (");
		sb.append(
			destinationQuery.getSQL(
				false, StringUtil.merge(destinationColumns)));

		for (int i = 0; i<conditionColumns.size(); i++) {
			sb.append(" AND ");
			sb.append(conditionColumns.get(i));
			sb.append("=");
			sb.append(destinationColumns.get(i));
		}

		sb.append(")");

		return SQLUtil.transformSQL(sb.toString());
	}

	protected String getSQLNotIn(
		boolean count, Query originQuery, Query destinationQuery) {

		List<String> conditionColumns = originQuery.getColumnsWithCast(
			destinationQuery);

		List<String> destinationColumns = destinationQuery.getColumnsWithCast(
			originQuery);

		StringBundler sb = new StringBundler();

		if (count) {
			sb.append(originQuery.getSQLCount());
		}
		else {
			sb.append(originQuery.getSQL());
		}

		sb.append(" AND (");
		sb.append(StringUtil.merge(conditionColumns));
		sb.append(") NOT IN (");
		sb.append(
			destinationQuery.getSQL(
				false, StringUtil.merge(destinationColumns)));
		sb.append(")");

		return SQLUtil.transformSQL(sb.toString());
	}

	protected TableUtil getTableUtil(
			Connection con, Configuration configuration)
		throws SQLException {

		String dbType = SQLUtil.getDBType();

		DatabaseMetaData metadata = con.getMetaData();

		String catalog = con.getCatalog();
		String schema = null;

		if ((catalog == null) && dbType.equals(SQLUtil.TYPE_ORACLE)) {
			catalog = metadata.getUserName();
			schema = catalog;
		}

		Map<String, String> tableToClassNameMapping =
			configuration.getTableToClassNameMapping();

		return new TableUtil(
			metadata, catalog, schema, tableToClassNameMapping,
			configuration.getIgnoreTables(), configuration.getIgnoreColumns());
	}

	protected boolean isValidValue(Object[] result) {
		if (!ignoreNullValues) {
			return false;
		}

		for (Object o : result) {
			if (o == null) {
				continue;
			}

			if (o instanceof Number) {
				Number n = (Number)o;

				if (n.longValue() == 0) {
					continue;
				}
			}

			if (Validator.isNotNull(o) && !"0".equals(o.toString())) {
				return false;
			}
		}

		return true;
	}

	private static Log _log = LogFactoryUtil.getLog(ReferencesChecker.class);

	private boolean checkUndefinedTables;
	private Configuration configuration;
	private String dbType;
	private List<String> excludeColumns;
	private boolean ignoreNullValues;
	private TableUtil tableUtil;

}