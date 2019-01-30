/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
