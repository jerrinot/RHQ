package org.rhq.enterprise.gui.coregui.client.dashboard.portlets.recent.operations;

/*
 * RHQ Management Platform
 * Copyright (C) 2010 Red Hat, Inc.
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

import org.rhq.core.domain.common.EntityContext;
import org.rhq.enterprise.gui.coregui.client.dashboard.Portlet;
import org.rhq.enterprise.gui.coregui.client.dashboard.PortletViewFactory;

/**
 * @author Jay Shaughnessy
 */
public class OperationHistoryPortlet extends AbstractOperationHistoryPortlet {

    // A non-displayed, persisted identifier for the portlet
    public static final String KEY = "Operations";
    // A default displayed, persisted name for the portlet    
    public static final String NAME = MSG.view_portlet_defaultName_operations();

    public OperationHistoryPortlet(String locatorId) {
        super(locatorId, EntityContext.forSubsystemView());
    }

    public static final class Factory implements PortletViewFactory {
        public static PortletViewFactory INSTANCE = new Factory();

        public final Portlet getInstance(String locatorId, EntityContext context) {

            return new OperationHistoryPortlet(locatorId);
        }
    }
}
