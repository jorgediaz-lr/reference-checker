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

package com.liferay.referenceschecker.model;

import com.liferay.referenceschecker.util.JDBCUtil;
import com.liferay.referenceschecker.util.SQLUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;

/**
 * @author Jorge Díaz
 */
public class ModelUtilImpl implements ModelUtil {

	@Override
	public String getClassName(String tableName) {
		if (tableName == null) {
			return null;
		}

		return tableNameToClassNameMapping.get(
			StringUtils.lowerCase(tableName));
	}

	public Long getClassNameId(String className) {
		if (className == null) {
			return null;
		}

		if (StringUtils.isBlank(className)) {
			return 0L;
		}

		return classNameToClassNameIdMapping.get(className);
	}

	public Set<String> getClassNames() {
		return Collections.unmodifiableSet(
			classNameToClassNameIdMapping.keySet());
	}

	@Override
	public String getTableName(String className) {
		if (className == null) {
			return null;
		}

		return classNameToTableNameMapping.get(className);
	}

	@Override
	public long getTableRank(String tableName) {
		Number rank = tableRank.get(StringUtils.lowerCase(tableName));

		if (rank == null) {
			return 0L;
		}

		return rank.longValue();
	}

	public void init(
			Connection connection,
			Map<String, String> tableNameToClassNameMapping,
			Map<String, Number> tableRank)
		throws SQLException {

		Map<String, String> tableNameToClassNameMappingAux =
			new ConcurrentHashMap<>();

		for (Entry<String, String> entry :
				tableNameToClassNameMapping.entrySet()) {

			String key = StringUtils.lowerCase(entry.getKey());
			String value = entry.getValue();

			tableNameToClassNameMappingAux.put(key, value);
		}

		Map<String, String> classNameToTableNameMappingAux =
			new ConcurrentHashMap<>();

		for (Map.Entry<String, String> entry :
				tableNameToClassNameMapping.entrySet()) {

			if (StringUtils.isBlank(entry.getValue())) {
				continue;
			}

			classNameToTableNameMappingAux.put(
				entry.getValue(), entry.getKey());
		}

		this.tableNameToClassNameMapping = tableNameToClassNameMappingAux;

		classNameToClassNameIdMapping = getClassNameIdsMapping(connection);

		classNameToTableNameMapping = classNameToTableNameMappingAux;

		this.tableRank = tableRank;
	}

	protected Map<String, Long> getClassNameIdsMapping(Connection connection)
		throws SQLException {

		Map<String, Long> mapping = new ConcurrentHashMap<>();

		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			ps = connection.prepareStatement(
				"select classNameId, value from ClassName_");

			ps.setQueryTimeout(SQLUtil.QUERY_TIMEOUT);

			rs = ps.executeQuery();

			while (rs.next()) {
				long classNameId = rs.getLong("classNameId");
				String value = rs.getString("value");

				mapping.put(value, classNameId);
			}
		}
		finally {
			JDBCUtil.cleanUp(ps, rs);
		}

		return mapping;
	}

	protected Map<String, Long> classNameToClassNameIdMapping;
	protected Map<String, String> classNameToTableNameMapping;
	protected Map<String, String> tableNameToClassNameMapping;
	protected Map<String, Number> tableRank;

}