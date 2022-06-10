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

package com.liferay.referencechecker.ref;

import com.liferay.referencechecker.config.Configuration;
import com.liferay.referencechecker.dao.Query;
import com.liferay.referencechecker.dao.Table;
import com.liferay.referencechecker.dao.TableUtil;
import com.liferay.referencechecker.model.ModelUtil;

import java.sql.Connection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * @author Jorge Díaz
 */
public class ReferenceUtil {

	public ReferenceUtil(TableUtil tableUtil, ModelUtil modelUtil) {
		this.tableUtil = tableUtil;
		this.modelUtil = modelUtil;
	}

	public Collection<Reference> calculateReferences(
		Connection connection, Configuration configuration) {

		List<Reference> references = getReferences(connection, configuration);

		Map<Reference, Reference> referencesMap = new TreeMap<>();

		for (Reference reference : references) {
			referencesMap.put(reference, reference);
		}

		if (_log.isDebugEnabled()) {
			Set<String> idColumns = getNotCheckedColumns(
				referencesMap.values());

			_log.debug("List of id, key and pk columns that are not checked:");

			for (String idColumn : idColumns) {
				_log.debug(idColumn);
			}
		}

		return referencesMap.values();
	}

	public Set<String> getNotCheckedColumns(Collection<Reference> references) {
		Set<String> idColumns = new TreeSet<>();

		for (Table table : tableUtil.getTables()) {
			String tableName = StringUtils.lowerCase(table.getTableName());

			String primaryKey = table.getPrimaryKey();

			if (primaryKey != null) {
				primaryKey = StringUtils.lowerCase(primaryKey);
			}

			for (String columnName : table.getColumnNames()) {
				columnName = StringUtils.lowerCase(columnName);

				if (tableUtil.ignoreColumn(tableName, columnName) ||
					columnName.equals("uuid_") ||
					columnName.equals(primaryKey)) {

					continue;
				}

				if (columnName.contains("id") || columnName.contains("pk") ||
					columnName.contains("key")) {

					idColumns.add(tableName + "." + columnName);
				}
			}
		}

		for (Reference reference : references) {
			if (reference.getDestinationQuery() == null) {
				continue;
			}

			Query query = reference.getOriginQuery();

			Table table = query.getTable();

			String tableName = table.getTableName();

			for (String columnName : query.getColumns()) {
				String idColumn = tableName + "." + columnName;

				idColumn = StringUtils.lowerCase(idColumn);

				idColumns.remove(idColumn);
			}
		}

		return idColumns;
	}

	public List<Reference> getReferences(
		Connection connection, Configuration configuration) {

		List<Reference> referencesList = new ArrayList<>();

		for (Configuration.Reference referenceConfig :
				configuration.getReferences()) {

			try {
				List<Reference> references = getReferences(
					connection, referenceConfig);

				if (references.isEmpty() && _log.isInfoEnabled()) {
					_log.info("Ignoring reference config: " + referenceConfig);
				}

				referencesList.addAll(references);
			}
			catch (Exception exception) {
				_log.error(
					"Error parsing reference config: " + referenceConfig +
						" EXCEPTION: " + exception.getClass() + " - " +
							exception.getMessage(),
					exception);
			}
		}

		return referencesList;
	}

	public List<Reference> getReferences(
		Connection connection, Configuration.Reference referenceConfig) {

		/* origin */
		List<Table> originTables = getTablesToApply(
			referenceConfig.getOrigin());

		if (originTables.isEmpty()) {
			if (_log.isDebugEnabled()) {
				_log.debug("Origin tables list is empty");
			}

			return Collections.emptyList();
		}

		/* destination */
		List<Table> destinationTables = getTablesToApply(
			referenceConfig.getDest());

		/* generate references */
		List<Reference> referenceList = new ArrayList<>();

		for (Table originTable : originTables) {
			String originTableClassModel = modelUtil.getClassName(
				originTable.getTableName());

			if (!isCheckUndefinedTables() && (originTableClassModel == null)) {
				if (_log.isDebugEnabled()) {
					_log.debug(
						"Ignoring table because className is undefined: " +
							originTable);
				}

				continue;
			}

			if (ignoreEmptyTables &&
				tableUtil.isTableEmpty(connection, originTable)) {

				if (_log.isDebugEnabled()) {
					_log.debug(
						"Ignoring table because is empty: " + originTable);
				}

				continue;
			}

			referenceList.addAll(
				getReferences(
					connection, referenceConfig, originTable,
					destinationTables));
		}

		return referenceList;
	}

