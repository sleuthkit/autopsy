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
package org.sleuthkit.autopsy.keywordsearch;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
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
import org.sleuthkit.datamodel.TskData.FileKnown;
import org.sleuthkit.datamodel.TskData;

/**
 * Visitor for getting all the files to try to index from any Content object.
 * Currently gets all the non-zero sized files with a file extensions that match a list of
 * document types that Tika/Solr-Cell supports.
 */
class GetIngestableFilesContentVisitor extends GetFilesContentVisitor {

    private static final Logger logger = Logger.getLogger(GetIngestableFilesContentVisitor.class.getName());
    // TODO: use a more robust method than checking file extension to determine
    // whether to try a file
    // supported extensions list from http://www.lucidimagination.com/devzone/technical-articles/content-extraction-tika
    private static final String[] supportedExtensions = {"tar", "jar", "zip", "bzip2",
        "gz", "tgz", "doc", "xls", "ppt", "rtf", "pdf", "html", "xhtml", "txt",
        "bmp", "gif", "png", "jpeg", "tiff", "mp3", "aiff", "au", "midi", "wav",
        "pst", "xml", "class"};
    // the full predicate of a SQLite statement to match supported extensions
    private static final String extensionsLikePredicate;

    static {
        // build the query fragment for matching file extensions

        StringBuilder likes = new StringBuilder("0");

        for (String ext : supportedExtensions) {
            likes.append(" OR (name LIKE '%.");
            likes.append(ext);
            likes.append("')");
        }

        extensionsLikePredicate = likes.toString();
    }

    @Override
    public Collection<FsContent> visit(File file) {
        String extension = getExtension(file.getName());
        if (Arrays.asList(supportedExtensions).contains(extension)) {
            return Collections.singleton((FsContent) file);
        } else {
            return Collections.EMPTY_LIST;
        }
    }

    @Override
    public Collection<FsContent> visit(FileSystem fs) {
        // Files in the database have a filesystem field, so it's quick to
        // get all the matching files for an entire filesystem with a query

        SleuthkitCase sc = Case.getCurrentCase().getSleuthkitCase();

        String query = "SELECT * FROM tsk_files WHERE fs_obj_id = " + fs.getId()
                + " AND (" + extensionsLikePredicate + ")"
                + " AND (known != " + FileKnown.KNOWN.toLong() + ")"
                + " AND (meta_type = " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getMetaType() + ")"
                + " AND (size > 0)";
        try {
            ResultSet rs = sc.runQuery(query);
            List<FsContent> contents = sc.resultSetToFsContents(rs);
            final Statement s = rs.getStatement();
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
