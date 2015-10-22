/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.gui.drawableviews;

import java.lang.ref.SoftReference;
import java.util.Objects;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
abstract public class DrawableUIBase extends AnchorPane implements DrawableView {

    private static final Logger LOGGER = Logger.getLogger(DrawableUIBase.class.getName());

    @FXML
    protected BorderPane imageBorder;
    @FXML
    protected ImageView imageView;

    private final ImageGalleryController controller;

    private Optional<DrawableFile<?>> fileOpt = Optional.empty();

    private Optional<Long> fileIDOpt = Optional.empty();
    private Task<Image> imageTask;
    private SoftReference<Image> imageCache;
    private ProgressIndicator progressIndicator;

    public DrawableUIBase(ImageGalleryController controller) {
        this.controller = controller;
    }

    @Override
    public ImageGalleryController getController() {
        return controller;
    }

    @Override
    public Optional<Long> getFileID() {
        return fileIDOpt;
    }

    synchronized void setFileIDOpt(Optional<Long> fileIDOpt) {
        this.fileIDOpt = fileIDOpt;
    }

    synchronized void setFileOpt(Optional<DrawableFile<?>> fileOpt) {
        this.fileOpt = fileOpt;
    }

    @Override
    synchronized public Optional<DrawableFile<?>> getFile() {
        if (fileIDOpt.isPresent()) {
            if (fileOpt.isPresent() && fileOpt.get().getId() == fileIDOpt.get()) {
                return fileOpt;
            } else {
                try {
                    fileOpt = Optional.ofNullable(getController().getFileFromId(fileIDOpt.get()));
                } catch (TskCoreException ex) {
                    Logger.getAnonymousLogger().log(Level.WARNING, "failed to get DrawableFile for obj_id" + fileIDOpt.get(), ex);
                    fileOpt = Optional.empty();
                }
                return fileOpt;
            }
        } else {
            return Optional.empty();
        }
    }

    protected abstract void setFileHelper(Long newFileID);

    @Override
    synchronized public void setFile(Long newFileID) {
        if (getFileID().isPresent()) {
            if (Objects.equals(newFileID, getFileID().get()) == false) {
                if (Objects.nonNull(newFileID)) {
                    setFileHelper(newFileID);
                }
            }
        } else if (Objects.nonNull(newFileID)) {
            setFileHelper(newFileID);
        }
    }

    synchronized protected void updateContent() {
        Node content = getContentNode();
        Platform.runLater(() -> {
            imageBorder.setCenter(content);
        });
    }

    synchronized protected void disposeContent() {
        if (imageTask != null) {
            imageTask.cancel(true);
        }
        imageTask = null;
        imageCache = null;
    }

    ProgressIndicator getLoadingProgressIndicator() {
        if (progressIndicator == null) {
            progressIndicator = new ProgressIndicator();
        }
        return progressIndicator;
    }

    Node getContentNode() {
        if (getFile().isPresent() == false) {
            imageCache = null;
            Platform.runLater(() -> {
                if (imageView != null) {
                    imageView.setImage(null);
                }
            });
            return null;
        } else {
            Image thumbnail = isNull(imageCache) ? null : imageCache.get();

            if (nonNull(thumbnail)) {
                Platform.runLater(() -> {
                    if (imageView != null) {
                        imageView.setImage(thumbnail);
                    }
                });
                return imageView;
            } else {
                DrawableFile<?> file = getFile().get();

                if (isNull(imageTask)) {
                    imageTask = getNewImageLoadTask(file);
                    new Thread(imageTask).start();
                } else if (imageTask.isDone()) {
                    return null;
                }
                return getLoadingProgressIndicator();
            }
        }
    }

    abstract CachedLoaderTask<Image, DrawableFile<?>> getNewImageLoadTask(DrawableFile<?> file);

    abstract class CachedLoaderTask<X, Y extends DrawableFile<?>> extends Task<X> {

        protected final Y file;

        public CachedLoaderTask(Y file) {
            this.file = file;
        }

        @Override
        protected X call() throws Exception {
            return (isCancelled() == false) ? load() : null;
        }

        abstract X load();

        @Override
        protected void succeeded() {
            super.succeeded();
            if (isCancelled() == false) {
                try {
                    saveToCache(get());
                    updateContent();
                } catch (InterruptedException | ExecutionException ex) {
                    LOGGER.log(Level.WARNING, "Failed to cache content for" + file.getName(), ex);
                }
            }
        }

        @Override
        protected void failed() {
            super.failed();
            LOGGER.log(Level.SEVERE, "Failed to cache content for" + file.getName(), getException());
        }

        abstract void saveToCache(X result);
    }

    abstract class ImageLoaderTask extends CachedLoaderTask<Image, DrawableFile<?>> {

        public ImageLoaderTask(DrawableFile<?> file) {
            super(file);
        }

        @Override
        void saveToCache(Image result) {
            synchronized (DrawableUIBase.this) {
                imageCache = new SoftReference<>(result);
            }
        }
    }

    class ThumbnailLoaderTask extends ImageLoaderTask {

        public ThumbnailLoaderTask(DrawableFile<?> file) {
            super(file);
        }

        @Override
        Image load() {
            return isCancelled() ? null : file.getThumbnail();
        }
    }
}
