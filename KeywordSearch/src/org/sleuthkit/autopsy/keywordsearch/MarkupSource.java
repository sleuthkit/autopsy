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

import java.util.LinkedHashMap;

/**
 * Interface to provide HTML text to display in ExtractedContentViewer.
 * There is a SOLR implementaiton of this that interfaces with SOLR to 
 * highlight the keyword hits and a version that does not do markup
 * so that you can simply view the stored text. 
 */
interface MarkupSource {

    /**
     * @return text optionally marked up with the subsest of HTML that Swing
     * components can handle in their setText() method.
     * 
     */
    String getMarkup();
    
    /**
     * 
     * @return true if markup is marked to be searchable
     */
    boolean isSearchable();
    
    /**
     * If searchable markup, returns prefix of anchor, otherwise return empty string
     * @return 
     */
    String getAnchorPrefix();
    
    /**
     * if searchable markup, returns number of hits found and encoded in the markup
     * @return 
     */
    int getNumberHits();

    /**
     * @return title of markup source
     */
    @Override
    String toString();
    
    /**
     * get number pages/chunks
     * @return number pages
     */
    int getNumberPages();
    
    /**
     * get the current page number
     * @return current page number
     */
    int getCurrentPage();
    
    /**
     * Check if has next page
     * @return true, if next page exists in the source
     */
    boolean hasNextPage();
    
    /**
     * Move to next page
     * @return the new page number
     */
    int nextPage();
    
     /**
     * Check if has previous page
     * @return true, if previous page exists in the source
     */
    boolean hasPreviousPage();
    
    
    /**
     * Move to previous page
     * @return the new page number
     */
    int previousPage();
    
    /**
     * Check if has next searchable item
     * @return true, if next item exists in the source
     */
    boolean hasNextItem();
    
    /**
     * Move to next item
     * @return the new item number
     */
    int nextItem();
    
     /**
     * Check if has previous item
     * @return true, if previous item exists in the source
     */
    boolean hasPreviousItem();
    
    
    /**
     * Move to previous item
     * @return the new item number
     */
    int previousItem();
    
    /**
     * Get the current item number, do not change anything
     * @return the current item number
     */
    int currentItem();
    
    
    /**
     * get a map storing which pages have matches to their number, or 0 if unknown
     * @return map storing pages with matches, or null if not supported
     */
    LinkedHashMap<Integer,Integer> getHitsPages();
 
}
