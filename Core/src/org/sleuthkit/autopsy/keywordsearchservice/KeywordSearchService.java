/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearchservice;

import java.io.Closeable;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An interface for implementations of a keyword search service.
 *
 * TODO (AUT-2158: This interface should not extend Closeable.
 */
public interface KeywordSearchService extends Closeable {

    /**
     * Tries to connect to the keyword search service server.
     *
     * @param host The hostname or IP address of the service.
     * @param port The port used by the service.
     *
     * @throws KeywordSearchServiceException if cannot connect.
     */
    public void tryConnect(String host, int port) throws KeywordSearchServiceException;

    /**
     * Adds an artifact to the keyword search text index as a concatenation of
     * all of its attributes.
     *
     * @param artifact The artifact to index.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public void indexArtifact(BlackboardArtifact artifact) throws TskCoreException;

    /**
     * Deletes the keyword search text index for a case.
     *
     * @param textIndexName The text index name.
     * 
     * @throws KeywordSearchServiceException if unable to delete.
     * @deprecated deleteCore(String textIndexName, String caseDirectory) should be used instead to support newer solr cores
     */
    @Deprecated
    public void deleteTextIndex(String textIndexName) throws KeywordSearchServiceException;
    
    public void deleteCore(String textIndexName, String caseDirectory) throws KeywordSearchServiceException;

}
