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

package com.liferay.referenceschecker.util;

import com.liferay.portal.kernel.dao.db.DB;
import com.liferay.portal.kernel.dao.jdbc.DataAccess;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.PortalClassLoaderUtil;

import java.io.IOException;
import java.io.PrintStream;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;

import java.util.HashMap;
import java.util.Map;
public class SQLUtil {

	public static final String TYPE_DB2 = "db2";

	public static final String TYPE_HYPERSONIC = "hypersonic";

	public static final String TYPE_MARIADB = "mariadb";

	public static final String TYPE_MYSQL = "mysql";

	public static final String TYPE_ORACLE = "oracle";

	public static final String TYPE_POSTGRESQL = "postgresql";

	public static final String TYPE_SQLSERVER = "sqlserver";

	public static final String TYPE_SYBASE = "sybase";

	public static void executeSQL(PrintStream out, String sql, String sep)
		throws IOException {

		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			con = DataAccess.getConnection();

			sql = transformSQL(sql);

			out.println("");
			out.println("SQL: "+sql);
			out.println("");

			ps = con.prepareStatement(sql);

			rs = ps.executeQuery();

			ResultSetMetaData rsmd = rs.getMetaData();

			int numberOfColumns = rsmd.getColumnCount();

			int rows = 0;

			for (int i = 1; i <= numberOfColumns; i++) {
				if (i > 1)out.print(sep);
				String columnName = rsmd.getColumnName(i);
				out.print(columnName);
			}

			out.println("");

			while (rs.next()) {
				for (int i = 1; i <= numberOfColumns; i++) {
					if (i > 1)out.print(sep);
					String columnValue = rs.getString(i);
					out.print(columnValue);
					//out.print(HtmlUtil.escape(columnValue));
				}

				out.println("");
				rows++;
			}

			out.println("("+rows+" rows returned)");
			out.println("");
		}
		catch (Exception e) {
			e.printStackTrace(out);
		}
		finally {
			DataAccess.cleanUp(con, ps, rs);
		}
	}

	public static DB getDB() {
		ClassLoader classLoader = PortalClassLoaderUtil.getClassLoader();

		Class<?> dbManagerUtil = null;

		try {
			/* 7.x */
			dbManagerUtil = classLoader.loadClass(
				"com.liferay.portal.kernel.dao.db.DBManagerUtil");
		}
		catch (Throwable t) {
			if (_log.isDebugEnabled()) {
				_log.debug(t);
			}
		}

		if (dbManagerUtil == null) {
			try {
				/* 6.x */
				dbManagerUtil = classLoader.loadClass(
					"com.liferay.portal.kernel.dao.db.DBFactoryUtil");
			}
			catch (Throwable t) {
				if (_log.isDebugEnabled()) {
					_log.debug(t);
				}
			}
		}

		if (dbManagerUtil == null) {
			return null;
		}

		try {
			Method getDB = dbManagerUtil.getMethod("getDB");

			return (DB)getDB.invoke(null);
		}
		catch (Throwable t) {
			if (_log.isDebugEnabled()) {
				_log.debug(t);
			}
		}

		return null;
	}

	public static String getDBType() {
		DB db = getDB();

		try {
			/* 7.x */
			Method getType = db.getClass().getMethod("getDBType");

			return String.valueOf(getType.invoke(db));
		}
		catch (Throwable t) {
			if (_log.isDebugEnabled()) {
				_log.debug(t);
			}
		}

		try {
			/* 6.x */
			Method getType = db.getClass().getMethod("getType");

			return String.valueOf(getType.invoke(db));
		}
		catch (Throwable t) {
			if (_log.isDebugEnabled()) {
				_log.debug(t);
			}
		}

		return null;
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
				result = java.math.BigDecimal.class;
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
				result = java.sql.Date.class;
				break;

			case Types.TIME:
				result = java.sql.Time.class;
				break;

			case Types.TIMESTAMP:
				result = java.sql.Timestamp.class;
				break;
		}

		return result;
	}

	public static Map<Integer, String> getJdbcTypeNames() {

		if (jdbcTypeNames == null) {
			Map<Integer, String> aux = new HashMap<Integer, String>();

			for (Field field : Types.class.getFields()) {
				try {
					aux.put((Integer)field.get(null), field.getName());
				}
				catch (IllegalArgumentException e) {
				}
				catch (IllegalAccessException e) {
				}
			}

			jdbcTypeNames = aux;
		}

		return jdbcTypeNames;
	}

	/* PortalUtil.transformSQL doesn't exists in 6.1.20 */

	public static String transformSQL(String sql) {
		try {
			if (transformSQLMethod == null) {
				Class<?> sqlTransformer =
					ReflectionUtil.getPortalClass(
						"com.liferay.portal.dao.orm.common.SQLTransformer");

				Class<?>[] parameterTypes = {String.class};

				transformSQLMethod = sqlTransformer.getMethod(
					"transform", parameterTypes);
			}

			return (String)transformSQLMethod.invoke(null, sql);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static Log _log = LogFactoryUtil.getLog(SQLUtil.class);

	private static Map<Integer, String> jdbcTypeNames = null;
	private static Method transformSQLMethod = null;

}