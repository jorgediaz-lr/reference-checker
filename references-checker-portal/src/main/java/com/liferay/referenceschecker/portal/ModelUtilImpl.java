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

package com.liferay.referenceschecker.portal;

import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.DynamicQueryFactoryUtil;
import com.liferay.portal.kernel.repository.model.RepositoryModel;
import com.liferay.portal.kernel.util.PortalClassLoaderUtil;
import com.liferay.referenceschecker.model.ModelUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
public class ModelUtilImpl implements ModelUtil {

	public String getClassName(String tableName) {
		return tableToClassNameMapping.get(StringUtils.lowerCase(tableName));
	}

	public Set<String> getClassNames() {
		return new HashSet<>(classNameToTableMapping.keySet());
	}

	public String getClassNameId(String className) {
		return null;
	}

	public Class<?> getLiferayModelImpl(String className)
		throws ClassNotFoundException {

		ClassLoader classLoader;
		DynamicQuery dynamicQuery;
		Object service = getPersistedModelLocalService(className);

		if (service == null) {
			classLoader = PortalClassLoaderUtil.getClassLoader();
			dynamicQuery = newDynamicQueryFromPortal(className);
			service = groupService;
		}
		else {
			classLoader = service.getClass().getClassLoader();
			dynamicQuery = newDynamicQuery(service);
		}

		if (dynamicQuery == null) {
			return null;
		}

		String liferayModelImpl = ModelUtilImpl.getLiferayModelImplClassName(
			service, dynamicQuery);

		return ModelUtilImpl.getLiferayModelImplClass(
			classLoader, liferayModelImpl);
	}

	public String getTableName(String className) {
		return classNameToTableMapping.get(className);
	}

	public void initModelMappings(Collection<String> classNames) {
		persistedModelLocalServiceRegistry =
			getPersistedModelLocalServiceRegistry();

		if (persistedModelLocalServiceRegistry == null) {
			if (_log.isInfoEnabled()) {
				_log.info("Model data is not available");
			}

			return;
		}

		List<String> classNamesList = new ArrayList<String>();

		for (String className : classNames) {
			if (!className.contains(".model.")) {
				if (_log.isInfoEnabled()) {
					_log.info("Ignoring " + className);
				}

				continue;
			}

			classNamesList.add(className);

			if (className.startsWith("com.liferay.") &&
				className.endsWith(".Group")) {

				groupService = getPersistedModelLocalService(className);
			}
		}

		Map<String, String> classNameToTableMapping =
			new ConcurrentHashMap<String, String>();
		Map<String, String> tableToClassNameMapping =
			new ConcurrentHashMap<String, String>();

		for (String className : classNamesList) {
			String realClassName = className;
			int pos = className.indexOf(".UserPersonalSite");

			if (pos != -1) {
				realClassName = className.substring(0, pos) + ".User";
			}

			Class<?> classLiferayModelImpl = null;

			try {
				classLiferayModelImpl = getLiferayModelImpl(realClassName);
			}
			catch (Throwable t) {
				_log.warn("Ignoring " + className + " due to exception: " + t);
			}

			if (classLiferayModelImpl == null) {
				continue;
			}

			String modelTableName;
			try {
				Field field =
					ReflectionUtil.getDeclaredField(
						classLiferayModelImpl, "TABLE_NAME");

				modelTableName = (String)field.get(null);
			}
			catch (Exception e) {
				_log.error(
					"Error accessing to " + classLiferayModelImpl.getName() +
					"#TABLE_NAME", e);

				continue;
			}

			if (_log.isDebugEnabled()) {
				_log.debug(
					"ClassName: " + className + " table: " + modelTableName);
			}

			classNameToTableMapping.put(className, modelTableName);
			tableToClassNameMapping.put(
				StringUtils.lowerCase(modelTableName), className);
		}

		this.classNameToTableMapping = classNameToTableMapping;
		this.tableToClassNameMapping = tableToClassNameMapping;
	}

	protected static Class<?> getLiferayModelImplClass(
			ClassLoader classloader, String liferayModelImpl)
		throws ClassNotFoundException {

		if (liferayModelImpl == null) {
			return null;
		}

		liferayModelImpl = liferayModelImpl + "ModelImpl";

		liferayModelImpl = liferayModelImpl.replace(
			"ImplModelImpl", "ModelImpl");

		Class<?> clazz = classloader.loadClass(liferayModelImpl);

		if (_log.isDebugEnabled()) {
			_log.debug(
				"loaded class: " + clazz + " from classloader: " +
					classloader);
		}

		return clazz;
	}

