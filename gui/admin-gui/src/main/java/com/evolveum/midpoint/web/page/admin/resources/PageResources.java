/*
 * Copyright (c) 2010-2015 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.web.page.admin.resources;

import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.query.ObjectPaging;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.application.AuthorizationAction;
import com.evolveum.midpoint.web.application.PageDescriptor;
import com.evolveum.midpoint.web.component.data.BaseSortableDataProvider;
import com.evolveum.midpoint.web.component.data.BoxedTablePanel;
import com.evolveum.midpoint.web.component.data.ObjectDataProvider;
import com.evolveum.midpoint.web.component.data.Table;
import com.evolveum.midpoint.web.component.data.column.*;
import com.evolveum.midpoint.web.component.dialog.ConfirmationDialog;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.search.Search;
import com.evolveum.midpoint.web.component.search.SearchFactory;
import com.evolveum.midpoint.web.component.search.SearchFormPanel;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.page.admin.configuration.PageDebugView;
import com.evolveum.midpoint.web.page.admin.configuration.component.HeaderMenuAction;
import com.evolveum.midpoint.web.page.admin.resources.component.ContentPanel;
import com.evolveum.midpoint.web.page.admin.resources.content.PageContentAccounts;
import com.evolveum.midpoint.web.page.admin.resources.content.PageContentEntitlements;
import com.evolveum.midpoint.web.page.admin.resources.dto.ResourceController;
import com.evolveum.midpoint.web.page.admin.resources.dto.ResourceDto;
import com.evolveum.midpoint.web.page.admin.resources.dto.ResourceState;
import com.evolveum.midpoint.web.page.admin.server.dto.OperationResultStatusIcon;
import com.evolveum.midpoint.web.session.ResourcesStorage;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.web.util.OnePageParameterEncoder;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConnectorHostType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lazyman
 */
@PageDescriptor(url = "/admin/resources", action = {
        @AuthorizationAction(actionUri = PageAdminResources.AUTH_RESOURCE_ALL,
                label = PageAdminResources.AUTH_RESOURCE_ALL_LABEL,
                description = PageAdminResources.AUTH_RESOURCE_ALL_DESCRIPTION),
        @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_RESOURCES_URL,
                label = "PageResources.auth.resources.label",
                description = "PageResources.auth.resources.description")})
public class PageResources extends PageAdminResources {

    private static final Trace LOGGER = TraceManager.getTrace(PageResources.class);
    private static final String DOT_CLASS = PageResources.class.getName() + ".";
    private static final String OPERATION_TEST_RESOURCE = DOT_CLASS + "testResource";
    private static final String OPERATION_SYNC_STATUS = DOT_CLASS + "syncStatus";
    private static final String OPERATION_DELETE_RESOURCES = DOT_CLASS + "deleteResources";
    private static final String OPERATION_DELETE_HOSTS = DOT_CLASS + "deleteHosts";
    private static final String OPERATION_CONNECTOR_DISCOVERY = DOT_CLASS + "connectorDiscovery";

    private static final String ID_DELETE_RESOURCES_POPUP = "deleteResourcesPopup";
    private static final String ID_DELETE_HOSTS_POPUP = "deleteHostsPopup";
    private static final String ID_MAIN_FORM = "mainForm";
    private static final String ID_TABLE = "table";
    private static final String ID_CONNECTOR_TABLE = "connectorTable";

    private IModel<Search> searchModel;
    private IModel<Search> chSearchModel;
    private ResourceDto singleDelete;

    public PageResources() {
        this(true);
    }

    public PageResources(boolean clearSessionPaging) {
        this(clearSessionPaging, "");
    }

    public PageResources(String searchText) {
        this(true, searchText);
    }

    public PageResources(boolean clearSessionPaging, final String searchText) {
        getSessionStorage().clearPagingInSession(clearSessionPaging);

        searchModel = new LoadableModel<Search>(false) {

            @Override
            protected Search load() {
                ResourcesStorage storage = getSessionStorage().getResources();
                Search dto = storage.getResourceSearch();

                if (dto == null) {
                    dto = SearchFactory.createSearch(ResourceType.class, getPrismContext(), true);
                }

                return dto;
            }
        };

        chSearchModel = new LoadableModel<Search>(false) {

            @Override
            protected Search load() {
                return SearchFactory.createSearch(ConnectorHostType.class, getPrismContext(), true);
            }
        };

        initLayout();
    }

