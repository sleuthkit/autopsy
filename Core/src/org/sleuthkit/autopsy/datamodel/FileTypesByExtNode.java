/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2015 Basis Technology Corp.
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

import org.sleuthkit.autopsy.datamodel.accounts.FileTypeExtensionFilters;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Node for root of file types view. Children are nodes for specific types.
 */
class FileTypesByExtNode extends DisplayableItemNode {

    private static final String FNAME = NbBundle.getMessage(FileTypesByExtNode.class, "FileTypesByExtNode.fname.text");
    private final FileTypeExtensionFilters.RootFilter filter;

    /**
     *
     * @param skCase
     * @param filter null to display root node of file type tree, pass in
     * something to provide a sub-node.
     */
    FileTypesByExtNode(SleuthkitCase skCase, FileTypeExtensionFilters.RootFilter filter) {
        super(Children.create(new ByExtChildren(skCase, filter, null), true), Lookups.singleton(filter == null ? FNAME : filter.getName()));
        this.filter = filter;
        init();
    }

    /**
     *
     * @param skCase
     * @param filter
     * @param o Observable that was created by a higher-level node that provides
     * updates on events
     */
    private FileTypesByExtNode(SleuthkitCase skCase, FileTypeExtensionFilters.RootFilter filter, Observable o) {
        super(Children.create(new ByExtChildren(skCase, filter, o), true), Lookups.singleton(filter == null ? FNAME : filter.getName()));
        this.filter = filter;
        init();
    }

