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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

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

	public static List<String> addPrefixToSqlList(
		List<String> sqlList, String prefix, List<String> columnNames) {

		if (StringUtils.isBlank(prefix)) {
			return sqlList;
		}

		List<String> newSqlList = new ArrayList<>();

		for (int i = 0; i < sqlList.size(); i++) {
			String column = columnNames.get(i);

			String sql = sqlList.get(i);

			sql = sql.replace(column, prefix + "." + column);

			newSqlList.add(sql);
		}

		return newSqlList;
	}

	public static List<String> castColumnsToText(
		List<String> columns, List<Class<?>> columnTypes,
		List<Class<?>> castTypes) {

		List<String> castedColumns = new ArrayList<>();

		for (int i = 0; i < columns.size(); i++) {
			String column = columns.get(i);

			Class<?> columnType = columnTypes.get(i);
			Class<?> castType = castTypes.get(i);

			if (!columnType.equals(castType) && String.class.equals(castType) &&
				!Object.class.equals(columnType)) {

				column = "CAST_TEXT(" + column + ")";
			}

			castedColumns.add(column);
		}

		return castedColumns;
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

	public static List<String> transform(String dbType, List<String> sqlList) {
		if (sqlList == null) {
			return null;
		}

		List<String> transformedSqlList = new ArrayList<>();

		for (String sql : sqlList) {
			String transformedSql = transform(dbType, sql);

			transformedSqlList.add(transformedSql);
		}

		return transformedSqlList;
	}

	public static String transform(String dbType, String sql) {
		if (sql == null) {
			return sql;
		}

		String newSQL = sql;

		newSQL = _replaceCastText(dbType, newSQL);
		newSQL = _replaceInstr(dbType, newSQL);
		newSQL = _replaceSubstr(dbType, newSQL);

		if (_log.isDebugEnabled()) {
			_log.debug("Original SQL " + sql);
			_log.debug("Modified SQL " + newSQL);
		}

		return newSQL;
	}

	private static String _replaceCastText(String dbType, String sql) {
		Matcher matcher = _castTextPattern.matcher(sql);

		if (dbType.equals(TYPE_DB2)) {
			return matcher.replaceAll("CAST($1 AS VARCHAR(254))");
		}

		if (dbType.equals(TYPE_HYPERSONIC)) {
			return matcher.replaceAll("CONVERT($1, SQL_VARCHAR)");
		}

		if (dbType.equals(TYPE_MARIADB) || dbType.equals(TYPE_MYSQL)) {
			return matcher.replaceAll("$1");
		}

		if (dbType.equals(TYPE_ORACLE)) {
			return matcher.replaceAll("CAST($1 AS VARCHAR(4000))");
		}

		if (dbType.equals(TYPE_POSTGRESQL)) {
			return matcher.replaceAll("CAST($1 AS TEXT)");
		}

		if (dbType.equals(TYPE_SQLSERVER)) {
			return matcher.replaceAll("CAST($1 AS NVARCHAR(MAX))");
		}

		if (dbType.equals(TYPE_SYBASE)) {
			return matcher.replaceAll("CAST($1 AS NVARCHAR(5461))");
		}

		return matcher.replaceAll("$1");
	}

	private static String _replaceInstr(String dbType, String sql) {
		Matcher matcher = _instrPattern.matcher(sql);

		if (dbType.equals(TYPE_DB2)) {
			return matcher.replaceAll("LOCATE($2, $1)");
		}

		if (dbType.equals(TYPE_HYPERSONIC)) {
			return matcher.replaceAll("INSTR($1, $2)");
		}

		if (dbType.equals(TYPE_MARIADB) || dbType.equals(TYPE_MYSQL)) {
			return matcher.replaceAll("INSTR($1, $2)");
		}

		if (dbType.equals(TYPE_ORACLE)) {
			return matcher.replaceAll("INSTR($1, $2)");
		}

		if (dbType.equals(TYPE_POSTGRESQL)) {
			return matcher.replaceAll("STRPOS($1, $2)");
		}

		if (dbType.equals(TYPE_SQLSERVER)) {
			return matcher.replaceAll("CHARINDEX($2, $1)");
		}

		if (dbType.equals(TYPE_SYBASE)) {
			return matcher.replaceAll("CHARINDEX($2, $1)");
		}

		return matcher.replaceAll("SUBSTRING_REGEX ($2 IN $1)");
	}

	private static String _replaceSubstr(String dbType, String sql) {
		Matcher matcher = _substrPattern.matcher(sql);

		if (dbType.equals(TYPE_DB2) || dbType.equals(TYPE_HYPERSONIC) ||
			dbType.equals(TYPE_MARIADB) || dbType.equals(TYPE_MYSQL) ||
			dbType.equals(TYPE_ORACLE) || dbType.equals(TYPE_POSTGRESQL) ||
			dbType.equals(TYPE_SYBASE)) {

			return matcher.replaceAll("SUBSTR($1, $2, $3)");
		}

		if (dbType.equals(TYPE_SQLSERVER)) {
			return matcher.replaceAll("SUBSTRING($1, $2, $3)");
		}

		return matcher.replaceAll("SUBSTRING($1 FROM $2 FOR $3)");
	}

	private static Logger _log = LogManager.getLogger(SQLUtil.class);

	private static Pattern _castTextPattern = Pattern.compile(
		"CAST_TEXT\\((.+?)\\)", Pattern.CASE_INSENSITIVE);
	private static Pattern _instrPattern = Pattern.compile(
		"INSTR\\(\\s*(.+?)\\s*,\\s*(.+?)\\s*\\)", Pattern.CASE_INSENSITIVE);
	private static Pattern _substrPattern = Pattern.compile(
		"SUBSTR\\(\\s*(.+?)\\s*,\\s*(.+?)\\s*,\\s*(.+?)\\s*\\)",
		Pattern.CASE_INSENSITIVE);

}