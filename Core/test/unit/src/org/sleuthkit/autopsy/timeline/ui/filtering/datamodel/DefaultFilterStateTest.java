/*
 * Central Repository
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.timeline.ui.filtering.datamodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.sleuthkit.datamodel.timeline.TimelineFilter;

/**
 *
 */
public class DefaultFilterStateTest extends FilterStateTestAbstract<TimelineFilter.HideKnownFilter, DefaultFilterState<TimelineFilter.HideKnownFilter>> {

    public DefaultFilterStateTest() {
    }

    @Override
    DefaultFilterState<TimelineFilter.HideKnownFilter> getInstance() {
        return new DefaultFilterState<>(new TimelineFilter.HideKnownFilter());
    }
     /**
     * Test of getActiveFilter method, of class DefaultFilterState.
     */
    @Test
    @Override
    public   void testGetActiveFilter() {
        System.out.println("getActiveFilter");

        assertNull(instance.getActiveFilter());
        instance.setSelected(Boolean.TRUE);
        assertEquals(new TimelineFilter.HideKnownFilter(), instance.getActiveFilter());
        instance.setDisabled(Boolean.TRUE);
        assertNull(instance.getActiveFilter());

    } /**
     * Test of equals method, of class DefaultFilterState.
     */
    @Test
    @Override
    public void testEquals() {
        System.out.println("equals");

        DefaultFilterState<?> instance2 = new DefaultFilterState<>(new TimelineFilter.HideKnownFilter(), true);
        DefaultFilterState<?> instance3 = new DefaultFilterState<>(new TimelineFilter.HideKnownFilter(), false);
        DefaultFilterState<?> instance4 = new DefaultFilterState<>(new TimelineFilter.TextFilter("test"));
        assertTrue(instance.equals(instance));
        assertTrue(instance.equals(instance3));
        assertFalse(instance.equals(instance2));
        assertFalse(instance.equals(instance4));
    }
}
