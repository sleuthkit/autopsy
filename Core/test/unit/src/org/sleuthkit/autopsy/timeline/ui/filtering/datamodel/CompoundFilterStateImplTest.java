/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.filtering.datamodel;

import java.util.Arrays;
import org.hamcrest.CoreMatchers;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;
import org.junit.Test;
import org.sleuthkit.datamodel.timeline.TimelineFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.CompoundFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.FileTypeFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.IntersectionFilter;
import sun.font.CoreMetrics;

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
