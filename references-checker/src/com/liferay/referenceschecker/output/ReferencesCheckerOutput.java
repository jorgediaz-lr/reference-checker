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

package com.liferay.referenceschecker.output;

import com.liferay.portal.kernel.dao.search.ResultRow;
import com.liferay.portal.kernel.dao.search.SearchContainer;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.PortalClassLoaderUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.referenceschecker.dao.Query;
import com.liferay.referenceschecker.dao.Table;
import com.liferay.referenceschecker.ref.MissingReferences;
import com.liferay.referenceschecker.ref.Reference;
import com.liferay.referenceschecker.util.StringUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.portlet.PortletURL;
import javax.portlet.RenderRequest;

/**
 * @author Jorge Díaz
 */
public class ReferencesCheckerOutput {

	public static Log _log = LogFactoryUtil.getLog(
			ReferencesCheckerOutput.class);

	public static List<String> generateCSVOutputCheckReferences(
		List<String> headers, List<MissingReferences> listMissingReferences,
		int missingReferencesLimit) {

		List<String> out = new ArrayList<String>();

		out.add(getCSVRow(headers));

		for (MissingReferences missingReferences : listMissingReferences) {
			List<String> line = generateReferenceCells(
				missingReferences.getReference(), false);

			Throwable throwable = missingReferences.getThrowable();
			Collection<String> missingValues = missingReferences.getValues();

			if ((throwable != null) || (missingValues == null)) {
				line.add("-1");
				line.add("Error checking references");
			}

			if (throwable != null) {
				line.add(
					"EXCEPTION: " +throwable.getClass() + " - " +
					throwable.getMessage());
			}
			else if (missingValues != null) {
				line.add(String.valueOf(missingValues.size()));

				if (missingReferencesLimit == 0) {
					line.add(StringPool.BLANK);
				}
				else if ((missingReferencesLimit != -1) &&
						 (missingValues.size() >= missingReferencesLimit)) {

					String[] missingValuesArray = missingValues.toArray(
						new String[0]);
					missingValuesArray = Arrays.copyOfRange(
						missingValuesArray, 0, missingReferencesLimit);
					line.add(StringUtil.merge(missingValuesArray)+ "...");
				}
				else {
					line.add(StringUtil.merge(missingValues));
				}
			}

			out.add(getCSVRow(line));
		}

		return out;
	}

	public static List<String> generateCSVOutputMap(
		List<String> headers, Map<String, ?> mapTableCount) {

		List<String> out = new ArrayList<String>();

		out.add(getCSVRow(headers));

		for (Entry<String, ?> entry : mapTableCount.entrySet()) {
			List<String> line = new ArrayList<String>();
			line.add(entry.getKey());

			String valueString;

			Object value = entry.getValue();

			if (value instanceof List) {
				List<?> list = (List<?>)value;

				valueString = Arrays.toString(list.toArray());
			}
			else {
				valueString = String.valueOf(value);
			}

			line.add(valueString);
			out.add(getCSVRow(line));
		}

		return out;
	}

	public static List<String> generateCSVOutputMappingList(
		List<String> headers, Map<Reference, Reference> references) {

		List<String> out = new ArrayList<String>();

		out.add(getCSVRow(headers));

		for (Reference reference : references.values()) {
			if (!reference.isHidden()) {
				List<String> line = generateReferenceCells(reference, false);

				out.add(getCSVRow(line));
			}
		}

		return out;
	}

