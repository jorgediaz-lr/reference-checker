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

package com.liferay.referenceschecker.ref;

import com.liferay.referenceschecker.dao.Query;

/**
 * @author Jorge Díaz
 */
public class Reference implements Comparable<Reference>, Cloneable {

	public Reference(Query originQuery, Query destinationQuery) {
		this.originQuery = originQuery;
		this.destinationQuery = destinationQuery;
	}

	public Reference clone() {
		return new Reference(this.originQuery, this.destinationQuery);
	}

	@Override
	public int compareTo(Reference ref) {
		return originQuery.compareTo(ref.originQuery);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Reference)) {
			return false;
		}

		Reference ref = (Reference)obj;
		return originQuery.equals(ref.originQuery);
	}

	public Query getDestinationQuery() {
		return destinationQuery;
	}

	public Query getOriginQuery() {
		return originQuery;
	}

	@Override
	public int hashCode() {
		return originQuery.hashCode();
	}

	public String toString() {

		return String.valueOf(originQuery) + " => " +
			String.valueOf(destinationQuery);
	}

	protected Query destinationQuery;
	protected Query originQuery;

}