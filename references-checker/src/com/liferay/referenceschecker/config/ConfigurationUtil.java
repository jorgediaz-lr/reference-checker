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

package com.liferay.referenceschecker.config;

import com.liferay.referenceschecker.util.StringUtil;

import java.io.IOException;
import java.io.InputStream;

import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * @author Jorge Díaz
 */
public class ConfigurationUtil {

	public static String getConfigurationFileName(long liferayBuildNumber) {
		long liferayVersion = liferayBuildNumber / 100;

		String configurationFile = String.format(
			CONFIGURATION_FILE, liferayVersion);
		return configurationFile;
	}

	public static Configuration readConfigurationFile(String configurationFile)
		throws IOException {

		ClassLoader classLoader = ConfigurationUtil.class.getClassLoader();

		InputStream inputStream = classLoader.getResourceAsStream(
			configurationFile);

		if (inputStream == null) {
			throw new RuntimeException(
				"File " + configurationFile + " doesn't exists");
		}

		String configuration = StringUtil.read(inputStream);

		Constructor constructor = new Constructor(Configuration.class);
		TypeDescription configurationDescription = new TypeDescription(
			Configuration.class);
		configurationDescription.putListPropertyType(
			"references", Configuration.Reference.class);
		configurationDescription.putListPropertyType(
			"ignoreTables", String.class);
		configurationDescription.putMapPropertyType(
			"tableToClassNameMapping", String.class, String.class);
		constructor.addTypeDescription(configurationDescription);
		Yaml yaml = new Yaml(constructor);

		return (Configuration)yaml.load(configuration);
	}

	private static final String CONFIGURATION_FILE = "configuration_%s.yml";

}