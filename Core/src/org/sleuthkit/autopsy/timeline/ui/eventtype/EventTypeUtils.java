/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.eventtype;

import javafx.scene.image.Image;
import org.sleuthkit.datamodel.timeline.eventtype.BaseType;
import org.sleuthkit.datamodel.timeline.eventtype.EventType;
import org.sleuthkit.datamodel.timeline.eventtype.FileSystemType;
import org.sleuthkit.datamodel.timeline.eventtype.RootEventType;
import org.sleuthkit.datamodel.timeline.eventtype.WebType;

/**
 *
 */
public class EventTypeUtils {

    static final private String IMAGE_BASE_PATH = "/org/sleuthkit/autopsy/timeline/images/";

    Image getImage(EventType type) {
        int typeID = type.getTypeID();
        String imagePath;
        if (typeID == BaseType.FILE_SYSTEM.getTypeID()) {
            imagePath = "blue-document.png";
        } else if (typeID == BaseType.MISC_TYPES.getTypeID()) {
            imagePath = "block.png";
        } else if (typeID == BaseType.WEB_ACTIVITY.getTypeID()) {
            imagePath = "web-file.png";
        } else if (typeID == BaseType.MISC_TYPES.getTypeID()) {
            imagePath = "block.png";
        } else if (typeID == FileSystemType.FILE_ACCESSED.getTypeID()) {
            imagePath = "blue-document-attribute-a.png";
        } else if (typeID == FileSystemType.FILE_CHANGED.getTypeID()) {
            imagePath = "blue-document-attribute-c.png";
        } else if (typeID == FileSystemType.FILE_MODIFIED.getTypeID()) {
            imagePath = "blue-document-attribute-m.png";
        } else if (typeID == FileSystemType.FILE_CREATED.getTypeID()) {
            imagePath = "blue-document-attribute-b.png";
        } else if (typeID == WebType.WEB_DOWNLOADS.getTypeID()) {
            imagePath = "downloads.png";
        } else if (typeID == WebType.WEB_COOKIE.getTypeID()) {
            imagePath = "cookies.png";
        } else if (typeID == WebType.WEB_BOOKMARK.getTypeID()) {
            imagePath = "bookmarks.png";
        } else if (typeID == WebType.WEB_HISTORY.getTypeID()) {
            imagePath = "history.png";
        } else if (typeID == WebType.WEB_SEARCH.getTypeID()) {
            imagePath = "searchquery.png";

        } else {
            imagePath = "default";
        }

        return new Image(EventTypeUtils.class
                .getResourceAsStream(IMAGE_BASE_PATH
                        + imagePath));
    }
