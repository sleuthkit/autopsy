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

import java.util.Arrays;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;
import org.junit.Test;
import org.sleuthkit.datamodel.timeline.TimelineFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.CompoundFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.FileTypeFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.IntersectionFilter;

public class CompoundFilterStateImplTest extends FilterStateTestAbstract<CompoundFilter<TimelineFilter>, CompoundFilterState<TimelineFilter, CompoundFilter<TimelineFilter>>> {

    private static final FileTypeFilter PNG_FILTER = new FileTypeFilter("png", Arrays.asList("image/png"));

    public CompoundFilterStateImplTest() {
    }

    @Override
    CompoundFilterState<TimelineFilter, CompoundFilter<TimelineFilter>> getInstance() {
        return new CompoundFilterStateImpl<>(
                new IntersectionFilter<>(Arrays.asList(PNG_FILTER)));
    }

    /**
     * Test of getSubFilterStates method, of class CompoundFilterStateImpl.
     */
    @Test
    public void testGetSubFilterStates() {
        System.out.println("getSubFilterStates");
        assertEquals(instance.getSubFilterStates(), Arrays.asList(new DefaultFilterState<>(PNG_FILTER, false, true)));
        instance.setSelected(Boolean.TRUE);
        assertEquals(instance.getSubFilterStates(), Arrays.asList(new DefaultFilterState<>(PNG_FILTER, Boolean.TRUE, false)));
    }

    /**
     * Test of getActiveFilter method, of class CompoundFilterStateImpl.
     */
    @Test
    @Override
    public void testGetActiveFilter() {
        System.out.println("getActiveFilter");

        assertNull(instance.getActiveFilter());
        instance.setSelected(Boolean.TRUE);
        assertEquals(instance.getActiveFilter(), new IntersectionFilter<>(Arrays.asList(PNG_FILTER)));
        instance.setDisabled(Boolean.TRUE);
        assertNull(instance.getActiveFilter());
    }

    @Override
    public void testEquals() {
        CompoundFilterStateImpl<?, ?> instance2 = new CompoundFilterStateImpl<>(new IntersectionFilter<>(Arrays.asList(PNG_FILTER)));
        assertThat(instance, equalTo(instance2));
        instance2.setSelected(Boolean.TRUE);
        assertThat(instance, not(equalTo(instance2)));
    }
}
