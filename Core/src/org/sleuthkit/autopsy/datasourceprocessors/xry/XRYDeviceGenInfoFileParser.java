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

    /**
     * Device-General Information reports generally have 2 key value pairs for
     * every blackboard attribute. The two only known keys are "Data" and
     * "Attribute", where data is some generic information that the Attribute
     * key describes.
     *
     * Example:
     *
     * Data:            Nokia XYZ 
     * Attribute:	Device Name
     *
     * This parse implementation assumes that the data field does not span
     * multiple lines. If the data does span multiple lines, then on the next
     * iteration it will log an exception proclaiming a failed attempt to make a
     * 'Data' and 'Attribute' pair.
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
            List<BlackboardAttribute> attributes = getBlackboardAttributes(xryEntity);
            if (!attributes.isEmpty()) {
                //Save the artifact.
                BlackboardArtifact artifact = parent.newArtifact(
                        BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_INFO);
                artifact.addAttributes(attributes);
            }
        }
    }

    /**
     * Parses the XRY entity, extracts key value pairs and creates blackboard
     * attributes from these key value pairs.
     *
     * @param xryEntity XRY entity to parse
     * @return A collection of attributes from the XRY entity.
     */
    private List<BlackboardAttribute> getBlackboardAttributes(String xryEntity) {
        String[] xryLines = xryEntity.split("\n");

        //First line of the entity is the title, the entity will always be non-empty.
        logger.log(Level.INFO, String.format("[XRY DSP] Processing [ %s ]", xryLines[0]));
        List<BlackboardAttribute> attributes = new ArrayList<>();

        //Iterate two lines at a time. For Device-General Information, we generally 
        //need two XRY Key Value pairs per blackboard attribute.
        for (int i = 1; i < xryLines.length; i += 2) {
            if (!XRYKeyValuePair.isPair(xryLines[i])) {
                logger.log(Level.WARNING, String.format("[XRY DSP] Expected a key value "
                        + "pair on this line (in brackets) [ %s ], but one was not detected."
                        + " Discarding...", xryLines[i]));
                continue;
            }

            XRYKeyValuePair firstPair = XRYKeyValuePair.from(xryLines[i]);
            Optional<BlackboardAttribute> attribute = Optional.empty();
            if (i + 1 == xryLines.length) {
                attribute = getBlackboardAttribute(firstPair);
            } else if (XRYKeyValuePair.isPair(xryLines[i + 1])) {
                XRYKeyValuePair secondPair = XRYKeyValuePair.from(xryLines[i + 1]);
                attribute = getBlackboardAttribute(firstPair, secondPair);
            } else {
                logger.log(Level.WARNING, String.format("[XRY DSP] Expected a key value "
                        + "pair on this line (in brackets) [ %s ], but one was not detected."
                        + " Discarding...", xryLines[i+1]));
            }

            if (attribute.isPresent()) {
                attributes.add(attribute.get());
            }
        }
        return attributes;
    }

    /**
     * Creates the appropriate blackboard attribute given a single XRY Key Value
     * pair. It is assumed that the only 'Data' keys can appear by themselves in
     * Device-Gen Info reports. If a Data key is by itself, then it's assumed to
     * be a TSK_PATH attribute.
     * 
     * A WARNING will be logged if this input is not a Data key.
     *
     * @param pair KeyValuePair to 
     * @return
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
     * Creates the appropriate blackboard attribute given the XRY Key Value
     * pairs. If the attribute value is recognized but has no corresponding
     * Blackboard attribute type, the Optional will be empty.
     *
     * A WARNING message will be logged for all recognized attribute values that
     * don't have a type. More data is needed to make a decision about the
     * appropriate type.
     * 
     * @param firstPair
     * @param secondPair
     * @return 
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
        if (!KEY_TO_TYPE.containsKey(normalizedAttributeValue)) {
            logger.log(Level.WARNING, String.format("[XRY DSP] Key value pair "
                    + "(in brackets) [ %s : %s ] was not recognized. Discarding... ", 
                    attributeValue, dataValue));
            return Optional.empty();
        }

        BlackboardAttribute.ATTRIBUTE_TYPE attrType = KEY_TO_TYPE.get(normalizedAttributeValue);
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
