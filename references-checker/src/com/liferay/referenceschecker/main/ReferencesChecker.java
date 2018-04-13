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

package com.liferay.referenceschecker.main;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import com.liferay.portal.kernel.util.StringPool;
import com.liferay.referenceschecker.main.util.CommandArguments;
import com.liferay.referenceschecker.main.util.InitPortal;
import com.liferay.referenceschecker.main.util.TeePrintStream;
import com.liferay.referenceschecker.output.ReferencesCheckerOutput;
import com.liferay.referenceschecker.ref.MissingReferences;
import com.liferay.referenceschecker.ref.Reference;
import com.liferay.referenceschecker.util.SQLUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import java.net.URL;

import java.security.CodeSource;
import java.security.ProtectionDomain;

import java.sql.SQLException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
			filenamePrefix = StringPool.BLANK;
		}

		if (filenameSuffix == null) {
			filenameSuffix = "_" + System.currentTimeMillis();
		}

		File logFile = new File(
			filenamePrefix + "references-checker" + filenameSuffix + ".log");

		System.setOut(
			new TeePrintStream(new FileOutputStream(logFile), System.out));

		InitPortal initPortal = new InitPortal();

		initPortal.initLiferayClasses();

		initPortal.connectToDatabase(databaseCfg);

		ReferencesChecker referenceChecker;

		try {
			referenceChecker = new ReferencesChecker(
				filenamePrefix, filenameSuffix, missingReferencesLimit);
		}
		catch (Throwable t) {
			t.printStackTrace(System.out);

			System.exit(-1);

			return;
		}

		if (commandArguments.showInformation()) {
			referenceChecker.dumpDatabaseInfo();
		}

		if (commandArguments.showRelations()) {
			referenceChecker.calculateReferences();
		}

		if (commandArguments.countTables()) {
			referenceChecker.calculateTableCount();
		}

		if (commandArguments.showMissingReferences() ||
			(!commandArguments.showInformation() &&
			 !commandArguments.showRelations() &&
			 !commandArguments.countTables())) {

			referenceChecker.execute();
		}
	}

	public ReferencesChecker(
			String filenamePrefix, String filenameSuffix,
			int missingReferencesLimit)
		throws Exception {

		this.filenamePrefix = filenamePrefix;
		this.filenameSuffix = filenameSuffix;
		this.missingReferencesLimit = missingReferencesLimit;

		String dbType = SQLUtil.getDBType();

		this.referencesChecker =
			new com.liferay.referenceschecker.ReferencesChecker(
				dbType, null, false, true);
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

		Map<Reference, Reference> references =
			referencesChecker.calculateReferences();

		String[] headers = new String[] {
			"origin table", "attributes", "destination table", "attributes"};

		List<String> outputList =
			ReferencesCheckerOutput.generateCSVOutputMappingList(
					Arrays.asList(headers), references);

		String outputFile = _getOutputFileName("references", "csv");

		PrintWriter writer = new PrintWriter(outputFile, "UTF-8");

		for (String line : outputList) {
			writer.println(line);
		}

		writer.close();

		long endTime = System.currentTimeMillis();

		System.out.println("");
		System.out.println("Total time: " + (endTime-startTime) + " ms");
		System.out.println("Output was written to file: " + outputFile);
	}

	protected void calculateTableCount() throws IOException, SQLException {

		long startTime = System.currentTimeMillis();

		Map<String, Long> mapTableCount =
			referencesChecker.calculateTableCount();

		String[] headers = new String[] {"table", "count"};

		List<String> outputList =
			ReferencesCheckerOutput.generateCSVOutputMap(
					Arrays.asList(headers), mapTableCount);

		String outputFile = _getOutputFileName("tablesCount", "csv");

		PrintWriter writer = new PrintWriter(outputFile, "UTF-8");

		for (String line : outputList) {
			writer.println(line);
		}

		writer.close();

		long endTime = System.currentTimeMillis();

		System.out.println("");
		System.out.println("Total time: " + (endTime-startTime) + " ms");
		System.out.println("Output was written to file: " + outputFile);
	}

	protected void dumpDatabaseInfo() throws IOException, SQLException {

		long startTime = System.currentTimeMillis();

		List<String> outputList = referencesChecker.dumpDatabaseInfo();

		String outputFile = _getOutputFileName("information", "txt");

		PrintWriter writer = new PrintWriter(outputFile, "UTF-8");

		for (String line : outputList) {
			writer.println(line);
		}

		writer.close();

		long endTime = System.currentTimeMillis();

		System.out.println("");
		System.out.println("Total time: " + (endTime-startTime) + " ms");
		System.out.println("Output was written to file: " + outputFile);
	}

	protected void execute() throws IOException, SQLException {

		long startTime = System.currentTimeMillis();

		List<MissingReferences> listMissingReferences =
			referencesChecker.execute();

		String[] headers = new String[] {
			"origin table", "attributes", "destination table", "attributes",
			"#", "missing references"};

		List<String> outputList =
			ReferencesCheckerOutput.generateCSVOutputCheckReferences(
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
		System.out.println("Total time: " + (endTime-startTime) + " ms");
		System.out.println("Output was written to file: " + outputFile);
	}

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

	private String filenamePrefix;
	private String filenameSuffix;
	private int missingReferencesLimit;
	private com.liferay.referenceschecker.ReferencesChecker referencesChecker;

}