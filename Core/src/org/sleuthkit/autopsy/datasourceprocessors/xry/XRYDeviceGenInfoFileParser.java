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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Parses XRY Device-General Information files and creates artifacts.
 */
final class XRYDeviceGenInfoFileParser implements XRYFileParser {
    
    private static final Logger logger = Logger.getLogger(XRYDeviceGenInfoFileParser.class.getName());

    //Human readable name of this parser.
    private static final String PARSER_NAME = "XRY DSP";
    private static final char KEY_VALUE_DELIMITER = ':';
    
    //All known XRY keys for Device Gen Info reports.
    private static final String ATTRIBUTE_KEY = "attribute";
    private static final String DATA_KEY = "data";

    //All of the known XRY Attribute values for device gen info. The value of the
    //attribute keys are actionable for this parser. See parse header for more
    //details.
    private static final Map<String, BlackboardAttribute.ATTRIBUTE_TYPE> KEY_TO_TYPE
            = new HashMap<String, BlackboardAttribute.ATTRIBUTE_TYPE>() {
        {
            put("device name", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_NAME);
            put("device family", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MODEL);
            put("device type", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MAKE);
            put("mobile id (imei)", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_IMEI);
            put("security code", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PASSWORD);
        }
    };

    /**
     * Device-General Information reports have 2 key value pairs for every
     * attribute. The two only known keys are "Data" and "Attribute", where data
     * is some generic information that the Attribute key describes.
     *
     * Example:
     *
     * Data:	Nokia XYZ 
     * Attribute:	Device Name
     *
     * This parse implementation assumes that the data field does not span
     * multiple lines. If the data does span multiple lines, it will log an
     * error describing an expectation for an "Attribute" key that is not found.
     *
     * @param reader The XRYFileReader that reads XRY entities from the
     * Device-General Information report.
     * @param parent The parent Content to create artifacts from.
     * @throws IOException If an I/O error is encountered during report reading
     * @throws TskCoreException If an error during artifact creation is encountered.
     */
    @Override
    public void parse(XRYFileReader reader, Content parent) throws IOException, TskCoreException {
        Path reportPath = reader.getReportPath();
        logger.log(Level.INFO, String.format("[XRY DSP] Processing report at [ %s ]", reportPath.toString()));

        while (reader.hasNextEntity()) {
            String xryEntity = reader.nextEntity();
            String[] xryLines = xryEntity.split("\n");

            List<BlackboardAttribute> attributes = new ArrayList<>();

            //First line of the entity is the title.
            if (xryLines.length > 0) {
                logger.log(Level.INFO, String.format("[XRY DSP] Processing [ %s ]", xryLines[0]));
            }

            for (int i = 1; i < xryLines.length; i++) {
                String xryLine = xryLines[i];

                //Expecting to see a "Data" key.
                if (!hasDataKey(xryLine)) {
                    logger.log(Level.SEVERE, String.format("[XRY DSP] Expected a 'Data' key "
                            + "on this line (in brackets) [ %s ], but none was found. "
                            + "Discarding... Here is the previous line for context [ %s ]. "
                            + "What does this mean?", xryLine, xryLines[i - 1]));
                    continue;
                }

                if (i + 1 == xryLines.length) {
                    logger.log(Level.SEVERE, String.format("[XRY DSP] Found a 'Data' key "
                            + "but no corresponding 'Attribute' key. Discarding... Here "
                            + "is the 'Data' line (in brackets) [ %s ]. Here is the previous "
                            + "line for context [ %s ]. What does this mean?", xryLine, xryLines[i - 1]));
                    continue;
                }
                
                int dataKeyIndex = xryLine.indexOf(KEY_VALUE_DELIMITER);
                String dataValue = xryLine.substring(dataKeyIndex + 1).trim();

                String nextXryLine = xryLines[++i];

                //Expecting to see an "Attribute" key
                if (!hasAttributeKey(nextXryLine)) {
                    logger.log(Level.SEVERE, String.format("[XRY DSP] Expected an 'Attribute' "
                            + "key on this line (in brackets) [ %s ], but none was found. "
                            + "Discarding... Here is the previous line for context [ %s ]. "
                            + "What does this mean?", nextXryLine, xryLine));
                    continue;
                }

                int attributeKeyIndex = nextXryLine.indexOf(KEY_VALUE_DELIMITER);
                String attributeValue = nextXryLine.substring(attributeKeyIndex + 1).trim();
                String normalizedAttributeValue = attributeValue.toLowerCase();

                //Check if the attribute value is recognized.
                if (KEY_TO_TYPE.containsKey(normalizedAttributeValue)) {
                    //All of the attribute types in the map expect a string.
                    attributes.add(new BlackboardAttribute(KEY_TO_TYPE.get(normalizedAttributeValue), PARSER_NAME, dataValue));
                } else {
                    logger.log(Level.SEVERE, String.format("[XRY DSP] Attribute type (in brackets) "
                            + "[ %s ] was not recognized. Discarding... Here is the "
                            + "previous line for context [ %s ]. What does this mean?", nextXryLine, xryLine));
                }
            }

            if(!attributes.isEmpty()) {
                //Build the artifact.
                BlackboardArtifact artifact = parent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_INFO);
                artifact.addAttributes(attributes);
            }
        }
    }

    /**
     * Determines if the XRY line has a data key on it.
     *
     * @param xryLine
     * @return
     */
    private boolean hasDataKey(String xryLine) {
        int dataKeyIndex = xryLine.indexOf(KEY_VALUE_DELIMITER);
        //No key structure found.
        if (dataKeyIndex == -1) {
            return false;
        }

        String normalizedDataKey = xryLine.substring(0,
                dataKeyIndex).trim().toLowerCase();
        return normalizedDataKey.equals(DATA_KEY);
    }

    /**
     * Determines if the XRY line has an attribute key on it.
     *
     * @param xryLine
     * @return
     */
    private boolean hasAttributeKey(String xryLine) {
        int attributeKeyIndex = xryLine.indexOf(KEY_VALUE_DELIMITER);
        //No key structure found.
        if (attributeKeyIndex == -1) {
            return false;
        }

        String normalizedDataKey = xryLine.substring(0,
                attributeKeyIndex).trim().toLowerCase();
        return normalizedDataKey.equals(ATTRIBUTE_KEY);
    }
}
