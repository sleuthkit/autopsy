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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.RecentFiles.RecentFilesFilter;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 *
 * @author dfickling
 */
public class RecentFilesFilterChildren extends ChildFactory<Content>{
    
    SleuthkitCase skCase;
    RecentFilesFilter filter;
    private final static Logger logger = Logger.getLogger(RecentFilesFilterChildren.class.getName());

    RecentFilesFilterChildren(RecentFilesFilter filter, SleuthkitCase skCase) {
        this.skCase = skCase;
        this.filter = filter;
    }

    @Override
    protected boolean createKeys(List<Content> list) {
        list.addAll(runFsQuery());
        return true;
    }
    
    private String createQuery(){
        String query = "select * from tsk_files where ";
        long latestUpdate = getLastTime();
        long threshold = latestUpdate-filter.getDuration();
        query += "(crtime between " + threshold + " and " + latestUpdate + ") or ";
        query += "(ctime between " + threshold + " and " + latestUpdate + ") or ";
        query += "(atime between " + threshold + " and " + latestUpdate + ") or ";
        query += "(mtime between " + threshold + " and " + latestUpdate + ")";
        return query;
    }
    
    private List<FsContent> runFsQuery(){
        List<FsContent> list = new ArrayList<FsContent>();
        try {
            ResultSet rs = skCase.runQuery(createQuery());
            for(FsContent c : skCase.resultSetToFsContents(rs)){
                if(!c.getName().equals(".") && !c.getName().equals("..")){
                    list.add(c);
                }
            }
            Statement s = rs.getStatement();
            rs.close();
            if (s != null)
                s.close();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Couldn't get search results", ex);
        }
        return list;
        
    }
    
    @Override
    protected Node createNodeForKey(Content key){
        return key.accept(new ContentVisitor.Default<AbstractNode>(){
            
            @Override
            public FileNode visit(File f){
                return new FileNode(f);
            }
            
            @Override
            public DirectoryNode visit(Directory d) {
                return new DirectoryNode(d);
            }

            @Override
            protected AbstractNode defaultVisit(Content di) {
                throw new UnsupportedOperationException("Not supported for this type of Displayable Item: " + di.toString());
            }
            
        });
    }

    private long getLastTime() {
        String query = createMaxQuery("crtime");
        long maxcr = runTimeQuery(query);
        query = createMaxQuery("ctime");
        long maxc = runTimeQuery(query);
        query = createMaxQuery("mtime");
        long maxm = runTimeQuery(query);
        query = createMaxQuery("atime");
        long maxa = runTimeQuery(query);
        return Math.max(maxcr, Math.max(maxc, Math.max(maxm, maxa)));
    }
    
    private String createMaxQuery(String attr){
        return "select max(" + attr + ") from tsk_files where " + attr + " < " + System.currentTimeMillis()/1000;
    }
    
    private long runTimeQuery(String query) {
        long result = 0;
        try {
            ResultSet rs = skCase.runQuery(query);
            result = rs.getLong(1);
            Statement s = rs.getStatement();
            rs.close();
            if (s != null)
                s.close();
        } catch (SQLException ex) {
            Logger.getLogger(RecentFilesFilterChildren.class.getName())
                    .log(Level.INFO, "Couldn't get search results", ex);
        }
        return result;
    }
}
