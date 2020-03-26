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
package org.sleuthkit.autopsy.datamodel.utils;

import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Utility methods for handling icons
 */
public class IconsUtil {
    @SuppressWarnings("deprecation")
    public static String getIconFilePath(int typeID) {
        String filePath = "org/sleuthkit/autopsy/images/"; //NON-NLS
        if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID()) {
            return filePath + "bookmarks.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID()) {
            return filePath + "cookies.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID()) {
            return filePath + "history.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID()) {
            return filePath + "downloads.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_RECENT_OBJECT.getTypeID()) {
            return filePath + "recent_docs.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT.getTypeID()) {
            return filePath + "gps_trackpoint.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_INSTALLED_PROG.getTypeID()) {
            return filePath + "programs.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID()) {
            return filePath + "usb_devices.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID()) {
            return filePath + "mail-icon-16.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_EXTRACTED_TEXT.getTypeID()) {
            return filePath + "text-file.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID()) {
            return filePath + "searchquery.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID()) {
            return filePath + "camera-icon-16.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_INFO.getTypeID()) {
            return filePath + "computer.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_SERVICE_ACCOUNT.getTypeID()) {
            return filePath + "account-icon-16.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT.getTypeID()) {
            return filePath + "contact.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE.getTypeID()) {
            return filePath + "message.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG.getTypeID()) {
            return filePath + "calllog.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_CALENDAR_ENTRY.getTypeID()) {
            return filePath + "calendar.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_SPEED_DIAL_ENTRY.getTypeID()) {
            return filePath + "speeddialentry.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_BLUETOOTH_PAIRING.getTypeID()) {
            return filePath + "bluetooth.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK.getTypeID()) {
            return filePath + "gpsfav.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_LAST_KNOWN_LOCATION.getTypeID()) {
            return filePath + "gps-lastlocation.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_SEARCH.getTypeID()) {
            return filePath + "gps-search.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_PROG_RUN.getTypeID()) {
            return filePath + "installed.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED.getTypeID()
                || typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_SUSPECTED.getTypeID()) {
            return filePath + "encrypted-file.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_EXT_MISMATCH_DETECTED.getTypeID()) {
            return filePath + "mismatch-16.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE.getTypeID()) {
            return filePath + "gps_trackpoint.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_REMOTE_DRIVE.getTypeID()) {
            return filePath + "drive_network.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_FACE_DETECTED.getTypeID()) {
            return filePath + "face.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_WIFI_NETWORK.getTypeID()) {
            return filePath + "network-wifi.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_WIFI_NETWORK_ADAPTER.getTypeID()) {
            return filePath + "network-wifi.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_SIM_ATTACHED.getTypeID()) {
            return filePath + "sim_card.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_BLUETOOTH_ADAPTER.getTypeID()) {
            return filePath + "Bluetooth.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_INFO.getTypeID()) {
            return filePath + "devices.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_VERIFICATION_FAILED.getTypeID()) {
            return filePath + "validationFailed.png"; //NON-NLS
        }
        return filePath + "artifact-icon.png"; //NON-NLS
    }
}
