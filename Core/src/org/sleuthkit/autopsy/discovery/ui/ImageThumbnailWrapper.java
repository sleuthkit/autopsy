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
package org.sleuthkit.autopsy.discovery.ui;

import java.awt.Image;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.discovery.search.ResultFile;

/**
 * Class to wrap all the information necessary for an image thumbnail to be
 * displayed.
 */
final class ImageThumbnailWrapper {

    private Image thumbnail;
    private final ResultFile resultFile;

    /**
     * Construct a new ImageThumbnailsWrapper.
     *
     * @param file The ResultFile which represents the image file which the
     *             thumbnails were created for.
     */
    ImageThumbnailWrapper(ResultFile file) {
        this.thumbnail = ImageUtils.getDefaultThumbnail();
        this.resultFile = file;
    }

    /**
     * Set the image thumbnail which exists.
     *
     * @param thumbnail The thumbnail which exists for this file.
     */
    void setImageThumbnail(Image thumbnail) {
        this.thumbnail = thumbnail;
    }

    /**
     * Get the ResultFile which represents the image file which the thumbnail
     * was created for.
     *
     * @return The ResultFile which represents the image file which the
     *         thumbnail was created for.
     */
    ResultFile getResultFile() {
        return resultFile;
    }

    /**
     * Get the thumbnail for the image.
     *
     * @return The Image which is the thumbnail for the image.
     */
    Image getThumbnail() {
        return thumbnail;
    }
}
