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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 *
 * @author dfickling
 */
 class RecentFilesChildren extends ChildFactory<RecentFiles.RecentFilesFilter> {

    private SleuthkitCase skCase;
    private Calendar lastDay;
    private final static Logger logger = Logger.getLogger(RecentFilesChildren.class.getName());

    public RecentFilesChildren(SleuthkitCase skCase) {
        this.skCase = skCase;
    }

    @Override
    protected boolean createKeys(List<RecentFiles.RecentFilesFilter> list) {
        list.addAll(Arrays.asList(RecentFiles.RecentFilesFilter.values()));
        lastDay = Calendar.getInstance();
        lastDay.setTimeInMillis(getLastTime() * 1000);
        lastDay.set(Calendar.HOUR_OF_DAY, 0);
        lastDay.set(Calendar.MINUTE, 0);
        lastDay.set(Calendar.SECOND, 0);
        lastDay.set(Calendar.MILLISECOND, 0);
        return true;
    }

    @Override
    protected Node createNodeForKey(RecentFiles.RecentFilesFilter key) {
        return new RecentFilesFilterNode(skCase, key, lastDay);
    }

    private long getLastTime() {
        String query = createMaxQuery("crtime");
        long maxcr = runTimeQuery(query);
        query = createMaxQuery("ctime");
        long maxc = runTimeQuery(query);
        query = createMaxQuery("mtime");
        long maxm = runTimeQuery(query);
        //query = createMaxQuery("atime");
        //long maxa = runTimeQuery(query);
        //return Math.max(maxcr, Math.max(maxc, Math.max(maxm, maxa)));
        return Math.max(maxcr, Math.max(maxc, maxm));
    }

    //TODO add a generic query to SleuthkitCase
    private String createMaxQuery(String attr) {
        return "SELECT MAX(" + attr + ") from tsk_files WHERE " + attr + " < " + System.currentTimeMillis() / 1000;
    }

    @SuppressWarnings("deprecation")
    private long runTimeQuery(String query) {
        long result = 0;
        ResultSet rs = null;
        try {
            rs = skCase.runQuery(query);
            result = rs.getLong(1);
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Couldn't get recent files results", ex);
        } finally {
            if (rs != null) {
                try {
                    skCase.closeRunQuery(rs);
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "Error closing result set after getting recent files results", ex);
                }
            }
        }
        return result;
    }
}
