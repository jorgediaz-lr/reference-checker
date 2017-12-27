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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
		String dbType, List<String> excludeColumns, boolean ignoreEmptyTables,
		boolean ignoreNullValues) {

		this.dbType = dbType;
		this.excludeColumns = excludeColumns;
		this.ignoreEmptyTables = ignoreEmptyTables;
		this.ignoreNullValues = ignoreNullValues;
	}

	public Map<Reference, Reference> calculateReferences()
		throws IOException, SQLException {

		Connection connection = null;

		Map<Reference, Reference> references = null;

		try {
			connection = DataAccess.getConnection();

			long liferayBuildNumber = getLiferayBuildNumber(connection);

			String configurationFile =
				ConfigurationUtil.getConfigurationFileName(liferayBuildNumber);

			Configuration configuration =
				ConfigurationUtil.readConfigurationFile(configurationFile);

			if (excludeColumns != null) {
				configuration.getIgnoreColumns().addAll(excludeColumns);
			}

			String dbType = SQLUtil.getDBType();

			TableUtil tableUtil = getTableUtil(
				connection, dbType, configuration.getTableToClassNameMapping(),
				configuration.getIgnoreTables(),
				configuration.getIgnoreColumns());

			references = calculateReferences(configuration, tableUtil);
		}
		finally {
			DataAccess.cleanUp(connection);
		}

		return references;
	}

	public List<MissingReferences> execute() throws IOException, SQLException {
		Connection connection = null;

		List<MissingReferences> listMissingReferences = null;

		try {
			connection = DataAccess.getConnection();

			long liferayBuildNumber = getLiferayBuildNumber(connection);

			String configurationFile =
				ConfigurationUtil.getConfigurationFileName(liferayBuildNumber);

			Configuration configuration =
				ConfigurationUtil.readConfigurationFile(configurationFile);

			if (excludeColumns != null) {
				configuration.getIgnoreColumns().addAll(excludeColumns);
			}

			String dbType = SQLUtil.getDBType();

			TableUtil tableUtil = getTableUtil(
				connection, dbType, configuration.getTableToClassNameMapping(),
				configuration.getIgnoreTables(),
				configuration.getIgnoreColumns());

			listMissingReferences = execute(
				configuration, tableUtil, tableUtil.getTables());
		}
		finally {
			DataAccess.cleanUp(connection);
		}

		return listMissingReferences;
	}

	public Collection<String> queryInvalidValues(
			Query originQuery, Query destinationQuery)
		throws SQLException {

		Set<String> invalidValuesSet = new TreeSet<String>();

		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			con = DataAccess.getConnection();

			Table originTable = originQuery.getTable();
			Table destinationTable = destinationQuery.getTable();

			List<String> originColumns = originQuery.getColumns();
			List<String> destinationColumns = destinationQuery.getColumns();

			List<Class<?>> originTypes = originTable.getColumnTypesClass(
				originColumns);
			List<Class<?>> destinationTypes =
				destinationTable.getColumnTypesClass(destinationColumns);

			List<String> conditionColumns = castColumnsToText(
				originColumns, originTypes, destinationTypes);

			destinationColumns =
				castColumnsToText(
					destinationColumns, destinationTypes, originTypes);

			StringBundler sb = new StringBundler();

			sb.append(originQuery.getSQL());
			sb.append(" AND ");

			if ((conditionColumns.size() > 1) &&
				dbType.equals(SQLUtil.TYPE_SQLSERVER)) {

				/* SQL Server */
				sb.append("NOT EXISTS (");
				sb.append(
					destinationQuery.getSQL(
						false, StringUtil.merge(destinationColumns)));

				for (int i = 0; i<conditionColumns.size(); i++) {
					sb.append(" AND ");
					sb.append(originQuery.getTable().getTableName());
					sb.append(".");
					sb.append(conditionColumns.get(i));
					sb.append("=");
					sb.append(destinationQuery.getTable().getTableName());
					sb.append(".");
					sb.append(destinationColumns.get(i));
				}

				sb.append(")");
			}
			else {
				sb.append("(");
				sb.append(StringUtil.merge(conditionColumns));
				sb.append(") NOT IN (");
				sb.append(
					destinationQuery.getSQL(
						false, StringUtil.merge(destinationColumns)));
				sb.append(")");
			}

			String sql = SQLUtil.transformSQL(sb.toString());

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

				if (columnsNumber == 1) {
					invalidValuesSet.add(String.valueOf(result[0]));
				}
				else {
					invalidValuesSet.add(Arrays.toString(result));
				}
			}
		}
		finally {
			DataAccess.cleanUp(con, ps, rs);
		}

		return invalidValuesSet;
	}

	protected Map<Reference, Reference> calculateReferences(
		Configuration configuration, TableUtil tableUtil) {

		Map<Reference, Reference> referencesMap =
			new TreeMap<Reference, Reference>();

		List<Reference> references;
		try {
			references = ReferenceUtil.getConfigurationReferences(
				tableUtil, configuration, ignoreEmptyTables);
		}
		catch (IOException e) {
			_log.error(
				"Error reading references.txt configuration: " + e.getMessage(),
				e);
			throw new RuntimeException(e);
		}

		for (Reference reference : references) {
			referencesMap.put(reference, reference);
		}

		if (_log.isDebugEnabled()) {
			Set<String> idColumns = getNotCheckedColumns(
				tableUtil, referencesMap.values());

			_log.debug("List of id, key and pk columns that are not checked:");

			for (String idColumn : idColumns) {
				_log.debug(idColumn);
			}
		}

		return referencesMap;
	}

	protected List<MissingReferences> execute(
		Configuration configuration, TableUtil tableUtil,
		List<Table> tableList) {

		Map<Reference, Reference> references = calculateReferences(
			configuration, tableUtil);

		List<MissingReferences> listMissingReferences =
			new ArrayList<MissingReferences>();

		for (Reference reference : references.values()) {
			try {
				if (_log.isInfoEnabled()) {
					_log.info("Processing: "+ reference);
				}

				Query originQuery = reference.getOriginQuery();
				Query destinationQuery = reference.getDestinationQuery();

				if (destinationQuery == null) {
					continue;
				}

				Collection<String> missingReferences = queryInvalidValues(
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

	protected Set<String> getNotCheckedColumns(
		TableUtil tableUtil, Collection<Reference> references) {

		Set<String> idColumns = new TreeSet<String>();

		for (Table table : tableUtil.getTables()) {
			String tableName = StringUtil.toLowerCase(table.getTableName());
			String primaryKey = table.getPrimaryKey();

			if (primaryKey != null) {
				primaryKey = StringUtil.toLowerCase(primaryKey);
			}

			for (String columnName : table.getColumnNames()) {
				columnName = StringUtil.toLowerCase(columnName);

				if (tableUtil.ignoreColumn(tableName, columnName) ||
					"uuid_".equals(columnName) ||
					columnName.equals(primaryKey)) {

					continue;
				}

				if (columnName.contains("id") || columnName.contains("pk") ||
					columnName.contains("key")) {

					idColumns.add(tableName+"."+columnName);
				}
			}
		}

		for (Reference reference : references) {
			if (reference.getDestinationQuery() == null) {
				continue;
			}

			Query query = reference.getOriginQuery();
			String tableName = query.getTable().getTableName();

			for (String columnName : query.getColumns()) {
				String idColumn = tableName + "." + columnName;
				idColumn = StringUtil.toLowerCase(idColumn);
				idColumns.remove(idColumn);
			}
		}

		return idColumns;
	}

	protected TableUtil getTableUtil(
			Connection con, String dbType,
			Map<String, String> tableToClassNameMapping,
			List<String> ignoreTables, List<String> ignoreColumns)
		throws SQLException {

		DatabaseMetaData metadata = con.getMetaData();

		String catalog = con.getCatalog();
		String schema = null;

		if ((catalog == null) && dbType.equals(SQLUtil.TYPE_ORACLE)) {
			catalog = metadata.getUserName();
			schema = catalog;
		}

		return new TableUtil(
			metadata, catalog, schema, tableToClassNameMapping, ignoreTables,
			ignoreColumns);
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

	private List<String> castColumnsToText(
		List<String> columns, List<Class<?>> columnTypes,
		List<Class<?>> castTypes) {

		List<String> castColumns = new ArrayList<String>();

		for (int i = 0; i<columns.size(); i++) {
			String column = columns.get(i);

			Class<?> columnType = columnTypes.get(i);
			Class<?> castType = castTypes.get(i);

			if (!columnType.equals(castType) && String.class.equals(castType) &&
				!Object.class.equals(columnType)) {

				column = "CAST_TEXT(" + column + ")";
			}

			castColumns.add(column);
		}

		return castColumns;
	}

	private static Log _log = LogFactoryUtil.getLog(ReferencesChecker.class);

	private String dbType;
	private List<String> excludeColumns;
	private boolean ignoreEmptyTables;
	private boolean ignoreNullValues;

}