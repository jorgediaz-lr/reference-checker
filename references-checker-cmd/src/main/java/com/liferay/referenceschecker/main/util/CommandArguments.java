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

import com.beust.jcommander.Parameter;

import java.io.IOException;

/**
 * @author Jorge Díaz
 */
public class CommandArguments {

	public boolean checkUndefinedTables() {
		return _checkUndefinedTables;
	}

	public boolean countTables() throws IOException {
		return _countTables;
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

	public boolean showInformation() throws IOException {
		return _info;
	}

	public boolean showMissingReferences() throws IOException {
		return _showMissingReferences;
	}

	public boolean showRelations() throws IOException {
		return _showRelations;
	}

	@Parameter(
		description = "Check undefined tables", hidden = true,
		names = "--checkUndefinedTables"
	)
	private boolean _checkUndefinedTables;


	@Parameter(
		description = "Count all tables.", names = {"-c", "--countTables"}
	)
	private boolean _countTables;

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
		description = "Show Liferay database information.",
		names = {"-i", "--showInformation"}
	)
	private boolean _info;

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

	@Parameter(
		description = "Show missing references.",
		names = {"-m", "--showMissingReferences"}
	)
	private boolean _showMissingReferences;

	@Parameter(
		description = "Show all detected relations.",
		names = {"-r", "--showRelations"}
	)
	private boolean _showRelations;

}