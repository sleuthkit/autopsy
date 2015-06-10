/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2014 Basis Technology Corp.
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

import java.util.List;
import java.util.logging.Level;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
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
 * Node for a specific file type / extension
 */
public class FileTypeNode extends DisplayableItemNode {

    FileTypeExtensionFilters.SearchFilterInterface filter;
    SleuthkitCase skCase;

    FileTypeNode(FileTypeExtensionFilters.SearchFilterInterface filter, SleuthkitCase skCase) {
        super(Children.create(new FileTypeChildFactory(filter, skCase), true), Lookups.singleton(filter.getDisplayName()));
        this.filter = filter;
        this.skCase = skCase;

        super.setName(filter.getName());

        /* There is a bug in the below code. The count becomes inaccurate when ZIP files
         * blow up and when a 2nd data source is added. I think we need to change the overall
         * design of this and have a singleton class (or some other place) that will store
         * the results and each level can either ask it for the count or specific ids and
         * it would be responsible for listening for case events and data events. 
         */
        //get count of children without preloading all children nodes
        final long count = new FileTypeChildFactory(filter, skCase).calculateItems();
        //final long count = getChildren().getNodesCount(true);
        
        super.setDisplayName(filter.getDisplayName() + " (" + count + ")");
        
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-filter-icon.png"); //NON-NLS
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

        ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileTypeNode.createSheet.filterType.name"),
                NbBundle.getMessage(this.getClass(), "FileTypeNode.createSheet.filterType.displayName"),
                NbBundle.getMessage(this.getClass(), "FileTypeNode.createSheet.filterType.desc"),
                filter.getDisplayName()));
        String extensions = "";
        for (String ext : filter.getFilter()) {
            extensions += "'" + ext + "', ";
        }
        extensions = extensions.substring(0, extensions.lastIndexOf(','));
        ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileTypeNode.createSheet.fileExt.name"),
                NbBundle.getMessage(this.getClass(), "FileTypeNode.createSheet.fileExt.displayName"),
                NbBundle.getMessage(this.getClass(), "FileTypeNode.createSheet.fileExt.desc"),
                extensions));

        return s;
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }

    /**
     * Child node factory for a specific file type - does the database query.
     */
    public static class FileTypeChildFactory extends ChildFactory<Content> {
        private final SleuthkitCase skCase;
        private final FileTypeExtensionFilters.SearchFilterInterface filter;
        private final Logger logger = Logger.getLogger(FileTypeChildFactory.class.getName());

        FileTypeChildFactory(FileTypeExtensionFilters.SearchFilterInterface filter, SleuthkitCase skCase) {
            super();
            this.filter = filter;
            this.skCase = skCase;
        }

        /**
         * Get children count without actually loading all nodes
         * @return
         */
        long calculateItems() {
            try {
                return skCase.countFilesWhere(createQuery());
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error getting file search view count", ex); //NON-NLS
                return 0;
            }
        }

        @Override
        protected boolean createKeys(List<Content> list) {
            try {
                List<AbstractFile> files = skCase.findAllFilesWhere(createQuery()); 
                list.addAll(files);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Couldn't get search results", ex); //NON-NLS
            }
            return true;
        }

        private String createQuery() {
            StringBuilder query = new StringBuilder();
            query.append("(dir_type = ").append(TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue()).append(")"); //NON-NLS
            if (UserPreferences.hideKnownFilesInViewsTree()) {
                query.append(" AND (known IS NULL OR known != ").append(TskData.FileKnown.KNOWN.getFileKnownValue()).append(")"); //NON-NLS
            }
            query.append(" AND (0"); //NON-NLS
            for (String s : filter.getFilter()) {
                query.append(" OR name LIKE '%").append(s).append("'"); //NON-NLS
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
