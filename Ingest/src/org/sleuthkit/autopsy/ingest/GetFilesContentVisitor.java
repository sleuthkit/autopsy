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

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;
import org.sleuthkit.datamodel.LayoutFile;

/**
 * Abstract visitor for getting all the files from content
 * TODO should be moved to utility module (needs resolve cyclic deps)
 */
public abstract class GetFilesContentVisitor implements ContentVisitor<Collection<AbstractFile>> {

    private static final Logger logger = Logger.getLogger(GetFilesContentVisitor.class.getName());

    @Override
    public abstract Collection<AbstractFile> visit(File file);

    @Override
    public abstract Collection<AbstractFile> visit(FileSystem fs);

    @Override
    public Collection<AbstractFile> visit(Directory drctr) {
        return getAllFromChildren(drctr);
    }

    @Override
    public Collection<AbstractFile> visit(Image image) {
        return getAllFromChildren(image);
    }

    @Override
    public Collection<AbstractFile> visit(Volume volume) {
        return getAllFromChildren(volume);
    }

    @Override
    public Collection<AbstractFile> visit(VolumeSystem vs) {
        return getAllFromChildren(vs);
    }
    
    @Override
    public Collection<AbstractFile> visit(LayoutFile lc) {
        return null;
    }

    /**
     * Aggregate all the matches from visiting the children Content objects of the
     * one passed
     * @param parent
     * @return 
     */
    protected Collection<AbstractFile> getAllFromChildren(Content parent) {
        Collection<AbstractFile> all = new ArrayList<AbstractFile>();

        try {
            for (Content child : parent.getChildren()) {
                all.addAll(child.accept(this));
            }
        } catch (TskException ex) {
            logger.log(Level.SEVERE, "Error getting Content children", ex);
        }

        return all;
    }

    /**
     * Get the part of a file name after (not including) the last '.' and
     * coerced to lowercase.
     * @param fileName
     * @return the file extension, or an empty string if there is none
     */
    protected static String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf(".");

        if (lastDot >= 0) {
            return fileName.substring(lastDot + 1, fileName.length()).toLowerCase();
        } else {
            return "";
        }
    }
}
