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

public class ExtractedContentFind {

    private static final Logger logger = Logger.getLogger(ExtractedContentFind.class.getName());

    public ExtractedContentFind() {
        curIndex = new HashMap<MarkupSource, Integer>();
    }
    private HashMap<MarkupSource, Integer> curIndex;
    
    public static final int INDEX_NOT_FOUND = -2;
    public static final int INDEX_INITIALIZED = -1;
    
    public int getCurrentIndexTotal(MarkupSource source) {
        return source.getNumberHits();
    }

    public int getCurrentIndexI(MarkupSource source) {
        Integer curI = curIndex.get(source);
        if (curI != null) {
            return curI;
        } else {
            return -1;
        }
    }

    /**
     * 
     * @param source
     * @return true if the source has next hit
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
     * 
     * @param source
     * @return true if the source has previous hit
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
     * get next index
     * requires call to hasNext() first
     * or INDEX_NOT_FOUND if no next hit
     * @param source
     * @return line number where match occurs
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
     * get previous index
     * requires call to hasPrevious() first
     * or INDEX_NOT_FOUND if no previous hit
     * @param source
     * @return line number where match occurs
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
     * initialize find functionality with the source
     * @param source MarkupSource to initialize find with
     */
    public void init(MarkupSource source) {
        if (curIndex.get(source) == null)
            curIndex.put(source, INDEX_INITIALIZED);
    }
}
