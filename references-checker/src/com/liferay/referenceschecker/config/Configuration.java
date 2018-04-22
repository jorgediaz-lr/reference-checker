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

		public String getTable() {
			return table;
		}

		public void setTable(String table) {
			this.table = table;
		}

		public String toString() {
			return "table=" + table + ", columns=" + columns + ", condition=" +
				condition;
		}

		private List<String> columns;
		private List<String> conditionColumns;
		private String condition;
		private String table;

	}

	public static class Reference {

		public Boolean isDisplayRaw() {
			return getDisplayRaw();
		}

		public Boolean getDisplayRaw() {
			if (displayRaw == null) {
				return false;
			}

			return displayRaw;
		}

		public void setDisplayRaw(Boolean displayRaw) {
			this.displayRaw = displayRaw;
		}

		public Query getDest() {
			return dest;
		}

		public void setDest(Query dest) {
			this.dest = dest;
		}

		public Boolean isHidden() {
			return getHidden();
		}

		public Boolean getHidden() {
			if (hidden == null) {
				return false;
			}

			return hidden;
		}

		public void setHidden(Boolean hidden) {
			this.hidden = hidden;
		}

		public Query getOrigin() {
			return origin;
		}

		public void setOrigin(Query origin) {
			this.origin = origin;
		}

		public String toString() {
			return getOrigin() + " => " + String.valueOf(getDest());
		}

		private Boolean displayRaw;
		private Boolean hidden;
		private Query dest;
		private Query origin;

	}

	private List<String> ignoreColumns;
	private List<String> ignoreTables;
	private List<Reference> references;
	private Map<String, String> tableToClassNameMapping;

}