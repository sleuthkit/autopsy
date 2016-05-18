/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-16 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.explorernodes;

import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Root Explorer node to represent events.
 */
public class EventRootNode extends DisplayableItemNode {

    /**
     * Since the lazy loading seems to be broken if there are more than this
     * many child events, we don't show them and just show a message showing the
     * number of events
     */
    public static final int MAX_EVENTS_TO_DISPLAY = 5000;

    /**
     * the number of child events
     */
    private final int childCount;

    public EventRootNode(String NAME, Collection<Long> fileIds, FilteredEventsModel filteredEvents) {
        super(Children.create(new EventNodeChildFactory(fileIds, filteredEvents), true), Lookups.singleton(fileIds));

        super.setName(NAME);
        super.setDisplayName(NAME);

        childCount = fileIds.size();
    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return null;
    }

    public int getChildCount() {
        return childCount;
    }

    /*
     * TODO (AUT-1849): Correct or remove peristent column reordering code
     *
     * Added to support this feature.
     */
//    @Override
//    public String getItemType() {
//        return "EventRoot";
//    }
    /**
     * ChildFactory for EventNodes.
     */
    private static class EventNodeChildFactory extends ChildFactory<Long> {

        private static final Logger LOGGER = Logger.getLogger(EventNodeChildFactory.class.getName());

        /**
         * list of event ids that act as keys for the child nodes.
         */
        private final Collection<Long> eventIDs;

        /**
         * filteredEvents is used to lookup the events from their ids
         */
        private final FilteredEventsModel filteredEvents;

        EventNodeChildFactory(Collection<Long> fileIds, FilteredEventsModel filteredEvents) {
            this.eventIDs = fileIds;
            this.filteredEvents = filteredEvents;
        }

        @Override
        protected boolean createKeys(List<Long> toPopulate) {
            /**
             * if there are too many events, just add one id (-1) to indicate
             * this.
             */
            if (eventIDs.size() < MAX_EVENTS_TO_DISPLAY) {
                toPopulate.addAll(eventIDs);
            } else {
                toPopulate.add(-1L);
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(Long eventID) {
            if (eventID < 0) {
                /*
                 * if the eventId is a the special value, return a node with a
                 * warning that their are too many evens
                 */
                return new TooManyNode(eventIDs.size());
            } else {
                try {
                    return EventNode.createEventNode(eventID, filteredEvents);
                } catch (IllegalStateException ex) {
                    //Since the case is closed, the user probably doesn't care about this, just log it as a precaution.
                    LOGGER.log(Level.SEVERE, "There was no case open to lookup the Sleuthkit object backing a SingleEvent.", ex); // NON-NLS
                    return null;
                } catch (TskCoreException ex) {
                    /*
                     * Just log it: There might be lots of these errors, and we
                     * don't want to flood the user with notifications. It will
                     * be obvious the UI is broken anyways
                     */
                    LOGGER.log(Level.SEVERE, "Failed to lookup Sleuthkit object backing a SingleEvent.", ex); // NON-NLS
                    return null;
                }
            }
        }
    }

    /**
     * A Node that just shows a warning message that their are too many events
     * to show
     */
    private static class TooManyNode extends AbstractNode {

        @NbBundle.Messages({
            "# {0} - maximum number of events to display",
            "# {1} - the number of events that is too many",
            "EventRoodNode.tooManyNode.displayName=Too many events to display.  Maximum = {0}. But there are {1} to display."})
        TooManyNode(int size) {
            super(Children.LEAF);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/info-icon-16.png"); // NON-NLS
            setDisplayName(Bundle.EventRoodNode_tooManyNode_displayName(MAX_EVENTS_TO_DISPLAY, size));
        }
    }
}
