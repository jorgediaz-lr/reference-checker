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

package com.liferay.referenceschecker.portlet;

import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.dao.search.SearchContainer;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.language.LanguageUtil;
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
import com.liferay.referenceschecker.ReferencesChecker;
import com.liferay.referenceschecker.dao.Query;
import com.liferay.referenceschecker.dao.Table;
import com.liferay.referenceschecker.output.ReferencesCheckerOutput;
import com.liferay.referenceschecker.ref.MissingReferences;
import com.liferay.referenceschecker.ref.Reference;
import com.liferay.referenceschecker.util.PortletFileRepositoryUtil;
import com.liferay.referenceschecker.util.SQLUtil;
import com.liferay.referenceschecker.util.StringUtil;
import com.liferay.util.bridges.mvc.MVCPortlet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletConfig;
import javax.portlet.PortletException;
import javax.portlet.PortletURL;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.portlet.ResourceURL;

import javax.servlet.http.HttpServletResponse;

/**
 * Portlet implementation class ReferencesCheckerPortlet
 *
 * @author Jorge Díaz
 */
public class ReferencesCheckerPortlet extends MVCPortlet {

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

			ReferencesCheckerPortlet.cleanupPortletFileEntries(
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

	public static List<String> generateCSVOutputCheckReferences(
		PortletConfig portletConfig, Locale locale,
		List<MissingReferences> listMissingReferences) {

		if (listMissingReferences == null) {
			return null;
		}

		String[] headerKeys = new String[] {
			"output.table-name", "output.attributes", "output.destination",
			"output.attributes", "output.number", "output.missing-references"};

		List<String> headers = getHeaders(portletConfig, locale, headerKeys);

		return ReferencesCheckerOutput.generateCSVOutputCheckReferences(
			headers, listMissingReferences, -1);
	}

	public static List<String> generateCSVOutputMappingList(
		PortletConfig portletConfig, Locale locale,
		Collection<Reference> references) {

		if (references == null) {
			return null;
		}

		String[] headerKeys = new String[] {
			"output.table-name", "output.attributes", "output.destination",
			"output.attributes"};

		List<String> headers = getHeaders(portletConfig, locale, headerKeys);

		return ReferencesCheckerOutput.generateCSVOutputMappingList(
			headers, references);
	}

	public static SearchContainer<MissingReferences>
		generateSearchContainerCheckReferences(
			PortletConfig portletConfig, RenderRequest renderRequest,
			List<MissingReferences> listMissingReferences, PortletURL serverURL)
		throws SystemException {

		Locale locale = renderRequest.getLocale();

		String[] headerKeys = new String[] {
			"output.table-name", "output.attributes", "output.destination",
			"output.attributes", "output.missing-references"};

		List<String> headers = getHeaders(portletConfig, locale, headerKeys);

		return ReferencesCheckerOutput.generateSearchContainerCheckReferences(
			renderRequest, serverURL, headers, listMissingReferences);
	}

	public static
		SearchContainer<Reference> generateSearchContainerMappingList(
			PortletConfig portletConfig, RenderRequest renderRequest,
			List<Reference> referecesList, PortletURL serverURL)
		throws SystemException {

		Locale locale = renderRequest.getLocale();

		String[] headerKeys = new String[] {
			"output.table-name", "output.attributes", "output.destination",
			"output.attributes"};

		List<String> headers = getHeaders(portletConfig, locale, headerKeys);

		return ReferencesCheckerOutput.generateSearchContainerMappingList(
			renderRequest, serverURL, headers, referecesList);
	}

	public static
		Map<Table, SearchContainer<Reference>>
			generateSearchContainersMappingList(
			PortletConfig portletConfig, RenderRequest renderRequest,
			Collection<Reference> references, PortletURL serverURL)
		throws SystemException {

		Map<Table, List<Reference>> referenceListMap =
			new TreeMap<Table, List<Reference>>();

		for (Reference reference : references) {
			Query originQuery = reference.getOriginQuery();
			Table originTable = originQuery.getTable();

			if (!referenceListMap.containsKey(originTable)) {
				referenceListMap.put(originTable, new ArrayList<Reference>());
			}

			referenceListMap.get(originTable).add(reference);
		}

		Map<Table, SearchContainer<Reference>> searchContainerMap =
			new TreeMap<Table, SearchContainer<Reference>>();

		for (Entry<Table, List<Reference>> e : referenceListMap.entrySet()) {
			SearchContainer<Reference> sc =
				ReferencesCheckerPortlet.generateSearchContainerMappingList(
					portletConfig, renderRequest, e.getValue(), serverURL);
			searchContainerMap.put(e.getKey(), sc);
		}

		return searchContainerMap;
	}

	public static List<String> getHeaders(
		PortletConfig portletConfig, Locale locale, String[] headerKeys) {

		List<String> headers = new ArrayList<String>();

		for (int i = 0; i<headerKeys.length; i++) {
			headers.add(LanguageUtil.get(portletConfig, locale, headerKeys[i]));
		}

		return headers;
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

	@SuppressWarnings("unchecked")
	public void doView(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		PortletConfig portletConfig =
			(PortletConfig)renderRequest.getAttribute(
				JavaConstants.JAVAX_PORTLET_CONFIG);

		List<String> outputList = null;

		List<MissingReferences> listMissingReferences =
			(List<MissingReferences>)renderRequest.getAttribute(
				"missingReferencesList");

		if (listMissingReferences != null) {
			outputList =
				ReferencesCheckerPortlet.generateCSVOutputCheckReferences(
					portletConfig, renderRequest.getLocale(),
					listMissingReferences);
		}

		Collection<Reference> references =
			(Collection<Reference>)renderRequest.getAttribute("references");

		if (references != null) {
			outputList = ReferencesCheckerPortlet.generateCSVOutputMappingList(
				portletConfig, renderRequest.getLocale(), references);
		}

		long groupId = getGlobalGroupId();
		String portletId = portletConfig.getPortletName();
		long userId = PortalUtil.getUserId(renderRequest);

		String outputContent = StringUtil.merge(
				outputList, StringPool.NEW_LINE);

		FileEntry exportCsvFileEntry =
			ReferencesCheckerPortlet.addPortletOutputFileEntry(
				groupId, portletId, userId, outputContent);

		if (exportCsvFileEntry != null) {
			ResourceURL exportCsvResourceURL =
				renderResponse.createResourceURL();
			exportCsvResourceURL.setResourceID(exportCsvFileEntry.getTitle());

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

		String dbType = SQLUtil.getDBType();

		boolean ignoreNullValues = ParamUtil.getBoolean(
			request, "ignoreNullValues");
		String excludeColumnsParam = ParamUtil.getString(
			request, "excludeColumns");

		List<String> excludeColumns = null;

		if (excludeColumnsParam != null) {
			excludeColumns = Arrays.asList(excludeColumnsParam.split(","));
		}

		ReferencesChecker referencesChecker = new ReferencesChecker(
			dbType, excludeColumns, ignoreNullValues, false);

		List<MissingReferences> listMissingReferences =
			referencesChecker.execute();

		long endTime = System.currentTimeMillis();

		request.setAttribute("missingReferencesList", listMissingReferences);

		response.setRenderParameter("processTime", "" + (endTime-startTime));
	}

	public void executeMappingList(
			ActionRequest request, ActionResponse response)
		throws Exception {

		long startTime = System.currentTimeMillis();

		PortalUtil.copyRequestParameters(request, response);

		String dbType = SQLUtil.getDBType();

		boolean ignoreEmptyTables = ParamUtil.getBoolean(
			request, "ignoreEmptyTables");
		String excludeColumnsParam = ParamUtil.getString(
			request, "excludeColumns");

		List<String> excludeColumns = null;

		if (excludeColumnsParam != null) {
			excludeColumns = Arrays.asList(excludeColumnsParam.split(","));
		}

		ReferencesChecker referencesChecker = new ReferencesChecker(
			dbType, excludeColumns, true, false);

		Collection<Reference> references =
			referencesChecker.calculateReferences(ignoreEmptyTables);

		long endTime = System.currentTimeMillis();

		request.setAttribute("references", references);

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

			ReferencesCheckerPortlet.servePortletFileEntry(
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

	private static Log _log = LogFactoryUtil.getLog(
			ReferencesCheckerPortlet.class);

}