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
import java.util.ArrayList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.image.ImageView;
import static javafx.scene.input.KeyCode.LEFT;
import static javafx.scene.input.KeyCode.RIGHT;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.text.Text;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.coreutils.ThreadConfined.ThreadType;
import org.sleuthkit.autopsy.imagegallery.FXMLConstructor;
import org.sleuthkit.autopsy.imagegallery.FileIDSelectionModel;
import org.sleuthkit.autopsy.imagegallery.actions.CategorizeAction;
import org.sleuthkit.autopsy.imagegallery.datamodel.Category;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.datamodel.VideoFile;
import org.sleuthkit.autopsy.imagegallery.gui.GuiUtils;
import org.sleuthkit.autopsy.imagegallery.gui.VideoPlayer;
import static org.sleuthkit.autopsy.imagegallery.gui.drawableviews.DrawableView.CAT_BORDER_WIDTH;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Displays the files of a group one at a time. Designed to be embedded in a
 * GroupPane. TODO: Extract a subclass for video files in slideshow mode-jm
 *
 * TODO: reduce coupling to GroupPane
 */
public class SlideShowView extends DrawableTileBase {

    private static final Logger LOGGER = Logger.getLogger(SlideShowView.class.getName());

    @FXML
    private ToggleButton cat0Toggle;
    @FXML
    private ToggleButton cat1Toggle;
    @FXML
    private ToggleButton cat2Toggle;
    @FXML
    private ToggleButton cat3Toggle;
    @FXML
    private ToggleButton cat4Toggle;
    @FXML
    private ToggleButton cat5Toggle;

    @FXML
    private SplitMenuButton tagSplitButton;

    @FXML
    private Region spring;
    @FXML
    private Button leftButton;
    @FXML
    private Button rightButton;
    @FXML
    private ToolBar toolBar;
    @FXML
    private BorderPane footer;
    private Task<Node> mediaTask;

    SlideShowView(GroupPane gp) {
        super(gp);
        FXMLConstructor.construct(this, "SlideShow.fxml");
    }

