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

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.datamodel.TagName;

/**
 *
 */
final  class CategorizationChangeSet implements Command {

    private final TagName newCategory;
    final private Map<Long, TagName> oldCategories = new HashMap<>();

    public CategorizationChangeSet(TagName newCategory) {
        this.newCategory = newCategory;
    }

    public TagName getNewCategory() {
        return newCategory;
    }

    void add(long fileID, TagName old) {
        oldCategories.put(fileID, old);
    }

    Map<Long, TagName> getOldCategories() {
        return ImmutableMap.copyOf(oldCategories);
    }

    /**
     *
     * @param controller the value of controller
     */
    public void apply(final ImageGalleryController controller) {
        CategorizeAction categorizeAction = new CategorizeAction(controller);
        categorizeAction.addTagsToFiles(newCategory, "", this.oldCategories.keySet(), false);

    }

    /**
     *
     * @param controller the value of controller
     */
    public void undo(final ImageGalleryController controller) {
        CategorizeAction categorizeAction = new CategorizeAction(controller);
        for (Map.Entry<Long, TagName> entry : this.getOldCategories().entrySet()) {
            categorizeAction.addTagsToFiles(entry.getValue(), "", Collections.singleton(entry.getKey()), false);
        }
    }
}
