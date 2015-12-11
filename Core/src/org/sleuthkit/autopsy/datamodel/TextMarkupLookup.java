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
 * This interface acts as a sort of bridge between the Autopsy Core NetBeans
 * Module (NBM) and the Autopsy KeywordSearch NBM. It is used to get indexed
 * text marked up with HTML to highlight search hits for a particular keyword.
 *
 * Here is an example of how it works. It is used to put highlighted markup into
 * the Lookups of the BlackboardArtifactNodes for keyword search hit artifacts.
 * The BlackboardArtifactNode code that populates the node's Lookup asks the
 * default global Lookup for an instance of TextMarkupLookup. The
 * org.sleuthkit.autopsy.keywordsearch.HighlightedText class is the sole
 * implementation of the interface, so the BlackboardArtifactNode gets a default
 * constructed instance of HighlightedText. This otherwise useless
 * instance is then used to call createInstance with parameters that are used to
 * employ the Solr highlighting capability to create the markup. The
 * TextMarkupLookup object goes in the BlackboardArtifactNode Lookup for later
 * use by the ExtractedContentViewer, a DataContentViewer in the KeywordSearch
 * NBM.
 */
public interface TextMarkupLookup {

    /**
     * Factory method for getting an object that encapsulates indexed text
     * marked up (HTML) to highlight search hits for a particular keyword.
     *
     * @param objectId      ID of the object (file or artifact) that is the
     *                      source of the indexed text.
     * @param keyword       The keyword to be highlighted in the text.
     * @param isRegex       Whether or not the query that follows is a regex.
     * @param originalQuery The query that produces the keyword hit.
     *
     * @return An object that encapsulates indexed text marked up (HTML) to
     *         highlight search hits for a particular keyword.
     */
    public TextMarkupLookup createInstance(long objectId, String keyword, boolean isRegex, String originalQuery);
}