	public boolean isCheckUndefinedTables() {
		return checkUndefinedTables;
	}

	public boolean isIgnoreEmptyTables() {
		return ignoreEmptyTables;
	}

	public void setCheckUndefinedTables(boolean checkUndefinedTables) {
		this.checkUndefinedTables = checkUndefinedTables;
	}

	public void setIgnoreEmptyTables(boolean ignoreEmptyTables) {
		this.ignoreEmptyTables = ignoreEmptyTables;
	}

	protected String calculateFixAction(
		Query originQuery, Query destinationQuery) {

		if ((originQuery == null) || (destinationQuery == null)) {
			return null;
		}

		Table originTable = originQuery.getTable();

		Table destinationTable = destinationQuery.getTable();

		if (originTable.equals(destinationTable)) {
			return "???";
		}

		long destinationRank = modelUtil.getTableRank(
			destinationTable.getTableName());

		long originRank = modelUtil.getTableRank(originTable.getTableName());

		if (originRank < destinationRank) {
			return "delete";
		}

		if (originRank == 0) {
			if (isAuxiliaryTable(originTable)) {
				return "delete";
			}

			if (isChildTable(originTable, destinationTable)) {
				return "delete";
			}

			String originClassName = modelUtil.getClassName(
				originTable.getTableName());

			if ((originClassName != null) &&
				StringUtils.isBlank(originClassName)) {

				return "delete";
			}

			return "???";
		}

		if ((destinationRank == 0) || (originRank == destinationRank)) {
			return "???";
		}

		for (String originColumn : originQuery.getColumns()) {
			if (StringUtils.equalsIgnoreCase(
					originColumn, originTable.getPrimaryKey())) {

				return "???";
			}
		}

		return "update";
	}

	protected <T> List<List<T>> cartesianProduct(List<List<T>> lists) {
		List<List<T>> resultLists = new ArrayList<>();

		if (lists.isEmpty()) {
			List<T> emptyList = Collections.emptyList();

			resultLists.add(emptyList);

			return resultLists;
		}

		List<T> firstList = lists.get(0);
		List<List<T>> remainingLists = cartesianProduct(
			lists.subList(1, lists.size()));

		for (T condition : firstList) {
			for (List<T> remainingList : remainingLists) {
				ArrayList<T> resultList = new ArrayList<>();

				resultList.add(condition);
				resultList.addAll(remainingList);

				resultLists.add(resultList);
			}
		}

		return resultLists;
	}

	protected List<Reference> filterReferencesByDestinationClassName(
		List<Reference> references, String filter) {

		List<Reference> selectedReferences = new ArrayList<>();

		for (Reference reference : references) {
			Table destinationTable = _getDestinationTable(reference);

			String className = null;

			if (destinationTable != null) {
				className = modelUtil.getClassName(
					destinationTable.getTableName());
			}

			if (StringUtils.equals(filter, className) ||
				(_STAR.equals(filter) && StringUtils.isNotBlank(className))) {

				selectedReferences.add(reference);
			}
		}

		return selectedReferences;
	}

	protected Collection<Reference> getBestMatchingReferences(
		Query query, List<Reference> references) {

		String[] filters = {_STAR, StringUtils.EMPTY, null};

		for (String filter : filters) {
			List<Reference> filteredReferences =
				filterReferencesByDestinationClassName(references, filter);

			Collection<Reference> bestReferences = getBestMatchingReferences2(
				query, filteredReferences);

			if (!bestReferences.isEmpty()) {
				return bestReferences;
			}
		}

		return Collections.emptyList();
	}

	protected Collection<Reference> getBestMatchingReferences2(
		Query originQuery, Collection<Reference> references) {

		if (references.isEmpty()) {
			return Collections.emptyList();
		}

		if (references.size() == 1) {
			return references;
		}

		Table originTable = originQuery.getTable();

		String commonPrefix = StringUtils.EMPTY;
		List<Reference> bestReferences = new ArrayList<>();

		for (Reference reference : references) {
			Table destinationTable = _getDestinationTable(reference);

			if (destinationTable == null) {
				continue;
			}

			String newCommonPrefix = _getGreatestCommonPrefix(
				originTable.getTableName(), destinationTable.getTableName());

			if ((commonPrefix.length() < newCommonPrefix.length()) &&
				(newCommonPrefix.length() >= 2)) {

				commonPrefix = newCommonPrefix;
				bestReferences = new ArrayList<>();
			}

			if (commonPrefix.length() == newCommonPrefix.length()) {
				bestReferences.add(reference);
			}
		}

		return bestReferences;
	}

