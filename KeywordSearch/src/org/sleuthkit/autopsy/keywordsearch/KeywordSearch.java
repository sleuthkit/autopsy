/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Wrapper over KeywordSearch Solr server singleton. The class also provides
 * some global types and property change support on the server events.
 */
public class KeywordSearch {

    private static Server server;
    //we want a custom java.util.logging.Logger here for a reason
    //a separate logger from framework logs
    private static final java.util.logging.Logger tikaLogger = java.util.logging.Logger.getLogger("Tika"); //NON-NLS
    private static final Logger logger = Logger.getLogger(Case.class.getName());

    // @@@ We should move this into TskData (or somewhere) because we are using
    // this value in the results tree to display substring differently from regexp (KeywordHit.java)
    public enum QueryType {
        LITERAL, SUBSTRING, REGEX
    };
    public static final String NUM_FILES_CHANGE_EVT = "NUM_FILES_CHANGE_EVT"; //NON-NLS
    private static PropertyChangeSupport changeSupport = new PropertyChangeSupport(KeywordSearch.class);

    /**
     * Get an instance of KeywordSearch server to execute queries on Content,
     * getting extracted text, performing searches, etc.
     *
     * @return singleton instance of KeywordSearch server
     */
    public static synchronized Server getServer() {
        if (server == null) {
            server = new Server();
        }
        return server;
    }

    static {
        try {
            final int MAX_TIKA_LOG_FILES = 3;
            FileHandler tikaLogHandler = new FileHandler(PlatformUtil.getUserDirectory().getAbsolutePath() + "/var/log/tika.log", //NON-NLS
                    0, MAX_TIKA_LOG_FILES);
            tikaLogHandler.setFormatter(new SimpleFormatter());
            tikaLogHandler.setEncoding(PlatformUtil.getLogFileEncoding());
            tikaLogger.addHandler(tikaLogHandler);
            //do not forward to the parent autopsy logger
            tikaLogger.setUseParentHandlers(false);
        } catch (IOException | SecurityException ex) {
            logger.log(Level.SEVERE, "Error setting up tika logging", ex); //NON-NLS
        }
    }

    // don't instantiate
    private KeywordSearch() {
        throw new AssertionError();
    }

    static java.util.logging.Logger getTikaLogger() {
        return tikaLogger;
    }

    public static void addNumIndexedFilesChangeListener(PropertyChangeListener l) {
        changeSupport.addPropertyChangeListener(NUM_FILES_CHANGE_EVT, l);
    }

    public static void removeNumIndexedFilesChangeListener(PropertyChangeListener l) {
        changeSupport.removePropertyChangeListener(l);
    }

    public static void fireNumIndexedFilesChange(Integer oldNum, Integer newNum) {

        try {
            changeSupport.firePropertyChange(NUM_FILES_CHANGE_EVT, oldNum, newNum);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "KeywordSearch listener threw exception", e); //NON-NLS
            MessageNotifyUtil.Notify.show(NbBundle.getMessage(KeywordSearch.class, "KeywordSearch.moduleErr"),
                    NbBundle.getMessage(KeywordSearch.class,
                            "KeywordSearch.fireNumIdxFileChg.moduleErr.msg"),
                    MessageNotifyUtil.MessageType.ERROR);
        }
    }
}
