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
import org.sleuthkit.autopsy.datamodel.DhsImageCategory;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.CategoryManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;

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

    static final Border HASH_BORDER = new Border(new BorderStroke(Color.PURPLE, BorderStrokeStyle.DASHED, CAT_CORNER_RADII, CAT_BORDER_WIDTHS));

    static final Border CAT1_BORDER = new Border(new BorderStroke(DhsImageCategory.ONE.getColor(), BorderStrokeStyle.SOLID, CAT_CORNER_RADII, CAT_BORDER_WIDTHS));

    static final Border CAT2_BORDER = new Border(new BorderStroke(DhsImageCategory.TWO.getColor(), BorderStrokeStyle.SOLID, CAT_CORNER_RADII, CAT_BORDER_WIDTHS));

    static final Border CAT3_BORDER = new Border(new BorderStroke(DhsImageCategory.THREE.getColor(), BorderStrokeStyle.SOLID, CAT_CORNER_RADII, CAT_BORDER_WIDTHS));

    static final Border CAT4_BORDER = new Border(new BorderStroke(DhsImageCategory.FOUR.getColor(), BorderStrokeStyle.SOLID, CAT_CORNER_RADII, CAT_BORDER_WIDTHS));

    static final Border CAT5_BORDER = new Border(new BorderStroke(DhsImageCategory.FIVE.getColor(), BorderStrokeStyle.SOLID, CAT_CORNER_RADII, CAT_BORDER_WIDTHS));

    static final Border CAT0_BORDER = new Border(new BorderStroke(DhsImageCategory.ZERO.getColor(), BorderStrokeStyle.SOLID, CAT_CORNER_RADII, CAT_BORDER_WIDTHS));

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

    static Border getCategoryBorder(DhsImageCategory category) {
        if (category != null) {
            switch (category) {
                case ONE:
                    return CAT1_BORDER;
                case TWO:
                    return CAT2_BORDER;
                case THREE:
                    return CAT3_BORDER;
                case FOUR:
                    return CAT4_BORDER;
                case FIVE:
                    return CAT5_BORDER;
                case ZERO:
                default:
                    return CAT0_BORDER;

            }
        } else {
            return CAT0_BORDER;
        }
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.ANY)
    default DhsImageCategory updateCategory() {
        if (getFile().isPresent()) {
            final DhsImageCategory category = getFile().map(DrawableFile::getCategory).orElse(DhsImageCategory.ZERO);
            final Border border = hasHashHit() && (category == DhsImageCategory.ZERO) ? HASH_BORDER : getCategoryBorder(category);
            Platform.runLater(() -> getCategoryBorderRegion().setBorder(border));
            return category;
        } else {
            return DhsImageCategory.ZERO;
        }
    }
}
