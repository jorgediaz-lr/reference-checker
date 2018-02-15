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

import com.liferay.portal.kernel.util.StringPool;
import com.liferay.referenceschecker.dao.Query;

import java.util.Arrays;

/**
 * @author Jorge Díaz
 */
public class Reference implements Comparable<Reference>, Cloneable {

	public Reference(Query originQuery, Query destinationQuery) {
		this.originQuery = originQuery;
		this.destinationQuery = destinationQuery;

		if (destinationQuery == null) {
			hidden = true;
		}
	}

	public Reference clone() {
		Reference copy = new Reference(this.originQuery, this.destinationQuery);
		copy.setHidden(this.isHidden());
		copy.setRaw(this.isRaw());
		return copy;
	}

	@Override
	public int compareTo(Reference ref) {
		int value = originQuery.compareTo(ref.originQuery);

		if ((value == 0) && (isRaw() || ref.isRaw())) {
			value = destinationQuery.compareTo(ref.destinationQuery);
		}

		return value;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Reference)) {
			return false;
		}

		Reference ref = (Reference)obj;

		if (this.isRaw() || ref.isRaw()) {
			return originQuery.equals(ref.originQuery) &&
				destinationQuery.equals(ref.destinationQuery);
		}

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
		if (!isRaw()) {
			return originQuery.hashCode();
		}

		Object[] array = {originQuery, destinationQuery};

		return Arrays.hashCode(array);
	}

	public boolean isHidden() {
		return hidden;
	}

	public boolean isRaw() {
		return raw;
	}

	public String toString() {

		String rawRule = isRaw() ? "raw" : StringPool.BLANK;

		return String.valueOf(originQuery) + " => " +
			String.valueOf(destinationQuery) + " " + rawRule;
	}

	protected void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	protected void setRaw(boolean raw) {
		this.raw = raw;
	}

	protected Query destinationQuery;
	protected boolean hidden = false;
	protected Query originQuery;
	protected boolean raw = false;

}