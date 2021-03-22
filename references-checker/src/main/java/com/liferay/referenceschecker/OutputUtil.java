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

package com.liferay.referenceschecker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import com.liferay.referenceschecker.dao.Query;
import com.liferay.referenceschecker.dao.Table;
import com.liferay.referenceschecker.ref.MissingReferences;
import com.liferay.referenceschecker.ref.Reference;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * @author Jorge Díaz
 */
public class OutputUtil {

	public static String concatenate(Collection<Object[]> values) {
		return concatenate(values, -1);
	}

	public static String concatenate(Collection<Object[]> values, int limit) {
		if ((limit == 0) || (values == null) || values.isEmpty()) {
			return StringUtils.EMPTY;
		}

		StringBuilder sb = new StringBuilder(values.size() * 2);

		int i = 0;

		for (Object[] value : values) {
			if (i != 0) {
				sb.append(",");
			}

			String string;

			if (value.length == 1) {
				string = String.valueOf(value[0]);
			}
			else {
				string = Arrays.toString(value);
			}

			sb.append(string);

			i++;

			if ((limit >= 0) && (i >= limit)) {
				break;
			}
		}

		if ((limit > 0) && (values.size() >= limit)) {
			sb.append("...");
		}

		return sb.toString();
	}

	public static List<Map<?, ?>> csvToMapList(String csvContent)
		throws IOException {

		CsvSchema emptySchema = CsvSchema.emptySchema();

		CsvSchema bootstrap = emptySchema.withHeader();

		CsvMapper csvMapper = new CsvMapper();

		ObjectReader objectReader = csvMapper.readerFor(Map.class);

		objectReader = objectReader.with(bootstrap);

		MappingIterator<Map<?, ?>> mappingIterator = objectReader.readValues(
			csvContent);

		return mappingIterator.readAll();
	}

	public static List<String> generateCSVOutputCheckReferences(
		Collection<MissingReferences> listMissingReferences,
		int missingReferencesLimit) {

		List<String> out = new ArrayList<>();

		List<String> headers = Arrays.asList(HEADERS_MISSING_REFERENCES);

		out.add(getCSVRow(headers));

		for (MissingReferences missingReferences : listMissingReferences) {
			List<String> line = generateReferenceCells(
				missingReferences.getReference(), false);

			Throwable throwable = missingReferences.getThrowable();
			Collection<Object[]> missingValues = missingReferences.getValues();

			if (missingValues != null) {
				line.add(String.valueOf(missingReferences.getAffectedRows()));
				line.add(String.valueOf(missingValues.size()));

				String missingReferencesString = concatenate(
					missingValues, missingReferencesLimit);

				line.add(missingReferencesString);
			}
			else if (throwable == null) {
				line.add("-1");
				line.add("-1");

				line.add("Error checking references");
			}
			else {
				line.add("-1");
				line.add("-1");
				line.add(
					"EXCEPTION: " + throwable.getClass() + " - " +
						throwable.getMessage());
			}

			out.add(getCSVRow(line));
		}

		return out;
	}

	public static List<String> generateCSVOutputMap(
		List<String> headers, Map<String, ?> mapTableCount) {

		List<String> out = new ArrayList<>();

		out.add(getCSVRow(headers));

		for (Map.Entry<String, ?> entry : mapTableCount.entrySet()) {
			List<String> line = new ArrayList<>();

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
		Collection<Reference> references) {

		List<String> out = new ArrayList<>();

		List<String> headers = Arrays.asList(HEADERS_REFERENCES);

		out.add(getCSVRow(headers));

		for (Reference reference : references) {
			if (!reference.isHidden()) {
				List<String> line = generateReferenceCells(reference, false);

				out.add(getCSVRow(line));
			}
		}

		return out;
	}

	public static List<String> generateReferenceCells(
		Reference reference, boolean withTypes) {

		List<String> line = new ArrayList<>();

		line.addAll(
			generateReferenceCells(reference.getOriginQuery(), withTypes));
		line.addAll(
			generateReferenceCells(reference.getDestinationQuery(), withTypes));
		line.add(reference.getFixAction());

		return line;
	}

	public static String mapListToJSON(List<Map<?, ?>> data)
		throws JsonProcessingException {

		ObjectMapper mapper = new ObjectMapper();

		return mapper.writeValueAsString(data);
	}

	protected static String addCell(String line, String cell, String sep) {
		if (cell.contains(StringUtils.SPACE) || cell.contains(sep)) {
			cell = "\"" + cell + "\"";
		}

		if (StringUtils.isBlank(line)) {
			line = cell;
		}
		else {
			line += sep + cell;
		}

		return line;
	}

	protected static List<String> generateReferenceCells(
		Query query, boolean withTypes) {

		List<String> line = new ArrayList<>();

		Table table = query.getTable();

		String tableWithCondition = table.getTableName();

		if (query.getCondition() != null) {
			StringBuilder sb = new StringBuilder();

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
			attributes = StringUtils.join(query.getColumns(), ",");
		}

		List<String> casting = query.getCasting();

		if ((casting != null) && !casting.isEmpty()) {
			attributes = attributes + " // " + StringUtils.join(casting, ",");
		}

		line.add(tableWithCondition);
		line.add(attributes);

		return line;
	}

	protected static String getCSVRow(List<String> rowData) {
		return getCSVRow(rowData, ",");
	}

	protected static String getCSVRow(List<String> rowData, String sep) {
		String row = StringUtils.EMPTY;

		for (String aux : rowData) {
			row = addCell(row, aux, sep);
		}

		return row;
	}

	protected static final String[] HEADERS_MISSING_REFERENCES = {
		"origin table", "attributes", "destination table", "dest attributes",
		"fix action", "affec. rows", "affec. values", "missing references"
	};

	protected static final String[] HEADERS_REFERENCES = {
		"origin table", "attributes", "destination table", "dest attributes",
		"fix action"
	};

}