/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import java.util.prefs.PreferenceChangeEvent;
import org.sleuthkit.autopsy.core.UserPreferences;
import static org.sleuthkit.autopsy.datamodel.KnownAndSlackFilterBase.filterKnown;
import static org.sleuthkit.autopsy.datamodel.KnownAndSlackFilterBase.filterSlack;
import org.sleuthkit.datamodel.Content;

/**
 * Known and Slack filter for Views section of the tree.
 *
 * @param <T>
 */
class ViewsKnownAndSlackFilter<T extends Content> extends KnownAndSlackFilterBase<T> {

    static {
        /**
         * Watch for user preference changes and update variables inherited from
         * our parent. The actual filtering is provided by our parent class.
         */
        UserPreferences.addChangeListener((PreferenceChangeEvent evt) -> {
            if (evt.getKey().equals(UserPreferences.HIDE_KNOWN_FILES_IN_VIEWS_TREE)) {
                filterKnown = UserPreferences.hideKnownFilesInViewsTree();
            } else if (evt.getKey().equals(UserPreferences.HIDE_SLACK_FILES_IN_VIEWS_TREE)) {
                filterSlack = UserPreferences.hideSlackFilesInViewsTree();
            }
        });
    }

    ViewsKnownAndSlackFilter() {
        filterKnown = UserPreferences.hideKnownFilesInViewsTree();
        filterSlack = UserPreferences.hideSlackFilesInViewsTree();
    }
}
