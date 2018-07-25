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

package com.liferay.referenceschecker.util;

/**
 * @author Jorge Díaz
 */
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.DynamicQueryFactoryUtil;
import com.liferay.portal.kernel.dao.orm.Property;
import com.liferay.portal.kernel.dao.orm.PropertyFactoryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.MimeTypesUtil;
import com.liferay.portal.kernel.util.PortalClassLoaderUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.UnicodeProperties;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.model.Group;
import com.liferay.portal.model.Repository;
import com.liferay.portal.model.User;
import com.liferay.portal.service.GroupLocalServiceUtil;
import com.liferay.portal.service.RepositoryLocalServiceUtil;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portlet.documentlibrary.model.DLFolderConstants;
import com.liferay.portlet.documentlibrary.service.DLAppLocalServiceUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import java.lang.reflect.Method;

import java.util.List;
public class PortletFileRepositoryUtil {

	public static FileEntry addPortletFileEntry(
			Repository repository, InputStream inputStream, long userId,
			String title, String mimeType)
		throws PortalException, SystemException {

		if (inputStream == null) {
			return null;
		}

		File file = null;

		try {
			file = FileUtil.createTempFile(inputStream);

			return addPortletFileEntry(
					repository, userId, StringPool.BLANK, (long) 0, file, title,
					mimeType, true);
		}
		catch (IOException ioe) {
			throw new SystemException("Unable to write temporary file", ioe);
		}
		finally {
			FileUtil.delete(file);
		}
	}

	public static Repository addPortletRepository(
			long groupId, String portletId, ServiceContext serviceContext)
		throws PortalException, SystemException {

		Group group = GroupLocalServiceUtil.getGroup(groupId);

		User user = UserLocalServiceUtil.getDefaultUser(group.getCompanyId());

		long classNameId = PortalUtil.getClassNameId(LIFERAY_REPOSITORY);

		UnicodeProperties typeSettingsProperties = new UnicodeProperties();

		try {
			Class<?> repositoryServiceClass = RepositoryLocalServiceUtil.class;
			Class[] parameterTypes = {
				long.class, long.class, long.class, long.class, String.class,
				String.class, String.class, UnicodeProperties.class,
				ServiceContext.class };

			Method addRepositoryMethod = repositoryServiceClass.getMethod(
				"addRepository", parameterTypes);

			Object object = addRepositoryMethod.invoke(
				null, user.getUserId(), groupId, classNameId,
				DLFolderConstants.DEFAULT_PARENT_FOLDER_ID, portletId,
				StringPool.BLANK, portletId, typeSettingsProperties,
				serviceContext);

			if (object instanceof Repository) {
				return (Repository)object;
			}

			if (object instanceof Long) {
				long repositoryId = (Long)object;

				return RepositoryLocalServiceUtil.fetchRepository(repositoryId);
			}

			return null;
		}
		catch (Throwable t) {
			throw new SystemException(t);
		}
	}

	public static Repository fetchRepository(
			long groupId, String name, String portletId)
		throws SystemException {

		Property groupIdProperty = PropertyFactoryUtil.forName("groupId");
		Property nameProperty = PropertyFactoryUtil.forName("name");
		Property portletIdProperty = PropertyFactoryUtil.forName("portletId");

		DynamicQuery dynamicQuery = DynamicQueryFactoryUtil.forClass(
			Repository.class, PortalClassLoaderUtil.getClassLoader());

		dynamicQuery.add(groupIdProperty.eq(groupId));
		dynamicQuery.add(nameProperty.eq(name));
		dynamicQuery.add(portletIdProperty.eq(portletId));

		@SuppressWarnings("unchecked")
		List<Repository> result =
			(List<Repository>)RepositoryLocalServiceUtil.dynamicQuery(
				dynamicQuery);

		if (result.size() == 0) {
			return null;
		}

		return (Repository)result.get(0);
	}

	public static Repository getPortletRepository(
			long groupId, String portletId)
		throws PortalException, SystemException {

		Repository repository = fetchRepository(groupId, portletId, portletId);

		if (repository == null) {
			repository = addPortletRepository(
				groupId, portletId, new ServiceContext());
		}

		return repository;
	}

	protected static FileEntry addPortletFileEntry(
			Repository repository, long userId, String className, long classPK,
			File file, String fileName, String mimeType,
			boolean indexingEnabled)
		throws PortalException, SystemException {

		if (Validator.isNull(fileName)) {
			return null;
		}

		ServiceContext serviceContext = new ServiceContext();

		serviceContext.setAddGroupPermissions(true);
		serviceContext.setAddGuestPermissions(true);

		serviceContext.setAttribute("className", className);
		serviceContext.setAttribute("classPK", String.valueOf(classPK));
		serviceContext.setIndexingEnabled(indexingEnabled);

		if (Validator.isNull(mimeType) ||
			mimeType.equals(ContentTypes.APPLICATION_OCTET_STREAM)) {

			mimeType = MimeTypesUtil.getContentType(file, fileName);
		}

		return DLAppLocalServiceUtil.addFileEntry(
			userId, repository.getRepositoryId(), repository.getDlFolderId(),
			fileName, mimeType, fileName, StringPool.BLANK, StringPool.BLANK,
			file, serviceContext);
	}

	private static String LIFERAY_REPOSITORY =
		"com.liferay.portal.repository.liferayrepository.LiferayRepository";

}