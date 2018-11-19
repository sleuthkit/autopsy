/*
 * Central Repository
 *
 * Copyright 2015-2018 Basis Technology Corp.
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
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.HashUtility;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 *
 */
public class EamArtifactUtil {

    private static final Logger logger = Logger.getLogger(EamArtifactUtil.class.getName());

    public EamArtifactUtil() {
    }

    @Messages({"EamArtifactUtil.emailaddresses.text=Email Addresses"})
    public static String getEmailAddressAttrString() {
        return Bundle.EamArtifactUtil_emailaddresses_text();
    }

    /**
     * Static factory method to examine a BlackboardArtifact to determine if it
     * has contents that can be used for Correlation. If so, return a
     * EamArtifact with a single EamArtifactInstance within. If not, return
     * null.
     *
     * @param bbArtifact   BlackboardArtifact to examine
     * @param checkEnabled If true, only create a CorrelationAttribute if it is
     *                     enabled
     *
     * @return List of EamArtifacts
     */
    public static List<CorrelationAttributeInstance> makeInstancesFromBlackboardArtifact(BlackboardArtifact bbArtifact,
            boolean checkEnabled) {

        List<CorrelationAttributeInstance> eamArtifacts = new ArrayList<>();

        try {
            // Cycle through the types and see if there is a correlation attribute that works
            // for the given blackboard artifact
            //
            // @@@ This seems ineffecient. Instead of cycling based on correlation type, we should just
            // have switch based on artifact type
            for (CorrelationAttributeInstance.Type aType : EamDb.getInstance().getDefinedCorrelationTypes()) {
                if ((checkEnabled && aType.isEnabled()) || !checkEnabled) {
                    // Now always adds the instance details associated with this occurance.
                    CorrelationAttributeInstance correlationAttribute = EamArtifactUtil.makeInstanceFromBlackboardArtifact(aType, bbArtifact);
                    if (correlationAttribute != null) {
                        eamArtifacts.add(correlationAttribute);
                    }
                }
            }
        } catch (EamDbException ex) {
            logger.log(Level.SEVERE, "Error getting defined correlation types.", ex); // NON-NLS
            return eamArtifacts;
        }

        return eamArtifacts;
    }

