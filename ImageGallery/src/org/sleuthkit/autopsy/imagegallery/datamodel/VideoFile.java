/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Paths;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;

public class VideoFile<T extends AbstractFile> extends DrawableFile<T> {

    private static final Image VIDEO_ICON = new Image("org/sleuthkit/autopsy/imagegallery/images/Clapperboard.png");

    VideoFile(T file, Boolean analyzed) {
        super(file, analyzed);
    }

    @Override
    public Image getThumbnail() {
        //TODO: implement video thumbnailing here?
        return VIDEO_ICON;
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
            ContentUtils.writeToFile(this.getAbstractFile(), cacheFile);
        }
        try {
            media = new Media(Paths.get(cacheFile.getAbsolutePath()).toUri().toString());
            mediaRef = new SoftReference<>(media);
            return media;
        } catch (MediaException ex) {
            throw ex;
        }
    }


    private File getCacheFile(long id) {
        return new File(Case.getCurrentCase().getCacheDirectory() + File.separator + id);
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
