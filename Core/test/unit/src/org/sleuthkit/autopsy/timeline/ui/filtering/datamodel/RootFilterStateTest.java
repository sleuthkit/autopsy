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

import java.util.Collections;
import java.util.function.Function;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.sleuthkit.datamodel.PublicTagName;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.timeline.EventType;
import org.sleuthkit.datamodel.timeline.TimelineFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.DataSourcesFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.EventTypeFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.FileTypesFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.HashHitsFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.HashSetFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.HideKnownFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.RootFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.TagNameFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.TagsFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.TextFilter;

/**
 * Test class for RootFilterState
 */
public class RootFilterStateTest extends FilterStateTestAbstract< RootFilterState> {

    private TimelineFilter.FileTypesFilter fileTypesFilter;
    private TimelineFilter.HideKnownFilter hideKnownFilter;
    private TimelineFilter.DataSourcesFilter dataSourcesFilter;
    private TimelineFilter.EventTypeFilter eventTypeFilter;
    private TimelineFilter.TextFilter textFilter;
    private TimelineFilter.HashHitsFilter hashHitsFilter;
    private TimelineFilter.TagsFilter tagsFilter;
    private TimelineFilter.RootFilter rootFilterInstance;
    private TimelineFilter.TagNameFilter bookmarkTagNameFilter;
    private TimelineFilter.TagNameFilter followupTagNameFilter;

    private final PublicTagName followupTagName = new PublicTagName(1, "follow up", "test tag name description", TagName.HTML_COLOR.NONE, TskData.FileKnown.KNOWN);
    private final PublicTagName bookmarkTagName = new PublicTagName(0, "bookmark", "test tag name description", TagName.HTML_COLOR.NONE, TskData.FileKnown.KNOWN);

    public RootFilterStateTest() {
    }

    @Before
    public void setUp() {
        bookmarkTagNameFilter = new TagNameFilter(bookmarkTagName);
        followupTagNameFilter = new TagNameFilter(followupTagName);

        fileTypesFilter = new FileTypesFilter();
        hideKnownFilter = new HideKnownFilter();
        dataSourcesFilter = new DataSourcesFilter();
        eventTypeFilter = new EventTypeFilter(EventType.ROOT_EVENT_TYPE);
        textFilter = new TextFilter();
        hashHitsFilter = new HashHitsFilter();
        hashHitsFilter.addSubFilter(new HashSetFilter("hashset 1"));
        hashHitsFilter.addSubFilter(new HashSetFilter("hashset 2"));
        tagsFilter = new TagsFilter();
        tagsFilter.addSubFilter(bookmarkTagNameFilter);
        tagsFilter.addSubFilter(followupTagNameFilter);
        rootFilterInstance = new RootFilter(
                hideKnownFilter,
                tagsFilter,
                hashHitsFilter,
                textFilter,
                eventTypeFilter,
                dataSourcesFilter,
                fileTypesFilter, Collections.emptyList());
        instance = new RootFilterState(rootFilterInstance);
    }

    /**
     * Test of equals method, of class RootFilterState.
     */
    @Test
    @Override
    public void testEquals() {
        System.out.println("equals");
        RootFilterState other = new RootFilterState(rootFilterInstance.copyOf());
        assertFalse(instance.equals(null));
        assertTrue(instance.equals(instance));
        assertTrue(instance.equals(other));
        assertTrue(other.equals(instance));
        assertFalse(instance.equals(new RootFilterState(new RootFilter(
                new HideKnownFilter(),
                new TagsFilter(),
                new HashHitsFilter(),
                new TextFilter(),
                new EventTypeFilter(EventType.CUSTOM_TYPES),
                new DataSourcesFilter(),
                new FileTypesFilter(),
                Collections.emptyList()))));
    }

    /**
     * Test of selectedProperty method, of class RootFilterState.
     */
    @Test
    @Override
    public void testSelectedProperty() {
        System.out.println("selectedProperty");

        assertTrue(instance.selectedProperty().getValue());
        assertTrue(instance.isSelected());

        try {
            instance.setSelected(Boolean.FALSE);
        } catch (UnsupportedOperationException ex) {
            assertTrue(instance.selectedProperty().getValue());
            assertTrue(instance.isSelected());
        }
    }

    /**
     * Test of disabledProperty method, of class RootFilterState.
     */
    @Test
    public void testDisabledProperty() {
        System.out.println("disabledProperty");

        assertFalse(instance.disabledProperty().getValue());
        assertFalse(instance.isDisabled());

        try {
            instance.setDisabled(Boolean.TRUE);
        } catch (UnsupportedOperationException ex) {
            assertFalse(instance.disabledProperty().getValue());
            assertFalse(instance.isDisabled());
        }
    }

    /**
     * Test of activeProperty method, of class RootFilterState.
     */
    @Test
    @Override
    public void testActiveProperty() {
        System.out.println("activeProperty");

        assertTrue(instance.isActive());
        assertTrue(instance.activeProperty().getValue());

        try {
            instance.setSelected(Boolean.FALSE);
        } catch (UnsupportedOperationException ex) {
            assertTrue(instance.isActive());
            assertTrue(instance.activeProperty().getValue());
        }
        try {
            instance.setDisabled(Boolean.TRUE);
        } catch (UnsupportedOperationException ex) {
            assertTrue(instance.isActive());
            assertTrue(instance.activeProperty().getValue());
        }
    }

    /**
     * Test of intersect method, of class RootFilterState.
     */
    @Test
    public void testIntersect() {
        System.out.println("intersect");
        assertThat(instance.getSubFilterStates().size(), is(7));

        RootFilterState intersection = instance.intersect(new DefaultFilterState<>(new TextFilter("intersection test")));

        assertFalse(intersection.equals(instance));
        assertThat(intersection.getSubFilterStates().size(), is(8));
        assertTrue(intersection.getFilter().getSubFilters().contains(new TextFilter("intersection test")));
    }

