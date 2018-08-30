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

import com.liferay.referenceschecker.OutputUtil;
import com.liferay.referenceschecker.main.util.CommandArguments;
import com.liferay.referenceschecker.main.util.InitDatabase;
import com.liferay.referenceschecker.main.util.TeePrintStream;
import com.liferay.referenceschecker.ref.MissingReferences;
import com.liferay.referenceschecker.ref.Reference;
import com.liferay.referenceschecker.util.JDBCUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import java.net.URL;

import java.security.CodeSource;
import java.security.ProtectionDomain;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;

/**
 * @author Jorge Díaz
 */
public class ReferencesChecker {

	public static void main(String[] args) throws Exception {
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
			databaseCfg = "database.properties";
		}

		if (filenamePrefix == null) {
			filenamePrefix = StringUtils.EMPTY;
		}

		if (filenameSuffix == null) {
			filenameSuffix = "_" + System.currentTimeMillis();
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

			referencesChecker.setCheckUndefinedTables(checkUndefinedTables);
			referencesChecker.initModelUtil(connection);
			referencesChecker.initTableUtil(connection);
		}
		finally {
			JDBCUtil.cleanUp(connection);
		}
	}

	protected static CommandArguments getCommandArguments(String[] args)
		throws Exception {

		CommandArguments commandArguments = new CommandArguments();

		JCommander jCommander = new JCommander(commandArguments);

		File jarFile = _getJarFile();

		if (jarFile.isFile()) {
			jCommander.setProgramName("java -jar " + jarFile.getName());
		}
		else {
			jCommander.setProgramName(ReferencesChecker.class.getName());
		}

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

		String[] headers =
			{"origin table", "attributes", "destination table", "attributes"};

		List<String> outputList = OutputUtil.generateCSVOutputMappingList(
			Arrays.asList(headers), references);

		String outputFile = _getOutputFileName("references", "csv");

		PrintWriter writer = new PrintWriter(outputFile, "UTF-8");

		for (String line : outputList) {
			writer.println(line);
		}

		writer.close();

		long endTime = System.currentTimeMillis();

		System.out.println("");
		System.out.println("Total time: " + (endTime - startTime) + " ms");
		System.out.println("Output was written to file: " + outputFile);
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

		String outputFile = _getOutputFileName("tablesCount", "csv");

		PrintWriter writer = new PrintWriter(outputFile, "UTF-8");

		for (String line : outputList) {
			writer.println(line);
		}

		writer.close();

		long endTime = System.currentTimeMillis();

		System.out.println("");
		System.out.println("Total time: " + (endTime - startTime) + " ms");
		System.out.println("Output was written to file: " + outputFile);
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

		String outputFile = _getOutputFileName("information", "txt");

		PrintWriter writer = new PrintWriter(outputFile, "UTF-8");

		for (String line : outputList) {
			writer.println(line);
		}

		writer.close();

		long endTime = System.currentTimeMillis();

		System.out.println("");
		System.out.println("Total time: " + (endTime - startTime) + " ms");
		System.out.println("Output was written to file: " + outputFile);
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

		String[] headers = {
			"origin table", "attributes", "destination table", "attributes",
			"#", "missing references"
		};

		List<String> outputList = OutputUtil.generateCSVOutputCheckReferences(
			Arrays.asList(headers), listMissingReferences,
			missingReferencesLimit);

		String outputFile = _getOutputFileName("missing-references", "csv");

		PrintWriter writer = new PrintWriter(outputFile, "UTF-8");

		for (String line : outputList) {
			writer.println(line);
		}

		writer.close();

		long endTime = System.currentTimeMillis();

		System.out.println("");
		System.out.println("Total time: " + (endTime - startTime) + " ms");
		System.out.println("Output was written to file: " + outputFile);
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

	private static void _printHelp(JCommander jCommander) {
		String commandName = jCommander.getParsedCommand();

		if (commandName == null) {
			jCommander.usage();
		}
		else {
			jCommander.usage(commandName);
		}
	}

	private String _getOutputFileName(String name, String extension) {
		return filenamePrefix + name + filenameSuffix + "." + extension;
	}

}