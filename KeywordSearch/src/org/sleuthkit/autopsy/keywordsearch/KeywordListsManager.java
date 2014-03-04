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

/**
 * RJCTODO:
 */
public class KeywordListsManager {
    private static KeywordListsManager instance = null;
    private List<Keyword> keywords = new ArrayList<>(); //keywords to search
    private List<String> keywordLists = new ArrayList<>(); // lists currently being searched
    private Map<String, KeywordSearchListsAbstract.KeywordSearchList> keywordToList = new HashMap<>();    
        
    /**
     * Gets the singleton instance of this class.
     */
    public static synchronized KeywordListsManager getInstance() {
        if (null == instance) {
            instance = new KeywordListsManager();
        }
        return instance;
    }       
}
