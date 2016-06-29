/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013-2015 Basis Technology Corp.
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
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * deleted content view nodes
 */
public class DeletedContent implements AutopsyVisitableItem {

    private SleuthkitCase skCase;

    public enum DeletedContentFilter implements AutopsyVisitableItem {

        FS_DELETED_FILTER(0,
                "FS_DELETED_FILTER", //NON-NLS
                NbBundle.getMessage(DeletedContent.class, "DeletedContent.fsDelFilter.text")),
        ALL_DELETED_FILTER(1,
                "ALL_DELETED_FILTER", //NON-NLS
                NbBundle.getMessage(DeletedContent.class, "DeletedContent.allDelFilter.text"));
        private int id;
        private String name;
        private String displayName;

        private DeletedContentFilter(int id, String name, String displayName) {
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

    public DeletedContent(SleuthkitCase skCase) {
        this.skCase = skCase;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }

    public SleuthkitCase getSleuthkitCase() {
        return this.skCase;
    }

    public static class DeletedContentsNode extends DisplayableItemNode {

        private static final String NAME = NbBundle.getMessage(DeletedContent.class,
                "DeletedContent.deletedContentsNode.name");
        private SleuthkitCase skCase;

        DeletedContentsNode(SleuthkitCase skCase) {
            super(Children.create(new DeletedContentsChildren(skCase), true), Lookups.singleton(NAME));
            super.setName(NAME);
            super.setDisplayName(NAME);
            this.skCase = skCase;
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon-deleted.png"); //NON-NLS
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

            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "DeletedContent.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "DeletedContent.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "DeletedContent.createSheet.name.desc"),
                    NAME));
            return s;
        }

