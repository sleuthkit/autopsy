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
 * Paging tracker / find functionality for a given content
 * Supports keeping track of paging for multiple contents.
 */
public class ExtractedContentPaging {

    static class PageInfo {

        PageInfo(int total) {
            this.total = total;
            if (this.total == 0) {
                //no chunks
                this.current = 0;
            }
            else {
                this.current = 1;
            }
        }
        int current;
        int total;
    }
    private static final Logger logger = Logger.getLogger(ExtractedContentPaging.class.getName());

    public ExtractedContentPaging() {
        sources = new HashMap<MarkupSource, PageInfo>();
    }
    //maps markup source to page info being tracked
    private HashMap<MarkupSource, PageInfo> sources;

    /**
     * add pages tracking for the content
     * needs to be called first for each content
     * @param source
     * @param totalPages 
     */
    void add(MarkupSource source, int totalPages) {
        sources.put(source, new PageInfo(totalPages));
    }

    /**
     * check if the source paging if currently being tracked
     * @param contentID content to check for
     * @return true if it is being tracked already
     */
    boolean isTracked(MarkupSource source) {
        return sources.containsKey(source);
    }

    /**
     * get total number of pages in the source
     * @param contentID content to check for
     * @return number of matches in the source
     */
    int getTotalPages(MarkupSource source) {
        if (!isTracked(source)) {
            throw new IllegalStateException("Source is not being tracked");
        }
        return sources.get(source).total;
    }

    /**
     * get current page
     * @param contentID content to check for
     * @return current page 
     */
    int getCurrentPage(MarkupSource source) {
        if (!isTracked(source)) {
            throw new IllegalStateException("Source is not being tracked");
        }
        return sources.get(source).current;
    }

    /**
     * Check if there is a next page
     * @param contentID content to check for
     * @return true if the source has next page
     */
    boolean hasNext(MarkupSource source) {
        if (!isTracked(source)) {
            throw new IllegalStateException("Source is not being tracked");
        }
        PageInfo info = sources.get(source);
        return info.current < info.total;
    }

    /**
     * Check if there is a previous page
     * @param contentID content to check for
     * @return true if the source has previous page
     */
    boolean hasPrevious(MarkupSource source) {
        if (!isTracked(source)) {
            throw new IllegalStateException("Source is not being tracked");
        }
        PageInfo info = sources.get(source);
        return info.current > 1;
    }

    /**
     * make step toward next page
     * requires call to hasNext() first
     * @param contentID content to check for
     * 
     */
    void next(MarkupSource source) {
        if (!isTracked(source)) {
            throw new IllegalStateException("Source is not being tracked");
        }
        sources.get(source).current++;
    }

    /**
     * make step toward previous page
     * requires call to hasPrevious() first
     * @param source 
     * 
     */
    void previous(MarkupSource source) {
        if (!isTracked(source)) {
            throw new IllegalStateException("Source is not being tracked");
        }
        sources.get(source).current--;
    }
}
