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

package com.liferay.referenceschecker.ref;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.referenceschecker.config.Configuration;
import com.liferay.referenceschecker.dao.Query;
import com.liferay.referenceschecker.dao.Table;
import com.liferay.referenceschecker.dao.TableUtil;
import com.liferay.referenceschecker.util.StringUtil;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @author Jorge Díaz
 */
public class ReferenceUtil {

	public static List<Reference> getConfigurationReferences(
		TableUtil tableUtil, Configuration configuration,
			boolean ignoreEmptyTables)
		throws IOException {

		ReferenceUtil referenceUtil = new ReferenceUtil(
			tableUtil, ignoreEmptyTables);

		List<Reference> referencesList = new ArrayList<Reference>();

		for (Configuration.Reference referenceConfig :
				configuration.getReferences()) {

			try {
				List<Reference> references = referenceUtil.getReferences(
					referenceConfig);

				if (references.isEmpty() && _log.isInfoEnabled()) {
					_log.info("Ignoring reference config: " + referenceConfig);
				}

				referencesList.addAll(references);
			}
			catch (Exception e) {
				_log.error(
					"Error parsing reference config: " + referenceConfig +
					" EXCEPTION: " + e.getClass() + " - " + e.getMessage(), e);
			}
		}

		return referencesList;
	}

	public ReferenceUtil(TableUtil tableUtil, boolean ignoreEmptyTables) {
		this.tableUtil = tableUtil;
		this.ignoreEmptyTables = ignoreEmptyTables;
	}

	public List<Reference> getReferences(
		Configuration.Reference referenceConfig) {

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
		List<Reference> listReferences = new ArrayList<Reference>();

		for (Table originTable : originTables) {
			if (ignoreEmptyTables && tableUtil.isTableEmpty(originTable)) {
				if (_log.isDebugEnabled()) {
					_log.debug(
						"Ignoring table because is empty: " + originTable);
				}

				continue;
			}

			listReferences.addAll(
				getReferences(referenceConfig, originTable, destinationTables));
		}

		return listReferences;
	}

	protected <T> List<List<T>> cartesianProduct(List<List<T>> lists) {
		List<List<T>> resultLists = new ArrayList<List<T>>();

		if (lists.size() == 0) {
			List<T> emptyList = Collections.emptyList();
			resultLists.add(emptyList);
			return resultLists;
		}

		List<T> firstList = lists.get(0);
		List<List<T>> remainingLists = cartesianProduct(
			lists.subList(1, lists.size()));

		for (T condition : firstList) {
			for (List<T> remainingList : remainingLists) {
				ArrayList<T> resultList = new ArrayList<T>();
				resultList.add(condition);
				resultList.addAll(remainingList);
				resultLists.add(resultList);
			}
		}

		return resultLists;
	}

	protected Collection<Reference> getBestMatchingReferences(
		Query originQuery, Collection<Reference> references) {

		if (references.size() == 0) {
			return Collections.emptyList();
		}

		if (references.size() == 1) {
			return references;
		}

		Table originTable = originQuery.getTable();

		String commonPrefix = StringPool.BLANK;
		List<Reference> bestReferences = new ArrayList<Reference>();

		for (Reference reference : references) {
			Query destinationQuery = reference.getDestinationQuery();
			Table destinationTable = destinationQuery.getTable();

			String newCommonPrefix = getGreatestCommonPrefix(
				originTable.getTableName(), destinationTable.getTableName());

			if ((commonPrefix.length() < newCommonPrefix.length()) &&
				(newCommonPrefix.length() >= 2)) {

				commonPrefix = newCommonPrefix;
				bestReferences = new ArrayList<Reference>();
			}

			if (commonPrefix.length() == newCommonPrefix.length()) {
				bestReferences.add(reference);
			}
		}

		return bestReferences;
	}

	protected List<List<String>> getListColumns(
		Table table, List<String> columnsFilters) {

		List<List<String>> valuesAllColumns = new ArrayList<List<String>>();

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
			originQueryConf.getCondition());

		Configuration.Query destinationQueryConf = referenceConfig.getDest();
		String destinationTableName = destinationQueryConf.getTable();
		Table destinationTable = tableUtil.getTable(destinationTableName);

		if (destinationTable == null) {
			if ("*".equals(destinationTableName)) {
				destinationTableName = "ANY";
			}

			destinationTable = new Table.Raw(destinationTableName);
		}

		Query rawDestinationQuery = new Query(
			destinationTable, destinationQueryConf.getColumns(),
			destinationQueryConf.getCondition());

		Reference reference = new Reference(
			rawOriginQuery, rawDestinationQuery);

		reference.setRaw(true);

