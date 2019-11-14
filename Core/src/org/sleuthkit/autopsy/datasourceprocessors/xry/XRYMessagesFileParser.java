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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Parses Messages-SMS files and creates artifacts.
 */
final class XRYMessagesFileParser implements XRYFileParser {

    private static final Logger logger = Logger.getLogger(
            XRYMessagesFileParser.class.getName());
    
    private static final String PARSER_NAME = "XRY DSP";
    private static final char KEY_VALUE_DELIMITER = ':';
    private static final DateTimeFormatter DATE_TIME_PARSER
            = DateTimeFormatter.ofPattern("M/d/y h:m:s [a][ z]");

    //Meta keys. These describe how the XRY message entites are split 
    //up in the report file.
    private static final String SEGMENT_COUNT = "segments";
    private static final String SEGMENT_NUMBER = "segment number";
    private static final String REFERENCE_NUMBER = "reference number";

    //A more readable version of these values. Referring to if the user
    //has read the message.
    private static final int READ = 1;
    private static final int UNREAD = 0;

    private static final String TEXT_KEY = "text";

    //All known XRY keys for message reports.
    private static final Set<String> XRY_KEYS = new HashSet<String>() {
        {
            add(TEXT_KEY);
            add("direction");
            add("time");
            add("status");
            add("tel");
            add("storage");
            add("index");
            add("folder");
            add("service center");
            add("type");
            add("name");
        }
    };

    //All known XRY namespaces for message reports.
    private static final Set<String> XRY_NAMESPACES = new HashSet<String>() {
        {
            add("to");
            add("from");
            add("participant");
        }
    };

    //All known meta keys.
    private static final Set<String> XRY_META_KEYS = new HashSet<String>() {
        {
            add(REFERENCE_NUMBER);
            add(SEGMENT_NUMBER);
            add(SEGMENT_COUNT);
        }
    };

