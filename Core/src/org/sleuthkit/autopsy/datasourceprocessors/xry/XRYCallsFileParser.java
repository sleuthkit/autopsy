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

import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.Blackboard.BlackboardException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.InvalidAccountIDException;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper;
import org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper.CallMediaType;
import org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper.CommunicationDirection;

/**
 * Parses XRY Calls files and creates artifacts.
 */
final class XRYCallsFileParser extends AbstractSingleEntityParser {

    private static final Logger logger = Logger.getLogger(XRYCallsFileParser.class.getName());

    /**
     * All of the known XRY keys for call reports and their corresponding
     * blackboard attribute types, if any.
     */
    private enum XryKey {
        NAME_MATCHED("name (matched)", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME),
        TIME("time", null),
        DIRECTION("direction", null),
        CALL_TYPE("call type", null),
        NUMBER("number", null),
        TEL("tel", null),
        TO("to", null),
        FROM("from", null),
        DELETED("deleted", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ISDELETED),
        DURATION("duration", null),
        STORAGE("storage", null),
        INDEX("index", null),
        TYPE("type", null),
        NAME("name", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME);

        private final String name;
        private final BlackboardAttribute.ATTRIBUTE_TYPE type;

        XryKey(String name, BlackboardAttribute.ATTRIBUTE_TYPE type) {
            this.name = name;
            this.type = type;
        }

        public BlackboardAttribute.ATTRIBUTE_TYPE getType() {
            return type;
        }

