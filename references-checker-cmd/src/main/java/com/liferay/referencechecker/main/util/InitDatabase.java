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

import com.liferay.referenceschecker.util.JDBCUtil;
import com.liferay.referenceschecker.util.SQLUtil;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.io.IOException;

import java.sql.Connection;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import jline.console.ConsoleReader;

/**
 * @author Jorge Díaz
 */
public class InitDatabase {

	public InitDatabase() throws IOException {
	}

	public DataSource connectToDatabase(String databaseCfg) throws Exception {
		File databasePropertiesFile = new File(databaseCfg);

		Properties databaseProperties = new Properties();

		if (databasePropertiesFile.exists()) {
			System.out.println(
				"Loading database configuration from " +
					databasePropertiesFile + " configuration file");

			try {
				databaseProperties.load(databasePropertiesFile);
			}
			catch (IOException ioe) {
				System.err.println("Unable to load " + databasePropertiesFile);
			}
		}

		String value = databaseProperties.getProperty(
			"jdbc.default.driverClassName");

		if ((value == null) || value.isEmpty()) {
			databaseProperties = fillDatabaseProperties();
		}

		String driverClassName = databaseProperties.getProperty(
			"jdbc.default.driverClassName");
		String password = databaseProperties.getProperty(
			"jdbc.default.password");
		String url = databaseProperties.getProperty("jdbc.default.url");
		String username = databaseProperties.getProperty(
			"jdbc.default.username");

		try {
			Class.forName(driverClassName);
		}
		catch (Throwable t) {
			System.err.println(
				"Unable to load jdbc driver: " + driverClassName);
			System.err.println(
				"Please copy jdbc jar of your database to classpath");

			throw new Exception(t);
		}

		HikariConfig hikariConfig = new HikariConfig();

		hikariConfig.setDriverClassName(driverClassName);
		hikariConfig.setJdbcUrl(url);
		hikariConfig.setUsername(username);
		hikariConfig.setPassword(password);

		DataSource dataSource = new HikariDataSource(hikariConfig);

		Connection testConnection = dataSource.getConnection();

		JDBCUtil.cleanUp(testConnection);

		try {
			databaseProperties.store(databasePropertiesFile);
		}
		catch (IOException ioe) {
			System.out.println(
				"Error writting to " +
					databasePropertiesFile.getAbsolutePath());

			throw ioe;
		}

		return dataSource;
	}

	protected Properties fillDatabaseProperties() throws IOException {
		String response = null;

		Database dataSource = null;

		while (dataSource == null) {
			System.out.print("[ ");

			for (String database : _databases.keySet()) {
				System.out.print(database + " ");
			}

			System.out.println("]");

			System.out.println("Please enter your database (mysql): ");

			response = _consoleReader.readLine();

			if (response.isEmpty()) {
				response = "mysql";
			}

			dataSource = _databases.get(response);

			if (dataSource == null) {
				System.err.println(response + " is an unsupported database.");
			}
		}

		System.out.println(
			"Please enter your database JDBC driver class name (" +
				dataSource.getClassName() + "): ");

		response = _consoleReader.readLine();

		if (!response.isEmpty()) {
			dataSource.setClassName(response);
		}

		System.out.println(
			"Please enter your database JDBC driver protocol (" +
				dataSource.getProtocol() + "): ");

		response = _consoleReader.readLine();

		if (!response.isEmpty()) {
			dataSource.setProtocol(response);
		}

		System.out.println(
			"Please enter your database host (" + dataSource.getHost() + "): ");

		response = _consoleReader.readLine();

		if (!response.isEmpty()) {
			dataSource.setHost(response);
		}

		String port = null;

		if (dataSource.getPort() > 0) {
			port = String.valueOf(dataSource.getPort());
		}
		else {
			port = "none";
		}

		System.out.println("Please enter your database port (" + port + "): ");

		response = _consoleReader.readLine();

		if (!response.isEmpty()) {
			if (response.equals("none")) {
				dataSource.setPort(0);
			}
			else {
				try {
					dataSource.setPort(Integer.parseInt(response));
				}
				catch (NumberFormatException nfe) {
				}
			}
		}

		System.out.println(
			"Please enter your database name (" + dataSource.getDatabaseName() +
				"): ");

		response = _consoleReader.readLine();

		if (!response.isEmpty()) {
			dataSource.setDatabaseName(response);
		}

		System.out.println("Please enter your database username: ");

		String username = _consoleReader.readLine();

		System.out.println("Please enter your database password: ");

		String password = _consoleReader.readLine('*');

		Properties databaseProperties = new Properties();

		databaseProperties.setProperty(
			"jdbc.default.driverClassName", dataSource.getClassName());
		databaseProperties.setProperty("jdbc.default.password", password);
		databaseProperties.setProperty("jdbc.default.url", dataSource.getURL());
		databaseProperties.setProperty("jdbc.default.username", username);

		return databaseProperties;
	}

	private static final Map<String, Database> _databases =
		new HashMap<String, Database>() {
			{
				put(SQLUtil.TYPE_DB2, Database.getDB2Database());
				put(SQLUtil.TYPE_MARIADB, Database.getMariaDBDatabase());
				put(SQLUtil.TYPE_MYSQL, Database.getMySQLDatabase());
				put(SQLUtil.TYPE_ORACLE, Database.getOracleDataSource());
				put(SQLUtil.TYPE_POSTGRESQL, Database.getPostgreSQLDatabase());
				put(SQLUtil.TYPE_SQLSERVER, Database.getSQLServerDatabase());
				put(SQLUtil.TYPE_SYBASE, Database.getSybaseDatabase());
			}
		};

	private final ConsoleReader _consoleReader = new ConsoleReader();

}