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

import com.liferay.portal.kernel.dao.jdbc.DataSourceFactory;
import com.liferay.portal.kernel.dao.jdbc.DataSourceFactoryUtil;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactory;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.FastDateFormatFactory;
import com.liferay.portal.kernel.util.FastDateFormatFactoryUtil;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.Html;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.InfrastructureUtil;
import com.liferay.portal.kernel.util.PortalClassLoaderUtil;
import com.liferay.portal.kernel.util.Props;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.referenceschecker.main.util.CommandArguments;
import com.liferay.referenceschecker.main.util.Database;
import com.liferay.referenceschecker.main.util.Properties;
import com.liferay.referenceschecker.main.util.TeePrintStream;
import com.liferay.referenceschecker.output.ReferencesCheckerOutput;
import com.liferay.referenceschecker.ref.MissingReferences;
import com.liferay.referenceschecker.ref.Reference;
import com.liferay.referenceschecker.util.ReflectionUtil;
import com.liferay.referenceschecker.util.SQLUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import java.lang.reflect.Method;

import java.net.URL;

import java.security.CodeSource;
import java.security.ProtectionDomain;

import java.sql.SQLException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import jline.console.ConsoleReader;
public class ReferencesChecker {

	public static void main(String[] args) throws Exception {
		CommandArguments commandArguments = getCommandArguments(args);

		if (commandArguments == null) {
			System.exit(-1);
		}

		String databaseCfg = commandArguments.getDatabaseConfiguration();
		String filenamePrefix = commandArguments.getOutputFilesPrefix();
		String filenameSuffix = commandArguments.getOutputFilesSuffix();

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

		ReferencesChecker referenceChecker = new ReferencesChecker(
			filenamePrefix, filenameSuffix);

		referenceChecker.connectToDatabase(databaseCfg);

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

	public ReferencesChecker(String filenamePrefix, String filenameSuffix)
		throws Exception {

		this.filenamePrefix = filenamePrefix;
		this.filenameSuffix = filenameSuffix;

		Class<?> clazz = getClass();

		ClassLoader classLoader = clazz.getClassLoader();

		_initUtil(classLoader);
	}

	public void connectToDatabase(String databaseCfg) throws Exception {
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

		_initDatabase(
			driverClassName, url, username, password, StringPool.BLANK);

		databaseProperties.store(databasePropertiesFile);
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

		String dbType = SQLUtil.getDBType();

		com.liferay.referenceschecker.ReferencesChecker referencesChecker =
			new com.liferay.referenceschecker.ReferencesChecker(
				dbType, null, false, true);

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

		String dbType = SQLUtil.getDBType();

		com.liferay.referenceschecker.ReferencesChecker referencesChecker =
			new com.liferay.referenceschecker.ReferencesChecker(
				dbType, null, false, true);

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

		String dbType = SQLUtil.getDBType();

		com.liferay.referenceschecker.ReferencesChecker referencesChecker =
			new com.liferay.referenceschecker.ReferencesChecker(
				dbType, null, false, true);

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

		String dbType = SQLUtil.getDBType();

		com.liferay.referenceschecker.ReferencesChecker referencesChecker =
			new com.liferay.referenceschecker.ReferencesChecker(
				dbType, null, true, true);

		List<MissingReferences> listMissingReferences =
			referencesChecker.execute();

		String[] headers = new String[] {
			"origin table", "attributes", "destination table", "attributes",
			"missing references"};

		List<String> outputList =
			ReferencesCheckerOutput.generateCSVOutputCheckReferences(
				Arrays.asList(headers), listMissingReferences);

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
			"Please enter your database host (" + dataSource.getHost() +
				"): ");

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
				dataSource.setPort(Integer.parseInt(response));
			}
		}

		System.out.println(
			"Please enter your database name (" +
				dataSource.getDatabaseName() + "): ");

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

	private void _initDatabase(
			String driverClassName, String url, String userName,
			String password, String jndiName)
		throws Exception {

		/* DBFactoryUtil.setDBFactory(
			(DBFactory)getPortalObject(
				"com.liferay.portal.dao.db.DBFactoryImpl")); */
		ReflectionUtil.initFactory(
			"com.liferay.portal.kernel.dao.db.DBFactoryUtil", "setDBFactory",
			"com.liferay.portal.kernel.dao.db.DBFactory",
			"com.liferay.portal.dao.db.DBFactoryImpl");

		/* DBManagerUtil.setDBManager(
			(DBManager)getPortalObject(
				"com.liferay.portal.dao.db.DBManagerImpl"));*/
		ReflectionUtil.initFactory(
			"com.liferay.portal.kernel.dao.db.DBManagerUtil", "setDBManager",
			"com.liferay.portal.kernel.dao.db.DBManager",
			"com.liferay.portal.dao.db.DBManagerImpl");

		DataSourceFactoryUtil.setDataSourceFactory(
			(DataSourceFactory)ReflectionUtil.newPortalObject(
				"com.liferay.portal.dao.jdbc.DataSourceFactoryImpl"));

		DataSource dataSource = DataSourceFactoryUtil.initDataSource(
			driverClassName, url, userName, password, jndiName);

		Class<?> dialectDectector = ReflectionUtil.getPortalClass(
			"com.liferay.portal.spring.hibernate.DialectDetector");
		Method getDialectMethod = dialectDectector.getMethod(
			"getDialect", DataSource.class);

		/* DBFactoryUtil.setDB(getDialectMethod.invoke(null, dataSource)); */
		try {
			Class<?> dbFactoryUtil = ReflectionUtil.getPortalClass(
				"com.liferay.portal.kernel.dao.db.DBFactoryUtil");

			Method method = dbFactoryUtil.getMethod(
				"setDB", new Class<?>[] {Object.class});

			method.invoke(null, getDialectMethod.invoke(null, dataSource));
		}
		catch (Throwable t) {
			if (_log.isDebugEnabled()) {
				_log.debug(t);
			}
		}

		/* DBManagerUtil.setDB(
			getDialectMethod.invoke(null, dataSource), dataSource); */

		try {
			Class<?> dbFactoryUtil = ReflectionUtil.getPortalClass(
				"com.liferay.portal.kernel.dao.db.DBManagerUtil");

			Method method = dbFactoryUtil.getMethod(
				"setDB", new Class<?>[] {Object.class, DataSource.class});

			method.invoke(
				null, getDialectMethod.invoke(null, dataSource), dataSource);
		}
		catch (Throwable t) {
			if (_log.isDebugEnabled()) {
				_log.debug(t);
			}
		}

		(new InfrastructureUtil()).setDataSource(dataSource);
	}

	private void _initUtil(ClassLoader classLoader) throws Exception {
		FastDateFormatFactoryUtil fastDateFormatFactoryUtil =
			new FastDateFormatFactoryUtil();

		fastDateFormatFactoryUtil.setFastDateFormatFactory(
			(FastDateFormatFactory)ReflectionUtil.newPortalObject(
			"com.liferay.portal.util.FastDateFormatFactoryImpl"));

		FileUtil fileUtil = new FileUtil();

		fileUtil.setFile(
			(com.liferay.portal.kernel.util.File)ReflectionUtil.newPortalObject(
				"com.liferay.portal.util.FileImpl"));

		PortalClassLoaderUtil.setClassLoader(classLoader);

		/*PortalUtil portalUtil = new PortalUtil();
		 * portalUtil.setPortal(new PortalImpl())*/
		ReflectionUtil.initFactory(
			"com.liferay.portal.util.PortalUtil", "setPortal",
			"com.liferay.portal.util.Portal",
			"com.liferay.portal.util.PortalImpl");

		ReflectionUtil.initFactory(
			"com.liferay.portal.kernel.util.PortalUtil", "setPortal",
			"com.liferay.portal.kernel.util.Portal",
			"com.liferay.portal.util.PortalImpl");

		PropsUtil.setProps(
			(Props)ReflectionUtil.newPortalObject(
				"com.liferay.portal.util.PropsImpl"));

		try {
			Class<?> log4JUtil = ReflectionUtil.getPortalClass(
				"com.liferay.util.log4j.Log4JUtil");

			Method configureLog4J = log4JUtil.getMethod(
				"configureLog4J", ClassLoader.class);

			configureLog4J.invoke(null, classLoader);
		}
		catch (Throwable t) {
		}

		try {
			Class<?> log4JUtil = ReflectionUtil.getPortalClass(
				"com.liferay.petra.log4j.Log4JUtil");

			Method configureLog4J = log4JUtil.getMethod(
				"configureLog4J", ClassLoader.class);

			configureLog4J.invoke(null, classLoader);
		}
		catch (Throwable t) {
		}

		try {
			LogFactoryUtil.setLogFactory(
				(LogFactory)ReflectionUtil.newPortalObject(
					"com.liferay.portal.log.Log4jLogFactoryImpl"));
		}
		catch (Throwable t) {
		}

		HtmlUtil htmlUtil = new HtmlUtil();

		htmlUtil.setHtml(
			(Html)ReflectionUtil.newPortalObject(
				"com.liferay.portal.util.HtmlImpl"));

		LanguageUtil languageUtil = new LanguageUtil();

		languageUtil.setLanguage(
			(Language)ReflectionUtil.newPortalObject(
				"com.liferay.portal.language.LanguageImpl"));

		/* RegistryUtil.setRegistry(new BasicRegistryImpl()); */
		ReflectionUtil.initFactory(
			"com.liferay.registry.RegistryUtil", "setRegistry",
			"com.liferay.registry.Registry",
			"com.liferay.registry.BasicRegistryImpl");
	}

	private static final Map<String, Database> _databases =
		new HashMap<String, Database>();

	static {
		_databases.put(SQLUtil.TYPE_DB2, Database.getDB2Database());
		_databases.put(SQLUtil.TYPE_MARIADB, Database.getMariaDBDatabase());
		_databases.put(SQLUtil.TYPE_MYSQL, Database.getMySQLDatabase());
		_databases.put(SQLUtil.TYPE_ORACLE, Database.getOracleDataSource());
		_databases.put(
			SQLUtil.TYPE_POSTGRESQL, Database.getPostgreSQLDatabase());
		_databases.put(SQLUtil.TYPE_SQLSERVER, Database.getSQLServerDatabase());
		_databases.put(SQLUtil.TYPE_SYBASE, Database.getSybaseDatabase());
	}

	private static Log _log = LogFactoryUtil.getLog(ReferencesChecker.class);

	private final ConsoleReader _consoleReader = new ConsoleReader();
	private String filenamePrefix;
	private String filenameSuffix;

}