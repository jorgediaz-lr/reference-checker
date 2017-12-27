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
import java.util.Collections;
import java.util.List;

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
		List<Reference> listReference = new ArrayList<Reference>();

		for (Table originTable : originTables) {
			if (ignoreEmptyTables && tableUtil.isTableEmpty(originTable)) {
				if (_log.isDebugEnabled()) {
					_log.debug(
						"Ignoring table because is empty: " + originTable);
				}

				continue;
			}

			listReference.addAll(
				getReferences(referenceConfig, originTable, destinationTables));
		}

		return listReference;
	}

	protected Reference getReference(
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
			(TableUtil.countTable(originTable, originCondition) <= 0)) {

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

		List<String> originConditionColumns =
			originConfig.getConditionColumns();

		if ((originConditionColumns != null) &&
			!hasColumns(originTable, originConditionColumns)) {

			return Collections.emptyList();
		}

		List<String> originColumns = originConfig.getColumns();
		String originCondition = originConfig.getCondition();

		if ((destinationConfig == null) || destinationTables.isEmpty()) {
			Table destinationTable = null;
			List<String> destinationColumns = null;
			String destinationCondition = null;

			if ((destinationConfig != null) &&
				"SELF".equals(destinationConfig.getTable())) {

				List<String> destinationConditionColumns =
					destinationConfig.getConditionColumns();

				if ((destinationConditionColumns != null) &&
					!hasColumns(originTable, destinationConditionColumns)) {

					return Collections.emptyList();
				}

				destinationTable = originTable;
				destinationColumns = destinationConfig.getColumns();
				destinationCondition = destinationConfig.getCondition();
			}

			Reference reference = getReference(
				originTable, originColumns, originCondition, destinationTable,
				destinationColumns, destinationCondition);

			if (reference == null) {
				return Collections.emptyList();
			}

			return Collections.singletonList(reference);
		}

		List<Reference> listReferences = new ArrayList<Reference>();

		List<String> destinationColumns = destinationConfig.getColumns();
		String destinationCondition = destinationConfig.getCondition();
		List<String> destinationConditionColumns =
			destinationConfig.getConditionColumns();

		for (Table destinationTable : destinationTables) {
			if ((destinationConditionColumns != null) &&
				!hasColumns(destinationTable, destinationConditionColumns)) {

				continue;
			}

			Reference reference = getReference(
				originTable, originColumns, originCondition, destinationTable,
				destinationColumns, destinationCondition);

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

		return StringUtil.replace(
				text,
				new String[] {
					"${" + varPrefix + ".className}",
					"${" + varPrefix + ".classNameId}",
					"${" + varPrefix + ".primaryKey}",
					"${" + varPrefix + ".PrimaryKey}" },
				new String[] {
					className, Long.toString(classNameId), primaryKeyColumn,
					primaryKeyColumnFirstUpper });
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

	private static Log _log = LogFactoryUtil.getLog(ReferenceUtil.class);

	private boolean ignoreEmptyTables;
	private TableUtil tableUtil;

}