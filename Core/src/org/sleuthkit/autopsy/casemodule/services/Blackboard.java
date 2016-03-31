/*
 * Sleuth Kit Data Model
 *
 * Copyright 2011-2016 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
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
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/**
 * Represents the blackboard, a place where artifacts and their attributes are
 * posted.
 *
 * NOTE: This API of this class is under development.
 */
public final class Blackboard implements Closeable {

    /**
     * Indexes the text associated with the an artifact.
     *
     * @param artifact The artifact to be indexed.
     *
     * @throws BlackboardException If there is a problem indexing the artifact.
     */
    public void indexArtifact(BlackboardArtifact artifact) throws BlackboardException {
        KeywordSearchService searchService = Lookup.getDefault().lookup(KeywordSearchService.class);
        if (null == searchService) {
            throw new BlackboardException("Keyword search service not found");
        }
        try {
            searchService.indexArtifact(artifact);
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
    public BlackboardArtifact.Type getOrAddArtifactType(String typeName, String displayName) throws BlackboardException {
        try {
            return Case.getCurrentCase().getSleuthkitCase().addBlackboardArtifactType(typeName, displayName);
        } catch (TskDataException typeExistsEx) {
            try {
                return Case.getCurrentCase().getSleuthkitCase().getArtifactType(typeName);
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
    public BlackboardAttribute.Type addAttributeType(String typeName, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE valueType, String displayName) throws BlackboardException {
        try {
            return Case.getCurrentCase().getSleuthkitCase().addArtifactAttributeType(typeName, valueType, displayName);
        } catch (TskDataException typeExistsEx) {
            try {
                return Case.getCurrentCase().getSleuthkitCase().getAttributeType(typeName);
            } catch (TskCoreException ex) {
                throw new BlackboardException("Failed to get or add attribute type", ex);
            }
        } catch (TskCoreException ex) {
            throw new BlackboardException("Failed to get or add attribute type", ex);
        }
    }

    /**
     * Cloese this blackboard and releases any resources associated with it. 
     * @throws IOException 
     */
    @Override
    public void close() throws IOException {
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
