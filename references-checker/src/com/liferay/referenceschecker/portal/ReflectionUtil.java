package com.liferay.referenceschecker.portal;

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

import com.liferay.portal.kernel.util.PortalClassLoaderUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * @author Jorge Díaz
 */
public class ReflectionUtil {

	public static Field getDeclaredField(Class<?> clazz, String name)
		throws Exception {

		return com.liferay.portal.kernel.util.ReflectionUtil.getDeclaredField(
			clazz, name);
	}

	public static Method getMethod(
			Object object, String methodName, Class<?>... parameterTypes)
		throws ClassNotFoundException, NoSuchMethodException {

		Class<?> clazz = object.getClass();

		return clazz.getMethod(methodName, parameterTypes);
	}

	public static Class<?> getPortalClass(String name)
		throws ClassNotFoundException {

		ClassLoader classLoader = PortalClassLoaderUtil.getClassLoader();

		if (classLoader == null) {
			Class<?> clazz = ReflectionUtil.class;

			classLoader = clazz.getClassLoader();
		}

		return classLoader.loadClass(name);
	}

	public static Object getPrivateField(Object object, String fieldName)
		throws Exception {

		Class<?> clazz = object.getClass();

		Field field =
			com.liferay.portal.kernel.util.ReflectionUtil.getDeclaredField(
				clazz, fieldName);

		return field.get(object);
	}

	public static void initFactory(
			String util, String methodName, String interfaceName,
			String implementation) {

		try {
			Class<?> utilClass = getPortalClass(util);

			Object utilObject = utilClass.newInstance();

			Class<?> interfaceClass = getPortalClass(interfaceName);

			Method method = utilClass.getMethod(
				methodName, new Class<?>[] {interfaceClass});

			Object implementationObject = newPortalObject(implementation);

			method.invoke(utilObject, implementationObject);
		}
		catch (Throwable t) {
			if (_log.isDebugEnabled()) {
				_log.debug(t);
			}
		}
	}

	public static Object invokeMethod(
		Object object, String methodName, Class<?>[] parameterType,
		Object[] arg) {

		try {
			Method method = getMethod(object, methodName, parameterType);

			return method.invoke(object, arg);
		}
		catch (NoSuchMethodException e) {
			throw new RuntimeException(
				"invokeMethod: " + methodName + " method not found for " +
				object, e);
		}
		catch (Exception e) {
			String cause = null;
			Throwable rootException = e.getCause();

			if (rootException != null) {
				cause = " (root cause: " + rootException.getMessage() + ")";
			}

			throw new RuntimeException(
				"invokeMethod: " + methodName + " method for " +
				object + ": " + cause, e);
		}
	}

	public static Object newPortalObject(
			String className, Class<?>... parameterTypes)
		throws Exception {

		Class<?> classImpl = getPortalClass(className);

		Constructor<?> constructor = classImpl.getDeclaredConstructor(
			parameterTypes);

		constructor.setAccessible(true);

		return constructor.newInstance();
	}

	private static Logger _log = LogManager.getLogger(ReflectionUtil.class);

}