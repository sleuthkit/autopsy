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

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Paths;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.VideoUtils;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;

public class VideoFile extends DrawableFile {

    private static final Logger LOGGER = Logger.getLogger(VideoFile.class.getName());

    private static final Image VIDEO_ICON = new Image("org/sleuthkit/autopsy/imagegallery/images/Clapperboard.png"); //NON-NLS

    VideoFile(AbstractFile file, Boolean analyzed) {
        super(file, analyzed);
    }

    public static Image getGenericVideoThumbnail() {
        return VIDEO_ICON;
    }

    

    @Override
    String getMessageTemplate(final Exception exception) {
        return "Failed to get image preview for video {0}: " + exception.toString(); //NON-NLS
    }

    @Override
    Task<Image> getReadFullSizeImageTaskHelper() {
        return ImageUtils.newGetThumbnailTask(getAbstractFile(), 1024, false);
    }

    private SoftReference<Media> mediaRef;

    @NbBundle.Messages({"VideoFile.getMedia.progress=writing temporary file to disk"})
    public Media getMedia() throws IOException, MediaException, NoCurrentCaseException {
        Media media = (mediaRef != null) ? mediaRef.get() : null;

        if (media != null) {
            return media;
        }
        final File cacheFile = VideoUtils.getTempVideoFile(this.getAbstractFile());

        if (cacheFile.exists() == false || cacheFile.length() < getAbstractFile().getSize()) {
            Files.createParentDirs(cacheFile);
            ProgressHandle progressHandle = ProgressHandle.createHandle(Bundle.VideoFile_getMedia_progress());
            progressHandle.start(100);
            ContentUtils.writeToFile(this.getAbstractFile(), cacheFile, progressHandle, null, true);
            progressHandle.finish();
        }

        media = new Media(Paths.get(cacheFile.getAbsolutePath()).toUri().toString());
        mediaRef = new SoftReference<>(media);
        return media;

    }

    @Override
    Double getWidth() {
        try {
            return (double) getMedia().getWidth();
        } catch (IOException | MediaException | NoCurrentCaseException ex) {
            return -1.0;
        }
    }

    @Override
    public boolean isVideo() {
        return true;
    }

    @Override
    Double getHeight() {
        try {
            return (double) getMedia().getHeight();
        } catch (IOException | MediaException | NoCurrentCaseException ex) {
            return -1.0;
        }
    }
}
