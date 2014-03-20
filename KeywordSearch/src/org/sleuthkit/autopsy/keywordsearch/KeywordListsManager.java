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
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Keeps track, by name, of the keyword lists to be used for file ingest.
 */
// Note: This is a first step towards a keyword lists manager; it consists of
// the portion of the keyword list management code that resided in the keyword 
// search file ingest module.
// RJCTODO: How to keyword lists get initialized
final class KeywordListsManager {

    private static KeywordListsManager instance = null;
    private final Logger logger = Logger.getLogger(KeywordListsManager.class.getName());
    private final List<String> keywordListNames = new ArrayList<>();
    private final List<Keyword> keywords = new ArrayList<>();

    /**
     * Gets the keyword lists manager singleton.
     */
    static synchronized KeywordListsManager getInstance() {
        if (null == instance) {
            instance = new KeywordListsManager();
        }
        return instance;
    }

    private KeywordListsManager() {
        addKeywordListsForFileIngest(null);
    }

    /**
     * Sets the keyword lists to be used for ingest. The lists that are used
     * will be the union of the lists enabled using the keyword search global
     * options panel and a selection, possibly empty, of the disabled lists.
     *
     * @param listNames The names of disabled lists to temporarily enable
     */
    synchronized void addKeywordListsForFileIngest(List<String> listNames) {
        keywords.clear();
        keywordListNames.clear();

        StringBuilder logMessage = new StringBuilder();
        KeywordSearchListsXML globalKeywordSearchOptions = KeywordSearchListsXML.getCurrent();
        for (KeywordList list : globalKeywordSearchOptions.getListsL()) {
            String listName = list.getName();
            if ((list.getUseForIngest() == true) || (listNames != null && listNames.contains(listName))) {
                keywordListNames.add(listName);
                logMessage.append(listName).append(" ");
            }

            for (Keyword keyword : list.getKeywords()) {
                if (!keywords.contains(keyword)) {
                    keywords.add(keyword);
                }
            }
        }

        logger.log(Level.INFO, "Keyword lists for file ingest set to: {0}", logMessage.toString());
    }

    /**
     * Returns the keyword lists to be used for ingest, by name.
     *
     * @return The names of the enabled keyword lists
     */
    synchronized List<String> getNamesOfKeywordListsForFileIngest() {
        return new ArrayList<>(keywordListNames);
    }

    /**
     * Indicates whether or not there are currently keywords for which to search
     * during ingest.
     *
     * @return True if there are no keywords specified, false otherwise
     */
    synchronized boolean hasNoKeywordsForSearch() {
        return (keywords.isEmpty());
    }
}
