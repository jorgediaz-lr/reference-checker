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

package com.liferay.referencechecker.portlet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.liferay.portal.kernel.dao.jdbc.DataAccess;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.portlet.PortletResponseUtil;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.util.JavaConstants;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.model.Company;
import com.liferay.portal.model.Repository;
import com.liferay.portal.service.CompanyLocalServiceUtil;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portlet.documentlibrary.NoSuchFileEntryException;
import com.liferay.portlet.documentlibrary.model.DLFileEntry;
import com.liferay.portlet.documentlibrary.service.DLAppLocalServiceUtil;
import com.liferay.portlet.documentlibrary.service.DLFileEntryLocalServiceUtil;
import com.liferay.referencechecker.OutputUtil;
import com.liferay.referencechecker.ReferenceChecker;
import com.liferay.referencechecker.model.ModelUtil;
import com.liferay.referencechecker.model.ModelUtilImpl;
import com.liferay.referencechecker.ref.MissingReferences;
import com.liferay.referencechecker.ref.Reference;
import com.liferay.referencechecker.util.PortletFileRepositoryUtil;
import com.liferay.util.bridges.mvc.MVCPortlet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.sql.Connection;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletConfig;
import javax.portlet.PortletException;
import javax.portlet.PortletRequest;
import javax.portlet.PortletResponse;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.portlet.ResourceURL;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

/**
 * Portlet implementation class ReferenceCheckerPortlet
 *
 * @author Jorge Díaz
 */
public class ReferenceCheckerPortlet extends MVCPortlet {

	public static FileEntry addPortletOutputFileEntry(
		long groupId, String portletId, long userId, String outputContent) {

		if (Validator.isNull(outputContent)) {
			return null;
		}

		try {
			InputStream inputStream = new ByteArrayInputStream(
				outputContent.getBytes(StringPool.UTF8));

			Repository repository =
				PortletFileRepositoryUtil.getPortletRepository(
					groupId, portletId);

			ReferenceCheckerPortlet.cleanupPortletFileEntries(
				repository, 8 * 60);

			String fileName =
				portletId + "_output_" + userId + "_" +
					System.currentTimeMillis() + ".csv";

			return PortletFileRepositoryUtil.addPortletFileEntry(
				repository, inputStream, userId, fileName, "text/csv");
		}
		catch (Throwable t) {
			_log.error(t, t);

			return null;
		}
	}

	public static void cleanupPortletFileEntries(
			Repository repository, long minutes)
		throws PortalException, SystemException {

		if (repository == null) {
			return;
		}

		List<DLFileEntry> dlFileEntries =
			DLFileEntryLocalServiceUtil.getFileEntries(
				repository.getGroupId(), repository.getDlFolderId(),
				QueryUtil.ALL_POS, QueryUtil.ALL_POS, null);

		for (DLFileEntry dlFileEntry : dlFileEntries) {
			long fileEntryDate = dlFileEntry.getCreateDate().getTime();
			long delta = minutes * 60 *1000;

			if ((fileEntryDate + delta) < System.currentTimeMillis()) {
				DLAppLocalServiceUtil.deleteFileEntry(
					dlFileEntry.getFileEntryId());
			}
		}
	}

	public static void servePortletFileEntry(
			long groupId, String portletId, String title,
			ResourceRequest request, ResourceResponse response)
		throws IOException, NoSuchFileEntryException, PortalException,
			SystemException {

		Repository repository = PortletFileRepositoryUtil.getPortletRepository(
			groupId, portletId);

		DLFileEntry dlFileEntry = DLFileEntryLocalServiceUtil.getFileEntry(
			repository.getGroupId(), repository.getDlFolderId(), title);

		InputStream inputStream = dlFileEntry.getContentStream();

		String mimeType = dlFileEntry.getMimeType();

		PortletResponseUtil.sendFile(
			request, response, title, inputStream, -1, mimeType);
	}

	public void doView(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		String exportCsvTitle = (String)renderRequest.getAttribute(
			"exportCsvTitle");

		if (exportCsvTitle != null) {
			ResourceURL exportCsvResourceURL =
				renderResponse.createResourceURL();
	
			exportCsvResourceURL.setResourceID(exportCsvTitle);
	
			renderRequest.setAttribute(
				"exportCsvResourceURL", exportCsvResourceURL.toString());
		}

		super.doView(renderRequest, renderResponse);
	}

