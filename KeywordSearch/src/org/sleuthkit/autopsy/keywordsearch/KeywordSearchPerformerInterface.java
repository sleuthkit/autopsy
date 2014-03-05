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

import java.util.List;


/**
 * KeywordSearchPerformers are perform different searches from
 * different interfaces and places in the application. Its
 * results are then passed to a KeywordSearchQuery implementation
 * to perform the actual search. 
 */
interface KeywordSearchPerformerInterface {
    
    /**
     * Does this interface support multi-word queries?
     * @return 
     */
    boolean isMultiwordQuery();
    
    /**
     * True if the user did not choose to do a regular expression search
     * @return 
     */
    boolean isRegExQuerySelected();
    
    /**
     * True if the user wants to match substrings instead of just whole words
     * @return 
     */    
    boolean isWholewordQuerySelected();
    
    /**
     * Returns the query/keyword string that the user entered/selected
     * @return Keyword to search
     */
    String getQueryText();
    
    /**
     * Returns the list of Keyword objects that the user entered/selected
     * @return 
     */
    List<Keyword> getQueryList();
    
    /**
     * Set the number of files that have been indexed
     * @param filesIndexed 
     */
    void setFilesIndexed(int filesIndexed);
    
    /**
     * Performs the search using the selected keywords.  
     * Creates a DataResultTopComponent with the results. 
     */
    void search();
}
