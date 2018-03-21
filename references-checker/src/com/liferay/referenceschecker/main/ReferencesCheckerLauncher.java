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

import com.liferay.referenceschecker.main.util.AppServer;
import com.liferay.referenceschecker.main.util.Properties;

import java.io.File;
import java.io.IOException;

import java.lang.reflect.Method;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;

import java.nio.file.Path;
import java.nio.file.Paths;

import java.security.CodeSource;
import java.security.ProtectionDomain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jline.console.ConsoleReader;
public class ReferencesCheckerLauncher {

	public static void main(String[] args) throws Exception {
		ReferencesCheckerLauncher referencesCheckerLauncher =
			new ReferencesCheckerLauncher();

		ClassLoader classLoader = referencesCheckerLauncher.getClassLoader(
			"app-server.properties");

		Thread currentThread = Thread.currentThread();

		currentThread.setContextClassLoader(classLoader);

		Class<?> referencesCheckerClazz = classLoader.loadClass(
			"com.liferay.referenceschecker.main.ReferencesChecker");

		Method main = referencesCheckerClazz.getMethod("main", String[].class);

		main.invoke(null, ((Object)args));
	}

	public ReferencesCheckerLauncher() throws IOException {
	}

	public ClassLoader getClassLoader(String appServerCfg) throws IOException {
		File appServerPropertiesFile = new File(_jarDir, appServerCfg);

		AppServer appServer = getAppServerConfiguration(
			appServerPropertiesFile);

		String classPath = _getClassPath(appServer);

		return new URLClassLoader(_getClassPathURLs(classPath), null);
	}

	protected AppServer getAppServerConfiguration(File appServerPropertiesFile)
		throws IOException {

		Properties appServerProperties = _readProperties(
			appServerPropertiesFile);

		String value = appServerProperties.getProperty(
			"server.detector.server.id");

		if ((value != null) && !value.isEmpty()) {
			String dirName = appServerProperties.getProperty("dir");

			File dir = new File(dirName);

			if (!dir.isAbsolute()) {
				dir = new File(_jarDir, dirName);
			}

			dirName = dir.getCanonicalPath();

			return new AppServer(
				dirName, appServerProperties.getProperty("extra.lib.dirs"),
				appServerProperties.getProperty("global.lib.dir"),
				appServerProperties.getProperty("portal.dir"), value);
		}

		String response = null;

		AppServer appServer = null;

		while (appServer == null) {
			System.out.print("[ ");

			for (String appServerName : _appServers.keySet()) {
				System.out.print(appServerName + " ");
			}

			System.out.println("]");
			System.out.println(
				"Please enter your application server (tomcat): ");

			response = _consoleReader.readLine();

			if (response.isEmpty()) {
				response = "tomcat";
			}

			appServer = _appServers.get(response);

			if (appServer == null) {
				System.err.println(
					response + " is an unsupported application server.");
			}
		}

		File dir = appServer.getDir();

		System.out.println(
			"Please enter your application server directory (" + dir +
				"): ");

		response = _consoleReader.readLine();

		if (!response.isEmpty()) {
			appServer.setDirName(response);
		}

		System.out.println(
			"Please enter your extra library directories in application " +
				"server directory (" + appServer.getExtraLibDirNames() +
					"): ");

		response = _consoleReader.readLine();

		if (!response.isEmpty()) {
			appServer.setExtraLibDirNames(response);
		}

		System.out.println(
			"Please enter your global library directory in application " +
				"server directory (" + appServer.getGlobalLibDirName() +
					"): ");

		response = _consoleReader.readLine();

		if (!response.isEmpty()) {
			appServer.setGlobalLibDirName(response);
		}

		System.out.println(
			"Please enter your portal directory in application server " +
				"directory (" + appServer.getPortalDirName() + "): ");

		response = _consoleReader.readLine();

		if (!response.isEmpty()) {
			appServer.setPortalDirName(response);
		}

		appServerProperties.setProperty(
			"dir", appServer.getDir().getCanonicalPath());
		appServerProperties.setProperty(
			"extra.lib.dirs", appServer.getExtraLibDirNames());
		appServerProperties.setProperty(
			"global.lib.dir", appServer.getGlobalLibDirName());
		appServerProperties.setProperty(
			"portal.dir", appServer.getPortalDirName());
		appServerProperties.setProperty(
			"server.detector.server.id", appServer.getServerDetectorServerId());

		try {
			appServerProperties.store(appServerPropertiesFile);
		}
		catch (IOException ioe) {
			ioe.printStackTrace();
		}

		return appServer;
	}

	private static URL[] _getClassPathURLs(String classPath)
		throws MalformedURLException {

		String[] paths = classPath.split(File.pathSeparator);

		Set<URL> urls = new LinkedHashSet<URL>();

		for (String path : paths) {
			File file = new File(path);

			URI uri = file.toURI();

			urls.add(uri.toURL());
		}

		return urls.toArray(new URL[urls.size()]);
	}

	private void _appendClassPath(StringBuilder sb, File dir)
		throws IOException {

		if (dir.exists() && dir.isDirectory()) {
			for (File file : dir.listFiles()) {
				String fileName = file.getName();

				if (file.isFile() && fileName.endsWith("jar")) {
					sb.append(file.getCanonicalPath());
					sb.append(File.pathSeparator);
				}
				else if (file.isDirectory()) {
					_appendClassPath(sb, file);
				}
			}
		}
	}

	private void _appendClassPath(StringBuilder sb, List<File> dirs)
		throws IOException {

		for (File dir : dirs) {
			_appendClassPath(sb, dir);
		}
	}

	private String _getClassPath(AppServer appServer) throws IOException {
		StringBuilder sb = new StringBuilder();

		String liferayClassPath = System.getenv("LIFERAY_CLASSPATH");

		if ((liferayClassPath != null) && !liferayClassPath.isEmpty()) {
			sb.append(liferayClassPath);
			sb.append(File.pathSeparator);
		}

		_appendClassPath(sb, new File(_jarDir, "lib"));
		_appendClassPath(sb, _jarDir);
		_appendClassPath(sb, appServer.getGlobalLibDir());
		_appendClassPath(sb, appServer.getExtraLibDirs());

		sb.append(appServer.getPortalClassesDir());
		sb.append(File.pathSeparator);

		_appendClassPath(sb, appServer.getPortalLibDir());

		return sb.toString();
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

	private static final Map<String, AppServer> _appServers =
		new LinkedHashMap<String, AppServer>();

	private static File _jarDir;

	static {
		_appServers.put("jboss", AppServer.getJBossEAPAppServer());
		_appServers.put("jonas", AppServer.getJOnASAppServer());
		_appServers.put("resin", AppServer.getResinAppServer());
		_appServers.put("tcserver", AppServer.getTCServerAppServer());
		_appServers.put("tomcat", AppServer.getTomcatAppServer());
		_appServers.put("weblogic", AppServer.getWebLogicAppServer());
		_appServers.put("websphere", AppServer.getWebSphereAppServer());
		_appServers.put("wildfly", AppServer.getWildFlyAppServer());

		ProtectionDomain protectionDomain =
			ReferencesCheckerLauncher.class.getProtectionDomain();

		CodeSource codeSource = protectionDomain.getCodeSource();

		URL url = codeSource.getLocation();

		try {
			Path path = Paths.get(url.toURI());

			File jarFile = path.toFile();

			_jarDir = jarFile.getParentFile();
		}
		catch (URISyntaxException urise) {
			throw new ExceptionInInitializerError(urise);
		}
	}

	private final ConsoleReader _consoleReader = new ConsoleReader();
}