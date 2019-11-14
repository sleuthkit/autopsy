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
package org.sleuthkit.autopsy.communications.relationships;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME_PERSON;
import static org.sleuthkit.datamodel.BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME;
import org.sleuthkit.datamodel.TimeUtilities;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.communications.Utils;
import org.sleuthkit.autopsy.coreutils.PhoneNumUtil;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;

/**
 * Extends BlackboardArtifactNode to override createSheet to create a contact
 * artifact specific sheet.
 */
final class ContactNode extends BlackboardArtifactNode {

    private static final Logger logger = Logger.getLogger(ContactNode.class.getName());

    @Messages({
        "ContactNode_Name=Name",
        "ContactNode_Phone=Phone Number",
        "ContactNode_Email=Email Address",
        "ContactNode_Mobile_Number=Mobile Number",
        "ContactNode_Office_Number=Office Number",
        "ContactNode_URL=URL",
        "ContactNode_Home_Number=Home Number",})

    ContactNode(BlackboardArtifact artifact) {
        super(artifact);

        String name = getAttributeDisplayString(artifact, TSK_NAME);
        if (name == null || name.trim().isEmpty()) {
            // VCards use TSK_NAME_PERSON instead of TSK_NAME
            name = getAttributeDisplayString(artifact, TSK_NAME_PERSON);
        }
        setDisplayName(name);
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = new Sheet();

        final BlackboardArtifact artifact = getArtifact();
        BlackboardArtifact.ARTIFACT_TYPE fromID = BlackboardArtifact.ARTIFACT_TYPE.fromID(artifact.getArtifactTypeID());
        if (fromID != TSK_CONTACT) {
            return sheet;
        }

        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        // Sorting the attributes by type so that the duplicates can be removed
        // and they can be grouped by type for display.  The attribute prefixes
        // are used so that all attributed of that type are found, including 
        // ones that are not predefined as part of BlackboardAttributes
        try {
            List<BlackboardAttribute> phoneNumList = new ArrayList<>();
            List<BlackboardAttribute> emailList = new ArrayList<>();
            List<BlackboardAttribute> nameList = new ArrayList<>();
            List<BlackboardAttribute> otherList = new ArrayList<>();
            for (BlackboardAttribute bba : artifact.getAttributes()) {
                if (bba.getAttributeType().getTypeName().startsWith("TSK_PHONE")) {
                    phoneNumList.add(bba);
                } else if (bba.getAttributeType().getTypeName().startsWith("TSK_EMAIL")) {
                    emailList.add(bba);
                } else if (bba.getAttributeType().getTypeName().startsWith("TSK_NAME")) {
                    nameList.add(bba);
                } else {
                    otherList.add(bba);
                }
            }

            addPropertiesToSheet(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getLabel(),
                    sheetSet, nameList);
            addPropertiesToSheet(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER.getLabel(),
                    sheetSet, phoneNumList);
            addPropertiesToSheet(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL.getLabel(),
                    sheetSet, emailList);

            for (BlackboardAttribute bba : otherList) {
                sheetSet.put(new NodeProperty<>(bba.getAttributeType().getTypeName(), bba.getAttributeType().getDisplayName(), "", bba.getDisplayString()));
            }

            List<Content> children = artifact.getChildren();
            if (children != null) {
                int count = 0;
                String imageLabelPrefix = "Image";
                for (Content child : children) {
                    if (child instanceof AbstractFile) {
                        String imageLabel = imageLabelPrefix;
                        if (count > 0) {
                            imageLabel = imageLabelPrefix + "-" + count;
                        }
                        sheetSet.put(new NodeProperty<>(imageLabel, imageLabel, imageLabel, child.getName()));
                    }
                }
            }

        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error getting attribute values.", ex); //NON-NLS
        }

        return sheet;
    }

    private void addPropertiesToSheet(String propertyID, Sheet.Set sheetSet, List<BlackboardAttribute> attributeList) {
        int count = 0;
        for (BlackboardAttribute bba : attributeList) {
            if (count++ > 0) {
                if (bba.getAttributeType().getTypeName().startsWith("TSK_PHONE")) {
                    String phoneNumCountry = PhoneNumUtil.getCountryCode(bba.getValueString());
                    if (phoneNumCountry.equals("")) {
                        sheetSet.put(new NodeProperty<>(propertyID + "_" + count, bba.getAttributeType().getDisplayName(), "", bba.getDisplayString()));
                    } else {
                        sheetSet.put(new NodeProperty<>(propertyID + "_" + count, bba.getAttributeType().getDisplayName(), "", bba.getDisplayString() + "  [" + phoneNumCountry + "]"));
                    }
                } else {
                    sheetSet.put(new NodeProperty<>(propertyID + "_" + count, bba.getAttributeType().getDisplayName(), "", bba.getDisplayString()));
                }
            } else {
                if (bba.getAttributeType().getTypeName().startsWith("TSK_PHONE")) {
                    String phoneNumCountry = PhoneNumUtil.getCountryCode(bba.getValueString());
                    if (phoneNumCountry.equals("")) {
                        sheetSet.put(new NodeProperty<>(propertyID, bba.getAttributeType().getDisplayName(), "", bba.getDisplayString()));
                    } else {
                        sheetSet.put(new NodeProperty<>(propertyID, bba.getAttributeType().getDisplayName(), "", bba.getDisplayString() + "  [" + phoneNumCountry + "]"));
                    }
                } else {
                    sheetSet.put(new NodeProperty<>(propertyID, bba.getAttributeType().getDisplayName(), "", bba.getDisplayString()));
                }
            }
        }
    }

    private static String getAttributeDisplayString(final BlackboardArtifact artifact, final BlackboardAttribute.ATTRIBUTE_TYPE attributeType) {
        try {
            BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.fromID(attributeType.getTypeID())));
            if (attribute == null) {
                return "";
            } else if (attributeType.getValueType() == DATETIME) {
                return TimeUtilities.epochToTime(attribute.getValueLong(),
                        TimeZone.getTimeZone(Utils.getUserPreferredZoneId()));
            } else {
                return attribute.getDisplayString();
            }
        } catch (TskCoreException tskCoreException) {
            logger.log(Level.WARNING, "Error getting attribute value.", tskCoreException); //NON-NLS
            return "";
        }
    }

    /**
     * Circumvent DataResultFilterNode's slightly odd delegation to
     * BlackboardArtifactNode.getSourceName().
     *
     * @return the displayName of this Node, which is the type.
     */
    @Override
    public String getSourceName() {
        return getDisplayName();
    }

}
