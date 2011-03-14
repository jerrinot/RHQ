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
import java.util.Map;

import com.allen_sauer.gwt.log.client.Log;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.smartgwt.client.widgets.Canvas;
import com.smartgwt.client.widgets.HTMLFlow;
import com.smartgwt.client.widgets.form.DynamicForm;
import com.smartgwt.client.widgets.form.events.SubmitValuesEvent;
import com.smartgwt.client.widgets.form.events.SubmitValuesHandler;
import com.smartgwt.client.widgets.form.fields.CheckboxItem;
import com.smartgwt.client.widgets.form.fields.FormItem;
import com.smartgwt.client.widgets.form.fields.StaticTextItem;
import com.smartgwt.client.widgets.layout.VLayout;

import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.domain.dashboard.DashboardPortlet;
import org.rhq.core.domain.event.EventSeverity;
import org.rhq.enterprise.gui.coregui.client.ImageManager;
import org.rhq.enterprise.gui.coregui.client.UserSessionManager;
import org.rhq.enterprise.gui.coregui.client.components.measurement.CustomConfigMeasurementRangeEditor;
import org.rhq.enterprise.gui.coregui.client.dashboard.AutoRefreshPortlet;
import org.rhq.enterprise.gui.coregui.client.dashboard.CustomSettingsPortlet;
import org.rhq.enterprise.gui.coregui.client.dashboard.Portlet;
import org.rhq.enterprise.gui.coregui.client.dashboard.PortletViewFactory;
import org.rhq.enterprise.gui.coregui.client.dashboard.PortletWindow;
import org.rhq.enterprise.gui.coregui.client.dashboard.portlets.PortletConfigurationEditorComponent;
import org.rhq.enterprise.gui.coregui.client.dashboard.portlets.PortletConfigurationEditorComponent.Constant;
import org.rhq.enterprise.gui.coregui.client.gwt.GWTServiceLookup;
import org.rhq.enterprise.gui.coregui.client.inventory.common.detail.summary.AbstractActivityView;
import org.rhq.enterprise.gui.coregui.client.resource.disambiguation.ReportDecorator;
import org.rhq.enterprise.gui.coregui.client.util.GwtTuple;
import org.rhq.enterprise.gui.coregui.client.util.MeasurementUtility;
import org.rhq.enterprise.gui.coregui.client.util.selenium.LocatableCanvas;
import org.rhq.enterprise.gui.coregui.client.util.selenium.LocatableDynamicForm;
import org.rhq.enterprise.gui.coregui.client.util.selenium.LocatableVLayout;

/**This portlet allows the end user to customize the Events display
 *
 * @author Simeon Pinder
 */
public class GroupEventsPortlet extends LocatableVLayout implements CustomSettingsPortlet, AutoRefreshPortlet {

    private int groupId = -1;
    protected LocatableCanvas recentEventsContent = new LocatableCanvas(extendLocatorId("RecentEvents"));
    private boolean currentlyLoading = false;
    private Configuration portletConfig = null;
    private DashboardPortlet storedPortlet;

    public GroupEventsPortlet(String locatorId) {
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
        addMember(recentEventsContent);
    }

    // A non-displayed, persisted identifier for the portlet
    public static final String KEY = "GroupEvents";
    // A default displayed, persisted name for the portlet
    public static final String NAME = "Group: Event Counts";
    public static final String ID = "id";

    // set on initial configuration, the window for this portlet view.
    private PortletWindow portletWindow;
    //instance ui widgets

    private Timer refreshTimer;

    private static List<String> CONFIG_INCLUDE = new ArrayList<String>();
    static {
        CONFIG_INCLUDE.add(Constant.RESULT_COUNT);
        CONFIG_INCLUDE.add(Constant.METRIC_RANGE);
        CONFIG_INCLUDE.add(Constant.METRIC_RANGE_BEGIN_END_FLAG);
        CONFIG_INCLUDE.add(Constant.METRIC_RANGE_ENABLE);
        CONFIG_INCLUDE.add(Constant.METRIC_RANGE_LASTN);
        CONFIG_INCLUDE.add(Constant.METRIC_RANGE_UNIT);
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
        return new HTMLFlow(MSG.view_portlet_help_eventcounts());
    }

    public static final class Factory implements PortletViewFactory {
        public static PortletViewFactory INSTANCE = new Factory();

        public final Portlet getInstance(String locatorId) {
            return new GroupEventsPortlet(locatorId);
        }
    }

    protected void loadData() {
        currentlyLoading = true;
        getRecentEventUpdates();
    }

