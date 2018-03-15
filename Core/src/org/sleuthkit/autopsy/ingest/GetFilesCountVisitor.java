/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2014 Basis Technology Corp.
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

import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Get counts of ingestable files/dirs for the content input source.
 *
 * Note, also includes counts of all unalloc children files (for the fs, image,
 * volume) even if ingest didn't ask for them
 */
final class GetFilesCountVisitor extends ContentVisitor.Default<Long> {

    private static final Logger logger = Logger.getLogger(GetFilesCountVisitor.class.getName());

    @Override
    public Long visit(FileSystem fs) {
        //recursion stop here
        //case of a real fs, query all files for it
        SleuthkitCase sc;
        try {
            sc = Case.getOpenCase().getSleuthkitCase();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return 0L;
        }
        StringBuilder queryB = new StringBuilder();
        queryB.append("( (fs_obj_id = ").append(fs.getId()); //NON-NLS
        //queryB.append(") OR (fs_obj_id = NULL) )");
        queryB.append(") )");
        queryB.append(" AND ( (meta_type = ").append(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue()); //NON-NLS
        queryB.append(") OR (meta_type = ").append(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR.getValue()); //NON-NLS
        queryB.append(") OR (meta_type = ").append(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_VIRT_DIR.getValue()); //NON-NLS
        queryB.append(") OR (meta_type = ").append(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_VIRT.getValue()); //NON-NLS                
        queryB.append(" AND (name != '.') AND (name != '..')"); //NON-NLS
        queryB.append(") )");
        //queryB.append( "AND (type = ");
        //queryB.append(TskData.TSK_DB_FILES_TYPE_ENUM.FS.getFileType());
        //queryB.append(")");
        try {
            final String query = queryB.toString();
            return sc.countFilesWhere(query);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Couldn't get count of all files in FileSystem", ex); //NON-NLS
            return 0L;
        }
    }

    @Override
    public Long visit(LayoutFile lf) {
        //recursion stop here
        //case of LayoutFile child of Image or Volume
        return 1L;
    }

    private long getCountFromChildren(Content content) {
        long count = 0;
        try {
            List<Content> children = content.getChildren();
            if (children.size() > 0) {
                for (Content child : children) {
                    count += child.accept(this);
                }
            } else {
                count = 1;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Could not get count of objects from children to get num of total files to be ingested", ex); //NON-NLS
        }
        return count;
    }

    @Override
    protected Long defaultVisit(Content cntnt) {
        //recurse assuming this is image/vs/volume
        //recursion stops at fs or unalloc file
        return getCountFromChildren(cntnt);
    }
}
