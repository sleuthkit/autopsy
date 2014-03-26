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
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
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

        SIZE_50_200(0, "SIZE_50_200", "50 - 200MB"),
        SIZE_200_1000(1, "SIZE_200_1GB", "200MB - 1GB"),
        SIZE_1000_(2, "SIZE_1000+", "1GB+");
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

    public static class FileSizeRootNode extends DisplayableItemNode {

        private static final String NAME = NbBundle.getMessage(FileSize.class, "FileSize.fileSizeRootNode.name");

        FileSizeRootNode(SleuthkitCase skCase) {
            super(Children.create(new FileSizeRootChildren(skCase), true), Lookups.singleton(NAME));
            super.setName(NAME);
            super.setDisplayName(NAME);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-size-16.png");
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
    }

    public static class FileSizeRootChildren extends ChildFactory<org.sleuthkit.autopsy.datamodel.FileSize.FileSizeFilter> {

        private SleuthkitCase skCase;

        public FileSizeRootChildren(SleuthkitCase skCase) {
            this.skCase = skCase;

        }

        @Override
        protected boolean createKeys(List<FileSizeFilter> list) {
            list.addAll(Arrays.asList(FileSizeFilter.values()));
            return true;
        }

        @Override
        protected Node createNodeForKey(FileSizeFilter key) {
            return new FileSizeNode(skCase, key);
        }

        public class FileSizeNode extends DisplayableItemNode {

            private FileSizeFilter filter;
            private final Logger logger = Logger.getLogger(FileSizeNode.class.getName());

            FileSizeNode(SleuthkitCase skCase, FileSizeFilter filter) {
                super(Children.create(new FileSizeChildren(filter, skCase), true), Lookups.singleton(filter.getDisplayName()));
                super.setName(filter.getName());
                this.filter = filter;

                String tooltip = filter.getDisplayName();
                this.setShortDescription(tooltip);
                this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-size-16.png");

                //get count of children without preloading all children nodes
                final long count = new FileSizeChildren(filter, skCase).calculateItems();
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

        class FileSizeChildren extends ChildFactory<AbstractFile> {

            private SleuthkitCase skCase;
            private FileSizeFilter filter;
            private final Logger logger = Logger.getLogger(FileSizeChildren.class.getName());

            FileSizeChildren(FileSizeFilter filter, SleuthkitCase skCase) {
                this.skCase = skCase;
                this.filter = filter;
            }

            @Override
            protected boolean createKeys(List<AbstractFile> list) {
                List<AbstractFile> l = runFsQuery();
                if (l == null) {
                    return false;
                }
                list.addAll(l);
                return true;
            }

            private String makeQuery() {
                String query;
                switch (filter) {
                    case SIZE_50_200:
                        query = "(size >= 50000000 AND size < 200000000)";
                        break;
                    case SIZE_200_1000:
                        query = "(size >= 200000000 AND size < 1000000000)";
                        break;

                    case SIZE_1000_:
                        query = "(size >= 1000000000)";
                        break;

                    default:
                        logger.log(Level.SEVERE, "Unsupported filter type to get files by size: {0}", filter);
                        return null;
                }
                // ignore unalloc block files
                query = query + " AND (type != " + TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS.getFileType() + ")";

                return query;
            }

            private List<AbstractFile> runFsQuery() {
                List<AbstractFile> ret = new ArrayList<>();

                String query = makeQuery();
                if (query == null) {
                    return null;
                }

                try {
                    ret = skCase.findAllFilesWhere(query);
                } catch (TskCoreException e) {
                    logger.log(Level.SEVERE, "Error getting files for the file size view using: " + query, e);
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
                    logger.log(Level.SEVERE, "Error getting files by size search view count", ex);
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
