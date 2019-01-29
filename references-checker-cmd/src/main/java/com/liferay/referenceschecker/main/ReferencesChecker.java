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

package com.liferay.referenceschecker.main;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import com.fasterxml.jackson.core.JsonProcessingException;

import com.liferay.referenceschecker.OutputUtil;
import com.liferay.referenceschecker.main.util.CommandArguments;
import com.liferay.referenceschecker.main.util.InitDatabase;
import com.liferay.referenceschecker.main.util.TeePrintStream;
import com.liferay.referenceschecker.ref.MissingReferences;
import com.liferay.referenceschecker.ref.Reference;
import com.liferay.referenceschecker.util.JDBCUtil;

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
import java.sql.SQLException;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Jorge Díaz
 */
public class ReferencesChecker {

	public static void main(String[] args) throws Exception {
		System.out.println("========");
		System.out.println(
			"WARNING: This tool is for Liferay internal use only and it " +
				"should not be distributed to customers or external users. " +
					"False positives can be returned, if you have any " +
						"question, contact with Jorge Diaz");
		System.out.println("========");
		System.out.println("");
		System.out.println("Reference checker version " + _getJarVersion());
		System.out.println("");

		CommandArguments commandArguments = getCommandArguments(args);

		if (commandArguments == null) {
			System.exit(-1);

			return;
		}

		String databaseCfg = commandArguments.getDatabaseConfiguration();
		String filenamePrefix = commandArguments.getOutputFilesPrefix();
		String filenameSuffix = commandArguments.getOutputFilesSuffix();

		int missingReferencesLimit =
			commandArguments.getMissingReferencesLimit();

		if (missingReferencesLimit == -1) {
			missingReferencesLimit = 50;
		}

		if (databaseCfg == null) {
			String configFolder = getConfigFolder();

			databaseCfg = configFolder + "database.properties";
		}

		if (filenamePrefix == null) {
			filenamePrefix = StringUtils.EMPTY;
		}

		if (filenameSuffix == null) {
			DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
			Date date = new Date();

			filenameSuffix = "_" + dateFormat.format(date);
		}

		File logFile = new File(
			filenamePrefix + "references-checker" + filenameSuffix + ".log");

		System.setOut(
			new TeePrintStream(new FileOutputStream(logFile), System.out));

		ReferencesChecker referencesChecker;

		try {
			InitDatabase initDB = new InitDatabase();

			DataSource dataSource = initDB.connectToDatabase(databaseCfg);

			boolean checkUndefinedTables =
				commandArguments.checkUndefinedTables();

			referencesChecker = new ReferencesChecker(
				dataSource, filenamePrefix, filenameSuffix,
				missingReferencesLimit, checkUndefinedTables);
		}
		catch (Throwable t) {
			t.printStackTrace(System.out);

			System.exit(-1);

			return;
		}

		if (commandArguments.showInformation()) {
			referencesChecker.dumpDatabaseInfo();
		}

		if (commandArguments.showRelations()) {
			referencesChecker.calculateReferences();
		}

		if (commandArguments.countTables()) {
			referencesChecker.calculateTableCount();
		}

		if (commandArguments.showMissingReferences() ||
			(!commandArguments.showInformation() &&
			 !commandArguments.showRelations() &&
			 !commandArguments.countTables())) {

			referencesChecker.execute();
		}
	}

	public ReferencesChecker(
			DataSource dataSource, String filenamePrefix, String filenameSuffix,
			int missingReferencesLimit, boolean checkUndefinedTables)
		throws Exception {

		this.dataSource = dataSource;
		this.filenamePrefix = filenamePrefix;
		this.filenameSuffix = filenameSuffix;
		this.missingReferencesLimit = missingReferencesLimit;

		Connection connection = null;

		try {
			connection = dataSource.getConnection();

			referencesChecker =
				new com.liferay.referenceschecker.ReferencesChecker(connection);

			if (referencesChecker.getConfiguration() == null) {
				throw new RuntimeException("Error loading configuration");
			}

			referencesChecker.setCheckUndefinedTables(checkUndefinedTables);
			referencesChecker.initModelUtil(connection);
			referencesChecker.initTableUtil(connection);
		}
		finally {
			JDBCUtil.cleanUp(connection);
		}
	}