    private void init() {
        // root node of tree
        if (filter == null) {
            super.setName(FNAME);
            super.setDisplayName(FNAME);
        } // sub-node in file tree (i.e. documents, exec, etc.)
        else {
            super.setName(filter.getName());
            super.setDisplayName(filter.getDisplayName());
        }
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file_types.png"); //NON-NLS    
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

        ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.name.name"),
                NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.name.displayName"),
                NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.name.desc"),
                getName()));
        return s;
    }

    @Override
    public String getItemType() {
        /**
         * Because Documents and Executable are further expandable, their column
         * order settings should be stored separately.
         */
        if (filter == null) {
            return getClass().getName();
        }
        if (filter.equals(FileTypeExtensionFilters.RootFilter.TSK_DOCUMENT_FILTER)
                || filter.equals(FileTypeExtensionFilters.RootFilter.TSK_EXECUTABLE_FILTER)) {
            return getClass().getName() + filter.getName();
        }
        return getClass().getName();
    }

    /**
     *
     */
    private static class ByExtChildren extends ChildFactory<FileTypeExtensionFilters.SearchFilterInterface> {

        private final SleuthkitCase skCase;
        private final FileTypeExtensionFilters.RootFilter filter;
        private final Observable notifier;

        /**
         *
         * @param skCase
         * @param filter Is null for root node
         * @param o Observable that provides updates based on events being fired
         * (or null if one needs to be created)
         */
        private ByExtChildren(SleuthkitCase skCase, FileTypeExtensionFilters.RootFilter filter, Observable o) {
            super();
            this.skCase = skCase;
            this.filter = filter;
            if (o == null) {
                this.notifier = new ByExtChildrenObservable();
            } else {
                this.notifier = o;
            }
        }

        /**
         * Listens for case and ingest invest. Updates observers when events are
         * fired. FileType and FileTypes nodes are all listening to this.
         */
        private final class ByExtChildrenObservable extends Observable {

            private ByExtChildrenObservable() {
                IngestManager.getInstance().addIngestJobEventListener(pcl);
                IngestManager.getInstance().addIngestModuleEventListener(pcl);
                Case.addPropertyChangeListener(pcl);
            }

            private void removeListeners() {
                deleteObservers();
                IngestManager.getInstance().removeIngestJobEventListener(pcl);
                IngestManager.getInstance().removeIngestModuleEventListener(pcl);
                Case.removePropertyChangeListener(pcl);
            }

            private final PropertyChangeListener pcl = new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    String eventType = evt.getPropertyName();
                    if (eventType.equals(IngestManager.IngestModuleEvent.CONTENT_CHANGED.toString())
                            || eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                            || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())
                            || eventType.equals(Case.Events.DATA_SOURCE_ADDED.toString())) {
                        /**
                         * Checking for a current case is a stop gap measure
                         * until a different way of handling the closing of
                         * cases is worked out. Currently, remote events may be
                         * received for a case that is already closed.
                         */
                        try {
                            Case.getCurrentCase();
                            update();
                        } catch (IllegalStateException notUsed) {
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
                }
            };

            private void update() {
                setChanged();
                notifyObservers();
            }
        }

        @Override
        protected boolean createKeys(List<FileTypeExtensionFilters.SearchFilterInterface> list) {
            // root node
            if (filter == null) {
                list.addAll(Arrays.asList(FileTypeExtensionFilters.RootFilter.values()));
            } // document and executable has another level of nodes
            else if (filter.equals(FileTypeExtensionFilters.RootFilter.TSK_DOCUMENT_FILTER)) {
                list.addAll(Arrays.asList(FileTypeExtensionFilters.DocumentFilter.values()));
            } else if (filter.equals(FileTypeExtensionFilters.RootFilter.TSK_EXECUTABLE_FILTER)) {
                list.addAll(Arrays.asList(FileTypeExtensionFilters.ExecutableFilter.values()));
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(FileTypeExtensionFilters.SearchFilterInterface key) {
            // make new nodes for the sub-nodes
            if (key.getName().equals(FileTypeExtensionFilters.RootFilter.TSK_DOCUMENT_FILTER.getName())) {
                return new FileTypesByExtNode(skCase, FileTypeExtensionFilters.RootFilter.TSK_DOCUMENT_FILTER, notifier);
            } else if (key.getName().equals(FileTypeExtensionFilters.RootFilter.TSK_EXECUTABLE_FILTER.getName())) {
                return new FileTypesByExtNode(skCase, FileTypeExtensionFilters.RootFilter.TSK_EXECUTABLE_FILTER, notifier);
            } else {
                return new ByExtNode(key, skCase, notifier);
            }
        }
    }

    /**
     * Node for a specific file type / extension. Children of it will be the
     * files of that type.
     */
    static class ByExtNode extends DisplayableItemNode {

        FileTypeExtensionFilters.SearchFilterInterface filter;
        SleuthkitCase skCase;

        /**
         *
         * @param filter Extensions that will be shown for this node
         * @param skCase
         * @param o Observable that sends updates when the child factories
         * should refresh
         */
        ByExtNode(FileTypeExtensionFilters.SearchFilterInterface filter, SleuthkitCase skCase, Observable o) {
            super(Children.create(new ByExtChildFactory(filter, skCase, o), true), Lookups.singleton(filter.getDisplayName()));
            this.filter = filter;
            this.skCase = skCase;
            init();
            o.addObserver(new ByExtNodeObserver());
        }

        private void init() {
            super.setName(filter.getName());
            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-filter-icon.png"); //NON-NLS
        }

        // update the display name when new events are fired
        private class ByExtNodeObserver implements Observer {

            @Override
            public void update(Observable o, Object arg) {
                updateDisplayName();
            }
        }

        private void updateDisplayName() {
            final long count = ByExtChildFactory.calculateItems(skCase, filter);
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

            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.filterType.name"),
                    NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.filterType.displayName"),
                    NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.filterType.desc"),
                    filter.getDisplayName()));
            String extensions = "";
            for (String ext : filter.getFilter()) {
                extensions += "'" + ext + "', ";
            }
            extensions = extensions.substring(0, extensions.lastIndexOf(','));
            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.fileExt.name"),
                    NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.fileExt.displayName"),
                    NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.fileExt.desc"),
                    extensions));

            return s;
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }

        /**
         * Consider allowing different configurations for Images, Videos, etc
         * (in which case we'd return getClass().getName() + filter.getName()
         * for all filters).
         */
        @Override
        public String getItemType() {
            return DisplayableItemNode.FILE_PARENT_NODE_KEY;
        }

        /**
         * Child node factory for a specific file type - does the database
         * query.
         */
        private static class ByExtChildFactory extends ChildFactory.Detachable<Content> {

            private final SleuthkitCase skCase;
            private final FileTypeExtensionFilters.SearchFilterInterface filter;
            private final static Logger LOGGER = Logger.getLogger(ByExtChildFactory.class.getName());
            private final Observable notifier;

            // use the constructor that gets an observable passed in for updates
            @Deprecated
            ByExtChildFactory(FileTypeExtensionFilters.SearchFilterInterface filter, SleuthkitCase skCase) {
                super();
                this.filter = filter;
                this.skCase = skCase;
                notifier = null;
            }

            /**
             *
             * @param filter Extensions to display
             * @param skCase
             * @param o Observable that will notify when there could be new data
             * to display
             */
            private ByExtChildFactory(FileTypeExtensionFilters.SearchFilterInterface filter, SleuthkitCase skCase, Observable o) {
                super();
                this.filter = filter;
                this.skCase = skCase;
                notifier = o;
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
            private final Observer observer = new FileTypeChildFactoryObserver();

            // Cause refresh of children if there are changes
            private class FileTypeChildFactoryObserver implements Observer {

                @Override
                public void update(Observable o, Object arg) {
                    refresh(true);
                }
            }

            /**
             * Get children count without actually loading all nodes
             *
             * @return
             */
            private static long calculateItems(SleuthkitCase sleuthkitCase, FileTypeExtensionFilters.SearchFilterInterface filter) {
                try {
                    return sleuthkitCase.countFilesWhere(createQuery(filter));
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "Error getting file search view count", ex); //NON-NLS
                    return 0;
                }
            }

            @Override
            protected boolean createKeys(List<Content> list) {
                try {
                    List<AbstractFile> files = skCase.findAllFilesWhere(createQuery(filter));
                    list.addAll(files);
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "Couldn't get search results", ex); //NON-NLS
                }
                return true;
            }

            private static String createQuery(FileTypeExtensionFilters.SearchFilterInterface filter) {
                StringBuilder query = new StringBuilder();
                query.append("(dir_type = ").append(TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue()).append(")"); //NON-NLS
                if (UserPreferences.hideKnownFilesInViewsTree()) {
                    query.append(" AND (known IS NULL OR known != ").append(TskData.FileKnown.KNOWN.getFileKnownValue()).append(")"); //NON-NLS
                }
                query.append(" AND (NULL"); //NON-NLS
                for (String s : filter.getFilter()) {
                    query.append(" OR LOWER(name) LIKE LOWER('%").append(s).append("')"); //NON-NLS
                }
                query.append(')');
                return query.toString();
            }

            @Override
            protected Node createNodeForKey(Content key) {
                return key.accept(new ContentVisitor.Default<AbstractNode>() {
                    @Override
                    public FileNode visit(File f) {
                        return new FileNode(f, false);
                    }

                    @Override
                    public DirectoryNode visit(Directory d) {
                        return new DirectoryNode(d);
                    }

                    @Override
                    public LayoutFileNode visit(LayoutFile lf) {
                        return new LayoutFileNode(lf);
                    }

                    @Override
                    public LocalFileNode visit(DerivedFile df) {
                        return new LocalFileNode(df);
                    }

                    @Override
                    public LocalFileNode visit(LocalFile lf) {
                        return new LocalFileNode(lf);
                    }

                    @Override
                    protected AbstractNode defaultVisit(Content di) {
                        throw new UnsupportedOperationException(NbBundle.getMessage(this.getClass(), "FileTypeChildren.exception.notSupported.msg", di.toString()));
                    }
                });
            }
        }
    }
}
