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

import org.hamcrest.CoreMatchers;
import static org.hamcrest.CoreMatchers.equalTo;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.sleuthkit.datamodel.DescriptionLoD;
import org.sleuthkit.datamodel.timeline.TimelineFilter;

public class DescriptionFilterStateTest extends FilterStateTestAbstract<DescriptionFilter, FilterState<DescriptionFilter>> {

    public DescriptionFilterStateTest() {
    }

    @Override
    FilterState<DescriptionFilter> getInstance() {
        return new DescriptionFilterState(new DescriptionFilter(DescriptionLoD.SHORT, "text"));
    }

    /**
     * Test of getActiveFilter method, of class DescriptionFilterStateTest.
     */
    @Test
    @Override
    public void testGetActiveFilter() {
        System.out.println("getActiveFilter");

        assertNull(instance.getActiveFilter());
        instance.setSelected(Boolean.TRUE);
        assertEquals(new DescriptionFilter(DescriptionLoD.SHORT, "text"), instance.getActiveFilter());
        instance.setDisabled(Boolean.TRUE);
        assertNull(instance.getActiveFilter());

    }

    /**
     * Test of equals method, of class DescriptionFilterStateTest.
     */
    @Test
    @Override
    public void testEquals() {
        System.out.println("equals");

        DescriptionFilterState instance2 = new DescriptionFilterState(new DescriptionFilter(DescriptionLoD.SHORT, "text"), false);
        DescriptionFilterState instance3 = new DescriptionFilterState(new DescriptionFilter(DescriptionLoD.SHORT, "text foo"), false);
        DescriptionFilterState instance4 = new DescriptionFilterState(new DescriptionFilter(DescriptionLoD.FULL, "text"), false);
        DescriptionFilterState instance5 = new DescriptionFilterState(new DescriptionFilter(DescriptionLoD.SHORT, "text"), true);
        DescriptionFilterState instance6 = new DescriptionFilterState(new DescriptionFilter(DescriptionLoD.SHORT, "text"), true);
        DefaultFilterState<?> instance7 = new DefaultFilterState<>(new TimelineFilter.TextFilter("test"));

        assertThat(instance, equalTo(instance));
        assertThat(instance, equalTo(instance2));
        assertFalse(instance.equals(instance3));
        assertFalse(instance.equals(instance4));
        assertFalse(instance.equals(instance5));
        assertFalse(instance.equals(instance6));
        assertFalse(instance.equals(instance7));
    }
}
