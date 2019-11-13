/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourceprocessors.xry;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Parses XRY Web-Bookmark files and creates artifacts.
 */
final class XRYWebBookmarksFileParser extends AbstractSingleKeyValueParser {

    //All known XRY keys for web bookmarks.
    private static final Map<String, BlackboardAttribute.ATTRIBUTE_TYPE> KEY_TO_TYPE
            = new HashMap<String, BlackboardAttribute.ATTRIBUTE_TYPE>() {
        {
            put("web address", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL);
            put("domain", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN);
        }
    };

    @Override
    boolean isKey(String key) {
        String normalizedKey = key.toLowerCase();
        return KEY_TO_TYPE.containsKey(normalizedKey);
    }

    @Override
    boolean isNamespace(String nameSpace) {
        //No known namespaces for web reports.
        return false;
    }

    @Override
    BlackboardAttribute makeAttribute(String nameSpace, String key, String value) {
        String normalizedKey = key.toLowerCase();
        return new BlackboardAttribute(KEY_TO_TYPE.get(normalizedKey), PARSER_NAME, value);
    }
    
    @Override
    void makeArtifact(List<BlackboardAttribute> attributes, Content parent) throws TskCoreException {
        BlackboardArtifact artifact = parent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK);
        artifact.addAttributes(attributes);
    }
}