    /**
     * Message-SMS report artifacts can span multiple XRY entities and their
     * attributes can span multiple lines. The "Text" key is the only known key
     * value pair that can span multiple lines. Messages can be segmented,
     * meaning that their "Text" content can appear in multiple XRY entities.
     * Our goal for a segmented message is to aggregate all of the text pieces and
     * create 1 artifact.
     *
     * This parse implementation assumes that segments are contiguous and that
     * they ascend incrementally. There are checks in place to verify this
     * assumption is correct, otherwise an error will appear in the logs.
     *
     * @param reader The XRYFileReader that reads XRY entities from the
     * Message-SMS report.
     * @param parent The parent Content to create artifacts from.
     * @throws IOException If an I/O error is encountered during report reading
     * @throws TskCoreException If an error during artifact creation is
     * encountered.
     */
    @Override
    public void parse(XRYFileReader reader, Content parent) throws IOException, TskCoreException {
        Path reportPath = reader.getReportPath();
        logger.log(Level.INFO, String.format("[XRY DSP] Processing report at [ %s ]", reportPath.toString()));

        //Keep track of the reference numbers that have been parsed.
        Set<Integer> referenceNumbersSeen = new HashSet<>();

        while (reader.hasNextEntity()) {
            String xryEntity = reader.nextEntity();
            String[] xryLines = xryEntity.split("\n");

            //First line of the entity is the title.
            if (xryLines.length > 0) {
                logger.log(Level.INFO, String.format("[XRY DSP] Processing [ %s ]", xryLines[0]));
            }

            List<BlackboardAttribute> attributes = new ArrayList<>();

            String namespace = "";
            for (int i = 1; i < xryLines.length; i++) {
                String xryLine = xryLines[i];
                String candidateNamespace = xryLine.trim().toLowerCase();

                if (XRY_NAMESPACES.contains(candidateNamespace)) {
                    namespace = xryLine.trim();
                    continue;
                }

                //Find the XRY key on this line.
                int keyDelimiter = xryLine.indexOf(KEY_VALUE_DELIMITER);
                if (keyDelimiter == -1) {
                    logger.log(Level.SEVERE, String.format("[XRY DSP] Expected a key value "
                            + "pair on this line (in brackets) [ %s ], but one was not detected."
                            + " Is this the continuation of a previous line?"
                            + " Here is the previous line (in brackets) [ %s ]. "
                            + "What does this key mean?", xryLine, xryLines[i - 1]));
                    continue;
                }

                //Extract the key value pair
                String key = xryLine.substring(0, keyDelimiter).trim();
                String value = xryLine.substring(keyDelimiter + 1).trim();

                String normalizedKey = key.toLowerCase();

                if (XRY_META_KEYS.contains(normalizedKey)) {
                    //Skip meta keys, they are being dealt with seperately.
                    continue;
                }

                if (!XRY_KEYS.contains(normalizedKey)) {
                    logger.log(Level.SEVERE, String.format("[XRY DSP] The following key, "
                            + "value pair (in brackets, respectively) [ %s ], [ %s ] "
                            + "was not recognized. Discarding... Here is the previous line "
                            + "[ %s ] for context. What does this key mean?", key, value, xryLines[i - 1]));
                    continue;
                }

                if (value.isEmpty()) {
                    logger.log(Level.SEVERE, String.format("[XRY DSP] The following key "
                            + "(in brackets) [ %s ] was recognized, but the value "
                            + "was empty. Discarding... Here is the previous line "
                            + "for context [ %s ]. Is this a continuation of this line? "
                            + "What does an empty key mean?", key, xryLines[i - 1]));
                    continue;
                }

                //Assume text is the only field that can span multiple lines.
                if (normalizedKey.equals(TEXT_KEY)) {
                    //Build up multiple lines.
                    for (; (i + 1) < xryLines.length
                            && !hasKey(xryLines[i + 1])
                            && !hasNamespace(xryLines[i + 1]); i++) {
                        String continuedValue = xryLines[i + 1].trim();
                        //Assume multi lined values are split by word.
                        value = value + " " + continuedValue;
                    }

                    int referenceNumber = getMetaInfo(xryLines, REFERENCE_NUMBER);
                    //Check if there is any segmented text. Min val is used to 
                    //signify that no reference number was found.
                    if (referenceNumber != Integer.MIN_VALUE) {
                        logger.log(Level.INFO, String.format("[XRY DSP] Message entity "
                                + "appears to be segmented with reference number [ %d ]", referenceNumber));

                        if (referenceNumbersSeen.contains(referenceNumber)) {
                            logger.log(Level.SEVERE, String.format("[XRY DSP] This reference [ %d ] has already "
                                    + "been seen. This means that the segments are not "
                                    + "contiguous. Any segments contiguous with this "
                                    + "one will be aggregated and another "
                                    + "(otherwise duplicate) artifact will be created.", referenceNumber));
                        }

                        referenceNumbersSeen.add(referenceNumber);

                        int segmentNumber = getMetaInfo(xryLines, SEGMENT_NUMBER);

                        //Unify segmented text, if there is any.
                        String segmentedText = getSegmentedText(referenceNumber,
                                segmentNumber, reader);
                        //Assume it was segmented by word.
                        value = value + " " + segmentedText;
                    }
                }

                BlackboardAttribute attribute = makeAttribute(namespace, key, value);
                if (attribute != null) {
                    attributes.add(attribute);
                }
            }

            //Only create artifacts with non-empty attributes.
            if(!attributes.isEmpty()) {
                BlackboardArtifact artifact = parent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE);
                artifact.addAttributes(attributes);
            }
        }
    }

    /**
     * Builds up segmented message entities so that the text is unified in the 
     * artifact.
     * 
     * @param referenceNumber Reference number that messages are group by
     * @param segmentNumber Segment number of the starting segment.
     * @param reader
     * @return
     * @throws IOException
     */
    private String getSegmentedText(int referenceNumber, int segmentNumber, XRYFileReader reader) throws IOException {
        StringBuilder segmentedText = new StringBuilder();

        int currentSegmentNumber = segmentNumber;
        while (reader.hasNextEntity()) {
            //Peek at the next to see if it has the same reference number.
            String nextEntity = reader.peek();
            String[] nextEntityLines = nextEntity.split("\n");
            int nextReferenceNumber = getMetaInfo(nextEntityLines, REFERENCE_NUMBER);

            if (nextReferenceNumber != referenceNumber) {
                //Don't consume the next entity. It is not related
                //to the current message thread.
                break;
            }

            //Consume the entity.
            reader.nextEntity();

            int nextSegmentNumber = getMetaInfo(nextEntityLines, SEGMENT_NUMBER);

            //Extract the text key from the entity, which is potentially
            //multi-lined.
            if (nextEntityLines.length > 0) {
                logger.log(Level.INFO, String.format("[XRY DSP] Processing [ %s ] "
                        + "segment with reference number [ %d ]", nextEntityLines[0], referenceNumber));
            }

            if(nextSegmentNumber == Integer.MIN_VALUE) {
                logger.log(Level.SEVERE, String.format("[XRY DSP] Segment with reference"
                        + " number [ %d ] did not have a segment number associated with it."
                        + " It cannot be determined if the reconstructed text will be in order.", referenceNumber));
            } else if (nextSegmentNumber != currentSegmentNumber + 1) {
                logger.log(Level.SEVERE, String.format("[XRY DSP] Contiguous "
                        + "segments are not ascending incrementally. Encountered "
                        + "segment [ %d ] after segment [ %d ]. This means the reconstructed "
                        + "text will be out of order.", nextSegmentNumber, currentSegmentNumber));
            }

            for (int i = 1; i < nextEntityLines.length; i++) {
                String xryLine = nextEntityLines[i];
                //Find the XRY key on this line.
                int keyDelimiter = xryLine.indexOf(KEY_VALUE_DELIMITER);
                if (keyDelimiter == -1) {
                    //Skip this line, we are searching only for a text key-value pair.
                    continue;
                }

                String key = xryLine.substring(0, keyDelimiter);
                String normalizedKey = key.trim().toLowerCase();
                if (normalizedKey.equals(TEXT_KEY)) {
                    String value = xryLine.substring(keyDelimiter + 1).trim();
                    segmentedText.append(value).append(' ');

                    //Build up multiple lines.
                    for (; (i + 1) < nextEntityLines.length
                            && !hasKey(nextEntityLines[i + 1])
                            && !hasNamespace(nextEntityLines[i + 1]); i++) {
                        String continuedValue = nextEntityLines[i + 1].trim();
                        segmentedText.append(continuedValue).append(' ');
                    }
                }
            }

            currentSegmentNumber = nextSegmentNumber;
        }

        //Remove the trailing space.
        if (segmentedText.length() > 0) {
            segmentedText.setLength(segmentedText.length() - 1);
        }
        return segmentedText.toString();
    }

    /**
     * Determines if the line has recognized key value on it.
     *
     * @param xryLine
     * @return
     */
    private boolean hasKey(String xryLine) {
        int delimiter = xryLine.indexOf(':');
        if (delimiter != -1) {
            String key = xryLine.substring(0, delimiter);
            String normalizedKey = key.trim().toLowerCase();
            return XRY_KEYS.contains(normalizedKey);
        } else {
            return false;
        }
    }

    /**
     * Determines if the line is a recognized namespace.
     *
     * @param xryLine
     * @return
     */
    private boolean hasNamespace(String xryLine) {
        String normalizedLine = xryLine.trim().toLowerCase();
        return XRY_NAMESPACES.contains(normalizedLine);
    }

    /**
     * Extracts meta keys from the XRY entity. All of the known meta
     * keys are integers and describe the message segments.
     * 
     * @param xryLines Current XRY entity
     * @param expectedKey The meta key to search for
     * @return The interpreted integer value or Integer.MIN_VALUE if 
     * no meta key was found.
     */
    private int getMetaInfo(String[] xryLines, String metaKey) {
        for (int i = 0; i < xryLines.length; i++) {
            String xryLine = xryLines[i];

            String normalizedXryLine = xryLine.trim().toLowerCase();
            int firstDelimiter = normalizedXryLine.indexOf(KEY_VALUE_DELIMITER);
            if (firstDelimiter != -1) {
                String key = normalizedXryLine.substring(0, firstDelimiter);
                if (key.equals(metaKey)) {
                    String value = normalizedXryLine.substring(firstDelimiter + 1).trim();
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException ex) {
                        logger.log(Level.SEVERE, String.format("[XRY DSP] Value [ %s ] for "
                                + "meta key [ %s ] was not an integer.", value, metaKey), ex);
                    }
                }
            }
        }

        return Integer.MIN_VALUE;
    }

    /**
     * Creates an attribute from the extracted key value pair.
     * 
     * @param nameSpace The namespace of this key value pair.
     * It will have been verified beforehand, otherwise it will be empty.
     * @param key The key that was verified beforehand
     * @param value The value associated with that key.
     * @return 
     */
    private BlackboardAttribute makeAttribute(String namespace, String key, String value) {
        String normalizedKey = key.toLowerCase();
        String normalizedNamespace = namespace.toLowerCase();
        String normalizedValue = value.toLowerCase();

        switch (normalizedKey) {
            case "time":
                //Tranform value to epoch ms
                try {
                    String dateTime = removeDateTimeLocale(value);
                    String normalizedDateTime = dateTime.trim();
                    long dateTimeInEpoch = calculateSecondsSinceEpoch(normalizedDateTime);
                    return new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, PARSER_NAME, dateTimeInEpoch);
                } catch (DateTimeParseException ex) {
                    logger.log(Level.SEVERE, String.format("[XRY DSP] Assumption "
                            + "about the date time formatting of messages is not "
                            + "right. Here is the value [ %s ].", value), ex);
                    return null;
                }
            case "direction":
                return new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION, PARSER_NAME, value);
            case "text":
                return new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT, PARSER_NAME, value);
            case "status":
                switch (normalizedValue) {
                    case "read":
                        return new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_READ_STATUS, PARSER_NAME, READ);
                    case "unread":
                        return new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_READ_STATUS, PARSER_NAME, UNREAD);
                    case "sending failed":
                        //Ignore for now.
                        return null;
                    case "deleted":
                        //Ignore for now.
                        return null;
                    case "unsent":
                        //Ignore for now.
                        return null;
                    default:
                        logger.log(Level.SEVERE, String.format("[XRY DSP] Unrecognized "
                                + "status value [ %s ].", value));
                        return null;
                }
            case "type":
                switch (normalizedValue) {
                    case "deliver":
                        //Ignore for now.
                        return null;
                    case "submit":
                        //Ignore for now.
                        return null;
                    case "status report":
                        //Ignore for now.
                        return null;
                    default:
                        logger.log(Level.SEVERE, String.format("[XRY DSP] Unrecognized "
                                + "type value [ %s ]", value));
                        return null;
                }
            case "storage":
                //Ignore for now.
                return null;
            case "index":
                //Ignore for now.
                return null;
            case "folder":
                //Ignore for now.
                return null;
            case "name":
                //Ignore for now.
                return null;
            case "service center":
                //Ignore for now.
                return null;
            case "tel":
                //Apply the namespace
                if (normalizedNamespace.equals("from")) {
                    return new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM, PARSER_NAME, value);
                } else {
                    //Assume to and participant are both equivalent to TSK_PHONE_NUMBER_TO
                    return new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO, PARSER_NAME, value);
                }
            default:
                throw new IllegalArgumentException(String.format("key [ %s ] was not recognized.", key));
        }
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
