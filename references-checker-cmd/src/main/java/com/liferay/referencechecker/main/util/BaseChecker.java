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

package com.liferay.referencechecker.main.util;

import com.fasterxml.jackson.core.JsonProcessingException;

import com.liferay.referencechecker.OutputUtil;
import com.liferay.referencechecker.ReferenceChecker;
import com.liferay.referencechecker.util.JDBCUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import java.net.URL;

import java.security.CodeSource;
import java.security.ProtectionDomain;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Jorge Díaz
 */
public class BaseChecker {

	public static BaseChecker createBaseChecker(
			String programName, String databaseCfg, String fileNamePrefix,
			String fileNameSuffix, boolean checkUndefinedTables)
		throws Exception, FileNotFoundException {

		if (databaseCfg == null) {
			String configFolder = getConfigFolder();

			databaseCfg = configFolder + "database.properties";
		}

		if (fileNamePrefix == null) {
			fileNamePrefix = StringUtils.EMPTY;
		}

		if (fileNameSuffix == null) {
			DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");

			fileNameSuffix = "_" + dateFormat.format(new Date());
		}

		File logFile = new File(
			fileNamePrefix + programName + fileNameSuffix + ".log");

		System.setOut(
			new TeePrintStream(new FileOutputStream(logFile), System.out));

		try {
			System.out.println("");
			System.out.println("Loading database model...");
			System.out.println("");

			long startTime = System.currentTimeMillis();

			InitDatabase initDB = new InitDatabase();

			DataSource dataSource = initDB.connectToDatabase(databaseCfg);

			BaseChecker baseChecker = new BaseChecker(
				dataSource, fileNamePrefix, fileNameSuffix,
				checkUndefinedTables);

			long endTime = System.currentTimeMillis();

			System.out.println("");
			System.out.println("Total time: " + (endTime - startTime) + " ms");

			return baseChecker;
		}
		catch (Throwable t) {
			t.printStackTrace(System.out);

			System.exit(-1);

			return null;
		}
	}

	public static void printCmdBanner(String programName) {
		System.out.println("========");
		System.out.println(
			"WARNING: This tool is not officially supported by Liferay Inc. " +
				"or its affiliates. Use it under your responsibility: false " +
					"positives can be returned. If you have any question, " +
						"contact Jorge Diaz");
		System.out.println("========");
		System.out.println("");
		System.out.println(programName + " version " + _getJarVersion());
		System.out.println("");
	}

	public Connection getConnection() throws SQLException {
		return dataSource.getConnection();
	}

	public ReferenceChecker getReferenceChecker() {
		return referenceChecker;
	}

	public void writeOutput(String name, String format, List<String> outputList)
		throws IOException {

		writeOutput(name, format, null, outputList);
	}

	public void writeOutput(
			String name, String format, Long startTime, List<String> outputList)
		throws IOException {

		File outputFile = _getOutputFile(name, format);

		PrintWriter writer = new PrintWriter(outputFile, "UTF-8");

		for (String line : outputList) {
			writer.println(line);
		}

		writer.close();

		String outputFileName = outputFile.getName();

		if (Objects.equals(format, "csv")) {
			File htmlFile = _getOutputFile(name, "html");

			try {
				File csvFile = outputFile;

				String footer = "Version " + _getJarVersion();

				writeOutputHtml(csvFile, htmlFile, footer);

				outputFileName = outputFileName + " and " + htmlFile.getName();
			}
			catch (IOException ioException) {
				ioException.printStackTrace(System.out);
			}
		}

		System.out.println("");
		System.out.println("Output was written to file: " + outputFileName);

		if (startTime != null) {
			long endTime = System.currentTimeMillis();

			System.out.println("Total time: " + (endTime - startTime) + " ms");
		}
	}