	protected static String getLiferayModelImplClassName(
		Object service, DynamicQuery dynamicQuery) {

		String liferayModelImpl = getWrappedModelImpl(dynamicQuery);

		if (liferayModelImpl != null) {
			return liferayModelImpl;
		}

		try {
			dynamicQuery.setLimit(0, 1);

			List<?> list = (List<?>)ReflectionUtil.invokeMethod(
				service, "dynamicQuery", new Class<?>[] {DynamicQuery.class},
				new Object[] {dynamicQuery});

			Object obj = null;

			if ((list != null) && (list.size() > 0)) {
				obj = list.get(0);
			}

			if (obj != null) {
				return obj.getClass().getName();
			}
		}
		catch (Exception e) {
			if (_log.isDebugEnabled()) {
				_log.debug(e, e);
			}
		}

		return null;
	}

	protected static String getWrappedModelImpl(DynamicQuery dynamicQuery) {
		try {
			Object detachedCriteria = ReflectionUtil.getPrivateField(
				dynamicQuery, "_detachedCriteria");

			Object criteria = ReflectionUtil.getPrivateField(
				detachedCriteria, "impl");

			return (String)ReflectionUtil.getPrivateField(
				criteria, "entityOrClassName");
		}
		catch (Throwable t) {
			if (_log.isDebugEnabled()) {
				_log.debug(t, t);
			}
		}

		return null;
	}

	protected List<?> executeDynamicQuery(
		Object service, DynamicQuery dynamicQuery) {

		return (List<?>)ReflectionUtil.invokeMethod(
			service, "dynamicQuery", new Class<?>[] {DynamicQuery.class},
			new Object[] {dynamicQuery});
	}

	protected Object getPersistedModelLocalService(String className) {

		return ReflectionUtil.invokeMethod(
			persistedModelLocalServiceRegistry, "getPersistedModelLocalService",
			new Class<?>[] {String.class}, new Object[] {className});
	}

	protected Object getPersistedModelLocalServiceRegistry() {
		ClassLoader classLoader = PortalClassLoaderUtil.getClassLoader();

		Class<?> persistedModelLocalServiceRegistryUtil = null;

		try {
			/* 7.x */
			persistedModelLocalServiceRegistryUtil = classLoader.loadClass(
				"com.liferay.portal.kernel.service." +
				"PersistedModelLocalServiceRegistryUtil");
		}
		catch (Throwable t) {
			if (_log.isDebugEnabled()) {
				_log.debug(t);
			}
		}

		if (persistedModelLocalServiceRegistryUtil == null) {
			try {
				/* 6.x */
				persistedModelLocalServiceRegistryUtil = classLoader.loadClass(
					"com.liferay.portal.service." +
					"PersistedModelLocalServiceRegistryUtil");
			}
			catch (Throwable t) {
				if (_log.isDebugEnabled()) {
					_log.debug(t);
				}
			}
		}

		if (persistedModelLocalServiceRegistryUtil == null) {
			return null;
		}

		try {
			Method getPersistedModelLocalServiceRegistry =
				persistedModelLocalServiceRegistryUtil.getMethod(
					"getPersistedModelLocalServiceRegistry");

			return getPersistedModelLocalServiceRegistry.invoke(null);
		}
		catch (Throwable t) {
			if (_log.isDebugEnabled()) {
				_log.debug(t);
			}
		}

		return null;
	}

	protected DynamicQuery newDynamicQuery(Object service) {

		return (DynamicQuery)ReflectionUtil.invokeMethod(
			service, "dynamicQuery", null, null);
	}

	protected DynamicQuery newDynamicQueryFromPortal(String className) {
		try {
			Class<?> classInterface = ReflectionUtil.getPortalClass(className);

			if (RepositoryModel.class.isAssignableFrom(classInterface)) {
				return null;
			}

			return DynamicQueryFactoryUtil.forClass(
				classInterface, null, classInterface.getClassLoader());
		}
		catch (ClassNotFoundException cnfe) {
			return null;
		}
	}

	protected Map<String, String> classNameToTableMapping =
		new ConcurrentHashMap<String, String>();
	protected Object groupService = null;
	protected Object persistedModelLocalServiceRegistry = null;
	protected Map<String, String> tableToClassNameMapping =
		new ConcurrentHashMap<String, String>();

	private static Logger _log = LogManager.getLogger(ModelUtilImpl.class);

}