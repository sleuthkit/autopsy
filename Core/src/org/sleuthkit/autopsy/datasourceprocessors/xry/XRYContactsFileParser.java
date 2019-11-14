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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Parses XRY Contacts-Contacts files and creates artifacts.
 */
final class XRYContactsFileParser extends AbstractSingleKeyValueParser {

    //All of the known XRY keys for contacts.
    private static final Set<String> XRY_KEYS = new HashSet<String>() {{
        add("name");
        add("tel");
        add("storage");
    }};

    @Override
    boolean isKey(String key) {
        String normalizedKey = key.toLowerCase();
        return XRY_KEYS.contains(normalizedKey);
    }

    @Override
    boolean isNamespace(String nameSpace) {
        //No namespaces are currently known for this report type.
        return false;
    }

    @Override
    BlackboardAttribute makeAttribute(String nameSpace, String key, String value) {
        String normalizedKey = key.toLowerCase();
        switch(normalizedKey) {
            case "name":
                return new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, PARSER_NAME, value);
            case "tel":
                return new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER, PARSER_NAME, value);
            case "storage":
                //Ignore for now.
                return null;
            default:
                throw new IllegalArgumentException(String.format("Key [ %s ] was not recognized", key));
        }
    }
    
    @Override
    void makeArtifact(List<BlackboardAttribute> attributes, Content parent) throws TskCoreException {
        BlackboardArtifact artifact = parent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT);
        artifact.addAttributes(attributes);
    }
}
