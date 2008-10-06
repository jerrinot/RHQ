 /*
  * RHQ Management Platform
  * Copyright (C) 2005-2008 Red Hat, Inc.
  * All rights reserved.
  *
  * This program is free software; you can redistribute it and/or modify
  * it under the terms of the GNU General Public License, version 2, as
  * published by the Free Software Foundation, and/or the GNU Lesser
  * General Public License, version 2.1, also as published by the Free
  * Software Foundation.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  * GNU General Public License and the GNU Lesser General Public License
  * for more details.
  *
  * You should have received a copy of the GNU General Public License
  * and the GNU Lesser General Public License along with this program;
  * if not, write to the Free Software Foundation, Inc.,
  * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
  */
package org.rhq.core.pc.inventory;

import org.testng.annotations.Test;

import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.measurement.AvailabilityType;
import org.rhq.core.pluginapi.inventory.ResourceComponent;
import org.rhq.core.pluginapi.inventory.ResourceContext;
import org.rhq.core.pc.util.FacetLockType;

/**
 * Unit test for {@link ResourceContainer}.
 *
 * @author Ian Springer
 */
@Test
public class ResourceContainerTest {
    public void testCreateResourceComponentProxy() throws Exception {
        Resource resource = new Resource();
        ResourceContainer resourceContainer = new ResourceContainer(resource);
        ResourceComponent resourceComponent = new MockResourceComponent(false);
        resourceContainer.setResourceComponent(resourceComponent);
        System.out.println("Testing proxy call that should timeout...");
        ResourceComponent resourceComponentProxy = resourceContainer.createResourceComponentProxy(ResourceComponent.class, FacetLockType.NONE, 50, true, false);
        try {
            resourceComponentProxy.getAvailability();
            assert (false);
        }
        catch (RuntimeException e) {
            assert (e instanceof TimeoutException);
        }
        System.out.println("SUCCESS!");
        System.out.println("Testing proxy call that should complete successfully...");
        resourceComponentProxy = resourceContainer.createResourceComponentProxy(ResourceComponent.class, FacetLockType.NONE, 150, true, false);
        AvailabilityType avail = resourceComponentProxy.getAvailability();
        assert (avail == AvailabilityType.UP);
        System.out.println("SUCCESS!");
        System.out.println("Testing proxy call that should fail...");
        ResourceComponent naughtyResourceComponent = new MockResourceComponent(true);
        resourceContainer.setResourceComponent(naughtyResourceComponent);        
        resourceComponentProxy = resourceContainer.createResourceComponentProxy(ResourceComponent.class, FacetLockType.NONE, Long.MAX_VALUE, true, false);
        try {
            resourceComponentProxy.getAvailability();
            assert (false);
        }
        catch (RuntimeException e) {
            assert (e instanceof MockRuntimeException);
        }
        System.out.println("SUCCESS!");
        System.out.println("Testing proxy call that should not timeout because it is not to a method declared in the proxied interface...");
        resourceComponentProxy = resourceContainer.createResourceComponentProxy(ResourceComponent.class, FacetLockType.NONE, 50, true, false);
        String string = resourceComponentProxy.toString();
        assert (string.equals(MockResourceComponent.class.toString()));
        System.out.println("SUCCESS!");
        // TODO: Test proxy locking.
    }

    class MockResourceComponent implements ResourceComponent
    {
        private boolean naughty;

        MockResourceComponent(boolean naughty) {
            this.naughty = naughty;
        }

        public void start(ResourceContext resourceContext) throws Exception {
        }

        public void stop() {
        }

        public AvailabilityType getAvailability() {
            if (this.naughty) {
                throw new MockRuntimeException();
            }
            try {
                Thread.sleep(100);
            }
            catch (InterruptedException e) {
                return AvailabilityType.DOWN;
            }
            return AvailabilityType.UP;
        }

        public String toString() {
            try {
                Thread.sleep(100);
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return this.getClass().toString();
        }
    }

    class MockRuntimeException extends RuntimeException {
    }
}
