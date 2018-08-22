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

package com.liferay.referenceschecker.main.util;

import com.liferay.portal.kernel.util.PortalClassLoaderUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * @author Jorge Díaz
 */
public class ReflectionUtil {

	public static Class<?> getPortalClass(String name)
		throws ClassNotFoundException {

		ClassLoader classLoader = PortalClassLoaderUtil.getClassLoader();

		if (classLoader == null) {
			Class<?> clazz = ReflectionUtil.class;

			classLoader = clazz.getClassLoader();
		}

		return classLoader.loadClass(name);
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