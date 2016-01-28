/*
 * Sleuth Kit Data Model
 *
 * Copyright 2011-2015 Basis Technology Corp.
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
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/**
 * Provides utility methods for blackboard artifact indexing.
 */
public final class Blackboard implements Closeable {

    /**
     * Index the text associated with the given artifact.
     *
     * @param artifact The artifact to be indexed.
     *
     * @throws
     * org.sleuthkit.autopsy.casemodule.services.Blackboard.BlackboardException
     */
    public void indexArtifact(BlackboardArtifact artifact) throws BlackboardException {
        KeywordSearchService searchService = Lookup.getDefault().lookup(KeywordSearchService.class);
        if (null == searchService) {
            throw new BlackboardException(NbBundle.getMessage(this.getClass(), "Blackboard.keywordSearchNotFound.exception.msg"));
        }

        try {
            searchService.indexArtifact(artifact);
        } catch (TskCoreException ex) {
            throw new BlackboardException(NbBundle.getMessage(this.getClass(), "Blackboard.unableToIndexArtifact.exception.msg"), ex);
        }
    }

    /**
     * Adds a new artifact type based upon the parameters given
     *
     * @param typeName The name of the new artifact type
     * @param displayName The name displayed for the new attribute type
     * @return A type object representing the artifact type added
     * @throws
     * org.sleuthkit.autopsy.casemodule.services.Blackboard.BlackboardException
     */
    public BlackboardArtifact.Type addArtifactType(String typeName, String displayName) throws BlackboardException {
        try {
            return Case.getCurrentCase().getSleuthkitCase().addBlackboardArtifactType(typeName, displayName);
        } catch (TskCoreException | TskDataException ex) {
            throw new BlackboardException("New artifact type could not be added", ex);
        }
    }

    /**
     * Adds a new attribute type based upon the parameters given
     *
     * @param attrTypeString The type name of the attribute type
     * @param valueType The type of any attribute of this type's value
     * @param displayName The name displayed for the new attribute type
     * @throws
     * org.sleuthkit.autopsy.casemodule.services.Blackboard.BlackboardException
     */
    public BlackboardAttribute.Type addAttributeType(String attrTypeString, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE valueType, String displayName) throws BlackboardException {
        try {
            return Case.getCurrentCase().getSleuthkitCase().addArtifactAttributeType(attrTypeString, valueType, displayName);
        } catch (TskDataException ex) {
            throw new BlackboardException("New attribute type could not be added", ex);
        } catch (TskCoreException ex) {
            throw new BlackboardException("New attribute type could not be added", ex);
        }
    }

    @Override
    public void close() throws IOException {
    }

    /**
     * Provides a system exception for the Keyword Search package.
     */
    public static final class BlackboardException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new exception with null as its message.
         */
        public BlackboardException() {
            super();
        }

        /**
         * Constructs a new exception with the specified message.
         *
         * @param message The message.
         */
        public BlackboardException(String message) {
            super(message);
        }

        /**
         * Constructs a new exception with the specified message and cause.
         *
         * @param message The message.
         * @param cause The cause.
         */
        public BlackboardException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
