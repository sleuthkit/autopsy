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
package org.sleuthkit.autopsy.imagegallery.gui.drawableviews;

import java.io.IOException;
import java.lang.ref.SoftReference;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import java.util.Optional;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import static javafx.scene.input.KeyCode.LEFT;
import static javafx.scene.input.KeyCode.RIGHT;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.text.Text;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.coreutils.ThreadConfined.ThreadType;
import org.sleuthkit.autopsy.imagegallery.FXMLConstructor;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.Category;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.datamodel.VideoFile;
import org.sleuthkit.autopsy.imagegallery.gui.VideoPlayer;
import static org.sleuthkit.autopsy.imagegallery.gui.drawableviews.DrawableView.CAT_BORDER_WIDTH;

/**
 * Displays the files of a group one at a time. Designed to be embedded in a
 * GroupPane. TODO: Extract a subclass for video files in slideshow mode-jm
 *
 * TODO: reduce coupling to GroupPane
 */
public class SlideShowView extends DrawableTileBase {

    private static final Logger LOGGER = Logger.getLogger(SlideShowView.class.getName());

    @FXML
    private Button leftButton;
    @FXML
    private Button rightButton;

    @FXML
    private BorderPane footer;
    private Task<Node> mediaTask;

    SlideShowView(GroupPane gp, ImageGalleryController controller) {
        super(gp, controller);
        FXMLConstructor.construct(this, "SlideShowView.fxml");
    }

    @FXML
    @Override
    protected void initialize() {
        super.initialize();
        assert leftButton != null : "fx:id=\"leftButton\" was not injected: check your FXML file 'SlideShowView.fxml'.";
        assert rightButton != null : "fx:id=\"rightButton\" was not injected: check your FXML file 'SlideShowView.fxml'.";

        imageView.fitWidthProperty().bind(imageBorder.widthProperty().subtract(CAT_BORDER_WIDTH * 2));
        imageView.fitHeightProperty().bind(heightProperty().subtract(CAT_BORDER_WIDTH * 4).subtract(footer.heightProperty()));

        leftButton.setOnAction((ActionEvent t) -> {
            cycleSlideShowImage(-1);
        });
        rightButton.setOnAction((ActionEvent t) -> {
            cycleSlideShowImage(1);
        });

        //set up key listener equivalents of buttons
        addEventFilter(KeyEvent.KEY_PRESSED, (KeyEvent t) -> {
            if (t.getEventType() == KeyEvent.KEY_PRESSED) {
                switch (t.getCode()) {
                    case LEFT:
                        cycleSlideShowImage(-1);
                        t.consume();
                        break;
                    case RIGHT:
                        cycleSlideShowImage(1);
                        t.consume();
                        break;
                }
            }
        });

        syncButtonVisibility();

        getGroupPane().grouping().addListener((Observable observable) -> {
            syncButtonVisibility();
            if (getGroupPane().getGroup() != null) {
                getGroupPane().getGroup().fileIds().addListener((Observable observable1) -> {
                    syncButtonVisibility();
                });
            }
        });
    }

    @ThreadConfined(type = ThreadType.ANY)
    private void syncButtonVisibility() {
        try {
            final boolean hasMultipleFiles = getGroupPane().getGroup().fileIds().size() > 1;
            Platform.runLater(() -> {
                rightButton.setVisible(hasMultipleFiles);
                leftButton.setVisible(hasMultipleFiles);
                rightButton.setManaged(hasMultipleFiles);
                leftButton.setManaged(hasMultipleFiles);
            });
        } catch (NullPointerException ex) {
            // The case has likely been closed
            LOGGER.log(Level.WARNING, "Error accessing groupPane");
        }
    }

    @ThreadConfined(type = ThreadType.JFX)
    public void stopVideo() {
        if (imageBorder.getCenter() instanceof VideoPlayer) {
            ((VideoPlayer) imageBorder.getCenter()).stopVideo();
        }
    }

    /**
     * {@inheritDoc }
     */
    @Override
    synchronized public void setFile(final Long fileID) {
        super.setFile(fileID);
        getFileID().ifPresent(id -> getGroupPane().makeSelection(false, id));
        getFile().ifPresent(getGroupPane()::syncCatToggle);
    }

