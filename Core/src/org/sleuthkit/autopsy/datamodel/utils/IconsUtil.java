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

import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;

/**
 * Utility methods for handling icons
 */
public final class IconsUtil {
    
    private static final String ICON_BASE_PATH = "/org/sleuthkit/autopsy/images/"; //NON-NLS

    private IconsUtil() {

    }

    @SuppressWarnings("deprecation")
    public static String getIconFilePath(int typeID) {
        String imageFile;
        if (typeID == ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID()) {
            imageFile = "bookmarks.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID()) {
            imageFile = "cookies.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID()) {
            imageFile = "history.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID()) {
            imageFile = "downloads.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_RECENT_OBJECT.getTypeID()) {
            imageFile = "recent_docs.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_GPS_TRACKPOINT.getTypeID()) {
            imageFile = "gps_trackpoint.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_INSTALLED_PROG.getTypeID()) {
            imageFile = "programs.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID()) {
            imageFile = "usb_devices.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID()) {
            imageFile = "mail-icon-16.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_EXTRACTED_TEXT.getTypeID()) {
            imageFile = "text-file.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID()) {
            imageFile = "searchquery.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID()) {
            imageFile = "camera-icon-16.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_OS_INFO.getTypeID()) {
            imageFile = "computer.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_SERVICE_ACCOUNT.getTypeID()) {
            imageFile = "account-icon-16.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_CONTACT.getTypeID()) {
            imageFile = "contact.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_MESSAGE.getTypeID()) {
            imageFile = "message.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_CALLLOG.getTypeID()) {
            imageFile = "calllog.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_CALENDAR_ENTRY.getTypeID()) {
            imageFile = "calendar.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_SPEED_DIAL_ENTRY.getTypeID()) {
            imageFile = "speeddialentry.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_BLUETOOTH_PAIRING.getTypeID()) {
            imageFile = "Bluetooth.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_GPS_BOOKMARK.getTypeID()) {
            imageFile = "gpsfav.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_GPS_LAST_KNOWN_LOCATION.getTypeID()) {
            imageFile = "gps-lastlocation.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_GPS_SEARCH.getTypeID()) {
            imageFile = "gps-search.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_PROG_RUN.getTypeID()) {
            imageFile = "installed.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED.getTypeID()
                || typeID == ARTIFACT_TYPE.TSK_ENCRYPTION_SUSPECTED.getTypeID()) {
            imageFile = "encrypted-file.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_EXT_MISMATCH_DETECTED.getTypeID()) {
            imageFile = "mismatch-16.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_GPS_ROUTE.getTypeID()
                || typeID == ARTIFACT_TYPE.TSK_GPS_TRACK.getTypeID()) {
            imageFile = "gps_trackpoint.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_REMOTE_DRIVE.getTypeID()) {
            imageFile = "drive_network.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_OS_ACCOUNT.getTypeID()) {
            imageFile = "os-account.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_OBJECT_DETECTED.getTypeID()) {
            imageFile = "objects.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_WEB_FORM_AUTOFILL.getTypeID()) {
            imageFile = "web-form.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_WEB_CACHE.getTypeID()) {
            imageFile = "cache.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_USER_CONTENT_SUSPECTED.getTypeID()) {
            imageFile = "user-content.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_METADATA.getTypeID()) {
            imageFile = "metadata.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_CLIPBOARD_CONTENT.getTypeID()) {
            imageFile = "clipboard.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_FACE_DETECTED.getTypeID()) {
            imageFile = "face.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_WIFI_NETWORK.getTypeID()) {
            imageFile = "network-wifi.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_WIFI_NETWORK_ADAPTER.getTypeID()) {
            imageFile = "network-wifi.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_SIM_ATTACHED.getTypeID()) {
            imageFile = "sim_card.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_BLUETOOTH_ADAPTER.getTypeID()) {
            imageFile = "Bluetooth.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_DEVICE_INFO.getTypeID()) {
            imageFile = "devices.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_VERIFICATION_FAILED.getTypeID()) {
            imageFile = "validationFailed.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_WEB_ACCOUNT_TYPE.getTypeID()) {
            imageFile = "web-account-type.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_WEB_FORM_ADDRESS.getTypeID()) {
            imageFile = "web-form-address.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_WEB_CATEGORIZATION.getTypeID()) {
            imageFile = "domain-16.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_GPS_AREA.getTypeID()) {
            imageFile = "gps-area.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_YARA_HIT.getTypeID()) {
            imageFile = "yara_16.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_PREVIOUSLY_SEEN.getTypeID()) {
            imageFile = "previously-seen.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_PREVIOUSLY_UNSEEN.getTypeID()) {
            imageFile = "previously-unseen.png"; //NON-NLS
        } else if (typeID == ARTIFACT_TYPE.TSK_PREVIOUSLY_NOTABLE.getTypeID()) {
            imageFile = "red-circle-exclamation.png"; //NON-NLS
        } else if (typeID == BlackboardArtifact.Type.TSK_HASHSET_HIT.getTypeID()) {
            imageFile = "hashset_hits.png";
        } else if (typeID == BlackboardArtifact.Type.TSK_KEYWORD_HIT.getTypeID()) {
            imageFile = "keyword_hits.png";
        } else if (typeID == BlackboardArtifact.Type.TSK_INTERESTING_ARTIFACT_HIT.getTypeID()
                || typeID == BlackboardArtifact.Type.TSK_INTERESTING_FILE_HIT.getTypeID()
                || typeID == BlackboardArtifact.Type.TSK_INTERESTING_ITEM.getTypeID()) {
            imageFile = "interesting_item.png";
        } else if (typeID == BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID()) {
            imageFile = "accounts.png";
        } else {
            imageFile = "artifact-icon.png"; //NON-NLS
        }
        return "/org/sleuthkit/autopsy/images/" + imageFile;
    }
    
    /**
     * Get the path of the icon for the given Account Type.
     *
     * @return The path of the icon for the given Account Type.
     */
    public static String getIconFilePath(Account.Type type) {

        if (type.equals(Account.Type.CREDIT_CARD)) {
            return ICON_BASE_PATH + "credit-card.png";
        } else if (type.equals(Account.Type.DEVICE)) {
            return ICON_BASE_PATH + "image.png";
        } else if (type.equals(Account.Type.EMAIL)) {
            return ICON_BASE_PATH + "email.png";
        } else if (type.equals(Account.Type.FACEBOOK)) {
            return ICON_BASE_PATH + "facebook.png";
        } else if (type.equals(Account.Type.INSTAGRAM)) {
            return ICON_BASE_PATH + "instagram.png";
        } else if (type.equals(Account.Type.MESSAGING_APP)) {
            return ICON_BASE_PATH + "messaging.png";
        } else if (type.equals(Account.Type.PHONE)) {
            return ICON_BASE_PATH + "phone.png";
        } else if (type.equals(Account.Type.TWITTER)) {
            return ICON_BASE_PATH + "twitter.png";
        } else if (type.equals(Account.Type.WEBSITE)) {
            return ICON_BASE_PATH + "web-file.png";
        } else if (type.equals(Account.Type.WHATSAPP)) {
            return ICON_BASE_PATH + "WhatsApp.png";
        } else {
            //there could be a default icon instead...
            return ICON_BASE_PATH + "face.png";
//            throw new IllegalArgumentException("Unknown Account.Type: " + type.getTypeName());
        }
    }
}