    private void initLayout() {
        Form mainForm = new Form(ID_MAIN_FORM);
        add(mainForm);

        BoxedTablePanel resources = new BoxedTablePanel(ID_TABLE, initResourceDataProvider(), initResourceColumns(),
                UserProfileStorage.TableId.PAGE_RESOURCES_PANEL,
                (int) getItemsPerPage(UserProfileStorage.TableId.PAGE_RESOURCES_PANEL)) {

            @Override
            protected WebMarkupContainer createHeader(String headerId) {
                return new SearchFormPanel(headerId, searchModel) {

                    @Override
                    protected void searchPerformed(ObjectQuery query, AjaxRequestTarget target) {
                        PageResources.this.searchPerformed(query, target);
                    }
                };
            }
        };
        resources.setOutputMarkupId(true);

        ResourcesStorage storage = getSessionStorage().getResources();
        resources.setCurrentPage(storage.getResourcePaging());

        mainForm.add(resources);

        BoxedTablePanel connectorHosts = new BoxedTablePanel(ID_CONNECTOR_TABLE,
                new ObjectDataProvider(PageResources.this, ConnectorHostType.class), initConnectorHostsColumns(),
                UserProfileStorage.TableId.PAGE_RESOURCES_CONNECTOR_HOSTS,
                (int) getItemsPerPage(UserProfileStorage.TableId.PAGE_RESOURCES_CONNECTOR_HOSTS)) {

            @Override
            protected WebMarkupContainer createHeader(String headerId) {
                return new SearchFormPanel(headerId, chSearchModel) {

                    @Override
                    protected void searchPerformed(ObjectQuery query, AjaxRequestTarget target) {
                        PageResources.this.searchHostPerformed(query, target);
                    }
                };
            }
        };
        connectorHosts.setOutputMarkupId(true);
        mainForm.add(connectorHosts);

        add(new ConfirmationDialog(ID_DELETE_RESOURCES_POPUP,
                createStringResource("pageResources.dialog.title.confirmDelete"),
                createDeleteConfirmString("pageResources.message.deleteResourceConfirm",
                        "pageResources.message.deleteResourcesConfirm", true)) {

            @Override
            public void yesPerformed(AjaxRequestTarget target) {
                close(target);
                deleteResourceConfirmedPerformed(target);
            }
        });

        add(new ConfirmationDialog(ID_DELETE_HOSTS_POPUP,
                createStringResource("pageResources.dialog.title.confirmDelete"),
                createDeleteConfirmString("pageResources.message.deleteHostConfirm",
                        "pageResources.message.deleteHostsConfirm", false)) {

            @Override
            public void yesPerformed(AjaxRequestTarget target) {
                close(target);
                deleteHostConfirmedPerformed(target);
            }
        });
    }

    private BaseSortableDataProvider initResourceDataProvider() {
        ObjectDataProvider provider = new ObjectDataProvider<ResourceDto, ResourceType>(this, ResourceType.class) {

            @Override
            public ResourceDto createDataObjectWrapper(PrismObject<ResourceType> obj) {
                return createRowDto(obj);
            }

            @Override
            protected void saveProviderPaging(ObjectQuery query, ObjectPaging paging) {
                ResourcesStorage storage = getSessionStorage().getResources();
                storage.setResourcePaging(paging);
            }

            @Override
            protected void handleNotSuccessOrHandledErrorInIterator(OperationResult result) {
                if (result.isPartialError()) {
                    showResult(result);
                } else {
                    super.handleNotSuccessOrHandledErrorInIterator(result);
                }
            }
        };

        return provider;
    }

