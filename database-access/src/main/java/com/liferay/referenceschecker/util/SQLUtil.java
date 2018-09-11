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

package com.liferay.referenceschecker.util;

import java.math.BigDecimal;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * @author Jorge Díaz
 */
public class SQLUtil {

	public static final String TYPE_DB2 = "db2";

	public static final String TYPE_HYPERSONIC = "hypersonic";

	public static final String TYPE_MARIADB = "mariadb";

	public static final String TYPE_MYSQL = "mysql";

	public static final String TYPE_ORACLE = "oracle";

	public static final String TYPE_POSTGRESQL = "postgresql";

	public static final String TYPE_SQLSERVER = "sqlserver";

	public static final String TYPE_SYBASE = "sybase";

	public static final String TYPE_UNKNOWN = "unknown";

	public static List<String> castColumnsToText(
		String dbType, String prefix, List<String> columns,
		List<Class<?>> columnTypes, List<Class<?>> castTypes) {

		List<String> castedColumns = new ArrayList<>();

		for (int i = 0; i < columns.size(); i++) {
			String column = columns.get(i);

			Class<?> columnType = columnTypes.get(i);
			Class<?> castType = castTypes.get(i);

			if (StringUtils.isNotBlank(prefix)) {
				column = prefix + "." + column;
			}

			if (!columnType.equals(castType) && String.class.equals(castType) &&
				!Object.class.equals(columnType)) {

				column = castColumnToText(dbType, column);
			}

			castedColumns.add(column);
		}

		return castedColumns;
	}

	public static String castColumnToText(String dbType, String column) {
		if (dbType.equals(TYPE_DB2)) {
			return "CAST(" + column + " AS VARCHAR(254))";
		}

		if (dbType.equals(TYPE_HYPERSONIC)) {
			return "CONVERT(" + column + ", SQL_VARCHAR)";
		}

		if (dbType.equals(TYPE_MARIADB) || dbType.equals(TYPE_MYSQL)) {
			return column;
		}

		if (dbType.equals(TYPE_ORACLE)) {
			return "CAST(" + column + " AS VARCHAR(4000))";
		}

		if (dbType.equals(TYPE_POSTGRESQL)) {
			return "CAST(" + column + " AS TEXT)";
		}

		if (dbType.equals(TYPE_SQLSERVER)) {
			return "CAST(" + column + " AS NVARCHAR(MAX))";
		}

		if (dbType.equals(TYPE_SYBASE)) {
			return "CAST(" + column + " AS NVARCHAR(5461))";
		}

		return column;
	}

	public static String getDBType(Connection connection) throws SQLException {
		DatabaseMetaData databaseMetaData = connection.getMetaData();

		String dbName = databaseMetaData.getDatabaseProductName();

		if (StringUtils.startsWithIgnoreCase(dbName, "DB2")) {
			return TYPE_DB2;
		}

		if (StringUtils.startsWithIgnoreCase(dbName, "HSQL")) {
			return TYPE_HYPERSONIC;
		}

		if (StringUtils.startsWithIgnoreCase(dbName, "MariaDB")) {
			return TYPE_MARIADB;
		}

		if (StringUtils.equalsIgnoreCase(dbName, "MySQL")) {
			return TYPE_MYSQL;
		}

		if (StringUtils.equalsIgnoreCase(dbName, "Oracle")) {
			return TYPE_ORACLE;
		}

		if (StringUtils.equalsIgnoreCase(dbName, "PostgreSQL") ||
			StringUtils.equalsIgnoreCase(dbName, "EnterpriseDB")) {

			return TYPE_POSTGRESQL;
		}

		if (dbName.startsWith("Microsoft")) {
			return TYPE_SQLSERVER;
		}

		if (StringUtils.startsWithIgnoreCase(dbName, "Sybase") ||
			StringUtils.startsWithIgnoreCase(dbName, "Adaptive Server")) {

			return TYPE_SYBASE;
		}

		return TYPE_UNKNOWN;
	}

	public static Class<?> getJdbcTypeClass(int type) {
		Class<?> result = Object.class;

		switch (type) {
			case Types.CHAR:
			case Types.VARCHAR:
			case Types.LONGVARCHAR:
			case Types.CLOB:
				result = String.class;

				break;

			case Types.NUMERIC:
			case Types.DECIMAL:
				result = BigDecimal.class;

				break;

			case Types.BIT:
			case Types.BOOLEAN:
				result = Boolean.class;

				break;

			case Types.TINYINT:
				result = Byte.class;

				break;

			case Types.SMALLINT:
				result = Short.class;

				break;

			case Types.INTEGER:
				result = Integer.class;

				break;

			case Types.BIGINT:
				result = Long.class;

				break;

			case Types.REAL:
			case Types.FLOAT:
				result = Float.class;

				break;

			case Types.DOUBLE:
				result = Double.class;

				break;

			case Types.BINARY:
			case Types.VARBINARY:
			case Types.LONGVARBINARY:
				result = Byte[].class;

				break;

			case Types.DATE:
				result = Date.class;

				break;

			case Types.TIME:
				result = Time.class;

				break;

			case Types.TIMESTAMP:
				result = Timestamp.class;

				break;
		}

		return result;
	}

}