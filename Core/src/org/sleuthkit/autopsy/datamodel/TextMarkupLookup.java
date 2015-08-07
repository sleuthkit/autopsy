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

import org.sleuthkit.datamodel.Content;

/**
 * BC debugging notes: This seems to represent a combination of the content,
 * query, isRegexp and origialQuery that can be passed around. It is used by
 * BlackboardArtifactNode.getHighlightLookup and getLookups to add the markup
 * concept to the node.
 *
 * I find this very confusing. It doesn't seem to need lookup in its current
 * form, but (I think) exists because BlackboardArtifactNode is in Autopsy core
 * and the SOLR highlighter is in KeywordSearch module.
 */
public interface TextMarkupLookup {

    /**
     * Create an instance of the given TextMarkupLookup object.
     *
     * @param objectId        Id of the object (file or artifact) for which to
     *                        get highlights
     * @param keywordHitQuery keyword hit that needs to be highlighted
     * @param isRegex         whether the original query was a regex query
     * @param originalQuery   (regex or literal) that may need to be performed
     *                        again to get all ContentHit results
     *
     * @return
     */
    public TextMarkupLookup createInstance(long objectId, String keywordHitQuery, boolean isRegex, String originalQuery);
}
