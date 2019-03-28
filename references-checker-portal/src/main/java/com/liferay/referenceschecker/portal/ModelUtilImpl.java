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

package com.liferay.referenceschecker.portal;

import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.DynamicQueryFactoryUtil;
import com.liferay.portal.kernel.repository.model.RepositoryModel;
import com.liferay.portal.kernel.util.PortalClassLoaderUtil;
import com.liferay.portal.kernel.util.ReflectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * @author Jorge Díaz
 */
public class ModelUtilImpl
	extends com.liferay.referenceschecker.model.ModelUtilImpl {

	public static Object invokeMethod(
		Object object, String methodName, Class<?>[] parameterTypes,
		Object[] args) {

		try {
			Class<?> clazz = object.getClass();

			Method method = clazz.getMethod(methodName, parameterTypes);

			return method.invoke(object, args);
		}
		catch (NoSuchMethodException nsme) {
			throw new RuntimeException(
				"invokeMethod: " + methodName + " method not found for " +
					object,
				nsme);
		}
		catch (Exception e) {
			String cause = null;
			Throwable rootException = e.getCause();

			if (rootException != null) {
				cause = " (root cause: " + rootException.getMessage() + ")";
			}

			throw new RuntimeException(
				"invokeMethod: " + methodName + " method for " + object + ": " +
					cause,
				e);
		}
	}

	public String getClassName(String tableName) {
		if (tableName == null) {
			return null;
		}

		String className = super.getClassName(tableName);

		if (className != null) {
			return className;
		}

		className = tableToClassNameMappingFromPortal.get(
			StringUtils.lowerCase(tableName));

		if (className == null) {
			_log.info(tableName + " has no className");
		}
		else {
			_log.warn(
				"Mapping " + tableName + " => " + className +
					" was retrieved from model");

			super.tableNameToClassNameMapping.put(
				StringUtils.lowerCase(tableName), className);
		}

		return className;
	}

	public String getTableName(String className) {
		if (className == null) {
			return null;
		}

		String tableName = super.getTableName(className);

		if (tableName != null) {
			return tableName;
		}

		tableName = classNameToTableMappingFromPortal.get(className);

		if (tableName == null) {
			_log.info(className + " has no tableName");
		}
		else {
			_log.warn(
				"Mapping " + tableName + " => " + className +
					" was retrieved from model");

			super.classNameToTableNameMapping.put(className, tableName);
		}

		return className;
	}

	public void init(
			Connection connection,
			Map<String, String> tableNameToClassNameMapping,
			Map<String, Number> tableRank)
		throws SQLException {

		super.init(connection, tableNameToClassNameMapping, tableRank);

		persistedModelLocalServiceRegistry =
			getPersistedModelLocalServiceRegistry();

		if (persistedModelLocalServiceRegistry == null) {
			if (_log.isInfoEnabled()) {
				_log.info("Model data is not available");
			}

			return;
		}

		List<String> classNamesList = new ArrayList<>();

		for (String className : getClassNames()) {
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

		Map<String, String> classNameToTableMapping = new ConcurrentHashMap<>();
		Map<String, String> tableToClassNameMapping = new ConcurrentHashMap<>();

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
				Field field = ReflectionUtil.getDeclaredField(
					classLiferayModelImpl, "TABLE_NAME");

				modelTableName = (String)field.get(null);
			}
			catch (Exception e) {
				_log.error(
					"Error accessing to " + classLiferayModelImpl.getName() +
						"#TABLE_NAME",
					e);

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

		classNameToTableMappingFromPortal = classNameToTableMapping;
		tableToClassNameMappingFromPortal = tableToClassNameMapping;
	}

	protected static Class<?> getLiferayModelImplClass(
			ClassLoader classLoader, String liferayModelImpl)
		throws ClassNotFoundException {

		if (liferayModelImpl == null) {
			return null;
		}

		liferayModelImpl = liferayModelImpl + "ModelImpl";

		liferayModelImpl = liferayModelImpl.replace(
			"ImplModelImpl", "ModelImpl");

		Class<?> clazz = classLoader.loadClass(liferayModelImpl);

		if (_log.isDebugEnabled()) {
			_log.debug(
				"loaded class: " + clazz + " from classloader: " + classLoader);
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

			List<?> list = (List<?>)invokeMethod(
				service, "dynamicQuery", new Class<?>[] {DynamicQuery.class},
				new Object[] {dynamicQuery});

			Object obj = null;

			if ((list != null) && !list.isEmpty()) {
				obj = list.get(0);
			}

			if (obj != null) {
				Class<?> clazz = obj.getClass();

				return clazz.getName();
			}
		}
		catch (Exception e) {
			if (_log.isDebugEnabled()) {
				_log.debug(e, e);
			}
		}

		return null;
	}

	protected static Object getPrivateField(Object object, String fieldName)
		throws Exception {

		Class<?> clazz = object.getClass();

		Field field = ReflectionUtil.getDeclaredField(clazz, fieldName);

		return field.get(object);
	}

	protected static String getWrappedModelImpl(DynamicQuery dynamicQuery) {
		try {
			Object detachedCriteria = getPrivateField(
				dynamicQuery, "_detachedCriteria");

			Object criteria = getPrivateField(detachedCriteria, "impl");

			return (String)getPrivateField(criteria, "entityOrClassName");
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

		return (List<?>)invokeMethod(
			service, "dynamicQuery", new Class<?>[] {DynamicQuery.class},
			new Object[] {dynamicQuery});
	}

	protected Class<?> getLiferayModelImpl(String className)
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
			Class<?> serviceClass = service.getClass();

			classLoader = serviceClass.getClassLoader();

			dynamicQuery = newDynamicQuery(service);
		}

		if (dynamicQuery == null) {
			return null;
		}

		String liferayModelImpl = getLiferayModelImplClassName(
			service, dynamicQuery);

		return getLiferayModelImplClass(classLoader, liferayModelImpl);
	}

	protected Object getPersistedModelLocalService(String className) {
		return invokeMethod(
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
		return (DynamicQuery)invokeMethod(service, "dynamicQuery", null, null);
	}

	protected DynamicQuery newDynamicQueryFromPortal(String className) {
		try {
			ClassLoader classLoader = PortalClassLoaderUtil.getClassLoader();

			if (classLoader == null) {
				Class<?> clazz = getClass();

				classLoader = clazz.getClassLoader();
			}

			Class<?> classInterface = classLoader.loadClass(className);

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

	protected Map<String, String> classNameToTableMappingFromPortal =
		new ConcurrentHashMap<>();
	protected Object groupService = null;
	protected Object persistedModelLocalServiceRegistry = null;
	protected Map<String, String> tableToClassNameMappingFromPortal =
		new ConcurrentHashMap<>();

	private static Logger _log = LogManager.getLogger(ModelUtilImpl.class);

}