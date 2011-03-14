/*
 * RHQ Management Platform
 * Copyright (C) 2005-2010 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.enterprise.gui.coregui.client.dashboard.portlets.groups;

import java.util.ArrayList;
import java.util.List;

import com.allen_sauer.gwt.log.client.Log;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.smartgwt.client.widgets.Canvas;
import com.smartgwt.client.widgets.HTMLFlow;
import com.smartgwt.client.widgets.form.DynamicForm;
import com.smartgwt.client.widgets.form.events.SubmitValuesEvent;
import com.smartgwt.client.widgets.form.events.SubmitValuesHandler;
import com.smartgwt.client.widgets.form.fields.LinkItem;
import com.smartgwt.client.widgets.form.fields.SelectItem;
import com.smartgwt.client.widgets.form.fields.StaticTextItem;
import com.smartgwt.client.widgets.layout.VLayout;

import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.domain.dashboard.DashboardPortlet;
import org.rhq.core.domain.measurement.composite.MeasurementOOBComposite;
import org.rhq.core.domain.util.PageList;
import org.rhq.enterprise.gui.coregui.client.UserSessionManager;
import org.rhq.enterprise.gui.coregui.client.dashboard.AutoRefreshPortlet;
import org.rhq.enterprise.gui.coregui.client.dashboard.CustomSettingsPortlet;
import org.rhq.enterprise.gui.coregui.client.dashboard.Portlet;
import org.rhq.enterprise.gui.coregui.client.dashboard.PortletViewFactory;
import org.rhq.enterprise.gui.coregui.client.dashboard.PortletWindow;
import org.rhq.enterprise.gui.coregui.client.dashboard.portlets.PortletConfigurationEditorComponent;
import org.rhq.enterprise.gui.coregui.client.dashboard.portlets.PortletConfigurationEditorComponent.Constant;
import org.rhq.enterprise.gui.coregui.client.gwt.GWTServiceLookup;
import org.rhq.enterprise.gui.coregui.client.inventory.common.detail.summary.AbstractActivityView;
import org.rhq.enterprise.gui.coregui.client.util.GwtRelativeDurationConverter;
import org.rhq.enterprise.gui.coregui.client.util.MeasurementUtility;
import org.rhq.enterprise.gui.coregui.client.util.selenium.LocatableCanvas;
import org.rhq.enterprise.gui.coregui.client.util.selenium.LocatableDynamicForm;
import org.rhq.enterprise.gui.coregui.client.util.selenium.LocatableVLayout;

/**This portlet allows the end user to customize the OOB display
 *
 * @author Simeon Pinder
 */
public class GroupOobsPortlet extends LocatableVLayout implements CustomSettingsPortlet, AutoRefreshPortlet {

    private int groupId = -1;
    protected LocatableCanvas recentOobContent = new LocatableCanvas(extendLocatorId("RecentOobs"));
    private boolean currentlyLoading = false;
    private Configuration portletConfig = null;
    private DashboardPortlet storedPortlet;

    public GroupOobsPortlet(String locatorId) {
        super(locatorId);
        //figure out which page we're loading
        String currentPage = History.getToken();
        String[] elements = currentPage.split("/");
        int currentGroupIdentifier = Integer.valueOf(elements[1]);
        this.groupId = currentGroupIdentifier;
        initializeUi();
    }

    @Override
    protected void onInit() {
        super.onInit();
        loadData();
    }

    /**Defines layout for the portlet page.
     */
    protected void initializeUi() {
        setPadding(5);
        setMembersMargin(5);
        addMember(recentOobContent);
    }

    // A non-displayed, persisted identifier for the portlet
    public static final String KEY = "GroupOobs";
    // A default displayed, persisted name for the portlet
    public static final String NAME = "Group: OOB Metrics";
    public static final String ID = "id";

    // set on initial configuration, the window for this portlet view.
    private PortletWindow portletWindow;
    //instance ui widgets

    private Timer refreshTimer;

    private static List<String> CONFIG_INCLUDE = new ArrayList<String>();
    static {
        CONFIG_INCLUDE.add(Constant.RESULT_COUNT);
    }

    /** Responsible for initialization and lazy configuration of the portlet values
     */
    public void configure(PortletWindow portletWindow, DashboardPortlet storedPortlet) {
        //populate portlet configuration details
        if (null == this.portletWindow && null != portletWindow) {
            this.portletWindow = portletWindow;
        }

        if ((null == storedPortlet) || (null == storedPortlet.getConfiguration())) {
            return;
        }
        this.storedPortlet = storedPortlet;
        portletConfig = storedPortlet.getConfiguration();

        //lazy init any elements not yet configured.
        for (String key : PortletConfigurationEditorComponent.CONFIG_PROPERTY_INITIALIZATION.keySet()) {
            if ((portletConfig.getSimple(key) == null) && CONFIG_INCLUDE.contains(key)) {
                portletConfig.put(new PropertySimple(key,
                    PortletConfigurationEditorComponent.CONFIG_PROPERTY_INITIALIZATION.get(key)));
            }
        }
    }

