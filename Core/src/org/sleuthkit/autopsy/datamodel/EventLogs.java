/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;


/**
 * event logs view nodes
 */
public class EventLogs implements AutopsyVisitableItem {

    private SleuthkitCase skCase;

    public enum EventLogFilter implements AutopsyVisitableItem {

        FS_EVENT_LOG_FILTER(0, "FS_EVENT_LOG_FILTER", "Windows"),
        ALL_EVENT_LOG_FILTER(1, "ALL_EVENT_LOG_FILTER", "Other");
        private int id;
        private String name;
        private String displayName;

        private EventLogFilter(int id, String name, String displayName) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;

        }

        public String getName() {
            return this.name;
        }

        public int getId() {
            return this.id;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        @Override
        public <T> T accept(AutopsyItemVisitor<T> v) {
            return v.visit(this);
        }
    }

    public EventLogs(SleuthkitCase skCase) {
        this.skCase = skCase;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }

    public SleuthkitCase getSleuthkitCase() {
        return this.skCase;
    }

    public static class EventLogsNode extends DisplayableItemNode {

        private static final String NAME = "Event Logs";
        private SleuthkitCase skCase;

        EventLogsNode(SleuthkitCase skCase) {
            super(Children.create(new EventLogsChildren(skCase), true), Lookups.singleton(NAME));
            super.setName(NAME);
            super.setDisplayName(NAME);
            this.skCase = skCase;
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/text-file.png");
        }

        @Override
        public TYPE getDisplayableItemNodeType() {
            return TYPE.META;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty("Name",
                    "Name",
                    "no description",
                    NAME));
            return s;
        }
    }

    public static class EventLogsChildren extends ChildFactory<EventLogs.EventLogFilter> {

        private SleuthkitCase skCase;

        public EventLogsChildren(SleuthkitCase skCase) {
            this.skCase = skCase;

        }

        @Override
        protected boolean createKeys(List<EventLogs.EventLogFilter> list) {
            list.addAll(Arrays.asList(EventLogs.EventLogFilter.values()));
            return true;
        }

        @Override
        protected Node createNodeForKey(EventLogs.EventLogFilter key) {
            return new EventLogNode(skCase, key);
        }

        public class EventLogNode extends DisplayableItemNode {

            private SleuthkitCase skCase;
            private EventLogs.EventLogFilter filter;
            private final Logger logger = Logger.getLogger(EventLogNode.class.getName());

            EventLogNode(SleuthkitCase skCase, EventLogs.EventLogFilter filter) {
                super(Children.create(new EventLogChildren(filter, skCase), true), Lookups.singleton(filter.getDisplayName()));
                super.setName(filter.getName());
                this.skCase = skCase;
                this.filter = filter;

                String tooltip = filter.getDisplayName();
                this.setShortDescription(tooltip);
                this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/text-file.png.png");

                //get count of children without preloading all children nodes
                final long count = new EventLogChildren(filter, skCase).calculateItems();
                //final long count = getChildren().getNodesCount(true);
                super.setDisplayName(filter.getDisplayName() + " (" + count + ")");
            }

            @Override
            public <T> T accept(DisplayableItemNodeVisitor<T> v) {
                return v.visit(this);
            }

            @Override
            protected Sheet createSheet() {
                Sheet s = super.createSheet();
                Sheet.Set ss = s.get(Sheet.PROPERTIES);
                if (ss == null) {
                    ss = Sheet.createPropertiesSet();
                    s.put(ss);
                }

                ss.put(new NodeProperty("Filter Type",
                        "Filter Type",
                        "no description",
                        filter.getDisplayName()));

                return s;
            }

            @Override
            public TYPE getDisplayableItemNodeType() {
                return TYPE.META;
            }

            @Override
            public boolean isLeafTypeNode() {
                return true;
            }
        }

        class EventLogChildren extends ChildFactory<AbstractFile> {

            private SleuthkitCase skCase;
            private EventLogs.EventLogFilter filter;
            private final Logger logger = Logger.getLogger(EventLogsChildren.class.getName());

            EventLogChildren(EventLogs.EventLogFilter filter, SleuthkitCase skCase) {
                this.skCase = skCase;
                this.filter = filter;
            }

            @Override
            protected boolean createKeys(List<AbstractFile> list) {
                list.addAll(runFsQuery());
                return true;
            }

            private String makeQuery() {
                String query = "";
                switch (filter) {
                    case FS_EVENT_LOG_FILTER:
                        query = "name like '%.evt'";
                              

                        break;
                    case ALL_EVENT_LOG_FILTER:
                        query = query = "name like '%.log'";
                     
                        break;

                    default:
                        logger.log(Level.SEVERE, "Unsupported filter type to get log content: " + filter);

                }

                return query;
            }

            private List<AbstractFile> runFsQuery() {
                List<AbstractFile> ret = new ArrayList<AbstractFile>();

                String query = makeQuery();
                try {
                    ret = skCase.findAllFilesWhere(query);
                } catch (TskCoreException e) {
                    logger.log(Level.SEVERE, "Error getting files for the event log content view using: " + query, e);
                }

                return ret;

            }

            /**
             * Get children count without actually loading all nodes
             *
             * @return
             */
            long calculateItems() {
                try {
                    return skCase.countFilesWhere(makeQuery());
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error getting event log files search view count", ex);
                    return 0;
                }
            }

            @Override
            protected Node createNodeForKey(AbstractFile key) {
                return key.accept(new ContentVisitor.Default<AbstractNode>() {
                    public FileNode visit(AbstractFile f) {
                        return new FileNode(f, false);
                    }

                    public FileNode visit(FsContent f) {
                        return new FileNode(f, false);
                    }

                    @Override
                    public FileNode visit(LayoutFile f) {
                        return new FileNode(f, false);
                    }

                    @Override
                    public FileNode visit(File f) {
                        return new FileNode(f, false);
                    }

                    @Override
                    public FileNode visit(Directory f) {
                        return new FileNode(f, false);
                    }

                    @Override
                    protected AbstractNode defaultVisit(Content di) {
                        throw new UnsupportedOperationException("Not supported for this type of Displayable Item: " + di.toString());
                    }
                });
            }
        }
    }
}