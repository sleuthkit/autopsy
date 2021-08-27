/*
 * Central Repository
 *
 * Copyright 2017-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoAccount.CentralRepoAccountType;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.HashUtility;
import org.sleuthkit.datamodel.InvalidAccountIDException;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.OsAccountInstance;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Utility class for working with correlation attributes in the central
 * repository.
 */
public class CorrelationAttributeUtil {

    private static final Logger logger = Logger.getLogger(CorrelationAttributeUtil.class.getName());
    private static final List<String> domainsToSkip = Arrays.asList("localhost", "127.0.0.1");

    // artifact ids that specifically have a TSK_DOMAIN attribute that should be handled by CR
    private static Set<Integer> DOMAIN_ARTIFACT_TYPE_IDS = new HashSet<>(Arrays.asList(
            ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID(),
            ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID(),
            ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID(),
            ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID(),
            ARTIFACT_TYPE.TSK_WEB_CACHE.getTypeID()
    ));

    /**
     * Gets a string that is expected to be the same string that is stored in
     * the correlation_types table in the central repository as the display name
     * for the email address correlation attribute type. This string is
     * duplicated in the CorrelationAttributeInstance class.
     *
     * TODO (Jira-6088): We should not have multiple deifnitions of this string.
     *
     * @return The display name of the email address correlation attribute type.
     */
    @Messages({"CorrelationAttributeUtil.emailaddresses.text=Email Addresses"})
    private static String getEmailAddressAttrDisplayName() {
        return Bundle.CorrelationAttributeUtil_emailaddresses_text();
    }

    // Defines which artifact types act as the sources for CR data.
    // Most notably, does not include KEYWORD HIT, CALLLOGS, MESSAGES, CONTACTS
    // TSK_INTERESTING_ARTIFACT_HIT (See JIRA-6129 for more details on the
    // interesting artifact hit).
    // IMPORTANT: This set should be updated for new artifacts types that need to
    // be inserted into the CR.
    private static final Set<Integer> SOURCE_TYPES_FOR_CR_INSERT = new HashSet<Integer>() {
        {
            addAll(DOMAIN_ARTIFACT_TYPE_IDS);

            add(ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID());
            add(ARTIFACT_TYPE.TSK_WIFI_NETWORK.getTypeID());
            add(ARTIFACT_TYPE.TSK_WIFI_NETWORK_ADAPTER.getTypeID());
            add(ARTIFACT_TYPE.TSK_BLUETOOTH_PAIRING.getTypeID());
            add(ARTIFACT_TYPE.TSK_BLUETOOTH_ADAPTER.getTypeID());
            add(ARTIFACT_TYPE.TSK_DEVICE_INFO.getTypeID());
            add(ARTIFACT_TYPE.TSK_SIM_ATTACHED.getTypeID());
            add(ARTIFACT_TYPE.TSK_WEB_FORM_ADDRESS.getTypeID());
            add(ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID());
            add(ARTIFACT_TYPE.TSK_INSTALLED_PROG.getTypeID());
        }
    };

    /**
     * Makes zero to many correlation attribute instances from the attributes of
     * artifacts that have correlatable data. The intention of this method is to
     * use the results to save to the CR, not to correlate with them. If you
     * want to correlate, please use makeCorrAttrsForCorrelation. An artifact
     * that can have correlatable data != An artifact that should be the source
     * of data in the CR, so results may be un-necessarily incomplete.
     *
     * @param artifact An artifact.
     *
     * @return A list, possibly empty, of correlation attribute instances for
     *         the artifact.
     */
    public static List<CorrelationAttributeInstance> makeCorrAttrsToSave(BlackboardArtifact artifact) {
        if (SOURCE_TYPES_FOR_CR_INSERT.contains(artifact.getArtifactTypeID())) {
            // Restrict the correlation attributes to use for saving.
            // The artifacts which are suitable for saving are a subset of the
            // artifacts that are suitable for correlating.
            return makeCorrAttrsForCorrelation(artifact);
        }
        // Return an empty collection.
        return new ArrayList<>();
    }

