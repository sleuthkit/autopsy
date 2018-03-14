/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013-2018 Basis Technology Corp.
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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.logging.Level;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.VirtualDirectory;

/**
 * Files by Size View node and related child nodes
 */
public class FileSize implements AutopsyVisitableItem {

    private SleuthkitCase skCase;

    public enum FileSizeFilter implements AutopsyVisitableItem {

        SIZE_50_200(0, "SIZE_50_200", "50 - 200MB"), //NON-NLS
        SIZE_200_1000(1, "SIZE_200_1GB", "200MB - 1GB"), //NON-NLS
        SIZE_1000_(2, "SIZE_1000+", "1GB+"); //NON-NLS
        private int id;
        private String name;
        private String displayName;

        private FileSizeFilter(int id, String name, String displayName) {
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

    public FileSize(SleuthkitCase skCase) {
        this.skCase = skCase;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }

    public SleuthkitCase getSleuthkitCase() {
        return this.skCase;
    }

    /*
     * Root node. Children are nodes for specific sizes.
     */
    public static class FileSizeRootNode extends DisplayableItemNode {

        private static final String NAME = NbBundle.getMessage(FileSize.class, "FileSize.fileSizeRootNode.name");

        FileSizeRootNode(SleuthkitCase skCase) {
            super(Children.create(new FileSizeRootChildren(skCase), true), Lookups.singleton(NAME));
            super.setName(NAME);
            super.setDisplayName(NAME);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-size-16.png"); //NON-NLS
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
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

            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileSize.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "FileSize.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "FileSize.createSheet.name.desc"),
                    NAME));
            return s;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    /*
     * Makes the children for specific sizes
     */
    public static class FileSizeRootChildren extends ChildFactory<org.sleuthkit.autopsy.datamodel.FileSize.FileSizeFilter> {

        private SleuthkitCase skCase;
        private Observable notifier;

        public FileSizeRootChildren(SleuthkitCase skCase) {
            this.skCase = skCase;
            notifier = new FileSizeRootChildrenObservable();
        }

        /**
         * Listens for case and ingest invest. Updates observers when events are
         * fired. Size-based nodes are listening to this for changes.
         */
        private static final class FileSizeRootChildrenObservable extends Observable {

            private static final Set<Case.Events> CASE_EVENTS_OF_INTEREST = EnumSet.of(Case.Events.DATA_SOURCE_ADDED, Case.Events.CURRENT_CASE);

            FileSizeRootChildrenObservable() {
                IngestManager.getInstance().addIngestJobEventListener(pcl);
                IngestManager.getInstance().addIngestModuleEventListener(pcl);
                Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, pcl);
            }

            private void removeListeners() {
                deleteObservers();
                IngestManager.getInstance().removeIngestJobEventListener(pcl);
                IngestManager.getInstance().removeIngestModuleEventListener(pcl);
                Case.removeEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, pcl);
            }

            private final PropertyChangeListener pcl = (PropertyChangeEvent evt) -> {
                String eventType = evt.getPropertyName();

                if (eventType.equals(IngestManager.IngestModuleEvent.CONTENT_CHANGED.toString())) {
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        // new file was added
                        // @@@ could check the size here and only fire off updates if we know the file meets the min size criteria
                        Case.getOpenCase();
                        update();
                    } catch (NoCurrentCaseException notUsed) {
                        /**
                         * Case is closed, do nothing.
                         */
                    }
                } else if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                        || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())
                        || eventType.equals(Case.Events.DATA_SOURCE_ADDED.toString())) {
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getOpenCase();
                        update();
                    } catch (NoCurrentCaseException notUsed) {
                        /**
                         * Case is closed, do nothing.
                         */
                    }
                } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                    // case was closed. Remove listeners so that we don't get called with a stale case handle
                    if (evt.getNewValue() == null) {
                        removeListeners();
                    }
                }
            };

            private void update() {
                setChanged();
                notifyObservers();
            }
        }

        @Override
        protected boolean createKeys(List<FileSizeFilter> list) {
            list.addAll(Arrays.asList(FileSizeFilter.values()));
            return true;
        }

        @Override
        protected Node createNodeForKey(FileSizeFilter key) {
            return new FileSizeNode(skCase, key, notifier);
        }

        /*
         * Node for a specific size range. Children are files.
         */
        public class FileSizeNode extends DisplayableItemNode {

            private FileSizeFilter filter;

            // use version with observer instead so that it updates
            @Deprecated
            FileSizeNode(SleuthkitCase skCase, FileSizeFilter filter) {
                super(Children.create(new FileSizeChildren(filter, skCase, null), true), Lookups.singleton(filter.getDisplayName()));
                this.filter = filter;
                init();
            }

            /**
             *
             * @param skCase
             * @param filter
             * @param o      Observable that provides updates when events are
             *               fired
             */
            FileSizeNode(SleuthkitCase skCase, FileSizeFilter filter, Observable o) {
                super(Children.create(new FileSizeChildren(filter, skCase, o), true), Lookups.singleton(filter.getDisplayName()));
                this.filter = filter;
                init();
                o.addObserver(new FileSizeNodeObserver());
            }

            private void init() {
                super.setName(filter.getName());

                String tooltip = filter.getDisplayName();
                this.setShortDescription(tooltip);
                this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-size-16.png"); //NON-NLS

                updateDisplayName();
            }

            @Override
            public String getItemType() {
                /**
                 * Return getClass().getName() + filter.getName() if custom
                 * settings are desired for different filters.
                 */
                return DisplayableItemNode.FILE_PARENT_NODE_KEY;
            }

            // update the display name when new events are fired
            private class FileSizeNodeObserver implements Observer {

                @Override
                public void update(Observable o, Object arg) {
                    updateDisplayName();
                }
            }

            private void updateDisplayName() {
                final long numVisibleChildren = FileSizeChildren.calculateItems(skCase, filter);
                super.setDisplayName(filter.getDisplayName() + " (" + numVisibleChildren + ")");
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

                ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileSize.createSheet.filterType.name"),
                        NbBundle.getMessage(this.getClass(), "FileSize.createSheet.filterType.displayName"),
                        NbBundle.getMessage(this.getClass(), "FileSize.createSheet.filterType.desc"),
                        filter.getDisplayName()));

                return s;
            }

            @Override
            public boolean isLeafTypeNode() {
                return true;
            }
        }

        /*
         * Makes children, which are nodes for files of a given range
         */
        static class FileSizeChildren extends ChildFactory.Detachable<AbstractFile> {

            private final SleuthkitCase skCase;
            private final FileSizeFilter filter;
            private final Observable notifier;
            private static final Logger logger = Logger.getLogger(FileSizeChildren.class.getName());

            /**
             *
             * @param filter
             * @param skCase
             * @param o      Observable that provides updates when new files are
             *               added to case
             */
            FileSizeChildren(FileSizeFilter filter, SleuthkitCase skCase, Observable o) {
                this.skCase = skCase;
                this.filter = filter;
                this.notifier = o;
            }

            @Override
            protected void addNotify() {
                if (notifier != null) {
                    notifier.addObserver(observer);
                }
            }

            @Override
            protected void removeNotify() {
                if (notifier != null) {
                    notifier.deleteObserver(observer);
                }
            }

            private final Observer observer = new FileSizeChildrenObserver();

            // Cause refresh of children if there are changes
            private class FileSizeChildrenObserver implements Observer {

                @Override
                public void update(Observable o, Object arg) {
                    refresh(true);
                }
            }

            @Override
            protected boolean createKeys(List<AbstractFile> list) {
                list.addAll(runFsQuery());
                return true;
            }

            private static String makeQuery(FileSizeFilter filter) {
                String query;
                switch (filter) {
                    case SIZE_50_200:
                        query = "(size >= 50000000 AND size < 200000000)"; //NON-NLS
                        break;
                    case SIZE_200_1000:
                        query = "(size >= 200000000 AND size < 1000000000)"; //NON-NLS
                        break;

                    case SIZE_1000_:
                        query = "(size >= 1000000000)"; //NON-NLS
                        break;

                    default:
                        throw new IllegalArgumentException("Unsupported filter type to get files by size: " + filter); //NON-NLS
                }
                
                // Ignore unallocated block files.
                query = query + " AND (type != " + TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS.getFileType() + ")"; //NON-NLS
                
                // Hide known files if indicated in the user preferences.
                if(UserPreferences.hideKnownFilesInViewsTree()) {
                    query += " AND (known != " + TskData.FileKnown.KNOWN.getFileKnownValue() //NON-NLS
                            + " OR known IS NULL)"; //NON-NLS
                }
                
                // Hide slack files if indicated in the user preferences.
                if(UserPreferences.hideSlackFilesInViewsTree()) {
                    query += " AND (type != " + TskData.TSK_DB_FILES_TYPE_ENUM.SLACK.getFileType() + ")"; //NON-NLS
                }

                return query;
            }

            private List<AbstractFile> runFsQuery() {
                List<AbstractFile> ret = new ArrayList<>();

                try {
                    String query = makeQuery(filter);

                    ret = skCase.findAllFilesWhere(query);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error getting files for the file size view: " + e.getMessage()); //NON-NLS
                }

                return ret;
            }

            /**
             * Get children count without actually loading all nodes
             *
             * @return
             */
            static long calculateItems(SleuthkitCase sleuthkitCase, FileSizeFilter filter) {
                try {
                    return sleuthkitCase.countFilesWhere(makeQuery(filter));
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error getting files by size search view count", ex); //NON-NLS
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
                    public FileNode visit(LocalFile f) {
                        return new FileNode(f, false);
                    }

                    @Override
                    public FileNode visit(DerivedFile f) {
                        return new FileNode(f, false);
                    }

                    @Override
                    public FileNode visit(VirtualDirectory f) {
                        return new FileNode(f, false);
                    }

                    @Override
                    public FileNode visit(SlackFile f) {
                        return new FileNode(f, false);
                    }

                    @Override
                    protected AbstractNode defaultVisit(Content di) {
                        throw new UnsupportedOperationException(
                                NbBundle.getMessage(this.getClass(),
                                        "FileSize.exception.notSupported.msg",
                                        di.toString()));
                    }
                });
            }
        }
    }
}
