/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.zooming;

import org.openide.util.NbBundle;

/**
 * Enum of event type zoom levels
 *
 */
@NbBundle.Messages({
    "EventTypeZoomLevel.rootType=Root Type",
    "EventTypeZoomLevel.baseType=Base Type",
    "EventTypeZoomLevel.subType=Sub Type"})
public enum EventTypeZoomLevel implements DisplayNameProvider {

    ROOT_TYPE(Bundle.EventTypeZoomLevel_rootType()),
    BASE_TYPE(Bundle.EventTypeZoomLevel_baseType()),
    SUB_TYPE(Bundle.EventTypeZoomLevel_subType());

    @Override
    public String getDisplayName() {
        return displayName;
    }

    private final String displayName;

    private EventTypeZoomLevel(String displayName) {
        this.displayName = displayName;
    }
}
