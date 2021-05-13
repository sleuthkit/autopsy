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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Parses XRY Device-General Information files and creates artifacts.
 */
final class XRYDeviceGenInfoFileParser extends AbstractSingleEntityParser {

    private static final Logger logger = Logger.getLogger(XRYDeviceGenInfoFileParser.class.getName());

    //All known XRY keys for Device Gen Info reports.
    private static final String ATTRIBUTE_KEY = "attribute";
    private static final String DATA_KEY = "data";

    //All of the known XRY Attribute values for device gen info. The value of the
    //attribute keys are actionable for this parser.
    //Ex:
    // Data:        Nokia
    // Attribute:   Device Type
    private static final Map<String, BlackboardAttribute.ATTRIBUTE_TYPE> XRY_ATTRIBUTE_VALUES
            = new HashMap<String, BlackboardAttribute.ATTRIBUTE_TYPE>() {
        {
            put("device name", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_NAME);
            put("device type", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MAKE);
            put("mobile id (imei)", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_IMEI);
            put("security code", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PASSWORD);
            put("unlock code", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PASSWORD);
            put("imei/meid", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_IMEI);
            put("model", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MODEL);
            put("wifi address", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MAC_ADDRESS);
            put("subscriber id (imsi)", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_IMSI);

            //There could be two of these on an artifact, not aware of a way
            //to distinguish between two DATE_TIMEs such as the ones below.
            put("device clock", null);
            put("pc clock", null);

            //Ignore these for now, need more data or time to finish implementation.
            put("device family", null);
            put("advertising id", null);
            put("device status", null);
            put("baseband version", null);
            put("sim status", null);
            put("manufacturer", null);
            put("revision", null);
        }
    };
    
    
    @Override
    boolean canProcess(XRYKeyValuePair pair) {
        String key = pair.getKey().trim().toLowerCase();
        return key.equals(DATA_KEY) || key.equals(ATTRIBUTE_KEY);
    }

    @Override
    boolean isNamespace(String nameSpace) {
        //No known namespaces
        return false;
    }
    
    @Override
    void makeArtifact(List<XRYKeyValuePair> keyValuePairs, Content parent, SleuthkitCase currentCase) throws TskCoreException, Blackboard.BlackboardException {
        List<BlackboardAttribute> attributes = new ArrayList<>();
        for(int i = 0; i < keyValuePairs.size(); i+=2) {
            Optional<BlackboardAttribute> attribute;
            if(i + 1 == keyValuePairs.size()) {
                attribute = getBlackboardAttribute(keyValuePairs.get(i));
            } else {
                attribute = getBlackboardAttribute(keyValuePairs.get(i), keyValuePairs.get(i+1));
            }
            if(attribute.isPresent()) {
                attributes.add(attribute.get());
            }
        }
        if(!attributes.isEmpty()) {
            parent.newDataArtifact(BlackboardArtifact.Type.TSK_DEVICE_INFO, attributes);
        }
    }

    /**
     * Creates the appropriate blackboard attribute given a single XRY Key Value
     * pair. It is assumed that only 'Data' keys can appear by themselves.
     * If a Data key is by itself, its value most closely resembles a TSK_PATH attribute.
     */
    private Optional<BlackboardAttribute> getBlackboardAttribute(XRYKeyValuePair pair) {
        if (pair.hasKey(DATA_KEY)) {
            return Optional.of(new BlackboardAttribute(
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH,
                    PARSER_NAME, pair.getValue()));
        }

        logger.log(Level.WARNING, "Expected a 'Data' key value pair, but [ %s ] "
                + "was found.", pair);

        return Optional.empty();
    }

    /**
     * Creates the appropriate blackboard attribute given two XRY Key Value
     * pairs. The expectation is that one pair is the 'Data' key and the other is
     * an 'Attribute' key. If the attribute value is recognized but has no corresponding
     * Blackboard attribute type, the Optional will be empty.
     */
    private Optional<BlackboardAttribute> getBlackboardAttribute(XRYKeyValuePair firstPair, XRYKeyValuePair secondPair) {
        String attributeValue;
        String dataValue;
        if (firstPair.hasKey(DATA_KEY) && secondPair.hasKey(ATTRIBUTE_KEY)) {
            dataValue = firstPair.getValue();
            attributeValue = secondPair.getValue();
        } else if (firstPair.hasKey(ATTRIBUTE_KEY) && secondPair.hasKey(DATA_KEY)) {
            dataValue = secondPair.getValue();
            attributeValue = firstPair.getValue();
        } else {
            logger.log(Level.WARNING, String.format("[XRY DSP] Expected these key value"
                    + " pairs (in brackets) [ %s ], [ %s ] to be an 'Attribute' and 'Data' "
                    + "pair.", firstPair, secondPair));
            return Optional.empty();
        }

        String normalizedAttributeValue = attributeValue.toLowerCase();
        if (!XRY_ATTRIBUTE_VALUES.containsKey(normalizedAttributeValue)) {
            logger.log(Level.WARNING, String.format("[XRY DSP] Key value pair "
                    + "(in brackets) [ %s : %s ] was not recognized. Discarding... ", 
                    attributeValue, dataValue));
            return Optional.empty();
        }

        BlackboardAttribute.ATTRIBUTE_TYPE attrType = XRY_ATTRIBUTE_VALUES.get(normalizedAttributeValue);
        if (attrType == null) {
            logger.log(Level.WARNING, String.format("[XRY DSP] Key value pair "
                    + "(in brackets) [ %s : %s ] was recognized but we need "
                    + "more data or time to finish implementation. Discarding... ", 
                    attributeValue, dataValue));
            return Optional.empty();
        }

        return Optional.of(new BlackboardAttribute(attrType, PARSER_NAME, dataValue));
    }
}