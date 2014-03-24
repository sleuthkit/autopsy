/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013-2014 Basis Technology Corp.
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
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
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
import org.sleuthkit.datamodel.TskData;

/**
 * deleted content view nodes
 */
public class DeletedContent implements AutopsyVisitableItem {

    private SleuthkitCase skCase;

    public enum DeletedContentFilter implements AutopsyVisitableItem {

        FS_DELETED_FILTER(0,
        "FS_DELETED_FILTER",
        NbBundle.getMessage(DeletedContent.class, "DeletedContent.fsDelFilter.text")),
        ALL_DELETED_FILTER(1,
        "ALL_DELETED_FILTER",
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
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon-deleted.png");
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
    }

    public static class DeletedContentsChildren extends ChildFactory<DeletedContent.DeletedContentFilter> {

        private SleuthkitCase skCase;

        public DeletedContentsChildren(SleuthkitCase skCase) {
            this.skCase = skCase;

        }

        @Override
        protected boolean createKeys(List<DeletedContent.DeletedContentFilter> list) {
            list.addAll(Arrays.asList(DeletedContent.DeletedContentFilter.values()));
            return true;
        }

        @Override
        protected Node createNodeForKey(DeletedContent.DeletedContentFilter key) {
            return new DeletedContentNode(skCase, key);
        }

        public class DeletedContentNode extends DisplayableItemNode {

            private DeletedContent.DeletedContentFilter filter;
            private final Logger logger = Logger.getLogger(DeletedContentNode.class.getName());

            DeletedContentNode(SleuthkitCase skCase, DeletedContent.DeletedContentFilter filter) {
                super(Children.create(new DeletedContentChildren(filter, skCase), true), Lookups.singleton(filter.getDisplayName()));
                super.setName(filter.getName());
                this.filter = filter;

                String tooltip = filter.getDisplayName();
                this.setShortDescription(tooltip);
                this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon-deleted.png");

                //get count of children without preloading all children nodes
                final long count = new DeletedContentChildren(filter, skCase).calculateItems();
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

        class DeletedContentChildren extends ChildFactory<AbstractFile> {

            private SleuthkitCase skCase;
            private DeletedContent.DeletedContentFilter filter;
            private final Logger logger = Logger.getLogger(DeletedContentChildren.class.getName());
            private static final int MAX_OBJECTS = 10001;

            DeletedContentChildren(DeletedContent.DeletedContentFilter filter, SleuthkitCase skCase) {
                this.skCase = skCase;
                this.filter = filter;
            }

            @Override
            protected boolean createKeys(List<AbstractFile> list) {
                List<AbstractFile> queryList = runFsQuery();
                if (queryList.size() == MAX_OBJECTS) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            JOptionPane.showMessageDialog(null, NbBundle.getMessage(this.getClass(),
                                    "DeletedContent.createKeys.maxObjects.msg",
                                    MAX_OBJECTS - 1));
                        }
                    });
                }

                queryList.remove(queryList.size() - 1);
                list.addAll(queryList);
                return true;
            }

            private String makeQuery() {
                String query = "";
                switch (filter) {
                    case FS_DELETED_FILTER:
                        query = "dir_flags = " + TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC.getValue()
                                + " AND meta_flags != " + TskData.TSK_FS_META_FLAG_ENUM.ORPHAN.getValue()
                                + " AND type = " + TskData.TSK_DB_FILES_TYPE_ENUM.FS.getFileType();

                        break;
                    case ALL_DELETED_FILTER:
                        query = " ( "
                                + "( "
                                + "(dir_flags = " + TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC.getValue()
                                + " OR "
                                + "meta_flags = " + TskData.TSK_FS_META_FLAG_ENUM.ORPHAN.getValue()
                                + ")"
                                + " AND type = " + TskData.TSK_DB_FILES_TYPE_ENUM.FS.getFileType()
                                + " )"
                                + " OR type = " + TskData.TSK_DB_FILES_TYPE_ENUM.CARVED.getFileType()
                                + " )";
                        //+ " AND type != " + TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS.getFileType()
                        //+ " AND type != " + TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS.getFileType()
                        //+ " AND type != " + TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS.getFileType()
                        //+ " AND type != " + TskData.TSK_DB_FILES_TYPE_ENUM.DERIVED.getFileType()
                        //+ " AND type != " + TskData.TSK_DB_FILES_TYPE_ENUM.LOCAL.getFileType()
                        //+ " AND type != " + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType();
                        break;

                    default:
                        logger.log(Level.SEVERE, "Unsupported filter type to get deleted content: {0}", filter);

                }

                query += " LIMIT " + MAX_OBJECTS;
                return query;
            }

            private List<AbstractFile> runFsQuery() {
                List<AbstractFile> ret = new ArrayList<>();

                String query = makeQuery();
                try {
                    ret = skCase.findAllFilesWhere(query);
                } catch (TskCoreException e) {
                    logger.log(Level.SEVERE, "Error getting files for the deleted content view using: " + query, e);
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
                    logger.log(Level.SEVERE, "Error getting deleted files search view count", ex);
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