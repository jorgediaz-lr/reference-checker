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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jline.console.ConsoleReader;
public class Launcher {

	//0 => app-server.properties;
	//1 => com.liferay.referenceschecker.main.ReferencesChecker
	//2 => main
	//3 => [debug]

	public static void main(String[] args) throws IOException {
		if (args.length < 3) {
			System.err.println(
				"ERROR, missing parameters: <app-server.properties> " +
				"<className> <method> [parameters]");

			System.exit(-1);
		}

		String appServerCfg = args[0];
		String className = args[1];
		String methodName = args[2];

		int removeArgs = 3;
		boolean debug = false;

		if ((args.length>= 4) && args[3].trim().equals("debug")) {
			debug = true;
			removeArgs = 4;
		}

		args = Arrays.copyOfRange(args, removeArgs, args.length);

		Launcher launcher = new Launcher(debug);

		List<URL> classLoaderUrls = launcher.calculateClassLoaderURLs(
			appServerCfg);

		File currentDir = new File(".");

		currentDir = currentDir.getAbsoluteFile();

		URL url = currentDir.toURI().toURL();

		classLoaderUrls.add(0, url);

		launcher.setClassLoaderURLs(classLoaderUrls);

		launcher.execute(className, methodName, args);
	}

	public Launcher(boolean debug) {
		this.debug = debug;
	}

	public List<URL> calculateClassLoaderURLs(String appServerCfg)
		throws IOException {

		File appServerPropertiesFile = new File(appServerCfg);

		Properties appServerProperties = _readProperties(
			appServerPropertiesFile);

		String value = appServerProperties.getProperty(
			"server.detector.server.id");

		if ((value != null) && !value.isEmpty()) {
			try {
				AppServer appServer = getAppServerFromConfig(
					appServerProperties, value);

				return getClassLoaderURLs(appServer);
			}
			catch (Exception e) {
				System.err.println(
					"Unable to load configuration file " +
						appServerPropertiesFile);
				System.err.println(e.getMessage());
				System.err.println();
			}
		}

		AppServer appServer = null;
		List<URL> urls = null;

		while (urls == null) {
			ConsoleReader consoleReader = null;

			try {
				consoleReader = new ConsoleReader();

				appServer = getAppServerConfigFromCmd(consoleReader);

				urls = getClassLoaderURLs(appServer);
			}
			catch (Exception e) {
				System.err.println(e.getMessage());
				System.err.println();
			}
			finally {
				if (consoleReader != null) {
					consoleReader.close();
				}
			}
		}

		storeAppServerPropertiesFile(appServerPropertiesFile, appServer);

		return urls;
	}

	protected static File getReferencesCheckerLibFolder()
		throws ExceptionInInitializerError {

		ProtectionDomain protectionDomain =
			Launcher.class.getProtectionDomain();

		CodeSource codeSource = protectionDomain.getCodeSource();

		URL url = codeSource.getLocation();

		try {
			Path path = Paths.get(url.toURI());

			File jarFile = path.toFile();

			return jarFile.getParentFile();
		}
		catch (URISyntaxException urise) {
			return null;
		}
	}

	protected void execute(String className, String methodName, String[] args) {

		Thread currentThread = Thread.currentThread();

		currentThread.setContextClassLoader(urlClassLoader);

		Class<?> referencesCheckerClazz;
		try {
			referencesCheckerClazz = urlClassLoader.loadClass(className);

			Method main = referencesCheckerClazz.getMethod(
				methodName, String[].class);

			main.invoke(null, ((Object)args));
		}
		catch (Throwable t) {
			if (debug) {
				System.err.println(
					"Classloader URLs: " + Arrays.toString(
						urlClassLoader.getURLs()));
				System.err.println();
			}

			System.err.println(
				"Error executing " + className + "." + methodName);

			if (debug) {
				System.err.println("Arguments: " + Arrays.toString(args));
				System.err.println();
			}

			t.printStackTrace(System.err);
		}
	}

	protected AppServer getAppServerConfigFromCmd(ConsoleReader consoleReader)
		throws IOException {

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

			response = consoleReader.readLine();

			if (response.isEmpty()) {
				response = "tomcat";
			}

			appServer = _appServers.get(response);

			if (appServer == null) {
				System.err.println(
					response + " is an unsupported application server.");
			}
		}

		appServer = new AppServer(
			appServer.getDirName(), appServer.getExtraLibDirNames(),
			appServer.getGlobalLibDirName(), appServer.getPortalDirName(),
			appServer.getServerDetectorServerId());

