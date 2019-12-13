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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
    
    //Pattern is in reverse due to a Java 8 bug, see calculateSecondsSinceEpoch()
    //function for more details.
    private static final DateTimeFormatter DATE_TIME_PARSER
            = DateTimeFormatter.ofPattern("[(XXX) ][O ][(O) ]a h:m:s M/d/y");
    
    private static final String DEVICE_LOCALE = "(device)";
    private static final String NETWORK_LOCALE = "(network)";
    
    private static final int READ = 1;
    private static final int UNREAD = 0;
    
    /**
     * All of the known XRY keys for message reports and the blackboard
     * attribute types they map to.
     */
    private enum XryKey {
        DELETED("deleted", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ISDELETED),
        DIRECTION("direction", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION),
        MESSAGE("message", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT),
        NAME_MATCHED("name (matched)", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME_PERSON),
        TEXT("text", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT),
        TIME("time", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME),
        SERVICE_CENTER("service center", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER),
        FROM("from", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM),
        TO("to", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO),
        //The following keys either need special processing or more time and data to find a type.
        STORAGE("storage", null),
        NUMBER("number", null),
        TYPE("type", null),
        TEL("tel", null),
        FOLDER("folder", null),
        NAME("name", null),
        INDEX("index", null),
        STATUS("status", null);
        
        private final String name;
        private final BlackboardAttribute.ATTRIBUTE_TYPE type;
        
        XryKey(String name, BlackboardAttribute.ATTRIBUTE_TYPE type) {
            this.name = name;
            this.type = type;
        }
        
        public BlackboardAttribute.ATTRIBUTE_TYPE getType() {
            return type;
        }
        
        public String getDisplayName() {
            return name;
        }
        
        /**
         * Indicates if the display name of the XRY key is a recognized type.
         * 
         * @param xryKey
         * @return 
         */
        public static boolean contains(String name) {
            try {
                XryKey.fromDisplayName(name);
                return true;
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
        
        /**
         * Matches the display name of the xry key to the appropriate enum type.
         * 
         * It is assumed that XRY key string is recognized. Otherwise,
         * an IllegalArgumentException is thrown. Test all membership
         * with contains() before hand.
         * 
         * @param xryKey
         * @return 
         */
        public static XryKey fromDisplayName(String name) {
            String normalizedName = name.trim().toLowerCase();
            for(XryKey keyChoice : XryKey.values()) {
                if(normalizedName.equals(keyChoice.name)) {
                    return keyChoice;
                }
            }
            
            throw new IllegalArgumentException(String.format("Key [ %s ] was not found."
                    + " All keys should be tested with contains.", name));
        }
    }
    
    /**
     * All of the known XRY namespaces for message reports.
     */
    private enum XryNamespace {
        FROM("from"),
        PARTICIPANT("participant"),
        TO("to"),
        NONE(null);
        
        private final String name;
        
        XryNamespace(String name) {
            this.name = name;
        }
        
        /**
         * Indicates if the display name of the XRY namespace is a recognized type.
         * 
         * @param xryNamespace
         * @return 
         */
        public static boolean contains(String xryNamespace) {
            try {
                XryNamespace.fromDisplayName(xryNamespace);
                return true;
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
        
        /**
         * Matches the display name of the xry namespace to the appropriate enum type.
         * 
         * It is assumed that XRY namespace string is recognized. Otherwise,
         * an IllegalArgumentException is thrown. Test all membership
         * with contains() before hand.
         * 
         * @param xryNamespace
         * @return 
         */
        public static XryNamespace fromDisplayName(String xryNamespace) {
            String normalizedNamespace = xryNamespace.trim().toLowerCase();
            for(XryNamespace keyChoice : XryNamespace.values()) {
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
    private enum XryMetaKey {
        REFERENCE_NUMBER("reference number"),
        SEGMENT_COUNT("segments"),
        SEGMENT_NUMBER("segment number");
        
        private final String name;
        
        XryMetaKey(String name) {
            this.name = name;
        }
        
        public String getDisplayName() {
            return name;
        }
        
        
        /**
         * Indicates if the display name of the XRY key is a recognized type.
         * 
         * @param xryKey
         * @return 
         */
        public static boolean contains(String name) {
            try {
                XryMetaKey.fromDisplayName(name);
                return true;
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
        
        /**
         * Matches the display name of the xry key to the appropriate enum type.
         * 
         * It is assumed that XRY key string is recognized. Otherwise,
         * an IllegalArgumentException is thrown. Test all membership
         * with contains() before hand.
         * 
         * @param xryKey
         * @return 
         */
        public static XryMetaKey fromDisplayName(String name) {
            String normalizedName = name.trim().toLowerCase();
            for(XryMetaKey keyChoice : XryMetaKey.values()) {
                if(normalizedName.equals(keyChoice.name)) {
                    return keyChoice;
                }
            }
            
            throw new IllegalArgumentException(String.format("Key [ %s ] was not found."
                    + " All keys should be tested with contains.", name));
        }
    }

    /**
     * Message-SMS report artifacts can span multiple XRY entities and their
     * attributes can span multiple lines. The "Text" and "Message" keys are the only known key
     * value pair that can span multiple lines. Messages can be segmented,
     * meaning that their "Text" and "Message" content can appear in multiple XRY entities.
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
            List<BlackboardAttribute> attributes = getBlackboardAttributes(xryEntity, reader, referenceNumbersSeen);
            //Only create artifacts with non-empty attributes.
            if(!attributes.isEmpty()) {
                BlackboardArtifact artifact = parent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE);
                artifact.addAttributes(attributes);
            }
        }
    }
    
    /**
     * 
     * @param xryEntity
     * @param reader
     * @param referenceValues
     * @return
     * @throws IOException 
     */
    private List<BlackboardAttribute> getBlackboardAttributes(String xryEntity, 
            XRYFileReader reader, Set<Integer> referenceValues) throws IOException {
        String[] xryLines = xryEntity.split("\n");
        //First line of the entity is the title, each XRY entity is non-empty.
        logger.log(Level.INFO, String.format("[XRY DSP] Processing [ %s ]", xryLines[0]));
        
        List<BlackboardAttribute> attributes = new ArrayList<>();
        
        //Count the key value pairs in the XRY entity.
        int keyCount = countKeys(xryLines);
        for (int i = 1; i <= keyCount; i++) {
            //Get the ith key value pair in the entity. Always expect to have 
            //a valid value.
            XRYKeyValuePair pair = getKeyValuePairByIndex(xryLines, i).get();
            if (XryMetaKey.contains(pair.getKey())) {
                //Skip meta keys, they are being handled seperately.
                continue;
            }

            if (!XryKey.contains(pair.getKey())) {
                logger.log(Level.WARNING, String.format("[XRY DSP] The following key, "
                        + "value pair (in brackets) [ %s ], "
                        + "was not recognized. Discarding...", pair));
                continue;
            }

            if (pair.getValue().isEmpty()) {
                logger.log(Level.WARNING, String.format("[XRY DSP] The following key "
                        + "(in brackets) [ %s ] was recognized, but the value "
                        + "was empty. Discarding...", pair.getKey()));
                continue;
            }

            //Assume text and message are the only fields that can be segmented
            //among multiple XRY entities.
            if (pair.hasKey(XryKey.TEXT.getDisplayName()) || 
                    pair.hasKey(XryKey.MESSAGE.getDisplayName())) {
                String segmentedText  = getSegmentedText(xryLines, reader, referenceValues);
                pair = new XRYKeyValuePair(pair.getKey(), 
                        //Assume text is segmented by word.
                        pair.getValue() + " " + segmentedText,
                        pair.getNamespace());
            }

            //Get the corresponding blackboard attribute, if any.
            Optional<BlackboardAttribute> attribute = getBlackboardAttribute(pair);
            if (attribute.isPresent()) {
                attributes.add(attribute.get());
            }
        }
        
        return attributes;
    }
    
    /**
     * Counts the key value pairs in an XRY entity.
     * Skips counting the first line as it is assumed to be the title.
     */
    private Integer countKeys(String[] xryEntity) {
        int count = 0;
        for (int i = 1; i < xryEntity.length; i++) {
            if(XRYKeyValuePair.isPair(xryEntity[i])) {
                count++;
            }
        }
        return count;
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
    private String getSegmentedText(String[] xryEntity, XRYFileReader reader, 
            Set<Integer> referenceNumbersSeen) throws IOException {
        Optional<Integer> referenceNumber = getMetaKeyValue(xryEntity, XryMetaKey.REFERENCE_NUMBER);
        //Check if there is any segmented text.
        if(!referenceNumber.isPresent()) {
            return "";
        }
        
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

        Optional<Integer> segmentNumber = getMetaKeyValue(xryEntity, XryMetaKey.SEGMENT_NUMBER);
        if(!segmentNumber.isPresent()) {
            logger.log(Level.SEVERE, String.format("No segment "
                    + "number was found on the message entity"
                    + "with reference number [%d]", referenceNumber.get()));
            return "";
        }
        
        StringBuilder segmentedText = new StringBuilder();

        int currentSegmentNumber = segmentNumber.get();
        while (reader.hasNextEntity()) {
            //Peek at the next to see if it has the same reference number.
            String nextEntity = reader.peek();
            String[] nextEntityLines = nextEntity.split("\n");
            Optional<Integer> nextReferenceNumber = getMetaKeyValue(nextEntityLines, XryMetaKey.REFERENCE_NUMBER);

            if (!nextReferenceNumber.isPresent() || 
                    !Objects.equals(nextReferenceNumber, referenceNumber)) {
                //Don't consume the next entity. It is not related
                //to the current message thread.
                break;
            }

            //Consume the entity, it is a part of the message thread.
            reader.nextEntity();

            Optional<Integer> nextSegmentNumber = getMetaKeyValue(nextEntityLines, XryMetaKey.SEGMENT_NUMBER);

            logger.log(Level.INFO, String.format("[XRY DSP] Processing [ %s ] "
                    + "segment with reference number [ %d ]", nextEntityLines[0], referenceNumber.get()));

            if(!nextSegmentNumber.isPresent()) {
                logger.log(Level.SEVERE, String.format("[XRY DSP] Segment with reference"
                        + " number [ %d ] did not have a segment number associated with it."
                        + " It cannot be determined if the reconstructed text will be in order.", referenceNumber.get()));
            } else if (nextSegmentNumber.get() != currentSegmentNumber + 1) {
                logger.log(Level.SEVERE, String.format("[XRY DSP] Contiguous "
                        + "segments are not ascending incrementally. Encountered "
                        + "segment [ %d ] after segment [ %d ]. This means the reconstructed "
                        + "text will be out of order.", nextSegmentNumber.get(), currentSegmentNumber));
            }

            int keyCount = countKeys(nextEntityLines);
            for (int i = 1; i <= keyCount; i++) {
                XRYKeyValuePair pair = getKeyValuePairByIndex(nextEntityLines, i).get();
                if(pair.hasKey(XryKey.TEXT.getDisplayName()) || 
                        pair.hasKey(XryKey.MESSAGE.getDisplayName())) {
                    segmentedText.append(pair.getValue()).append(' ');
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
     * 
     * @param xryLines
     * @param metaKey
     * @return 
     */
    private Optional<Integer> getMetaKeyValue(String[] xryLines, XryMetaKey metaKey) {
        for (String xryLine : xryLines) {
            if (!XRYKeyValuePair.isPair(xryLine)) {
                continue;
            }
            
            XRYKeyValuePair pair = XRYKeyValuePair.from(xryLine);
            if(pair.hasKey(metaKey.getDisplayName())) {
                try {
                    return Optional.of(Integer.parseInt(pair.getValue()));
                } catch (NumberFormatException ex) {
                    logger.log(Level.SEVERE, String.format("[XRY DSP] Value [ %s ] for "
                            + "meta key [ %s ] was not an integer.", pair.getValue(), metaKey), ex);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 
     * @param xryLines
     * @param index
     * @return 
     */
    private Optional<XRYKeyValuePair> getKeyValuePairByIndex(String[] xryLines, int index) {
        int pairsParsed = 0;
        String namespace = "";
        for (int i = 1; i < xryLines.length; i++) {
            String xryLine = xryLines[i];
            if(XryNamespace.contains(xryLine)) {
                namespace = xryLine.trim();
                continue;
            }
            
            if(!XRYKeyValuePair.isPair(xryLine)) {
                logger.log(Level.SEVERE, String.format("[XRY DSP] Expected a key value "
                + "pair on this line (in brackets) [ %s ], but one was not detected."
                + " Discarding...", xryLine));   
                continue;
            }
            
            XRYKeyValuePair pair = XRYKeyValuePair.from(xryLine);
            String value = pair.getValue();
            //Build up multiple lines.
            for (; (i+1) < xryLines.length
                    && !XRYKeyValuePair.isPair(xryLines[i+1])
                    && !XryNamespace.contains(xryLines[i+1]); i++) {
                String continuedValue = xryLines[i+1].trim();
                //Assume multi lined values are split by word.
                value = value + " " + continuedValue;
            }

            pair = new XRYKeyValuePair(pair.getKey(), value, namespace);
            pairsParsed++;
            if(pairsParsed == index) {
                return Optional.of(pair);
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
    private Optional<BlackboardAttribute> getBlackboardAttribute(XRYKeyValuePair pair) {
        XryNamespace namespace = XryNamespace.NONE;
        if(XryNamespace.contains(pair.getNamespace())) {
            namespace = XryNamespace.fromDisplayName(pair.getNamespace());
        }
        XryKey key = XryKey.fromDisplayName(pair.getKey());
        String normalizedValue = pair.getValue().toLowerCase().trim();
        
        switch (key) {
            case TEL:
            case NUMBER:
                switch (namespace) {
                    case FROM:
                        return Optional.of(new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM,
                                PARSER_NAME, pair.getValue()));
                    case TO:
                    case PARTICIPANT:
                        return Optional.of(new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO,
                                PARSER_NAME, pair.getValue()));
                    default:
                        return Optional.of(new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER,
                                PARSER_NAME, pair.getValue()));
                }   
            case TIME:
                try {
                    //Tranform value to seconds since epoch
                    long dateTimeSinceInEpoch = calculateSecondsSinceEpoch(pair.getValue());
                    return Optional.of(new BlackboardAttribute(
                            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START, 
                            PARSER_NAME, dateTimeSinceInEpoch));
                } catch (DateTimeParseException ex) {
                    logger.log(Level.WARNING, String.format("[XRY DSP] Assumption"
                            + " about the date time formatting of messages is "
                            + "not right. Here is the pair [ %s ]", pair), ex);
                    return Optional.empty();
                }
            case TYPE:
                switch (normalizedValue) {
                    case "incoming":
                    case "outgoing":
                        return Optional.of(new BlackboardAttribute(
                            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION,
                            PARSER_NAME, pair.getValue()));
                    case "deliver":
                    case "submit":
                    case "status report":
                        //Ignore for now.
                        return Optional.empty();
                    default:
                        logger.log(Level.WARNING, String.format("[XRY DSP] Unrecognized "
                                + " value for key pair [ %s ].", pair));
                        return Optional.empty();
                }
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
                        return Optional.empty();
                    default:
                        logger.log(Level.WARNING, String.format("[XRY DSP] Unrecognized "
                                + " value for key pair [ %s ].", pair));
                        return Optional.empty();
                }
            default:
                //Otherwise, the XryKey enum contains the correct BlackboardAttribute
                //type.
                if(key.getType() != null) {
                    return Optional.of(new BlackboardAttribute(key.getType(), 
                        PARSER_NAME, pair.getValue()));
                }
                
                logger.log(Level.WARNING, String.format("[XRY DSP] Key value pair "
                    + "(in brackets) [ %s ] was recognized but "
                    + "more data or time is needed to finish implementation. Discarding... ", pair));
                
                return Optional.empty();
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
        String result = dateTime;
        int deviceIndex = result.toLowerCase().indexOf(DEVICE_LOCALE);
        if (deviceIndex != -1) {
            result = result.substring(0, deviceIndex);
        }
        int networkIndex = result.toLowerCase().indexOf(NETWORK_LOCALE);
        if(networkIndex != -1) {
            result = result.substring(0, networkIndex);
        }
        return result;
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
         * of the JDK (9 and beyond). 
         * https://bugs.openjdk.java.net/browse/JDK-8154050
         * Rather than update the JDK to accommodate this, the components of 
         * the date time string are reversed:
         * 
         *      UTC+4 AM 1:23:54 1/3/1990
         * 
         * The java time package will correctly parse this date time format.
         */
        String reversedDateTime = reverseOrderOfDateTimeComponents(dateTimeWithoutLocale);
        /**
         * Furthermore, the DateTimeFormatter's timezone offset letter ('O') does
         * not recognize UTC but recognizes GMT. According to 
         * https://en.wikipedia.org/wiki/Coordinated_Universal_Time,
         * GMT only differs from UTC by at most 1 second and so substitution
         * will only introduce a trivial amount of error.
         */
        String reversedDateTimeWithGMT = reversedDateTime.replace("UTC", "GMT");
        TemporalAccessor result = DATE_TIME_PARSER.parseBest(reversedDateTimeWithGMT,
                ZonedDateTime::from,
                LocalDateTime::from,
                OffsetDateTime::from);
        //Query for the ZoneID
        if(result.query(TemporalQueries.zoneId()) == null) {
            //If none, assumed GMT+0.
            return ZonedDateTime.of(LocalDateTime.from(result), 
                    ZoneId.of("GMT")).toEpochSecond();
        } else {
            return Instant.from(result).getEpochSecond();
        }
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
