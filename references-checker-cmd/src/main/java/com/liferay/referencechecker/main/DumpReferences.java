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

import com.liferay.referencechecker.main.util.BaseChecker;
import com.liferay.referencechecker.main.util.CommandArguments;
import com.liferay.referenceschecker.OutputUtil;
import com.liferay.referenceschecker.ReferencesChecker;
import com.liferay.referenceschecker.ref.Reference;
import com.liferay.referenceschecker.util.JDBCUtil;

import java.io.IOException;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.Collection;
import java.util.List;

/**
 * @author Jorge Díaz
 */
public class DumpReferences {

	public static final String PROGRAM_NAME = "dump-references";

	public static void main(String[] args) throws Exception {
		BaseChecker.printCmdBanner(PROGRAM_NAME);

		CommandArguments commandArguments =
			CommandArguments.getCommandArguments(PROGRAM_NAME, args);

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

		calculateReferences(baseChecker);
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

}