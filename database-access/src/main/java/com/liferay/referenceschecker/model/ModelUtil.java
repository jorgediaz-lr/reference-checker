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

package com.liferay.referenceschecker.model;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.Map;
import java.util.Set;

/**
 * @author Jorge Díaz
 */
public interface ModelUtil {

	public String getClassName(String tableName);

	public Long getClassNameId(String className);

	public Set<String> getClassNames();

	public String getTableName(String className);

	public void init(
			Connection connection,
			Map<String, String> tableNameToClassNameMapping)
		throws SQLException;

}