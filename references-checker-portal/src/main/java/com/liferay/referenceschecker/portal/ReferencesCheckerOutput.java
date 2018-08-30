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

package com.liferay.referenceschecker.portal;

import com.liferay.portal.kernel.dao.search.ResultRow;
import com.liferay.portal.kernel.dao.search.SearchContainer;
import com.liferay.portal.kernel.util.PortalClassLoaderUtil;
import com.liferay.referenceschecker.OutputUtil;
import com.liferay.referenceschecker.dao.Query;
import com.liferay.referenceschecker.ref.MissingReferences;
import com.liferay.referenceschecker.ref.Reference;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.portlet.PortletURL;
import javax.portlet.RenderRequest;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.text.StringEscapeUtils;

/**
 * @author Jorge Díaz
 */
public class ReferencesCheckerOutput {

	public static SearchContainer<MissingReferences>
		generateSearchContainerCheckReferences(
			RenderRequest renderRequest, PortletURL serverURL,
			List<String> headers,
			List<MissingReferences> listMissingReferences) {

		SearchContainer<MissingReferences> searchContainer =
			new SearchContainer<>(
				renderRequest, null, null, SearchContainer.DEFAULT_CUR_PARAM,
				SearchContainer.MAX_DELTA, serverURL, headers, null);

		listMissingReferences = _sublist(
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
				throw new RuntimeException(e);
			}
		}

		searchContainer.setTotal(numberOfRows);

		return searchContainer;
	}

	public static SearchContainer<Reference> generateSearchContainerMappingList(
		RenderRequest renderRequest, PortletURL serverURL, List<String> headers,
		List<Reference> referecesList) {

		SearchContainer<Reference> searchContainer = new SearchContainer<>(
			renderRequest, null, null, SearchContainer.DEFAULT_CUR_PARAM,
			SearchContainer.MAX_DELTA, serverURL, headers, null);

		List<Reference> filteredReferencesList = new ArrayList<>();

		for (Reference reference : referecesList) {
			if (!reference.isHidden()) {
				filteredReferencesList.add(reference);
			}
		}

		filteredReferencesList = _sublist(
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

	public static ResultRow newResultRow(
		Object obj, String primaryKey, int pos) {

		try {
			return (ResultRow)_resultRowConstructor.newInstance(
				obj, primaryKey, pos);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected static Object generateSearchContainerRowCheckReferences(
			MissingReferences missingReferences, int numberOfRows, int maxSize)
		throws Exception {

		Reference reference = missingReferences.getReference();

		Query originQuery = reference.getOriginQuery();

		Object row = newResultRow(
			missingReferences, originQuery.toString(), numberOfRows);

		List<String> line = OutputUtil.generateReferenceCells(
			missingReferences.getReference(), false);

		Class<?> rowClass = row.getClass();

		Method addTextMethod = rowClass.getMethod("addText", String.class);

		for (String cell : line) {
			addTextMethod.invoke(row, _escapeText(cell));
		}

		Throwable throwable = missingReferences.getThrowable();
		Collection<Object[]> missingValues = missingReferences.getValues();

		if (throwable != null) {
			addTextMethod.invoke(
				row,
				StringEscapeUtils.escapeHtml4(
					"Error checking references, EXCEPTION: " +
						throwable.getClass() + " - " + throwable.getMessage()));
		}
		else if (missingValues == null) {
			addTextMethod.invoke(
				row,
				StringEscapeUtils.escapeHtml4("Error checking references"));
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

		Query originQuery = reference.getOriginQuery();

		Object row = newResultRow(
			reference, originQuery.toString(), numberOfRows);

		List<String> line = OutputUtil.generateReferenceCells(reference, true);

		Class<?> rowClass = row.getClass();

		Method addTextMethod = rowClass.getMethod("addText", String.class);

		for (String cell : line) {
			addTextMethod.invoke(row, _escapeText(cell));
		}

		return row;
	}

	protected static String getOutput(
		Collection<Object[]> missingValues, int numberOfRows, int maxSize) {

		String outputString = OutputUtil.concatenate(missingValues);

		outputString = StringEscapeUtils.escapeHtml4(
			outputString.replace(",", ", "));

		String outputStringTrimmed = null;

		int overflow = missingValues.size() - maxSize;

		if (overflow > 0) {
			outputStringTrimmed = OutputUtil.concatenate(
				missingValues, maxSize);

			outputStringTrimmed = StringEscapeUtils.escapeHtml4(
				outputStringTrimmed.replace(",", ", "));

			String tagId =
				RandomStringUtils.randomAlphabetic(4) + "_" + numberOfRows;

			String onClick =
				"onclick=\"showHide('" + tagId + "');return false;\"";

			String linkMore =
				"<a href=\"#\"" + onClick + " >(" + overflow + " more)</a>";
			String linkCollapse =
				"<a href=\"#\"" + onClick + " >(collapse)</a>";

			outputString =
				"<span id=\"" + tagId + "-show\" >" + outputStringTrimmed +
					" " + linkMore + "</span><span id=\"" + tagId +
						"\" style=\"display: none;\" >" + outputString + " " +
							linkCollapse + "</span>";
		}

		return outputString;
	}

	private static String _escapeText(String text) {
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

		return StringEscapeUtils.escapeHtml4(text);
	}

	private static <E> List<E> _sublist(List<E> list, int start, int end) {
		if (start < 0) {
			start = 0;
		}

		if ((end < 0) || (end > list.size())) {
			end = list.size();
		}

		if (start < end) {
			return list.subList(start, end);
		}

		return Collections.emptyList();
	}

	private static Constructor<?> _resultRowConstructor;

	static {
		try {
			_resultRowConstructor = ResultRow.class.getConstructor(
				Object.class, String.class, int.class);
		}
		catch (Throwable t) {
		}

		if (_resultRowConstructor == null) {
			try {
				ClassLoader classLoader =
					PortalClassLoaderUtil.getClassLoader();

				Class<?> resultRowTagLib = classLoader.loadClass(
					"com.liferay.taglib.search.ResultRow");

				_resultRowConstructor = resultRowTagLib.getConstructor(
					Object.class, String.class, int.class);
			}
			catch (Throwable t) {
			}
		}
	}

}