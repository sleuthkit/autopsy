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
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.logging.Level;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.VideoUtils;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.imagegallery.ThumbnailCache;
import org.sleuthkit.datamodel.AbstractFile;

public class VideoFile<T extends AbstractFile> extends DrawableFile<T> {

    private static final Image VIDEO_ICON = new Image("org/sleuthkit/autopsy/imagegallery/images/Clapperboard.png");

    VideoFile(T file, Boolean analyzed) {
        super(file, analyzed);
    }

    public static Image getGenericVideoThumbnail() {
        return VIDEO_ICON;
    }

    @Override
    public Image getThumbnail() {
        return ThumbnailCache.getDefault().get(this);
    }

    @Override
    public Image getFullSizeImage() {
        Image image = (imageRef != null) ? imageRef.get() : null;

        if (image == null) {
            final BufferedImage bufferedImage = (BufferedImage) ImageUtils.getThumbnail(getAbstractFile(), 1024);
            imageRef = new SoftReference<>((bufferedImage == ImageUtils.getDefaultThumbnail()) ? null : SwingFXUtils.toFXImage(bufferedImage, null));
        }

        return image;

    }

    private SoftReference<Media> mediaRef;

    public Media getMedia() throws IOException, MediaException {
        Media media = (mediaRef != null) ? mediaRef.get() : null;

        if (media != null) {
            return media;
        }
        final File cacheFile = VideoUtils.getTempVideoFile(this.getAbstractFile());

        if (cacheFile.exists() == false || cacheFile.length() < getAbstractFile().getSize()) {

            Files.createParentDirs(cacheFile);
            ProgressHandle progressHandle = ProgressHandleFactory.createHandle("writing temporary file to disk");
            progressHandle.start(100);
            ContentUtils.writeToFile(this.getAbstractFile(), cacheFile, progressHandle, null, true);
            progressHandle.finish();

        }

        media = new Media(Paths.get(cacheFile.getAbsolutePath()).toUri().toString());
        mediaRef = new SoftReference<>(media);
        return media;

    }

    public boolean isDisplayableAsMedia() {
        try {
            Media media = getMedia();
            return Objects.nonNull(media) && Objects.isNull(media.getError());
        } catch (IOException ex) {
            Logger.getLogger(VideoFile.class.getName()).log(Level.SEVERE, "failed to write video to cache for playback.", ex);
            return false;
        } catch (MediaException ex) {
            return false;
        }
    }

    @Override
    Double getWidth() {
        try {
            return (double) getMedia().getWidth();
        } catch (IOException | MediaException ex) {
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
        } catch (IOException | MediaException ex) {
            return -1.0;
        }
    }
}
