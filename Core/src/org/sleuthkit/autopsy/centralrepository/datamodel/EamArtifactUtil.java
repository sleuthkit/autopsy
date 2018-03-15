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
    private static final Logger LOGGER = Logger.getLogger(EamArtifactUtil.class.getName());

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
                    CorrelationAttribute eamArtifact = EamArtifactUtil.getCorrelationAttributeFromBlackboardArtifact(aType, bbArtifact);
                    if (eamArtifact != null) {
                        eamArtifacts.add(eamArtifact);
                    }
                }
            }
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error getting defined correlation types.", ex); // NON-NLS
            return eamArtifacts;
        }

        // if they asked for it, add the instance details associated with this occurance.
        if (!eamArtifacts.isEmpty() && addInstanceDetails) {
            try {
                Case currentCase = Case.getOpenCase();
                AbstractFile bbSourceFile = currentCase.getSleuthkitCase().getAbstractFileById(bbArtifact.getObjectID());
                if (null == bbSourceFile) {
                    //@@@ Log this
                    return eamArtifacts;
                }

                // make an instance for the BB source file 
                CorrelationCase correlationCase = EamDb.getInstance().getCase(Case.getOpenCase());
                if (null == correlationCase) {
                    correlationCase = EamDb.getInstance().newCase(Case.getOpenCase());
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
                LOGGER.log(Level.SEVERE, "Error creating artifact instance.", ex); // NON-NLS
                return eamArtifacts;
            } catch (NoCurrentCaseException ex) {
                LOGGER.log(Level.SEVERE, "Case is closed.", ex); // NON-NLS
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
     * @return the new EamArtifact, or null if one was not created because
     *         bbArtifact did not contain the needed data
     */
    private static CorrelationAttribute getCorrelationAttributeFromBlackboardArtifact(CorrelationAttribute.Type correlationType, 
            BlackboardArtifact bbArtifact) throws EamDbException {
        String value = null;
        int artifactTypeID = bbArtifact.getArtifactTypeID();

        try {
            if (BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID() == artifactTypeID) {
                // Get the associated artifact
                BlackboardAttribute attribute = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT));
                if (attribute != null) {
                    BlackboardArtifact associatedArtifact = Case.getOpenCase().getSleuthkitCase().getBlackboardArtifact(attribute.getValueLong());
                    return EamArtifactUtil.getCorrelationAttributeFromBlackboardArtifact(correlationType, associatedArtifact);
                }

            } else if (correlationType.getId() == CorrelationAttribute.EMAIL_TYPE_ID
                    && BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID() == artifactTypeID) {

                BlackboardAttribute setNameAttr = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME));
                if (setNameAttr != null
                        && EamArtifactUtil.getEmailAddressAttrString().equals(setNameAttr.getValueString())) {
                    value = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD)).getValueString();
                }
            } else if (correlationType.getId() == CorrelationAttribute.DOMAIN_TYPE_ID
                    && (BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID() == artifactTypeID
                    || BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID() == artifactTypeID
                    || BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID() == artifactTypeID
                    || BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID() == artifactTypeID)) {

                // Lower-case this to normalize domains
                value = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN)).getValueString();
            } else if (correlationType.getId() == CorrelationAttribute.PHONE_TYPE_ID
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

            } else if (correlationType.getId() == CorrelationAttribute.USBID_TYPE_ID
                    && BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID() == artifactTypeID) {

                value = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_ID)).getValueString();
            }

        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Error getting attribute while getting type from BlackboardArtifact.", ex); // NON-NLS
            return null;
        }  catch (NoCurrentCaseException ex) {
            LOGGER.log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
            return null;
        }

        if (null != value) {
            return new CorrelationAttribute(correlationType, value);
        } else {
            return null;
        }
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
     * @param content     The content object
     * @param knownStatus Unknown, notable, or known
     * @param comment     The comment for the new artifact (generally used for a
     *                    tag comment)
     *
     * @return The new EamArtifact or null if creation failed
     */
    public static CorrelationAttribute getCorrelationAttributeFromContent(Content content, TskData.FileKnown knownStatus, String comment) {

        if (!(content instanceof AbstractFile)) {
            return null;
        }

        final AbstractFile af = (AbstractFile) content;

        if (!isValidCentralRepoFile(af)) {
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
            CorrelationCase correlationCase = EamDb.getInstance().getCase(Case.getOpenCase());
            if (null == correlationCase) {
                correlationCase = EamDb.getInstance().newCase(Case.getOpenCase());
            }
            CorrelationAttributeInstance cei = new CorrelationAttributeInstance(
                    correlationCase,
                    CorrelationDataSource.fromTSKDataSource(correlationCase, af.getDataSource()),
                    af.getParentPath() + af.getName(),
                    comment,
                    knownStatus
            );
            eamArtifact.addInstance(cei);
            return eamArtifact;
        } catch (TskCoreException | EamDbException | NoCurrentCaseException ex) {
            LOGGER.log(Level.SEVERE, "Error making correlation attribute.", ex);
            return null;
        }
    }

    /**
     * Check whether the given abstract file should be processed for the central
     * repository.
     *
     * @param af The file to test
     *
     * @return true if the file should be added to the central repo, false
     *         otherwise
     */
    public static boolean isValidCentralRepoFile(AbstractFile af) {
        if (af == null) {
            return false;
        }

        if (af.getKnown() == TskData.FileKnown.KNOWN) {
            return false;
        }

        switch (af.getType()) {
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
                return af.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.ALLOC);
            default:
                LOGGER.log(Level.WARNING, "Unexpected file type {0}", af.getType().getName());
                return false;
        }
    }
}
