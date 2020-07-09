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
package org.sleuthkit.autopsy.timeline.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import org.sleuthkit.datamodel.TimelineEventType;

/**
 * Utilities for dealy with EventTypes, specifically for getting GUI properties
 * for them.
 */
final public class EventTypeUtils {

    static final private String IMAGE_BASE_PATH = "org/sleuthkit/autopsy/timeline/images/";
    static private final Map<TimelineEventType, Image> imageMap = new HashMap<>();

    public static Image getImage(TimelineEventType type) {
        return imageMap.computeIfAbsent(type, type2 -> new Image(getImagePath(type2)));
    }

    public static String getImagePath(TimelineEventType type) {
        long typeID = type.getTypeID();
        String imageFileName;
        if (typeID == TimelineEventType.FILE_SYSTEM.getTypeID()) {
            imageFileName = "blue-document.png";
        } else if (typeID == TimelineEventType.MISC_TYPES.getTypeID()) {
            imageFileName = "block.png";
        } else if (typeID == TimelineEventType.WEB_ACTIVITY.getTypeID()) {
            imageFileName = "web-file.png";
        } else if (typeID == TimelineEventType.MISC_TYPES.getTypeID()) {
            imageFileName = "block.png";
        } else if (typeID == TimelineEventType.FILE_ACCESSED.getTypeID()) {
            imageFileName = "blue-document-attribute-a.png";
        } else if (typeID == TimelineEventType.FILE_CHANGED.getTypeID()) {
            imageFileName = "blue-document-attribute-c.png";
        } else if (typeID == TimelineEventType.FILE_MODIFIED.getTypeID()) {
            imageFileName = "blue-document-attribute-m.png";
        } else if (typeID == TimelineEventType.FILE_CREATED.getTypeID()) {
            imageFileName = "blue-document-attribute-b.png";
        } else if (typeID == TimelineEventType.WEB_DOWNLOADS.getTypeID()) {
            imageFileName = "downloads.png";
        } else if (typeID == TimelineEventType.WEB_COOKIE.getTypeID()) {
            imageFileName = "cookies.png";
        } else if (typeID == TimelineEventType.WEB_BOOKMARK.getTypeID()) {
            imageFileName = "bookmarks.png";
        } else if (typeID == TimelineEventType.WEB_HISTORY.getTypeID()) {
            imageFileName = "history.png";
        } else if (typeID == TimelineEventType.WEB_SEARCH.getTypeID()) {
            imageFileName = "searchquery.png";
        } else if (typeID == TimelineEventType.CALL_LOG.getTypeID()) {
            imageFileName = "calllog.png";
        } else if (typeID == TimelineEventType.DEVICES_ATTACHED.getTypeID()) {
            imageFileName = "usb_devices.png";
        } else if (typeID == TimelineEventType.EMAIL.getTypeID()) {
            imageFileName = "mail-icon-16.png";
        } else if (typeID == TimelineEventType.EXIF.getTypeID()) {
            imageFileName = "camera-icon-16.png";
        } else if (typeID == TimelineEventType.GPS_ROUTE.getTypeID()) {
            imageFileName = "gps-search.png";
        } else if (typeID == TimelineEventType.GPS_TRACKPOINT.getTypeID()) {
            imageFileName = "gps-trackpoint.png";
        } else if (typeID == TimelineEventType.INSTALLED_PROGRAM.getTypeID()) {
            imageFileName = "programs.png";
        } else if (typeID == TimelineEventType.MESSAGE.getTypeID()) {
            imageFileName = "message.png";
        } else if (typeID == TimelineEventType.RECENT_DOCUMENTS.getTypeID()) {
            imageFileName = "recent_docs.png";
        } else if (typeID == TimelineEventType.REGISTRY.getTypeID()) {
            imageFileName = "registry.png";
        } else if (typeID == TimelineEventType.LOG_ENTRY.getTypeID()) {
            imageFileName = "raw_access_logs.png";
        } else if (typeID == TimelineEventType.USER_CREATED.getTypeID()) {
            imageFileName = "hand_point.png";
        } else if (typeID == TimelineEventType.WEB_FORM_AUTOFILL.getTypeID()) {
            imageFileName = "artifact-icon.png";
        } else if (typeID == TimelineEventType.WEB_FORM_ADDRESSES.getTypeID()) {
            imageFileName = "artifact-icon.png";
        } else if (typeID == TimelineEventType.METADATA_CREATED.getTypeID()) {
            imageFileName = "blue-document-attribute-b.png";
        } else if (typeID == TimelineEventType.METADATA_LAST_SAVED.getTypeID()) {
            imageFileName = "blue-document-attribute-m.png";
        } else if (typeID == TimelineEventType.METADATA_LAST_PRINTED.getTypeID()) {
            imageFileName = "blue-document.png";
        }else {
            imageFileName = "timeline_marker.png";
        }

        return IMAGE_BASE_PATH + imageFileName;
    }

    public static Color getColor(TimelineEventType type) {
        if (type.equals(TimelineEventType.ROOT_EVENT_TYPE)) {
            return Color.hsb(359, .9, .9, 0);
        }

        TimelineEventType superType = type.getParent();

        Color baseColor = getColor(superType);
        int siblings = superType.getSiblings().stream()
                .max((type1, type2) -> Integer.compare(type1.getChildren().size(), type2.getChildren().size()))
                .get().getChildren().size() + 1;
        int superSiblingsCount = superType.getSiblings().size();

        int ordinal = new ArrayList<>(type.getSiblings()).indexOf(type);
        double offset = (360.0 / superSiblingsCount) / siblings;
        Color deriveColor = baseColor.deriveColor(ordinal * offset, 1, 1, 1);

        return Color.hsb(deriveColor.getHue(), deriveColor.getSaturation(), deriveColor.getBrightness());
    }

    private EventTypeUtils() {
    }
}
