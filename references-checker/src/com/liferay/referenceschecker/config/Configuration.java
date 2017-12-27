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

package com.liferay.referenceschecker.config;

import com.liferay.referenceschecker.util.StringUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Jorge Díaz
 */
public class Configuration {

	public List<String> getIgnoreColumns() {
		return ignoreColumns;
	}

	public List<String> getIgnoreTables() {
		return ignoreTables;
	}

	public List<Reference> getReferences() {
		return references;
	}

	public Map<String, String> getTableToClassNameMapping() {
		return tableToClassNameMapping;
	}

	public void setIgnoreColumns(List<String> ignoreColumns) {
		this.ignoreColumns = ignoreColumns;
	}

	public void setIgnoreTables(List<String> ignoreTables) {
		this.ignoreTables = ignoreTables;
	}

	public void setReferences(List<Reference> references) {
		this.references = references;
	}

	public void setTableToClassNameMapping(
		Map<String, String> tableToClassNameMapping) {

		this.tableToClassNameMapping = new HashMap<String, String>();

		for (
			Map.Entry<String, String> entry :
				tableToClassNameMapping.entrySet()) {

			this.tableToClassNameMapping.put(
				StringUtil.toLowerCase(entry.getKey()), entry.getValue());
		}
	}

	public static class Query {

		private String table;
		private List<String> columns;
		private List<String> conditionColumns;
		private String condition;

		public String getTable() {
			return table;
		}

		public void setTable(String table) {
			this.table = table;
		}

		public List<String> getColumns() {
			return columns;
		}

		public void setColumns(List<String> columns) {
			this.columns = columns;
		}

		public String getCondition() {
			return condition;
		}

		public void setCondition(String condition) {
			this.condition = condition;
		}

		public List<String> getConditionColumns() {
			return conditionColumns;
		}

		public void setConditionColumns(List<String> conditionColumns) {
			this.conditionColumns = conditionColumns;
		}

		public String toString() {
			return "table=" + table + ", columns=" + columns + ", condition=" +
				condition;
		}

	}

	public static class Reference {

		private Query origin;
		public Query getOrigin() {
			return origin;
		}

		public void setOrigin(Query origin) {
			this.origin = origin;
		}

		public Query getDest() {
			return dest;
		}

		public void setDest(Query dest) {
			this.dest = dest;
		}

		public String toString() {
			return getOrigin() + " => " + String.valueOf(getDest());
		}

		private Query dest;

	}

	private List<String> ignoreColumns;
	private List<String> ignoreTables;
	private List<Reference> references;
	private Map<String, String> tableToClassNameMapping;

}