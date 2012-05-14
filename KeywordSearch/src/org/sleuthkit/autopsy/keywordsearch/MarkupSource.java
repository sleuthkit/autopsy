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

/**
 * Interface to provide HTML markup (to be displayed in ExtractedContentViewer)
 * in a Node's lookup
 */
public interface MarkupSource {

    /**
     * @param pageNum page number to get markup for
     * @return text optionally marked up with the subsest of HTML that Swing
     * components can handle in their setText() method.
     * 
     */
    String getMarkup(int pageNum);
    
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
}