	public void writeOutputHtml(
			File sourceCsvFile, File htmlFile, String footer)
		throws FileNotFoundException, IOException, JsonProcessingException,
			   UnsupportedEncodingException {

		String sourceCsvContent = FileUtils.readFileToString(
			sourceCsvFile, "UTF-8");

		List<Map<?, ?>> data = OutputUtil.csvToMapList(sourceCsvContent);

		String jsonData = OutputUtil.mapListToJSON(data);

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

	protected static CommandArguments getCommandArguments(String[] args)
		throws Exception {

		CommandArguments commandArguments = new CommandArguments();

		JCommander jCommander = new JCommander(commandArguments);

		jCommander.setProgramName("referenceschecker");

		try {
			jCommander.parse(args);

			if (commandArguments.isHelp()) {
				_printHelp(jCommander);

				return null;
			}
		}
		catch (ParameterException pe) {
			if (!commandArguments.isHelp()) {
				System.err.println(pe.getMessage());
			}

			_printHelp(jCommander);

			return null;
		}

		return commandArguments;
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

	protected void calculateReferences() throws IOException, SQLException {
		long startTime = System.currentTimeMillis();

		Connection connection = null;

		Collection<Reference> references;

		try {
			connection = dataSource.getConnection();

			references = referencesChecker.calculateReferences(
				connection, false);
		}
		finally {
			JDBCUtil.cleanUp(connection);
		}

		List<String> outputList = OutputUtil.generateCSVOutputMappingList(
			references);

		writeOutput("references", "csv", startTime, outputList);
	}

	protected void calculateTableCount() throws IOException, SQLException {
		long startTime = System.currentTimeMillis();

		Connection connection = null;

		Map<String, Long> mapTableCount;

		try {
			connection = dataSource.getConnection();

			mapTableCount = referencesChecker.calculateTableCount(connection);
		}
		finally {
			JDBCUtil.cleanUp(connection);
		}

		String[] headers = {"table", "count"};

		List<String> outputList = OutputUtil.generateCSVOutputMap(
			Arrays.asList(headers), mapTableCount);

		writeOutput("tablesCount", "csv", startTime, outputList);
	}

	protected void dumpDatabaseInfo() throws IOException, SQLException {
		long startTime = System.currentTimeMillis();

		Connection connection = null;

		List<String> outputList;

		try {
			connection = dataSource.getConnection();

			outputList = referencesChecker.dumpDatabaseInfo(connection);
		}
		finally {
			JDBCUtil.cleanUp(connection);
		}

		writeOutput("information", "txt", startTime, outputList);
	}

	protected void execute() throws IOException, SQLException {
		long startTime = System.currentTimeMillis();

		List<MissingReferences> listMissingReferences = null;

		Connection connection = null;

		try {
			connection = dataSource.getConnection();

			listMissingReferences = referencesChecker.execute(connection);
		}
		finally {
			JDBCUtil.cleanUp(connection);
		}

		List<String> outputList = OutputUtil.generateCSVOutputCheckReferences(
			listMissingReferences, missingReferencesLimit);

		writeOutput("missing-references", "csv", startTime, outputList);
	}

	protected String getResource(String resourceName)
		throws IOException, UnsupportedEncodingException {

		ClassLoader classLoader = getClass().getClassLoader();

		InputStream inputStream = classLoader.getResourceAsStream(resourceName);

		return IOUtils.toString(inputStream, "UTF-8");
	}

	protected void writeOutput(
			String name, String format, long startTime, List<String> outputList)
		throws IOException {

		File outputFile = _getOutputFile(name, format);

		PrintWriter writer = new PrintWriter(outputFile, "UTF-8");

		for (String line : outputList) {
			writer.println(line);
		}

		writer.close();

		String outputFileName = outputFile.getName();

		if ("csv".equals(format)) {
			File htmlFile = _getOutputFile(name, "html");

			try {
				File csvFile = outputFile;

				String footer = "Version " + _getJarVersion();

				writeOutputHtml(csvFile, htmlFile, footer);

				outputFileName = outputFileName + " and " + htmlFile.getName();
			}
			catch (IOException ioe) {
				ioe.printStackTrace(System.out);
			}
		}

		long endTime = System.currentTimeMillis();

		System.out.println("");
		System.out.println("Total time: " + (endTime - startTime) + " ms");
		System.out.println("Output was written to file: " + outputFileName);
	}

	protected DataSource dataSource;
	protected String filenamePrefix;
	protected String filenameSuffix;
	protected int missingReferencesLimit;
	protected com.liferay.referenceschecker.ReferencesChecker referencesChecker;

	private static File _getJarFile() throws Exception {
		ProtectionDomain protectionDomain =
			ReferencesChecker.class.getProtectionDomain();

		CodeSource codeSource = protectionDomain.getCodeSource();

		URL url = codeSource.getLocation();

		return new File(url.toURI());
	}

	private static String _getJarVersion() {
		Package p = ReferencesChecker.class.getPackage();

		return p.getImplementationVersion();
	}

	private static void _printHelp(JCommander jCommander) {
		String commandName = jCommander.getParsedCommand();

		if (commandName == null) {
			jCommander.usage();
		}
		else {
			jCommander.usage(commandName);
		}
	}

	private File _getOutputFile(String name, String extension) {
		String fileName = filenamePrefix + name + filenameSuffix;

		if (StringUtils.isNotEmpty(extension)) {
			fileName = fileName + "." + extension;
		}

		return new File(fileName);
	}

}