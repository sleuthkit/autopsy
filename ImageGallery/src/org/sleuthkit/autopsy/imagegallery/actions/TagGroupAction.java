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
package org.sleuthkit.autopsy.imagegallery.actions;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javafx.scene.image.ImageView;
import org.controlsfx.control.action.Action;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.datamodel.TagName;

/**
 *
 */
public class TagGroupAction extends Action {

    public TagGroupAction(final TagName tagName, ImageGalleryController controller) {
        super(tagName.getDisplayName(), (javafx.event.ActionEvent actionEvent) -> {
            Set<Long> fileIdSet = ImmutableSet.copyOf(controller.viewState().get().getGroup().getFileIDs());
            new AddDrawableTagAction(controller).addTagsToFiles(tagName, "", fileIdSet);
        });
        setGraphic(new ImageView(DrawableAttribute.TAGS.getIcon()));
    }
}
