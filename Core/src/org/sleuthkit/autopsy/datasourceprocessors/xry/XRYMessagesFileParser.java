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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
    
    //Pattern is in reverse due to a Java 8 bug, see calculateSecondsSinceEpoch()
    //function for more details.
    private static final DateTimeFormatter DATE_TIME_PARSER
            = DateTimeFormatter.ofPattern("O a h:m:s M/d/y");

    //A more readable version of these values. Referring to if the user
    //has read the message.
    private static final int READ = 1;
    private static final int UNREAD = 0;
    
    /**
     * All of the known XRY keys for message reports.
     */
    private static enum XRY_KEY {
        TEXT("text"),
        DIRECTION("direction"),
        TIME("time"),
        STATUS("status"),
        TEL("tel"),
        STORAGE("storage"),
        INDEX("index"),
        FOLDER("folder"),
        SERVICE_CENTER("service center"),
        TYPE("type"),
        NAME("name"),
        NAME_MATCHED("name (matched)");
        
        private final String name;
        
        XRY_KEY(String name) {
            this.name = name;
        }
        
        /**
         * Indicates if the XRY key is a recognized type.
         * 
         * @param xryKey
         * @return 
         */
        public static boolean contains(String xryKey) {
            String normalizedKey = xryKey.trim().toLowerCase();
            for(XRY_KEY keyChoice : XRY_KEY.values()) {
                if(keyChoice.name.equals(normalizedKey)) {
                    return true;
                }
            }
            
            return false;
        }
        
        /**
         * Fetches the enum type for the given XRY key.
         * 
         * It is assumed that XRY key string is recognized. Otherwise,
         * an IllegalArgumentException is thrown. Test all membership
         * with contains() before hand.
         * 
         * @param xryKey
         * @return 
         */
        public static XRY_KEY fromName(String xryKey) {
            String normalizedKey = xryKey.trim().toLowerCase();
            for(XRY_KEY keyChoice : XRY_KEY.values()) {
                if(keyChoice.name.equals(normalizedKey)) {
                    return keyChoice;
                }
            }
            
            throw new IllegalArgumentException(String.format("Key [%s] was not found."
                    + " All keys should be tested with contains.", xryKey));
        }
    }
    
    /**
     * All of the known XRY namespaces for message reports.
     */
    private static enum XRY_NAMESPACE {
        TO("to"),
        FROM("from"),
        PARTICIPANT("participant"),
        NONE(null);
        
        private final String name;
        
        XRY_NAMESPACE(String name) {
            this.name = name;
        }
        
        /**
         * Indicates if the XRY namespace is a recognized type.
         * 
         * @param xryNamespace
         * @return 
         */
        public static boolean contains(String xryNamespace) {
            String normalizedNamespace = xryNamespace.trim().toLowerCase();
            for(XRY_NAMESPACE keyChoice : XRY_NAMESPACE.values()) {
                if(normalizedNamespace.equals(keyChoice.name)) {
                    return true;
                }
            }
            
            return false;
        }
        
        /**
         * Fetches the enum type for the given XRY namespace.
         * 
         * It is assumed that XRY namespace string is recognized. Otherwise,
         * an IllegalArgumentException is thrown. Test all membership
         * with contains() before hand.
         * 
         * @param xryNamespace
         * @return 
         */
        public static XRY_NAMESPACE fromName(String xryNamespace) {
            String normalizedNamespace = xryNamespace.trim().toLowerCase();
            for(XRY_NAMESPACE keyChoice : XRY_NAMESPACE.values()) {
                if(normalizedNamespace.equals(keyChoice.name)) {
                    return keyChoice;
                }
            }
            
            throw new IllegalArgumentException(String.format("Namespace [%s] was not found."
                    + " All namespaces should be tested with contains.", xryNamespace));
        }
    }
    
    /**
     * All known XRY meta keys for message reports.
     */
    private static enum XRY_META_KEY {
        REFERENCE_NUMBER("reference number"),
        SEGMENT_NUMBER("segment number"),
        SEGMENT_COUNT("segments");
        
        private final String name;
        
        XRY_META_KEY(String name) {
            this.name = name;
        }
        
        /**
         * Indicates if the XRY meta key is a recognized type.
         * 
         * @param xryMetaKey
         * @return 
         */
        public static boolean contains(String xryMetaKey) {
            String normalizedMetaKey = xryMetaKey.trim().toLowerCase();
            for(XRY_META_KEY keyChoice : XRY_META_KEY.values()) {
                if(keyChoice.name.equals(normalizedMetaKey)) {
                    return true;
                }
            }
            
            return false;
        }
        
        /**
         * Fetches the enum type for the given XRY meta key.
         * 
         * It is assumed that XRY meta key string is recognized. Otherwise,
         * an IllegalArgumentException is thrown. Test all membership
         * with contains() before hand.
         * 
         * @param xryMetaKey
         * @return 
         */
        public static XRY_META_KEY fromName(String xryMetaKey) {
            String normalizedMetaKey = xryMetaKey.trim().toLowerCase();
            for(XRY_META_KEY keyChoice : XRY_META_KEY.values()) {
                if(keyChoice.name.equals(normalizedMetaKey)) {
                    return keyChoice;
                }
            }
            
            throw new IllegalArgumentException(String.format("Meta key [%s] was not found."
                    + " All meta keys should be tested with contains.", xryMetaKey));
        }
    }

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
        logger.log(Level.INFO, String.format("[XRY DSP] Processing report at"
                + " [ %s ]", reportPath.toString()));

        //Keep track of the reference numbers that have been parsed.
        Set<Integer> referenceNumbersSeen = new HashSet<>();

        while (reader.hasNextEntity()) {
            String xryEntity = reader.nextEntity();
            String[] xryLines = xryEntity.split("\n");

            //First line of the entity is the title, each XRY entity is non-empty.
            logger.log(Level.INFO, String.format("[XRY DSP] Processing [ %s ]", xryLines[0]));

            List<BlackboardAttribute> attributes = new ArrayList<>();

            XRY_NAMESPACE namespace = XRY_NAMESPACE.NONE;
            for (int i = 1; i < xryLines.length; i++) {
                String xryLine = xryLines[i];

                if (XRY_NAMESPACE.contains(xryLine)) {
                    namespace = XRY_NAMESPACE.fromName(xryLine);
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
                String key = xryLine.substring(0, keyDelimiter);
                String value = xryLine.substring(keyDelimiter + 1).trim();

                if (XRY_META_KEY.contains(key)) {
                    //Skip meta keys, they are being handled seperately.
                    continue;
                }

                if (!XRY_KEY.contains(key)) {
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
                
                XRY_KEY xryKey = XRY_KEY.fromName(key);

                //Assume text is the only field that can span multiple lines.
                if (xryKey.equals(XRY_KEY.TEXT)) {
                    //Build up multiple lines.
                    for (; (i + 1) < xryLines.length
                            && !hasKey(xryLines[i + 1])
                            && !hasNamespace(xryLines[i + 1]); i++) {
                        String continuedValue = xryLines[i + 1].trim();
                        //Assume multi lined values are split by word.
                        value = value + " " + continuedValue;
                    }

                    Optional<Integer> referenceNumber = getMetaInfo(xryLines, XRY_META_KEY.REFERENCE_NUMBER);
                    //Check if there is any segmented text.
                    if (referenceNumber.isPresent()) {
                        logger.log(Level.INFO, String.format("[XRY DSP] Message entity "
                                + "appears to be segmented with reference number [ %d ]", referenceNumber.get()));

                        if (referenceNumbersSeen.contains(referenceNumber.get())) {
                            logger.log(Level.SEVERE, String.format("[XRY DSP] This reference [ %d ] has already "
                                    + "been seen. This means that the segments are not "
                                    + "contiguous. Any segments contiguous with this "
                                    + "one will be aggregated and another "
                                    + "(otherwise duplicate) artifact will be created.", referenceNumber.get()));
                        }

                        referenceNumbersSeen.add(referenceNumber.get());

                        Optional<Integer> segmentNumber = getMetaInfo(xryLines, XRY_META_KEY.SEGMENT_NUMBER);
                        if(segmentNumber.isPresent()) {
                            //Unify segmented text
                            String segmentedText = getSegmentedText(referenceNumber.get(),
                                    segmentNumber.get(), reader);
                            //Assume it was segmented by word.
                            value = value + " " + segmentedText;
                        } else {
                            logger.log(Level.SEVERE, String.format("No segment "
                                    + "number was found on the message entity"
                                    + "with reference number [%d]", referenceNumber.get()));
                        }
                    }
                }

                //Get the corresponding blackboard attribute, if any.
                Optional<BlackboardAttribute> attribute = makeAttribute(namespace, xryKey, value);
                if (attribute.isPresent()) {
                    attributes.add(attribute.get());
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
            Optional<Integer> nextReferenceNumber = getMetaInfo(nextEntityLines, XRY_META_KEY.REFERENCE_NUMBER);

            if (!nextReferenceNumber.isPresent() || nextReferenceNumber.get() != referenceNumber) {
                //Don't consume the next entity. It is not related
                //to the current message thread.
                break;
            }

            //Consume the entity, it is a part of the message thread.
            reader.nextEntity();

            Optional<Integer> nextSegmentNumber = getMetaInfo(nextEntityLines, XRY_META_KEY.SEGMENT_NUMBER);

            logger.log(Level.INFO, String.format("[XRY DSP] Processing [ %s ] "
                    + "segment with reference number [ %d ]", nextEntityLines[0], referenceNumber));

            if(!nextSegmentNumber.isPresent()) {
                logger.log(Level.SEVERE, String.format("[XRY DSP] Segment with reference"
                        + " number [ %d ] did not have a segment number associated with it."
                        + " It cannot be determined if the reconstructed text will be in order.", referenceNumber));
            } else if (nextSegmentNumber.get() != currentSegmentNumber + 1) {
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

                //Extract the text key from the entity
                String key = xryLine.substring(0, keyDelimiter);
                if(XRY_KEY.contains(key) && XRY_KEY.fromName(key).equals(XRY_KEY.TEXT)) {
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

            if(nextSegmentNumber.isPresent()) {
                currentSegmentNumber = nextSegmentNumber.get();
            }
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
        if(delimiter == -1) {
            return false;
        }
        
        String key = xryLine.substring(0, delimiter);
        return XRY_KEY.contains(key);
    }

    /**
     * Determines if the line is a recognized namespace.
     *
     * @param xryLine
     * @return
     */
    private boolean hasNamespace(String xryLine) {
        return XRY_NAMESPACE.contains(xryLine);
    }

    /**
     * Extracts meta keys from the XRY entity. All of the known meta
     * keys are assumed integers and part of the XRY_META_KEY enum.
     * 
     * @param xryLines Current XRY entity
     * @param expectedKey The meta key to search for
     * @return The interpreted integer value or Integer.MIN_VALUE if 
     * no meta key was found.
     */
    private Optional<Integer> getMetaInfo(String[] xryLines, XRY_META_KEY metaKey) {
        for (int i = 0; i < xryLines.length; i++) {
            String xryLine = xryLines[i];

            int firstDelimiter = xryLine.indexOf(KEY_VALUE_DELIMITER);
            if (firstDelimiter != -1) {
                String key = xryLine.substring(0, firstDelimiter);
                if(!XRY_META_KEY.contains(key)) {
                    continue;
                }
                
                XRY_META_KEY currentMetaKey = XRY_META_KEY.fromName(key);
                if (currentMetaKey.equals(metaKey)) {
                    String value = xryLine.substring(firstDelimiter + 1).trim();
                    try {
                        return Optional.of(Integer.parseInt(value));
                    } catch (NumberFormatException ex) {
                        logger.log(Level.SEVERE, String.format("[XRY DSP] Value [ %s ] for "
                                + "meta key [ %s ] was not an integer.", value, metaKey), ex);
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Creates an attribute from the extracted key value pair.
     * 
     * @param nameSpace The namespace of this key value pair.
     * It will have been verified beforehand, otherwise it will be NONE.
     * @param key The recognized XRY key.
     * @param value The value associated with that key.
     * @return Corresponding blackboard attribute, if any.
     */
    private Optional<BlackboardAttribute> makeAttribute(XRY_NAMESPACE namespace, XRY_KEY key, String value) {
        String normalizedValue = value.toLowerCase().trim();
        switch (key) {
            case DIRECTION:
                return Optional.of(new BlackboardAttribute(
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION, 
                        PARSER_NAME, value));
            case NAME_MATCHED: 
                return Optional.of(new BlackboardAttribute(
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME_PERSON, 
                        PARSER_NAME, value));
            case TEL:
                if(namespace.equals(XRY_NAMESPACE.FROM)) {
                    return Optional.of(new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM,
                                PARSER_NAME, value));
                } else {
                    //Assume TO and PARTICIPANT are TSK_PHONE_NUMBER_TOs
                    return Optional.of(new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO,
                                PARSER_NAME, value));
                }
            case TEXT:
                return Optional.of(new BlackboardAttribute(
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT, 
                        PARSER_NAME, value));
            case TIME:
                try {
                    //Tranform value to seconds since epoch
                    long dateTimeSinceInEpoch = calculateSecondsSinceEpoch(value);
                    return Optional.of(new BlackboardAttribute(
                            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START, 
                            PARSER_NAME, dateTimeSinceInEpoch));
                } catch (DateTimeParseException ex) {
                    logger.log(Level.WARNING, String.format("[XRY DSP] Assumption"
                            + " about the date time formatting of messages is "
                            + "not right. Here is the value [ %s ]", value), ex);
                    return Optional.empty();
                }
            case TYPE:
                switch (normalizedValue) {
                    case "deliver":
                    case "submit":
                    case "status report":
                        //Ignore for now.
                        break;
                    default:
                        logger.log(Level.WARNING, String.format("[XRY DSP] Unrecognized "
                                + "type value [ %s ]", value));
                }
                return Optional.empty();
            case SERVICE_CENTER:
                return Optional.of(new BlackboardAttribute(
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER, 
                        PARSER_NAME, value));
            case STATUS:
                switch (normalizedValue) {
                    case "read":
                        return Optional.of(new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_READ_STATUS, 
                                PARSER_NAME, READ));
                    case "unread":
                        return Optional.of(new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_READ_STATUS, 
                                PARSER_NAME, UNREAD));
                    case "sending failed":
                    case "deleted":
                    case "unsent":
                    case "sent":
                        //Ignore for now.
                        break;
                    default:
                        logger.log(Level.WARNING, String.format("[XRY DSP] Unrecognized "
                                + "status value [ %s ].", value));
                }
                return Optional.empty();
            case STORAGE:
            case INDEX:
            case FOLDER:
            case NAME:
                //Ignore for now.
                return Optional.empty();
            default:
                throw new IllegalArgumentException(String.format("Key [ %s ] "
                        + "passed the isKey() test but was not matched. There is"
                        + " likely a typo in the  code.", key));
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
     * Parses the date time value and calculates seconds since epoch.
     *
     * @param dateTime
     * @return
     */
    private long calculateSecondsSinceEpoch(String dateTime) {
        String dateTimeWithoutLocale = removeDateTimeLocale(dateTime).trim();
        /**
         * The format of time in XRY Messages reports is of the form:
         * 
         *      1/3/1990 1:23:54 AM UTC+4
         * 
         * In our current version of Java (openjdk-1.8.0.222), there is
         * a bug with having the timezone offset (UTC+4 or GMT-7) at the 
         * end of the date time input. This is fixed in later versions
         * of the JDK (9 and beyond). Rather than update the JDK to
         * accommodate this, the components of the date time string are reversed:
         * 
         *      UTC+4 AM 1:23:54 1/3/1990
         * 
         * The java time package will correctly parse this date time format.
         */
        String reversedDateTime = reverseOrderOfDateTimeComponents(dateTimeWithoutLocale);
        /**
         * Furthermore, the DateTimeFormatter's timezone offset letter ('O') does
         * not recognized UTC but recognizes GMT. According to 
         * https://en.wikipedia.org/wiki/Coordinated_Universal_Time,
         * GMT only differs from UTC by at most 1 second and so substitution
         * will only introduce a trivial amount of error.
         */
        String reversedDateTimeWithGMT = reversedDateTime.replace("UTC", "GMT");
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(reversedDateTimeWithGMT, DATE_TIME_PARSER);
        return zonedDateTime.toEpochSecond();
    }
    
    /**
     * Reverses the order of the date time components.
     * 
     * Example: 
     *  1/3/1990 1:23:54 AM UTC+4 
     * becomes
     *  UTC+4 AM 1:23:54 1/3/1990
     * 
     * @param dateTime
     * @return
     */
    private String reverseOrderOfDateTimeComponents(String dateTime) {
        StringBuilder reversedDateTime = new StringBuilder(dateTime.length());
        String[] dateTimeComponents = dateTime.split(" ");
        for (String component : dateTimeComponents) {
            reversedDateTime.insert(0, " ").insert(0, component);
        }
        return reversedDateTime.toString().trim();
    }
}
