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
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.FileKnown;

/**
 * Visitor for getting all the files to try to index from any Content object.
 * Currently gets all non-zero files. 
 * TODO should be moved to utility module (needs resolve cyclic deps)
 */
class GetAllFilesContentVisitor extends GetFilesContentVisitor {

    private static final Logger logger = Logger.getLogger(GetAllFilesContentVisitor.class.getName());

    @Override
    public Collection<FsContent> visit(File file) {
        return Collections.singleton((FsContent) file);
    }

    @Override
    public Collection<FsContent> visit(FileSystem fs) {
        // Files in the database have a filesystem field, so it's quick to
        // get all the matching files for an entire filesystem with a query

        SleuthkitCase sc = Case.getCurrentCase().getSleuthkitCase();

        String query = "SELECT * FROM tsk_files WHERE fs_obj_id = " + fs.getId()
                + " AND (meta_type = " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getMetaType()
                + ") AND (known != " + FileKnown.KNOWN.toLong() + ") AND (size > 0)";
        try {
            ResultSet rs = sc.runQuery(query);
            List<FsContent> contents = sc.resultSetToFsContents(rs);
            Statement s = rs.getStatement();
            rs.close();
            if (s != null) {
                s.close();
            }
            return contents;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Couldn't get all files in FileSystem", ex);
            return Collections.EMPTY_SET;
        }
    }
}
