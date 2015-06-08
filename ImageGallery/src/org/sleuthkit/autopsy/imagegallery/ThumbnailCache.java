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
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javax.imageio.ImageIO;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.openide.util.Exceptions;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corelibs.ScalrWrapper;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.gui.Toolbar;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

/** Singleton to manage creation and access of icons. Keeps a cache in memory of
 * most recently used icons, and a disk cache of all icons.
 *
 * TODO: this was only a singleton for convenience, convert this to
 * non-singleton class -jm?
 */
public enum ThumbnailCache {

    instance;

    /** save thumbnails to disk as this format */
    private static final String FORMAT = "png";
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

    /** thread that saves generated thumbnails to disk for use later */
    private final Executor imageSaver = Executors.newSingleThreadExecutor(new BasicThreadFactory.Builder().namingPattern("icon saver-%d").build());

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
    public Image get(DrawableFile<?> file) {
        try {
            return cache.get(file.getId(), () -> load(file)).orElse(null);
        } catch (UncheckedExecutionException | CacheLoader.InvalidCacheLoadException | ExecutionException ex) {
            LOGGER.log(Level.WARNING, "failed to load icon for file: " + file.getName(), ex.getCause());
            return null;
        }
    }

    public Image get(Long fileID) {
        try {
            return get(ImageGalleryController.getDefault().getFileFromId(fileID));
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
    }

    /**
     * load a thumbnail from the disk based cache for the given file, or
     * generate and save a new thumnbail if one doesn't already exist
     *
     * @param file the file to load a thumbnail of
     *
     * @return an optional containing a thumbnail, or null if a thumbnail
     *         couldn't be loaded or generated
     */
    private Optional<Image> load(DrawableFile<?> file) {
        Image thumbnail = null;
        File cacheFile;
        try {// try to load the thumbnail from disk
            cacheFile = getCacheFile(file.getId());

            if (cacheFile.exists()) {
                // If a thumbnail file is already saved locally, load it
                try {
                    int dim = iconSize.get();
                    thumbnail = new Image(Utilities.toURI(cacheFile).toURL().toString(), dim, dim, true, false, true);
                } catch (MalformedURLException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        } catch (IllegalStateException e) {
            LOGGER.log(Level.WARNING, "can't load icon when no case is open");
            return Optional.empty();
        }

        if (thumbnail == null) {  //if we failed to load the icon, try to generate it
            thumbnail = generateAndSaveThumbnail(file);
        }

        return Optional.ofNullable(thumbnail); //return icon, or null if generation failed
    }

    private static File getCacheFile(long id) {
        // @@@ should use ImageUtils.getFile();
        return new File(Case.getCurrentCase().getCacheDirectory() + File.separator + id + ".png");
    }

    /**
     * generate a new thumbnail for the given file and save it to the disk cache
     *
     * @param file
     *
     * @return the newly generated thumbnail {@link Image}, or {@code null} if a
     *         thumbnail could not be generated
     */
    private Image generateAndSaveThumbnail(final DrawableFile<?> file) {
        //create a buffered input stream for the underlying Abstractfile
        try (InputStream inputStream = new BufferedInputStream(new ReadContentInputStream(file.getAbstractFile()))) {
            final Image thumbnail = new Image(inputStream, MAX_ICON_SIZE, MAX_ICON_SIZE, true, true);
            if (thumbnail.isError()) {  //if there was an error loading the image via JFX, fall back on Swing
                LOGGER.log(Level.WARNING, "problem loading thumbnail for image: " + file.getName() + " .");
                // Doing it this way puts the whole stack trace in the console output, which is probably not
                // needed. There are a significant number of cases where this is expected to fail (bitmaps,
                // empty files, etc.)
                //LOGGER.log(Level.WARNING, "problem loading image: " + file.getName() + " .", thumbnail.getException());
                return fallbackToSwingImage(file);
            } else { //if the load went successfully, save the thumbnail to disk on a background thread
                imageSaver.execute(() -> {
                    saveIcon(file, thumbnail);
                });
                return thumbnail;
            }
        } catch (IOException ex) {
            //if the JX load throws an exception fall back to Swing
            return fallbackToSwingImage(file);
        }
    }

    /**
     * use Swing to generate and save a thumbnail for the given file
     *
     * @param file
     *
     * @return a thumbnail generated for the given file, or {@code null} if a
     *         thumbnail could not be generated
     */
    private Image fallbackToSwingImage(final DrawableFile<?> file) {
        final BufferedImage generateSwingIcon = generateSwingThumbnail(file);
        if (generateSwingIcon == null) {    //if swing failed,
            return null;                    //propagate failure up cal stack.
        } else {//Swing load succeeded, convert to JFX Image
            final WritableImage toFXImage = SwingFXUtils.toFXImage(generateSwingIcon, null);
            if (toFXImage != null) { //if conversion succeeded save to disk cache
                imageSaver.execute(() -> {
                    saveIcon(file, toFXImage);
                });
            }
            return toFXImage;  //could be null
        }
    }

    /**
     * use Swing/ImageIO to generate a thumbnail for the given file
     *
     * @param file
     *
     * @return a BufferedImage thumbail for the given file, or {@code null} if a
     *         thumbnail could not be generated
     */
    private BufferedImage generateSwingThumbnail(DrawableFile<?> file) {
        //create a buffered input stream for the underlying Abstractfile
        try (InputStream inputStream = new BufferedInputStream(new ReadContentInputStream(file.getAbstractFile()))) {
            BufferedImage bi = ImageIO.read(inputStream);
            if (bi != null) {
                try { // resize (shrink) the buffered image if needed
                    if (Math.max(bi.getWidth(), bi.getHeight()) > MAX_ICON_SIZE) {
                        bi = ScalrWrapper.resizeFast(bi, iconSize.get());
                    }
                } catch (IllegalArgumentException e) {
                    //if scalr failed, just use unscaled image
                    LOGGER.log(Level.WARNING, "scalr could not scale image to 0: {0}", file.getName());
                } catch (OutOfMemoryError e) {
                    LOGGER.log(Level.WARNING, "scalr could not scale image (too large): {0}", file.getName());
                    return null;
                }
            } else { //ImageIO failed to read the image
                LOGGER.log(Level.WARNING, "No image reader for file: {0}", file.getName());
                return null;
            }
            return bi;
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Could not read image: " + file.getName());
            return null;
        }
    }

    /**
     * save the generated thumbnail to disk in the cache folder with
     * the obj_id as the name.
     *
     * @param file the file the given image is a thumbnail for
     * @param bi   the thumbnail to save for the given DrawableFile
     */
    private void saveIcon(final DrawableFile<?> file, final Image bi) {
        try {
            if (bi != null) {
                File f = getCacheFile(file.getId());
                //convert back to swing to save
                ImageIO.write(SwingFXUtils.fromFXImage(bi, null), FORMAT, f);
            }
        } catch (IllegalArgumentException | IOException ex) {
            //LOGGER.log(Level.WARNING, "failed to save generated icon ", ex);
            LOGGER.log(Level.WARNING, "failed to save generated icon");
        }
    }
}
