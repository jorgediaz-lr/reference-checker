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

package com.liferay.referenceschecker.main.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * @author Jorge Díaz
 */
public class CsvToHtmlUtil {

	public static List<Map<?, ?>> readObjectsFromCsv(File file)
		throws IOException {

		CsvSchema emptySchema = CsvSchema.emptySchema();

		CsvSchema bootstrap = emptySchema.withHeader();

		CsvMapper csvMapper = new CsvMapper();

		ObjectReader objectReader = csvMapper.readerFor(Map.class);

		objectReader = objectReader.with(bootstrap);

		MappingIterator<Map<?, ?>> mappingIterator = objectReader.readValues(
			file);

		return mappingIterator.readAll();
	}

	public static void writeOutputHtml(
			File sourceCsvFile, File htmlFile, String footer)
		throws FileNotFoundException, IOException, JsonProcessingException,
			   UnsupportedEncodingException {

		List<Map<?, ?>> data = readObjectsFromCsv(sourceCsvFile);

		ObjectMapper mapper = new ObjectMapper();

		String jsonData = mapper.writeValueAsString(data);

		String template = getResource("show-table_template.html");

		String title = StringUtils.removeEnd(htmlFile.getName(), ".html");

		template = template.replace("${TITLE}", title);
		template = template.replace(
			"${CSV_FILE_NAME}", sourceCsvFile.getName());
		template = template.replace("${JSON_DATA}", jsonData);
		template = template.replace("${FOOTER}", footer);

		PrintWriter writer = new PrintWriter(htmlFile, "UTF-8");

		writer.print(template);

		writer.close();
	}

	protected static String getResource(String resourceName)
		throws IOException, UnsupportedEncodingException {

		ClassLoader classLoader = CsvToHtmlUtil.class.getClassLoader();

		InputStream inputStream = classLoader.getResourceAsStream(resourceName);

		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int length;
		while ((length = inputStream.read(buffer)) != -1) {
			result.write(buffer, 0, length);
		}

		// StandardCharsets.UTF_8.name() > JDK 7

		return result.toString("UTF-8");
	}

}