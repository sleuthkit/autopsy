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

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.RecentFiles.RecentFilesFilter;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 *
 * @author dfickling
 */
class RecentFilesFilterChildren extends ChildFactory<Content> {

    private SleuthkitCase skCase;
    private RecentFilesFilter filter;
    private Calendar prevDay;
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
        list.addAll(runQuery());
        return true;
    }

    private String createQuery() {
        Calendar prevDayQuery = (Calendar) prevDay.clone();
        String query = "(dir_type = " + TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue() + ")" //NON-NLS
                + " AND (known IS NULL OR known != 1) AND ("; //NON-NLS
        long lowerLimit = prevDayQuery.getTimeInMillis() / 1000;
        prevDayQuery.add(Calendar.DATE, 1);
        prevDayQuery.add(Calendar.MILLISECOND, -1);
        long upperLimit = prevDayQuery.getTimeInMillis() / 1000;
        query += "(crtime BETWEEN " + lowerLimit + " AND " + upperLimit + ") OR "; //NON-NLS
        query += "(ctime BETWEEN " + lowerLimit + " AND " + upperLimit + ") OR "; //NON-NLS
        //query += "(atime BETWEEN " + lowerLimit + " AND " + upperLimit + ") OR ";
        query += "(mtime BETWEEN " + lowerLimit + " AND " + upperLimit + "))"; //NON-NLS
        //query += " LIMIT " + MAX_OBJECTS;
        return query;
    }

    private List<AbstractFile> runQuery() {
        List<AbstractFile> ret = new ArrayList<AbstractFile>();
        try {
            List<AbstractFile> found = skCase.findAllFilesWhere(createQuery());
            for (AbstractFile c : found) {
                ret.add(c);
            }

        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Couldn't get search results", ex); //NON-NLS
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
            return skCase.countFilesWhere(createQuery());
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting recent files search view count", ex); //NON-NLS
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
            public LocalFileNode visit(DerivedFile f) {
                return new LocalFileNode(f);
            }

            @Override
            public LocalFileNode visit(LocalFile f) {
                return new LocalFileNode(f);
            }

            @Override
            protected AbstractNode defaultVisit(Content di) {
                throw new UnsupportedOperationException(
                        NbBundle.getMessage(this.getClass(),
                                "RecentFilesFilterChildren.exception.defaultVisit.msg",
                                di.toString()));
            }
        });
    }
}
