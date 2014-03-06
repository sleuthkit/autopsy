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
 * 
 */
class KeywordListsManager {
            
    private static KeywordListsManager instance = null;    
    private final KeywordLists defaultKeywordLists = new KeywordLists();
    private final HashMap<Long, KeywordLists> keywordListsForIngestJobs = new HashMap<>();
    

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
        defaultKeywordLists.addKeywordLists(null); // RJCTODO: Not too fond of this trick...
    }

    // RJCTODO: May need to change this one
    synchronized void addKeywordListsToDefaultLists(List<String> listNames) {
        defaultKeywordLists.addKeywordLists(listNames);
    }

    // RJCTODO: May not need this one
     synchronized void addKeywordListsToAllIngestJobs(List<String> listNames) {
        for (KeywordLists listsForJob : keywordListsForIngestJobs.values()) {
            listsForJob.addKeywordLists(listNames);
        }
    }

     synchronized void addKeywordListsToIngestJob(List<String> listNames, long ingestJobId) {
        KeywordLists listsForJob = keywordListsForIngestJobs.get(ingestJobId);
        if (null == listsForJob) {
            listsForJob = new KeywordLists();
            keywordListsForIngestJobs.put(ingestJobId, listsForJob);
        }
        listsForJob.addKeywordLists(listNames);
    }

    synchronized List<String> getDefaultKeywordLists() {
       return defaultKeywordLists.getKeywordLists();
    }

    synchronized List<String> getKeywordListsForIngestJob(long ingestJobId) {
       KeywordLists listsForJob = keywordListsForIngestJobs.get(ingestJobId);
       if (null == listsForJob) {
           listsForJob = new KeywordLists();
           keywordListsForIngestJobs.put(ingestJobId, listsForJob);
       }
       return listsForJob.getKeywordLists();
    }

    synchronized List<String> getKeywordListsForAllIngestJobs() {
        List<String> keywordLists = new ArrayList<>();
        for (KeywordLists listsForJob : keywordListsForIngestJobs.values()) {
            List<String> listNames = listsForJob.getKeywordLists();
            for (String listName : listNames) {
                if (!keywordLists.contains(listName)) {
                    keywordLists.add(listName);
                }
            }
        }    
        return keywordLists;
    }

    synchronized void removeKeywordListsForIngestTask(long ingestTaskId) {
        // RJCTODO: May want to have an event trigger this
        keywordListsForIngestJobs.clear();        
    }

    private static final class KeywordLists {        

        // RJCTODO: Understand better how these are used
        private List<Keyword> keywords = new ArrayList<>(); //keywords to search
        private List<String> keywordLists = new ArrayList<>(); // lists currently being searched
        private Map<String, KeywordList> keywordToList = new HashMap<>();    

        KeywordLists() {
            addKeywordLists(null);
        }

        List<String> getKeywordLists() {
            return new ArrayList<>(keywordLists);
        }

        void addKeywordLists(List<String> listNames) {
            // Refresh everything to pick up changes to the keywords lists 
            // saved to disk.
            // RJCTODO: Is this a good idea? Or should the XML file be read
            // only once, in the constructor, now that there are lists per 
            // ingest job?
            keywords.clear();
            keywordLists.clear();
            keywordToList.clear();

    //            StringBuilder sb = new StringBuilder();
            KeywordSearchListsXML loader = KeywordSearchListsXML.getCurrent();
            for (KeywordList list : loader.getListsL()) {
                // Add the list by list name.
                // RJCTODO: Understand this better.
                String listName = list.getName();
                if ((list.getUseForIngest() == true) 
                        || (null != listNames && listNames.contains(listName))) {
                    keywordLists.add(listName);
    //                    sb.append(listName).append(" ");
                }

                // Add the keywords from the list.
                // RJCTODO: Understand this better - isn't this adding the 
                // keywords from every list, whether enabled for ingest or not?
                for (Keyword keyword : list.getKeywords()) {
                    if (!keywords.contains(keyword)) {
                        keywords.add(keyword);
                        keywordToList.put(keyword.getQuery(), list);
                    }
                }
            }

            // RJCTODO: Was logging code that was here useful? If so, specify
            // ingest job id in message, set up logger for this class
    //            logger.log(Level.INFO, "Set new effective keyword lists: {0}", sb.toString());          
        }        
    }    
}
