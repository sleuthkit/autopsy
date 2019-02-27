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
package org.sleuthkit.autopsy.timeline.zooming;

import java.util.Collections;
import static org.hamcrest.CoreMatchers.is;
import org.joda.time.Interval;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.RootFilterState;
import org.sleuthkit.datamodel.DescriptionLoD;
import org.sleuthkit.datamodel.timeline.EventType;
import org.sleuthkit.datamodel.timeline.EventTypeZoomLevel;
import org.sleuthkit.datamodel.timeline.TimelineFilter.DataSourcesFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.EventTypeFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.FileTypesFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.HashHitsFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.HideKnownFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.RootFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.TagsFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.TextFilter;

/**
 * Test Class for ZoomState.
 */
public class ZoomStateTest {

    private ZoomState instance;
    private RootFilter rootFilter;

    public ZoomStateTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
        rootFilter = new RootFilter(new HideKnownFilter(), new TagsFilter(), new HashHitsFilter(), new TextFilter(),
                new EventTypeFilter(EventType.ROOT_EVENT_TYPE),
                new DataSourcesFilter(), new FileTypesFilter(), Collections.emptyList());
        instance = new ZoomState(new Interval(100, 200), EventTypeZoomLevel.ROOT_TYPE, new RootFilterState(rootFilter), DescriptionLoD.SHORT);
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of withTimeAndType method, of class ZoomState.
     */
    @Test
    public void testWithTimeAndType() {
        System.out.println("withTimeAndType");

        ZoomState expResult = new ZoomState(new Interval(200, 300), EventTypeZoomLevel.BASE_TYPE, new RootFilterState(rootFilter), DescriptionLoD.SHORT);
        ZoomState result = instance.withTimeAndType(new Interval(200, 300), EventTypeZoomLevel.BASE_TYPE);
        assertEquals(expResult, result);

    }

    /**
     * Test of withTypeZoomLevel method, of class ZoomState.
     */
    @Test
    public void testWithTypeZoomLevel() {
        System.out.println("withTypeZoomLevel");

        ZoomState expResult = new ZoomState(new Interval(100, 200), EventTypeZoomLevel.SUB_TYPE, new RootFilterState(rootFilter), DescriptionLoD.SHORT);
        ZoomState result = instance.withTypeZoomLevel(EventTypeZoomLevel.SUB_TYPE);
        assertEquals(expResult, result);
    }

    /**
     * Test of withTimeRange method, of class ZoomState.
     */
    @Test
    public void testWithTimeRange() {
        System.out.println("withTimeRange");

        ZoomState expResult = new ZoomState(new Interval(1002, 2004), EventTypeZoomLevel.ROOT_TYPE, new RootFilterState(rootFilter), DescriptionLoD.SHORT);
        ZoomState result = instance.withTimeRange(new Interval(1002, 2004));
        assertEquals(expResult, result);
    }

    /**
     * Test of withDescrLOD method, of class ZoomState.
     */
    @Test
    public void testWithDescrLOD() {
        System.out.println("withDescrLOD");

        ZoomState expResult = new ZoomState(new Interval(100, 200), EventTypeZoomLevel.ROOT_TYPE, new RootFilterState(rootFilter), DescriptionLoD.FULL);
        ZoomState result = instance.withDescrLOD(DescriptionLoD.FULL);
        assertEquals(expResult, result);
    }

    /**
     * Test of withFilterState method, of class ZoomState.
     */
    @Test
    public void testWithFilterState() {
        System.out.println("withFilterState");
        RootFilterState filter = new RootFilterState(rootFilter);
        filter.getEventTypeFilterState().setSelected(true);

        ZoomState expResult = new ZoomState(new Interval(100, 200), EventTypeZoomLevel.ROOT_TYPE, filter, DescriptionLoD.SHORT);
        ZoomState result = instance.withFilterState(filter);
        assertEquals(expResult, result);
    }

    /**
     * Test of hasFilterState method, of class ZoomState.
     */
    @Test
    public void testHasFilterState() {
        System.out.println("hasFilterState");
        RootFilterState filterState = new RootFilterState(rootFilter);

        assertEquals(true, instance.hasFilterState(filterState));
        filterState.getFileTypesFilterState().setSelected(Boolean.TRUE);
        assertEquals(false, instance.hasFilterState(filterState));
    }

    /**
     * Test of hasTypeZoomLevel method, of class ZoomState.
     */
    @Test
    public void testHasTypeZoomLevel() {
        System.out.println("hasTypeZoomLevel");

        assertEquals(true, instance.hasTypeZoomLevel(EventTypeZoomLevel.ROOT_TYPE));
        assertEquals(false, instance.hasTypeZoomLevel(EventTypeZoomLevel.BASE_TYPE));
    }

    /**
     * Test of hasTimeRange method, of class ZoomState.
     */
    @Test
    public void testHasTimeRange() {
        System.out.println("hasTimeRange");
        assertEquals(true, instance.hasTimeRange(new Interval(100, 200)));
        assertEquals(false, instance.hasTimeRange(new Interval(1002, 2004)));
    }

    /**
     * Test of hasDescrLOD method, of class ZoomState.
     */
    @Test
    public void testHasDescrLOD() {
        System.out.println("hasDescrLOD");
        assertEquals(true, instance.hasDescrLOD(DescriptionLoD.SHORT));
        assertEquals(false, instance.hasDescrLOD(DescriptionLoD.MEDIUM));
    }

    /**
     * Test of equals method, of class ZoomState.
     */
    @Test
    public void testEquals() {
        System.out.println("equals");

        ZoomState other = null;
        assertThat(instance.equals(other), is(false));

        assertThat(instance.equals(instance), is(true));

        other = new ZoomState(new Interval(100, 200), EventTypeZoomLevel.ROOT_TYPE, new RootFilterState(rootFilter), DescriptionLoD.SHORT);

        assertThat(instance.equals(other), is(true));
        assertThat(other.equals(instance), is(true));

        other = instance.withDescrLOD(DescriptionLoD.FULL);
        assertThat(instance.equals(other), is(false));
        assertThat(other.equals(instance), is(false));

        other = instance.withTimeRange(new Interval(200, 300));
        assertThat(instance.equals(other), is(false));
        assertThat(other.equals(instance), is(false));

        other = instance.withTypeZoomLevel(EventTypeZoomLevel.SUB_TYPE);
        assertThat(instance.equals(other), is(false));
        assertThat(other.equals(instance), is(false));

        other = instance.withTimeAndType(new Interval(100, 200), EventTypeZoomLevel.BASE_TYPE);
        assertThat(instance.equals(other), is(false));
        assertThat(other.equals(instance), is(false));
        other = instance.withTimeAndType(new Interval(200, 300), EventTypeZoomLevel.ROOT_TYPE);
        assertThat(instance.equals(other), is(false));
        assertThat(other.equals(instance), is(false));

        RootFilterState otherFilterState = new RootFilterState(rootFilter);

        other = instance.withFilterState(otherFilterState);
        assertThat(instance.equals(other), is(true));
        assertThat(other.equals(instance), is(true));

        otherFilterState.getTagsFilterState().setSelected(true);
        other = instance.withFilterState(otherFilterState);
        assertThat(instance.equals(other), is(false));
        assertThat(other.equals(instance), is(false));
    }
}
