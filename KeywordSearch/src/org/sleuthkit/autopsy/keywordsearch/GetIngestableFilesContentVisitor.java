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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData.FileKnown;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

class GetIngestableFilesContentVisitor implements ContentVisitor<Collection<FsContent>> {

    private static Logger logger = Logger.getLogger(GetIngestableFilesContentVisitor.class.getName());
    // supported extensions list from http://www.lucidimagination.com/devzone/technical-articles/content-extraction-tika
    private static final String[] supportedExtensions = {"tar", "jar", "zip", "bzip2",
        "gz", "tgz", "doc", "xls", "ppt", "rtf", "pdf", "html", "xhtml", "txt",
        "bmp", "gif", "png", "jpeg", "tiff", "mp3", "aiff", "au", "midi", "wav",
        "pst", "xml", "class"};
    // the full predicate of a SQLite statement to match supported extensions
    private static final String extensionsLikePredicate;

    static {
        StringBuilder likes = new StringBuilder("0");

        for (String ext : supportedExtensions) {
            likes.append(" OR (name LIKE '%.");
            likes.append(ext);
            likes.append("')");
        }

        extensionsLikePredicate = likes.toString();
    }

    @Override
    public Collection<FsContent> visit(Directory drctr) {
        return getAllFromChildren(drctr);
    }

    private String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf(".");

        if (lastDot >= 0) {
            return fileName.substring(lastDot + 1, fileName.length()).toLowerCase();
        } else {
            return "";
        }
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
        SleuthkitCase sc = Case.getCurrentCase().getSleuthkitCase();

        String query = "SELECT * FROM tsk_files WHERE fs_obj_id = " + fs.getId()
                + " AND (" + extensionsLikePredicate + ")"
                + " AND (known != " + FileKnown.KNOWN.toLong() + ")";
        try {
            ResultSet rs = sc.runQuery(query);
            return sc.resultSetToFsContents(rs);
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Couldn't get all files in FileSystem", ex);
            return Collections.EMPTY_SET;
        }
    }

    @Override
    public Collection<FsContent> visit(Image image) {
        return getAllFromChildren(image);
    }

    @Override
    public Collection<FsContent> visit(Volume volume) {
        return getAllFromChildren(volume);
    }

    @Override
    public Collection<FsContent> visit(VolumeSystem vs) {
        return getAllFromChildren(vs);
    }

    private Collection<FsContent> getAllFromChildren(Content c) {
        Collection<FsContent> all = new ArrayList<FsContent>();

        try {
            for (Content child : c.getChildren()) {
                all.addAll(child.accept(this));
            }
        } catch (TskException ex) {
            logger.log(Level.SEVERE, "Error getting Content children", ex);
        }

        return all;
    }
}
