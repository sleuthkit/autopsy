/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel.accounts;

import org.sleuthkit.datamodel.Account;

/**
 * AutopsyVisitableItem for the Accounts section of the tree. All nodes,
 * factories, and custom key class related to accounts are inner classes.
 */
final public class Accounts {

    private static final String ICON_BASE_PATH = "/org/sleuthkit/autopsy/images/"; //NON-NLS

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
