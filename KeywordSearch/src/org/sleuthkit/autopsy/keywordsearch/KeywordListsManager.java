/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2019 Basis Technology Corp.
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

    /**
     * Constructs a keyword lists manager.
     */
    private KeywordListsManager() {
        this.listsChangeListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                KeywordListsManager.this.setChanged();
                KeywordListsManager.this.notifyObservers();
            }
        };
        XmlKeywordSearchList.getCurrent().addPropertyChangeListener(this.listsChangeListener);
    }

    /**
     * Gets the singleton instance of the keyword lists manager.
     * 
     * @return an instance of KeywordListsManager.
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
        for (KeywordList list : XmlKeywordSearchList.getCurrent().getListsL()) {
            names.add(list.getName());
        }
        return names;
    }
    
     /**
     * Get keyword list by name.
     *
     * @param name id of the list
     *
     * @return keyword list representation, null if no list by that name
     */
    public KeywordList getList(String name) {
        return XmlKeywordSearchList.getCurrent().getList(name);
    }
    
    /**
     * Force reload of the keyword lists XML file.
     */
    public static void reloadKeywordLists(){
        XmlKeywordSearchList.getCurrent().reload();
    }    

}
