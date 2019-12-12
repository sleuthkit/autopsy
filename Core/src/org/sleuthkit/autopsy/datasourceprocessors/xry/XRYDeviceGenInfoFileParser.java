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
import java.util.Optional;
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
    //attribute keys are actionable for this parser. See parse() header for more
    //details.
    private static final Map<String, BlackboardAttribute.ATTRIBUTE_TYPE> KEY_TO_TYPE
            = new HashMap<String, BlackboardAttribute.ATTRIBUTE_TYPE>() {
        {
            put("device name", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_NAME);
            put("device type", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MAKE);
            put("mobile id (imei)", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_IMEI);
            put("security code", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PASSWORD);
            put("imei/meid", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_IMEI);
            put("model", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MODEL);
            put("wifi address", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MAC_ADDRESS);

            //There could be two of these on an artifact, not aware of a way
            //to distinguish between two DATE_TIMEs such as the ones below.
            put("device clock", null);
            put("pc clock", null);

            //Ignore these for now, need more data.
            put("device family", null);
            put("advertising id", null);
            put("device status", null);
            put("baseband version", null);
            put("sim status", null);
            put("manufacturer", null);
            put("revision", null);
        }
    };

    /**
     * Device-General Information reports have 2 key value pairs for every
     * attribute. The two only known keys are "Data" and "Attribute", where data
     * is some generic information that the Attribute key describes.
     *
     * Example:
     *
     * Data:	        Nokia XYZ 
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
     * @throws TskCoreException If an error during artifact creation is
     * encountered.
     */
    @Override
    public void parse(XRYFileReader reader, Content parent) throws IOException, TskCoreException {
        Path reportPath = reader.getReportPath();
        logger.log(Level.INFO, String.format("[XRY DSP] Processing report at [ %s ]", reportPath.toString()));

        while (reader.hasNextEntity()) {
            String xryEntity = reader.nextEntity();
            //Extract attributes from this entity.
            List<BlackboardAttribute> attributes = createTSKAttributes(xryEntity);
            if (!attributes.isEmpty()) {
                //Save the artifact.
                BlackboardArtifact artifact = parent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_INFO);
                artifact.addAttributes(attributes);
            }
        }
    }

    /**
     * Parses the XRY entity, extracts key value pairs and creates blackboard
     * attributes from these key value pairs.
     * 
     * @param xryEntity
     * @return A collection of attributes from the XRY entity.
     */
    private List<BlackboardAttribute> createTSKAttributes(String xryEntity) {
        //Examine this XRY entity line by line.
        String[] xryLines = xryEntity.split("\n");
        List<BlackboardAttribute> attributes = new ArrayList<>();

        //First line of the entity is the title, the entity will always be non-empty.
        logger.log(Level.INFO, String.format("[XRY DSP] Processing [ %s ]", xryLines[0]));

        for (int i = 1; i < xryLines.length; i++) {
            String xryLine = xryLines[i];

            //Expecting to see a "Data" key.
            if (!hasDataKey(xryLine)) {
                logger.log(Level.WARNING, String.format("[XRY DSP] Expected a "
                        + "'Data' key on this line (in brackets) [ %s ], but none "
                        + "was found. Discarding... Here is the previous line for"
                        + " context [ %s ]. What does this mean?", 
                        xryLine, xryLines[i - 1]));
                continue;
            }

            int dataKeyIndex = xryLine.indexOf(KEY_VALUE_DELIMITER);
            String dataValue = xryLine.substring(dataKeyIndex + 1).trim();

            /**
             * If there is only a Data key in the XRY Entity, then assume it is
             * the path to the device.
             */
            if (i + 1 == xryLines.length) {
                attributes.add(new BlackboardAttribute(
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH,
                        PARSER_NAME, dataValue));
                continue;
            }

            String nextXryLine = xryLines[++i];

            //Expecting to see an "Attribute" key
            if (!hasXRYAttributeKey(nextXryLine)) {
                logger.log(Level.WARNING, String.format("[XRY DSP] Expected an "
                        + "'Attribute' key on this line (in brackets) [ %s ], "
                        + "but none was found. Discarding... Here is the previous "
                        + "line for context [ %s ]. What does this mean?", 
                        nextXryLine, xryLine));
                continue;
            }

            int attributeKeyIndex = nextXryLine.indexOf(KEY_VALUE_DELIMITER);
            String attributeValue = nextXryLine.substring(attributeKeyIndex + 1).trim();
            String normalizedAttributeValue = attributeValue.toLowerCase();

            //Check if this value is known.
            if (!isXRYAttributeValueRecognized(normalizedAttributeValue)) {
                logger.log(Level.WARNING, String.format("[XRY DSP] Attribute value "
                        + "(in brackets) [ %s ] was not recognized. Discarding... "
                        + "Here is the data field for context [ %s ]. "
                        + "What does this mean?", attributeValue, dataValue));
                continue;
            }

            Optional<BlackboardAttribute> attribute = createTSKAttribute(
                    normalizedAttributeValue, dataValue);
            if (attribute.isPresent()) {
                attributes.add(attribute.get());
            }
        }
        return attributes;
    }

    /**
     * Creates the appropriate blackboard attribute given the XRY Key Value pair.
     * If the value is recognized but has no corresponding Blackboard
     * attribute type, the Optional will be empty.
     *
     * A WARNING message will be logged for all recognized values that don't have
     * a type. More data is needed to make a decision about the appropriate type.
     *
     * @param normalizedAttributeValue Normalized (trimmed and lowercased)
     * attribute value to map.
     * @param dataValue The value of the blackboard attribute.
     * @return Corresponding BlackboardAttribute, if any.
     */
    private Optional<BlackboardAttribute> createTSKAttribute(
            String normalizedAttributeValue, String dataValue) {
        BlackboardAttribute.ATTRIBUTE_TYPE attrType = KEY_TO_TYPE.get(normalizedAttributeValue);
        if (attrType == null) {
            logger.log(Level.WARNING, String.format("[XRY DSP] Key [%s] was "
                    + "recognized but more examples of its values are needed "
                    + "to make a decision on an appropriate TSK attribute. "
                        + "Here is the value [%s].", normalizedAttributeValue, dataValue));
            return Optional.empty();
        }

        return Optional.of(new BlackboardAttribute(attrType, PARSER_NAME, dataValue));
    }

    /**
     * Tests if the attribute value is a recognized type.
     *
     * @param normalizedAttributeValue Normalized (trimmed and lowercased) value
     * to test.
     * @return True if the attribute value is known, False otherwise.
     */
    private boolean isXRYAttributeValueRecognized(String normalizedAttributeValue) {
        return KEY_TO_TYPE.containsKey(normalizedAttributeValue);
    }

    /**
     * Tests if the XRY line has a data key on it.
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
     * Tests if the XRY line has an attribute key on it.
     *
     * @param xryLine
     * @return
     */
    private boolean hasXRYAttributeKey(String xryLine) {
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
