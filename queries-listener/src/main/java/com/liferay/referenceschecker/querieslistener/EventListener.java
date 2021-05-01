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

package com.liferay.referenceschecker.querieslistener;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author Jorge Díaz
 */
public interface EventListener {

	public void afterCommit(
		int connectionId, Connection connection, SQLException e);

	public void afterConnectionClose(
		int connectionId, Connection connection, SQLException e);

	public void afterGetConnection(
		int connectionId, Connection connection, SQLException e);

	public void afterQuery(
		int connectionId, Connection connection, Query query, SQLException e);

	public void afterRollback(
		int connectionId, Connection connection, SQLException e);

	public void beforeCommit(int connectionId, Connection connection);

	public void beforeGetConnection(int connectionId, Connection connection);

	public void beforeQuery(
		int connectionId, Connection connection, Query query);

	public void beforeRollback(int connectionId, Connection connection);

	public void deleteConnectionData(int connectionId);

	public void resetConnectionData(int connectionId);

}