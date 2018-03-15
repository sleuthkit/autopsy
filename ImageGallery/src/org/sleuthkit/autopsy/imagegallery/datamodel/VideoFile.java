/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2018 Basis Technology Corp.
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
import java.util.logging.Level;
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
import org.sleuthkit.datamodel.ReadContentInputStream.ReadContentInputStreamException;

public class VideoFile extends DrawableFile {

    private static final Logger logger = Logger.getLogger(VideoFile.class.getName());

    private static final Image videoIcon = new Image("org/sleuthkit/autopsy/imagegallery/images/Clapperboard.png"); //NON-NLS

    /**
     * Instantiate a VideoFile object.
     *
     * @param file     The file on which to base the object.
     * @param analyzed
     */
    VideoFile(AbstractFile file, Boolean analyzed) {
        super(file, analyzed);
    }

    /**
     * Get the genereric video thumbnail.
     *
     * @return The thumbnail.
     */
    public static Image getGenericVideoThumbnail() {
        return videoIcon;
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

    /**
     * Get the media associated with the VideoFile.
     *
     * @return The media.
     *
     * @throws IOException
     * @throws MediaException
     * @throws NoCurrentCaseException
     */
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
        double width = -1.0;
        try {
            width = getMedia().getWidth();
        } catch (ReadContentInputStreamException ex) {
            logger.log(Level.WARNING, "Error reading video file", ex); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error writing video file to disk", ex); //NON-NLS
        } catch (MediaException ex) {
            logger.log(Level.SEVERE, "Error creating media from source file", ex); //NON-NLS
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "The current case has been closed", ex); //NON-NLS
        }
        return width;
    }

    @Override
    public boolean isVideo() {
        return true;
    }

    @Override
    Double getHeight() {
        double height = -1.0;
        try {
            height = getMedia().getHeight();
        } catch (ReadContentInputStreamException ex) {
            logger.log(Level.WARNING, "Error reading video file.", ex); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error writing video file to disk.", ex); //NON-NLS
        } catch (MediaException ex) {
            logger.log(Level.SEVERE, "Error creating media from source file.", ex); //NON-NLS
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "The current case has been closed", ex); //NON-NLS
        }
        return height;
    }
}
