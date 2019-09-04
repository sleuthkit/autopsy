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

/**
 * Class to wrap all the information necessary for video thumbnails to be
 * displayed.
 */
final class VideoThumbnailsWrapper {

    private List<Image> thumbnails;
    private final ResultFile resultFile;
    private int[] timeStamps;

    /**
     * Construct a new VideoThumbnailsWrapper.
     *
     * @param thumbnails The list of Images which are the thumbnails for the
     *                   video.
     * @param timeStamps An array containing the time in milliseconds into the
     *                   video that each thumbnail created for.
     * @param file       The ResultFile which represents the video file which
     *                   the thumbnails were created for.
     */
    VideoThumbnailsWrapper(List<Image> thumbnails, int[] timeStamps, ResultFile file) {
        this.thumbnails = thumbnails;
        this.timeStamps = timeStamps;
        this.resultFile = file;
    }

    /**
     * Get the ResultFile which represents the video file which the thumbnails
     * were created for.
     *
     * @return The ResultFile which represents the video file which the
     *         thumbnails were created for.
     */
    ResultFile getResultFile() {
        return resultFile;
    }

    /**
     * Get the array containing thumbnail timestamps. Each timestamp is stored
     * as the number of milliseconds into the video each thumbnail was created
     * at.
     *
     * @return The array of timestamps in milliseconds from start of video.
     */
    int[] getTimeStamps() {
        return timeStamps.clone();
    }

    /**
     * Get the list of thumbnails for the video.
     *
     * @return The list of Images which are the thumbnails for the video.
     */
    List<Image> getThumbnails() {
        return Collections.unmodifiableList(thumbnails);
    }

    void setThumbnails(List<Image> videoThumbnails, int[] framePositions) {
        this.thumbnails = videoThumbnails;
        this.timeStamps = framePositions;
    }

}