    @Override
    protected void disposeContent() {
        stopVideo();

        super.disposeContent();
        if (mediaTask != null) {
            mediaTask.cancel(true);
        }
        mediaTask = null;
        mediaCache = null;
    }
    private SoftReference<Node> mediaCache;

    /**
     * {@inheritDoc }
     */
    @Override
    Node getContentNode() {
        if (getFile().isPresent() == false) {
            mediaCache = null;
            return super.getContentNode();
        } else {
            DrawableFile<?> file = getFile().get();
            if (file.isVideo()) {
                Node mediaNode = (isNull(mediaCache)) ? null : mediaCache.get();
                if (nonNull(mediaNode)) {
                    return mediaNode;
                } else {
                    if (isNull(mediaTask)) {
                        mediaTask = new MediaLoadTask(((VideoFile<?>) file));
                        new Thread(mediaTask).start();
                    } else if (mediaTask.isDone()) {
                        return null;
                    }
                    return getLoadingProgressIndicator();
                }
            }
            return super.getContentNode();
        }
    }

    /**
     * {@inheritDoc }
     */
    @Override
    protected String getTextForLabel() {
        return getFile().map(file -> file.getName()).orElse("") + " " + getSupplementalText();
    }

    /**
     * cycle the image displayed in thes SlideShowview, to the next/previous one
     * in the group.
     *
     * @param direction the direction to cycle: -1 => left / back 1 => right /
     *                  forward
     */
    @ThreadConfined(type = ThreadType.JFX)
    synchronized private void cycleSlideShowImage(int direction) {
        stopVideo();
        final int groupSize = getGroupPane().getGroup().fileIds().size();
        final Integer nextIndex = getFileID().map(fileID -> {
            final int currentIndex = getGroupPane().getGroup().fileIds().indexOf(fileID);
            return (currentIndex + direction + groupSize) % groupSize;
        }).orElse(0);
        setFile(getGroupPane().getGroup().fileIds().get(nextIndex));

    }

    /**
     * @return supplemental text to include in the label, specifically: "image x
     *         of y"
     */
    private String getSupplementalText() {
        final ObservableList<Long> fileIds = getGroupPane().getGroup().fileIds();
        return getFileID().map(fileID -> " ( " + (fileIds.indexOf(fileID) + 1) + " of " + fileIds.size() + " in group )")
                .orElse("");

    }

    /**
     * {@inheritDoc }
     */
    @Override
    @ThreadConfined(type = ThreadType.ANY)
    public Category updateCategory() {
        Optional<DrawableFile<?>> file = getFile();
        if (file.isPresent()) {
            Category updateCategory = super.updateCategory();
            Platform.runLater(() -> getGroupPane().syncCatToggle(file.get()));
            return updateCategory;
        } else {
            return Category.ZERO;
        }
    }

    @Override
    CachedLoaderTask<Image, DrawableFile<?>> getNewImageLoadTask(DrawableFile<?> file) {

        return new ImageLoaderTask(file) {

            @Override
            Image load() {
                return isCancelled() ? null : file.getFullSizeImage();
            }
        };
    }

    private class MediaLoadTask extends CachedLoaderTask<Node, VideoFile<?>> {

        public MediaLoadTask(VideoFile<?> file) {
            super(file);
        }

        @Override
        void saveToCache(Node result) {
            synchronized (SlideShowView.this) {
                mediaCache = new SoftReference<>(result);
            }
        }

        @Override
        Node load() {
            try {
                final Media media = file.getMedia();
                return new VideoPlayer(new MediaPlayer(media), file);
            } catch (MediaException | IOException | OutOfMemoryError ex) {
                Logger.getLogger(VideoFile.class.getName()).log(Level.WARNING, "failed to initialize MediaControl for file " + file.getName(), ex);

                if (file.isDisplayableAsImage()) {
                    Image fullSizeImage = file.getFullSizeImage();
                    Platform.runLater(() -> {
                        imageView.setImage(fullSizeImage);
                    });
                    return imageView;
                }
                return new Text(ex.getLocalizedMessage() + "\nSee the logs for details.\n\nTry the \"Open In External Viewer\" action.");
            }
        }
    }
}