    /**
     * Makes zero to many correlation attribute instances from the attributes of
     * artifacts that have correlatable data. The intention of this method is to
     * use the results to correlate with, not to save. If you want to save,
     * please use makeCorrAttrsToSave. An artifact that can have correlatable
     * data != An artifact that should be the source of data in the CR, so
     * results may be too lenient.
     *
     * IMPORTANT: The correlation attribute instances are NOT added to the
     * central repository by this method.
     *
     * TODO (Jira-6088): The methods in this low-level, utility class should
     * throw exceptions instead of logging them. The reason for this is that the
     * clients of the utility class, not the utility class itself, should be in
     * charge of error handling policy, per the Autopsy Coding Standard. Note
     * that clients of several of these methods currently cannot determine
     * whether receiving a null return value is an error or not, plus null
     * checking is easy to forget, while catching exceptions is enforced.
     *
     * @param artifact An artifact.
     *
     * @return A list, possibly empty, of correlation attribute instances for
     *         the artifact.
     */
    public static List<CorrelationAttributeInstance> makeCorrAttrsForCorrelation(BlackboardArtifact artifact) {
        List<CorrelationAttributeInstance> correlationAttrs = new ArrayList<>();
        try {
            BlackboardArtifact sourceArtifact = getCorrAttrSourceArtifact(artifact);
            if (sourceArtifact != null) {

                List<BlackboardAttribute> attributes = sourceArtifact.getAttributes();

                int artifactTypeID = sourceArtifact.getArtifactTypeID();
                if (artifactTypeID == ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
                    BlackboardAttribute setNameAttr = getAttribute(attributes, new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME));
                    if (setNameAttr != null && CorrelationAttributeUtil.getEmailAddressAttrDisplayName().equals(setNameAttr.getValueString())) {
                        makeCorrAttrFromArtifactAttr(correlationAttrs, sourceArtifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD, CorrelationAttributeInstance.EMAIL_TYPE_ID, attributes);
                    }
                } else if (DOMAIN_ARTIFACT_TYPE_IDS.contains(artifactTypeID)) {
                    BlackboardAttribute domainAttr = getAttribute(attributes, new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DOMAIN));
                    if ((domainAttr != null)
                            && !domainsToSkip.contains(domainAttr.getValueString())) {
                        makeCorrAttrFromArtifactAttr(correlationAttrs, sourceArtifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN, CorrelationAttributeInstance.DOMAIN_TYPE_ID, attributes);
                    }
                } else if (artifactTypeID == ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID()) {
                    // prefetch all the information as we will be calling makeCorrAttrFromArtifactAttr() multiple times
                    Content sourceContent = Case.getCurrentCaseThrows().getSleuthkitCase().getContentById(sourceArtifact.getObjectID());
                    Content dataSource = sourceContent.getDataSource();
                    makeCorrAttrFromArtifactAttr(correlationAttrs, sourceArtifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_ID, CorrelationAttributeInstance.USBID_TYPE_ID,
                            attributes, sourceContent, dataSource);
                    makeCorrAttrFromArtifactAttr(correlationAttrs, sourceArtifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MAC_ADDRESS, CorrelationAttributeInstance.MAC_TYPE_ID,
                            attributes, sourceContent, dataSource);

                } else if (artifactTypeID == ARTIFACT_TYPE.TSK_WIFI_NETWORK.getTypeID()) {
                    makeCorrAttrFromArtifactAttr(correlationAttrs, sourceArtifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SSID, CorrelationAttributeInstance.SSID_TYPE_ID, attributes);

                } else if (artifactTypeID == ARTIFACT_TYPE.TSK_WIFI_NETWORK_ADAPTER.getTypeID()
                        || artifactTypeID == ARTIFACT_TYPE.TSK_BLUETOOTH_PAIRING.getTypeID()
                        || artifactTypeID == ARTIFACT_TYPE.TSK_BLUETOOTH_ADAPTER.getTypeID()) {
                    makeCorrAttrFromArtifactAttr(correlationAttrs, sourceArtifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MAC_ADDRESS, CorrelationAttributeInstance.MAC_TYPE_ID, attributes);

                } else if (artifactTypeID == ARTIFACT_TYPE.TSK_DEVICE_INFO.getTypeID()) {
                    // prefetch all the information as we will be calling makeCorrAttrFromArtifactAttr() multiple times
                    Content sourceContent = Case.getCurrentCaseThrows().getSleuthkitCase().getContentById(sourceArtifact.getObjectID());
                    Content dataSource = sourceContent.getDataSource();
                    makeCorrAttrFromArtifactAttr(correlationAttrs, sourceArtifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_IMEI, CorrelationAttributeInstance.IMEI_TYPE_ID,
                            attributes, sourceContent, dataSource);
                    makeCorrAttrFromArtifactAttr(correlationAttrs, sourceArtifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_IMSI, CorrelationAttributeInstance.IMSI_TYPE_ID,
                            attributes, sourceContent, dataSource);
                    makeCorrAttrFromArtifactAttr(correlationAttrs, sourceArtifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ICCID, CorrelationAttributeInstance.ICCID_TYPE_ID,
                            attributes, sourceContent, dataSource);

                } else if (artifactTypeID == ARTIFACT_TYPE.TSK_SIM_ATTACHED.getTypeID()) {
                    // prefetch all the information as we will be calling makeCorrAttrFromArtifactAttr() multiple times
                    Content sourceContent = Case.getCurrentCaseThrows().getSleuthkitCase().getContentById(sourceArtifact.getObjectID());
                    Content dataSource = sourceContent.getDataSource();
                    makeCorrAttrFromArtifactAttr(correlationAttrs, sourceArtifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_IMSI, CorrelationAttributeInstance.IMSI_TYPE_ID,
                            attributes, sourceContent, dataSource);
                    makeCorrAttrFromArtifactAttr(correlationAttrs, sourceArtifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ICCID, CorrelationAttributeInstance.ICCID_TYPE_ID,
                            attributes, sourceContent, dataSource);

                } else if (artifactTypeID == ARTIFACT_TYPE.TSK_WEB_FORM_ADDRESS.getTypeID()) {
                    // prefetch all the information as we will be calling makeCorrAttrFromArtifactAttr() multiple times
                    Content sourceContent = Case.getCurrentCaseThrows().getSleuthkitCase().getContentById(sourceArtifact.getObjectID());
                    Content dataSource = sourceContent.getDataSource();
                    makeCorrAttrFromArtifactAttr(correlationAttrs, sourceArtifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER, CorrelationAttributeInstance.PHONE_TYPE_ID,
                            attributes, sourceContent, dataSource);
                    makeCorrAttrFromArtifactAttr(correlationAttrs, sourceArtifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL, CorrelationAttributeInstance.EMAIL_TYPE_ID,
                            attributes, sourceContent, dataSource);

                } else if (artifactTypeID == ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID()) {
                    makeCorrAttrFromAcctArtifact(correlationAttrs, sourceArtifact);

                } else if (artifactTypeID == ARTIFACT_TYPE.TSK_INSTALLED_PROG.getTypeID()) {
                    BlackboardAttribute setNameAttr = getAttribute(attributes, new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH));
                    String pathAttrString = null;
                    if (setNameAttr != null) {
                        pathAttrString = setNameAttr.getValueString();
                    }
                    if (pathAttrString != null && !pathAttrString.isEmpty()) {
                        makeCorrAttrFromArtifactAttr(correlationAttrs, sourceArtifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH, CorrelationAttributeInstance.INSTALLED_PROGS_TYPE_ID, attributes);
                    } else {
                        makeCorrAttrFromArtifactAttr(correlationAttrs, sourceArtifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME, CorrelationAttributeInstance.INSTALLED_PROGS_TYPE_ID, attributes);
                    }
                } else if (artifactTypeID == ARTIFACT_TYPE.TSK_CONTACT.getTypeID()
                        || artifactTypeID == ARTIFACT_TYPE.TSK_CALLLOG.getTypeID()
                        || artifactTypeID == ARTIFACT_TYPE.TSK_MESSAGE.getTypeID()) {
                    makeCorrAttrsFromCommunicationArtifacts(correlationAttrs, sourceArtifact, attributes);
                }
            }
        } catch (CorrelationAttributeNormalizationException ex) {
            logger.log(Level.WARNING, String.format("Error normalizing correlation attribute (%s)", artifact), ex); // NON-NLS
            return correlationAttrs;
        } catch (InvalidAccountIDException ex) {
            logger.log(Level.WARNING, String.format("Invalid account identifier (artifactID: %d)", artifact.getId())); // NON-NLS
            return correlationAttrs;
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, String.format("Error querying central repository (%s)", artifact), ex); // NON-NLS
            return correlationAttrs;
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Error getting querying case database (%s)", artifact), ex); // NON-NLS
            return correlationAttrs;
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Error getting current case", ex); // NON-NLS
            return correlationAttrs;
        }
        return correlationAttrs;
    }

    /**
     * Gets a specific attribute from a list of attributes.
     *
     * @param attributes    List of attributes
     * @param attributeType Attribute type of interest
     *
     * @return Attribute of interest, null if not found.
     *
     * @throws TskCoreException
     */
    private static BlackboardAttribute getAttribute(List<BlackboardAttribute> attributes, BlackboardAttribute.Type attributeType) throws TskCoreException {
        for (BlackboardAttribute attribute : attributes) {
            if (attribute.getAttributeType().equals(attributeType)) {
                return attribute;
            }
        }
        return null;
    }

    /**
     * Makes a correlation attribute instance from a phone number attribute of
     * an artifact.
     *
     * @param corrAttrInstances Correlation attributes will be added to this.
     * @param artifact          An artifact with a phone number attribute.
     *
     * @throws TskCoreException                           If there is an error
     *                                                    querying the case
     *                                                    database.
     * @throws CentralRepoException                       If there is an error
     *                                                    querying the central
     *                                                    repository.
     * @throws CorrelationAttributeNormalizationException If there is an error
     *                                                    in normalizing the
     *                                                    attribute.
     */
    private static void makeCorrAttrsFromCommunicationArtifacts(List<CorrelationAttributeInstance> corrAttrInstances, BlackboardArtifact artifact,
            List<BlackboardAttribute> attributes) throws TskCoreException, CentralRepoException, CorrelationAttributeNormalizationException {
        CorrelationAttributeInstance corrAttr = null;

        /*
         * Extract the phone number from the artifact attribute.
         */
        String value = null;
        if (null != getAttribute(attributes, new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER))) {
            value = getAttribute(attributes, new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER)).getValueString();
        } else if (null != getAttribute(attributes, new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM))) {
            value = getAttribute(attributes, new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM)).getValueString();
        } else if (null != getAttribute(attributes, new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO))) {
            value = getAttribute(attributes, new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO)).getValueString();
        }

        /*
         * Normalize the phone number.
         */
        if (value != null
                && CorrelationAttributeNormalizer.isValidPhoneNumber(value)) {

            value = CorrelationAttributeNormalizer.normalizePhone(value);
            corrAttr = makeCorrAttr(artifact, CentralRepository.getInstance().getCorrelationTypeById(CorrelationAttributeInstance.PHONE_TYPE_ID), value);
            if (corrAttr != null) {
                corrAttrInstances.add(corrAttr);
            }
        }
    }

    /**
     * Gets the associated artifact of a "meta-artifact" such as an "interesting
     * artifact hit" or "previously seen" artifact.
     *
     * @param artifact An artifact.
     *
     * @return The associated artifact if the input artifact is a
     *         "meta-artifact", otherwise the input artifact.
     *
     * @throws NoCurrentCaseException If there is no open case.
     * @throws TskCoreException       If there is an error querying thew case
     *                                database.
     */
    private static BlackboardArtifact getCorrAttrSourceArtifact(BlackboardArtifact artifact) throws NoCurrentCaseException, TskCoreException {
        BlackboardArtifact sourceArtifact = null;
        if (BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID() == artifact.getArtifactTypeID()) {
            BlackboardAttribute assocArtifactAttr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT));
            if (assocArtifactAttr != null) {
                sourceArtifact = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboardArtifact(assocArtifactAttr.getValueLong());
            }
        } else if (BlackboardArtifact.ARTIFACT_TYPE.TSK_PREVIOUSLY_SEEN.getTypeID() == artifact.getArtifactTypeID() 
                || BlackboardArtifact.ARTIFACT_TYPE.TSK_PREVIOUSLY_NOTABLE.getTypeID() == artifact.getArtifactTypeID()
                || BlackboardArtifact.ARTIFACT_TYPE.TSK_PREVIOUSLY_UNSEEN.getTypeID() == artifact.getArtifactTypeID()) {
            Content content = Case.getCurrentCaseThrows().getSleuthkitCase().getContentById(artifact.getObjectID());
            if (content instanceof DataArtifact) {
                sourceArtifact = (BlackboardArtifact) content;
            }
        }

        if (sourceArtifact == null) {
            sourceArtifact = artifact;
        }
        return sourceArtifact;
    }

    /**
     * Makes a correlation attribute instance for an account artifact.
     *
     * Also creates an account in the CR DB if it doesn't exist.
     *
     * IMPORTANT: The correlation attribute instance is NOT added to the central
     * repository by this method.
     *
     * @param corrAttrInstances A list of correlation attribute instances.
     * @param acctArtifact      An account artifact.
     *
     * @return The correlation attribute instance.
     */
    private static void makeCorrAttrFromAcctArtifact(List<CorrelationAttributeInstance> corrAttrInstances, BlackboardArtifact acctArtifact) throws InvalidAccountIDException, TskCoreException, CentralRepoException {

        // Get the account type from the artifact
        BlackboardAttribute accountTypeAttribute = acctArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE));
        String accountTypeStr = accountTypeAttribute.getValueString();

        // @@TODO Vik-6136: CR currently does not know of custom account types.  
        // Ensure there is a predefined account type for this account.
        Account.Type predefinedAccountType = Account.Type.PREDEFINED_ACCOUNT_TYPES.stream().filter(type -> type.getTypeName().equalsIgnoreCase(accountTypeStr)).findAny().orElse(null);

        // do not create any correlation attribute instance for a Device account
        if (Account.Type.DEVICE.getTypeName().equalsIgnoreCase(accountTypeStr) == false && predefinedAccountType != null) {

            // Get the corresponding CentralRepoAccountType from the database.
            Optional<CentralRepoAccountType> optCrAccountType = CentralRepository.getInstance().getAccountTypeByName(accountTypeStr);
            if (!optCrAccountType.isPresent()) {
                return;
            }
            CentralRepoAccountType crAccountType = optCrAccountType.get();

            int corrTypeId = crAccountType.getCorrelationTypeId();
            CorrelationAttributeInstance.Type corrType = CentralRepository.getInstance().getCorrelationTypeById(corrTypeId);

            // Get the account identifier
            BlackboardAttribute accountIdAttribute = acctArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ID));
            String accountIdStr = accountIdAttribute.getValueString();

            // add/get the account and get its accountId.
            CentralRepoAccount crAccount = CentralRepository.getInstance().getOrCreateAccount(crAccountType, accountIdStr);

            CorrelationAttributeInstance corrAttr = makeCorrAttr(acctArtifact, corrType, accountIdStr);
            if (corrAttr != null) {
                // set the account_id in correlation attribute
                corrAttr.setAccountId(crAccount.getId());
                corrAttrInstances.add(corrAttr);
            }
        }
    }

    /**
     * Makes a correlation attribute instance from a specified attribute of an
     * artifact. The correlation attribute instance is added to an input list.
     *
     * @param corrAttrInstances A list of correlation attribute instances.
     * @param artifact          An artifact.
     * @param artAttrType       The type of the atrribute of the artifact that
     *                          is to be made into a correlatin attribute
     *                          instance.
     * @param typeId            The type ID for the desired correlation
     *                          attribute instance.
     * @param sourceContent     The source content object.
     * @param dataSource        The data source content object.
     *
     * @throws CentralRepoException If there is an error querying the central
     *                              repository.
     * @throws TskCoreException     If there is an error querying the case
     *                              database.
     */
    private static void makeCorrAttrFromArtifactAttr(List<CorrelationAttributeInstance> corrAttrInstances, BlackboardArtifact artifact, ATTRIBUTE_TYPE artAttrType, int typeId,
            List<BlackboardAttribute> attributes, Content sourceContent, Content dataSource) throws CentralRepoException, TskCoreException {

        BlackboardAttribute attribute = getAttribute(attributes, new BlackboardAttribute.Type(artAttrType));
        if (attribute != null) {
            String value = attribute.getValueString();
            if ((null != value) && (value.isEmpty() == false)) {
                CorrelationAttributeInstance inst = makeCorrAttr(artifact, CentralRepository.getInstance().getCorrelationTypeById(typeId), value, sourceContent, dataSource);
                if (inst != null) {
                    corrAttrInstances.add(inst);
                }
            }
        }
    }

    /**
     * Makes a correlation attribute instance from a specified attribute of an
     * artifact. The correlation attribute instance is added to an input list.
     *
     * @param corrAttrInstances A list of correlation attribute instances.
     * @param artifact          An artifact.
     * @param artAttrType       The type of the atrribute of the artifact that
     *                          is to be made into a correlatin attribute
     *                          instance.
     * @param typeId            The type ID for the desired correlation
     *                          attribute instance.
     *
     * @throws CentralRepoException If there is an error querying the central
     *                              repository.
     * @throws TskCoreException     If there is an error querying the case
     *                              database.
     */
    private static void makeCorrAttrFromArtifactAttr(List<CorrelationAttributeInstance> corrAttrInstances, BlackboardArtifact artifact, ATTRIBUTE_TYPE artAttrType, int typeId,
            List<BlackboardAttribute> attributes) throws CentralRepoException, TskCoreException {

        makeCorrAttrFromArtifactAttr(corrAttrInstances, artifact, artAttrType, typeId, attributes, null, null);
    }

    /**
     * Makes a correlation attribute instance of a given type from an artifact.
     *
     * @param artifact        The artifact.
     * @param correlationType the correlation attribute type.
     * @param value           The correlation attribute value.
     *
     * TODO (Jira-6088): The methods in this low-level, utility class should
     * throw exceptions instead of logging them. The reason for this is that the
     * clients of the utility class, not the utility class itself, should be in
     * charge of error handling policy, per the Autopsy Coding Standard. Note
     * that clients of several of these methods currently cannot determine
     * whether receiving a null return value is an error or not, plus null
     * checking is easy to forget, while catching exceptions is enforced.
     *
     * @return The correlation attribute instance or null, if an error occurred.
     */
    private static CorrelationAttributeInstance makeCorrAttr(BlackboardArtifact artifact, CorrelationAttributeInstance.Type correlationType, String value) {
        return makeCorrAttr(artifact, correlationType, value, null, null);
    }

    /**
     * Makes a correlation attribute instance of a given type from an artifact.
     *
     * @param artifact        The artifact.
     * @param correlationType the correlation attribute type.
     * @param value           The correlation attribute value.
     * @param sourceContent   The source content object.
     * @param dataSource      The data source content object.
     *
     * TODO (Jira-6088): The methods in this low-level, utility class should
     * throw exceptions instead of logging them. The reason for this is that the
     * clients of the utility class, not the utility class itself, should be in
     * charge of error handling policy, per the Autopsy Coding Standard. Note
     * that clients of several of these methods currently cannot determine
     * whether receiving a null return value is an error or not, plus null
     * checking is easy to forget, while catching exceptions is enforced.
     *
     * @return The correlation attribute instance or null, if an error occurred.
     */
    private static CorrelationAttributeInstance makeCorrAttr(BlackboardArtifact artifact, CorrelationAttributeInstance.Type correlationType, String value,
            Content sourceContent, Content dataSource) {
        try {

            if (sourceContent == null) {
                sourceContent = Case.getCurrentCaseThrows().getSleuthkitCase().getContentById(artifact.getObjectID());
            }
            if (null == sourceContent) {
                logger.log(Level.SEVERE, "Error creating artifact instance of type {0}. Failed to load content with ID: {1} associated with artifact with ID: {2}",
                        new Object[]{correlationType.getDisplayName(), artifact.getObjectID(), artifact.getId()}); // NON-NLS
                return null;
            }

            if (dataSource == null) {
                dataSource = sourceContent.getDataSource();
            }
            if (dataSource == null) {
                logger.log(Level.SEVERE, "Error creating artifact instance of type {0}. Failed to load data source for content with ID: {1}",
                        new Object[]{correlationType.getDisplayName(), artifact.getObjectID()}); // NON-NLS
                return null;
            }

            CorrelationCase correlationCase = CentralRepository.getInstance().getCase(Case.getCurrentCaseThrows());
            if (artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_INSTALLED_PROG.getTypeID()) {
                return new CorrelationAttributeInstance(
                        correlationType,
                        value,
                        correlationCase,
                        CorrelationDataSource.fromTSKDataSource(correlationCase, dataSource),
                        "",
                        "",
                        TskData.FileKnown.UNKNOWN,
                        sourceContent.getId());
            } else {
                if (!(sourceContent instanceof AbstractFile)) {
                    logger.log(Level.SEVERE, "Error creating artifact instance of type {0}. Source content of artifact with ID: {1} is not an AbstractFile",
                            new Object[]{correlationType.getDisplayName(), artifact.getId()});
                    return null;
                }
                AbstractFile bbSourceFile = (AbstractFile) sourceContent;

                return new CorrelationAttributeInstance(
                        correlationType,
                        value,
                        correlationCase,
                        CorrelationDataSource.fromTSKDataSource(correlationCase, dataSource),
                        bbSourceFile.getParentPath() + bbSourceFile.getName(),
                        "",
                        TskData.FileKnown.UNKNOWN,
                        bbSourceFile.getId());
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Error getting querying case database (%s)", artifact), ex); // NON-NLS
            return null;
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, String.format("Error querying central repository (%s)", artifact), ex); // NON-NLS
            return null;
        } catch (CorrelationAttributeNormalizationException ex) {
            logger.log(Level.WARNING, String.format("Error creating correlation attribute instance (%s)", artifact), ex); // NON-NLS
            return null;
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Error getting current case", ex); // NON-NLS
            return null;
        }
    }

    /**
     * Makes a correlation attribute instance of a given type from an OS
     * account. Checks address if it is null, or one of the ones always present
     * on a windows system and thus not unique.
     *
     * @param osAccoun   The OS account.
     * @param dataSource The data source content object.
     *
     * @return The correlation attribute instance or null, if an error occurred.
     */
    public static CorrelationAttributeInstance makeCorrAttr(OsAccount osAccount, Content dataSource) {

        Optional<String> accountAddr = osAccount.getAddr();
        // Check address if it is null or one of the ones below we want to ignore it since they will always be one a windows system
        // and they are not unique
        if (!accountAddr.isPresent() || accountAddr.get().equals("S-1-5-18") || accountAddr.get().equals("S-1-5-19") || accountAddr.get().equals("S-1-5-20")) {
            return null;
        }
        try {

            CorrelationCase correlationCase = CentralRepository.getInstance().getCase(Case.getCurrentCaseThrows());
            CorrelationAttributeInstance correlationAttributeInstance = new CorrelationAttributeInstance(
                    CentralRepository.getInstance().getCorrelationTypeById(CorrelationAttributeInstance.OSACCOUNT_TYPE_ID),
                    accountAddr.get(),
                    correlationCase,
                    CorrelationDataSource.fromTSKDataSource(correlationCase, dataSource),
                    "",
                    "",
                    TskData.FileKnown.KNOWN,
                    osAccount.getId());

            return correlationAttributeInstance;

        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, String.format("Cannot get central repository for OsAccount: %s.", accountAddr.get()), ex);  //NON-NLS
            return null;
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex);  //NON-NLS
            return null;
        } catch (CorrelationAttributeNormalizationException ex) {
            logger.log(Level.SEVERE, "Exception with Correlation Attribute Normalization.", ex);  //NON-NLS
            return null;
        }
    }

    /**
     * Gets the correlation attribute instance for a file.
     *
     * @param file The file.
     *
     * TODO (Jira-6088): The methods in this low-level, utility class should
     * throw exceptions instead of logging them. The reason for this is that the
     * clients of the utility class, not the utility class itself, should be in
     * charge of error handling policy, per the Autopsy Coding Standard. Note
     * that clients of several of these methods currently cannot determine
     * whether receiving a null return value is an error or not, plus null
     * checking is easy to forget, while catching exceptions is enforced.
     *
     * @return The correlation attribute instance or null, if no such
     *         correlation attribute instance was found or an error occurred.
     */
    public static CorrelationAttributeInstance getCorrAttrForFile(AbstractFile file) {

        if (!isSupportedAbstractFileType(file)) {
            return null;
        }

        CorrelationAttributeInstance.Type type;
        CorrelationCase correlationCase;
        CorrelationDataSource correlationDataSource;

        try {
            type = CentralRepository.getInstance().getCorrelationTypeById(CorrelationAttributeInstance.FILES_TYPE_ID);
            correlationCase = CentralRepository.getInstance().getCase(Case.getCurrentCaseThrows());
            if (null == correlationCase) {
                //if the correlationCase is not in the Central repo then attributes generated in relation to it will not be
                return null;
            }
            correlationDataSource = CorrelationDataSource.fromTSKDataSource(correlationCase, file.getDataSource());
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Error getting querying case database (%s)", file), ex); // NON-NLS
            return null;
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, String.format("Error querying central repository (%s)", file), ex); // NON-NLS
            return null;
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Error getting current case", ex); // NON-NLS
            return null;
        }

        CorrelationAttributeInstance correlationAttributeInstance;
        try {
            correlationAttributeInstance = CentralRepository.getInstance().getCorrelationAttributeInstance(type, correlationCase, correlationDataSource, file.getId());
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, String.format("Error querying central repository (%s)", file), ex); // NON-NLS
            return null;
        } catch (CorrelationAttributeNormalizationException ex) {
            logger.log(Level.WARNING, String.format("Error creating correlation attribute instance (%s)", file), ex); // NON-NLS
            return null;
        }

        /*
         * If no correlation attribute instance was found when querying by file
         * object ID, try searching by file path instead. This is necessary
         * because file object IDs were not stored in the central repository in
         * early versions of its schema.
         */
        if (correlationAttributeInstance == null && file.getMd5Hash() != null) {
            String filePath = (file.getParentPath() + file.getName()).toLowerCase();
            try {
                correlationAttributeInstance = CentralRepository.getInstance().getCorrelationAttributeInstance(type, correlationCase, correlationDataSource, file.getMd5Hash(), filePath);
            } catch (CentralRepoException ex) {
                logger.log(Level.SEVERE, String.format("Error querying central repository (%s)", file), ex); // NON-NLS
                return null;
            } catch (CorrelationAttributeNormalizationException ex) {
                logger.log(Level.WARNING, String.format("Error creating correlation attribute instance (%s)", file), ex); // NON-NLS
                return null;
            }
        }

        return correlationAttributeInstance;
    }

    /**
     * Makes a correlation attribute instance for a file.
     *
     * IMPORTANT: The correlation attribute instance is NOT added to the central
     * repository by this method.
     *
     * TODO (Jira-6088): The methods in this low-level, utility class should
     * throw exceptions instead of logging them. The reason for this is that the
     * clients of the utility class, not the utility class itself, should be in
     * charge of error handling policy, per the Autopsy Coding Standard. Note
     * that clients of several of these methods currently cannot determine
     * whether receiving a null return value is an error or not, plus null
     * checking is easy to forget, while catching exceptions is enforced.
     *
     * @param file The file.
     *
     * @return The correlation attribute instance or null, if an error occurred.
     */
    public static CorrelationAttributeInstance makeCorrAttrFromFile(AbstractFile file) {

        if (!isSupportedAbstractFileType(file)) {
            return null;
        }

        // We need a hash to make the correlation artifact instance.
        String md5 = file.getMd5Hash();
        if (md5 == null || md5.isEmpty() || HashUtility.isNoDataMd5(md5)) {
            return null;
        }

        try {
            CorrelationAttributeInstance.Type filesType = CentralRepository.getInstance().getCorrelationTypeById(CorrelationAttributeInstance.FILES_TYPE_ID);

            CorrelationCase correlationCase = CentralRepository.getInstance().getCase(Case.getCurrentCaseThrows());
            return new CorrelationAttributeInstance(
                    filesType,
                    file.getMd5Hash(),
                    correlationCase,
                    CorrelationDataSource.fromTSKDataSource(correlationCase, file.getDataSource()),
                    file.getParentPath() + file.getName(),
                    "",
                    TskData.FileKnown.UNKNOWN,
                    file.getId());

        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Error querying case database (%s)", file), ex); // NON-NLS
            return null;
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, String.format("Error querying central repository (%s)", file), ex); // NON-NLS
            return null;
        } catch (CorrelationAttributeNormalizationException ex) {
            logger.log(Level.WARNING, String.format("Error creating correlation attribute instance (%s)", file), ex); // NON-NLS
            return null;
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Error getting current case", ex); // NON-NLS
            return null;
        }
    }

    /**
     * Checks whether or not a file is of a type that can be added to the
     * central repository as a correlation attribute instance.
     *
     * @param file A file.
     *
     * @return True or false.
     */
    public static boolean isSupportedAbstractFileType(AbstractFile file) {
        if (file == null) {
            return false;
        }
        switch (file.getType()) {
            case UNALLOC_BLOCKS:
            case UNUSED_BLOCKS:
            case SLACK:
            case VIRTUAL_DIR:
            case LOCAL_DIR:
                return false;
            case CARVED:
            case DERIVED:
            case LOCAL:
            case LAYOUT_FILE:
                return true;
            case FS:
                return file.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.ALLOC);
            default:
                logger.log(Level.WARNING, "Unexpected file type {0}", file.getType().getName());
                return false;
        }
    }

    /**
     * Prevent instantiation of this utility class.
     */
    private CorrelationAttributeUtil() {
    }

}