    @FXML
    @Override
    protected void initialize() {
        super.initialize();
        assert cat0Toggle != null : "fx:id=\"cat0Toggle\" was not injected: check your FXML file 'SlideShow.fxml'.";
        assert cat1Toggle != null : "fx:id=\"cat1Toggle\" was not injected: check your FXML file 'SlideShow.fxml'.";
        assert cat2Toggle != null : "fx:id=\"cat2Toggle\" was not injected: check your FXML file 'SlideShow.fxml'.";
        assert cat3Toggle != null : "fx:id=\"cat3Toggle\" was not injected: check your FXML file 'SlideShow.fxml'.";
        assert cat4Toggle != null : "fx:id=\"cat4Toggle\" was not injected: check your FXML file 'SlideShow.fxml'.";
        assert cat5Toggle != null : "fx:id=\"cat5Toggle\" was not injected: check your FXML file 'SlideShow.fxml'.";
        assert leftButton != null : "fx:id=\"leftButton\" was not injected: check your FXML file 'SlideShow.fxml'.";
        assert rightButton != null : "fx:id=\"rightButton\" was not injected: check your FXML file 'SlideShow.fxml'.";
        assert tagSplitButton != null : "fx:id=\"tagSplitButton\" was not injected: check your FXML file 'SlideShow.fxml'.";

        Platform.runLater(() -> {
            HBox.setHgrow(spring, Priority.ALWAYS);
            spring.setMinWidth(Region.USE_PREF_SIZE);
        });

        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.fitWidthProperty().bind(imageBorder.widthProperty().subtract(CAT_BORDER_WIDTH * 2));
        imageView.fitHeightProperty().bind(heightProperty().subtract(CAT_BORDER_WIDTH * 4).subtract(footer.heightProperty()).subtract(toolBar.heightProperty()));

        tagSplitButton.setOnAction((ActionEvent t) -> {
            try {
                GuiUtils.createSelTagMenuItem(getController().getTagsManager().getFollowUpTagName(), tagSplitButton, getController()).getOnAction().handle(t);
            } catch (TskCoreException ex) {
                Exceptions.printStackTrace(ex);
            }
        });

        tagSplitButton.setGraphic(new ImageView(DrawableAttribute.TAGS.getIcon()));
        tagSplitButton.showingProperty().addListener((ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) -> {
            if (t1) {
                ArrayList<MenuItem> selTagMenues = new ArrayList<>();
                for (final TagName tn : getController().getTagsManager().getNonCategoryTagNames()) {
                    MenuItem menuItem = GuiUtils.createSelTagMenuItem(tn, tagSplitButton, getController());
                    selTagMenues.add(menuItem);
                }
                tagSplitButton.getItems().setAll(selTagMenues);
            }
        });

        //configure category toggles
        cat0Toggle.setBorder(new Border(new BorderStroke(Category.ZERO.getColor(), BorderStrokeStyle.SOLID, new CornerRadii(1), new BorderWidths(1))));
        cat1Toggle.setBorder(new Border(new BorderStroke(Category.ONE.getColor(), BorderStrokeStyle.SOLID, new CornerRadii(1), new BorderWidths(1))));
        cat2Toggle.setBorder(new Border(new BorderStroke(Category.TWO.getColor(), BorderStrokeStyle.SOLID, new CornerRadii(1), new BorderWidths(1))));
        cat3Toggle.setBorder(new Border(new BorderStroke(Category.THREE.getColor(), BorderStrokeStyle.SOLID, new CornerRadii(1), new BorderWidths(1))));
        cat4Toggle.setBorder(new Border(new BorderStroke(Category.FOUR.getColor(), BorderStrokeStyle.SOLID, new CornerRadii(1), new BorderWidths(1))));
        cat5Toggle.setBorder(new Border(new BorderStroke(Category.FIVE.getColor(), BorderStrokeStyle.SOLID, new CornerRadii(1), new BorderWidths(1))));

        cat0Toggle.selectedProperty().addListener(new CategorizeToggleHandler(Category.ZERO));
        cat1Toggle.selectedProperty().addListener(new CategorizeToggleHandler(Category.ONE));
        cat2Toggle.selectedProperty().addListener(new CategorizeToggleHandler(Category.TWO));
        cat3Toggle.selectedProperty().addListener(new CategorizeToggleHandler(Category.THREE));
        cat4Toggle.selectedProperty().addListener(new CategorizeToggleHandler(Category.FOUR));
        cat5Toggle.selectedProperty().addListener(new CategorizeToggleHandler(Category.FIVE));

        cat0Toggle.toggleGroupProperty().addListener((o, oldGroup, newGroup) -> {
            newGroup.selectedToggleProperty().addListener((ov, oldToggle, newToggle) -> {
                if (newToggle == null) {
                    oldToggle.setSelected(true);
                }
            });
        });

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

    /** {@inheritDoc } */
    @Override
    synchronized public void setFile(final Long fileID) {
        super.setFile(fileID);

        getFileID().ifPresent((Long id) -> {
            getGroupPane().makeSelection(false, id);
        });
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

    /** {@inheritDoc } */
    @Override
    Node getContentNode() {
        if (getFile().isPresent() == false) {
            imageCache = null;
            mediaCache = null;
            Platform.runLater(() -> {
                imageView.setImage(null);
            });
            return null;
        } else {
            DrawableFile<?> file = getFile().get();
            if ((file.isVideo() && file.isDisplayable()) == false) {
                return super.getContentNode();
            } else {
                Node media = (isNull(mediaCache)) ? null : mediaCache.get();
                if (nonNull(media)) {
                    return media;
                } else {
                    if (isNull(mediaTask) || mediaTask.isDone()) {
                        mediaTask = new MediaLoadTask(((VideoFile<?>) file)) {
                            @Override
                            void saveToCache(Node result) {
                                synchronized (SlideShowView.this) {
                                    mediaCache = new SoftReference<>(result);
                                }
                            }
                        };

                        new Thread(mediaTask).start();
                    }
                    return getLoadingProgressIndicator();
                }
            }
        }
    }

    /** {@inheritDoc } */
    @Override
    protected String getTextForLabel() {
        return getFile().map(file -> file.getName()).orElse("") + " " + getSupplementalText();
    }

    /**
     * cycle the image displayed in thes SlideShowview, to the next/previous one
     * in the group.
     *
     * @param direction the direction to cycle:
     *                  -1 => left / back
     *                  1 => right / forward
     */
    @ThreadConfined(type = ThreadType.JFX)
    synchronized private void cycleSlideShowImage(int direction) {
        stopVideo();
        final int groupSize = getGroupPane().getGroup().fileIds().size();
        final Integer nextIndex = getFileID().map(fileID -> {
            final int currentIndex = getGroupPane().getGroup().fileIds().indexOf(fileID);
            return (currentIndex + direction + groupSize) % groupSize;
        }).orElse(0);
        setFile(getGroupPane().getGroup().fileIds().get(nextIndex)
        );
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

    /** {@inheritDoc } */
    @Override
    @ThreadConfined(type = ThreadType.ANY)
    public Category updateCategory() {
        if (getFile().isPresent()) {
            final Category category = super.updateCategory();
            ToggleButton toggleForCategory = getToggleForCategory(category);
            Platform.runLater(() -> {
                toggleForCategory.setSelected(true);
            });
            return category;
        } else {
            return Category.ZERO;
        }
    }

    private ToggleButton getToggleForCategory(Category category) {
        switch (category) {
            case ZERO:
                return cat0Toggle;
            case ONE:
                return cat1Toggle;
            case TWO:
                return cat2Toggle;
            case THREE:
                return cat3Toggle;
            case FOUR:
                return cat4Toggle;
            case FIVE:
                return cat5Toggle;
            default:
                throw new IllegalArgumentException(category.name());
        }
    }

    private class CategorizeToggleHandler implements ChangeListener<Boolean> {

        private final Category cat;

        public CategorizeToggleHandler(Category cat) {
            this.cat = cat;
        }

        @Override
        public void changed(ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) {
            getFileID().ifPresent(fileID -> {
                if (t1) {
                    FileIDSelectionModel.getInstance().clearAndSelect(fileID);
                    new CategorizeAction(getController()).addTag(getController().getTagsManager().getTagName(cat), "");
                }
            });
        }
    }

    abstract private class MediaLoadTask extends CachedLoaderTask<Node, VideoFile<?>> {

        public MediaLoadTask(VideoFile<?> file) {
            super(file);
        }

        @Override
        Node load() {
            try {
                final Media media = file.getMedia();
                return new VideoPlayer(new MediaPlayer(media), file);
            } catch (IOException ex) {
                Logger.getLogger(VideoFile.class.getName()).log(Level.WARNING, "failed to initialize MediaControl for file " + file.getName(), ex);
                return new Text(ex.getLocalizedMessage() + "\nSee the logs for details.\n\nTry the \"Open In External Viewer\" action.");
            } catch (MediaException ex) {
                Logger.getLogger(VideoFile.class.getName()).log(Level.WARNING, ex.getType() + " Failed to initialize MediaControl for file " + file.getName(), ex);
                return new Text(ex.getType() + "\nSee the logs for details.\n\nTry the \"Open In External Viewer\" action.");
            } catch (OutOfMemoryError ex) {
                Logger.getLogger(VideoFile.class.getName()).log(Level.WARNING, "failed to initialize MediaControl for file " + file.getName(), ex);
                return new Text("There was a problem playing video file.\nSee the logs for details.\n\nTry the \"Open In External Viewer\" action.");
            }
        }
    }
}
