<html>
<head>
    <meta name="layout" content="main" />
    <title><g:message code="dashboard.list.label"/></title>

</head>

<body class="dashboard">

	<ui:flashMessage/>

	<div class="btn-group toolbar">
		<a id="createButton" class="btn btn-primary" href="${createLink(action:'create')}">
			<i class="fa fa-plus"></i> Create a new dashboard
		</a>        	
	</div>
	
	<div class="panel">
            <div class="panel-heading">
            	<span class="panel-title">
            		<g:message code="dashboard.list.label" />
            	</span>
            </div>
            
            <div class="panel-body">
            
				<ui:clickableTable>
				    <thead>
				        <tr>
				            <th>Name</th>
				            <th>Created</th>
				            <th>Modified</th>
				        </tr>
				    </thead>
				    <tbody>
					    <g:each in="${dashboards}" status="i" var="dashboard">
					    	<ui:clickableRow title="Show or edit dashboard" link="${createLink(action: 'show', id:dashboard.id) }" id="${dashboard.id}">
					            <td>${dashboard.name}</td>					        
					           	<td>${dashboard.dateCreated.format("yyyy-MM-dd")}</td>
					            <td>${dashboard.lastUpdated.format("yyyy-MM-dd")}</td>	
				            </ui:clickableRow>	            	
						</g:each>
					</tbody>
				</ui:clickableTable>
            </div> <%-- end panel body --%>
        </div> <%-- end panel --%>
</body>
</html>
