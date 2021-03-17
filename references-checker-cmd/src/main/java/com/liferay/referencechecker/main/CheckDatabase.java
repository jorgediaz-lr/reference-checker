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

package com.liferay.referencechecker.main;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

import com.liferay.referenceschecker.OutputUtil;
import com.liferay.referenceschecker.ReferencesChecker;
import com.liferay.referenceschecker.ref.MissingReferences;
import com.liferay.referenceschecker.util.JDBCUtil;

import java.io.IOException;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.List;

/**
 * @author Jorge Díaz
 */
public class CheckDatabase {

	public static final String PROGRAM_NAME = "check-database";

	public static void main(String[] args) throws Exception {
		BaseChecker.banner(PROGRAM_NAME);

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

		boolean checkUndefinedTables = commandArguments.checkUndefinedTables();

		BaseChecker baseChecker = BaseChecker.createBaseChecker(
			PROGRAM_NAME, databaseCfg, filenamePrefix, filenameSuffix,
			checkUndefinedTables);

		List<MissingReferences> missingReferenceList = execute(
			baseChecker, missingReferencesLimit);

		boolean dumpCleanupScript = commandArguments.dumpCleanupScript();

		if (dumpCleanupScript) {
			dumpCleanup(baseChecker, missingReferenceList);
		}
	}

	public static class CommandArguments {

		public boolean checkUndefinedTables() {
			return _checkUndefinedTables;
		}

		public boolean dumpCleanupScript() {
			return _dumpCleanupScript;
		}

		public String getDatabaseConfiguration() throws IOException {
			return _databaseConfiguration;
		}

		public int getMissingReferencesLimit() throws IOException {
			try {
				return Integer.valueOf(_missingReferencesLimit);
			}
			catch (Exception e) {
				return -1;
			}
		}

		public String getOutputFilesPrefix() throws IOException {
			return _outputFilesPrefix;
		}

		public String getOutputFilesSuffix() throws IOException {
			return _outputFilesSuffix;
		}

		public boolean isHelp() {
			return _help;
		}

		@Parameter(
			description = "Check undefined tables", hidden = true,
			names = "--checkUndefinedTables"
		)
		private boolean _checkUndefinedTables;

		@Parameter(
			description = "database configuration file.",
			names = {"-d", "--databaseConfiguration"}
		)
		private String _databaseConfiguration;

		@Parameter(
			description = "Dump cleanup script", hidden = true,
			names = "--dumpCleanupScript"
		)
		private boolean _dumpCleanupScript;

		@Parameter(
			description = "Print this message.", help = true,
			names = {"-h", "--help"}
		)
		private boolean _help;

		@Parameter(
			description = "Missing references limit.",
			names = {"-l", "--missingReferencesLimit"}
		)
		private String _missingReferencesLimit;

		@Parameter(
			description = "Output files prefix", names = "--outputFilesPrefix"
		)
		private String _outputFilesPrefix;

		@Parameter(
			description = "Output files suffix", names = "--outputFilesSuffix"
		)
		private String _outputFilesSuffix;

	}

	protected static void dumpCleanup(
			BaseChecker baseChecker,
			List<MissingReferences> missingReferenceList)
		throws IOException, SQLException {

		System.out.println("");
		System.out.println("Executing dump cleanup script...");

		ReferencesChecker referenceChecker = baseChecker.getReferenceChecker();

		List<String> cleanup = referenceChecker.generateCleanupSentences(
			missingReferenceList);

		baseChecker.writeOutput("missing-references_cleanup", "sql", cleanup);
	}

	protected static List<MissingReferences> execute(
			BaseChecker baseChecker, int missingReferencesLimit)
		throws IOException, SQLException {

		System.out.println("");
		System.out.println("Executing dump missing references...");

		long startTime = System.currentTimeMillis();

		List<MissingReferences> missingReferenceList = null;

		ReferencesChecker referenceChecker = baseChecker.getReferenceChecker();

		Connection connection = null;

		try {
			connection = baseChecker.getConnection();

			missingReferenceList = referenceChecker.execute(connection);
		}
		finally {
			JDBCUtil.cleanUp(connection);
		}

		List<String> selectSentences = referenceChecker.generateSelectSentences(
			missingReferenceList);

		baseChecker.writeOutput("missing-references", "sql", selectSentences);

		List<String> outputList = OutputUtil.generateCSVOutputCheckReferences(
			missingReferenceList, missingReferencesLimit);

		baseChecker.writeOutput(
			"missing-references", "csv", startTime, outputList);

		return missingReferenceList;
	}

	protected static CommandArguments getCommandArguments(String[] args)
		throws Exception {

		CommandArguments commandArguments = new CommandArguments();

		JCommander jCommander = new JCommander(commandArguments);

		jCommander.setProgramName(PROGRAM_NAME);

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

	private static void _printHelp(JCommander jCommander) {
		String commandName = jCommander.getParsedCommand();

		if (commandName == null) {
			jCommander.usage();
		}
		else {
			jCommander.usage(commandName);
		}
	}

}