/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractContent;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.LocalDirectory;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.Report;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 * Abstract visitor for getting all the files from content.
 */
abstract class GetFilesContentVisitor implements ContentVisitor<Collection<AbstractFile>> {

    private static final Logger logger = Logger.getLogger(GetFilesContentVisitor.class.getName());

    @Override
    public Collection<AbstractFile> visit(VirtualDirectory ld) {
        return getAllFromChildren(ld);
    }
    
    @Override
    public Collection<AbstractFile> visit(LocalDirectory ld) {
        return getAllFromChildren(ld);
    }

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
    public Collection<AbstractFile> visit(Pool pool) {
        return getAllFromChildren(pool);
    }

    @Override
    public Collection<AbstractFile> visit(Report r) {
        return getAllFromChildren(r);
    }

    /**
     * Aggregate all the matches from visiting the children Content objects of a
     * parent Content object.
     *
     * @param parent A content object.
     *
     * @return The child files of the content.
     */
    protected Collection<AbstractFile> getAllFromChildren(Content parent) {
        Collection<AbstractFile> all = new ArrayList<>();

        try {
            for (Content child : parent.getChildren()) {
                if (child instanceof AbstractContent){
                    all.addAll(child.accept(this));
                }
            }
        } catch (TskException ex) {
            logger.log(Level.SEVERE, "Error getting Content children", ex); //NON-NLS
        }

        return all;
    }
}
