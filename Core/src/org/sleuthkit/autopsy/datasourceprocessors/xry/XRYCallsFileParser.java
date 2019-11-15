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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Parses XRY Calls files and creates artifacts.
 */
final class XRYCallsFileParser extends AbstractSingleKeyValueParser {

    private static final Logger logger = Logger.getLogger(XRYCallsFileParser.class.getName());

    private static final DateTimeFormatter DATE_TIME_PARSER
            = DateTimeFormatter.ofPattern("M/d/y h:m:s [a][ z]");

    private static final String INCOMING = "Incoming";

    //All known XRY keys for call reports.
    private static final Set<String> XRY_KEYS = new HashSet<String>() {
        {
            add("tel");
            add("number");
            add("call type");
            add("name (matched)");
            add("time");
            add("duration");
            add("storage");
            add("index");
        }
    };

    //All known XRY namespaces for call reports.
    private static final Set<String> XRY_NAMESPACES = new HashSet<String>() {
        {
            add("to");
            add("from");
        }
    };

    @Override
    boolean isKey(String key) {
        String normalizedKey = key.toLowerCase();
        return XRY_KEYS.contains(normalizedKey);
    }

    @Override
    boolean isNamespace(String nameSpace) {
        String normalizedNamespace = nameSpace.toLowerCase();
        return XRY_NAMESPACES.contains(normalizedNamespace);
    }

    @Override
    BlackboardAttribute makeAttribute(String nameSpace, String key, String value) {
        String normalizedKey = key.toLowerCase();
        String normalizedNamespace = nameSpace.toLowerCase();

        switch (normalizedKey) {
            case "time":
                //Tranform value to epoch ms
                try {
                    String dateTime = removeDateTimeLocale(value);
                    String normalizedDateTime = dateTime.trim();
                    long dateTimeInEpoch = calculateSecondsSinceEpoch(normalizedDateTime);
                    return new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START, PARSER_NAME, dateTimeInEpoch);
                } catch (DateTimeParseException ex) {
                    logger.log(Level.SEVERE, String.format("[XRY DSP] Assumption about the date time "
                            + "formatting of call logs is not right. Here is the value [ %s ]", value), ex);
                    return null;
                }
            case "duration":
                //Ignore for now.
                return null;
            case "storage":
                //Ignore for now.
                return null;
            case "index":
                //Ignore for now.
                return null;
            case "tel":
                //Apply the namespace
                if(normalizedNamespace.equals("from")) {
                    return new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM, PARSER_NAME, value);
                } else {
                    return new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO, PARSER_NAME, value);
                }
            case "call type":
                String normalizedValue = value.toLowerCase();
                switch (normalizedValue) {
                    case "missed":
                    case "received":
                        return new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION, PARSER_NAME, INCOMING);
                    case "dialed":
                        //Ignore for now.
                        return null;
                    case "last dialed":
                        //Ignore for now.
                        return null;
                    default:
                        logger.log(Level.SEVERE, String.format("Call type (in brackets) [ %s ] not recognized.", value));
                        return null;
                }
            case "number":
                return new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER, PARSER_NAME, value);
            case "name (matched)":
                return new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, PARSER_NAME, value);
            default:
                throw new IllegalArgumentException(String.format("key [ %s ] was not recognized.", key));
        }
    }

    @Override
    void makeArtifact(List<BlackboardAttribute> attributes, Content parent) throws TskCoreException {
        BlackboardArtifact artifact = parent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG);
        artifact.addAttributes(attributes);
    }

    /**
     * Removes the locale from the date time value.
     *
     * Locale in this case being (Device) or (Network).
     *
     * @param dateTime XRY datetime value to be sanitized.
     * @return A purer date time value.
     */
    private String removeDateTimeLocale(String dateTime) {
        int index = dateTime.indexOf('(');
        if (index == -1) {
            return dateTime;
        }

        return dateTime.substring(0, index);
    }

    /**
     * Parses the date time value and calculates ms since epoch. The time zone is
     * assumed to be UTC.
     *
     * @param dateTime
     * @return
     */
    private long calculateSecondsSinceEpoch(String dateTime) {
        LocalDateTime localDateTime = LocalDateTime.parse(dateTime, DATE_TIME_PARSER);
        //Assume dates have no offset.
        return localDateTime.toInstant(ZoneOffset.UTC).getEpochSecond();
    }
}
