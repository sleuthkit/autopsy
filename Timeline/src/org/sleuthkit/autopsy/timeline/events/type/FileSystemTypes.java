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
package org.sleuthkit.autopsy.timeline.events.type;

import java.util.Collections;
import java.util.List;
import javafx.scene.image.Image;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;

/**
 *
 */
public enum FileSystemTypes implements EventType {

    FILE_MODIFIED("File Modified", "blue-document-attribute-m.png"),
    FILE_ACCESSED("File Accessed", "blue-document-attribute-a.png"),
    FILE_CREATED("File Created", "blue-document-attribute-b.png"),
    FILE_CHANGED("File Changed", "blue-document-attribute-c.png");

    private final String iconBase;

    private final Image image;

    @Override
    public Image getFXImage() {
        return image;
    }

    @Override
    public String getIconBase() {
        return iconBase;
    }

    @Override
    public EventTypeZoomLevel getZoomLevel() {
        return EventTypeZoomLevel.SUB_TYPE;
    }

    private final String displayName;

    @Override
    public EventType getSubType(String string) {
        return FileSystemTypes.valueOf(string);
    }

    @Override
    public EventType getSuperType() {
        return BaseTypes.FILE_SYSTEM;
    }

    @Override
    public List<? extends EventType> getSubTypes() {
        return Collections.emptyList();
    }

    private FileSystemTypes(String displayName, String iconBase) {
        this.displayName = displayName;
        this.iconBase = iconBase;
        this.image = new Image("org/sleuthkit/autopsy/timeline/images/" + iconBase, true);
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }
}
