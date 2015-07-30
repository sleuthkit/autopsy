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
package org.sleuthkit.autopsy.imagegallery.gui;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.image.ImageView;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.actions.AddDrawableTagAction;
import org.sleuthkit.autopsy.imagegallery.actions.CategorizeAction;
import org.sleuthkit.autopsy.imagegallery.datamodel.Category;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.datamodel.TagName;

/**
 * Static utility methods for working with GUI components
 */
public class GuiUtils {

    /**
     * make a new menu item that when clicked, tags the selected files with the
     * given tagname
     *
     * @param tagName
     * @param tagSelectedMenuButton
     * @param controller
     *
     * @return
     */
    public static MenuItem createSelTagMenuItem(final TagName tagName, final SplitMenuButton tagSelectedMenuButton, ImageGalleryController controller) {
        final MenuItem menuItem = new MenuItem(tagName.getDisplayName(), new ImageView(DrawableAttribute.TAGS.getIcon()));
        menuItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent t) {
                new AddDrawableTagAction(controller).addTag(tagName, "");
                tagSelectedMenuButton.setText(tagName.getDisplayName());
                tagSelectedMenuButton.setOnAction(this);
            }
        });
        return menuItem;
    }

    public static MenuItem createSelCatMenuItem(Category cat, final SplitMenuButton catSelectedMenuButton, ImageGalleryController controller) {
        final MenuItem menuItem = new MenuItem(cat.getDisplayName(), new ImageView(DrawableAttribute.CATEGORY.getIcon()));
        menuItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent t) {
                new CategorizeAction(controller).addTag(controller.getTagsManager().getTagName(cat), "");
                catSelectedMenuButton.setText(cat.getDisplayName());
                catSelectedMenuButton.setOnAction(this);
            }
        });
        return menuItem;
    }

    private GuiUtils() {
    }

}
