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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Parses XRY Contacts-Contacts files and creates artifacts.
 */
final class XRYContactsFileParser extends AbstractSingleEntityParser {
    
    private static final Logger logger = Logger.getLogger(XRYContactsFileParser.class.getName());

    //All of the known XRY keys for contacts.
    private static final Map<String, BlackboardAttribute.ATTRIBUTE_TYPE> XRY_KEYS = 
            new HashMap<String, BlackboardAttribute.ATTRIBUTE_TYPE>() {{
        put("name", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME);
        put("tel", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER);
        put("mobile", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_MOBILE);
        put("home", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_HOME);
        put("related application", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME);
        put("address home", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION);
        put("email home", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_HOME);
        put("deleted", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ISDELETED);
        
        //Ignoring or need more information to decide.
        put("storage", null);
        put("other", null);
        put("picture", null);
        put("index", null);
        put("account name", null);
        
    }};
    
    @Override
    boolean canProcess(XRYKeyValuePair pair) {
        String normalizedKey = pair.getKey().toLowerCase();
        return XRY_KEYS.containsKey(normalizedKey);
    }

    @Override
    boolean isNamespace(String nameSpace) {
        //No namespaces are currently known for this report type.
        return false;
    }

    /**
     * Creates the appropriate blackboard attribute given a single XRY Key Value
     * pair. 
     */
    private Optional<BlackboardAttribute> getBlackboardAttribute(XRYKeyValuePair pair) {
        String normalizedKey = pair.getKey().toLowerCase();
        BlackboardAttribute.ATTRIBUTE_TYPE attrType = XRY_KEYS.get(normalizedKey);
        if(attrType != null) {
            return Optional.of(new BlackboardAttribute(attrType, PARSER_NAME, pair.getValue()));
        }

        logger.log(Level.INFO, String.format("[XRY DSP] Key value pair "
               + "(in brackets) [ %s ] was recognized but we need "
               + "more data or time to finish implementation. Discarding... ", 
               pair));
        return Optional.empty();
    }
    
    @Override
    void makeArtifact(List<XRYKeyValuePair> keyValuePairs, Content parent) throws TskCoreException {
        List<BlackboardAttribute> attributes = new ArrayList<>();
        for(XRYKeyValuePair pair : keyValuePairs) {
            Optional<BlackboardAttribute> attribute = getBlackboardAttribute(pair);
            if(attribute.isPresent()) {
                attributes.add(attribute.get());
            }
        }
        if(!attributes.isEmpty()) {
            BlackboardArtifact artifact = parent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT);
            artifact.addAttributes(attributes);
        }
    }
}
