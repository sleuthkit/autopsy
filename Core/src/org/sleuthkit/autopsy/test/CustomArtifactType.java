/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import javax.xml.bind.DatatypeConverter;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
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
    private static BlackboardArtifact.Type artifactType;
    private static BlackboardAttribute.Type intAttrType;
    private static BlackboardAttribute.Type doubleAttrType;
    private static BlackboardAttribute.Type longAttributeType;
    private static BlackboardAttribute.Type dateTimeAttrType;
    private static BlackboardAttribute.Type bytesAttrType;
    private static BlackboardAttribute.Type stringAttrType;

    /**
     * Adds the custom artifact type, with its associated custom attribute
     * types, to the case database of the current case.
     *
     * @throws BlackboardException If there is an error adding any of the types.
     */
    static void addToCaseDatabase() throws Blackboard.BlackboardException, NoCurrentCaseException {
        Blackboard blackboard = Case.getOpenCase().getServices().getBlackboard();
        artifactType = blackboard.getOrAddArtifactType(ARTIFACT_TYPE_NAME, ARTIFACT_DISPLAY_NAME);
        intAttrType = blackboard.getOrAddAttributeType(INT_ATTR_TYPE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.INTEGER, INT_ATTR_DISPLAY_NAME);
        doubleAttrType = blackboard.getOrAddAttributeType(DOUBLE_ATTR_TYPE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DOUBLE, DOUBLE_ATTR_DISPLAY_NAME);
        longAttributeType = blackboard.getOrAddAttributeType(LONG_ATTR_TYPE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.LONG, LONG_ATTR_DISPLAY_NAME);
        dateTimeAttrType = blackboard.getOrAddAttributeType(DATETIME_ATTR_TYPE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME, DATETIME_ATTR_DISPLAY_NAME);
        bytesAttrType = blackboard.getOrAddAttributeType(BYTES_ATTR_TYPE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.BYTE, BYTES_ATTR_DISPLAY_NAME);
        stringAttrType = blackboard.getOrAddAttributeType(STRING_ATTR_TYPE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING, STRING_ATTR_DISPLAY_NAME);
    }

    /**
     * Creates and instance of the custom artifact type.
     *
     * @param source The artifact source content.
     *
     * @return A BlackboardArtifact object.
     *
     * @throws TskCoreException If there is an error creating the artifact.
     */
    static BlackboardArtifact createInstance(Content source) throws TskCoreException {
        BlackboardArtifact artifact = source.newArtifact(artifactType.getTypeID());
        List<BlackboardAttribute> attributes = new ArrayList<>();
        attributes.add(new BlackboardAttribute(intAttrType, MODULE_NAME, 0));
        attributes.add(new BlackboardAttribute(doubleAttrType, MODULE_NAME, 0.0));
        attributes.add(new BlackboardAttribute(longAttributeType, MODULE_NAME, 0L));
        attributes.add(new BlackboardAttribute(dateTimeAttrType, MODULE_NAME, 60L));
        attributes.add(new BlackboardAttribute(bytesAttrType, MODULE_NAME, DatatypeConverter.parseHexBinary("ABCD")));
        attributes.add(new BlackboardAttribute(stringAttrType, MODULE_NAME, "Zero"));
        artifact.addAttributes(attributes);

        /*
         * Add a second source module to the attributes. Try to do it twice. The
         * second attempt should have no effect on the data.
         */
        for (BlackboardAttribute attr : attributes) {
            attr.addSource(ADDITIONAL_MODULE_NAME);
            attr.addSource(ADDITIONAL_MODULE_NAME);
        }

        return artifact;
    }

    /**
     * Prevents instantiation of this utility class.
     */
    private CustomArtifactType() {
    }

}