    /**
     * Create an EamArtifact of type correlationType if one can be generated
     * based on the data in the blackboard artifact.
     *
     * @param correlationType The Central Repository artifact type to create
     * @param bbArtifact      The blackboard artifact to pull data from
     *
     * @return the new EamArtifact, or null if one was not created because
     *         bbArtifact did not contain the needed data
     */
    private static CorrelationAttributeInstance makeInstanceFromBlackboardArtifact(CorrelationAttributeInstance.Type correlationType,
            BlackboardArtifact bbArtifact) throws EamDbException {
        String value = null;
        int artifactTypeID = bbArtifact.getArtifactTypeID();

        try {
            if (BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID() == artifactTypeID) {
                // Get the associated artifact
                BlackboardAttribute attribute = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT));
                if (attribute != null) {
                    BlackboardArtifact associatedArtifact = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboardArtifact(attribute.getValueLong());
                    return EamArtifactUtil.makeInstanceFromBlackboardArtifact(correlationType, associatedArtifact);
                }

            } else if (correlationType.getId() == CorrelationAttributeInstance.EMAIL_TYPE_ID
                    && BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID() == artifactTypeID) {

                BlackboardAttribute setNameAttr = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME));
                if (setNameAttr != null
                        && EamArtifactUtil.getEmailAddressAttrString().equals(setNameAttr.getValueString())) {
                    value = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD)).getValueString();
                }
            } else if (correlationType.getId() == CorrelationAttributeInstance.DOMAIN_TYPE_ID
                    && (BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID() == artifactTypeID
                    || BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID() == artifactTypeID
                    || BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID() == artifactTypeID
                    || BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID() == artifactTypeID)) {

                // Lower-case this to normalize domains
                BlackboardAttribute attribute = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN));
                if (attribute != null) {
                    value = attribute.getValueString();
                }
            } else if (correlationType.getId() == CorrelationAttributeInstance.PHONE_TYPE_ID
                    && (BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT.getTypeID() == artifactTypeID
                    || BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG.getTypeID() == artifactTypeID
                    || BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE.getTypeID() == artifactTypeID)) {

                if (null != bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER))) {
                    value = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER)).getValueString();
                } else if (null != bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM))) {
                    value = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM)).getValueString();
                } else if (null != bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO))) {
                    value = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO)).getValueString();
                }

                // Remove all non-numeric symbols to semi-normalize phone numbers, preserving leading "+" character
                if (value != null) {
                    String newValue = value.replaceAll("\\D", "");
                    if (value.startsWith("+")) {
                        newValue = "+" + newValue;
                    }

                    value = newValue;

                    // If the resulting phone number is too small to be of use, return null
                    // (these 3-5 digit numbers can be valid, but are not useful for correlation)
                    if (value.length() <= 5) {
                        return null;
                    }
                }
            } else if (correlationType.getId() == CorrelationAttributeInstance.USBID_TYPE_ID
                    && BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID() == artifactTypeID) {

                value = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_ID)).getValueString();
            } else if (correlationType.getId() == CorrelationAttributeInstance.SSID_TYPE_ID
                    && BlackboardArtifact.ARTIFACT_TYPE.TSK_WIFI_NETWORK.getTypeID() == artifactTypeID) {
                value = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SSID)).getValueString();
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting attribute while getting type from BlackboardArtifact.", ex); // NON-NLS
            return null;
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
            return null;
        }

        if ((null != value) && (value.isEmpty() == false)) {
            return makeCorrelationAttributeInstanceUsingTypeValue(bbArtifact, correlationType, value);
        } else {
            return null;
        }
    }

    /**
     * Uses the determined type and vallue, then looks up instance details to
     * create proper CorrelationAttributeInstance.
     *
     * @param bbArtifact      the blackboard artifact
     * @param correlationType the given type
     * @param value           the artifact value
     *
     * @return CorrelationAttributeInstance from details
     */
    private static CorrelationAttributeInstance makeCorrelationAttributeInstanceUsingTypeValue(BlackboardArtifact bbArtifact, CorrelationAttributeInstance.Type correlationType, String value) {
        try {
            Case currentCase = Case.getCurrentCaseThrows();
            AbstractFile bbSourceFile = currentCase.getSleuthkitCase().getAbstractFileById(bbArtifact.getObjectID());
            if (null == bbSourceFile) {
                logger.log(Level.SEVERE, "Error creating artifact instance. Abstract File was null."); // NON-NLS
                return null;
            }

            // make an instance for the BB source file
            CorrelationCase correlationCase = EamDb.getInstance().getCase(Case.getCurrentCaseThrows());
            if (null == correlationCase) {
                correlationCase = EamDb.getInstance().newCase(Case.getCurrentCaseThrows());
            }
            return new CorrelationAttributeInstance(
                    correlationType,
                    value,
                    correlationCase,
                    CorrelationDataSource.fromTSKDataSource(correlationCase, bbSourceFile.getDataSource()),
                    bbSourceFile.getParentPath() + bbSourceFile.getName(),
                    "",
                    TskData.FileKnown.UNKNOWN,
                    bbSourceFile.getId());

        } catch (TskCoreException | EamDbException | CorrelationAttributeNormalizationException ex) {
            logger.log(Level.SEVERE, "Error creating artifact instance.", ex); // NON-NLS
            return null;
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Case is closed.", ex); // NON-NLS
            return null;
        }
    }

    /**
     * Retrieve CorrelationAttribute from the given Content.
     *
     * @param content The content object
     *
     * @return The new CorrelationAttribute, or null if retrieval failed.
     */
    public static CorrelationAttributeInstance getInstanceFromContent(Content content) {

        if (!(content instanceof AbstractFile)) {
            return null;
        }

        final AbstractFile file = (AbstractFile) content;

        if (!isSupportedAbstractFileType(file)) {
            return null;
        }

        CorrelationAttributeInstance.Type type;
        CorrelationCase correlationCase;
        CorrelationDataSource correlationDataSource;

        try {
            type = EamDb.getInstance().getCorrelationTypeById(CorrelationAttributeInstance.FILES_TYPE_ID);
            correlationCase = EamDb.getInstance().getCase(Case.getCurrentCaseThrows());
            if (null == correlationCase) {
                //if the correlationCase is not in the Central repo then attributes generated in relation to it will not be
                return null;
            }
            correlationDataSource = CorrelationDataSource.fromTSKDataSource(correlationCase, file.getDataSource());
        } catch (TskCoreException | EamDbException ex) {
            logger.log(Level.SEVERE, "Error retrieving correlation attribute.", ex);
            return null;
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Case is closed.", ex);
            return null;
        }

        CorrelationAttributeInstance correlationAttributeInstance;
        try {
            correlationAttributeInstance = EamDb.getInstance().getCorrelationAttributeInstance(type, correlationCase, correlationDataSource, file.getId());
        } catch (EamDbException | CorrelationAttributeNormalizationException ex) {
            logger.log(Level.WARNING, String.format(
                    "Correlation attribute could not be retrieved for '%s' (id=%d): %s",
                    content.getName(), content.getId(), ex.getMessage()));
            return null;
        }
        //if there was no correlation attribute found for the item using object_id then check for attributes added with schema 1,1 which lack object_id  
        if (correlationAttributeInstance == null) {
            String value = file.getMd5Hash();
            String filePath = (file.getParentPath() + file.getName()).toLowerCase();
            try {
                correlationAttributeInstance = EamDb.getInstance().getCorrelationAttributeInstance(type, correlationCase, correlationDataSource, value, filePath);
            } catch (EamDbException | CorrelationAttributeNormalizationException ex) {
                logger.log(Level.WARNING, String.format(
                        "Correlation attribute could not be retrieved for '%s' (id=%d): %s",
                        content.getName(), content.getId(), ex.getMessage()));
                return null;
            }
        }

        return correlationAttributeInstance;
    }

    /**
     * Create an EamArtifact from the given Content. Will return null if an
     * artifact can not be created - this is not necessarily an error case, it
     * just means an artifact can't be made. If creation fails due to an error
     * (and not that the file is the wrong type or it has no hash), the error
     * will be logged before returning.
     *
     * Does not add the artifact to the database.
     *
     * @param content The content object
     *
     * @return The new EamArtifact or null if creation failed
     */
    public static CorrelationAttributeInstance makeInstanceFromContent(Content content) {

        if (!(content instanceof AbstractFile)) {
            return null;
        }

        final AbstractFile af = (AbstractFile) content;

        if (!isSupportedAbstractFileType(af)) {
            return null;
        }

        // We need a hash to make the artifact
        String md5 = af.getMd5Hash();
        if (md5 == null || md5.isEmpty() || HashUtility.isNoDataMd5(md5)) {
            return null;
        }

        try {
            CorrelationAttributeInstance.Type filesType = EamDb.getInstance().getCorrelationTypeById(CorrelationAttributeInstance.FILES_TYPE_ID);

            CorrelationCase correlationCase = EamDb.getInstance().getCase(Case.getCurrentCaseThrows());
            if (null == correlationCase) {
                correlationCase = EamDb.getInstance().newCase(Case.getCurrentCaseThrows());
            }

            return new CorrelationAttributeInstance(
                    filesType,
                    af.getMd5Hash(),
                    correlationCase,
                    CorrelationDataSource.fromTSKDataSource(correlationCase, af.getDataSource()),
                    af.getParentPath() + af.getName(),
                    "",
                    TskData.FileKnown.UNKNOWN,
                    af.getId());

        } catch (TskCoreException | EamDbException | CorrelationAttributeNormalizationException ex) {
            logger.log(Level.SEVERE, "Error making correlation attribute.", ex);
            return null;
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Case is closed.", ex);
            return null;
        }
    }

    /**
     * Check whether the given abstract file should be processed for the central
     * repository.
     *
     * @param file The file to test
     *
     * @return true if the file should be added to the central repo, false
     *         otherwise
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
                return true;
            case FS:
                return file.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.ALLOC);
            default:
                logger.log(Level.WARNING, "Unexpected file type {0}", file.getType().getName());
                return false;
        }
    }
}
