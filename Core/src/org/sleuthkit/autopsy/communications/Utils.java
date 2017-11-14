/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications;

import java.time.ZoneId;
import java.time.ZoneOffset;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.datamodel.Account;

/**
 * Utility class with helpers for dealing with accounts.
 */
class Utils {

    static ZoneId getUserPreferredZoneId() {
        ZoneId zone = UserPreferences.displayTimesInLocalTime() ? ZoneOffset.systemDefault() : ZoneOffset.UTC;
        return zone;
    }

    private Utils() {
    }

    /**
     * The file name of the icon for the given Account Type. Will not include
     * the path but will include the extension.
     *
     * @return The file name of the icon for the given Account Type.
     */
    static final String getIconFileName(Account.Type type) {
        if (type.equals(Account.Type.CREDIT_CARD)) {
            return "credit-card.png";
        } else if (type.equals(Account.Type.DEVICE)) {
            return "image.png";
        } else if (type.equals(Account.Type.EMAIL)) {
            return "email.png";
        } else if (type.equals(Account.Type.FACEBOOK)) {
            return "facebook.png";
        } else if (type.equals(Account.Type.INSTAGRAM)) {
            return "instagram.png";
        } else if (type.equals(Account.Type.MESSAGING_APP)) {
            return "messaging.png";
        } else if (type.equals(Account.Type.PHONE)) {
            return "phone.png";
        } else if (type.equals(Account.Type.TWITTER)) {
            return "twitter.png";
        } else if (type.equals(Account.Type.WEBSITE)) {
            return "web-file.png";
        } else if (type.equals(Account.Type.WHATSAPP)) {
            return "WhatsApp.png";
        } else {
            //there could be a default icon instead...
            throw new IllegalArgumentException("Unknown Account.Type: " + type.getTypeName());
        }
    }
}
