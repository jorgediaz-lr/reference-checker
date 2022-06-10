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

	public static Configuration readConfigurationFile(
			ClassLoader classLoader, String versionSuffix)
		throws IOException {

		InputStream inputStream = _getInputStream(
			classLoader, _CONFIGURATION_FILE, versionSuffix);

		Constructor constructor = new CustomConstructor(
			Configuration.class, classLoader, versionSuffix);

		Yaml yaml = new Yaml(constructor);

		return (Configuration)yaml.load(inputStream);
	}

	private static InputStream _getInputStream(
		ClassLoader classLoader, String configurationFile,
		String versionSuffix) {

		configurationFile = String.format(configurationFile, versionSuffix);

		InputStream inputStream = classLoader.getResourceAsStream(
			configurationFile);

		if (inputStream == null) {
			throw new RuntimeException(
				"File " + configurationFile + " does not exists");
		}

		return inputStream;
	}

	private static final String _CONFIGURATION_FILE = "configuration.yml";

	private static class CustomConstructor extends Constructor {

		public CustomConstructor(
			Class<? extends Object> theRoot, ClassLoader classLoader,
			String versionSuffix) {

			super(theRoot);

			TypeDescription configurationDescription = new TypeDescription(
				Configuration.class);

			configurationDescription.putListPropertyType(
				"references", Configuration.Reference.class);

			addTypeDescription(configurationDescription);

			yamlConstructors.put(
				new Tag("!include"),
				new ImportConstruct(classLoader, versionSuffix));
		}

	}

	private static class ImportConstruct extends AbstractConstruct {

		public ImportConstruct(ClassLoader classLoader, String versionSuffix) {
			_classLoader = classLoader;
			_versionSuffix = versionSuffix;
		}

		@Override
		public Object construct(Node node) {
			if (!(node instanceof ScalarNode)) {
				throw new IllegalArgumentException(
					"Non-scalar !import: " + node.toString());
			}

			ScalarNode scalarNode = (ScalarNode)node;

			InputStream inputStream = _getInputStream(
				_classLoader, scalarNode.getValue(), _versionSuffix);

			Class<? extends Object> clazz = node.getType();

			if (clazz != Configuration.References.class) {
				Constructor constructor = new CustomConstructor(
					clazz, _classLoader, _versionSuffix);

				Yaml yaml = new Yaml(constructor);

				return yaml.load(inputStream);
			}

			Constructor constructor = new CustomConstructor(
				Configuration.class, _classLoader, _versionSuffix);

			Yaml yaml = new Yaml(constructor);

			Configuration configuration = (Configuration)yaml.load(inputStream);

			return configuration.getReferences();
		}

		private ClassLoader _classLoader;
		private String _versionSuffix;

	}

}