	protected List<String> getCastingsReplaced(
		List<String> castings, List<String> columns, List<String> newColumns) {

		if (castings == null) {
			return null;
		}

		List<String> castingsReplaced = new ArrayList<>();

		for (int i = 0; i < columns.size(); i++) {
			String casting = castings.get(i);
			String column = columns.get(i);
			String newColumn = newColumns.get(i);

			casting = casting.replace(column, newColumn);

			castingsReplaced.add(casting);
		}

		return castingsReplaced;
	}

	protected List<List<String>> getListColumns(
		Table table, List<String> columnsFilters) {

		List<List<String>> valuesAllColumns = new ArrayList<>();

		for (String column : columnsFilters) {
			List<String> valuesColumns = table.getColumnNames(column);

			valuesAllColumns.add(valuesColumns);
		}

		return cartesianProduct(valuesAllColumns);
	}

	protected Reference getRawReference(
		Table originTable, Configuration.Reference referenceConfig) {

		Configuration.Query originQueryConf = referenceConfig.getOrigin();

		Query rawOriginQuery = new Query(
			originTable, originQueryConf.getColumns(),
			originQueryConf.getCastings(), originQueryConf.getCondition());

		Configuration.Query destinationQueryConf = referenceConfig.getDest();

		String destinationTableName = destinationQueryConf.getTable();

		Table destinationTable = tableUtil.getTable(destinationTableName);

		if (destinationTable == null) {
			if (Objects.equals(destinationTableName, ".*")) {
				destinationTableName = "ANY";
			}

			destinationTable = new Table.Raw(destinationTableName);
		}

		Query rawDestinationQuery = new Query(
			destinationTable, destinationQueryConf.getColumns(),
			destinationQueryConf.getCastings(),
			destinationQueryConf.getCondition());

		Reference reference = new Reference(
			rawOriginQuery, rawDestinationQuery);

		reference.setRaw(true);

		return reference;
	}

