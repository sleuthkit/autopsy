/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers.artifactviewers;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.blackboardutils.attributes.BlackboardJsonAttrUtil;
import org.sleuthkit.datamodel.blackboardutils.attributes.MessageAttachments;

/**
 * SwingWork for gather the data from the DB needed for showing messages.
 *
 */
class MessageArtifactWorker extends SwingWorker<MessageArtifactWorker.MesssageArtifactData, Void> {

    private BlackboardArtifact artifact;
    private static final Logger logger = Logger.getLogger(MessageArtifactWorker.class.getName());

    private static final BlackboardAttribute.Type TSK_ASSOCIATED_TYPE = new BlackboardAttribute.Type(TSK_ASSOCIATED_ARTIFACT);

    MessageArtifactWorker(BlackboardArtifact artifact) {
        this.artifact = artifact;
    }

    @Override
    protected MesssageArtifactData doInBackground() throws Exception {
        /*
         * If the artifact is a keyword hit, use the associated artifact as the
         * one to show in this viewer
         */
        if (artifact.getArtifactTypeID() == TSK_KEYWORD_HIT.getTypeID()) {
            try {
                getAssociatedArtifact(artifact).ifPresent(associatedArtifact -> {
                    this.artifact = associatedArtifact;
                });
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "error getting associated artifact", ex);
            }
        }

        Map<BlackboardAttribute.Type, BlackboardAttribute> map = getAttributesForArtifact(artifact);
        Set<MessageAttachments.Attachment> attachements = getAttachments(artifact);

        if (isCancelled()) {
            return null;
        }

        return new MesssageArtifactData(artifact, map, attachements);
    }

    /**
     * Returns a map containing all of the attributes for the given artifact.
     *
     * @param artifact Artifact to get the attributes for.
     *
     * @return A map of all the attributes available for the given artifact.
     *
     * @throws TskCoreException
     */
    static private Map<BlackboardAttribute.Type, BlackboardAttribute> getAttributesForArtifact(BlackboardArtifact artifact) throws TskCoreException {
        Map<BlackboardAttribute.Type, BlackboardAttribute> attributeMap = new HashMap<>();
        for (BlackboardAttribute attribute : artifact.getAttributes()) {
            attributeMap.put(attribute.getAttributeType(), attribute);
        }

        return attributeMap;
    }

    /**
     * Returns the set of for the given artifact.
     * 
     * @param art Message artifact to get attachements from.
     * 
     * @return A set of attachments objects, or empty list if none were found;
     * 
     * @throws TskCoreException 
     */
    private Set<MessageAttachments.Attachment> getAttachments(BlackboardArtifact art) throws TskCoreException {

        final Set<MessageAttachments.Attachment> attachments = new HashSet<>();

        //  Attachments are specified in an attribute TSK_ATTACHMENTS as JSON attribute
        BlackboardAttribute attachmentsAttr = art.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ATTACHMENTS));
        if (attachmentsAttr != null) {
            try {
                MessageAttachments msgAttachments = BlackboardJsonAttrUtil.fromAttribute(attachmentsAttr, MessageAttachments.class);
                Collection<MessageAttachments.FileAttachment> fileAttachments = msgAttachments.getFileAttachments();
                for (MessageAttachments.FileAttachment fileAttachment : fileAttachments) {
                    attachments.add(fileAttachment);
                }
                Collection<MessageAttachments.URLAttachment> urlAttachments = msgAttachments.getUrlAttachments();
                for (MessageAttachments.URLAttachment urlAttachment : urlAttachments) {
                    attachments.add(urlAttachment);
                }
            } catch (BlackboardJsonAttrUtil.InvalidJsonException ex) {
                logger.log(Level.WARNING, String.format("Unable to parse json for MessageAttachments object in artifact: %s", art.getName()), ex);
            }
        } else {    // For backward compatibility - email attachements are derived files and children of the email message artifact
            for (Content child : art.getChildren()) {
                if (child instanceof AbstractFile) {
                    attachments.add(new MessageAttachments.FileAttachment((AbstractFile) child));
                }
            }
        }

        return attachments;
    }

    /**
     * Get the artifact associated with the given artifact, if there is one.
     *
     * @param artifact The artifact to get the associated artifact from. Must
     *                 not be null
     *
     * @throws TskCoreException If there is a critical error querying the DB.
     * @return An optional containing the artifact associated with the given
     *         artifact, if there is one.
     */
    static Optional<BlackboardArtifact> getAssociatedArtifact(final BlackboardArtifact artifact) throws TskCoreException {
        BlackboardAttribute attribute = artifact.getAttribute(TSK_ASSOCIATED_TYPE);
        if (attribute != null) {
            //in the context of the Message content viewer the associated artifact will always be a data artifact
            return Optional.of(artifact.getSleuthkitCase().getBlackboard().getDataArtifactById(attribute.getValueLong()));
        }
        return Optional.empty();
    }

    /**
     * Object to store the data gathered by the worker thread.
     */
    static class MesssageArtifactData {

        private final BlackboardArtifact artifact;
        private final Map<BlackboardAttribute.Type, BlackboardAttribute> attributeMap;
        private final Set<MessageAttachments.Attachment> attachements;

        MesssageArtifactData(BlackboardArtifact artifact, Map<BlackboardAttribute.Type, BlackboardAttribute> attributeMap, Set<MessageAttachments.Attachment> attachements) {
            this.artifact = artifact;
            this.attributeMap = attributeMap;
            this.attachements = attachements;
        }

        BlackboardArtifact getArtifact() {
            return artifact;
        }

        Map<BlackboardAttribute.Type, BlackboardAttribute> getAttributeMap() {
            return attributeMap;
        }

        Set<MessageAttachments.Attachment> getAttachements() {
            return attachements;
        }

        /**
         * Returns the display string for the given attribute.
         *
         * @param attributeType Desired attribute.
         *
         * @return Display string for attribute or empty string if the attribute
         *         was not found.
         */
        String getAttributeDisplayString(BlackboardAttribute.ATTRIBUTE_TYPE attributeType) {
            BlackboardAttribute attribute = attributeMap.get(new BlackboardAttribute.Type(attributeType));
            if (attribute != null) {
                return attribute.getDisplayString();
            }

            return "";
        }
    }
}
