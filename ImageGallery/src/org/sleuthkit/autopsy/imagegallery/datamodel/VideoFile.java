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
import java.util.Objects;
import java.util.logging.Level;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
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
        //TODO: implement video thumbnailing here?
        return getGenericVideoThumbnail();
    }

    SoftReference<Media> mediaRef;

    public Media getMedia() throws IOException, MediaException {
        Media media = null;
        if (mediaRef != null) {
            media = mediaRef.get();
        }
        if (media != null) {
            return media;
        }
        final File cacheFile = getCacheFile(this.getId());
        if (cacheFile.exists() == false) {
            Files.createParentDirs(cacheFile);
            ContentUtils.writeToFile(this.getAbstractFile(), cacheFile);
        }

        media = new Media(Paths.get(cacheFile.getAbsolutePath()).toUri().toString());
        mediaRef = new SoftReference<>(media);
        return media;

    }

    private File getCacheFile(long id) {
        return Paths.get(Case.getCurrentCase().getCacheDirectory(), "videos", "" + id).toFile();
    }

    @Override
    public boolean isDisplayable() {
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
