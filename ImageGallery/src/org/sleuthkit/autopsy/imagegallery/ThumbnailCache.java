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
package org.sleuthkit.autopsy.imagegallery;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import org.openide.util.Exceptions;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.gui.Toolbar;
import org.sleuthkit.datamodel.TskCoreException;

/** Singleton to manage creation and access of icons. Keeps a cache in memory of
 * most recently used icons, and a disk cache of all icons.
 *
 * TODO: this was only a singleton for convenience, convert this to
 * non-singleton class -jm?
 */
public enum ThumbnailCache {

    instance;

    private static final int MAX_ICON_SIZE = 300;

    private static final Logger LOGGER = Logger.getLogger(ThumbnailCache.class.getName());

    /** in memory cache. keeps at most 1000 items each for up to 10 minutes.
     * items may be garbage collected if there are no strong references to them.
     */
    private final Cache<Long, Optional<Image>> cache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .softValues()
            .expireAfterAccess(10, TimeUnit.MINUTES).build();

    public static ThumbnailCache getDefault() {
        return instance;
    }

    /** currently desired icon size. is bound in {@link Toolbar} */
    public final SimpleIntegerProperty iconSize = new SimpleIntegerProperty(200);

    /**
     * Clear out the cache between cases
     */
    public final void clearCache() {
        cache.invalidateAll();
    }

    /** get the cached thumbnail for the given file or generate a new one if
     * needed
     *
     * @param file
     *
     * @return a thumbnail for the given file, returns null if the thumbnail
     *         could not be generated
     */
    @Nullable
    public Image get(DrawableFile<?> file) {
        try {
            return cache.get(file.getId(), () -> ImageUtils.getIcon(file.getAbstractFile(), MAX_ICON_SIZE)).orElse(null);
        } catch (UncheckedExecutionException | CacheLoader.InvalidCacheLoadException | ExecutionException ex) {
            LOGGER.log(Level.WARNING, "failed to load icon for file: " + file.getName(), ex.getCause());
            return null;
        }
    }

    @Nullable
    public Image get(Long fileID) {
        try {
            return get(ImageGalleryController.getDefault().getFileFromId(fileID));
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "failed to load icon for file id : " + fileID, ex.getCause());
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
    private Optional<Image> load(DrawableFile<?> file) {

        Image thumbnail;

        try {
            thumbnail = getCacheFile(file).map(new Function<File, Image>() {
                @Override
                public Image apply(File cachFile) {
                    if (cachFile.exists()) {
                        // If a thumbnail file is already saved locally, load it
                        try {
                            BufferedImage read = ImageIO.read(cachFile);
                            if (read.getWidth() < MAX_ICON_SIZE) {
                                read = ImageUtils.getIcon(file.getAbstractFile(), MAX_ICON_SIZE);
                            }

                            return new Image(Utilities.toURI(cachFile).toURL().toString(), MAX_ICON_SIZE, MAX_ICON_SIZE, true, false, true);
                        } catch (MalformedURLException ex) {
                            LOGGER.log(Level.WARNING, "Unable to parse cache file path..");
                        } catch (IOException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                    return null;
                }
            }).orElse(null);

        } catch (IllegalStateException e) {
            LOGGER.log(Level.WARNING, "can't load icon when no case is open");
            return Optional.empty();
        }

        if (thumbnail == null) {  //if we failed to load the icon, try to generate it
            thumbnail = SwingFXUtils.toFXImage((BufferedImage) ImageUtils.getIcon(file.getAbstractFile(), MAX_ICON_SIZE), null);
        }

        return Optional.ofNullable(thumbnail); //return icon, or null if generation failed
    }

    /**
     * get a File to store the cached icon in.
     *
     * @param id the obj id of the file to get a cache file for
     *
     * @return a Optional containing a File to store the cahced icon in or an
     *         empty optional if there was a
     *         problem.
     */
    private static Optional<File> getCacheFile(DrawableFile<?> file) {
        try {
            return Optional.of(ImageUtils.getIconFile(file.getAbstractFile(), MAX_ICON_SIZE));
        } catch (IllegalStateException e) {
            LOGGER.log(Level.WARNING, "Failed to create cache file.{0}", e.getLocalizedMessage());
            return Optional.empty();
        }
    }
}
