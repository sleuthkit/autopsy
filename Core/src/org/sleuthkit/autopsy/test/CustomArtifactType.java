/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.test;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.joda.time.DateTime;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.Blackboard.BlackboardException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A utility for creating instances of a custom artifact type.
 */
final class CustomArtifactType {

    private static final String MODULE_NAME = CustomArtifactsCreatorIngestModuleFactory.getModuleName();
    private static final String ADDITIONAL_MODULE_NAME = "Another Module";
    private static final String ARTIFACT_TYPE_NAME = "CUSTOM_ARTIFACT";
    private static final String ARTIFACT_DISPLAY_NAME = "Custom Artifact";
    private static final String INT_ATTR_TYPE_NAME = "CUSTOM_INT_ATTRIBUTE";
    private static final String INT_ATTR_DISPLAY_NAME = "Custom Integer";
    private static final String DOUBLE_ATTR_TYPE_NAME = "CUSTOM_DOUBLE_ATTRIBUTE";
    private static final String DOUBLE_ATTR_DISPLAY_NAME = "Custom Double";
    private static final String LONG_ATTR_TYPE_NAME = "CUSTOM_LONG_ATTRIBUTE";
    private static final String LONG_ATTR_DISPLAY_NAME = "Custom Long";
    private static final String DATETIME_ATTR_TYPE_NAME = "CUSTOM_DATETIME_ATTRIBUTE";
    private static final String DATETIME_ATTR_DISPLAY_NAME = "Custom Datetime";
    private static final String BYTES_ATTR_TYPE_NAME = "CUSTOM_BYTES_ATTRIBUTE";
    private static final String BYTES_ATTR_DISPLAY_NAME = "Custom Bytes";
    private static final String STRING_ATTR_TYPE_NAME = "CUSTOM_STRING_ATTRIBUTE";
    private static final String STRING_ATTR_DISPLAY_NAME = "Custom String";
    private static final String JSON_ATTR_TYPE_NAME = "CUSTOM_JSON_ATTRIBUTE";
    private static final String JSON_ATTR_DISPLAY_NAME = "Custom Json";
    private static BlackboardArtifact.Type artifactType;
    private static BlackboardAttribute.Type intAttrType;
    private static BlackboardAttribute.Type doubleAttrType;
    private static BlackboardAttribute.Type longAttributeType;
    private static BlackboardAttribute.Type dateTimeAttrType;
    private static BlackboardAttribute.Type bytesAttrType;
    private static BlackboardAttribute.Type stringAttrType;
    private static BlackboardAttribute.Type jsonAttrType;

    /**
     * Adds the custom artifact type, with its associated custom attribute
     * types, to the case database of the current case.
     *
     * @throws BlackboardException If there is an error adding any of the types.
     */
    static void addToCaseDatabase() throws Blackboard.BlackboardException {
        Blackboard blackboard = Case.getCurrentCase().getServices().getArtifactsBlackboard();
        artifactType = blackboard.getOrAddArtifactType(ARTIFACT_TYPE_NAME, ARTIFACT_DISPLAY_NAME);
        intAttrType = blackboard.getOrAddAttributeType(INT_ATTR_TYPE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.INTEGER, INT_ATTR_DISPLAY_NAME);
        doubleAttrType = blackboard.getOrAddAttributeType(DOUBLE_ATTR_TYPE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DOUBLE, DOUBLE_ATTR_DISPLAY_NAME);
        longAttributeType = blackboard.getOrAddAttributeType(LONG_ATTR_TYPE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.LONG, LONG_ATTR_DISPLAY_NAME);
        dateTimeAttrType = blackboard.getOrAddAttributeType(DATETIME_ATTR_TYPE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME, DATETIME_ATTR_DISPLAY_NAME);
        bytesAttrType = blackboard.getOrAddAttributeType(BYTES_ATTR_TYPE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.BYTE, BYTES_ATTR_DISPLAY_NAME);
        stringAttrType = blackboard.getOrAddAttributeType(STRING_ATTR_TYPE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING, STRING_ATTR_DISPLAY_NAME);
        jsonAttrType = blackboard.getOrAddAttributeType(JSON_ATTR_TYPE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.JSON, JSON_ATTR_DISPLAY_NAME);
    }

    /**
     * Creates an instance of the custom artifact type and posts it to the
     * blackboard.
     *
     * @param source The artifact source content.
     * @param ingestJobId The ingest job ID.
     *
     * @return A BlackboardArtifact object.
     *
     * @throws TskCoreException               If there is an error creating the
     *                                        artifact.
     * @throws Blackboard.BlackboardException If there is an error posting the
     *                                        artifact to the blackboard.
     */
    static BlackboardArtifact createAndPostInstance(Content source, long ingestJobId) throws TskCoreException, Blackboard.BlackboardException, DecoderException {
        List<BlackboardAttribute> attributes = new ArrayList<>();
        attributes.add(new BlackboardAttribute(intAttrType, MODULE_NAME, 0));
        attributes.add(new BlackboardAttribute(doubleAttrType, MODULE_NAME, 0.0));
        attributes.add(new BlackboardAttribute(longAttributeType, MODULE_NAME, 0L));
        attributes.add(new BlackboardAttribute(dateTimeAttrType, MODULE_NAME, 60L));
        attributes.add(new BlackboardAttribute(bytesAttrType, MODULE_NAME, Hex.decodeHex("ABCD")));
        attributes.add(new BlackboardAttribute(stringAttrType, MODULE_NAME, "Zero"));
        attributes.add(new BlackboardAttribute(jsonAttrType, MODULE_NAME, "{\"fruit\": \"Apple\",\"size\": \"Large\",\"color\": \"Red\"}"));

        /*
         * Add a second source module to the attributes. Try to do it twice. The
         * second attempt should have no effect on the data.
         */
        for (BlackboardAttribute attr : attributes) {
            attr.addSource(ADDITIONAL_MODULE_NAME);
            attr.addSource(ADDITIONAL_MODULE_NAME);
        }

        BlackboardArtifact artifact;        
        switch (artifactType.getCategory()) {
            case DATA_ARTIFACT:
                artifact = source.newDataArtifact(artifactType, attributes);
                break;
                
            case ANALYSIS_RESULT:
                artifact = source.newAnalysisResult(artifactType, Score.SCORE_UNKNOWN, null, null, null, attributes)
                        .getAnalysisResult();
                break;
                
            default:
                throw new TskCoreException(String.format("Artifact type: %s has no known category: %s", 
                        artifactType.getDisplayName(), artifactType.getCategory().getDisplayName()));
        }
                
        Blackboard blackboard = Case.getCurrentCase().getServices().getArtifactsBlackboard();
        blackboard.postArtifact(artifact, MODULE_NAME, ingestJobId);

        return artifact;
    }

    /**
     * Prevents instantiation of this utility class.
     */
    private CustomArtifactType() {
    }

}
