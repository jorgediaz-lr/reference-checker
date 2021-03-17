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

package com.liferay.referenceschecker.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

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

	public Listener getListener() {
		if (listener == null) {
			listener = new Listener();
		}

		return listener;
	}

	public List<Reference> getReferences() {
		return references;
	}

	public Map<String, Number> getTableRank() {
		return tableRank;
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

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public void setReferences(List<Reference> references) {
		this.references = references;
	}

	public void setTableRank(Map<String, Number> tableRank) {
		this.tableRank = new HashMap<>();

		for (Map.Entry<String, Number> entry : tableRank.entrySet()) {
			this.tableRank.put(
				StringUtils.lowerCase(entry.getKey()), entry.getValue());
		}
	}

	public void setTableToClassNameMapping(
		Map<String, String> tableToClassNameMapping) {

		this.tableToClassNameMapping = new HashMap<>();

		for (Map.Entry<String, String> entry :
				tableToClassNameMapping.entrySet()) {

			this.tableToClassNameMapping.put(
				StringUtils.lowerCase(entry.getKey()), entry.getValue());
		}
	}

	public static class Listener {

		public List<String> getIgnoredClasses() {
			if (ignoredClasses == null) {
				return Collections.emptyList();
			}

			return ignoredClasses;
		}

		public List<String> getIgnoredMethods() {
			if (ignoredMethods == null) {
				return Collections.emptyList();
			}

			return ignoredMethods;
		}

		public Boolean getPrintThreadDump() {
			if (printThreadDump == null) {
				return false;
			}

			return printThreadDump;
		}

		public Boolean isPrintThreadDump() {
			return getPrintThreadDump();
		}

		public void setIgnoredClasses(List<String> ignoredClasses) {
			this.ignoredClasses = ignoredClasses;
		}

		public void setIgnoredMethods(List<String> ignoredMethods) {
			this.ignoredMethods = ignoredMethods;
		}

		public void setPrintThreadDump(Boolean printThreadDump) {
			this.printThreadDump = printThreadDump;
		}

		protected List<String> ignoredClasses;
		protected List<String> ignoredMethods;
		protected Boolean printThreadDump;

	}

	public static class Query {

		public List<String> getCastings() {
			return castings;
		}

		public List<String> getColumns() {
			return columns;
		}

		public String getCondition() {
			return condition;
		}

		public List<String> getConditionColumns() {
			return conditionColumns;
		}

		public String getTable() {
			return table;
		}

		public void setCastings(List<String> castings) {
			this.castings = castings;
		}

		public void setColumns(List<String> columns) {
			this.columns = columns;
		}

		public void setCondition(String condition) {
			this.condition = condition;
		}

		public void setConditionColumns(List<String> conditionColumns) {
			this.conditionColumns = conditionColumns;
		}

		public void setTable(String table) {
			this.table = table;
		}

		public String toString() {
			return "table=" + table + ", castings=" + castings + ", columns=" +
				columns + ", condition=" + condition;
		}

		protected List<String> castings;
		protected List<String> columns;
		protected String condition;
		protected List<String> conditionColumns;
		protected String table;

	}

	public static class Reference {

		public Query getDest() {
			return dest;
		}

		public Boolean getDisplayRaw() {
			if (displayRaw == null) {
				return false;
			}

			return displayRaw;
		}

		public String getFixAction() {
			return fixAction;
		}

		public Boolean getHidden() {
			if (hidden == null) {
				return false;
			}

			return hidden;
		}

		public Query getOrigin() {
			return origin;
		}

		public Boolean isDisplayRaw() {
			return getDisplayRaw();
		}

		public Boolean isHidden() {
			return getHidden();
		}

		public void setDest(Query dest) {
			this.dest = dest;
		}

		public void setDisplayRaw(Boolean displayRaw) {
			this.displayRaw = displayRaw;
		}

		public void setFixAction(String fixAction) {
			this.fixAction = fixAction;
		}

		public void setHidden(Boolean hidden) {
			this.hidden = hidden;
		}

		public void setOrigin(Query origin) {
			this.origin = origin;
		}

		public String toString() {
			return getOrigin() + " => " + String.valueOf(getDest());
		}

		protected Query dest;
		protected Boolean displayRaw;
		protected String fixAction;
		protected Boolean hidden;
		protected Query origin;

	}

	protected List<String> ignoreColumns;
	protected List<String> ignoreTables;
	protected Listener listener;
	protected List<Reference> references;
	protected Map<String, Number> tableRank;
	protected Map<String, String> tableToClassNameMapping;

}