		response = null;
		File dir = null;

		while (((response == null) || response.trim().isEmpty()) &&
			   (dir == null)) {

			dir = appServer.getDir();
			String dirName = appServer.getDirName();

			String defaultDir = "LIFERAY_HOME/" + dirName + " or " + dirName;

			if (dir != null) {
				defaultDir = dir.toString();
			}

			System.out.println(
				"Please enter your application server directory, ex: " +
					defaultDir);

			response = consoleReader.readLine();

			if (!response.isEmpty()) {
				appServer.setDirName(response);

				dir = appServer.getDir();

				if (dir == null) {
					System.err.println("ERROR " + response + " doesn't exists");

					appServer.setDirName(dirName);

					response = null;
				}
			}
		}

		System.out.println(
			"Please enter your extra library directories in application " +
				"server directory (" + appServer.getExtraLibDirNames() +
					"): ");

		response = consoleReader.readLine();

		if (!response.isEmpty()) {
			appServer.setExtraLibDirNames(response);
		}

		System.out.println(
			"Please enter your global library directory in application " +
				"server directory (" + appServer.getGlobalLibDirName() +
					"): ");

		response = consoleReader.readLine();

		if (!response.isEmpty()) {
			appServer.setGlobalLibDirName(response);
		}

		System.out.println(
			"Please enter your portal directory in application server " +
				"directory (" + appServer.getPortalDirName() + "): ");

		response = consoleReader.readLine();

		if (!response.isEmpty()) {
			appServer.setPortalDirName(response);
		}

		return appServer;
	}

	protected AppServer getAppServerFromConfig(
			Properties appServerProperties, String value)
		throws IOException {

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

	protected List<URL> getClassLoaderURLs(AppServer appServer)
		throws IOException, MalformedURLException {

		List<File> classPath = new ArrayList<File>();

		_appendClassPath(classPath, _jarDir);
		_appendClassPath(classPath, appServer.getGlobalLibDir());
		_appendClassPath(classPath, appServer.getExtraLibDirs());

		classPath.add(appServer.getPortalClassesDir());

		_appendClassPath(classPath, appServer.getPortalLibDir());

		List<URL> urls = new ArrayList<URL>();

		for (File file : classPath) {
			if (!file.exists()) {
				throw new RuntimeException(
					"ERROR: " + file + " doesn't exists");
			}

			URI uri = file.toURI();

			URL url = uri.toURL();

			if (!urls.contains(url)) {
				urls.add(url);
			}
		}

		return urls;
	}

	protected void setClassLoaderURLs(List<URL> classLoaderUrls) {
		URL[] urls = classLoaderUrls.toArray(new URL[classLoaderUrls.size()]);

		urlClassLoader = new URLClassLoader(urls, null);
	}

	protected void storeAppServerPropertiesFile(
			File appServerPropertiesFile, AppServer appServer)
		throws IOException {

		File dir = appServer.getDir();

		if ((dir == null)||!dir.exists()) {
			return;
		}

		Properties appServerProperties = new Properties();

		appServerProperties.setProperty("dir", dir.getCanonicalPath());
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
	}

	protected URLClassLoader urlClassLoader = null;

	private void _appendClassPath(List<File> classPath, File dir)
		throws IOException {

		if (!dir.exists()) {
			throw new RuntimeException("ERROR: " + dir + " doesn't exists");
		}

		if (!dir.isDirectory()) {
			return;
		}

		for (File file : dir.listFiles()) {
			String fileName = file.getName();

			if (file.isFile() && fileName.endsWith("jar")) {
				classPath.add(file);
			}
			else if (file.isDirectory()) {
				_appendClassPath(classPath, file);
			}
		}
	}

	private void _appendClassPath(List<File> classPath, List<File> dirs)
		throws IOException {

		for (File dir : dirs) {
			_appendClassPath(classPath, dir);
		}
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

	static {
		_appServers.put("jboss", AppServer.getJBossEAPAppServer());
		_appServers.put("jonas", AppServer.getJOnASAppServer());
		_appServers.put("resin", AppServer.getResinAppServer());
		_appServers.put("tcserver", AppServer.getTCServerAppServer());
		_appServers.put("tomcat", AppServer.getTomcatAppServer());
		_appServers.put("weblogic", AppServer.getWebLogicAppServer());
		_appServers.put("websphere", AppServer.getWebSphereAppServer());
		_appServers.put("wildfly", AppServer.getWildFlyAppServer());

		_jarDir = getReferencesCheckerLibFolder();
	}

	private static File _jarDir;

	private boolean debug;

}