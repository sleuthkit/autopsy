/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.filtering.datamodel;

import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public abstract class FilterStateTestAbstract<FT, FS extends FilterState<FT>> {

    protected FS instance;

    abstract FS getInstance();

    public FilterStateTestAbstract() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
        instance = getInstance();

    }

    @After
    public void tearDown() {

    }

    /**
     * Test of selectedProperty method, of class DefaultFilterState.
     */
    @Test
    public void testSelectedProperty() {
        System.out.println("selectedProperty");

        assertFalse(instance.isSelected());

        instance.setSelected(Boolean.TRUE);
        assertTrue(instance.selectedProperty().getValue());
        assertTrue(instance.isSelected());

        instance.setSelected(Boolean.FALSE);
        assertFalse(instance.selectedProperty().getValue());
        assertFalse(instance.isSelected());
    }

    /**
     * Test of activeProperty method, of class DefaultFilterState.
     */
    @Test
    public void testActiveProperty() {
        System.out.println("activeProperty");

        assertFalse(instance.isActive());
        assertFalse(instance.activeProperty().getValue());

        instance.setSelected(Boolean.TRUE);
        assertTrue(instance.isActive());
        assertTrue(instance.activeProperty().getValue());

        instance.setDisabled(Boolean.TRUE);

        assertFalse(instance.isActive());
        assertFalse(instance.activeProperty().getValue());
    }

    /**
     * Test of copyOf method, of class DefaultFilterState.
     */
    @Test
    public void testCopyOf() {
        System.out.println("copyOf");

        assertEquals(instance, instance.copyOf());
    }

    /**
     * Test of getActiveFilter method, of class DefaultFilterState.
     */
    @Test
    public abstract void testGetActiveFilter();

    /**
     * Test of equals method, of class DefaultFilterState.
     */
    @Test
    public abstract void testEquals();
}