    public Canvas getHelpCanvas() {
        return new HTMLFlow(MSG.view_portlet_help_oobs());
    }

    public static final class Factory implements PortletViewFactory {
        public static PortletViewFactory INSTANCE = new Factory();

        public final Portlet getInstance(String locatorId) {
            return new GroupOobsPortlet(locatorId);
        }
    }

    protected void loadData() {
        currentlyLoading = true;
        getRecentOobs();
    }

    @Override
    public DynamicForm getCustomSettingsForm() {
        LocatableDynamicForm customSettings = new LocatableDynamicForm(extendLocatorId("customSettings"));
        LocatableVLayout page = new LocatableVLayout(customSettings.extendLocatorId("page"));
        //build editor form container
        final LocatableDynamicForm form = new LocatableDynamicForm(page.extendLocatorId("alert-filter"));
        form.setMargin(5);
        //add result count selector
        final SelectItem resultCountSelector = PortletConfigurationEditorComponent.getResultCountEditor(portletConfig);
        form.setItems(resultCountSelector);

        //submit handler
        customSettings.addSubmitValuesHandler(new SubmitValuesHandler() {

            @Override
            public void onSubmitValues(SubmitValuesEvent event) {

                //results count
                portletConfig = AbstractActivityView.saveResultCounterSettings(resultCountSelector, portletConfig);

                //persist
                storedPortlet.setConfiguration(portletConfig);
                configure(portletWindow, storedPortlet);
                loadData();
            }

        });
        page.addMember(form);
        customSettings.addChild(page);
        return customSettings;
    }

    /** Fetches OOB measurements and updates the DynamicForm instance with the latest N
     *  oob change details.
     */
    private void getRecentOobs() {
        final int groupId = this.groupId;
        int resultCount = 5;//default to

        //result count
        PropertySimple property = portletConfig.getSimple(Constant.RESULT_COUNT);
        if (property != null) {
            String currentSetting = property.getStringValue();
            if (currentSetting.trim().isEmpty() || currentSetting.equalsIgnoreCase("5")) {
                resultCount = 5;
            } else {
                resultCount = Integer.valueOf(currentSetting);
            }
        }

        GWTServiceLookup.getMeasurementDataService().getHighestNOOBsForGroup(groupId, resultCount,
            new AsyncCallback<PageList<MeasurementOOBComposite>>() {
                @Override
                public void onFailure(Throwable caught) {
                    Log.debug("Error retrieving recent out of bound metrics for group [" + groupId + "]:"
                        + caught.getMessage());
                }

                @Override
                public void onSuccess(PageList<MeasurementOOBComposite> result) {
                    VLayout column = new VLayout();
                    column.setHeight(10);
                    if (!result.isEmpty()) {
                        for (MeasurementOOBComposite oob : result) {
                            LocatableDynamicForm row = new LocatableDynamicForm(recentOobContent.extendLocatorId(oob
                                .getScheduleName()));
                            row.setNumCols(2);

                            String title = oob.getScheduleName() + ":";
                            String destination = "/resource/common/monitor/Visibility.do?m=" + oob.getDefinitionId()
                                + "&id=" + groupId + "&mode=chartSingleMetricSingleResource";
                            LinkItem link = AbstractActivityView.newLinkItem(title, destination);
                            StaticTextItem time = AbstractActivityView.newTextItem(GwtRelativeDurationConverter
                                .format(oob.getTimestamp()));

                            row.setItems(link, time);
                            column.addMember(row);
                        }
                        //insert see more link spinder(2/24/11): no page that displays all oobs... See More not possible.
                    } else {
                        LocatableDynamicForm row = AbstractActivityView.createEmptyDisplayRow(recentOobContent
                            .extendLocatorId("None"), AbstractActivityView.RECENT_OOB_NONE);
                        column.addMember(row);
                    }
                    recentOobContent.setContents("");
                    for (Canvas child : recentOobContent.getChildren()) {
                        child.destroy();
                    }
                    recentOobContent.addChild(column);
                    recentOobContent.markForRedraw();
                }
            });
    }

    @Override
    public void startRefreshCycle() {
        //current setting
        final int refreshInterval = UserSessionManager.getUserPreferences().getPageRefreshInterval();

        //cancel any existing timer
        if (refreshTimer != null) {
            refreshTimer.cancel();
        }

        if (refreshInterval >= MeasurementUtility.MINUTES) {

            refreshTimer = new Timer() {
                public void run() {
                    if (!currentlyLoading) {
                        loadData();
                        redraw();
                    }
                }
            };

            refreshTimer.scheduleRepeating(refreshInterval);
        }
    }

    @Override
    protected void onDestroy() {
        if (refreshTimer != null) {

            refreshTimer.cancel();
        }
        super.onDestroy();
    }

    @Override
    public void redraw() {
        super.redraw();
        loadData();
    }

}