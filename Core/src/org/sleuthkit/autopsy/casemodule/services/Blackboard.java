/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.services;

import java.io.Closeable;
import java.io.IOException;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/**
 * A representation of the blackboard, a place where artifacts and their
 * attributes are posted.
 *
 * NOTE: This API of this class is under development.
 */
public final class Blackboard implements Closeable {

    private SleuthkitCase caseDb;
    
    /**
     * Constructs a representation of the blackboard, a place where artifacts
     * and their attributes are posted.
     *
     * @param casedb The case database.
     */
    Blackboard(SleuthkitCase casedb) {
        this.caseDb = casedb;
    }

    /**
     * Indexes the text associated with the an artifact.
     *
     * @param artifact The artifact to be indexed.
     *
     * @throws BlackboardException If there is a problem indexing the artifact.
     */
    public synchronized void indexArtifact(BlackboardArtifact artifact) throws BlackboardException {
        if (null == caseDb) {
            throw new BlackboardException("Blackboard has been closed");
        }
        KeywordSearchService searchService = Lookup.getDefault().lookup(KeywordSearchService.class);
        if (null == searchService) {
            throw new BlackboardException("Keyword search service not found");
        }
        try {
            searchService.index(artifact);
        } catch (TskCoreException ex) {
            throw new BlackboardException("Error indexing artifact", ex);
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
     * @throws BlackboardBlackboardException If there is a problem getting or
     *                                       adding the artifact type.
     */
    public synchronized BlackboardArtifact.Type getOrAddArtifactType(String typeName, String displayName) throws BlackboardException {
        if (null == caseDb) {
            throw new BlackboardException("Blackboard has been closed");
        }
        try {
            return caseDb.addBlackboardArtifactType(typeName, displayName);
        } catch (TskDataException typeExistsEx) {
            try {
                return caseDb.getArtifactType(typeName);
            } catch (TskCoreException ex) {
                throw new BlackboardException("Failed to get or add artifact type", ex);
            }
        } catch (TskCoreException ex) {
            throw new BlackboardException("Failed to get or add artifact type", ex);
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
     * @throws BlackboardBlackboardException If there is a problem getting or
     *                                       adding the attribute type.
     */
    public synchronized BlackboardAttribute.Type getOrAddAttributeType(String typeName, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE valueType, String displayName) throws BlackboardException {
        if (null == caseDb) {
            throw new BlackboardException("Blackboard has been closed");
        }
        try {
            return caseDb.addArtifactAttributeType(typeName, valueType, displayName);
        } catch (TskDataException typeExistsEx) {
            try {
                return caseDb.getAttributeType(typeName);
            } catch (TskCoreException ex) {
                throw new BlackboardException("Failed to get or add attribute type", ex);
            }
        } catch (TskCoreException ex) {
            throw new BlackboardException("Failed to get or add attribute type", ex);
        }
    }

    /**
     * Closes the blackboard.
     *
     * @throws IOException If there is a problem closing the blackboard.
     */
    @Override
    public synchronized void close() throws IOException {
        caseDb = null;
    }


    /**
     * A blackboard exception.
     */
    public static final class BlackboardException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs a blackboard exception with the specified message.
         *
         * @param message The message.
         */
        public BlackboardException(String message) {
            super(message);
        }

        /**
         * Constructs a blackboard exception with the specified message and
         * cause.
         *
         * @param message The message.
         * @param cause   The cause.
         */
        public BlackboardException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
