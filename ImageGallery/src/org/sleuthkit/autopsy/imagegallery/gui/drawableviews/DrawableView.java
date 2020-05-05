/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-18 Basis Technology Corp.
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

import com.google.common.eventbus.Subscribe;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.CategoryManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TagName.HTML_COLOR;

/**
 * Interface for classes that are views of a single DrawableFile. Implementation
 * of DrawableView must be registered with {@link CategoryManager#registerListener(java.lang.Object)
 * } to have there {@link DrawableView#handleCategoryChanged(org.sleuthkit.autopsy.imagegallery.datamodel.CategoryChangeEvent)
 * } method invoked
 */
public interface DrawableView {

    //TODO: do this all in css? -jm
    static final int CAT_BORDER_WIDTH = 10;

    static final BorderWidths CAT_BORDER_WIDTHS = new BorderWidths(CAT_BORDER_WIDTH);

    static final CornerRadii CAT_CORNER_RADII = new CornerRadii(3);

    Border HASH_BORDER = new Border(new BorderStroke(Color.CYAN, BorderStrokeStyle.DASHED, CAT_CORNER_RADII, CAT_BORDER_WIDTHS));

    Map<String, Border> BORDER_MAP = new HashMap<>();

    Region getCategoryBorderRegion();

    Optional<DrawableFile> getFile();

    void setFile(final Long fileID);

    Optional<Long> getFileID();

    /**
     * update the visual representation of the category of the assigned file.
     * Implementations of DrawableView } must register themselves with
     * CategoryManager.registerListener()} to have this method invoked
     *
     * @param evt the CategoryChangeEvent to handle
     */
    @Subscribe
    default void handleCategoryChanged(org.sleuthkit.autopsy.imagegallery.datamodel.CategoryManager.CategoryChangeEvent evt) {
        getFileID().ifPresent(fileID -> {
            if (evt.getFileIDs().contains(fileID)) {
                updateCategory();
            }
        });
    }

    @Subscribe
    void handleTagAdded(ContentTagAddedEvent evt);

    @Subscribe
    void handleTagDeleted(ContentTagDeletedEvent evt);

    ImageGalleryController getController();

    default boolean hasHashHit() {
        try {
            return getFile().map(DrawableFile::getHashSetNamesUnchecked)
                    .map((Collection<String> t) -> t.isEmpty() == false)
                    .orElse(false);

        } catch (NullPointerException ex) {
            // I think this happens when we're in the process of removing images from the view while
            // also trying to update it? 
            Logger.getLogger(DrawableView.class.getName()).log(Level.WARNING, "Error looking up hash set hits"); //NON-NLS
            return false;
        }

    }

    /**
     * Get the boarder for the given category.
     *
     * Static instances of the boarders will lazily constructed and stored in
     * the BORDER_MAP.
     *
     * @param category
     *
     * @return
     */
    static Border getCategoryBorder(TagName category) {
        Border border = null;
        if (category != null && category.getColor() != HTML_COLOR.NONE) {
            border = BORDER_MAP.get(category.getDisplayName());

            if (border == null) {
                border = new Border(new BorderStroke(Color.web(category.getColor().getRgbValue()), BorderStrokeStyle.SOLID, CAT_CORNER_RADII, CAT_BORDER_WIDTHS));
                BORDER_MAP.put(category.getDisplayName(), border);
            }
        }
        return border;
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.ANY)
    default TagName updateCategory() {
        if (getFile().isPresent()) {
            final TagName tagNameCat = getFile().map(DrawableFile::getCategory).orElse(null);
            final Border border = hasHashHit() ? HASH_BORDER : getCategoryBorder(tagNameCat);
            Platform.runLater(() -> getCategoryBorderRegion().setBorder(border));
            return tagNameCat;
        } else {
            return null;
        }
    }
}
