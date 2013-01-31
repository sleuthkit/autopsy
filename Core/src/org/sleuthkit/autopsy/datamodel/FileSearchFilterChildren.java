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

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author dfickling
 */
class FileSearchFilterChildren extends ChildFactory<Content> {

    SleuthkitCase skCase;
    SearchFilters.SearchFilterInterface filter;
    Logger logger = Logger.getLogger(FileSearchFilterChildren.class.getName());
    //private final static int MAX_OBJECTS = 2000;

    public FileSearchFilterChildren(SearchFilters.SearchFilterInterface filter, SleuthkitCase skCase) {
        this.filter = filter;
        this.skCase = skCase;
    }

    @Override
    protected boolean createKeys(List<Content> list) {
        list.addAll(runQuery());
        return true;
    }

    private String createQuery() {
        String query = "known <> 1 and (0";
        for (String s : filter.getFilter()) {
            query += " OR name LIKE '%" + s + "'";
        }
        query += ')';
        //query += " LIMIT " + MAX_OBJECTS;
        return query;
    }

    private List<FsContent> runQuery() {
        ResultSet rs = null;
        List<FsContent> ret = new ArrayList<FsContent>();
        try {
            List<FsContent> found = skCase.findFilesWhere(createQuery());
            for (FsContent c : found) {
                if (c.isFile()) {
                    ret.add(c);
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Couldn't get search results", ex);
        } 
        return ret;

    }

    @Override
    protected Node createNodeForKey(Content key) {
        return key.accept(new ContentVisitor.Default<AbstractNode>() {
            @Override
            public FileNode visit(File f) {
                return new FileNode(f, false);
            }

            @Override
            public DerivedFileNode visit(DerivedFile df) {
                return new DerivedFileNode(df);
            }

            @Override
            protected AbstractNode defaultVisit(Content di) {
                throw new UnsupportedOperationException("Not supported for this type of Displayable Item: " + di.toString());
            }
        });
    }
}
