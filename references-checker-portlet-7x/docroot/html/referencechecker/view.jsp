<%--
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
--%>

<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/sql" prefix="sql" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/xml" prefix="x" %>

<%@ taglib uri="http://liferay.com/tld/aui" prefix="aui" %>
<%@ taglib uri="http://liferay.com/tld/portlet" prefix="liferay-portlet" %>
<%@ taglib uri="http://liferay.com/tld/security" prefix="liferay-security" %>
<%@ taglib uri="http://liferay.com/tld/theme" prefix="liferay-theme" %>
<%@ taglib uri="http://liferay.com/tld/ui" prefix="liferay-ui" %>
<%@ taglib uri="http://liferay.com/tld/util" prefix="liferay-util" %>

<%@ page contentType="text/html; charset=UTF-8" %>

<%@ page import="com.liferay.portal.kernel.dao.search.SearchContainer" %>
<%@ page import="com.liferay.referencechecker.dao.Table" %>
<%@ page import="com.liferay.referencechecker.portlet.ReferenceCheckerPortlet" %>
<%@ page import="com.liferay.referencechecker.ref.MissingReferences" %>
<%@ page import="com.liferay.referencechecker.ref.Reference" %>

<%@ page import="java.util.Collection" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>

<%@ page import="javax.portlet.PortletURL" %>

<portlet:defineObjects />

<portlet:renderURL var="viewURL" />

<portlet:actionURL name="executeCheckReferences" var="executeCheckReferencesURL" windowState="normal" />
<portlet:actionURL name="executeMappingList" var="executeMappingListURL" windowState="normal" />

<div class="container-fluid-1280"><div class="card-horizontal main-content-card"><div class="panel-body">
<div class="alert alert-warning"><liferay-ui:message key="disclaimer" /></div>
<aui:form action="<%= executeCheckReferencesURL %>" method="POST" name="fm">
	<aui:col width="33">
		<aui:input name="ignoreNullValues" type="checkbox" value="true" />
		<aui:input name="ignoreEmptyTables" type="checkbox" value="false" />
	</aui:col>
	<aui:col width="33">
		<aui:input name="excludeColumns"  style="width: 100%;" type="text" value="userId" />
	</aui:col>
	<aui:button-row>
		<aui:button type="submit" value="execute" />
		<aui:button onClick='<%= renderResponse.getNamespace() + "mappingList();" %>' value="mapping-list" />

<%
	String exportCsvResourceURL = (String)request.getAttribute("exportCsvResourceURL");
	if (exportCsvResourceURL != null) {
		exportCsvResourceURL = "window.open('" + exportCsvResourceURL + "');";
%>

		<aui:button onClick="<%= exportCsvResourceURL %>" value="export-to-csv" />

<%
	}
%>

		<aui:button onClick="<%= viewURL %>" value="clean" />
	</aui:button-row>
</aui:form>

<%
	String jsonData = (String) request.getAttribute("jsonData");

	if (jsonData != null) {
%>

	<%@ include file="/html/referencechecker/output.jspf" %>

<%
	}
%>

</div></div></div>

<aui:script>
	function <portlet:namespace />mappingList() {
		document.<portlet:namespace />fm.action = "<%= executeMappingListURL %>";

		submitForm(document.<portlet:namespace />fm);
	}
</aui:script>