        /**
         * Indicates if the display name of the XRY key is a recognized type.
         */
        public static boolean contains(String key) {
            try {
                XryKey.fromDisplayName(key);
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
         */
        public static XryKey fromDisplayName(String key) {
            String normalizedKey = key.trim().toLowerCase();
            for (XryKey keyChoice : XryKey.values()) {
                if (normalizedKey.equals(keyChoice.name)) {
                    return keyChoice;
                }
            }

            throw new IllegalArgumentException(String.format("Key [%s] was not found."
                    + " All keys should be tested with contains.", key));
        }
    }

    /**
     * All known XRY namespaces for call reports.
     */
    private enum XryNamespace {
        TO("to"),
        FROM("from"),
        NONE(null);

        private final String name;

        XryNamespace(String name) {
            this.name = name;
        }

        /**
         * Indicates if the display name of the XRY namespace is a recognized
         * type.
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
         */
        public static XryNamespace fromDisplayName(String xryNamespace) {
            String normalizedNamespace = xryNamespace.trim().toLowerCase();
            for (XryNamespace keyChoice : XryNamespace.values()) {
                if (normalizedNamespace.equals(keyChoice.name)) {
                    return keyChoice;
                }
            }

            throw new IllegalArgumentException(String.format("Key [%s] was not found."
                    + " All keys should be tested with contains.", xryNamespace));
        }
    }

    @Override
    boolean canProcess(XRYKeyValuePair pair) {
        return XryKey.contains(pair.getKey());
    }

    @Override
    boolean isNamespace(String nameSpace) {
        return XryNamespace.contains(nameSpace);
    }

    @Override
    void makeArtifact(List<XRYKeyValuePair> keyValuePairs, Content parent, SleuthkitCase currentCase) throws TskCoreException, BlackboardException {
        // Transform all the data from XRY land into the appropriate CommHelper
        // data types.
        String callerId = null;
        final Collection<String> calleeList = new ArrayList<>();
        CommunicationDirection direction = CommunicationDirection.UNKNOWN;
        long startTime = 0L;
        final long endTime = 0L;
        final CallMediaType callType = CallMediaType.UNKNOWN;
        final Collection<BlackboardAttribute> otherAttributes = new ArrayList<>();

        for (XRYKeyValuePair pair : keyValuePairs) {
            XryKey xryKey = XryKey.fromDisplayName(pair.getKey());
            XryNamespace xryNamespace = XryNamespace.NONE;
            if (XryNamespace.contains(pair.getNamespace())) {
                xryNamespace = XryNamespace.fromDisplayName(pair.getNamespace());
            }

            switch (xryKey) {
                case TEL:
                case NUMBER:
                    if (!XRYUtils.isPhoneValid(pair.getValue())) {
                        continue;
                    }

                    // Apply namespace or direction
                    if (xryNamespace == XryNamespace.FROM || direction == CommunicationDirection.INCOMING) {
                        callerId = pair.getValue();
                    } else if (xryNamespace == XryNamespace.TO || direction == CommunicationDirection.OUTGOING) {
                        calleeList.add(pair.getValue());
                    } else {
                        otherAttributes.add(new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER,
                                PARSER_NAME, pair.getValue()));
                    }
                    break;
                // Although confusing, as these are also 'name spaces', it appears
                // later versions of XRY just made these standardized lines.
                case TO:
                    if (!XRYUtils.isPhoneValid(pair.getValue())) {
                        continue;
                    }

                    calleeList.add(pair.getValue());
                    break;
                case FROM:
                    if (!XRYUtils.isPhoneValid(pair.getValue())) {
                        continue;
                    }

                    callerId = pair.getValue();
                    break;
                case TIME:
                    try {
                    //Tranform value to seconds since epoch
                    long dateTimeSinceEpoch = XRYUtils.calculateSecondsSinceEpoch(pair.getValue());
                    startTime = dateTimeSinceEpoch;
                } catch (DateTimeParseException ex) {
                    logger.log(Level.WARNING, String.format("[XRY DSP] Assumption"
                            + " about the date time formatting of call logs is "
                            + "not right. Here is the value [ %s ]", pair.getValue()), ex);
                }
                break;
                case DIRECTION:
                    String directionString = pair.getValue().toLowerCase();
                    if (directionString.equals("incoming")) {
                        direction = CommunicationDirection.INCOMING;
                    } else {
                        direction = CommunicationDirection.OUTGOING;
                    }
                    break;
                case TYPE:
                    String typeString = pair.getValue();
                    if (typeString.equalsIgnoreCase("received")) {
                        direction = CommunicationDirection.INCOMING;
                    } else if (typeString.equalsIgnoreCase("dialed")) {
                        direction = CommunicationDirection.OUTGOING;
                    }
                    break;
                default:
                    //Otherwise, the XryKey enum contains the correct BlackboardAttribute
                    //type.
                    if (xryKey.getType() != null) {
                        otherAttributes.add(new BlackboardAttribute(xryKey.getType(),
                                PARSER_NAME, pair.getValue()));
                    }

                    logger.log(Level.INFO, String.format("[XRY DSP] Key value pair "
                            + "(in brackets) [ %s ] was recognized but "
                            + "more data or time is needed to finish implementation. Discarding... ",
                            pair));
            }
        }

        // Make sure we have the required fields, otherwise the CommHelper will
        // complain about illegal arguments.
        // These are all the invalid combinations.
        if (callerId == null && calleeList.isEmpty()
                || direction == CommunicationDirection.INCOMING && callerId == null
                || direction == CommunicationDirection.OUTGOING && calleeList.isEmpty()) {

            // If the combo is invalid, just make an artifact with what we've got.
            if (direction != CommunicationDirection.UNKNOWN) {
                otherAttributes.add(new BlackboardAttribute(
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION,
                        PARSER_NAME, direction.getDisplayName()));
            }

            if (startTime > 0L) {
                otherAttributes.add(new BlackboardAttribute(
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START,
                        PARSER_NAME, startTime));
            }

            // If the DIRECTION check failed, just manually create accounts
            // for these phones. Note, there is no need to create relationships.
            // If both callerId and calleeList were non-null/non-empty, then 
            // it would have been a valid combination.
            if (callerId != null) {
                try {
                    currentCase.getCommunicationsManager().createAccountFileInstance(
                            Account.Type.PHONE, callerId, PARSER_NAME, parent, null, null);
                } catch (InvalidAccountIDException ex) {
                    logger.log(Level.WARNING, String.format("Invalid account identifier %s", callerId), ex);
                }

                otherAttributes.add(new BlackboardAttribute(
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER,
                        PARSER_NAME, callerId));
            }

            for (String phone : calleeList) {
                try {
                    currentCase.getCommunicationsManager().createAccountFileInstance(
                            Account.Type.PHONE, phone, PARSER_NAME, parent, null, null);
                } catch (InvalidAccountIDException ex) {
                    logger.log(Level.WARNING, String.format("Invalid account identifier %s", phone), ex);
                }

                otherAttributes.add(new BlackboardAttribute(
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER,
                        PARSER_NAME, phone));
            }

            if (!otherAttributes.isEmpty()) {
                BlackboardArtifact artifact = parent.newDataArtifact(new BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG), otherAttributes);

                currentCase.getBlackboard().postArtifact(artifact, PARSER_NAME, null);
            }
        } else {

            // Otherwise we can safely use the helper.
            CommunicationArtifactsHelper helper = new CommunicationArtifactsHelper(
                    currentCase, PARSER_NAME, parent, Account.Type.PHONE, null);

            helper.addCalllog(direction, callerId, calleeList, startTime,
                    endTime, callType, otherAttributes);
        }
    }
}
