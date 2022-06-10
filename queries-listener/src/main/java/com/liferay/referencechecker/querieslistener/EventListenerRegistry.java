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

package com.liferay.referencechecker.querieslistener;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Jorge Díaz
 */
public class EventListenerRegistry {

	public static final EventListenerRegistry INSTANCE =
		new EventListenerRegistry();

	public static EventListenerRegistry getEventListenerRegistry() {
		return INSTANCE;
	}

	public EventListener getEventListener(String className) {
		return _eventListeners.get(className);
	}

	public Set<EventListener> getEventListeners() {
		return new HashSet<>(_eventListeners.values());
	}

	public void register(EventListener eventListener) {
		Class<?> clazz = eventListener.getClass();

		_eventListeners.put(clazz.getName(), eventListener);
	}

	public void unregister(EventListener eventListener) {
		Class<?> clazz = eventListener.getClass();

		unregister(clazz.getName());
	}

	public void unregister(String listenerName) {
		_eventListeners.remove(listenerName);
	}

	private final Map<String, EventListener> _eventListeners =
		new ConcurrentHashMap<>();

}