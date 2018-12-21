/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications;

import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.Sheet;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbUtil;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_SENT;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_FROM;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_TO;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SUBJECT;
import static org.sleuthkit.datamodel.BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TimeUtilities;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Node for a relationship, as represented by a BlackboardArtifact.
 */
final class RelationshipNode extends BlackboardArtifactNode {

    private static final Logger logger = Logger.getLogger(RelationshipNode.class.getName());

    RelationshipNode(BlackboardArtifact artifact) {
        super(artifact);
        final String stripEnd = StringUtils.stripEnd(artifact.getDisplayName(), "s");
        String removeEndIgnoreCase = StringUtils.removeEndIgnoreCase(stripEnd, "message");
        setDisplayName(removeEndIgnoreCase.isEmpty() ? stripEnd : removeEndIgnoreCase);
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = new Sheet();
        List<Tag> tags = getAllTagsFromDatabase();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        sheetSet.put(new NodeProperty<>("Type", "Type", "Type", getDisplayName()));
        
        addScoreProperty(sheetSet, tags);
        
        CorrelationAttributeInstance correlationAttribute = null;
        if (UserPreferences.hideCentralRepoCommentsAndOccurrences()== false) {
            correlationAttribute = getCorrelationAttributeInstance();
        }
        addCommentProperty(sheetSet, tags, correlationAttribute);
        
        if (UserPreferences.hideCentralRepoCommentsAndOccurrences()== false) {
            addCountProperty(sheetSet, correlationAttribute);
        }
        final BlackboardArtifact artifact = getArtifact();
        BlackboardArtifact.ARTIFACT_TYPE fromID = BlackboardArtifact.ARTIFACT_TYPE.fromID(getArtifact().getArtifactTypeID());
        if (null != fromID) {
            //Consider refactoring this to reduce boilerplate
            switch (fromID) {
                case TSK_EMAIL_MSG:
                    sheetSet.put(new NodeProperty<>("From", "From", "From",
                            StringUtils.strip(getAttributeDisplayString(artifact, TSK_EMAIL_FROM), " \t\n;")));
                    sheetSet.put(new NodeProperty<>("To", "To", "To",
                            StringUtils.strip(getAttributeDisplayString(artifact, TSK_EMAIL_TO), " \t\n;")));
                    sheetSet.put(new NodeProperty<>("Date", "Date", "Date",
                            getAttributeDisplayString(artifact, TSK_DATETIME_SENT)));
                    sheetSet.put(new NodeProperty<>("Subject", "Subject", "Subject",
                            getAttributeDisplayString(artifact, TSK_SUBJECT)));
                    try {
                        sheetSet.put(new NodeProperty<>("Attms", "Attms", "Attms", artifact.getChildrenCount()));
                    } catch (TskCoreException ex) {
                        logger.log(Level.WARNING, "Error loading attachment count for " + artifact, ex);
                    }

                    break;
                case TSK_MESSAGE:
                    sheetSet.put(new NodeProperty<>("From", "From", "From",
                            getAttributeDisplayString(artifact, TSK_PHONE_NUMBER_FROM)));
                    sheetSet.put(new NodeProperty<>("To", "To", "To",
                            getAttributeDisplayString(artifact, TSK_PHONE_NUMBER_TO)));
                    sheetSet.put(new NodeProperty<>("Date", "Date", "Date",
                            getAttributeDisplayString(artifact, TSK_DATETIME)));
                    sheetSet.put(new NodeProperty<>("Subject", "Subject", "Subject",
                            getAttributeDisplayString(artifact, TSK_SUBJECT)));
                    try {
                        sheetSet.put(new NodeProperty<>("Attms", "Attms", "Attms", artifact.getChildrenCount()));
                    } catch (TskCoreException ex) {
                        logger.log(Level.WARNING, "Error loading attachment count for " + artifact, ex);
                    }
                    break;
                case TSK_CALLLOG:
                    sheetSet.put(new NodeProperty<>("From", "From", "From",
                            getAttributeDisplayString(artifact, TSK_PHONE_NUMBER_FROM)));
                    sheetSet.put(new NodeProperty<>("To", "To", "To",
                            getAttributeDisplayString(artifact, TSK_PHONE_NUMBER_TO)));
                    sheetSet.put(new NodeProperty<>("Date", "Date", "Date",
                            getAttributeDisplayString(artifact, TSK_DATETIME_START)));
                    break;
                default:
                    break;
            }
        }

        return sheet;
    }

    /**
     *
     * Get the display string for the attribute of the given type from the given
     * artifact.
     *
     * @param artifact      the value of artifact
     * @param attributeType the value of TSK_SUBJECT1
     *
     * @return The display string, or an empty string if there is no such
     *         attribute or an an error.
     */
    private static String getAttributeDisplayString(final BlackboardArtifact artifact, final ATTRIBUTE_TYPE attributeType) {
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
            logger.log(Level.WARNING, "Error getting attribute value.", tskCoreException);
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
