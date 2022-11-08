/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.Blackboard.BlackboardException;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.InvalidAccountIDException;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper;
import org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper.CommunicationDirection;
import org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper.MessageReadStatus;

/**
 * Parses Messages-SMS files and creates artifacts.
 */
final class XRYMessagesFileParser implements XRYFileParser {

    private static final Logger logger = Logger.getLogger(
            XRYMessagesFileParser.class.getName());

    private static final String PARSER_NAME = "XRY DSP";

    /**
     * All of the known XRY keys for message reports and their corresponding
     * blackboard attribute types, if any.
     */
    private enum XryKey {
        DELETED("deleted", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ISDELETED),
        DIRECTION("direction", null),
        MESSAGE("message", null),
        NAME_MATCHED("name (matched)", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME_PERSON),
        TEXT("text", null),
        TIME("time", null),
        SERVICE_CENTER("service center", null),
        FROM("from", null),
        TO("to", null),
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
         * @param name
         *
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
         * It is assumed that XRY key string is recognized. Otherwise, an
         * IllegalArgumentException is thrown. Test all membership with
         * contains() before hand.
         *
         * @param name
         *
         * @return
         */
        public static XryKey fromDisplayName(String name) {
            String normalizedName = name.trim().toLowerCase();
            for (XryKey keyChoice : XryKey.values()) {
                if (normalizedName.equals(keyChoice.name)) {
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
         * Indicates if the display name of the XRY namespace is a recognized
         * type.
         *
         * @param xryNamespace
         *
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
         * Matches the display name of the xry namespace to the appropriate enum
         * type.
         *
         * It is assumed that XRY namespace string is recognized. Otherwise, an
         * IllegalArgumentException is thrown. Test all membership with
         * contains() before hand.
         *
         * @param xryNamespace
         *
         * @return
         */
        public static XryNamespace fromDisplayName(String xryNamespace) {
            String normalizedNamespace = xryNamespace.trim().toLowerCase();
            for (XryNamespace keyChoice : XryNamespace.values()) {
                if (normalizedNamespace.equals(keyChoice.name)) {
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
         * @param name
         *
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
         * It is assumed that XRY key string is recognized. Otherwise, an
         * IllegalArgumentException is thrown. Test all membership with
         * contains() before hand.
         *
         * @param name
         *
         * @return
         */
        public static XryMetaKey fromDisplayName(String name) {
            String normalizedName = name.trim().toLowerCase();
            for (XryMetaKey keyChoice : XryMetaKey.values()) {
                if (normalizedName.equals(keyChoice.name)) {
                    return keyChoice;
                }
            }

            throw new IllegalArgumentException(String.format("Key [ %s ] was not found."
                    + " All keys should be tested with contains.", name));
        }
    }

    /**
     * Message-SMS report artifacts can span multiple XRY entities and their
     * attributes can span multiple lines. The "Text" and "Message" keys are the
     * only known key value pair that can span multiple lines. Messages can be
     * segmented, meaning that their "Text" and "Message" content can appear in
     * multiple XRY entities. Our goal for a segmented message is to aggregate
     * all of the text pieces and create 1 artifact.
     *
     * This parse implementation assumes that segments are contiguous and that
     * they ascend incrementally. There are checks in place to verify this
     * assumption is correct, otherwise an error will appear in the logs.
     *
     * @param reader The XRYFileReader that reads XRY entities from the
     *               Message-SMS report.
     * @param parent The parent Content to create artifacts from.
     *
     * @throws IOException      If an I/O error is encountered during report
     *                          reading
     * @throws TskCoreException If an error during artifact creation is
     *                          encountered.
     */
    @Override
    public void parse(XRYFileReader reader, Content parent, SleuthkitCase currentCase) throws IOException, TskCoreException, BlackboardException {
        Path reportPath = reader.getReportPath();
        logger.log(Level.INFO, String.format("[XRY DSP] Processing report at"
                + " [ %s ]", reportPath.toString()));

        //Keep track of the reference numbers that have been parsed.
        Set<Integer> referenceNumbersSeen = new HashSet<>();

        while (reader.hasNextEntity()) {
            String xryEntity = reader.nextEntity();

            // This call will combine all segmented text into a single key value pair
            List<XRYKeyValuePair> pairs = getXRYKeyValuePairs(xryEntity, reader, referenceNumbersSeen);

            // Transform all the data from XRY land into the appropriate CommHelper
            // data types.
            final String messageType = PARSER_NAME;
            CommunicationDirection direction = CommunicationDirection.UNKNOWN;
            String senderId = null;
            final List<String> recipientIdsList = new ArrayList<>();
            long dateTime = 0L;
            MessageReadStatus readStatus = MessageReadStatus.UNKNOWN;
            final String subject = null;
            String text = null;
            final String threadId = null;
            final Collection<BlackboardAttribute> otherAttributes = new ArrayList<>();

            for (XRYKeyValuePair pair : pairs) {
                XryNamespace namespace = XryNamespace.NONE;
                if (XryNamespace.contains(pair.getNamespace())) {
                    namespace = XryNamespace.fromDisplayName(pair.getNamespace());
                }
                XryKey key = XryKey.fromDisplayName(pair.getKey());
                String normalizedValue = pair.getValue().toLowerCase().trim();

                switch (key) {
                    case TEL:
                    case NUMBER:
                        if (!XRYUtils.isPhoneValid(pair.getValue())) {
                            continue;
                        }

                        // Apply namespace or direction
                        if (namespace == XryNamespace.FROM || direction == CommunicationDirection.INCOMING) {
                            senderId = pair.getValue();
                        } else if (namespace == XryNamespace.TO || direction == CommunicationDirection.OUTGOING) {
                            recipientIdsList.add(pair.getValue());
                        } else {
                            try {
                                currentCase.getCommunicationsManager().createAccountFileInstance(
                                        Account.Type.PHONE, pair.getValue(), PARSER_NAME, parent, null, null);
                            } catch (InvalidAccountIDException ex) {
                                logger.log(Level.WARNING, String.format("Invalid account identifier %s", pair.getValue()), ex);
                            }

                            otherAttributes.add(new BlackboardAttribute(
                                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER,
                                    PARSER_NAME, pair.getValue()));
                        }
                        break;
                    // Although confusing, as these are also 'name spaces', it appears
                    // later versions of XRY just made these standardized lines.
                    case FROM:
                        if (!XRYUtils.isPhoneValid(pair.getValue())) {
                            continue;
                        }

                        senderId = pair.getValue();
                        break;
                    case TO:
                        if (!XRYUtils.isPhoneValid(pair.getValue())) {
                            continue;
                        }

                        recipientIdsList.add(pair.getValue());
                        break;
                    case TIME:
                        try {
                        //Tranform value to seconds since epoch
                        long dateTimeSinceInEpoch = XRYUtils.calculateSecondsSinceEpoch(pair.getValue());
                        dateTime = dateTimeSinceInEpoch;
                    } catch (DateTimeParseException ex) {
                        logger.log(Level.WARNING, String.format("[%s] Assumption"
                                + " about the date time formatting of messages is "
                                + "not right. Here is the pair [ %s ]", PARSER_NAME, pair), ex);
                    }
                    break;
                    case TYPE:
                        switch (normalizedValue) {
                            case "incoming":
                                direction = CommunicationDirection.INCOMING;
                                break;
                            case "outgoing":
                                direction = CommunicationDirection.OUTGOING;
                                break;
                            case "deliver":
                            case "submit":
                            case "status report":
                                //Ignore for now.
                                break;
                            default:
                                logger.log(Level.WARNING, String.format("[%s] Unrecognized "
                                        + " value for key pair [ %s ].", PARSER_NAME, pair));
                        }
                        break;
                    case STATUS:
                        switch (normalizedValue) {
                            case "read":
                                readStatus = MessageReadStatus.READ;
                                break;
                            case "unread":
                                readStatus = MessageReadStatus.UNREAD;
                                break;
                            case "deleted":
                                otherAttributes.add(new BlackboardAttribute(
                                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ISDELETED,
                                        PARSER_NAME, pair.getValue()));
                                break;
                            case "sending failed":
                            case "unsent":
                            case "sent":
                                //Ignoring for now.
                                break;
                            default:
                                logger.log(Level.WARNING, String.format("[%s] Unrecognized "
                                        + " value for key pair [ %s ].", PARSER_NAME, pair));
                        }
                        break;
                    case TEXT:
                    case MESSAGE:
                        text = pair.getValue();
                        break;
                    case DIRECTION:
                        switch (normalizedValue) {
                            case "incoming":
                                direction = CommunicationDirection.INCOMING;
                                break;
                            case "outgoing":
                                direction = CommunicationDirection.OUTGOING;
                                break;
                            default:
                                direction = CommunicationDirection.UNKNOWN;
                                break;
                        }
                        break;
                    case SERVICE_CENTER:
                        if (!XRYUtils.isPhoneValid(pair.getValue())) {
                            continue;
                        }

                        otherAttributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER,
                                PARSER_NAME, pair.getValue()));
                        break;
                    default:
                        //Otherwise, the XryKey enum contains the correct BlackboardAttribute
                        //type.
                        if (key.getType() != null) {
                            otherAttributes.add(new BlackboardAttribute(key.getType(),
                                    PARSER_NAME, pair.getValue()));
                        } else {
                            logger.log(Level.INFO, String.format("[%s] Key value pair "
                                    + "(in brackets) [ %s ] was recognized but "
                                    + "more data or time is needed to finish implementation. Discarding... ",
                                    PARSER_NAME, pair));
                        }
                }
            }

            CommunicationArtifactsHelper helper = new CommunicationArtifactsHelper(
                    currentCase, PARSER_NAME, parent, Account.Type.PHONE, null);

            helper.addMessage(messageType, direction, senderId, recipientIdsList,
                    dateTime, readStatus, subject, text, threadId, otherAttributes);
        }
    }

    /**
     * Extracts all pairs from the XRY Entity. This function will unify any
     * segmented text, if need be.
     */
    private List<XRYKeyValuePair> getXRYKeyValuePairs(String xryEntity,
            XRYFileReader reader, Set<Integer> referenceValues) throws IOException {
        String[] xryLines = xryEntity.split("\n");
        //First line of the entity is the title, each XRY entity is non-empty.
        logger.log(Level.INFO, String.format("[XRY DSP] Processing [ %s ]", xryLines[0]));

        List<XRYKeyValuePair> pairs = new ArrayList<>();

        //Count the key value pairs in the XRY entity.
        int keyCount = getCountOfKeyValuePairs(xryLines);
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
            if (pair.hasKey(XryKey.TEXT.getDisplayName())
                    || pair.hasKey(XryKey.MESSAGE.getDisplayName())) {
                String segmentedText = getSegmentedText(xryLines, reader, referenceValues);
                pair = new XRYKeyValuePair(pair.getKey(),
                        //Assume text is segmented by word.
                        pair.getValue() + " " + segmentedText,
                        pair.getNamespace());
            }

            pairs.add(pair);
        }

        return pairs;
    }

    /**
     * Counts the key value pairs in an XRY entity. Skips counting the first
     * line as it is assumed to be the title.
     */
    private Integer getCountOfKeyValuePairs(String[] xryEntity) {
        int count = 0;
        for (int i = 1; i < xryEntity.length; i++) {
            if (XRYKeyValuePair.isPair(xryEntity[i])) {
                count++;
            }
        }
        return count;
    }

    /**
     * Builds up segmented message entities so that the text is unified for a
     * single artifact.
     *
     * @param reader               File reader that is producing XRY entities.
     * @param referenceNumbersSeen All known references numbers up until this
     *                             point.
     * @param xryEntity            The source XRY entity.
     *
     * @return
     *
     * @throws IOException
     */
    private String getSegmentedText(String[] xryEntity, XRYFileReader reader,
            Set<Integer> referenceNumbersSeen) throws IOException {
        Optional<Integer> referenceNumber = getMetaKeyValue(xryEntity, XryMetaKey.REFERENCE_NUMBER);
        //Check if there is any segmented text.
        if (!referenceNumber.isPresent()) {
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
        if (!segmentNumber.isPresent()) {
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

            if (!nextReferenceNumber.isPresent()
                    || !Objects.equals(nextReferenceNumber, referenceNumber)) {
                //Don't consume the next entity. It is not related
                //to the current message thread.
                break;
            }

            //Consume the entity, it is a part of the message thread.
            reader.nextEntity();

            Optional<Integer> nextSegmentNumber = getMetaKeyValue(nextEntityLines, XryMetaKey.SEGMENT_NUMBER);

            logger.log(Level.INFO, String.format("[XRY DSP] Processing [ %s ] "
                    + "segment with reference number [ %d ]", nextEntityLines[0], referenceNumber.get()));

            if (!nextSegmentNumber.isPresent()) {
                logger.log(Level.SEVERE, String.format("[XRY DSP] Segment with reference"
                        + " number [ %d ] did not have a segment number associated with it."
                        + " It cannot be determined if the reconstructed text will be in order.", referenceNumber.get()));
            } else if (nextSegmentNumber.get() != currentSegmentNumber + 1) {
                logger.log(Level.SEVERE, String.format("[XRY DSP] Contiguous "
                        + "segments are not ascending incrementally. Encountered "
                        + "segment [ %d ] after segment [ %d ]. This means the reconstructed "
                        + "text will be out of order.", nextSegmentNumber.get(), currentSegmentNumber));
            }

            int keyCount = getCountOfKeyValuePairs(nextEntityLines);
            for (int i = 1; i <= keyCount; i++) {
                XRYKeyValuePair pair = getKeyValuePairByIndex(nextEntityLines, i).get();
                if (pair.hasKey(XryKey.TEXT.getDisplayName())
                        || pair.hasKey(XryKey.MESSAGE.getDisplayName())) {
                    segmentedText.append(pair.getValue()).append(' ');
                }
            }

            if (nextSegmentNumber.isPresent()) {
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
     * Extracts the value of the XRY meta key, if any.
     *
     * @param xryLines XRY entity to extract from.
     * @param metaKey  The key type to extract.
     *
     * @return
     */
    private Optional<Integer> getMetaKeyValue(String[] xryLines, XryMetaKey metaKey) {
        for (String xryLine : xryLines) {
            if (!XRYKeyValuePair.isPair(xryLine)) {
                continue;
            }

            XRYKeyValuePair pair = XRYKeyValuePair.from(xryLine);
            if (pair.hasKey(metaKey.getDisplayName())) {
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
     * Extracts the ith XRY Key Value pair in the XRY Entity.
     *
     * The total number of pairs can be determined via
     * getCountOfKeyValuePairs().
     *
     * @param xryLines XRY entity.
     * @param index    The requested Key Value pair.
     *
     * @return
     */
    private Optional<XRYKeyValuePair> getKeyValuePairByIndex(String[] xryLines, int index) {
        int pairsParsed = 0;
        String namespace = "";
        for (int i = 1; i < xryLines.length; i++) {
            String xryLine = xryLines[i];
            if (XryNamespace.contains(xryLine)) {
                namespace = xryLine.trim();
                continue;
            }

            if (!XRYKeyValuePair.isPair(xryLine)) {
                logger.log(Level.SEVERE, String.format("[XRY DSP] Expected a key value "
                        + "pair on this line (in brackets) [ %s ], but one was not detected."
                        + " Discarding...", xryLine));
                continue;
            }

            XRYKeyValuePair pair = XRYKeyValuePair.from(xryLine);
            String value = pair.getValue();
            //Build up multiple lines.
            for (; (i + 1) < xryLines.length
                    && !XRYKeyValuePair.isPair(xryLines[i + 1])
                    && !XryNamespace.contains(xryLines[i + 1]); i++) {
                String continuedValue = xryLines[i + 1].trim();
                //Assume multi lined values are split by word.
                value = value + " " + continuedValue;
            }

            pair = new XRYKeyValuePair(pair.getKey(), value, namespace);
            pairsParsed++;
            if (pairsParsed == index) {
                return Optional.of(pair);
            }
        }

        return Optional.empty();
    }
}
