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

import java.util.Arrays;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sleuthkit.datamodel.timeline.TimelineFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.CompoundFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.DataSourceFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.DataSourcesFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.FileTypeFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.IntersectionFilter;

/**
 * Test class for CompoundFilterState
 */
public class CompoundFilterStateTest extends FilterStateTestAbstract<  CompoundFilterState<TimelineFilter, CompoundFilter<TimelineFilter>>> {

    private static final FileTypeFilter PNG_FILTER = new FileTypeFilter("png", Arrays.asList("image/png"));
    private static final FileTypeFilter JPG_FILTER = new FileTypeFilter("jpg", Arrays.asList("image/jpg"));
    private static final FileTypeFilter BMP_FILTER = new FileTypeFilter("bmp", Arrays.asList("image/bmp"));
    private static final FileTypeFilter GIF_FILTER = new FileTypeFilter("gif", Arrays.asList("image/gif"));

    public CompoundFilterStateTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setup() {
        instance = new CompoundFilterState<>(
                new IntersectionFilter<>(Arrays.asList(PNG_FILTER, JPG_FILTER)));
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * Test listeners
     */
    @Test
    public void testStateListeners() {
        //assert initial conditions
        assertThat(instance.isSelected(), is(false));
        assertThat(instance.isDisabled(), is(false));
        assertThat(instance.isActive(), is(false));
        instance.getSubFilterStates().forEach(subFilterState -> assertThat(subFilterState.isDisabled(), is(true)));
        instance.getSubFilterStates().forEach(subFilterState -> assertThat(subFilterState.isSelected(), is(false)));

        /*
         * When compound filter is selected, subfilters should be enabled.
         * Subfilters should be selected if none where previously selected.
         */
        instance.setSelected(true);
        instance.getSubFilterStates().forEach(
                subFilterState -> assertThat(subFilterState.isDisabled(), is(false)));
        instance.getSubFilterStates().forEach(
                subFilterState -> assertThat(subFilterState.isSelected(), is(true)));

        //reset
        instance.setDisabled(false);
        instance.setSelected(false);

        /*
         * When compound filter is selected, subfilters should only be enabled
         * if none where previously selected.
         */
        instance.getSubFilterStates().get(0).setSelected(Boolean.TRUE);
        instance.getSubFilterStates().get(1).setSelected(Boolean.FALSE);
        instance.setSelected(true);

        assertThat(instance.getSubFilterStates().get(0).isSelected(), is(true));
        assertThat(instance.getSubFilterStates().get(1).isSelected(), is(false));

        /**
         * When all subfilters are unselected, compound filter is unselected
         */
        instance.getSubFilterStates().get(0).setSelected(Boolean.FALSE);
        assertThat(instance.isSelected(), is(false));

        /*
         * When compound filter is disabled, subfilters should be disabled. No
         * effect on subfilter selection.
         */
        instance.setDisabled(Boolean.TRUE);
        instance.getSubFilterStates().forEach(
                subFilterState -> assertThat(subFilterState.isDisabled(), is(true)));
        instance.getSubFilterStates().forEach(
                subFilterState -> assertThat(subFilterState.isSelected(), is(false)));

    }

    /**
     * Test of getSubFilterStates method, of class CompoundFilterStateImpl.
     */
    @Test
    public void testGetSubFilterStates() {
        assertEquals(instance.getSubFilterStates(), Arrays.asList(
                new SqlFilterState<>(PNG_FILTER, false, true),
                new SqlFilterState<>(JPG_FILTER, false, true)));
        instance.setSelected(Boolean.TRUE);
        assertEquals(instance.getSubFilterStates(), Arrays.asList(
                new SqlFilterState<>(PNG_FILTER, Boolean.TRUE, false),
                new SqlFilterState<>(JPG_FILTER, Boolean.TRUE, false)));
    }

    /**
     * Test of getActiveFilter method, of class CompoundFilterStateImpl.
     */
    @Test
    @Override
    public void testGetActiveFilter() {
        assertNull(instance.getActiveFilter());
        instance.setSelected(Boolean.TRUE);
        assertEquals(instance.getActiveFilter(), new IntersectionFilter<>(Arrays.asList(PNG_FILTER, JPG_FILTER)));
        instance.setDisabled(Boolean.TRUE);
        assertNull(instance.getActiveFilter());
    }

    @Override
    public void testEquals() {
        CompoundFilterState<?, ?> instance2 = new CompoundFilterState<>(new IntersectionFilter<>(Arrays.asList(PNG_FILTER, JPG_FILTER)));

        assertThat(instance, equalTo(instance));
        assertThat(instance, equalTo(instance2));
        instance2.setSelected(Boolean.TRUE);
        assertThat(instance, not(equalTo(instance2)));
        instance2.setSelected(false);
        instance2.setDisabled(Boolean.TRUE);
        assertThat(instance, not(equalTo(instance2)));
    }

    @Test
    public void testSubFilterAddedListeners() {
        DataSourcesFilter dataSourcesfilter = new TimelineFilter.DataSourcesFilter();
        dataSourcesfilter.addSubFilter(new DataSourceFilter("data source 1", 1));
        dataSourcesfilter.addSubFilter(new DataSourceFilter("data source 2", 2));
        CompoundFilterState<DataSourceFilter, DataSourcesFilter> dataSourcesFilterState = new CompoundFilterState<>(dataSourcesfilter);

        dataSourcesFilterState.setSelected(true);
        dataSourcesFilterState.getSubFilterStates().get(0).setSelected(true);
        dataSourcesFilterState.getSubFilterStates().get(1).setSelected(false);

        assertThat(dataSourcesFilterState.isDisabled(), is(false));
        assertThat(dataSourcesFilterState.isSelected(), is(true));
        assertThat(dataSourcesFilterState.getSubFilterStates().get(0).isDisabled(), is(false));
        assertThat(dataSourcesFilterState.getSubFilterStates().get(1).isDisabled(), is(false));

        dataSourcesfilter.addSubFilter(new DataSourceFilter("data source 3", 3));

        assertThat(dataSourcesFilterState.getSubFilterStates().size(), is(3));

        assertThat(dataSourcesFilterState.isDisabled(), is(false));
        assertThat(dataSourcesFilterState.isSelected(), is(true));
        assertThat(dataSourcesFilterState.getSubFilterStates().get(0).isDisabled(), is(false));
        assertThat(dataSourcesFilterState.getSubFilterStates().get(0).isSelected(), is(true));
        assertThat(dataSourcesFilterState.getSubFilterStates().get(1).isDisabled(), is(false));
        assertThat(dataSourcesFilterState.getSubFilterStates().get(1).isSelected(), is(false));
        assertThat(dataSourcesFilterState.getSubFilterStates().get(2).isDisabled(), is(false));
        assertThat(dataSourcesFilterState.getSubFilterStates().get(2).isSelected(), is(false));

        assertThat(dataSourcesFilterState, equalTo(dataSourcesFilterState.copyOf()));
    }

    /**
     * Test of addSubFilterState method, of class CompoundFilterState.
     */
    @Test
    public void testAddSubFilterState() {
        //subfilters should always match through whether they are access though the sub filterstates, or as the subfilters of instance's filter.
        assertThat(instance.getFilter().getSubFilters().size(), is(2));
        assertThat(instance.getFilter().getSubFilters().get(0), is(PNG_FILTER));
        assertThat(instance.getFilter().getSubFilters().get(1), is(JPG_FILTER));
        assertThat(instance.getSubFilterStates().size(), is(2));
        assertThat(instance.getSubFilterStates().get(0).getFilter(), is(PNG_FILTER));
        assertThat(instance.getSubFilterStates().get(1).getFilter(), is(JPG_FILTER));
        //check that the subfilters are in the correct default state
        instance.getSubFilterStates().forEach(filterState -> {
            assertThat(filterState.isSelected(), is(false));
            assertThat(filterState.isDisabled(), is(true));
        });

        instance.addSubFilterState(new SqlFilterState<>(BMP_FILTER));

        //subfilters should always match through whether they are access though the sub filterstates, or as the subfilters of instance's filter.
        assertThat(instance.getFilter().getSubFilters().size(), is(3));
        assertThat(instance.getFilter().getSubFilters().get(0), is(PNG_FILTER));
        assertThat(instance.getFilter().getSubFilters().get(1), is(JPG_FILTER));
        assertThat(instance.getFilter().getSubFilters().get(2), is(BMP_FILTER));
        assertThat(instance.getSubFilterStates().size(), is(3));
        assertThat(instance.getSubFilterStates().get(0).getFilter(), is(PNG_FILTER));
        assertThat(instance.getSubFilterStates().get(1).getFilter(), is(JPG_FILTER));
        assertThat(instance.getSubFilterStates().get(2).getFilter(), is(BMP_FILTER));
        //check that the subfilters are in the correct default state
        instance.getSubFilterStates().forEach(filterState -> {
            assertThat(filterState.isSelected(), is(false));
            assertThat(filterState.isDisabled(), is(true));
        });

        instance.setSelected(Boolean.TRUE);
        instance.addSubFilterState(new SqlFilterState<>(GIF_FILTER));

        //subfilters should always match through whether they are access though the sub filterstates, or as the subfilters of instance's filter.
        assertThat(instance.getFilter().getSubFilters().size(), is(4));
        assertThat(instance.getFilter().getSubFilters().get(0), is(PNG_FILTER));
        assertThat(instance.getFilter().getSubFilters().get(1), is(JPG_FILTER));
        assertThat(instance.getFilter().getSubFilters().get(2), is(BMP_FILTER));
        assertThat(instance.getFilter().getSubFilters().get(3), is(GIF_FILTER));
        assertThat(instance.getSubFilterStates().size(), is(4));
        assertThat(instance.getSubFilterStates().get(0).getFilter(), is(PNG_FILTER));
        assertThat(instance.getSubFilterStates().get(1).getFilter(), is(JPG_FILTER));
        assertThat(instance.getSubFilterStates().get(2).getFilter(), is(BMP_FILTER));
        assertThat(instance.getSubFilterStates().get(3).getFilter(), is(GIF_FILTER));

        //pre-existing filters should have been selected when instance was selected.
        assertThat(instance.getSubFilterStates().get(0).isSelected(), is(true));
        assertThat(instance.getSubFilterStates().get(1).isSelected(), is(true));
        assertThat(instance.getSubFilterStates().get(2).isSelected(), is(true));
        // filter added later is in default (unselected state)
        assertThat(instance.getSubFilterStates().get(3).isSelected(), is(false));

        //subfilters of active filter should not be disabled.
        instance.getSubFilterStates().forEach(filterState -> assertThat(filterState.isDisabled(), is(false)));
    }

    /**
     * Test of copyOf method, of class DefaultFilterState.
     */
    @Test
    @Override
    public void testCopyOf() {
        super.testCopyOf();

        instance.setSelected(Boolean.TRUE);
        instance.getSubFilterStates().get(0).setSelected(Boolean.TRUE);
        instance.getSubFilterStates().get(1).setSelected(Boolean.FALSE);

        CompoundFilterState<TimelineFilter, CompoundFilter<TimelineFilter>> copyOf = instance.copyOf();

        assertEquals(instance, copyOf);

        assertThat(copyOf.isSelected(), is(true));
        assertThat(copyOf.isDisabled(), is(false));
        assertThat(copyOf.getSubFilterStates().get(0).isSelected(), is(true));
        assertThat(copyOf.getSubFilterStates().get(0).isDisabled(), is(false));
        assertThat(copyOf.getSubFilterStates().get(1).isSelected(), is(false));
        assertThat(copyOf.getSubFilterStates().get(1).isDisabled(), is(false));
    }
}
