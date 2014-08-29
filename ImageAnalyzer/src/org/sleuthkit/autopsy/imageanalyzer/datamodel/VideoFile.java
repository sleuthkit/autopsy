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
package org.sleuthkit.autopsy.imageanalyzer.datamodel;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Paths;
import java.util.logging.Level;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.text.Text;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.imageanalyzer.gui.MediaControl;
import org.sleuthkit.datamodel.AbstractFile;

public class VideoFile<T extends AbstractFile> extends DrawableFile<T> {

    private static final Image VIDEO_ICON = new Image("org/sleuthkit/autopsy/imageanalyzer/images/Clapperboard.png");

    VideoFile(T file, Boolean analyzed) {
        super(file, analyzed);
    }

    @Override
    public Image getIcon() {
        //TODO: implement video thumbnailing here
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

    @Override
    public Node getFullsizeDisplayNode() {
        final File cacheFile = getCacheFile(this.getId());

        try {
            if (cacheFile.exists() == false) {
                ContentUtils.writeToFile(this.getAbstractFile(), cacheFile);
            }
            final Media media = getMedia();
            final MediaPlayer mediaPlayer = new MediaPlayer(media);
            final MediaControl mediaView = new MediaControl(mediaPlayer);
            mediaPlayer.statusProperty().addListener((observableStatus, oldStatus, newStatus) -> {
                Logger.getAnonymousLogger().log(Level.INFO, "media player: {0}", newStatus);
            });
            return mediaView;
        } catch (IOException ex) {
            Logger.getLogger(VideoFile.class.getName()).log(Level.SEVERE, "failed to initialize MediaControl for file " + getName(), ex);
            return new Text(ex.getLocalizedMessage() + "\nSee the logs for details.");
        } catch (MediaException ex) {
            Logger.getLogger(VideoFile.class.getName()).log(Level.SEVERE, ex.getType() + " Failed to initialize MediaControl for file " + getName(), ex);
            Logger.getLogger(VideoFile.class.getName()).log(Level.SEVERE, "caused by " + ex.getCause().getLocalizedMessage(), ex.getCause());
            return new Text(ex.getType() + "\nSee the logs for details.");
        } catch (OutOfMemoryError ex) {
            Logger.getLogger(VideoFile.class.getName()).log(Level.SEVERE, "failed to initialize MediaControl for file " + getName(), ex);
            return new Text("There was a problem playing video file.\nSee the logs for details.");
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
//            Exceptions.printStackTrace(ex);
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
//            Exceptions.printStackTrace(ex);
            return -1.0;
        }
    }
}
