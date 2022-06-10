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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

import java.io.IOException;

/**
 * @author Jorge Díaz
 */
public class CommandArguments {

	public static CommandArguments getCommandArguments(
			String programName, String[] args)
		throws Exception {

		CommandArguments commandArguments = new CommandArguments();

		JCommander jCommander = new JCommander(commandArguments);

		jCommander.setProgramName(programName);

		try {
			jCommander.parse(args);

			if (commandArguments.isHelp()) {
				_printHelp(jCommander);

				return null;
			}
		}
		catch (ParameterException parameterException) {
			if (!commandArguments.isHelp()) {
				System.err.println(parameterException.getMessage());
			}

			_printHelp(jCommander);

			return null;
		}

		return commandArguments;
	}

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

	private static void _printHelp(JCommander jCommander) {
		String commandName = jCommander.getParsedCommand();

		if (commandName == null) {
			jCommander.usage();
		}
		else {
			jCommander.usage(commandName);
		}
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