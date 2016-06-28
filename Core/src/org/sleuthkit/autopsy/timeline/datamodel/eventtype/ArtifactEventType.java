/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-16 Basis Technology Corp.
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

import java.text.MessageFormat;
import java.util.Optional;
import java.util.function.Function;
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

    public static final Logger LOGGER = Logger.getLogger(ArtifactEventType.class.getName());

    /**
     * Get the artifact type this event type is derived from.
     *
     * @return The artifact type this event type is derived from.
     */
    public BlackboardArtifact.Type getArtifactType();

    /**
     * The attribute type this event type is derived from.
     *
     * @return The attribute type this event type is derived from.
     */
    public BlackboardAttribute.Type getDateTimeAttributeType();

    /**
     * Get the ID of the the artifact type that this EventType is derived from.
     *
     * @return the ID of the the artifact type that this EventType is derived
     *         from.
     */
    public default int getArtifactTypeID() {
        return getArtifactType().getTypeID();
    }

    /**
     * given an artifact, pull out the time stamp, and compose the descriptions.
     * Each implementation of {@link ArtifactEventType} needs to implement
     * parseAttributesHelper() as hook for {@link buildEventDescription(org.sleuthkit.datamodel.BlackboardArtifact)
     * to invoke. Most subtypes can use this default implementation.
     *
     * @param artf
     *
     * @return an {@link AttributeEventDescription} containing the timestamp
     *         and description information
     *
     * @throws TskCoreException
     */
    default AttributeEventDescription parseAttributesHelper(BlackboardArtifact artf) throws TskCoreException {
        final BlackboardAttribute dateTimeAttr = artf.getAttribute(getDateTimeAttributeType());

        long time = dateTimeAttr.getValueLong();
        String shortDescription = getShortExtractor().apply(artf);
        String medDescription = shortDescription + " : " + getMedExtractor().apply(artf);
        String fullDescription = medDescription + " : " + getFullExtractor().apply(artf);
        return new AttributeEventDescription(time, shortDescription, medDescription, fullDescription);
    }

    /**
     * @return a function from an artifact to a String to use as part of the
     *         full event description
     */
    Function<BlackboardArtifact, String> getFullExtractor();

    /**
     * @return a function from an artifact to a String to use as part of the
     *         medium event description
     */
    Function<BlackboardArtifact, String> getMedExtractor();

    /**
     * @return a function from an artifact to a String to use as part of the
     *         short event description
     */
    Function<BlackboardArtifact, String> getShortExtractor();

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
     * {@link ArtifactEventType}'s implementation of
     * {@link ArtifactEventType#parseAttributesHelper()} to know how to go from
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
        if (type.getArtifactTypeID() != artf.getArtifactTypeID()) {
            throw new IllegalArgumentException();
        }
        if (artf.getAttribute(type.getDateTimeAttributeType()) == null) {
            LOGGER.log(Level.WARNING, "Artifact {0} has no date/time attribute, skipping it.", artf.getArtifactID()); // NON-NLS
            return null;
        }
        //use the hook provided by this subtype implementation
        return type.parseAttributesHelper(artf);
    }

    static class AttributeExtractor implements Function<BlackboardArtifact, String> {

        public String apply(BlackboardArtifact artf) {
            return Optional.ofNullable(getAttributeSafe(artf, attributeType))
                    .map(BlackboardAttribute::getDisplayString)
                    .map(StringUtils::defaultString)
                    .orElse("");
        }

        private final BlackboardAttribute.Type attributeType;

        public AttributeExtractor(BlackboardAttribute.Type attribute) {
            this.attributeType = attribute;
        }

    }

    static class EmptyExtractor implements Function<BlackboardArtifact, String> {

        @Override
        public String apply(BlackboardArtifact t) {
            return "";
        }
    }

    static BlackboardAttribute getAttributeSafe(BlackboardArtifact artf, BlackboardAttribute.Type attrType) {
        try {
            return artf.getAttribute(attrType);
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("Error getting attribute from artifact {0}.", artf.getArtifactID()), ex); // NON-NLS
            return null;
        }
    }

    
}
