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
 */
package org.sleuthkit.autopsy.keywordsearchservice;

import com.google.common.annotations.Beta;
import java.io.Closeable;
import java.io.IOException;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An interface for implementations of a keyword search service. You can find
 * the implementations by using Lookup, such as:
 *
 * Lookup.getDefault().lookup(KeywordSearchService.class)
 *
 * although most clients should obtain a keyword search service by calling:
 *
 * Case.getCurrentCase().getServices().getKeywordSearchService()
 *
 * TODO (AUT-2158): This interface should not extend Closeable.
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
     * @deprecated Call org.sleuthkit.datamodel.Blackboard.postArtifact instead.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    @Deprecated
    public void indexArtifact(BlackboardArtifact artifact) throws TskCoreException;

    /**
     * Add the given Content object to the text index. This message should only
     * be used in atypical cases, such as indexing a report. Artifacts are
     * indexed when org.sleuthkit.datamodel.Blackboard.postArtifact is called
     * and files are indexed during ingest.
     *
     * @param content The content to index.
     *
     * @throws TskCoreException
     */
    public void index(Content content) throws TskCoreException;

    /**
     * Deletes the keyword search text index for a case.
     *
     * @param metadata The CaseMetadata which will have its core deleted.
     *
     * @throws KeywordSearchServiceException if unable to delete.
     */
    public void deleteTextIndex(CaseMetadata metadata) throws KeywordSearchServiceException;

    /**
     * Closes the keyword search service.
     *
     * @throws IOException If there is a problem closing the file manager.
     * @deprecated Do not use.
     */
    @Deprecated
    default public void close() throws IOException {
        /*
         * No-op maintained for backwards compatibility. Clients should not
         * attempt to close case services.
         */
    }

    /**
     * Deletes the keyword search text for a specific data source.
     *
     * @param dataSourceId The data source id to be deleted.
     *
     * @throws KeywordSearchServiceException if unable to delete.
     */
    void deleteDataSource(Long dataSourceId) throws KeywordSearchServiceException;
}
