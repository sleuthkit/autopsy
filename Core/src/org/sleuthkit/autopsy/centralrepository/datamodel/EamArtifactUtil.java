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

    private static final long serialVersionUID = 1L;
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
     * @param bbArtifact         BlackboardArtifact to examine
     * @param addInstanceDetails If true, add instance details from bbArtifact
     *                           into the returned structure
     * @param checkEnabled       If true, only create a CorrelationAttribute if
     *                           it is enabled
     *
     * @return List of EamArtifacts
     */
    public static List<CorrelationAttribute> getCorrelationAttributeFromBlackboardArtifact(BlackboardArtifact bbArtifact,
            boolean addInstanceDetails, boolean checkEnabled) {

        List<CorrelationAttribute> eamArtifacts = new ArrayList<>();

        try {
            // Cycle through the types and see if there is a correlation attribute that works
            // for the given blackboard artifact
            //
            // @@@ This seems ineffecient. Instead of cycling based on correlation type, we should just
            // have switch based on artifact type
            for (CorrelationAttribute.Type aType : EamDb.getInstance().getDefinedCorrelationTypes()) {
                if ((checkEnabled && aType.isEnabled()) || !checkEnabled) {
                    CorrelationAttribute correlationAttribute = EamArtifactUtil.getCorrelationAttributeFromBlackboardArtifact(aType, bbArtifact);
                    if (correlationAttribute != null) {
                        eamArtifacts.add(correlationAttribute);
                    }
                }
            }
        } catch (EamDbException | CorrelationAttributeNormalizationException ex) {
            logger.log(Level.SEVERE, "Error getting defined correlation types.", ex); // NON-NLS
            return eamArtifacts;
        }

        // if they asked for it, add the instance details associated with this occurance.
        if (!eamArtifacts.isEmpty() && addInstanceDetails) {
            try {
                Case currentCase = Case.getCurrentCaseThrows();
                AbstractFile bbSourceFile = currentCase.getSleuthkitCase().getAbstractFileById(bbArtifact.getObjectID());
                if (null == bbSourceFile) {
                    //@@@ Log this
                    return eamArtifacts;
                }

                // make an instance for the BB source file 
                CorrelationCase correlationCase = EamDb.getInstance().getCase(Case.getCurrentCaseThrows());
                if (null == correlationCase) {
                    correlationCase = EamDb.getInstance().newCase(Case.getCurrentCaseThrows());
                }
                CorrelationAttributeInstance eamInstance = new CorrelationAttributeInstance(
                        correlationCase,
                        CorrelationDataSource.fromTSKDataSource(correlationCase, bbSourceFile.getDataSource()),
                        bbSourceFile.getParentPath() + bbSourceFile.getName(),
                        "",
                        TskData.FileKnown.UNKNOWN
                );

                // add the instance details
                for (CorrelationAttribute eamArtifact : eamArtifacts) {
                    eamArtifact.addInstance(eamInstance);
                }
            } catch (TskCoreException | EamDbException ex) {
                logger.log(Level.SEVERE, "Error creating artifact instance.", ex); // NON-NLS
                return eamArtifacts;
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Case is closed.", ex); // NON-NLS
                return eamArtifacts;
            }
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
     * @return the new EamArtifact. Throws an exception if one was not created because
     *         bbArtifact did not contain the needed data
     */
    private static CorrelationAttribute getCorrelationAttributeFromBlackboardArtifact(CorrelationAttribute.Type correlationType,
            BlackboardArtifact bbArtifact) throws EamDbException, CorrelationAttributeNormalizationException {
        
        String value = null;
        
        int artifactTypeID = bbArtifact.getArtifactTypeID();

        try {
            final int correlationTypeId = correlationType.getId();
            
            if (BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID() == artifactTypeID) {
                // Get the associated artifact
                BlackboardAttribute attribute = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT));
                if (attribute != null) {
                    BlackboardArtifact associatedArtifact = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboardArtifact(attribute.getValueLong());
                    return EamArtifactUtil.getCorrelationAttributeFromBlackboardArtifact(correlationType, associatedArtifact);
                }

            } else if (correlationTypeId == CorrelationAttribute.EMAIL_TYPE_ID
                    && BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID() == artifactTypeID) {

                BlackboardAttribute setNameAttr = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME));
                if (setNameAttr != null
                        && EamArtifactUtil.getEmailAddressAttrString().equals(setNameAttr.getValueString())) {
                    value = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD)).getValueString();
                }
            } else if (correlationTypeId == CorrelationAttribute.DOMAIN_TYPE_ID
                    && (BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID() == artifactTypeID
                    || BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID() == artifactTypeID
                    || BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID() == artifactTypeID
                    || BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID() == artifactTypeID)) {

                // Lower-case this to validate domains
                value = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN)).getValueString();
            } else if (correlationTypeId == CorrelationAttribute.PHONE_TYPE_ID
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

            } else if (correlationTypeId == CorrelationAttribute.USBID_TYPE_ID
                    && BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID() == artifactTypeID) {

                value = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_ID)).getValueString();
            }

        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting attribute while getting type from BlackboardArtifact.", ex); // NON-NLS
            return null;
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
            return null;
        }

        return new CorrelationAttribute(correlationType, value);
    }

    /**
     * Retrieve CorrelationAttribute from the given Content.
     *
     * @param content The content object
     *
     * @return The new CorrelationAttribute, or null if retrieval failed.
     */
    public static CorrelationAttribute getCorrelationAttributeFromContent(Content content) throws EamDbException, CorrelationAttributeNormalizationException {

        if (!(content instanceof AbstractFile)) {
            throw new EamDbException("Content is not an AbstractFile.");
        }

        final AbstractFile file = (AbstractFile) content;

        if (!isSupportedAbstractFileType(file)) {
            throw new EamDbException("File type is not supported.");
        }

        CorrelationAttribute correlationAttribute;
        CorrelationAttribute.Type type;
        CorrelationCase correlationCase;
        CorrelationDataSource correlationDataSource;
        String value;
        String filePath;
        
        try {
            type = EamDb.getInstance().getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID);
            correlationCase = EamDb.getInstance().getCase(Case.getCurrentCaseThrows());
            if (null == correlationCase) {
                correlationCase = EamDb.getInstance().newCase(Case.getCurrentCaseThrows());
            }
            correlationDataSource = CorrelationDataSource.fromTSKDataSource(correlationCase, file.getDataSource());
            value = file.getMd5Hash();
            filePath = (file.getParentPath() + file.getName()).toLowerCase();
        } catch (TskCoreException ex) {
            throw new EamDbException("Error retrieving correlation attribute.", ex);
        } catch (NoCurrentCaseException ex) {
            throw new EamDbException("Case is closed.", ex);
        }
        
        try {
            correlationAttribute = EamDb.getInstance().getCorrelationAttribute(type, correlationCase, correlationDataSource, value, filePath);
        } catch (EamDbException ex) {
            logger.log(Level.WARNING, String.format(
                    "Correlation attribute could not be retrieved for '%s' (id=%d): %s",
                    content.getName(), content.getId(), ex.getMessage()));
            throw ex;
        }

        return correlationAttribute;
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
    public static CorrelationAttribute makeCorrelationAttributeFromContent(Content content) {

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

        CorrelationAttribute eamArtifact;
        try {
            CorrelationAttribute.Type filesType = EamDb.getInstance().getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID);
            eamArtifact = new CorrelationAttribute(filesType, af.getMd5Hash());
            CorrelationCase correlationCase = EamDb.getInstance().getCase(Case.getCurrentCaseThrows());
            if (null == correlationCase) {
                correlationCase = EamDb.getInstance().newCase(Case.getCurrentCaseThrows());
            }
            CorrelationAttributeInstance cei = new CorrelationAttributeInstance(
                    correlationCase,
                    CorrelationDataSource.fromTSKDataSource(correlationCase, af.getDataSource()),
                    af.getParentPath() + af.getName());
            eamArtifact.addInstance(cei);
            return eamArtifact;
        } catch (TskCoreException | EamDbException | NoCurrentCaseException | CorrelationAttributeNormalizationException ex) {
            logger.log(Level.SEVERE, "Error making correlation attribute.", ex);	//NON-NLS
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
