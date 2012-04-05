/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearchResultFactory.ResultWriter;

/**
 * Static class to track singletons for KeywordSearch module
 */
class KeywordSearch {

    private static final String BASE_URL = "http://localhost:8983/solr/";
    private static final Server SERVER = new Server(BASE_URL);

    public enum QueryType {WORD, REGEX};
    
    public static final String NUM_FILES_CHANGE_EVT = "NUM_FILES_CHANGE_EVT";
    
    static PropertyChangeSupport changeSupport = new PropertyChangeSupport(KeywordSearch.class);
    
    
    static Server getServer() {
        return SERVER;
    }

    // don't instantiate
    private KeywordSearch() {
        throw new AssertionError();
    }

    /**
     * Listener to swap cores when the case changes
     */
    static class CaseChangeListener implements PropertyChangeListener {

        CaseChangeListener() {
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String changed = evt.getPropertyName();
            Object oldValue = evt.getOldValue();
            Object newValue = evt.getNewValue();

            final Logger logger = Logger.getLogger(CaseChangeListener.class.getName());
            if (changed.equals(Case.CASE_CURRENT_CASE)) {
                if (newValue != null) {
                    // new case is open
                    try {
                        SERVER.openCore();
                    }
                    catch (Exception e) {
                        logger.log(Level.WARNING, "Could not open core.");
                    }
                } else if (oldValue != null) {
                    // a case was closed
                    try {
                        ResultWriter.stopAllWriters();
                        Thread.sleep(2000);
                        SERVER.closeCore();
                    }
                    catch (Exception e) {
                        logger.log(Level.WARNING, "Could not close core.");
                    }
                }
            }
        }
    }
}
