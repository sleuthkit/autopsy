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
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.RecentFiles.RecentFilesFilter;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author dfickling
 */
public class RecentFilesFilterChildren extends ChildFactory<Content> {

    SleuthkitCase skCase;
    RecentFilesFilter filter;
    Calendar prevDay;
    private final static Logger logger = Logger.getLogger(RecentFilesFilterChildren.class.getName());
    //private final static int MAX_OBJECTS = 1000000;

    RecentFilesFilterChildren(RecentFilesFilter filter, SleuthkitCase skCase, Calendar lastDay) {
        this.skCase = skCase;
        this.filter = filter;
        this.prevDay = (Calendar) lastDay.clone();
        prevDay.add(Calendar.DATE, -filter.getDurationDays());
    }

    @Override
    protected boolean createKeys(List<Content> list) {
        list.addAll(runFsQuery());
        return true;
    }

    private String createQuery() {
        String query = "known <> 1 AND (";
        long lowerLimit = prevDay.getTimeInMillis() / 1000;
        prevDay.add(Calendar.DATE, 1);
        prevDay.add(Calendar.MILLISECOND, -1);
        long upperLimit = prevDay.getTimeInMillis() / 1000;
        query += "(crtime BETWEEN " + lowerLimit + " AND " + upperLimit + ") OR ";
        query += "(ctime BETWEEN " + lowerLimit + " AND " + upperLimit + ") OR ";
        //query += "(atime BETWEEN " + lowerLimit + " AND " + upperLimit + ") OR ";
        query += "(mtime BETWEEN " + lowerLimit + " AND " + upperLimit + "))";
        //query += " LIMIT " + MAX_OBJECTS;
        return query;
    }

    private List<FsContent> runFsQuery() {
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
            protected AbstractNode defaultVisit(Content di) {
                throw new UnsupportedOperationException("Not supported for this type of Displayable Item: " + di.toString());
            }
        });
    }
}
