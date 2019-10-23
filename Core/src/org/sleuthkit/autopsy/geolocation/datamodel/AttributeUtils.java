/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * contact: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.geolocation.datamodel;

import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utilities for simplifying and reducing redundant when getting Artifact
 * attributes.
 */
final class AttributeUtils {
    
    /**
     * Private constructor for this Utility class.
     */
    private AttributeUtils() {
        
    }

    /**
     * Helper function for getting a String attribute from an artifact. This
     * will work for all attributes
     *
     * @param artifact      The BlackboardArtifact to get the attributeType
     * @param attributeType BlackboardAttribute attributeType
     *
     * @return String value for the given attribute or null if attribute was not
     *         set for the given artifact
     *
     * @throws TskCoreException
     */
    static String getString(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE attributeType) throws TskCoreException {
        if (artifact == null) {
            return null;
        }

        BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(attributeType));
        return (attribute != null ? attribute.getDisplayString() : null);
    }

    /**
     * Helper function for getting a Double attribute from an artifact.
     *
     * @param artifact      The BlackboardArtifact to get the attributeType
     * @param attributeType BlackboardAttribute attributeType
     *
     * @return Double value for the given attribute.
     *
     * @throws TskCoreException
     */
    static Double getDouble(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE attributeType) throws TskCoreException {
        if (artifact == null) {
            return null;
        }

        if (attributeType.getValueType() != BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DOUBLE) {
            return null;
        }

        BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(attributeType));
        return (attribute != null ? attribute.getValueDouble() : null);
    }

    /**
     * Helper function for getting a Long attribute from an artifact.
     *
     * @param artifact      The BlackboardArtifact to get the attributeType
     * @param attributeType BlackboardAttribute attributeType
     *
     * @return Long value for the given attribute.
     *
     * @throws TskCoreException
     */
    static Long getLong(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE attributeType) throws TskCoreException {
        if (artifact == null) {
            return null;
        }

        if (attributeType.getValueType() != BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.LONG
                || attributeType.getValueType() != BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME) {
            return null;
        }

        BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(attributeType));
        return (attribute != null ? attribute.getValueLong() : null);
    }

    /**
     * Helper function for getting a Integer attribute from an artifact.
     *
     * @param artifact      The BlackboardArtifact to get the attributeType
     * @param attributeType BlackboardAttribute attributeType
     *
     * @return Integer value for the given attribute.
     *
     * @throws TskCoreException
     */
    static Integer getInteger(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE attributeType) throws TskCoreException {
        if (artifact == null) {
            return null;
        }

        if (attributeType.getValueType() != BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.INTEGER) {
            return null;
        }

        BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(attributeType));
        return (attribute != null ? attribute.getValueInt() : null);
    }

}