	public static SearchContainer<MissingReferences>
		generateSearchContainerCheckReferences(
			RenderRequest renderRequest, PortletURL serverURL,
			List<String> headers,
			List<MissingReferences> listMissingReferences) {

		SearchContainer<MissingReferences> searchContainer =
			new SearchContainer<MissingReferences>(
				renderRequest, null, null, SearchContainer.DEFAULT_CUR_PARAM,
				SearchContainer.MAX_DELTA, serverURL, headers, null);

		listMissingReferences = ListUtil.subList(
			listMissingReferences, searchContainer.getStart(),
			searchContainer.getEnd());

		searchContainer.setResults(listMissingReferences);

		List resultRows = searchContainer.getResultRows();

		int numberOfRows = 0;
		int maxSize = 20;

		for (MissingReferences missingReferences : listMissingReferences) {
			try {
				Object row = generateSearchContainerRowCheckReferences(
					missingReferences, numberOfRows, maxSize);

				if (row != null) {
					numberOfRows++;
					resultRows.add(row);
				}
			}
			catch (Exception e) {
				throw new RuntimeException (e);
			}
		}

		searchContainer.setTotal(numberOfRows);

		return searchContainer;
	}

	public static SearchContainer<Reference>
		generateSearchContainerMappingList(
			RenderRequest renderRequest, PortletURL serverURL,
			List<String> headers, List<Reference> referecesList) {

		SearchContainer<Reference> searchContainer =
			new SearchContainer<Reference>(
				renderRequest, null, null, SearchContainer.DEFAULT_CUR_PARAM,
				SearchContainer.MAX_DELTA, serverURL, headers, null);

		List<Reference> filteredReferencesList = new ArrayList<Reference>();

		for (Reference reference : referecesList) {
			if (!reference.isHidden()) {
				filteredReferencesList.add(reference);
			}
		}

		filteredReferencesList = ListUtil.subList(
			filteredReferencesList, searchContainer.getStart(),
			searchContainer.getEnd());

		searchContainer.setResults(filteredReferencesList);

		List resultRows = searchContainer.getResultRows();

		int numberOfRows = 0;

		for (Reference reference : filteredReferencesList) {
			try {
				Object row = generateSearchContainerRowMappingList(
					reference, numberOfRows);

				numberOfRows++;
				resultRows.add(row);
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		searchContainer.setTotal(numberOfRows);

		return searchContainer;
	}

	public static String getCSVRow(List<String> rowData) {
		return getCSVRow(rowData, StringPool.COMMA);
	}

	public static String getCSVRow(List<String> rowData, String sep) {

		String row = StringPool.BLANK;

		for (String aux : rowData) {
			row = addCell(row, aux, sep);
		}

		return row;
	}

	public static ResultRow newResultRow(
		Object obj, String primaryKey, int pos) {

		try {
			return (ResultRow)resultRowConstructor.newInstance(
				obj, primaryKey, pos);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected static String addCell(String line, String cell, String sep) {
		if (cell.contains(StringPool.SPACE) || cell.contains(sep)) {
			cell = StringPool.QUOTE + cell + StringPool.QUOTE;
		}

		if (Validator.isNull(line)) {
			line = cell;
		}
		else {
			line += sep + cell;
		}

		return line;
	}

	protected static List<String> generateReferenceCells(
		Query query, boolean withTypes) {

		List<String> line = new ArrayList<String>();

		String tableWithCondition = query.getTable().getTableName();

		if (query.getCondition() != null) {
			StringBundler sb = new StringBundler();

			sb.append(tableWithCondition);
			sb.append(" WHERE ");
			sb.append(query.getCondition());

			tableWithCondition = sb.toString();
		}

		String attributes;

		if (withTypes) {
			attributes = Table.getColumnsWithTypes(
				query.getTable(), query.getColumns());
		}
		else {
			attributes = StringUtil.merge(query.getColumns());
		}

		line.add(tableWithCondition);
		line.add(attributes);

		return line;
	}

	protected static List<String> generateReferenceCells(
		Reference reference, boolean withTypes) {

		List<String> line = new ArrayList<String>();

		Query originQuery = reference.getOriginQuery();
		Query destinationQuery = reference.getDestinationQuery();

		line.addAll(generateReferenceCells(originQuery, withTypes));
		line.addAll(generateReferenceCells(destinationQuery, withTypes));

		return line;
	}

	protected static Object generateSearchContainerRowCheckReferences(
			MissingReferences missingReferences, int numberOfRows, int maxSize)
		throws Exception {

		Query originQuery = missingReferences.getReference().getOriginQuery();

		Object row = newResultRow(
			missingReferences, originQuery.toString(), numberOfRows);

		List<String> line = generateReferenceCells(
			missingReferences.getReference(), false);

		Method addTextMethod =
			row.getClass().getMethod("addText", String.class);

		for (String cell : line) {
			addTextMethod.invoke(row, escapeText(cell));
		}

		Throwable throwable = missingReferences.getThrowable();
		Collection<String> missingValues = missingReferences.getValues();

		if (throwable != null) {
			addTextMethod.invoke(
				row,
				HtmlUtil.escape(
					"Error checking references, EXCEPTION: " +
					throwable.getClass() + " - " + throwable.getMessage()));
		}
		else if (missingValues == null) {
			addTextMethod.invoke(
				row, (HtmlUtil.escape("Error checking references")));
		}
		else {
			String outputString = getOutput(
					missingValues, numberOfRows, maxSize);

			addTextMethod.invoke(row, outputString);
		}

		return row;
	}

	protected static Object generateSearchContainerRowMappingList(
			Reference reference, int numberOfRows)
		throws Exception {

		Object row = newResultRow(
			reference, reference.getOriginQuery().toString(), numberOfRows);

		List<String> line = generateReferenceCells(reference, true);

		Method addTextMethod =
			row.getClass().getMethod("addText", String.class);

		for (String cell : line) {
			addTextMethod.invoke(row, escapeText(cell));
		}

		return row;
	}

	protected static String getOutput(
			Collection<String> missingValues, int numberOfRows, int maxSize) {

		String outputString = StringUtil.merge(missingValues);

		outputString = HtmlUtil.escape(outputString.replace(",", ", "));

		String outputStringTrimmed = null;

		int overflow = missingValues.size() - maxSize;

		if (overflow > 0) {
			outputStringTrimmed = StringUtil.merge(
				ArrayUtil.subset(missingValues.toArray(), 0, maxSize));

			outputStringTrimmed = HtmlUtil.escape(
				outputStringTrimmed.replace(",", ", "));

			String tagId = StringUtil.randomString() + "_" + numberOfRows;
			String onClick =
				"onclick=\"showHide('" + tagId + "');return false;\"";
			String linkMore =
				"<a href=\"#\"" + onClick + " >(" + overflow + " more)</a>";
			String linkCollapse =
				"<a href=\"#\"" + onClick + " >(collapse)</a>";

			outputString =
				"<span id=\"" + tagId + "-show\" >" + outputStringTrimmed +
				"... " + linkMore + "</span><span id=\"" + tagId +
				"\" style=\"display: none;\" >" + outputString + " " +
				linkCollapse + "</span>";
		}

		return outputString;
	}

	private static String escapeText(String text) {
		text = text.replace(",", ", ");
		text = text.replace("=", " = ");
		int textLength = text.length();
		while (true) {
			text = text.replace("  ", " ");

			if (text.length() == textLength) {
				break;
			}

			textLength = text.length();
		}

		return HtmlUtil.escape(text);
	}

	private static Constructor<?> resultRowConstructor;

	static {
		try {
			resultRowConstructor = ResultRow.class.getConstructor(
				Object.class, String.class, int.class);
		}
		catch (Throwable t) {
		}

		if (resultRowConstructor == null) {
			try {
				ClassLoader classLoader =
					PortalClassLoaderUtil.getClassLoader();

				Class<?> resultRowTagLib = classLoader.loadClass(
					"com.liferay.taglib.search.ResultRow");

				resultRowConstructor = resultRowTagLib.getConstructor(
					Object.class, String.class, int.class);
			}
			catch (Throwable t) {
			}
		}
	}

}