        /*
         * TODO (AUT-1849): Correct or remove peristent column reordering code
         *
         * Added to support this feature.
         */
//        @Override
//        public String getItemType() {
//            return "DeletedContent"; //NON-NLS
//        }
    }

    public static class DeletedContentsChildren extends ChildFactory<DeletedContent.DeletedContentFilter> {

        private SleuthkitCase skCase;
        private Observable notifier;
        // true if we have already told user that not all files will be shown
        private static volatile boolean maxFilesDialogShown = false;

        public DeletedContentsChildren(SleuthkitCase skCase) {
            this.skCase = skCase;
            this.notifier = new DeletedContentsChildrenObservable();
        }

        /**
         * Listens for case and ingest invest. Updates observers when events are
         * fired. Other nodes are listening to this for changes.
         */
        private final class DeletedContentsChildrenObservable extends Observable {

            DeletedContentsChildrenObservable() {
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
                    if (eventType.equals(IngestManager.IngestModuleEvent.CONTENT_CHANGED.toString())) {
                        /**
                         * + // @@@ COULD CHECK If the new file is deleted
                         * before notifying... Checking for a current case is a
                         * stop gap measure	+ update(); until a different way of
                         * handling the closing of cases is worked out.
                         * Currently, remote events may be received for a case
                         * that is already closed.
                         */
                        try {
                            Case.getCurrentCase();
                            // new file was added                            		
                            // @@@ COULD CHECK If the new file is deleted before notifying...		
                            update();
                        } catch (IllegalStateException notUsed) {
                            /**
                             * Case is closed, do nothing.
                             */
                        }
                    } else if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
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
                        maxFilesDialogShown = false;
                    }
                }
            };

            private void update() {
                setChanged();
                notifyObservers();
            }
        }

        @Override

        protected boolean createKeys(List<DeletedContent.DeletedContentFilter> list) {
            list.addAll(Arrays.asList(DeletedContent.DeletedContentFilter.values()));
            return true;
        }

        @Override
        protected Node createNodeForKey(DeletedContent.DeletedContentFilter key) {
            return new DeletedContentNode(skCase, key, notifier);
        }

        public class DeletedContentNode extends DisplayableItemNode {

            private final DeletedContent.DeletedContentFilter filter;

            // Use version that has observer for updates
            @Deprecated
            DeletedContentNode(SleuthkitCase skCase, DeletedContent.DeletedContentFilter filter) {
                super(Children.create(new DeletedContentChildren(filter, skCase, null), true), Lookups.singleton(filter.getDisplayName()));
                this.filter = filter;
                init();
            }

            DeletedContentNode(SleuthkitCase skCase, DeletedContent.DeletedContentFilter filter, Observable o) {
                super(Children.create(new DeletedContentChildren(filter, skCase, o), true), Lookups.singleton(filter.getDisplayName()));
                this.filter = filter;
                init();
                o.addObserver(new DeletedContentNodeObserver());
            }

            private void init() {
                super.setName(filter.getName());

                String tooltip = filter.getDisplayName();
                this.setShortDescription(tooltip);
                this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon-deleted.png"); //NON-NLS
                updateDisplayName();
            }

            /*
             * TODO (AUT-1849): Correct or remove peristent column reordering
             * code
             *
             * Added to support this feature.
             */
//            @Override
//            public String getItemType() {
//                return "DeletedContentChildren"; //NON-NLS
//            }
            // update the display name when new events are fired
            private class DeletedContentNodeObserver implements Observer {

                @Override
                public void update(Observable o, Object arg) {
                    updateDisplayName();
                }
            }

            private void updateDisplayName() {
                //get count of children without preloading all children nodes
                final long count = DeletedContentChildren.calculateItems(skCase, filter);
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

                ss.put(new NodeProperty<>(
                        NbBundle.getMessage(this.getClass(), "DeletedContent.createSheet.filterType.name"),
                        NbBundle.getMessage(this.getClass(), "DeletedContent.createSheet.filterType.displayName"),
                        NbBundle.getMessage(this.getClass(), "DeletedContent.createSheet.filterType.desc"),
                        filter.getDisplayName()));

                return s;
            }

            @Override
            public boolean isLeafTypeNode() {
                return true;
            }
        }

        static class DeletedContentChildren extends ChildFactory.Detachable<AbstractFile> {

            private final SleuthkitCase skCase;
            private final DeletedContent.DeletedContentFilter filter;
            private static final Logger logger = Logger.getLogger(DeletedContentChildren.class.getName());
            private static final int MAX_OBJECTS = 10001;
            private final Observable notifier;

            DeletedContentChildren(DeletedContent.DeletedContentFilter filter, SleuthkitCase skCase, Observable o) {
                this.skCase = skCase;
                this.filter = filter;
                this.notifier = o;
            }

            private final Observer observer = new DeletedContentChildrenObserver();

            // Cause refresh of children if there are changes
            private class DeletedContentChildrenObserver implements Observer {

                @Override
                public void update(Observable o, Object arg) {
                    refresh(true);
                }
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

            @Override
            protected boolean createKeys(List<AbstractFile> list) {
                List<AbstractFile> queryList = runFsQuery();
                if (queryList.size() == MAX_OBJECTS) {
                    queryList.remove(queryList.size() - 1);
                    // only show the dialog once - not each time we refresh
                    if (maxFilesDialogShown == false) {
                        maxFilesDialogShown = true;
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), NbBundle.getMessage(this.getClass(),
                                        "DeletedContent.createKeys.maxObjects.msg",
                                        MAX_OBJECTS - 1));
                            }
                        });
                    }
                }
                list.addAll(queryList);
                return true;
            }

            static private String makeQuery(DeletedContent.DeletedContentFilter filter) {
                String query = "";
                switch (filter) {
                    case FS_DELETED_FILTER:
                        query = "dir_flags = " + TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC.getValue() //NON-NLS
                                + " AND meta_flags != " + TskData.TSK_FS_META_FLAG_ENUM.ORPHAN.getValue() //NON-NLS
                                + " AND type = " + TskData.TSK_DB_FILES_TYPE_ENUM.FS.getFileType(); //NON-NLS

                        break;
                    case ALL_DELETED_FILTER:
                        query = " ( "
                                + "( "
                                + "(dir_flags = " + TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC.getValue() //NON-NLS
                                + " OR " //NON-NLS
                                + "meta_flags = " + TskData.TSK_FS_META_FLAG_ENUM.ORPHAN.getValue() //NON-NLS
                                + ")"
                                + " AND type = " + TskData.TSK_DB_FILES_TYPE_ENUM.FS.getFileType() //NON-NLS
                                + " )"
                                + " OR type = " + TskData.TSK_DB_FILES_TYPE_ENUM.CARVED.getFileType() //NON-NLS
                                + " )";
                        //+ " AND type != " + TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS.getFileType()
                        //+ " AND type != " + TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS.getFileType()
                        //+ " AND type != " + TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS.getFileType()
                        //+ " AND type != " + TskData.TSK_DB_FILES_TYPE_ENUM.DERIVED.getFileType()
                        //+ " AND type != " + TskData.TSK_DB_FILES_TYPE_ENUM.LOCAL.getFileType()
                        //+ " AND type != " + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType();
                        break;

                    default:
                        logger.log(Level.SEVERE, "Unsupported filter type to get deleted content: {0}", filter); //NON-NLS

                }

                query += " LIMIT " + MAX_OBJECTS; //NON-NLS
                return query;
            }

            private List<AbstractFile> runFsQuery() {
                List<AbstractFile> ret = new ArrayList<>();

                String query = makeQuery(filter);
                try {
                    ret = skCase.findAllFilesWhere(query);
                } catch (TskCoreException e) {
                    logger.log(Level.SEVERE, "Error getting files for the deleted content view using: " + query, e); //NON-NLS
                }

                return ret;

            }

            /**
             * Get children count without actually loading all nodes
             *
             * @return
             */
            static long calculateItems(SleuthkitCase sleuthkitCase, DeletedContent.DeletedContentFilter filter) {
                try {
                    return sleuthkitCase.countFilesWhere(makeQuery(filter));
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error getting deleted files search view count", ex); //NON-NLS
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
                        throw new UnsupportedOperationException(NbBundle.getMessage(this.getClass(),
                                "DeletedContent.createNodeForKey.typeNotSupported.msg",
                                di.toString()));
                    }
                });
            }
        }
    }
}
