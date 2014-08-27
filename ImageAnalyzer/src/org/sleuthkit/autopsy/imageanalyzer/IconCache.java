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
package org.sleuthkit.autopsy.imageanalyzer;

import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableFile;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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
import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corelibs.ScalrWrapper;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

/** Manages creation and access of icons. Keeps a cache in memory of most
 * recently used icons, and a disk cache of all icons. */
public class IconCache {

    static private IconCache instance;

    private static final int MAX_ICON_SIZE = 300;

    private static final Logger LOGGER = Logger.getLogger(IconCache.class.getName());

    private static final Cache<Long, Optional<Image>> cache = CacheBuilder.newBuilder().maximumSize(1000).softValues().expireAfterAccess(10, TimeUnit.MINUTES).build();

    public SimpleIntegerProperty iconSize = new SimpleIntegerProperty(200);

    private final Executor imageSaver = Executors.newSingleThreadExecutor(new BasicThreadFactory.Builder().namingPattern("icon saver-%d").build());

    private IconCache() {

    }

    synchronized static public IconCache getDefault() {
        if (instance == null) {
            instance = new IconCache();
        }
        return instance;
    }

    public Image get(DrawableFile file) {
        try {
            return cache.get(file.getId(), () -> load(file)).orElse(null);
        } catch (CacheLoader.InvalidCacheLoadException | ExecutionException ex) {
            LOGGER.log(Level.WARNING, "failed to load icon for file: " + file.getName(), ex);
            return null;
        }
    }

    public Image get(Long fileID) {
        try {
            return get(ImageAnalyzerController.getDefault().getFileFromId(fileID));
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
    }

    public Optional<Image> load(DrawableFile file) throws IIOException {

        Image icon = null;
        File cacheFile;
        try {
            cacheFile = getCacheFile(file.getId());
        } catch (IllegalStateException e) {
            LOGGER.log(Level.WARNING, "can't load icon when no case is open");
            return Optional.empty();
        }

        // If a thumbnail file is already saved locally
        if (cacheFile.exists()) {
            try {
                int dim = iconSize.get();
                icon = new Image(cacheFile.toURI().toURL().toString(), dim, dim, true, false, true);
            } catch (MalformedURLException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        if (icon == null) {
            //  Logger.getAnonymousLogger().warning("wrong size cache found for image " + getName());
            icon = generateAndSaveIcon(file);
        }
        return Optional.ofNullable(icon);

    }

    private static File getCacheFile(long id) {
        return new File(Case.getCurrentCase().getCacheDirectory() + File.separator + id + ".png");
    }

    private Image generateAndSaveIcon(final DrawableFile file) {
        Image img;
        //TODO: should we wrap this in a BufferedInputStream? -jm
        try (ReadContentInputStream inputStream = new ReadContentInputStream(file.getAbstractFile())) {
            img = new Image(inputStream, MAX_ICON_SIZE, MAX_ICON_SIZE, true, true);
            if (img.isError()) {
                LOGGER.log(Level.WARNING, "problem loading image: {0}. {1}", new Object[]{file.getName(), img.getException().getLocalizedMessage()});
                return fallbackToSwingImage(file);
            } else {
                imageSaver.execute(() -> {
                    saveIcon(file, img);
                });
            }
        } catch (IOException ex) {
            return fallbackToSwingImage(file);
        }

        return img;

    }
    /* Generate a scaled image */

    private Image fallbackToSwingImage(final DrawableFile file) {
        final BufferedImage generateSwingIcon = generateSwingIcon(file);
        if (generateSwingIcon != null) {
            WritableImage toFXImage = SwingFXUtils.toFXImage(generateSwingIcon, null);
            if (toFXImage != null) {
                imageSaver.execute(() -> {
                    saveIcon(file, toFXImage);
                });
            }

            return toFXImage;
        } else {
            return null;
        }
    }

    private BufferedImage generateSwingIcon(DrawableFile file) {
        try (ReadContentInputStream inputStream = new ReadContentInputStream(file.getAbstractFile())) {
            BufferedImage bi = ImageIO.read(inputStream);
            if (bi == null) {
                LOGGER.log(Level.WARNING, "No image reader for file: {0}", file.getName());
                return null;
            } else {
                try {
                    if (Math.max(bi.getWidth(), bi.getHeight()) > MAX_ICON_SIZE) {
                        bi = ScalrWrapper.resizeFast(bi, iconSize.get());
                    }
                } catch (IllegalArgumentException e) {
                    LOGGER.log(Level.WARNING, "scalr could not scale image to 0: {0}", file.getName());
                } catch (OutOfMemoryError e) {
                    LOGGER.log(Level.WARNING, "scalr could not scale image (too large): {0}", file.getName());
                    return null;
                }
            }
            return bi;
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Could not read image: " + file.getName(), ex);
            return null;
        }
    }

    private void saveIcon(final DrawableFile file, final Image bi) {
        try {
            /* save the icon in a background thread. profiling
             * showed that it can take as much time as making
             * the icon? -bc
             *
             * We don't do this now as it doesn't fit the
             * current model of ui-related backgroiund tasks,
             * and there might be complications to not just
             * blocking (eg having more than one task to
             * create the same icon -jm */
            File f = getCacheFile(file.getId());
            ImageIO.write(SwingFXUtils.fromFXImage(bi, null), "png", f);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "failed to save generated icon ", ex);
        }
    }
}
