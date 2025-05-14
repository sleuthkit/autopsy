/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.tags;

import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TagSet;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 *
 * Utility methods for Tags.
 */
public final class TagUtils {

    private static final Logger logger = Logger.getLogger(TagUtils.class.getName());

    private TagUtils() {
        // Intentionally empty constructor;
    }

    /**
     * Returns a decorated name for the TagName that includes, if available, the
     * TagName's TagSet name and notability.
     *
     * If an exception is throw while trying to retrieve the TagName's TagSet
     * the TagSet name will not be included.
     *
     * @param tagName The TagName value generate name for.
     *
     * @return The decorated name for the TagName or the TagName display name
     *         value if an exception occurred.
     */
    public static String getDecoratedTagDisplayName(TagName tagName) {
        String displayName = tagName.getDisplayName();
        try {
            TagsManager tagsManager = Case.getCurrentCaseThrows().getServices().getTagsManager();
            TagSet tagSet = tagsManager.getTagSet(tagName);
            if (tagSet != null) {
                displayName = tagSet.getName() + ": " + displayName;
            }
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Failed to get TagSet for TagName '%s' (ID=%d)", tagName.getDisplayName(), tagName.getId()));            
        }

        if (tagName.getTagType() == TskData.TagType.BAD) {
            displayName += " (Notable)";
        }

        return displayName;
    }
}
