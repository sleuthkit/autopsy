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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import static org.sleuthkit.autopsy.datasourceprocessors.xry.AbstractSingleEntityParser.PARSER_NAME;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper;

/**
 * Parses XRY Contacts-Contacts files and creates artifacts.
 */
final class XRYContactsFileParser extends AbstractSingleEntityParser {

    private static final Logger logger = Logger.getLogger(XRYContactsFileParser.class.getName());

    @Override
    boolean canProcess(XRYKeyValuePair pair) {
        return XryKey.contains(pair.getKey());
    }

    @Override
    boolean isNamespace(String nameSpace) {
        //No namespaces are currently known for this report type.
        return false;
    }

    @Override
    void makeArtifact(List<XRYKeyValuePair> keyValuePairs, Content parent, SleuthkitCase currentCase) throws TskCoreException, Blackboard.BlackboardException {
        // Transform all the data from XRY land into the appropriate CommHelper
        // data types.
        String contactName = null;
        String phoneNumber = null;
        String homePhoneNumber = null;
        String mobilePhoneNumber = null;
        String emailAddr = null;
        boolean hasAnEmail = false;
        final Collection<BlackboardAttribute> additionalAttributes = new ArrayList<>();

        for (XRYKeyValuePair pair : keyValuePairs) {
            XryKey xryKey = XryKey.fromDisplayName(pair.getKey());
            switch (xryKey) {
                case NAME:
                    if (contactName != null) {
                        additionalAttributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, PARSER_NAME, pair.getValue()));
                    } else {
                        contactName = pair.getValue();
                    }
                    break;
                case TEL:
                    if (!XRYUtils.isPhoneValid(pair.getValue())) {
                        continue;
                    }

                    if (phoneNumber != null) {
                        additionalAttributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER, PARSER_NAME, pair.getValue()));
                    } else {
                        phoneNumber = pair.getValue();
                    }
                    break;
                case MOBILE:
                    if (!XRYUtils.isPhoneValid(pair.getValue())) {
                        continue;
                    }

                    if (mobilePhoneNumber != null) {
                        additionalAttributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_MOBILE, PARSER_NAME, pair.getValue()));
                    } else {
                        mobilePhoneNumber = pair.getValue();
                    }
                    break;
                case HOME:
                    if (!XRYUtils.isPhoneValid(pair.getValue())) {
                        continue;
                    }

                    if (homePhoneNumber != null) {
                        additionalAttributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_HOME, PARSER_NAME, pair.getValue()));
                    } else {
                        homePhoneNumber = pair.getValue();
                    }
                    break;
                case EMAIL_HOME:
                    if (!XRYUtils.isEmailValid(pair.getValue())) {
                        continue;
                    }

                    hasAnEmail = true;
                    additionalAttributes.add(new BlackboardAttribute(
                            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_HOME,
                            PARSER_NAME, pair.getValue()));
                    break;
                default:
                    //Otherwise, the XryKey enum contains the correct BlackboardAttribute
                    //type.
                    if (xryKey.getType() != null) {
                        additionalAttributes.add(new BlackboardAttribute(xryKey.getType(),
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
        if (phoneNumber != null || homePhoneNumber != null || mobilePhoneNumber != null || hasAnEmail) {
            CommunicationArtifactsHelper helper = new CommunicationArtifactsHelper(
                    currentCase, PARSER_NAME, parent, Account.Type.DEVICE, null);

            helper.addContact(contactName, phoneNumber, homePhoneNumber,
                    mobilePhoneNumber, emailAddr, additionalAttributes);
        } else {
            // Just create an artifact with the attributes that we do have.
            if (!additionalAttributes.isEmpty()) {
                BlackboardArtifact artifact = parent.newDataArtifact(new BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT), additionalAttributes);

                currentCase.getBlackboard().postArtifact(artifact, PARSER_NAME, null);
            }
        }
    }

    /**
     * Enum containing all known keys for contacts and their corresponding
     * blackboard attribute. Some keys are intentionally null, because they are
     * handled as special cases in makeArtifact(). Some are null because there's
     * not an appropriate attribute type.
     */
    private enum XryKey {
        NAME("name", null),
        TEL("tel", null),
        MOBILE("mobile", null),
        HOME("home", null),
        RELATED_APPLICATION("related application", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME),
        ADDRESS_HOME("address home", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION),
        EMAIL_HOME("email home", null),
        DELETED("deleted", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ISDELETED),
        //Ignoring or need more information to decide.
        STORAGE("storage", null),
        OTHER("other", null),
        PICTURE("picture", null),
        INDEX("index", null),
        ACCOUNT_NAME("account name", null);

        private final String name;
        private final BlackboardAttribute.ATTRIBUTE_TYPE type;

        XryKey(String name, BlackboardAttribute.ATTRIBUTE_TYPE type) {
            this.name = name;
            this.type = type;
        }

        BlackboardAttribute.ATTRIBUTE_TYPE getType() {
            return type;
        }

        /**
         * Indicates if the display name of the XRY key is a recognized type.
         */
        static boolean contains(String key) {
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
        static XryKey fromDisplayName(String key) {
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
}
