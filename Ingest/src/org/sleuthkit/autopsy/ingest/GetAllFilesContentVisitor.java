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
package org.sleuthkit.autopsy.ingest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.LayoutDirectory;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData;

/**
 * Visitor for getting all the files/unalloc files / dirs to ingest
 */
class GetAllFilesContentVisitor extends GetFilesContentVisitor {

    private static final Logger logger = Logger.getLogger(GetAllFilesContentVisitor.class.getName());
    private boolean getUnallocatedFiles;
    
    GetAllFilesContentVisitor(boolean getUnallocatedFiles) {
        this.getUnallocatedFiles = getUnallocatedFiles;
    }

    @Override
    public Collection<AbstractFile> visit(File file) {
        return Collections.<AbstractFile>singleton(file);
    }
    
    @Override
    public Collection<AbstractFile> visit(Directory drctr) {
        return Collections.<AbstractFile>singleton(drctr);
    }
    
    @Override
    public Collection<AbstractFile> visit(LayoutFile lf) {
        return Collections.<AbstractFile>singleton(lf);
    }
    
    @Override
    public Collection<AbstractFile> visit(LayoutDirectory ld) {
        return Collections.<AbstractFile>singleton(ld);
    }

    @Override
    public Collection<AbstractFile> visit(FileSystem fs) {
        // Files in the database have a filesystem field, so it's quick to
        // get all the matching files for an entire filesystem with a query

        SleuthkitCase sc = Case.getCurrentCase().getSleuthkitCase();

        StringBuilder queryB = new StringBuilder();
        queryB.append("SELECT * FROM tsk_files WHERE ( (fs_obj_id = ").append(fs.getId());
        queryB.append(") OR (fs_obj_id = NULL) )");
        queryB.append(" AND ( (meta_type = ").append(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getMetaType());
        queryB.append(") OR (meta_type = ").append(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR.getMetaType());
        queryB.append( " AND (name != '.') AND (name != '..')");
        queryB.append(") )");
        if (getUnallocatedFiles == false) {
            queryB.append( "AND (type = ");
            queryB.append(TskData.TSK_DB_FILES_TYPE_ENUM.FS.getFileType());
            queryB.append(")");
        }
        
        try {
            final String query = queryB.toString();
            logger.log(Level.INFO, "Executing query: " + query);
            ResultSet rs = sc.runQuery(query);
            List<AbstractFile> contents = sc.resultSetToAbstractFiles(rs);
            Statement s = rs.getStatement();
            rs.close();
            if (s != null) {
                s.close();
            }
            return contents;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Couldn't get all files in FileSystem", ex);
            return Collections.emptySet();
        }
    }
}