	public void executeCheckReferences(
			ActionRequest request, ActionResponse response)
		throws Exception {

		long startTime = System.currentTimeMillis();

		PortalUtil.copyRequestParameters(request, response);

		boolean ignoreNullValues = ParamUtil.getBoolean(
			request, "ignoreNullValues");
		String excludeColumnsParam = ParamUtil.getString(
			request, "excludeColumns");

		Connection connection = null;

		List<MissingReferences> listMissingReferences = null;

		try {
			connection = DataAccess.getConnection();

			ReferenceChecker referenceChecker = new ReferenceChecker(
				connection);

			if (excludeColumnsParam != null) {
				List<String> excludeColumns =
					Arrays.asList(excludeColumnsParam.split(","));

				referenceChecker.addExcludeColumns(excludeColumns);
			}

			referenceChecker.setIgnoreNullValues(ignoreNullValues);
			referenceChecker.initModelUtil(connection, getModelUtil());
			referenceChecker.initTableUtil(connection);

			listMissingReferences = referenceChecker.execute(connection);
		}
		finally {
			DataAccess.cleanUp(connection);
		}

		PortletConfig portletConfig =
			(PortletConfig)request.getAttribute(
				JavaConstants.JAVAX_PORTLET_CONFIG);

		String portletId = portletConfig.getPortletName();

		List<String> outputList = OutputUtil.generateCSVOutputCheckReferences(
			listMissingReferences, -1);

		handleOutput(request, response, portletId, outputList);

		long endTime = System.currentTimeMillis();

		response.setRenderParameter("processTime", "" + (endTime-startTime));
	}

	public void executeMappingList(
			ActionRequest request, ActionResponse response)
		throws Exception {

		long startTime = System.currentTimeMillis();

		PortalUtil.copyRequestParameters(request, response);

		boolean ignoreEmptyTables = ParamUtil.getBoolean(
			request, "ignoreEmptyTables");
		String excludeColumnsParam = ParamUtil.getString(
			request, "excludeColumns");

		Connection connection = null;

		Collection<Reference> references = null;

		try {
			connection = DataAccess.getConnection();

			ReferenceChecker referenceChecker = new ReferenceChecker(
				connection);

			if (excludeColumnsParam != null) {
				List<String> excludeColumns =
					Arrays.asList(excludeColumnsParam.split(","));

				referenceChecker.addExcludeColumns(excludeColumns);
			}

			referenceChecker.initModelUtil(connection, getModelUtil());
			referenceChecker.initTableUtil(connection);


			references = referenceChecker.calculateReferences(
				connection, ignoreEmptyTables);
		}
		finally {
			DataAccess.cleanUp(connection);
		}

		PortletConfig portletConfig =
			(PortletConfig)request.getAttribute(
				JavaConstants.JAVAX_PORTLET_CONFIG);

		String portletId = portletConfig.getPortletName();

		List<String> outputList = OutputUtil.generateCSVOutputMappingList(
			references);

		handleOutput(request, response, portletId, outputList);

		long endTime = System.currentTimeMillis();

		response.setRenderParameter("processTime", "" + (endTime-startTime));
	}

	public void serveResource(
			ResourceRequest request, ResourceResponse response)
		throws IOException, PortletException {

		try {
			PortletConfig portletConfig =
				(PortletConfig)request.getAttribute(
					JavaConstants.JAVAX_PORTLET_CONFIG);

			long groupId = getGlobalGroupId();
			String portletId = portletConfig.getPortletName();
			String resourceId = request.getResourceID();

			ReferenceCheckerPortlet.servePortletFileEntry(
				groupId, portletId, resourceId, request, response);
		}
		catch (NoSuchFileEntryException nsfe) {
			if (_log.isWarnEnabled()) {
				_log.warn(nsfe.getMessage());
			}

			response.setProperty(
				ResourceResponse.HTTP_STATUS_CODE,
				Integer.toString(HttpServletResponse.SC_NOT_FOUND));
		}
		catch (Exception e) {
			_log.error(e, e);

			response.setProperty(
				ResourceResponse.HTTP_STATUS_CODE,
				Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
		}
	}

	protected static long getGlobalGroupId() throws PortletException {
		try {
			List<Company> companies = CompanyLocalServiceUtil.getCompanies(
				false);

			return companies.get(0).getGroup().getGroupId();
		}
		catch (Exception e) {
			throw new PortletException(e);
		}
	}

	protected ModelUtil getModelUtil() {
		String className = "com.liferay.referencechecker.portal.ModelUtilImpl";

		try {
			Class<?> clazz = Class.forName(className);

			return (ModelUtil) clazz.newInstance(); 
		}
		catch (Throwable t) {
			_log.info(
				className + " is not available in classloader. Exception: " +
				t.getMessage());
		}

		return new ModelUtilImpl();
	}

	protected void handleOutput(
		PortletRequest request, PortletResponse response,
		String portletId, List<String> outputList)
		throws PortletException, IOException, JsonProcessingException {

		long groupId = getGlobalGroupId();
		long userId = PortalUtil.getUserId(request);

		String outputContent = StringUtils.join(
			outputList, StringPool.NEW_LINE);

		FileEntry exportCsvFileEntry =
			ReferenceCheckerPortlet.addPortletOutputFileEntry(
				groupId, portletId, userId, outputContent);

		if (exportCsvFileEntry != null) {
			request.setAttribute(
				"exportCsvTitle", exportCsvFileEntry.getTitle());
		}

		List<Map<?, ?>> data = OutputUtil.csvToMapList(outputContent);
		String jsonData = OutputUtil.mapListToJSON(data);

		request.setAttribute("jsonData", jsonData);
	}

	private static Log _log = LogFactoryUtil.getLog(
			ReferenceCheckerPortlet.class);

}