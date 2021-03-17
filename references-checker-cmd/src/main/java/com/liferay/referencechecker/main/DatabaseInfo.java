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
import com.liferay.referenceschecker.ref.Reference;
import com.liferay.referenceschecker.util.JDBCUtil;

import java.io.IOException;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Jorge Díaz
 */
public class DatabaseInfo {

	public static final String PROGRAM_NAME = "database-info";

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

		boolean checkUndefinedTables = commandArguments.checkUndefinedTables();

		BaseChecker baseChecker = BaseChecker.createBaseChecker(
			PROGRAM_NAME, databaseCfg, filenamePrefix, filenameSuffix,
			checkUndefinedTables);

		dumpDatabaseInfo(baseChecker);

		calculateReferences(baseChecker);

		calculateTableCount(baseChecker);
	}

	public static class CommandArguments {

		public boolean checkUndefinedTables() {
			return _checkUndefinedTables;
		}

		public String getDatabaseConfiguration() throws IOException {
			return _databaseConfiguration;
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
			description = "Print this message.", help = true,
			names = {"-h", "--help"}
		)
		private boolean _help;

		@Parameter(
			description = "Output files prefix", names = "--outputFilesPrefix"
		)
		private String _outputFilesPrefix;

		@Parameter(
			description = "Output files suffix", names = "--outputFilesSuffix"
		)
		private String _outputFilesSuffix;

	}

	protected static void calculateReferences(BaseChecker baseChecker)
		throws IOException, SQLException {

		System.out.println("");
		System.out.println("Executing calculate references...");

		long startTime = System.currentTimeMillis();

		Connection connection = null;

		ReferencesChecker referenceChecker = baseChecker.getReferenceChecker();

		Collection<Reference> references;

		try {
			connection = baseChecker.getConnection();

			references = referenceChecker.calculateReferences(
				connection, false);
		}
		finally {
			JDBCUtil.cleanUp(connection);
		}

		List<String> outputList = OutputUtil.generateCSVOutputMappingList(
			references);

		baseChecker.writeOutput("references", "csv", startTime, outputList);
	}

	protected static void calculateTableCount(BaseChecker baseChecker)
		throws IOException, SQLException {

		System.out.println("");
		System.out.println("Executing count tables...");

		long startTime = System.currentTimeMillis();

		Connection connection = null;

		ReferencesChecker referenceChecker = baseChecker.getReferenceChecker();

		Map<String, Long> mapTableCount;

		try {
			connection = baseChecker.getConnection();

			mapTableCount = referenceChecker.calculateTableCount(connection);
		}
		finally {
			JDBCUtil.cleanUp(connection);
		}

		String[] headers = {"table", "count"};

		List<String> outputList = OutputUtil.generateCSVOutputMap(
			Arrays.asList(headers), mapTableCount);

		baseChecker.writeOutput("tablesCount", "csv", startTime, outputList);
	}

	protected static void dumpDatabaseInfo(BaseChecker baseChecker)
		throws IOException, SQLException {

		System.out.println("");
		System.out.println("Executing dump Liferay database information...");

		long startTime = System.currentTimeMillis();

		Connection connection = null;

		ReferencesChecker referenceChecker = baseChecker.getReferenceChecker();

		List<String> outputList;

		try {
			connection = baseChecker.getConnection();

			outputList = referenceChecker.dumpDatabaseInfo(connection);
		}
		finally {
			JDBCUtil.cleanUp(connection);
		}

		baseChecker.writeOutput("information", "txt", startTime, outputList);
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