    private ResourceDto createRowDto(PrismObject<ResourceType> object) {
        ResourceDto dto = new ResourceDto(object);
        dto.getMenuItems().add(new InlineMenuItem(createStringResource("PageBase.button.delete"),
                new ColumnMenuAction<ResourceDto>() {

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        ResourceDto rowDto = getRowModel().getObject();
                        deleteResourcePerformed(target, rowDto);
                    }
                }));

        dto.getMenuItems().add(new InlineMenuItem(createStringResource("pageResources.inlineMenuItem.deleteSyncToken"),
                new ColumnMenuAction<ResourceDto>() {

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        deleteResourceSyncTokenPerformed(target, getRowModel());
                    }

                }));

        dto.getMenuItems().add(new InlineMenuItem(createStringResource("pageResources.inlineMenuItem.editResource"),
                new ColumnMenuAction<ResourceDto>() {

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        editResourcePerformed(getRowModel());
                    }
                }));
        dto.getMenuItems().add(new InlineMenuItem(createStringResource("pageResources.button.editAsXml"),
                new ColumnMenuAction<ResourceDto>() {

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        editAsXmlPerformed(getRowModel());
                    }
                }));

        return dto;
    }

    private List<IColumn<ResourceDto, String>> initResourceColumns() {
        List<IColumn<ResourceDto, String>> columns = new ArrayList<>();

        IColumn column = new CheckBoxHeaderColumn<ResourceDto>();
        columns.add(column);

        column = new LinkColumn<ResourceDto>(createStringResource("pageResources.name"),
                "name", "name") {

            @Override
            public void onClick(AjaxRequestTarget target, IModel<ResourceDto> rowModel) {
                ResourceDto resource = rowModel.getObject();
                resourceDetailsPerformed(target, resource.getOid());
            }
        };
        columns.add(column);

        columns.add(new PropertyColumn(createStringResource("pageResources.connectorType"), "type"));
        columns.add(new PropertyColumn(createStringResource("pageResources.version"), "version"));

        column = new LinkIconColumn<ResourceDto>(createStringResource("pageResources.status")) {

            @Override
            protected IModel<String> createIconModel(final IModel<ResourceDto> rowModel) {
                return new AbstractReadOnlyModel<String>() {

                    @Override
                    public String getObject() {
//                        testResourcePerformed(getRequestCycle().find(AjaxRequestTarget.class), rowModel);
                        ResourceDto dto = rowModel.getObject();
                        ResourceController.updateLastAvailabilityState(dto.getState(),
                                dto.getLastAvailabilityStatus());
                        ResourceState state = dto.getState();
                        return OperationResultStatusIcon.parseOperationalResultStatus(state.getLastAvailability()).getIcon();
                    }
                };
            }

            @Override
            protected IModel<String> createTitleModel(final IModel<ResourceDto> rowModel) {
                return new AbstractReadOnlyModel<String>() {

                    @Override
                    public String getObject() {
                        ResourceState state = rowModel.getObject().getState();
                        return PageResources.this.getString(OperationResultStatus.class.getSimpleName() + "." + state
                                .getLastAvailability().name());
                    }
                };
            }

            @Override
            protected void onClickPerformed(AjaxRequestTarget target, IModel<ResourceDto> rowModel,
                                            AjaxLink link) {
                testResourcePerformed(target, rowModel);
                target.add(link);
            }
        };
        columns.add(column);

        columns.add(new AbstractColumn<ResourceDto, String>(createStringResource("pageResources.content")) {

            @Override
            public void populateItem(Item<ICellPopulator<ResourceDto>> cellItem,
                                     String componentId, final IModel<ResourceDto> model) {
                cellItem.add(new ContentPanel(componentId) {

                    @Override
                    public void accountsPerformed(AjaxRequestTarget target) {
                        ResourceDto dto = model.getObject();

                        PageParameters parameters = new PageParameters();
                        parameters.add(OnePageParameterEncoder.PARAMETER, dto.getOid());
                        setResponsePage(PageContentAccounts.class, parameters);
                    }

                    @Override
                    public void entitlementsPerformed(AjaxRequestTarget target) {
                        ResourceDto dto = model.getObject();

                        PageParameters parameters = new PageParameters();
                        parameters.add(OnePageParameterEncoder.PARAMETER, dto.getOid());
                        setResponsePage(PageContentEntitlements.class, parameters);
                    }
                });
            }
        });

        InlineMenuHeaderColumn menu = new InlineMenuHeaderColumn(initInlineMenu());
        columns.add(menu);

        return columns;
    }

    private List<InlineMenuItem> initInlineMenu() {
        List<InlineMenuItem> headerMenuItems = new ArrayList<>();
        headerMenuItems.add(new InlineMenuItem(createStringResource("PageBase.button.delete"),
                new HeaderMenuAction(this) {

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        deleteResourcePerformed(target, null);
                    }
                }));

        return headerMenuItems;
    }

    private List<IColumn<ConnectorHostType, String>> initConnectorHostsColumns() {
        List<IColumn<ConnectorHostType, String>> columns = new ArrayList<>();

        IColumn column = new CheckBoxHeaderColumn<ConnectorHostType>();
        columns.add(column);

        column = new LinkColumn<SelectableBean<ConnectorHostType>>(createStringResource("pageResources.connector.name"),
                "name", "value.name") {

            @Override
            public void onClick(AjaxRequestTarget target, IModel<SelectableBean<ConnectorHostType>> rowModel) {
                ConnectorHostType host = rowModel.getObject().getValue();
                //resourceDetailsPerformed(target, host.getOid());
            }
        };
        columns.add(column);

        columns.add(new PropertyColumn(createStringResource("pageResources.connector.hostname"),
                "value.hostname"));
        columns.add(new PropertyColumn(createStringResource("pageResources.connector.port"),
                "value.port"));
        columns.add(new PropertyColumn(createStringResource("pageResources.connector.timeout"),
                "value.timeout"));
        columns.add(new CheckBoxColumn(createStringResource("pageResources.connector.protectConnection"),
                "value.protectConnection"));

        InlineMenuHeaderColumn menu = new InlineMenuHeaderColumn(initInlineHostsMenu());
        columns.add(menu);

        return columns;
    }

    private List<InlineMenuItem> initInlineHostsMenu() {
        List<InlineMenuItem> headerMenuItems = new ArrayList<>();
        headerMenuItems.add(new InlineMenuItem(createStringResource("PageBase.button.delete"),
                new HeaderMenuAction(this) {

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        deleteHostPerformed(target);
                    }
                }));
        headerMenuItems.add(new InlineMenuItem(createStringResource("pageResources.button.discoveryRemote"),
                new HeaderMenuAction(this) {

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        discoveryRemotePerformed(target);
                    }
                }));

        return headerMenuItems;
    }

    private void resourceDetailsPerformed(AjaxRequestTarget target, String oid) {
        PageParameters parameters = new PageParameters();
        parameters.add(OnePageParameterEncoder.PARAMETER, oid);
        setResponsePage(PageResource.class, parameters);
    }

    private void deleteHostPerformed(AjaxRequestTarget target) {
        List<SelectableBean<ConnectorHostType>> selected = WebComponentUtil.getSelectedData(getConnectorHostTable());
        if (selected.isEmpty()) {
            warn(getString("pageResources.message.noHostSelected"));
            target.add(getFeedbackPanel());
            return;
        }

        ModalWindow dialog = (ModalWindow) get(ID_DELETE_HOSTS_POPUP);
        dialog.show(target);
    }

    private List<ResourceDto> isAnyResourceSelected(AjaxRequestTarget target, ResourceDto single) {
        return WebComponentUtil.isAnythingSelected(target, single, getResourceTable(), this,
                "pageResources.message.noResourceSelected");
    }

    private void deleteResourcePerformed(AjaxRequestTarget target, ResourceDto single) {
        List<ResourceDto> selected = isAnyResourceSelected(target, single);
        singleDelete = single;

        if (selected.isEmpty()) {
            return;
        }

        ModalWindow dialog = (ModalWindow) get(ID_DELETE_RESOURCES_POPUP);
        dialog.show(target);
    }

    private Table getResourceTable() {
        return (Table) get(createComponentPath(ID_MAIN_FORM, ID_TABLE));
    }

    private Table getConnectorHostTable() {
        return (Table) get(createComponentPath(ID_MAIN_FORM, ID_CONNECTOR_TABLE));
    }

    /**
     * @param oneDeleteKey  message if deleting one item
     * @param moreDeleteKey message if deleting more items
     * @param resources     if true selecting resources if false selecting from hosts
     */
    private IModel<String> createDeleteConfirmString(final String oneDeleteKey, final String moreDeleteKey,
                                                     final boolean resources) {
        return new AbstractReadOnlyModel<String>() {

            @Override
            public String getObject() {
                Table table = resources ? getResourceTable() : getConnectorHostTable();
                List selected = new ArrayList();

                if (singleDelete != null) {
                    selected.add(singleDelete);
                } else {
                    selected = WebComponentUtil.getSelectedData(table);
                }

                switch (selected.size()) {
                    case 1:
                        Object first = selected.get(0);
                        String name = resources ? ((ResourceDto) first).getName() :
                                WebComponentUtil.getName(((SelectableBean<ConnectorHostType>) first).getValue());
                        return createStringResource(oneDeleteKey, name).getString();
                    default:
                        return createStringResource(moreDeleteKey, selected.size()).getString();
                }
            }
        };
    }

    private void deleteHostConfirmedPerformed(AjaxRequestTarget target) {
        Table hostTable = getConnectorHostTable();
        List<SelectableBean<ConnectorHostType>> selected = WebComponentUtil.getSelectedData(hostTable);

        OperationResult result = new OperationResult(OPERATION_DELETE_HOSTS);
        for (SelectableBean<ConnectorHostType> selectable : selected) {
            try {
                Task task = createSimpleTask(OPERATION_DELETE_HOSTS);

                ObjectDelta<ConnectorHostType> delta = ObjectDelta.createDeleteDelta(ConnectorHostType.class,
                        selectable.getValue().getOid(), getPrismContext());
                getModelService().executeChanges(WebComponentUtil.createDeltaCollection(delta), null, task, result);
            } catch (Exception ex) {
                result.recordPartialError("Couldn't delete host.", ex);
                LoggingUtils.logException(LOGGER, "Couldn't delete host", ex);
            }
        }

        result.recomputeStatus();
        if (result.isSuccess()) {
            result.recordStatus(OperationResultStatus.SUCCESS, "The resource(s) have been successfully deleted.");
        }

        BaseSortableDataProvider provider = (BaseSortableDataProvider) hostTable.getDataTable().getDataProvider();
        provider.clearCache();

        showResult(result);
        target.add(getFeedbackPanel(), (Component) hostTable);
    }

    private void deleteResourceConfirmedPerformed(AjaxRequestTarget target) {
        List<ResourceDto> selected = new ArrayList<>();

        if (singleDelete != null) {
            selected.add(singleDelete);
        } else {
            selected = WebComponentUtil.getSelectedData(getResourceTable());
        }

        OperationResult result = new OperationResult(OPERATION_DELETE_RESOURCES);
        for (ResourceDto resource : selected) {
            try {
                Task task = createSimpleTask(OPERATION_DELETE_RESOURCES);

                ObjectDelta<ResourceType> delta = ObjectDelta.createDeleteDelta(ResourceType.class, resource.getOid(),
                        getPrismContext());
                getModelService().executeChanges(WebComponentUtil.createDeltaCollection(delta), null, task, result);
            } catch (Exception ex) {
                result.recordPartialError("Couldn't delete resource.", ex);
                LoggingUtils.logException(LOGGER, "Couldn't delete resource", ex);
            }
        }

        result.recomputeStatus();
        if (result.isSuccess()) {
            result.recordStatus(OperationResultStatus.SUCCESS, "The resource(s) have been successfully deleted.");
        }

        Table resourceTable = getResourceTable();
        ObjectDataProvider provider = (ObjectDataProvider) resourceTable.getDataTable().getDataProvider();
        provider.clearCache();

        showResult(result);
        target.add(getFeedbackPanel(), (Component) resourceTable);
    }

    private void discoveryRemotePerformed(AjaxRequestTarget target) {
        target.add(getFeedbackPanel());

        PageBase page = (PageBase) getPage();
        Task task = page.createSimpleTask(OPERATION_CONNECTOR_DISCOVERY);
        OperationResult result = task.getResult();
        List<SelectableBean<ConnectorHostType>> selected = WebComponentUtil.getSelectedData(getConnectorHostTable());
        if (selected.isEmpty()) {
            warn(getString("pageResources.message.noHostSelected"));
            return;
        }

        for (SelectableBean<ConnectorHostType> bean : selected) {
            ConnectorHostType host = bean.getValue();
            try {
                getModelService().discoverConnectors(host, task, result);
            } catch (Exception ex) {
                result.recordFatalError("Fail to discover connectors on host '" + host.getHostname()
                        + ":" + host.getPort() + "'", ex);
            }
        }

        result.recomputeStatus();
        showResult(result);
    }

    private void testResourcePerformed(AjaxRequestTarget target, IModel<ResourceDto> rowModel) {

        OperationResult result = new OperationResult(OPERATION_TEST_RESOURCE);

        ResourceDto dto = rowModel.getObject();
        if (StringUtils.isEmpty(dto.getOid())) {
            result.recordFatalError("Resource oid not defined in request");
        }

        Task task = createSimpleTask(OPERATION_TEST_RESOURCE);
        try {
            result = getModelService().testResource(dto.getOid(), task);
            ResourceController.updateResourceState(dto.getState(), result);

            // todo de-duplicate code (see the same operation in PageResource)
            // this provides some additional tests, namely a test for schema handling section
            getModelService().getObject(ResourceType.class, dto.getOid(), null, task, result);
        } catch (Exception ex) {
            result.recordFatalError("Failed to test resource connection", ex);
        }

        // a bit of hack: result of TestConnection contains a result of getObject as a subresult
        // so in case of TestConnection succeeding we recompute the result to show any (potential) getObject problems
        if (result.isSuccess()) {
            result.recomputeStatus();
        }

        if (!result.isSuccess()) {
            showResult(result);
            target.add(getFeedbackPanel());
        }
    }

    private void searchHostPerformed(ObjectQuery query, AjaxRequestTarget target) {
        target.add(getFeedbackPanel());

        Table panel = getConnectorHostTable();
        DataTable table = panel.getDataTable();
        ObjectDataProvider provider = (ObjectDataProvider) table.getDataProvider();
        provider.setQuery(query);
        provider.setOptions(SelectorOptions.createCollection(GetOperationOptions.createNoFetch()));

        target.add((Component) panel);
    }

    private void searchPerformed(ObjectQuery query, AjaxRequestTarget target) {
        target.add(getFeedbackPanel());

        Table panel = getResourceTable();
        DataTable table = panel.getDataTable();
        ObjectDataProvider provider = (ObjectDataProvider) table.getDataProvider();
        provider.setQuery(query);
        provider.setOptions(SelectorOptions.createCollection(GetOperationOptions.createNoFetch()));

        ResourcesStorage storage = getSessionStorage().getResources();
        storage.setResourceSearch(searchModel.getObject());
        panel.setCurrentPage(storage.getResourcePaging());

        target.add((Component) panel);
    }

    private void deleteResourceSyncTokenPerformed(AjaxRequestTarget target, IModel<ResourceDto> model) {
        deleteSyncTokenPerformed(target, model);
    }

    private void editResourcePerformed(IModel<ResourceDto> model) {
        PageParameters parameters = new PageParameters();
        parameters.add(OnePageParameterEncoder.PARAMETER, model.getObject().getOid());
        setResponsePage(new PageResourceWizard(parameters));
    }

    private void editAsXmlPerformed(IModel<ResourceDto> model) {
        PageParameters parameters = new PageParameters();
        parameters.add(PageDebugView.PARAM_OBJECT_ID, model.getObject().getOid());
        parameters.add(PageDebugView.PARAM_OBJECT_TYPE, ResourceType.class.getSimpleName());
        setResponsePage(PageDebugView.class, parameters);
    }
}
