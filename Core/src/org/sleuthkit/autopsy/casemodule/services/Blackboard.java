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
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Provides utility methods for blackboard artifact indexing.
 */
public final class Blackboard implements Closeable {

    Blackboard() {
    }

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
         * @param cause   The cause.
         */
        public BlackboardException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
