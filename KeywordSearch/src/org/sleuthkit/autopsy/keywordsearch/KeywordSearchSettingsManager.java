/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.openide.util.io.NbObjectInputStream;
import org.openide.util.io.NbObjectOutputStream;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.datamodel.BlackboardAttribute;

class KeywordSearchSettingsManager {

    private static KeywordSearchSettings settings = new KeywordSearchSettings();

    private static final String CUR_LISTS_FILE_NAME = "keywords.settings";     //NON-NLS
    private static final String CUR_LISTS_FILE = PlatformUtil.getUserConfigDirectory() + File.separator + CUR_LISTS_FILE_NAME;
    private static final Logger logger = Logger.getLogger(KeywordSearchSettingsManager.class.getName());

    KeywordSearchSettingsManager() throws KeywordSearchSettingsManagerException {
        prepopulateLists();
        this.readSettings();
    }

    private void writeSettings() throws KeywordSearchSettingsManagerException {
        try (NbObjectOutputStream out = new NbObjectOutputStream(new FileOutputStream(CUR_LISTS_FILE))) {
            out.writeObject(settings);
        } catch (IOException ex) {
            throw new KeywordSearchSettingsManagerException("Couldn't keyword search settings.", ex);
        }
    }

    private void readSettings() throws KeywordSearchSettingsManagerException {
        File serializedDefs = new File(CUR_LISTS_FILE);
        try {
            try (NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(serializedDefs))) {
                settings = (KeywordSearchSettings) in.readObject();
            }
        } catch (IOException | ClassNotFoundException ex) {
            throw new KeywordSearchSettingsManagerException("Couldn't read keyword search settings.", ex);
        }
    }

    private void prepopulateLists() {
        //phone number
        List<Keyword> phones = new ArrayList<>();
        phones.add(new Keyword("[(]{0,1}\\d\\d\\d[)]{0,1}[\\.-]\\d\\d\\d[\\.-]\\d\\d\\d\\d", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER)); //NON-NLS
        //phones.add(new Keyword("\\d{8,10}", false));
        //IP address
        List<Keyword> ips = new ArrayList<>();
        ips.add(new Keyword("(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_IP_ADDRESS));
        //email
        List<Keyword> emails = new ArrayList<>();
        emails.add(new Keyword("(?=.{8})[a-z0-9%+_-]+(?:\\.[a-z0-9%+_-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,4}(?<!\\.txt|\\.exe|\\.dll|\\.jpg|\\.xml)", //NON-NLS
                false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL));
        //emails.add(new Keyword("[A-Z0-9._%-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}", 
        //                       false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL));
        //URL
        List<Keyword> urls = new ArrayList<>();
        //urls.add(new Keyword("http://|https://|^www\\.", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL));
        urls.add(new Keyword("((((ht|f)tp(s?))\\://)|www\\.)[a-zA-Z0-9\\-\\.]+\\.([a-zA-Z]{2,5})(\\:[0-9]+)*(/($|[a-zA-Z0-9\\.\\,\\;\\?\\'\\\\+&amp;%\\$#\\=~_\\-]+))*", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL)); //NON-NLS

        //urls.add(new Keyword("ssh://", false, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL));
        //disable messages for harcoded/locked lists
        String name;

        name = "Phone Numbers"; //NON-NLS
        settings.addList(name, phones, false, false, true);

        name = "IP Addresses"; //NON-NLS
        settings.addList(name, ips, false, false, true);

        name = "Email Addresses"; //NON-NLS
        settings.addList(name, emails, true, false, true);

        name = "URLs"; //NON-NLS
        settings.addList(name, urls, false, false, true);
    }

    /**
     * Used to translate more implementation-details-specific exceptions (which
     * are logged by this class) into more generic exceptions for propagation to
     * clients of the user-defined file types manager.
     */
    static class KeywordSearchSettingsManagerException extends Exception {

        private static final long serialVersionUID = 1L;

        KeywordSearchSettingsManagerException(String message) {
            super(message);
        }

        KeywordSearchSettingsManagerException(String message, Throwable throwable) {
            super(message, throwable);
        }
    }
}
