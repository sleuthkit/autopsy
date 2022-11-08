/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard.BlackboardException;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Parses XRY Web-Bookmark files and creates artifacts.
 */
final class XRYWebBookmarksFileParser extends AbstractSingleEntityParser {

    //All known XRY keys for web bookmarks.
    private static final Map<String, BlackboardAttribute.ATTRIBUTE_TYPE> XRY_KEYS
            = new HashMap<String, BlackboardAttribute.ATTRIBUTE_TYPE>() {
        {
            put("web address", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL);
            put("domain", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN);
            put("application", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME);
        }
    };

    @Override
    boolean canProcess(XRYKeyValuePair pair) {
        String normalizedKey = pair.getKey().toLowerCase();
        return XRY_KEYS.containsKey(normalizedKey);
    }

    @Override
    boolean isNamespace(String nameSpace) {
        //No known namespaces for web reports.
        return false;
    }

    /**
     * Creates the appropriate blackboard attribute given a single XRY Key Value
     * pair.
     */
    private Optional<BlackboardAttribute> getBlackboardAttribute(XRYKeyValuePair pair) {
        String normalizedKey = pair.getKey().toLowerCase();
        return Optional.of(new BlackboardAttribute(
                XRY_KEYS.get(normalizedKey), 
                PARSER_NAME, pair.getValue()));
    }
    
    @Override
    void makeArtifact(List<XRYKeyValuePair> keyValuePairs, Content parent, SleuthkitCase currentCase) throws TskCoreException, BlackboardException {
        List<BlackboardAttribute> attributes = new ArrayList<>();
        for(XRYKeyValuePair pair : keyValuePairs) {
            Optional<BlackboardAttribute> attribute = getBlackboardAttribute(pair);
            if(attribute.isPresent()) {
                attributes.add(attribute.get());
            }
        }
        if(!attributes.isEmpty()) {
            parent.newDataArtifact(BlackboardArtifact.Type.TSK_WEB_BOOKMARK, attributes);
        }
    }
}