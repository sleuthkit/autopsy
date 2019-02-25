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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.sleuthkit.datamodel.PublicTagName;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.timeline.TimelineFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.TagsFilter;

/**
 * Test class for TagsFilterState
 */
public class TagsFilterStateTest extends FilterStateTestAbstract<  TagsFilterState> {

    private static final PublicTagName PUBLIC_TAG_NAME = new PublicTagName(1, "test", "test tag name", TagName.HTML_COLOR.NONE, TskData.FileKnown.KNOWN);
    private static final PublicTagName PUBLIC_TAG_NAME2 = new PublicTagName(2, "test2", "test2 tag name", TagName.HTML_COLOR.NONE, TskData.FileKnown.UNKNOWN);
    private static final PublicTagName PUBLIC_TAG_NAME3 = new PublicTagName(3, "test3", "test3 tag name", TagName.HTML_COLOR.NONE, TskData.FileKnown.KNOWN);
    private static final PublicTagName PUBLIC_TAG_NAME4 = new PublicTagName(4, "test4", "test4 tag name", TagName.HTML_COLOR.NONE, TskData.FileKnown.KNOWN);

    public TagsFilterStateTest() {
    }

    @Before
    public void setUp() {

        TimelineFilter.TagsFilter tagsFilter = new TimelineFilter.TagsFilter();
        tagsFilter.addSubFilter(new TimelineFilter.TagNameFilter(PUBLIC_TAG_NAME));
        tagsFilter.addSubFilter(new TimelineFilter.TagNameFilter(PUBLIC_TAG_NAME2));
        instance = new TagsFilterState(tagsFilter);
    }

    /**
     * Test listeners
     */
    @Test
    public void testListeners() {
        System.out.println("listeners");
        //filters added through initial filterstate constructor are not selected
        assertThat(instance.getSubFilterStates().get(0).isSelected(), equalTo(false));
        assertThat(instance.getSubFilterStates().get(1).isSelected(), equalTo(false));

        //filter starts unselected
        DefaultFilterState<TimelineFilter.TagNameFilter> tagNameState
                = new DefaultFilterState<>(new TimelineFilter.TagNameFilter(PUBLIC_TAG_NAME3));
        assertThat(tagNameState.isSelected(), equalTo(false));
        instance.getSubFilterStates().add(tagNameState);
                //filter is selected after adding to TagsFilterState.
        assertThat(tagNameState.isSelected(), equalTo(true));


        //filter starts unselected
        DefaultFilterState<TimelineFilter.TagNameFilter> tagNameState2
                = new DefaultFilterState<>(new TimelineFilter.TagNameFilter(PUBLIC_TAG_NAME4),false);
        assertThat(tagNameState2.isSelected(), equalTo(false));
        instance.addSubFilterState(tagNameState2);
                //filter is selected after adding to TagsFilterState.
        assertThat(tagNameState2.isSelected(), equalTo(true));
    }

    @Test
    @Override
    public void testEquals() {
        assertThat(instance, equalTo(instance));

        TagsFilter tagsFilter = new TimelineFilter.TagsFilter();
        tagsFilter.addSubFilter(new TimelineFilter.TagNameFilter(PUBLIC_TAG_NAME));
        tagsFilter.addSubFilter(new TimelineFilter.TagNameFilter(PUBLIC_TAG_NAME2));
        TagsFilterState instance2 = new TagsFilterState(tagsFilter);
        assertThat(instance, equalTo(instance2));
        instance2.setSelected(Boolean.TRUE);
        assertThat(instance, not(equalTo(instance2)));
        instance2.setSelected(false);
        instance2.setDisabled(Boolean.TRUE);
        assertThat(instance, not(equalTo(instance2)));
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

        TimelineFilter.TagsFilter tagsFilter = new TimelineFilter.TagsFilter();
        tagsFilter.addSubFilter(new TimelineFilter.TagNameFilter(PUBLIC_TAG_NAME));
        tagsFilter.addSubFilter(new TimelineFilter.TagNameFilter(PUBLIC_TAG_NAME2));

        assertEquals(instance.getActiveFilter(), tagsFilter);
        instance.setDisabled(Boolean.TRUE);
        assertNull(instance.getActiveFilter());
    }
}
