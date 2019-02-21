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

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.HashUtility;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 *
 */
public class EamArtifactUtil {

    private static final Logger logger = Logger.getLogger(EamArtifactUtil.class.getName());
    private static final ImmutableMap<Integer, Integer> CORRELATABLE_ATTRIBUTES;

    public EamArtifactUtil() {
    }

    @Messages({"EamArtifactUtil.emailaddresses.text=Email Addresses"})
    public static String getEmailAddressAttrString() {
        return Bundle.EamArtifactUtil_emailaddresses_text();
    }

    static {
        CORRELATABLE_ATTRIBUTES = ImmutableMap.<Integer, Integer>builder()
                .put(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), CorrelationAttributeInstance.DOMAIN_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_DEVICE_ID.getTypeID(), CorrelationAttributeInstance.USBID_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_MAC_ADDRESS.getTypeID(), CorrelationAttributeInstance.MAC_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_IMEI.getTypeID(), CorrelationAttributeInstance.IMEI_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_IMSI.getTypeID(), CorrelationAttributeInstance.IMSI_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_ICCID.getTypeID(), CorrelationAttributeInstance.ICCID_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_SSID.getTypeID(), CorrelationAttributeInstance.SSID_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER.getTypeID(), CorrelationAttributeInstance.PHONE_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_HOME.getTypeID(), CorrelationAttributeInstance.PHONE_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_OFFICE.getTypeID(), CorrelationAttributeInstance.PHONE_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_MOBILE.getTypeID(), CorrelationAttributeInstance.PHONE_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO.getTypeID(), CorrelationAttributeInstance.PHONE_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM.getTypeID(), CorrelationAttributeInstance.PHONE_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_EMAIL.getTypeID(), CorrelationAttributeInstance.EMAIL_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_EMAIL_HOME.getTypeID(), CorrelationAttributeInstance.EMAIL_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_EMAIL_OFFICE.getTypeID(), CorrelationAttributeInstance.EMAIL_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_EMAIL_BCC.getTypeID(), CorrelationAttributeInstance.EMAIL_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_EMAIL_CC.getTypeID(), CorrelationAttributeInstance.EMAIL_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_EMAIL_FROM.getTypeID(), CorrelationAttributeInstance.EMAIL_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_EMAIL_TO.getTypeID(), CorrelationAttributeInstance.EMAIL_TYPE_ID)
                .put(ATTRIBUTE_TYPE.TSK_EMAIL_REPLYTO.getTypeID(), CorrelationAttributeInstance.EMAIL_TYPE_ID)
                .build();
    }

    /**
     * Static factory method to examine a BlackboardArtifact to determine if it
     * has contents that can be used for Correlation. If so, return a
     * EamArtifact with a single EamArtifactInstance within. If not, return
     * null.
     *
     * @param artifact     BlackboardArtifact to examine
     * @param checkEnabled If true, only create a CorrelationAttribute if it is
     *                     enabled
     *
     * @return List of EamArtifacts
     */
    public static List<CorrelationAttributeInstance> makeInstancesFromBlackboardArtifact(BlackboardArtifact artifact,
            boolean checkEnabled) {
        List<CorrelationAttributeInstance> eamArtifacts = new ArrayList<>();
        if(artifact == null) {
            return eamArtifacts;
        }
        
        try {
            int artifactTypeID = artifact.getArtifactTypeID();
            if (artifactTypeID == ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
                BlackboardAttribute setNameAttr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME));
                if (setNameAttr != null
                        && EamArtifactUtil.getEmailAddressAttrString().equals(setNameAttr.getValueString())) {
                    BlackboardAttribute emailKeywordHit = artifact.getAttribute(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_KEYWORD));
                    addCorrelationAttributeToList(eamArtifacts, artifact, emailKeywordHit, CorrelationAttributeInstance.EMAIL_TYPE_ID);
                }
            }
            for (BlackboardAttribute attribute : artifact.getAttributes()) {
                Integer customAttributeTypeId = attribute.getAttributeType().getTypeID();
                if (CORRELATABLE_ATTRIBUTES.containsKey(customAttributeTypeId)) {
                    addCorrelationAttributeToList(eamArtifacts, artifact,
                            attribute,
                            CORRELATABLE_ATTRIBUTES.get(customAttributeTypeId)
                    );
                }
            }
        } catch (EamDbException ex) {
            logger.log(Level.SEVERE, "Error getting defined correlation types.", ex); // NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting attribute while getting type from BlackboardArtifact.", ex); // NON-NLS
            return null;
        }
        
        return eamArtifacts;
    }

    /**
     * Add a CorrelationAttributeInstance of the specified type to the provided
     * list if the artifact has an Attribute of the given type with a
     * non empty value.
     *
     * @param eamArtifacts    the list of CorrelationAttributeInstance objects
     *                        which should be added to
     * @param artifact        the blackboard artifact which we are
     *                        creating a CorrelationAttributeInstance for
     * @param bbAttributeType the type of BlackboardAttribute we expect to exist
     *                        for a CorrelationAttributeInstance of this type
     *                        generated from this Blackboard Artifact
     * @param typeId          the integer type id of the
     *                        CorrelationAttributeInstance type
     *
     * @throws EamDbException
     * @throws TskCoreException
     */
    private static void addCorrelationAttributeToList(List<CorrelationAttributeInstance> eamArtifacts, BlackboardArtifact artifact, BlackboardAttribute bbAttribute, int typeId) throws EamDbException, TskCoreException {
        if(bbAttribute == null) {
            return;
        }
        
        String value = bbAttribute.getValueString();
        if ((null != value) && (value.isEmpty() == false)) {
            CorrelationAttributeInstance inst = makeCorrelationAttributeInstanceUsingTypeValue(artifact, EamDb.getInstance().getCorrelationTypeById(typeId), value);
            if (inst != null) {
                eamArtifacts.add(inst);
            }
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
     * @return CorrelationAttributeInstance from details, or null if validation
     *         failed or another error occurred
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
     * artifact can not be created - this is not necessarily an error
     * case, it just means an artifact can't be made. If creation
     * fails due to an error (and not that the file is the wrong type or it has
     * no hash), the error will be logged before returning.
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