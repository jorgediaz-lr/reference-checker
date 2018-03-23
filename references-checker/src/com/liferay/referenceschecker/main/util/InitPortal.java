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

import com.liferay.portal.kernel.cache.MultiVMPool;
import com.liferay.portal.kernel.cache.MultiVMPoolUtil;
import com.liferay.portal.kernel.cache.PortalCacheManager;
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
import com.liferay.referenceschecker.util.ReflectionUtil;
import com.liferay.referenceschecker.util.SQLUtil;

import java.io.File;
import java.io.IOException;

import java.lang.reflect.Method;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import jline.console.ConsoleReader;

/**
 * @author Jorge Díaz
 */
public class InitPortal {

	public InitPortal() throws IOException {
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

	public void initLiferayClasses() throws Exception {
		Thread currentThread = Thread.currentThread();

		ClassLoader classLoader = currentThread.getContextClassLoader();

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

		try {
			PortalCacheManager portalCacheManager =
				(PortalCacheManager)ReflectionUtil.newPortalObject(
					"com.liferay.portal.cache.memory.MemoryPortalCacheManager");

			Method afterPropertiesSet =
				portalCacheManager.getClass().getMethod("afterPropertiesSet");

			afterPropertiesSet.invoke(portalCacheManager);

			MultiVMPool multiVMPool =
				(MultiVMPool)ReflectionUtil.newPortalObject(
					"com.liferay.portal.cache.MultiVMPoolImpl");

			Method setPortalCacheManager = multiVMPool.getClass().getMethod(
				"setPortalCacheManager", PortalCacheManager.class);

			setPortalCacheManager.invoke(multiVMPool, portalCacheManager);

			MultiVMPoolUtil multiVMPoolUtil = new MultiVMPoolUtil();
			multiVMPoolUtil.setMultiVMPool(multiVMPool);
		}
		catch (Throwable t) {
		}

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

	private void _initDatabase(
		String driverClassName, String url, String userName, String password,
		String jndiName)
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

	private Properties _readProperties(File file) {
		Properties properties = new Properties();

		if (file.exists()) {
			try {
				properties.load(file);
			}
			catch (IOException ioe) {
				System.err.println("Unable to load " + file);
			}
		}

		return properties;
	}

	private static final Map<String, Database> _databases =
		new HashMap<String, Database>();

	private static Log _log = LogFactoryUtil.getLog(InitPortal.class);

	private static File _jarDir;

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

	private final ConsoleReader _consoleReader = new ConsoleReader();

}