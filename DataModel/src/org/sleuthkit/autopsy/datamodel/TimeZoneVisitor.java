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

import java.util.TimeZone;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 *
 * @author dfickling
 */
class TimeZoneVisitor implements ContentVisitor<TimeZone> {

    @Override
    public TimeZone visit(Directory drctr) {
        return visit(drctr.getFileSystem());
    }

    @Override
    public TimeZone visit(File file) {
        return visit(file.getFileSystem());
    }

    @Override
    public TimeZone visit(FileSystem fs) {
        return fs.getParent().accept(this);
    }

    @Override
    public TimeZone visit(Image image) {
        return TimeZone.getTimeZone(image.getTimeZone());
    }

    @Override
    public TimeZone visit(Volume volume) {
        return visit(volume.getParent());
    }

    @Override
    public TimeZone visit(VolumeSystem vs) {
        return TimeZone.getTimeZone(vs.getParent().getTimeZone());
    }

    @Override
    public TimeZone visit(LayoutFile lc) {
        return lc.getParent().accept(this);
    }
}
