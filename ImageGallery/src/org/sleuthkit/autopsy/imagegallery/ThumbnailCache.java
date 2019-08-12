/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-18 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javafx.beans.Observable;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.utils.TaskUtils;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Singleton to manage creation and access of icons. Keeps a cache in memory of
 * most recently used icons, and a disk cache of all icons.
 *
 * TODO: this was only a singleton for convenience, convert this to
 * non-singleton class -jm?
 */
public class ThumbnailCache {

    private final ImageGalleryController controller;

    public ThumbnailCache(ImageGalleryController controller) {
        this.controller = controller;
    }

    private static final int MAX_THUMBNAIL_SIZE = 300;

    private static final Logger LOGGER = Logger.getLogger(ThumbnailCache.class.getName());

    /**
     * in memory cache. keeps at most 1000 items each for up to 10 minutes.
     * items may be garbage collected if there are no strong references to them.
     */
    private final Cache<Long, Image> cache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .softValues()
            .expireAfterAccess(10, TimeUnit.MINUTES).build();

    /**
     * currently desired icon size. is bound in {@link Toolbar}
     */
    public final SimpleIntegerProperty iconSize = new SimpleIntegerProperty(200);

    /**
     * Clear out the cache between cases
     */
    public final void clearCache() {
        cache.invalidateAll();
    }

    /**
     * get the cached thumbnail for the given file or generate a new one if
     * needed
     *
     * @param file
     *
     * @return a thumbnail for the given file, returns null if the thumbnail
     *         could not be generated
     */
    @Nullable
    public Image get(DrawableFile file) {
        try {
            return cache.get(file.getId(), () -> load(file));
        } catch (UncheckedExecutionException | CacheLoader.InvalidCacheLoadException | ExecutionException ex) {
            LOGGER.log(Level.WARNING, "Failed to load thumbnail for file: " + file.getName(), ex.getCause()); //NON-NLS
            return null;
        }
    }

    @Nullable
    public Image get(Long fileID) {
        try {
            return get(controller.getFileFromID(fileID));
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "Failed to load thumbnail for file: " + fileID, ex.getCause()); //NON-NLS
            return null;
        }
    }

    /**
     * load a thumbnail from the disk based cache for the given file, or
     * generate and save a new thumbnail if one doesn't already exist
     *
     * @param file the DrawableFile to load a thumbnail of
     *
     * @return an (possibly empty) optional containing a thumbnail
     */
    private Image load(DrawableFile file) {

        if (ImageUtils.isGIF(file.getAbstractFile())) {
            //directly read gif to preserve potential animation,
            //NOTE: not saved to disk!
            return new Image(new BufferedInputStream(new ReadContentInputStream(file.getAbstractFile())), MAX_THUMBNAIL_SIZE, MAX_THUMBNAIL_SIZE, true, true);
        }

        BufferedImage thumbnail = getCacheFile(file).map(cachFile -> {
            if (cachFile.exists()) {
                // If a thumbnail file is already saved locally, load it
                try {
                    BufferedImage cachedThumbnail = ImageIO.read(cachFile);

                    if (cachedThumbnail.getWidth() < MAX_THUMBNAIL_SIZE) {
                        return cachedThumbnail;
                    }
                } catch (MalformedURLException ex) {
                    LOGGER.log(Level.WARNING, "Unable to parse cache file path: " + cachFile.getPath(), ex); //NON-NLS
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "Unable to read cache file " + cachFile.getPath(), ex); //NON-NLS
                }
            }
            return null;
        }).orElseGet(() -> {
            return ImageUtils.getThumbnail(file.getAbstractFile(), MAX_THUMBNAIL_SIZE);
        });

        WritableImage jfxthumbnail;
        if (thumbnail == ImageUtils.getDefaultThumbnail()) {
            // if we go the default icon, ignore it
            jfxthumbnail = null;
        } else {
            jfxthumbnail = SwingFXUtils.toFXImage(thumbnail, null);
        }

        return jfxthumbnail; //return icon, or null if generation failed
    }

    /**
     * get a File to store the cached icon in.
     *
     * @param id the obj id of the file to get a cache file for
     *
     * @return a Optional containing a File to store the cached icon in or an
     *         empty optional if there was a problem.
     */
    private static Optional<File> getCacheFile(DrawableFile file) {
        try {
            return Optional.of(ImageUtils.getCachedThumbnailFile(file.getAbstractFile(), MAX_THUMBNAIL_SIZE));

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to create cache file.{0}", e.getLocalizedMessage()); //NON-NLS
            return Optional.empty();
        }
    }

    public Task<Image> getThumbnailTask(DrawableFile file) {
        final Image thumbnail = cache.getIfPresent(file.getId());
        if (thumbnail != null) {
            return TaskUtils.taskFrom(() -> thumbnail);
        }
        final Task<Image> newGetThumbnailTask = ImageUtils.newGetThumbnailTask(file.getAbstractFile(), MAX_THUMBNAIL_SIZE, false);
        newGetThumbnailTask.stateProperty().addListener((Observable observable) -> {
            switch (newGetThumbnailTask.getState()) {
                case SUCCEEDED:
                    try {
                        cache.put(Long.MIN_VALUE, newGetThumbnailTask.get());
                    } catch (InterruptedException | ExecutionException ex) {
                        LOGGER.log(Level.SEVERE, "There was an exception even though thumbnail task succedded for.  This should not be possible.", ex); //NON-NLS
                    }
            }
        });
        return newGetThumbnailTask;
    }
}
