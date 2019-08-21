/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.filequery;

import java.awt.Image;
import java.util.Collections;
import java.util.List;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author wschaefer
 */
public class ThumbnailsWrapper {

    private final List<Image> thumbnails;
    private final AbstractFile abstractFile;
    private final int[] timeStamps;

    public ThumbnailsWrapper(List<Image> thumbnails, int[] timeStamps, AbstractFile file) {
        this.thumbnails = thumbnails;
        this.timeStamps = timeStamps;
        this.abstractFile = file;
    }

    AbstractFile getAbstractFile() {
        return abstractFile;
    }

    int[] getTimeStamps() {
        return timeStamps.clone();
    }

    String getFileInfo() {
        try {
            return abstractFile.getUniquePath();
        } catch (TskCoreException ingored) {
            return abstractFile.getParentPath() + "/" + abstractFile.getName();
        }
    }

    List<Image> getThumbnails() {
        return Collections.unmodifiableList(thumbnails);
    }

}
