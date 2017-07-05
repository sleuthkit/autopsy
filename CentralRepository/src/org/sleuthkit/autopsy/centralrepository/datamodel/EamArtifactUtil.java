/*
 * Central Repository
 *
 * Copyright 2015-2017 Basis Technology Corp.
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

import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskDataException;

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

    /*
     * Static factory method to examine a BlackboardArtifact to determine if it
     * has contents that can be used for Correlation. If so, return a
     * EamArtifact with a single EamArtifactInstance within. If not, return
     * null.
     *
     * @param bbArtifact BlackboardArtifact to examine @return EamArtifact or
     * null
     */
    public static EamArtifact fromBlackboardArtifact(BlackboardArtifact bbArtifact,
            boolean includeInstances,
            List<EamArtifact.Type> artifactTypes,
            boolean checkEnabled) {

        EamArtifact eamArtifact = null;
        for (EamArtifact.Type aType : artifactTypes) {
            if ((checkEnabled && aType.isEnabled()) || !checkEnabled) {
                eamArtifact = getTypeFromBlackboardArtifact(aType, bbArtifact);
            }
            if (null != eamArtifact) {
                break;
            }
        }

        if (null != eamArtifact && includeInstances) {
            try {
                AbstractFile af = Case.getCurrentCase().getSleuthkitCase().getAbstractFileById(bbArtifact.getObjectID());
                if (null == af) {
                    return null;
                }

                String deviceId = "";
                try {
                    deviceId = Case.getCurrentCase().getSleuthkitCase().getDataSource(af.getDataSource().getId()).getDeviceId();
                } catch (TskCoreException | TskDataException ex) {
                    LOGGER.log(Level.SEVERE, "Error, failed to get deviceID or data source from current case.", ex);
                }

                EamArtifactInstance eamInstance = new EamArtifactInstance(
                        new EamCase(Case.getCurrentCase().getName(), Case.getCurrentCase().getDisplayName()),
                        new EamDataSource(deviceId, af.getDataSource().getName()),
                        af.getParentPath() + af.getName(),
                        "",
                        TskData.FileKnown.UNKNOWN,
                        EamArtifactInstance.GlobalStatus.LOCAL
                );
                eamArtifact.addInstance(eamInstance);
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Error creating artifact instance.", ex); // NON-NLS
                return null;
            }
        }

        return eamArtifact;
    }

    /**
     * Convert a blackboard artifact to an EamArtifact
     *
     * @param aType      The Central Repository artifact type to create
     * @param bbArtifact The blackboard artifact to convert
     *
     * @return
     */
    public static EamArtifact getTypeFromBlackboardArtifact(EamArtifact.Type aType, BlackboardArtifact bbArtifact) {
        String value = null;
        int artifactTypeID = bbArtifact.getArtifactTypeID();

        try {
            if (aType.getId() == EamArtifact.EMAIL_TYPE_ID
                    && BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID() == artifactTypeID) {

                BlackboardAttribute setNameAttr = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME));
                if (setNameAttr != null
                        && EamArtifactUtil.getEmailAddressAttrString().equals(setNameAttr.getValueString())) {
                    value = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD)).getValueString();
                }
            } else if (aType.getId() == EamArtifact.DOMAIN_TYPE_ID
                    && (BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID() == artifactTypeID
                    || BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID() == artifactTypeID
                    || BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID() == artifactTypeID
                    || BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID() == artifactTypeID)) {

                // Lower-case this to normalize domains
                value = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN)).getValueString().toLowerCase();

            } else if (aType.getId() == EamArtifact.PHONE_TYPE_ID
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

                if (value != null) {
                    // Remove all non-numeric symbols to semi-normalize phone numbers
                    value = value.replaceAll("\\D", "");
                }
            } else if (aType.getId() == EamArtifact.USBID_TYPE_ID
                    && BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID() == artifactTypeID) {

                value = bbArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_ID)).getValueString();
            }

        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Error getting attribute while getting type from BlackboardArtifact.", ex); // NON-NLS
            return null;
        }

        if (null != value) {
            return new EamArtifact(aType, value);
        } else {
            return null;
        }
    }
}
