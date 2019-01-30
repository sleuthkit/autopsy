/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
