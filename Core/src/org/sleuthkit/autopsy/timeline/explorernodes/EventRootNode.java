/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.BaseTypes;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public class EventRootNode extends DisplayableItemNode {

    public static final int MAX_EVENTS_TO_DISPLAY = 5000;

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
     * The node factories used to make lists of files to send to the result
     * viewer using the lazy loading (rather than background) loading option to
     * facilitate
     */
    private static class EventNodeChildFactory extends ChildFactory<Long> {

        private static final Logger LOGGER = Logger.getLogger(EventNodeChildFactory.class.getName());

        private final Collection<Long> eventIDs;

        private final FilteredEventsModel filteredEvents;

        EventNodeChildFactory(Collection<Long> fileIds, FilteredEventsModel filteredEvents) {
            this.eventIDs = fileIds;
            this.filteredEvents = filteredEvents;
        }

        @Override
        protected boolean createKeys(List<Long> toPopulate) {
            if (eventIDs.size() < MAX_EVENTS_TO_DISPLAY) {
                toPopulate.addAll(eventIDs);
            } else {
                toPopulate.add(-1l);
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(Long eventID) {
            if (eventID >= 0) {
                final TimeLineEvent eventById = filteredEvents.getEventById(eventID);
                try {
                    AbstractFile file = Case.getCurrentCase().getSleuthkitCase().getAbstractFileById(eventById.getFileID());
                    if (file != null) {
                        if (eventById.getType().getSuperType() == BaseTypes.FILE_SYSTEM) {
                            return new EventNode(eventById, file);
                        } else {
                            BlackboardArtifact blackboardArtifact = Case.getCurrentCase().getSleuthkitCase().getBlackboardArtifact(eventById.getArtifactID());

                            return new EventNode(eventById, file, blackboardArtifact);
                        }
                    } else {
                        LOGGER.log(Level.WARNING, "Failed to lookup sleuthkit object backing TimeLineEvent."); // NON-NLS
                        return null;
                    }

                } catch (TskCoreException tskCoreException) {
                    LOGGER.log(Level.WARNING, "Failed to lookup sleuthkit object backing TimeLineEvent.", tskCoreException); // NON-NLS
                    return null;
                }
            } else {
                return new TooManyNode(eventIDs.size());
            }
        }
    }

    private static class TooManyNode extends AbstractNode {

        public TooManyNode(int size) {
            super(Children.LEAF);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/info-icon-16.png"); // NON-NLS
            setDisplayName(
                    NbBundle.getMessage(this.getClass(),
                            "EventRoodNode.tooManyNode.displayName",
                            MAX_EVENTS_TO_DISPLAY,
                            size));
        }
    }
}
