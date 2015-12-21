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

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javafx.beans.Observable;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import javax.imageio.ImageIO;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ThumbnailCache;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * ImageGallery data model object that represents an image file. It is a
 * wrapper(/decorator?/adapter?) around {@link AbstractFile} and provides
 * methods to get an thumbnail sized and a full sized {@link  Image}.
 */
public class ImageFile<T extends AbstractFile> extends DrawableFile<T> {

    private static final Logger LOGGER = Logger.getLogger(ImageFile.class.getName());

    static {
        ImageIO.scanForPlugins();
    }

    ImageFile(T f, Boolean analyzed) {
        super(f, analyzed);

    }

    @Override
    public Task<Image> getThumbnailTask() {
        return ThumbnailCache.getDefault().getThumbnailTask(this);
//            newGetThumbTask.stateProperty().addListener((Observable observable) -> {
//                switch (newGetThumbTask.getState()) {
//                    case CANCELLED:
//                        break;
//                    case FAILED:
//                        break;
//                    case SUCCEEDED:
//                        try {
//                            thumbref = new SoftReference<>(newGetThumbTask.get());
//                        } catch (InterruptedException | ExecutionException interruptedException) {
//                        }
//                        break;
//                }
//            });
//            return newGetThumbTask;
//        } else {
//            return new Task<Image>() {
//                @Override
//                protected Image call() throws Exception {
//                    return thumbnail;
//                }
//            };
//        }
    }

    @Override
    public Task<Image> getReadFullSizeImageTask() {
        Image image = (imageRef != null) ? imageRef.get() : null;
        if (image == null || image.isError()) {
            final Task<Image> newReadImageTask = ImageUtils.newReadImageTask(this.getAbstractFile());
            newReadImageTask.stateProperty().addListener((Observable observable) -> {
                switch (newReadImageTask.getState()) {
                    case CANCELLED:
                        break;
                    case FAILED:
                        break;
                    case SUCCEEDED:
                        try {
                            imageRef = new SoftReference<>(newReadImageTask.get());
                        } catch (InterruptedException | ExecutionException interruptedException) {
                        }
                        break;
                }
            });
            return newReadImageTask;
        } else {
            return new Task<Image>() {
                @Override
                protected Image call() throws Exception {
                    return image;
                }
            };
        }
    }

    @Override
    Double getWidth() {
        try {
            return (double) ImageUtils.getWidth(this.getAbstractFile());
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "getWidth", ex);
            return -1.0;
        }
    }

    @Override
    Double getHeight() {
        try {
            return (double) ImageUtils.getHeight(this.getAbstractFile());
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "getHeight", ex);
            return -1.0;
        }
    }

    @Override
    public boolean isVideo() {
        return false;
    }
}
