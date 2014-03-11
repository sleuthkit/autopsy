/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 Basis Technology Corp.
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
import java.util.List;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Children factory for a specific file type - does the database query. 
 */
class FileTypeChildren extends ChildFactory<Content> {

    private SleuthkitCase skCase;
    private FileTypeExtensionFilters.SearchFilterInterface filter;
    private static final Logger logger = Logger.getLogger(FileTypeChildren.class.getName());
    //private final static int MAX_OBJECTS = 2000;

    public FileTypeChildren(FileTypeExtensionFilters.SearchFilterInterface filter, SleuthkitCase skCase) {
        this.filter = filter;
        this.skCase = skCase;
    }

    @Override
    protected boolean createKeys(List<Content> list) {
        list.addAll(runQuery());
        return true;
    }
    
    private String createQuery(){
        String query = "(dir_type = " + TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue() + ")"
                + " AND (known IS NULL OR known != " + TskData.FileKnown.KNOWN.getFileKnownValue() + ") AND (0";
        for(String s : filter.getFilter()){
            query += " OR name LIKE '%" + s + "'";
        }
        query += ')';
//        query += " LIMIT " + MAX_OBJECTS;
        return query;
    }

    
    private List<AbstractFile> runQuery(){
        List<AbstractFile> list = new ArrayList<>();
        try {
            list = skCase.findAllFilesWhere(createQuery());
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Couldn't get search results", ex);
        }

        return list;
        
    }
    
    /**
     * Get children count without actually loading all nodes
     * @return 
     */
    long calculateItems() {
        try {
            return skCase.countFilesWhere(createQuery());
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting file search view count", ex);
            return 0;
        }
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
                throw new UnsupportedOperationException(
                        NbBundle.getMessage(this.getClass(),
                                            "FileTypeChildren.exception.notSupported.msg",
                                            di.toString()));
            }
        });
    }
}
