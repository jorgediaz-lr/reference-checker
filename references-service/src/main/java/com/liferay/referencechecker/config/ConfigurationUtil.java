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

package com.liferay.referencechecker.config;

import java.io.IOException;
import java.io.InputStream;

import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * @author Jorge Díaz
 */
public class ConfigurationUtil {

	public static String getConfigurationFileName(long liferayBuildNumber) {
		long liferayVersion = liferayBuildNumber / 100;

		return String.format(_CONFIGURATION_FILE, liferayVersion);
	}

	public static Configuration readConfigurationFile(
			ClassLoader classLoader, String configurationFile)
		throws IOException {

		InputStream inputStream = _getInputStream(
			classLoader, configurationFile);

		Constructor constructor = new CustomConstructor(
			Configuration.class, classLoader);

		TypeDescription configurationDescription = new TypeDescription(
			Configuration.class);

		configurationDescription.putListPropertyType(
			"references", Configuration.Reference.class);

		constructor.addTypeDescription(configurationDescription);

		Yaml yaml = new Yaml(constructor);

		return (Configuration)yaml.load(inputStream);
	}

	private static InputStream _getInputStream(
		ClassLoader classLoader, String configurationFile) {

		InputStream inputStream = classLoader.getResourceAsStream(
			configurationFile);

		if (inputStream == null) {
			throw new RuntimeException(
				"File " + configurationFile + " does not exists");
		}

		return inputStream;
	}

	private static final String _CONFIGURATION_FILE = "configuration_%s.yml";

	private static class CustomConstructor extends Constructor {

		public CustomConstructor(
			Class<? extends Object> theRoot, ClassLoader classLoader) {

			super(theRoot);

			Constructor nestedConstructor = this;

			if (theRoot != Object.class) {
				nestedConstructor = new CustomConstructor(
					Object.class, classLoader);
			}

			yamlConstructors.put(
				new Tag("!include"),
				new ImportConstruct(classLoader, nestedConstructor));
		}

	}

	private static class ImportConstruct extends AbstractConstruct {

		public ImportConstruct(
			ClassLoader classLoader, Constructor constructor) {

			_classLoader = classLoader;
			_constructor = constructor;
		}

		@Override
		public Object construct(Node node) {
			if (!(node instanceof ScalarNode)) {
				throw new IllegalArgumentException(
					"Non-scalar !import: " + node.toString());
			}

			ScalarNode scalarNode = (ScalarNode)node;

			InputStream inputStream = _getInputStream(
				_classLoader, scalarNode.getValue());

			Yaml yaml = new Yaml(_constructor);

			return yaml.load(inputStream);
		}

		private ClassLoader _classLoader;
		private Constructor _constructor;

	}

}