    @Override
    public DynamicForm getCustomSettingsForm() {
        final LocatableDynamicForm customSettings = new LocatableDynamicForm(extendLocatorId("customSettings"));
        LocatableVLayout page = new LocatableVLayout(customSettings.extendLocatorId("page"));

        //add range selector
        final CustomConfigMeasurementRangeEditor measurementRangeEditor = PortletConfigurationEditorComponent
            .getMeasurementRangeEditor(portletConfig);

        //submit handler
        customSettings.addSubmitValuesHandler(new SubmitValuesHandler() {

            @Override
            public void onSubmitValues(SubmitValuesEvent event) {

                String selectedValue = null;
                //alert time range filter. Check for enabled and then persist property. Dealing with compound widget.
                FormItem item = measurementRangeEditor.getItem(CustomConfigMeasurementRangeEditor.ENABLE_RANGE_ITEM);
                CheckboxItem itemC = (CheckboxItem) item;
                selectedValue = String.valueOf(itemC.getValueAsBoolean());
                if (!selectedValue.trim().isEmpty()) {//then call
                    portletConfig.put(new PropertySimple(Constant.METRIC_RANGE_ENABLE, selectedValue));
                }

                //alert time advanced time filter enabled.
                selectedValue = String.valueOf(measurementRangeEditor.isAdvanced());
                if ((selectedValue != null) && (!selectedValue.trim().isEmpty())) {
                    portletConfig.put(new PropertySimple(Constant.METRIC_RANGE_BEGIN_END_FLAG, selectedValue));
                }

                //alert time frame
                List<Long> begEnd = measurementRangeEditor.getBeginEndTimes();
                if (begEnd.get(0) != 0) {//advanced settings
                    portletConfig.put(new PropertySimple(Constant.METRIC_RANGE, (begEnd.get(0) + "," + begEnd.get(1))));
                }

                //persist
                storedPortlet.setConfiguration(portletConfig);
                configure(portletWindow, storedPortlet);
                loadData();
                customSettings.markForRedraw();
            }

        });
        page.addMember(measurementRangeEditor);
        customSettings.addChild(page);
        return customSettings;
    }

    /** Fetches recent events and updates the DynamicForm instance with the latest
     *  event information over last 24hrs.
     */
    private void getRecentEventUpdates() {
        final int groupId = this.groupId;
        long end = System.currentTimeMillis();
        long start = end - (24 * 60 * 60 * 1000);

        //result timeframe if enabled
        PropertySimple property = portletConfig.getSimple(Constant.METRIC_RANGE_ENABLE);
        if (Boolean.valueOf(property.getBooleanValue())) {//then proceed setting
            property = portletConfig.getSimple(Constant.METRIC_RANGE);
            if (property != null) {
                String currentSetting = property.getStringValue();
                String[] range = currentSetting.split(",");
                start = Long.valueOf(range[0]);
                end = Long.valueOf(range[1]);
            }
        }

        GWTServiceLookup.getEventService().getEventCountsBySeverityForGroup(groupId, start, end,
            new AsyncCallback<Map<EventSeverity, Integer>>() {

                @Override
                public void onFailure(Throwable caught) {
                    Log
                        .debug("Error retrieving recent event counts for group [" + groupId + "]:"
                            + caught.getMessage());
                }

                @Override
                public void onSuccess(Map<EventSeverity, Integer> eventCounts) {
                    //Now populated Tuples
                    List<GwtTuple<EventSeverity, Integer>> results = new ArrayList<GwtTuple<EventSeverity, Integer>>();
                    for (EventSeverity severity : eventCounts.keySet()) {
                        int count = eventCounts.get(severity);
                        if (count > 0) {
                            results.add(new GwtTuple<EventSeverity, Integer>(severity, count));
                        }
                    }
                    //build display
                    VLayout column = new VLayout();
                    column.setHeight(10);

                    if (!results.isEmpty()) {
                        int rowNum = 0;
                        for (GwtTuple<EventSeverity, Integer> tuple : results) {
                            // event history records do not have a usable locatorId, we'll use rownum, which is unique and
                            // may be repeatable.
                            LocatableDynamicForm row = new LocatableDynamicForm(recentEventsContent
                                .extendLocatorId(String.valueOf(rowNum)));
                            row.setNumCols(2);
                            row.setWidth(10);//pack.

                            //icon
                            StaticTextItem iconItem = AbstractActivityView.newTextItemIcon(ImageManager
                                .getEventSeverityIcon(tuple.getLefty()), tuple.getLefty().name());
                            //count
                            StaticTextItem count = AbstractActivityView.newTextItem(String.valueOf(tuple.righty));
                            row.setItems(iconItem, count);

                            column.addMember(row);
                        }
                        //insert see more link
                        LocatableDynamicForm row = new LocatableDynamicForm(recentEventsContent.extendLocatorId(String
                            .valueOf(rowNum)));
                        AbstractActivityView.addSeeMoreLink(row, ReportDecorator.GWT_GROUP_URL + groupId
                            + "/Events/History/", column);
                    } else {
                        LocatableDynamicForm row = AbstractActivityView.createEmptyDisplayRow(recentEventsContent
                            .extendLocatorId("None"), AbstractActivityView.RECENT_CRITERIA_EVENTS_NONE);
                        column.addMember(row);
                    }
                    //cleanup
                    for (Canvas child : recentEventsContent.getChildren()) {
                        child.destroy();
                    }
                    recentEventsContent.addChild(column);
                    recentEventsContent.markForRedraw();
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