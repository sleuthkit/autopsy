/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-15 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.datamodel;

import java.io.IOException;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * ImageGallery data model object that represents an image file. It is a
 * wrapper(/decorator?/adapter?) around {@link AbstractFile} and provides
 * methods to get an thumbnail sized and a full sized {@link  Image}.
 */
public class ImageFile extends DrawableFile {

    private static final Logger LOGGER = Logger.getLogger(ImageFile.class.getName());

    ImageFile(AbstractFile f, Boolean analyzed) {
        super(f, analyzed);
    }

    @Override
    String getMessageTemplate(final Exception exception) {
        return "Failed to read image {0}: " + exception.toString(); //NON-NLS
    }

    @Override
    Task<Image> getReadFullSizeImageTaskHelper() {
        return ImageUtils.newReadImageTask(this.getAbstractFile());
    }

    @Override
    Double getWidth() {
        try {
            return (double) ImageUtils.getImageWidth(this.getAbstractFile());
        } catch (IOException ex) {
            return -1.0;
        }
    }

    @Override
    Double getHeight() {
        try {
            return (double) ImageUtils.getImageHeight(this.getAbstractFile());
        } catch (IOException ex) {
            return -1.0;
        }
    }

    @Override
    public boolean isVideo() {
        return false;
    }
}