		return reference;
	}

	protected Reference getReference(
		Table originTable, List<String> originColumns, String originCondition,
		Table destinationTable, List<String> destinationColumns,
		String destinationCondition) {

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
				"Ignoring reference because origin and destination " +
					"tables and columns are the same ones");

			return null;
		}

		if (ignoreEmptyTables &&
			tableUtil.isTableEmpty(originTable, originCondition)) {

			if (_log.isDebugEnabled()) {
				_log.debug("Ignoring table because is empty: " + originTable);
			}

			return null;
		}

		Query originQuery = new Query(
			originTable, originColumns, originCondition);

		Query destinationQuery = null;

		if (destinationTable != null) {
			destinationQuery = new Query(
				destinationTable, destinationColumns, destinationCondition);
		}

		return new Reference(originQuery, destinationQuery);
	}

	protected List<Reference> getReferences(
		Configuration.Reference referenceConfig, Table originTable,
		List<Table> destinationTables) {

		Configuration.Query originConfig = referenceConfig.getOrigin();
		Configuration.Query destinationConfig = referenceConfig.getDest();
		boolean hiddenReference = referenceConfig.isHidden();

		List<String> originConditionColumns =
			originConfig.getConditionColumns();

		if ((originConditionColumns != null) &&
			!hasColumns(originTable, originConditionColumns)) {

			return Collections.emptyList();
		}

		List<String> originColumns = originConfig.getColumns();
		String originCondition = originConfig.getCondition();

		if ((destinationConfig == null) || destinationTables.isEmpty()) {
			return getReferences(
				originTable, originColumns, originCondition, null, null, null);
		}

		Map<Query, List<Reference>> mapReferences =
			new HashMap<Query, List<Reference>>();

		List<String> destinationColumns = destinationConfig.getColumns();
		String destinationCondition = destinationConfig.getCondition();
		List<String> destinationConditionColumns =
			destinationConfig.getConditionColumns();

		for (Table destinationTable : destinationTables) {
			if ((destinationConditionColumns != null) &&
				!hasColumns(destinationTable, destinationConditionColumns)) {

				continue;
			}

			List<Reference> references = getReferences(
				originTable, originColumns, originCondition, destinationTable,
				destinationColumns, destinationCondition);

			for (Reference reference : references) {
				if (hiddenReference) {
					reference.setHidden(true);
				}

				Query originQuery = reference.getOriginQuery();

				if (!mapReferences.containsKey(originQuery)) {
					mapReferences.put(originQuery, new ArrayList<Reference>());
				}

				mapReferences.get(originQuery).add(reference);
			}
		}

		List<Reference> listReferences = new ArrayList<Reference>();

		for (Entry<Query, List<Reference>> entry : mapReferences.entrySet()) {
			Collection<Reference> bestReferences = getBestMatchingReferences(
				entry.getKey(), entry.getValue());

			listReferences.addAll(bestReferences);
		}

		if (referenceConfig.isDisplayRaw()) {
			Reference rawReference = getRawReference(
				originTable, referenceConfig);

			listReferences.add(0, rawReference);
		}

		return listReferences;
	}

	protected List<Reference> getReferences(
		Table originTable, List<String> originColumns, String originCondition,
		Table destinationTable, List<String> destinationColumns,
		String destinationCondition) {

		originColumns = replaceVars(
			originTable, destinationTable, originColumns);
		originCondition = replaceVars(
			originTable, destinationTable, originCondition);
		destinationColumns = replaceVars(
			originTable, destinationTable, destinationColumns);
		destinationCondition = replaceVars(
			originTable, destinationTable, destinationCondition);

		List<List<String>> listOriginColumnsReplaced = getListColumns(
			originTable, originColumns);

		if ((destinationColumns != null) && (destinationColumns.size() == 1) &&
			"ignoreReference".equals(destinationColumns.get(0))) {

			destinationTable = null;
			destinationCondition = null;
		}

		List<Reference> listReferences = new ArrayList<Reference>();

		for (List<String> originColumnsReplaced : listOriginColumnsReplaced) {
			Reference reference = getReference(
				originTable, originColumnsReplaced, originCondition,
				destinationTable, destinationColumns, destinationCondition);

			if (reference != null) {
				listReferences.add(reference);
			}
		}

		return listReferences;
	}

	protected List<Table> getTablesToApply(Configuration.Query queryConfig) {
		if ((queryConfig == null) || Validator.isNull(queryConfig.getTable())) {
			return Collections.emptyList();
		}

		return tableUtil.getTables(queryConfig.getTable());
	}

	protected boolean hasColumns(Table table, List<String> columns) {
		if ((columns == null) || columns.isEmpty()) {
			return false;
		}

		for (String column : columns) {
			if (Validator.isNull(column)) {
				return false;
			}

			boolean isConstant =
				(column.charAt(0) == '\'') &&
				(column.charAt(column.length()-1) == '\'');

			if (isConstant || table.hasColumn(column)) {
				continue;
			}

			return false;
		}

		return true;
	}

	protected String replaceVars(String text) {
		int pos = text.indexOf("${");
		String substring = text.substring(pos+2);
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
		String primaryKeyColumnFirstUpper = StringPool.BLANK;

		if (primaryKeyColumn != null) {
			primaryKeyColumnFirstUpper = StringUtil.upperCaseFirstLetter(
				primaryKeyColumn);
		}

		String className = table.getClassName();
		long classNameId = table.getClassNameId();
		String tableName = table.getTableName();

		return StringUtil.replace(
				text,
				new String[] {
					"${" + varPrefix + ".className}",
					"${" + varPrefix + ".classNameId}",
					"${" + varPrefix + ".primaryKey}",
					"${" + varPrefix + ".PrimaryKey}",
					"${" + varPrefix + ".tableName}"},
				new String[] {
					className, Long.toString(classNameId), primaryKeyColumn,
					primaryKeyColumnFirstUpper, tableName });
	}

	protected List<String> replaceVars(
		Table originTable, Table destinationTable, List<String> list) {

		if (list == null) {
			return null;
		}

		List<String> newList = new ArrayList<String>();

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

	private String getGreatestCommonPrefix(String a, String b) {
		int minLength = Math.min(a.length(), b.length());

		for (int i = 0; i < minLength; i++) {
			if (a.charAt(i) != b.charAt(i)) {
				return a.substring(0, i);
			}
		}

		return a.substring(0, minLength);
	}

	private static Log _log = LogFactoryUtil.getLog(ReferenceUtil.class);

	private boolean ignoreEmptyTables;
	private TableUtil tableUtil;

}