	protected Reference getReference(
		Connection connection, Table originTable, List<String> originCastings,
		List<String> originColumns, String originCondition,
		Table destinationTable, List<String> destinationCastings,
		List<String> destinationColumns, String destinationCondition) {

		if (!hasColumns(originTable, originColumns)) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					"Ignoring origin table " + originTable.getTableName() +
						" because columns " + originColumns + " are not valid");
			}

			return null;
		}

		if ((destinationTable != null) &&
			!hasColumns(destinationTable, destinationColumns)) {

			if (_log.isDebugEnabled()) {
				_log.debug(
					"Ignoring destination table " +
						destinationTable.getTableName() + " because columns " +
							destinationColumns + " are not valid");
			}

			return null;
		}

		if (originTable.equals(destinationTable) &&
			originColumns.equals(destinationColumns)) {

			_log.debug(
				"Ignoring reference because origin and destination tables " +
					"and columns are the same ones");

			return null;
		}

		String originTableClassModel = modelUtil.getClassName(
			originTable.getTableName());

		if (!isCheckUndefinedTables() && (originTableClassModel == null)) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					"Ignoring originTable because className is undefined: " +
						originTable);
			}

			return null;
		}

		if (ignoreEmptyTables &&
			tableUtil.isTableEmpty(connection, originTable, originCondition)) {

			if (_log.isDebugEnabled()) {
				_log.debug(
					"Ignoring originTable because is empty: " + originTable);
			}

			return null;
		}

		Query originQuery = new Query(
			originTable, originColumns, originCastings, originCondition);

		if (destinationTable == null) {
			return new Reference(originQuery, null);
		}

		String destinationTableClassModel = modelUtil.getClassName(
			destinationTable.getTableName());

		if (isCheckUndefinedTables() || (destinationTableClassModel != null)) {
			Query destinationQuery = new Query(
				destinationTable, destinationColumns, destinationCastings,
				destinationCondition);

			return new Reference(originQuery, destinationQuery);
		}

		if (_log.isDebugEnabled()) {
			_log.debug(
				"Ignoring destinationTable because className is undefined: " +
					destinationTable);
		}

		return null;
	}

	protected List<Reference> getReferences(
		Connection connection, Configuration.Reference referenceConfig,
		Table originTable, List<Table> destinationTables) {

		Configuration.Query originConfig = referenceConfig.getOrigin();

		List<String> originConditionColumns =
			originConfig.getConditionColumns();

		if ((originConditionColumns != null) &&
			!hasColumns(originTable, originConditionColumns)) {

			return Collections.emptyList();
		}

		List<String> originCastings = originConfig.getCastings();
		List<String> originColumns = originConfig.getColumns();
		String originCondition = originConfig.getCondition();

		Configuration.Query destinationConfig = referenceConfig.getDest();

		if ((destinationConfig == null) || destinationTables.isEmpty()) {
			return getReferences(
				connection, originTable, originCastings, originColumns,
				originCondition, null, null, null, null);
		}

		Map<Query, List<Reference>> mapReferences = new HashMap<>();

		List<String> destinationCastings = destinationConfig.getCastings();
		List<String> destinationColumns = destinationConfig.getColumns();
		String destinationCondition = destinationConfig.getCondition();
		List<String> destinationConditionColumns =
			destinationConfig.getConditionColumns();

		boolean hiddenReference = referenceConfig.isHidden();

		for (Table destinationTable : destinationTables) {
			if ((destinationConditionColumns != null) &&
				!hasColumns(destinationTable, destinationConditionColumns)) {

				continue;
			}

			List<Reference> references = getReferences(
				connection, originTable, originCastings, originColumns,
				originCondition, destinationTable, destinationCastings,
				destinationColumns, destinationCondition);

			for (Reference reference : references) {
				if (hiddenReference) {
					reference.setHidden(true);
				}

				Query originQuery = reference.getOriginQuery();

				if (!mapReferences.containsKey(originQuery)) {
					mapReferences.put(originQuery, new ArrayList<Reference>());
				}

				List<Reference> list = mapReferences.get(originQuery);

				list.add(reference);
			}
		}

		List<Reference> referenceList = new ArrayList<>();

		for (Map.Entry<Query, List<Reference>> entry :
				mapReferences.entrySet()) {

			Collection<Reference> bestReferences = getBestMatchingReferences(
				entry.getKey(), entry.getValue());

			referenceList.addAll(bestReferences);
		}

		if (referenceConfig.isDisplayRaw()) {
			Reference rawReference = getRawReference(
				originTable, referenceConfig);

			referenceList.add(0, rawReference);
		}

		for (Reference reference : referenceList) {
			String fixAction = referenceConfig.getFixAction();

			if (fixAction == null) {
				fixAction = calculateFixAction(
					reference.getOriginQuery(),
					reference.getDestinationQuery());
			}

			reference.setFixAction(fixAction);
		}

		return referenceList;
	}

	protected List<Reference> getReferences(
		Connection connection, Table originTable, List<String> originCastings,
		List<String> originColumns, String originCondition,
		Table destinationTable, List<String> destinationCastings,
		List<String> destinationColumns, String destinationCondition) {

		originColumns = replaceVars(
			originTable, destinationTable, originColumns);
		originCastings = replaceVars(
			originTable, destinationTable, originCastings);
		originCondition = replaceVars(
			originTable, destinationTable, originCondition);
		destinationCastings = replaceVars(
			originTable, destinationTable, destinationCastings);
		destinationColumns = replaceVars(
			originTable, destinationTable, destinationColumns);
		destinationCondition = replaceVars(
			originTable, destinationTable, destinationCondition);

		List<List<String>> listOriginColumnsReplaced = getListColumns(
			originTable, originColumns);

		if ((destinationColumns != null) && (destinationColumns.size() == 1) &&
			Objects.equals(destinationColumns.get(0), "ignoreReference")) {

			destinationTable = null;
			destinationCondition = null;
		}

		List<Reference> referenceList = new ArrayList<>();

		for (List<String> originColumnsReplaced : listOriginColumnsReplaced) {
			List<String> originCastingsReplaced = getCastingsReplaced(
				originCastings, originColumns, originColumnsReplaced);

			Reference reference = getReference(
				connection, originTable, originCastingsReplaced,
				originColumnsReplaced, originCondition, destinationTable,
				destinationCastings, destinationColumns, destinationCondition);

			if (reference != null) {
				referenceList.add(reference);
			}
		}

		return referenceList;
	}

	protected List<Table> getTablesToApply(Configuration.Query queryConfig) {
		if ((queryConfig == null) ||
			StringUtils.isBlank(queryConfig.getTable())) {

			return Collections.emptyList();
		}

		return tableUtil.getTables(queryConfig.getTable());
	}

	protected boolean hasColumns(Table table, List<String> columns) {
		if (CollectionUtils.isEmpty(columns)) {
			return false;
		}

		for (String column : columns) {
			if (StringUtils.isBlank(column)) {
				return false;
			}

			boolean constant = false;

			if ((column.charAt(0) == '\'') &&
				(column.charAt(column.length() - 1) == '\'')) {

				constant = true;
			}

			if (constant || table.hasColumn(column)) {
				continue;
			}

			return false;
		}

		return true;
	}

	protected boolean isAuxiliaryTable(Table table) {
		String tableName = table.getTableName();

		for (String ending : _AUXILIARY_TABLE_ENDINGS) {
			if (StringUtils.endsWithIgnoreCase(tableName, ending)) {
				return true;
			}
		}

		return false;
	}

	protected boolean isChildTable(Table childTable, Table parentTable) {
		String childTableName = childTable.getTableName();
		String parentTableName = parentTable.getTableName();

		if (!StringUtils.startsWithIgnoreCase(
				childTableName, parentTableName)) {

			return false;
		}

		for (String ending : _CHILD_TABLE_ENDINGS) {
			if (StringUtils.equalsIgnoreCase(
					parentTableName + ending, childTableName)) {

				return true;
			}
		}

		return false;
	}

	protected String replaceVars(String text) {
		int pos = text.indexOf("${");

		String substring = text.substring(pos + 2);

		pos = substring.indexOf("}");

		if (pos == -1) {
			return text;
		}

		substring = substring.substring(0, pos);

		pos = substring.indexOf(".");

		if (pos == -1) {
			return text;
		}

		String tableName = substring.substring(0, pos);

		Table table = tableUtil.getTable(tableName);

		if (table == null) {
			return text;
		}

		return replaceVars(tableName, table, text);
	}

	protected String replaceVars(String varPrefix, Table table, String text) {
		String primaryKeyColumn = table.getPrimaryKey();

		String primaryKeyColumnFirstUpper = WordUtils.capitalize(
			primaryKeyColumn, new char[0]);

		if (primaryKeyColumn == null) {
			primaryKeyColumn = "INVALID_PK";
			primaryKeyColumnFirstUpper = "INVALID_PK";
		}

		String tableName = table.getTableName();

		String className = modelUtil.getClassName(tableName);

		return StringUtils.replaceEach(
			text,
			new String[] {
				"${" + varPrefix + ".className}",
				"${" + varPrefix + ".classNameId}",
				"${" + varPrefix + ".primaryKey}",
				"${" + varPrefix + ".PrimaryKey}",
				"${" + varPrefix + ".tableName}"
			},
			new String[] {
				className, String.valueOf(modelUtil.getClassNameId(className)),
				primaryKeyColumn, primaryKeyColumnFirstUpper, tableName
			});
	}

	protected List<String> replaceVars(
		Table originTable, Table destinationTable, List<String> list) {

		if (list == null) {
			return null;
		}

		List<String> newList = new ArrayList<>();

		for (String text : list) {
			text = replaceVars(originTable, destinationTable, text);

			newList.add(text);
		}

		return newList;
	}

	protected String replaceVars(
		Table originTable, Table destinationTable, String text) {

		if (text == null) {
			return null;
		}

		if (originTable != null) {
			text = replaceVars("origTable", originTable, text);
		}

		if (destinationTable != null) {
			text = replaceVars("destTable", destinationTable, text);
		}

		if (text.contains("${")) {
			text = replaceVars(text);
		}

		return text;
	}

	protected boolean checkUndefinedTables;
	protected boolean ignoreEmptyTables;
	protected ModelUtil modelUtil;
	protected TableUtil tableUtil;

	private Table _getDestinationTable(Reference reference) {
		Query destinationQuery = reference.getDestinationQuery();

		if (destinationQuery == null) {
			return null;
		}

		return destinationQuery.getTable();
	}

	private String _getGreatestCommonPrefix(String a, String b) {
		int minLength = Math.min(a.length(), b.length());

		for (int i = 0; i < minLength; i++) {
			if (a.charAt(i) != b.charAt(i)) {
				return a.substring(0, i);
			}
		}

		return a.substring(0, minLength);
	}

	private static final String[] _AUXILIARY_TABLE_ENDINGS = {
		"Link", "Localization", "Mapping", "Rel", "Version"
	};

	private static final String[] _CHILD_TABLE_ENDINGS = {
		"Attribute", "Instance", "InstanceToken", "Record", "Report", "Token",
		"Usage"
	};

	private static final String _STAR = "*";

	private static Logger _log = LogManager.getLogger(ReferenceUtil.class);

}