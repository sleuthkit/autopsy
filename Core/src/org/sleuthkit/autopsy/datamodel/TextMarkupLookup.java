/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

/**
 * This interface acts as a sort of circular-dependency-defeating bridge at run
 * time between the Autopsy Core NetBeans Module (NBM) and the Autopsy
 * KeywordSearch NBM. Here is how it works. Code in the Core NBM asks the
 * default global Lookup for an instance of TextMarkupLookup. The
 * org.sleuthkit.autopsy.keywordsearch.HighlightedTextMarkup class is the sole
 * implementation, so the Core code gets a default constructed instance of
 * HighlightedTextMarkup. This otherwise useless instance is then used to call
 * createInstance with parameters that will be used to employ the Solr
 * highlighting capability on text indexed through the KeywordSearch NBM
 * implementation of the KeywordSearchService interface. The Core code then puts
 * that TextMarkupLookup in its Lookup for later use by the
 * ExtractedContentViewer, a DataContentViewer in the KeywordSearch NBM.
 */
public interface TextMarkupLookup {

    /**
     * Creates an instance of a TextMarkupLookup object without knowing its
     * actual type.
     *
     * @param objectId      ID of the object (file or artifact) for which to get
     *                      keyword search indexed text marked up (HTML) to
     *                      highlight a particular keword search hit.
     * @param keyword       The keyword hit to be highlighted.
     * @param isRegex       Whether or not the query that follows is a regex.
     * @param originalQuery The query that produces the indexed text containing
     *                      the keyword to be highlighted.
     *
     * @return An object that encapsulates indexed text marked up (HTML) to
     *         highlight search hits for a particular keyword.
     */
    public TextMarkupLookup createInstance(long objectId, String keyword, boolean isRegex, String originalQuery);
}
