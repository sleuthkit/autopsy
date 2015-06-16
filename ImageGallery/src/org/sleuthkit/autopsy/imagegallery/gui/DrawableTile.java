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
package org.sleuthkit.autopsy.imagegallery.gui;

import java.util.Objects;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.CacheHint;
import javafx.scene.control.Control;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.coreutils.ThreadConfined.ThreadType;
import org.sleuthkit.autopsy.imagegallery.FXMLConstructor;
import static org.sleuthkit.autopsy.imagegallery.gui.DrawableViewBase.globalSelectionModel;

/**
 * GUI component that represents a single image as a tile with an icon, a label,
 * a color coded border and possibly other controls. Designed to be in a
 * {@link GroupPane}'s TilePane or SlideShow.
 *
 *
 * TODO: refactor this to extend from {@link Control}? -jm
 */
public class DrawableTile extends DrawableViewBase {

    private static final DropShadow LAST_SELECTED_EFFECT = new DropShadow(10, Color.BLUE);

    private static final Logger LOGGER = Logger.getLogger(DrawableTile.class.getName());

    /**
     * the central ImageView that shows a thumbnail of the represented file
     */
    @FXML
    private ImageView imageView;

    @Override
    protected void disposeContent() {
        //no-op
    }

    @FXML
    @Override
    protected void initialize() {
        super.initialize();
        assert imageBorder != null : "fx:id=\"imageAnchor\" was not injected: check your FXML file 'DrawableTile.fxml'.";
        assert imageView != null : "fx:id=\"imageView\" was not injected: check your FXML file 'DrawableTile.fxml'.";
        assert nameLabel != null : "fx:id=\"nameLabel\" was not injected: check your FXML file 'DrawableTile.fxml'.";

        //set up properties and binding
        setCache(true);
        setCacheHint(CacheHint.SPEED);
        nameLabel.prefWidthProperty().bind(imageView.fitWidthProperty());

        imageView.fitHeightProperty().bind(Toolbar.getDefault(getController()).sizeSliderValue());
        imageView.fitWidthProperty().bind(Toolbar.getDefault(getController()).sizeSliderValue());

        globalSelectionModel.lastSelectedProperty().addListener((observable, oldValue, newValue) -> {
            try {
                setEffect(Objects.equals(newValue, getFileID()) ? LAST_SELECTED_EFFECT : null);
            } catch (java.lang.IllegalStateException ex) {
                Logger.getLogger(DrawableTile.class.getName()).log(Level.WARNING, "Error displaying tile");
            }
        });
    }

    public DrawableTile(GroupPane gp) {
        super(gp);
        FXMLConstructor.construct(this, "DrawableTile.fxml");
    }

    @Override
    @ThreadConfined(type = ThreadType.JFX)
    protected void clearContent() {
        imageView.setImage(null);
    }

    /**
     * {@inheritDoc }
     */
    @Override
    protected void updateSelectionState() {
        super.updateSelectionState();
        final boolean lastSelected = Objects.equals(globalSelectionModel.lastSelectedProperty().get(), getFileID());
        Platform.runLater(() -> {
            setEffect(lastSelected ? LAST_SELECTED_EFFECT : null);
        });
    }

    @Override
    protected Runnable getContentUpdateRunnable() {
        Image image = getFile().getThumbnail();

        return () -> {
            imageView.setImage(image);
        };
    }

    @Override
    protected String getTextForLabel() {
        return getFile().getName();
    }
}
