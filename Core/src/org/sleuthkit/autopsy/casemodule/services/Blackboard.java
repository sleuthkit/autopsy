/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2021 Basis Technology Corp.
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
 * 
 * TODO (AUT-2158): This class should not extend Closeable.
 */
package org.sleuthkit.autopsy.casemodule.services;

import java.io.Closeable;
import java.io.IOException;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 * A representation of the blackboard, a place where artifacts and their
 * attributes are posted.
 *
 * @deprecated Use org.sleuthkit.datamodel.Blackboard instead.
 */
@Deprecated
public final class Blackboard implements Closeable {

    /**
     * Constructs a representation of the blackboard, a place where artifacts
     * and their attributes are posted.
     *
     * @deprecated Do not use.
     */
    @Deprecated
    Blackboard() {
    }

    /**
     * Indexes the text associated with an artifact.
     *
     * @param artifact The artifact to be indexed.
     *
     * @throws BlackboardException If there is a problem indexing the artifact.
     * @deprecated Use org.sleuthkit.datamodel.Blackboard.postArtifact instead.
     */
    @Deprecated
    public synchronized void indexArtifact(BlackboardArtifact artifact) throws BlackboardException {
        try {
            Case.getCurrentCase().getSleuthkitCase().getBlackboard().postArtifact(artifact, "", null);
        } catch (org.sleuthkit.datamodel.Blackboard.BlackboardException ex) {
            throw new BlackboardException(ex.getMessage(), ex);
        }
    }

    /**
     * Gets an artifact type, creating it if it does not already exist. Use this
     * method to define custom artifact types.
     *
     * @param typeName    The type name of the artifact type.
     * @param displayName The display name of the artifact type.
     *
     * @return A type object representing the artifact type.
     *
     * @throws BlackboardException If there is a problem getting or adding the
     *                             artifact type.
     * @deprecated Use org.sleuthkit.datamodel.Blackboard.getOrAddArtifactType
     * instead.
     */
    @Deprecated
    public synchronized BlackboardArtifact.Type getOrAddArtifactType(String typeName, String displayName) throws BlackboardException {
        try {
            return Case.getCurrentCase().getSleuthkitCase().getBlackboard().getOrAddArtifactType(typeName, displayName);
        } catch (org.sleuthkit.datamodel.Blackboard.BlackboardException ex) {
            throw new BlackboardException(ex.getMessage(), ex);
        }
    }

    /**
     * Gets an attribute type, creating it if it does not already exist. Use
     * this method to define custom attribute types.
     *
     * @param typeName    The type name of the attribute type.
     * @param valueType   The value type of the attribute type.
     * @param displayName The display name of the attribute type.
     *
     * @return A type object representing the attribute type.
     *
     * @throws BlackboardException If there is a problem getting or adding the
     *                             attribute type.
     * @deprecated Use org.sleuthkit.datamodel.Blackboard.getOrAddArtifactType
     * instead.
     */
    @Deprecated
    public synchronized BlackboardAttribute.Type getOrAddAttributeType(String typeName, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE valueType, String displayName) throws BlackboardException {
        try {
            return Case.getCurrentCase().getSleuthkitCase().getBlackboard().getOrAddAttributeType(typeName, valueType, displayName);
        } catch (org.sleuthkit.datamodel.Blackboard.BlackboardException ex) {
            throw new BlackboardException(ex.getMessage(), ex);
        }
    }

    /**
     * Closes the artifacts blackboard.
     *
     * @throws IOException If there is a problem closing the artifacts
     *                     blackboard.
     * @deprecated Do not use.
     */
    @Deprecated
    @Override
    public void close() throws IOException {
        /*
         * No-op maintained for backwards compatibility. Clients should not
         * attempt to close case services.
         */
    }

    /**
     * A blackboard exception.
     *
     * @deprecated Use org.sleuthkit.datamodel.Blackboard.BlackboardException
     * instead.
     */
    @Deprecated
    public static final class BlackboardException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs a blackboard exception with the specified message.
         *
         * @param message The message.
         *
         * @deprecated Do not use.
         */
        @Deprecated
        public BlackboardException(String message) {
            super(message);
        }

        /**
         * Constructs a blackboard exception with the specified message and
         * cause.
         *
         * @param message The message.
         * @param cause   The cause.
         *
         * @deprecated Do not use.
         */
        @Deprecated
        public BlackboardException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
