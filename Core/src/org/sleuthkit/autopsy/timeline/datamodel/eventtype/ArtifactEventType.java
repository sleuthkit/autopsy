/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.datamodel.eventtype;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public interface ArtifactEventType extends EventType {

    /**
     * @return the Artifact type this event type is derived form, or null if
     *         there is no artifact type (eg file system events)
     */
    public BlackboardArtifact.ARTIFACT_TYPE getArtifactType();

    public BlackboardAttribute.ATTRIBUTE_TYPE getDateTimeAttrubuteType();

    /**
     * given an artifact, and a map from attribute types to attributes, pull out
     * the time stamp, and compose the descriptions. Each implementation of
     * {@link ArtifactEventType} needs to implement parseAttributesHelper() as
     * hook for {@link buildEventDescription(org.sleuthkit.datamodel.BlackboardArtifact)
     * to invoke. Most subtypes can use this default implementation.
     *
     * @param artf
     * @param attrMap
     *
     * @return an {@link AttributeEventDescription} containing the timestamp
     *         and description information
     *
     * @throws TskCoreException
     */
    default AttributeEventDescription parseAttributesHelper(BlackboardArtifact artf, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attrMap) throws TskCoreException {
        final BlackboardAttribute dateTimeAttr = attrMap.get(getDateTimeAttrubuteType());

        long time = dateTimeAttr.getValueLong();
        String shortDescription = getShortExtractor().apply(artf, attrMap);
        String medDescription = shortDescription + " : " + getMedExtractor().apply(artf, attrMap);
        String fullDescription = medDescription + " : " + getFullExtractor().apply(artf, attrMap);
        return new AttributeEventDescription(time, shortDescription, medDescription, fullDescription);
    }

    /**
     * @return a function from an artifact and a map of its attributes, to a
     *         String to use as part of the full event description
     */
    BiFunction<BlackboardArtifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute>, String> getFullExtractor();

    /**
     * @return a function from an artifact and a map of its attributes, to a
     *         String to use as part of the medium event description
     */
    BiFunction<BlackboardArtifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute>, String> getMedExtractor();

    /**
     * @return a function from an artifact and a map of its attributes, to a
     *         String to use as part of the short event description
     */
    BiFunction<BlackboardArtifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute>, String> getShortExtractor();

    /**
     * bundles the per event information derived from a BlackBoard Artifact into
     * one object. Primarily used to have a single return value for
     * {@link ArtifactEventType#buildEventDescription(ArtifactEventType, BlackboardArtifact)}.
     */
    static class AttributeEventDescription {

        final private long time;

        public long getTime() {
            return time;
        }

        public String getShortDescription() {
            return shortDescription;
        }

        public String getMedDescription() {
            return medDescription;
        }

        public String getFullDescription() {
            return fullDescription;
        }

        final private String shortDescription;

        final private String medDescription;

        final private String fullDescription;

        public AttributeEventDescription(long time, String shortDescription,
                String medDescription,
                String fullDescription) {
            this.time = time;
            this.shortDescription = shortDescription;
            this.medDescription = medDescription;
            this.fullDescription = fullDescription;
        }

    }

    /**
     * Build a {@link AttributeEventDescription} derived from a
     * {@link BlackboardArtifact}. This is a template method that relies on each
     * {@link SubType}'s implementation of
     * {@link SubType#parseAttributesHelper()} to know how to go from
     * {@link BlackboardAttribute}s to the event description.
     *
     * @param artf the {@link BlackboardArtifact} to derive the event
     *             description from
     *
     * @return an {@link AttributeEventDescription} derived from the given
     *         artifact, if the given artifact has no timestamp
     *
     * @throws TskCoreException is there is a problem accessing the blackboard
     *                          data
     */
    static public AttributeEventDescription buildEventDescription(ArtifactEventType type, BlackboardArtifact artf) throws TskCoreException {
        //if we got passed an artifact that doesn't correspond to the type of the event, 
        //something went very wrong. throw an exception.
        if (type.getArtifactType().getTypeID() != artf.getArtifactTypeID()) {
            throw new IllegalArgumentException();
        }

        /*
         * build a map from attribute type to attribute, this makes implementing
         * the parseAttributeHelper easier but could be ineffecient if we don't
         * need most of the attributes. This would be unnessecary if there was
         * an api on Blackboard artifacts to get specific attributes by type
         */
        List<BlackboardAttribute> attributes = artf.getAttributes();
        Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attrMap = new HashMap<>();
        for (BlackboardAttribute attr : attributes) {
            attrMap.put(BlackboardAttribute.ATTRIBUTE_TYPE.fromLabel(attr.
                    getAttributeTypeName()), attr);
        }

        if (attrMap.get(type.getDateTimeAttrubuteType()) == null) {
            Logger.getLogger(AttributeEventDescription.class.getName()).log(Level.WARNING, "Artifact {0} has no date/time attribute, skipping it.", artf.getArtifactID()); // NON-NLS
            return null;
        }
        //use the hook provided by this subtype implementation
        return type.parseAttributesHelper(artf, attrMap);
    }

    public static class AttributeExtractor implements BiFunction<BlackboardArtifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute>, String> {

        @Override
        public String apply(BlackboardArtifact artf, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> attrMap) {
            final BlackboardAttribute attr = attrMap.get(attribute);
            return (attr != null) ? StringUtils.defaultString(attr.getDisplayString()) : " ";
        }

        private final BlackboardAttribute.ATTRIBUTE_TYPE attribute;

        public AttributeExtractor(BlackboardAttribute.ATTRIBUTE_TYPE attribute) {
            this.attribute = attribute;
        }
    }

    public static class EmptyExtractor implements BiFunction<BlackboardArtifact, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute>, String> {

        @Override
        public String apply(BlackboardArtifact t, Map<BlackboardAttribute.ATTRIBUTE_TYPE, BlackboardAttribute> u) {
            return "";
        }
    }
}
