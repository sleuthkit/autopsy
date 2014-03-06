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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Maintains the keyword lists to be used for file ingest.
 */
// Note: This is a first step towards a keyword lists manager, an extraction of 
// the keyword list management code from the keyword search file ingest module.
final class KeywordListsManager {

    private static KeywordListsManager instance = null;
    private final Logger logger = Logger.getLogger(KeywordListsManager.class.getName());
    private List<Keyword> keywords = new ArrayList<>();
    private List<String> keywordLists = new ArrayList<>();
    private Map<String, KeywordList> keywordToList = new HashMap<>();

    /**
     * Gets the keyword lists manager singleton.
     */
    static synchronized KeywordListsManager getInstance() {
        if (null == instance) {
            instance = new KeywordListsManager();
        }
        return instance;
    }

    /**
     * Creates a keyword lists manager initialized with the keyword lists
     * specified in the global options for the keyword search file ingest
     * module.
     */
    private KeywordListsManager() {
        // Passing null to this method makes use of a side effect of the method
        // to cause the keyword lists from the global options to be added to 
        // the keyword lists to be used during file ingest.
        addKeywordLists(null);
    }

    /**
     * RJCTODO
     *
     * @param listNames
     */
    void addKeywordLists(List<String> listNames) {
        keywords.clear();
        keywordLists.clear();
        keywordToList.clear();

        StringBuilder logMessage = new StringBuilder();
        KeywordSearchListsXML globalKeywordSearchOptions = KeywordSearchListsXML.getCurrent();
        for (KeywordList list : globalKeywordSearchOptions.getListsL()) {
            String listName = list.getName();
            if ((list.getUseForIngest() == true) || (null != listNames && listNames.contains(listName))) {
                keywordLists.add(listName);
                logMessage.append(listName).append(" ");
            }

            for (Keyword keyword : list.getKeywords()) {
                if (!keywords.contains(keyword)) {
                    keywords.add(keyword);
                    keywordToList.put(keyword.getQuery(), list);
                }
            }
        }

        logger.log(Level.INFO, "Keyword lists for file ingest set to: {0}", logMessage.toString());
    }
    
    /**
     * RJCTODO
     *
     * @return
     */
    List<String> getKeywordLists() {
        return new ArrayList<>(keywordLists);
    }
}
