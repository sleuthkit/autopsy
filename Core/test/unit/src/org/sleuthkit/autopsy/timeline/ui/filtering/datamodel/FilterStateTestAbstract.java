/*
 * Autopsy Forensic Browser
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

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Abstract base class for test classes testing subclasses of FilterState
 *
 * @param <FS> The type of the filter state this class tests.
 */
public abstract class FilterStateTestAbstract<FS extends FilterState<?>> {

    protected FS instance; //must be initialized by subclasses

    public FilterStateTestAbstract() {
    }

    /**
     * Test of selectedProperty method, of class DefaultFilterState.
     */
    @Test
    public void testSelectedProperty() {
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
