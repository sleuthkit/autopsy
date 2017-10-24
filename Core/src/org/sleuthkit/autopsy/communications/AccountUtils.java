/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications;

import org.sleuthkit.datamodel.Account;

/**
 *
 */
public class AccountUtils {

    /**
     * The file name of the icon for the given Account Type. Will not include
     * the path but will include the extension.
     *
     * @return The file name of the icon for the given Account Type.
     */
    static final String getIconFileName(Account.Type type) {
        if (type == Account.Type.CREDIT_CARD) {
            return "credit-card.png";
        } else if (type == Account.Type.DEVICE) {
            return "image.png";
        } else if (type == Account.Type.EMAIL) {
            return "email.png";
        } else if (type == Account.Type.FACEBOOK) {
            return "facebook.png";
        } else if (type == Account.Type.INSTAGRAM) {
            return "instagram.png";
        } else if (type == Account.Type.MESSAGING_APP) {
            return "messaging.png";
        } else if (type == Account.Type.PHONE) {
            return "phone.png";
        } else if (type == Account.Type.TWITTER) {
            return "twitter.png";
        } else if (type == Account.Type.WEBSITE) {
            return "web-file.png";
        } else if (type == Account.Type.WHATSAPP) {
            return "WhatsApp.png";
        } else {
            //there could be a default icon instead...
            throw new IllegalArgumentException("Unknown Account.Type: " + type.getTypeName());
        }
    }

}
