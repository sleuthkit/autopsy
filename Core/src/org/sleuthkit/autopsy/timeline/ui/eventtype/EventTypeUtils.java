/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.eventtype;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import org.sleuthkit.datamodel.timeline.EventType;

/**
 *
 */
final public class EventTypeUtils {

    static final private String IMAGE_BASE_PATH = "/org/sleuthkit/autopsy/timeline/images/";
    static private final Map<EventType, Image> imageMap = new HashMap<>();

    public static Image getImage(EventType type) {
        return imageMap.computeIfAbsent(type, type2 -> new Image(getImagePath(type2)));
    }

    public static String getImagePath(EventType type) {
        int typeID = type.getTypeID();
        String imageFileName;
        if (typeID == EventType.FILE_SYSTEM.getTypeID()) {
            imageFileName = "blue-document.png";
        } else if (typeID == EventType.MISC_TYPES.getTypeID()) {
            imageFileName = "block.png";
        } else if (typeID == EventType.WEB_ACTIVITY.getTypeID()) {
            imageFileName = "web-file.png";
        } else if (typeID == EventType.MISC_TYPES.getTypeID()) {
            imageFileName = "block.png";
        } else if (typeID == EventType.FILE_ACCESSED.getTypeID()) {
            imageFileName = "blue-document-attribute-a.png";
        } else if (typeID == EventType.FILE_CHANGED.getTypeID()) {
            imageFileName = "blue-document-attribute-c.png";
        } else if (typeID == EventType.FILE_MODIFIED.getTypeID()) {
            imageFileName = "blue-document-attribute-m.png";
        } else if (typeID == EventType.FILE_CREATED.getTypeID()) {
            imageFileName = "blue-document-attribute-b.png";
        } else if (typeID == EventType.WEB_DOWNLOADS.getTypeID()) {
            imageFileName = "downloads.png";
        } else if (typeID == EventType.WEB_COOKIE.getTypeID()) {
            imageFileName = "cookies.png";
        } else if (typeID == EventType.WEB_BOOKMARK.getTypeID()) {
            imageFileName = "bookmarks.png";
        } else if (typeID == EventType.WEB_HISTORY.getTypeID()) {
            imageFileName = "history.png";
        } else if (typeID == EventType.WEB_SEARCH.getTypeID()) {
            imageFileName = "searchquery.png";
        } else if (typeID == EventType.CALL_LOG.getTypeID()) {
            imageFileName = "calllog.png";
        } else if (typeID == EventType.DEVICES_ATTACHED.getTypeID()) {
            imageFileName = "usb_devices.png";
        } else if (typeID == EventType.EMAIL.getTypeID()) {
            imageFileName = "mail-icon-16.png";
        } else if (typeID == EventType.EXIF.getTypeID()) {
            imageFileName = "camera-icon-16.png";
        } else if (typeID == EventType.GPS_ROUTE.getTypeID()) {
            imageFileName = "gps-search.png";
        } else if (typeID == EventType.GPS_TRACKPOINT.getTypeID()) {
            imageFileName = "gps-trackpoint.png";
        } else if (typeID == EventType.INSTALLED_PROGRAM.getTypeID()) {
            imageFileName = "programs.png";
        } else if (typeID == EventType.MESSAGE.getTypeID()) {
            imageFileName = "message.png";
        } else if (typeID == EventType.RECENT_DOCUMENTS.getTypeID()) {
            imageFileName = "recent_docs.png";
        } else {
            imageFileName = "timeline_marker.png";
        }

        return IMAGE_BASE_PATH + imageFileName;
    }

    public static Color getColor(EventType type) {
        if (type.equals(EventType.ROOT_EVEN_TYPE)) {
            return Color.hsb(359, .9, .9, 0);
        }

        EventType superType = type.getSuperType();

        Color baseColor = getColor(superType);
        int siblings = superType.getSiblingTypes().stream()
                .max((t, t1) -> Integer.compare(t.getSubTypes().size(), t1.getSubTypes().size()))
                .get().getSubTypes().size() + 1;
        int superSiblingsCount = superType.getSiblingTypes().size();

        int ordinal = new ArrayList<>(type.getSiblingTypes()).indexOf(type);
        double offset = (360.0 / superSiblingsCount) / siblings;
        Color deriveColor = baseColor.deriveColor(ordinal * offset, 1, 1, 1);

        return Color.hsb(deriveColor.getHue(), deriveColor.getSaturation(), deriveColor.getBrightness());
    }

    private EventTypeUtils() {
    }
}