	public void writeOutputHtml(
			File sourceCsvFile, File htmlFile, String footer)
		throws FileNotFoundException, IOException, JsonProcessingException,
			   UnsupportedEncodingException {

		String sourceCsvContent = FileUtils.readFileToString(
			sourceCsvFile, "UTF-8");

		List<Map<?, ?>> data = OutputUtil.csvToMapList(sourceCsvContent);

		String databaseUrl = _getDatabaseURL(dataSource);

		String jsonData = OutputUtil.mapListToJSON(data);

		String template = getResource("show-table_template.html");
		String title = StringUtils.removeEnd(htmlFile.getName(), ".html");

		template = template.replace("${TITLE}", title);
		template = template.replace("${DATABASE_URL}", databaseUrl);
		template = template.replace(
			"${CSV_FILE_NAME}", sourceCsvFile.getName());
		template = template.replace("${JSON_DATA}", jsonData);
		template = template.replace("${FOOTER}", footer);

		PrintWriter writer = new PrintWriter(htmlFile, "UTF-8");

		writer.print(template);
		writer.close();
	}

	protected static String getConfigFolder() throws Exception {
		File jarFile = _getJarFile();

		if (jarFile == null) {
			return StringUtils.EMPTY;
		}

		File libFolder = jarFile.getParentFile();

		String libFolderPath = libFolder.getAbsolutePath();

		if (!libFolderPath.endsWith("lib")) {
			return StringUtils.EMPTY;
		}

		return libFolder.getParent() + "/config/";
	}

	protected BaseChecker(
			DataSource dataSource, String fileNamePrefix, String fileNameSuffix,
			boolean checkUndefinedTables)
		throws Exception {

		this.dataSource = dataSource;
		this.fileNamePrefix = fileNamePrefix;
		this.fileNameSuffix = fileNameSuffix;

		Connection connection = null;

		try {
			connection = dataSource.getConnection();

			referenceChecker = new ReferenceChecker(connection);

			if (referenceChecker.getConfiguration() == null) {
				throw new RuntimeException("Error loading configuration");
			}

			referenceChecker.setCheckUndefinedTables(checkUndefinedTables);
			referenceChecker.initModelUtil(connection);
			referenceChecker.initTableUtil(connection);
		}
		finally {
			JDBCUtil.cleanUp(connection);
		}
	}

	protected String getResource(String resourceName)
		throws IOException, UnsupportedEncodingException {

		ClassLoader classLoader = getClass().getClassLoader();

		InputStream inputStream = classLoader.getResourceAsStream(resourceName);

		return IOUtils.toString(inputStream, "UTF-8");
	}

	protected DataSource dataSource;
	protected String fileNamePrefix;
	protected String fileNameSuffix;
	protected ReferenceChecker referenceChecker;

	private static File _getJarFile() throws Exception {
		ProtectionDomain protectionDomain =
			BaseChecker.class.getProtectionDomain();

		CodeSource codeSource = protectionDomain.getCodeSource();

		URL url = codeSource.getLocation();

		return new File(url.toURI());
	}

	private static String _getJarVersion() {
		Package p = BaseChecker.class.getPackage();

		return p.getImplementationVersion();
	}

	private String _getDatabaseURL(DataSource dataSource) {
		String databaseUrl = null;

		Connection connection = null;

		try {
			connection = dataSource.getConnection();

			DatabaseMetaData databaseMetaData = connection.getMetaData();

			databaseUrl = databaseMetaData.getURL();
		}
		catch (SQLException sqlException) {
			sqlException.printStackTrace();
		}
		finally {
			JDBCUtil.cleanUp(connection);
		}

		if (databaseUrl == null) {
			return StringUtils.EMPTY;
		}

		return databaseUrl;
	}

	private File _getOutputFile(String name, String extension) {
		String fileName = fileNamePrefix + name + fileNameSuffix;

		if (StringUtils.isNotEmpty(extension)) {
			fileName = fileName + "." + extension;
		}

		return new File(fileName);
	}

}