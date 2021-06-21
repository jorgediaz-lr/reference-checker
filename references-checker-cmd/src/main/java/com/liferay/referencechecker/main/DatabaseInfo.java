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

import com.liferay.referencechecker.OutputUtil;
import com.liferay.referencechecker.ReferenceChecker;
import com.liferay.referencechecker.main.util.BaseChecker;
import com.liferay.referencechecker.main.util.CommandArguments;
import com.liferay.referenceschecker.util.JDBCUtil;

import java.io.IOException;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Jorge Díaz
 */
public class DatabaseInfo {

	public static final String PROGRAM_NAME = "database-info";

	public static void main(String[] args) throws Exception {
		BaseChecker.printCmdBanner(PROGRAM_NAME);

		CommandArguments commandArguments =
			CommandArguments.getCommandArguments(PROGRAM_NAME, args);

		if (commandArguments == null) {
			System.exit(-1);

			return;
		}

		String databaseCfg = commandArguments.getDatabaseConfiguration();
		String fileNamePrefix = commandArguments.getOutputFilesPrefix();
		String fileNameSuffix = commandArguments.getOutputFilesSuffix();

		boolean checkUndefinedTables = commandArguments.checkUndefinedTables();

		BaseChecker baseChecker = BaseChecker.createBaseChecker(
			PROGRAM_NAME, databaseCfg, fileNamePrefix, fileNameSuffix,
			checkUndefinedTables);

		dumpDatabaseInfo(baseChecker);

		calculateTableCount(baseChecker);
	}

	protected static void calculateTableCount(BaseChecker baseChecker)
		throws IOException, SQLException {

		System.out.println("");
		System.out.println("Executing count tables...");

		long startTime = System.currentTimeMillis();

		Connection connection = null;

		ReferenceChecker referenceChecker = baseChecker.getReferenceChecker();

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

		ReferenceChecker referenceChecker = baseChecker.getReferenceChecker();

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

}