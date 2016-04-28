/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * A manager for keyword lists.
 */
// TODO (RC): This class is a first step towards a fully functional and public
// keyword list manager, establishing the beginning of a public API. It is
// motivated by a desire to not expose XmlKeywordSearchList to public clients.
// It should be futher developed as time constraints and needs dictate.
public class KeywordListsManager extends Observable {

    private static KeywordListsManager instance;
    private final PropertyChangeListener listsChangeListener;
    private static KeywordSearchSettingsManager manager;
    private static final Logger logger = Logger.getLogger(KeywordListsManager.class.getName());

    /**
     * Constructs a keyword lists manager.
     */
    private KeywordListsManager() {

        try {
            manager = KeywordSearchSettingsManager.getInstance();
        } catch (KeywordSearchSettingsManager.KeywordSearchSettingsManagerException ex) {
            //OSTODO
            logger.log(Level.SEVERE, "Couldn't load settings.", ex);
        }
        this.listsChangeListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                KeywordListsManager.this.setChanged();
                KeywordListsManager.this.notifyObservers();
            }
        };
        manager.addPropertyChangeListener(listsChangeListener);
    }

    /**
     * Gets the singleton instance of the keyword lists manager.
     */
    public static synchronized KeywordListsManager getInstance() {
        if (instance == null) {
            instance = new KeywordListsManager();
        }
        return instance;
    }

    /**
     * Gets the names of the configured keyword lists.
     *
     * @return The name strings.
     */
    public List<String> getKeywordListNames() {
        List<String> names = new ArrayList<>();
        for (KeywordList list : manager.getKeywordLists()) {
            names.add(list.getName());
        }
        return names;
    }

    /**
     * Force reload of the keyword lists XML file.
     */
    public static void reloadKeywordLists() {
        try {
            manager.reload();
        } catch (KeywordSearchSettingsManager.KeywordSearchSettingsManagerException ex) {
            logger.log(Level.SEVERE, "Couldn't reload keyword serach settings.", ex);
        }
    }

}
