/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-16 Basis Technology Corp.
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

import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.CacheHint;
import javafx.scene.control.Control;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.FXMLConstructor;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;

/**
 * GUI component that represents a single image as a tile with an icon, a label,
 * a color coded border and possibly other controls. Designed to be in a
 * {@link GroupPane}'s TilePane or SlideShow.
 *
 *
 * TODO: refactor this to extend from {@link Control}? -jm
 */
public class DrawableTile extends DrawableTileBase {

    private static final DropShadow LAST_SELECTED_EFFECT = new DropShadow(10, Color.BLUE);

    private static final Logger LOGGER = Logger.getLogger(DrawableTile.class.getName());
    private final ChangeListener<? super Long> lastSelectionListener = (observable, oldValue, newValue) -> {
        updateSelectionState();
    };

    @FXML
    @Override
    protected void initialize() {
        super.initialize();
        assert imageView != null : "fx:id=\"imageView\" was not injected: check your FXML file 'DrawableTile.fxml'.";

        //set up properties and binding
        setCache(true);
        setCacheHint(CacheHint.SPEED);
        nameLabel.prefWidthProperty().bind(imageView.fitWidthProperty());
        imageView.fitHeightProperty().bind(getController().thumbnailSizeProperty());
        imageView.fitWidthProperty().bind(getController().thumbnailSizeProperty());

        selectionModel.lastSelectedProperty().addListener(new WeakChangeListener<>(lastSelectionListener));

        //set up mouse listener
        addEventHandler(MouseEvent.MOUSE_CLICKED, clickEvent -> {
            if (clickEvent.getButton() == MouseButton.PRIMARY) {
                getFile().ifPresent(file -> {
                    final long fileID = file.getId();
                    if (clickEvent.isControlDown()) {
                        selectionModel.toggleSelection(fileID);
                    } else {
                        getGroupPane().makeSelection(clickEvent.isShiftDown(), fileID);
                    }
                    if (clickEvent.getClickCount() > 1) {
                        getGroupPane().activateSlideShowViewer(fileID);
                    }
                });
                clickEvent.consume();
            }
        });
    }

    public DrawableTile(GroupPane gp, ImageGalleryController controller) {
        super(gp, controller);
        FXMLConstructor.construct(this, "DrawableTile.fxml"); //NON-NLS
    }

    /**
     * {@inheritDoc }
     */
    @Override
    protected void updateSelectionState() {
        super.updateSelectionState();
        getFileID().ifPresent(fileID -> {
            final boolean lastSelected = Objects.equals(selectionModel.lastSelectedProperty().get(), fileID);
            Platform.runLater(() -> setEffect(lastSelected ? LAST_SELECTED_EFFECT : null));
        });
    }

    @Override
    Task<Image> newReadImageTask(DrawableFile file) {
        return getController().getThumbsCache().getThumbnailTask(file);
    }

    @Override
    protected String getTextForLabel() {
        return getFile().map(DrawableFile::getName).orElse("");
    }
}
