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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.StringEscapeUtils;


public class ExtractedContentFind {
    
    private static final Logger logger = Logger.getLogger(ExtractedContentFind.class.getName());
    
    ExtractedContentFind(ExtractedContentViewer viewer) {
        this.viewer = viewer;
        findIndex = new HashMap<MarkupSource, ArrayList<Long>>();
        curIndex = new HashMap<MarkupSource, Integer>();
    }
    
    private HashMap<MarkupSource, ArrayList<Long>>findIndex;
    private HashMap<MarkupSource, Integer>curIndex;
    private ExtractedContentViewer viewer;
    
    public static int INDEX_INITIALIZED = -1;
    public static int INDEX_NOT_FOUND = -2;
    
    public int getCurrentIndexTotal(MarkupSource source) {
        ArrayList<Long> index = indexSource(source);
        return index.size();
    }
    
    public int getCurrentIndexI(MarkupSource source) {
        indexSource(source);
        Integer curI = curIndex.get(source);
        if (curI != null)
            return curI;
        else return -1;      
    }
    
    /**
     * get next line number corresponding to indexed match, no wrapping
     * requires call to hasNext() first
     * or INDEX_NOT_FOUND if no next hit
     * @param source
     * @return line number where match occurs
     */
    public long getNext(MarkupSource source) {
        ArrayList<Long> index = indexSource(source);
        int total = index.size();
        int cur = curIndex.get(source);
        if (total == 0 || cur == total -1) return INDEX_NOT_FOUND;
        ++cur;
        //update curIndex location
        curIndex.put(source, cur);
        return index.get(cur);
    }
    
    /**
     * 
     * @param source
     * @return true if the source has next hit
     */
    public boolean hasNext(MarkupSource source) {
        ArrayList<Long> index = indexSource(source);
        int total = index.size();
        int cur = curIndex.get(source);
        if (total == 0) return false;
        else if (cur == INDEX_INITIALIZED) return true;
        else if (cur == total - 1)
            return false;
        return true;
    }
    
    /**
     * 
     * @param source
     * @return true if the source has previous hit
     */
    public boolean hasPrevious(MarkupSource source) {
        ArrayList<Long> index = indexSource(source);
        int total = index.size();
        int cur = curIndex.get(source);
        if (total == 0) return false;
        else if (cur == INDEX_INITIALIZED) return false;
        else if (cur == 0) return false;
        return true;
    }
    
       /**
     * get previous line number corresponding to indexed match, no wrapping
     * requires call to hasPrevious() first
     * or INDEX_NOT_FOUND if no previous hit
     * @param source
     * @return line number where match occurs
     */
    public long getPrevious(MarkupSource source) {
       ArrayList<Long> index = indexSource(source);
        int total = index.size();
        int cur = curIndex.get(source);
        if (total == 0 || cur == 0) return INDEX_NOT_FOUND;
        --cur;
        //update curIndex location
        curIndex.put(source, cur);
        return index.get(cur);    
    }
    
    /**
     * Add MarkupSource to find functionality, or return if already exists for that source.
     * @param source MarkupSource to add to find
     */
    private ArrayList<Long> indexSource(MarkupSource source) {
        //return if already indexed
        ArrayList<Long> indexed = findIndex.get(source);
        if (indexed != null || source.isSearchable() == false)
            return indexed;
        
        indexed = new ArrayList<Long>();
        String markup = source.getMarkup();   

        //logger.log(Level.INFO,markup); 
        final String indexSearchTok = source.getSearchToken();
        if (indexSearchTok == null || indexSearchTok.equals("")) {
            return indexed;
        }
        final int indexSearchTokLen = indexSearchTok.length();
        long docOffset = 0;
        long index = -1;
       
        while ((index = markup.indexOf(indexSearchTok, (int)docOffset)) >= 0) {
            //TODO check if (int) cast above presents limitation for large files
            
            //calculate and store index stripping all markup for scrolling to work properly
            //need to map index to content with no html
            //try cheat: compensata fot highlight tags (might be other things, such as escape chars)
            //perfectly we'd scan both documents at same time and map index from one to another
            indexed.add(index);
            docOffset = index + indexSearchTokLen; //next offset past the keyword
        }     
        //add indices to index collection
        findIndex.put(source, indexed);
        //add current for tracking
        curIndex.put(source, INDEX_INITIALIZED);
        
        return indexed;
    }
    
    
}
