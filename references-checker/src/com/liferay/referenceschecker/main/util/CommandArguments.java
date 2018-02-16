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

package com.liferay.referenceschecker.main.util;

import com.beust.jcommander.Parameter;

import java.io.IOException;

/**
 * @author Jorge Díaz
 */
public class CommandArguments {

	public boolean countTables() throws IOException {
		return _countTables;
	}

	public boolean isHelp() {
		return _help;
	}

	public boolean showMissingReferences() throws IOException {
		return _showMissingReferences;
	}

	public boolean showRelations() throws IOException {
		return _showRelations;
	}

	@Parameter(
		description = "Count all tables.",
		names = {"-c", "--countTables"}
	)
	private boolean _countTables;

	@Parameter(
		description = "Print this message.", help = true,
		names = {"-h", "--help"}
	)
	private boolean _help;

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