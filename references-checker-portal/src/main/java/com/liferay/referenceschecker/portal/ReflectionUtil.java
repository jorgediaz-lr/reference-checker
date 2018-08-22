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

import com.liferay.portal.kernel.util.PortalClassLoaderUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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

	public static Object invokeMethod(
		Object object, String methodName, Class<?>[] parameterType,
		Object[] arg) {

		try {
			Method method = getMethod(object, methodName, parameterType);

			return method.invoke(object, arg);
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

}