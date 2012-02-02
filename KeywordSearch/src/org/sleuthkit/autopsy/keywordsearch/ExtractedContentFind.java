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

import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Match tracker / find functionality for a given MarkupSource
 * Supports keeping track of matches for multiple sources.
 * What is a match and how to index, is a responsibility of a given MarkupSource
 */
public class ExtractedContentFind {

    private static final Logger logger = Logger.getLogger(ExtractedContentFind.class.getName());

    public ExtractedContentFind() {
        curIndex = new HashMap<MarkupSource, Integer>();
    }
    private HashMap<MarkupSource, Integer> curIndex;
    
    public static final int INDEX_NOT_FOUND = -2;
    public static final int INDEX_INITIALIZED = -1;
    
    /**
     * get total number of matches in the source
     * @param source
     * @return number of matches in the source
     */
    public int getCurrentIndexTotal(MarkupSource source) {
        return source.getNumberHits();
    }

    /**
     * get current match
     * @param source
     * @return current match 
     */
    public int getCurrentIndexI(MarkupSource source) {
        Integer curI = curIndex.get(source);
        if (curI != null) {
            return curI;
        } else {
            return -1;
        }
    }

    /**
     * Check if there is a next match
     * @param source
     * @return true if the source has next match
     */
    public boolean hasNext(MarkupSource source) {
        int total = source.getNumberHits();
        int cur = curIndex.get(source);
        if (total == 0) {
            return false;
        } else if (cur == INDEX_INITIALIZED) {
            return true;
        } else if (cur == total - 1) {
            return false;
        }
        return true;
    }

    /**
     * Check if there is a previous match
     * @param source
     * @return true if the source has previous match
     */
    public boolean hasPrevious(MarkupSource source) {
        int total = source.getNumberHits();
        int cur = curIndex.get(source);
        if (total == 0) {
            return false;
        } else if (cur == INDEX_INITIALIZED) {
            return false;
        } else if (cur == 0) {
            return false;
        }
        return true;
    }

    /**
     * make step toward next match and return the next index
     * or INDEX_NOT_FOUND if no next match
     * @param source
     * @return index corresponding to next match
     */
    public long getNext(MarkupSource source) {
        int total = source.getNumberHits();
        int cur = curIndex.get(source);
        if (total == 0 || cur == total - 1) {
            return INDEX_NOT_FOUND;
        }
        ++cur;
        //update curIndex location
        curIndex.put(source, cur);
        return cur;
    }

    /**
     * make step toward previous match and return the prev. index
     * or INDEX_NOT_FOUND if no next match
     * @param source
     * @return index corresponding to prev. match
     */
    public long getPrevious(MarkupSource source) {
        int total = source.getNumberHits();
        int cur = curIndex.get(source);
        if (total == 0 || cur == 0) {
            return INDEX_NOT_FOUND;
        }
        --cur;
        //update curIndex location
        curIndex.put(source, cur);
        return cur;
    }
    
    /**
     * initialize find functionality for the source
     * @param source MarkupSource to initialize find with
     */
    public void init(MarkupSource source) {
        if (curIndex.get(source) == null)
            curIndex.put(source, INDEX_INITIALIZED);
    }
}
