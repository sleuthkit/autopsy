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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 * result of writing keyword search result to blackboard (cached artifact and
 * attributes) This is mainly to cache the attributes, so that we don't query
 * the DB to get them back again.
 */
class KeywordCachedArtifact {

    private BlackboardArtifact artifact;
    private Map<Integer, BlackboardAttribute> attributes;

    KeywordCachedArtifact(BlackboardArtifact artifact) {
        this.artifact = artifact;
        attributes = new HashMap<Integer, BlackboardAttribute>();
    }

    BlackboardArtifact getArtifact() {
        return artifact;
    }

    Collection<BlackboardAttribute> getAttributes() {
        return attributes.values();
    }

    BlackboardAttribute getAttribute(Integer attrTypeID) {
        return attributes.get(attrTypeID);
    }

    void add(BlackboardAttribute attribute) {
        attributes.put(attribute.getAttributeType().getTypeID(), attribute);
    }

    void add(Collection<BlackboardAttribute> attributes) {
        for (BlackboardAttribute attr : attributes) {
            this.attributes.put(attr.getAttributeType().getTypeID(), attr);
        }
    }
}