    /**
     * Test of addSubFilterState method, of class RootFilterState.
     */
    @Test
    public void testAddSubFilterState() {
        System.out.println("addSubFilterState");

        assertThat(instance.getSubFilterStates().size(), is(7));
        assertThat(instance.getFilter().getSubFilters().size(), is(7));

        instance.addSubFilterState(new DefaultFilterState<>(new TextFilter("intersection test")));

        assertThat(instance.getSubFilterStates().size(), is(8));
        assertThat(instance.getFilter().getSubFilters().size(), is(8));

        assertTrue(instance.getSubFilterStates().stream().map(FilterState::getFilter).anyMatch(new TextFilter("intersection test")::equals));
        assertTrue(instance.getFilter().getSubFilters().contains(new TextFilter("intersection test")));
    }

    /**
     * Test of getEventTypeFilterState method, of class RootFilterState.
     */
    @Test
    public void testGetEventTypeFilterState() {
        System.out.println("getEventTypeFilterState");

        CompoundFilterState<EventTypeFilter, EventTypeFilter> expResult = new CompoundFilterState<>(new EventTypeFilter(EventType.ROOT_EVENT_TYPE));
        assertEquals(expResult, instance.getEventTypeFilterState());
    }

    /**
     * Test of getKnownFilterState method, of class RootFilterState.
     */
    @Test
    public void testGetKnownFilterState() {
        System.out.println("getKnownFilterState");
        DefaultFilterState<?> expResult = new DefaultFilterState<>(new HideKnownFilter());
        assertEquals(expResult, instance.getKnownFilterState());
    }

    /**
     * Test of getTextFilterState method, of class RootFilterState.
     */
    @Test
    public void testGetTextFilterState() {
        System.out.println("getTextFilterState");
        DefaultFilterState<?> expResult = new DefaultFilterState<>(new TextFilter());
        assertEquals(expResult, instance.getTextFilterState());
    }

    /**
     * Test of getTagsFilterState method, of class RootFilterState.
     */
    @Test
    public void testGetTagsFilterState() {
        System.out.println("getTagsFilterState");
        TimelineFilter.TagsFilter tagsFilter2 = new TimelineFilter.TagsFilter();
        tagsFilter2.addSubFilter(new TimelineFilter.TagNameFilter(bookmarkTagName));
        tagsFilter2.addSubFilter(new TimelineFilter.TagNameFilter(followupTagName));

        TagsFilterState result = instance.getTagsFilterState();
        assertEquals(new TagsFilterState(tagsFilter2), result);
    }

    /**
     * Test of getHashHitsFilterState method, of class RootFilterState.
     */
    @Test
    public void testGetHashHitsFilterState() {
        System.out.println("getHashHitsFilterState");
        HashHitsFilter hashHitsFilter1 = new HashHitsFilter();
        hashHitsFilter1.addSubFilter(new HashSetFilter("hashset 1"));
        hashHitsFilter1.addSubFilter(new HashSetFilter("hashset 2"));
        DefaultFilterState<?> expResult = new CompoundFilterState<>(hashHitsFilter1);
        assertEquals(expResult, instance.getHashHitsFilterState());
    }

    /**
     * Test of getDataSourcesFilterState method, of class RootFilterState.
     */
    @Test
    public void testGetDataSourcesFilterState() {
        System.out.println("getDataSourcesFilterState");
        DefaultFilterState<?> expResult = new CompoundFilterState<>(new DataSourcesFilter());
        assertEquals(expResult, instance.getDataSourcesFilterState());
    }

    /**
     * Test of getFileTypesFilterState method, of class RootFilterState.
     */
    @Test
    public void testGetFileTypesFilterState() {
        System.out.println("getFileTypesFilterState");
        DefaultFilterState<?> expResult = new CompoundFilterState<>(new FileTypesFilter());
        assertEquals(expResult, instance.getFileTypesFilterState());
    }

    /**
     * Test of getActiveFilter method, of class RootFilterState.
     */
    @Test
    @Override
    public void testGetActiveFilter() {
        System.out.println("getActiveFilter");
        RootFilter expected = new RootFilter(null, null, null, null, null, null, null, Collections.emptyList());
        assertEquals(expected, instance.getActiveFilter());

        instance.getEventTypeFilterState().setSelected(Boolean.TRUE);
        expected = new RootFilter(null, null, null, null, new EventTypeFilter(EventType.ROOT_EVENT_TYPE), null, null, Collections.emptyList());
        assertEquals(expected, instance.getActiveFilter());
    }

    /**
     * Test of hasActiveHashFilters method, of class RootFilterState.
     */
    @Test
    public void testHasActiveHashFilters() {
        System.out.println("hasActiveHashFilters");

        assertEquals(false, instance.hasActiveHashFilters());
        instance.getHashHitsFilterState().setSelected(Boolean.TRUE);

        assertEquals(true, instance.hasActiveHashFilters());
        instance.getHashHitsFilterState().setSelected(Boolean.FALSE);
        assertEquals(false, instance.hasActiveHashFilters());

    }

    /**
     * Test of hasActiveTagsFilters method, of class RootFilterState.
     */
    @Test
    public void testHasActiveTagsFilters() {
        System.out.println("hasActiveTagsFilters");

        assertEquals(false, instance.hasActiveTagsFilters());
        instance.getTagsFilterState().setSelected(Boolean.TRUE);

        assertEquals(true, instance.hasActiveTagsFilters());
        instance.getTagsFilterState().setSelected(Boolean.FALSE);
        assertEquals(false, instance.hasActiveTagsFilters());
    }
}
