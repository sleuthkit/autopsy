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
package org.sleuthkit.autopsy.keywordsearch;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.logging.Level;
import org.apache.solr.client.solrj.SolrServerException;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.appservices.AutopsyService;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchServiceException;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
import org.sleuthkit.autopsy.textextractors.TextExtractor;
import org.sleuthkit.autopsy.textextractors.TextExtractorFactory;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An implementation of the KeywordSearchService interface that uses Solr for
 * text indexing and search.
 *
 * NOTE: UserPreferences.isMultiUserSupported relies on this class being found.
 * Changes to the name or package of this class will need to be reflected in
 * UserPreferences.isMultiUserSupported.
 */
@ServiceProviders(value = {
    @ServiceProvider(service = KeywordSearchService.class),
    @ServiceProvider(service = AutopsyService.class)
})
public class SolrSearchService implements KeywordSearchService, AutopsyService {

    private static final String BAD_IP_ADDRESS_FORMAT = "ioexception occurred when talking to server"; //NON-NLS
    private static final String SERVER_REFUSED_CONNECTION = "server refused connection"; //NON-NLS
    private static final int IS_REACHABLE_TIMEOUT_MS = 1000;
    private static final Logger logger = Logger.getLogger(SolrSearchService.class.getName());

    /**
     * Indexes the given content for keyword search.
     *
     * IMPORTANT: This indexes the given content, but does not execute a keyword
     * search. For the text of the content to be searched, the indexing has to
     * occur either in the context of an ingest job configured for keyword
     * search, or in the context of an ad hoc keyword search.
     *
     * @param content The content to index.
     *
     * @throws TskCoreException If there is a problem indexing the content.
     */
    @Override
    public void index(Content content) throws TskCoreException {
        if (content == null) {
            return;
        }
        final Ingester ingester = Ingester.getDefault();
        if (content instanceof BlackboardArtifact) {
            BlackboardArtifact artifact = (BlackboardArtifact) content;
            if (artifact.getArtifactID() > 0) {
                /*
                 * Artifact indexing is only supported for artifacts that use
                 * negative artifact ids to avoid overlapping with the object
                 * ids of other types of Content.
                 */
                return;
            }
            try {
                TextExtractor blackboardExtractor = TextExtractorFactory.getExtractor(content, null);
                Reader blackboardExtractedTextReader = blackboardExtractor.getReader();
                String sourceName = artifact.getDisplayName() + "_" + artifact.getArtifactID();
                ingester.indexMetaDataOnly(artifact, sourceName);
                ingester.indexText(blackboardExtractedTextReader, artifact.getArtifactID(), sourceName, content, null);
            } catch (Ingester.IngesterException | TextExtractorFactory.NoTextExtractorFound | TextExtractor.InitReaderException ex) {
                throw new TskCoreException("Error indexing artifact", ex);
            }
        } else {
            try {
                TextExtractor contentExtractor = TextExtractorFactory.getExtractor(content, null);
                Reader contentExtractedTextReader = contentExtractor.getReader();
                ingester.indexText(contentExtractedTextReader, content.getId(), content.getName(), content, null);
            } catch (TextExtractorFactory.NoTextExtractorFound | Ingester.IngesterException | TextExtractor.InitReaderException ex) {
                try {
                    // Try the StringsTextExtractor if Tika extractions fails.
                    TextExtractor stringsExtractor = TextExtractorFactory.getStringsExtractor(content, null);
                    Reader stringsExtractedTextReader = stringsExtractor.getReader();
                    ingester.indexStrings(stringsExtractedTextReader, content.getId(), content.getName(), content, null);
                } catch (Ingester.IngesterException | TextExtractor.InitReaderException ex1) {
                    throw new TskCoreException("Error indexing content", ex1);
                }
            }
            // only do a Solr commit if ingest is not running. If ingest is running, the changes will 
            // be committed via a periodic commit or via final commit after the ingest job has finished.
            if (!IngestManager.getInstance().isIngestRunning()) {
                ingester.commit();
            }
        }
    }

    /**
     * Tries to connect to the keyword search service.
     *
     * @param host The hostname or IP address of the service.
     * @param port The port used by the service.
     *
     * @throws KeywordSearchServiceException if cannot connect.
     */
    @Override
    public void tryConnect(String host, int port) throws KeywordSearchServiceException {
        if (host == null || host.isEmpty()) {
            throw new KeywordSearchServiceException(NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.MissingHostname")); //NON-NLS
        }
        try {
            KeywordSearch.getServer().connectToSolrServer(host, Integer.toString(port));
        } catch (SolrServerException ex) {
            logger.log(Level.SEVERE, "Unable to connect to Solr server. Host: " + host + ", port: " + port, ex);
            throw new KeywordSearchServiceException(NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.HostnameOrPort")); //NON-NLS*/
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Unable to connect to Solr server. Host: " + host + ", port: " + port, ex);
            String result = NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.HostnameOrPort"); //NON-NLS
            String message = ex.getCause().getMessage().toLowerCase();
            if (message.startsWith(SERVER_REFUSED_CONNECTION)) {
                try {
                    if (InetAddress.getByName(host).isReachable(IS_REACHABLE_TIMEOUT_MS)) {
                        // if we can reach the host, then it's probably port problem
                        result = Bundle.SolrConnectionCheck_Port();
                    } else {
                        result = NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.HostnameOrPort"); //NON-NLS
                    }
                } catch (IOException | MissingResourceException any) {
                    // it may be anything
                    result = NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.HostnameOrPort"); //NON-NLS
                }
            } else if (message.startsWith(BAD_IP_ADDRESS_FORMAT)) {
                result = NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.Hostname"); //NON-NLS
            }
            throw new KeywordSearchServiceException(result);
        } catch (NumberFormatException ex) {
            logger.log(Level.SEVERE, "Unable to connect to Solr server. Host: " + host + ", port: " + port, ex);
            throw new KeywordSearchServiceException(Bundle.SolrConnectionCheck_Port());
        } catch (IllegalArgumentException ex) {
            logger.log(Level.SEVERE, "Unable to connect to Solr server. Host: " + host + ", port: " + port, ex);
            throw new KeywordSearchServiceException(ex.getMessage());
        }
    }

    /**
     * Deletes a data source from Solr for a case.
     *
     * @param dataSourceId the id of the data source to delete.
     *
     * @throws
     * org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchServiceException
     */
    @Override
    public void deleteDataSource(Long dataSourceId) throws KeywordSearchServiceException {

        try {
            Server ddsServer = KeywordSearch.getServer();
            ddsServer.deleteDataSource(dataSourceId);
        } catch (IOException | KeywordSearchModuleException | NoOpenCoreException | SolrServerException ex) {
            logger.log(Level.WARNING, NbBundle.getMessage(SolrSearchService.class, "SolrSearchService.DeleteDataSource.msg", dataSourceId), ex);
            throw new KeywordSearchServiceException(NbBundle.getMessage(SolrSearchService.class, "SolrSearchService.DeleteDataSource.msg", dataSourceId), ex);
        }
    }

    /**
     * Deletes Solr core for a case.
     *
     * @param metadata The CaseMetadata which will have its core deleted.
     */
    @NbBundle.Messages({
        "# {0} - case directory", "SolrSearchService.exceptionMessage.noIndexMetadata=Unable to create IndexMetaData from case directory: {0}",
        "SolrSearchService.exceptionMessage.noCurrentSolrCore=IndexMetadata did not contain a current Solr core so could not delete the case",
        "# {0} - collection name", "SolrSearchService.exceptionMessage.unableToDeleteCollection=Unable to delete collection {0}",
        "# {0} - index folder path", "SolrSearchService.exceptionMessage.failedToDeleteIndexFiles=Failed to delete text index files at {0}"
    })
    @Override
    public void deleteTextIndex(CaseMetadata metadata) throws KeywordSearchServiceException {
        String caseDirectory = metadata.getCaseDirectory();
        IndexMetadata indexMetadata;
        try {
            indexMetadata = new IndexMetadata(caseDirectory);
        } catch (IndexMetadata.TextIndexMetadataException ex) {
            logger.log(Level.WARNING, NbBundle.getMessage(SolrSearchService.class, "SolrSearchService.exceptionMessage.noIndexMetadata", caseDirectory), ex);
            throw new KeywordSearchServiceException(NbBundle.getMessage(SolrSearchService.class, "SolrSearchService.exceptionMessage.noIndexMetadata", caseDirectory), ex);
        }

        if (indexMetadata.getIndexes().isEmpty()) {
            logger.log(Level.WARNING, NbBundle.getMessage(SolrSearchService.class,
                    "SolrSearchService.exceptionMessage.noCurrentSolrCore"));
            throw new KeywordSearchServiceException(NbBundle.getMessage(SolrSearchService.class,
                    "SolrSearchService.exceptionMessage.noCurrentSolrCore"));
        }

        // delete index(es) for this case        
        for (Index index : indexMetadata.getIndexes()) {
            try {
                // Unload/delete the collection on the server and then delete the text index files.
                KeywordSearch.getServer().deleteCollection(index.getIndexName(), metadata);
            } catch (KeywordSearchModuleException ex) {
                throw new KeywordSearchServiceException(Bundle.SolrSearchService_exceptionMessage_unableToDeleteCollection(index.getIndexName()), ex);
            }
            File indexDir = new File(index.getIndexPath()).getParentFile();
            if (indexDir.exists()) {
                if (!FileUtil.deleteDir(indexDir)) {
                    throw new KeywordSearchServiceException(Bundle.SolrSearchService_exceptionMessage_failedToDeleteIndexFiles(index.getIndexPath()));
                }
            }
        }
    }

    @Override
    public String getServiceName() {
        return NbBundle.getMessage(this.getClass(), "SolrSearchService.ServiceName");
    }

    /**
     * Creates/opens the Solr core/text index for a case
     *
     * @param context The case context.
     *
     * @throws
     * org.sleuthkit.autopsy.appservices.AutopsyService.AutopsyServiceException
     */
    @Override
    @NbBundle.Messages({
        "SolrSearch.lookingForMetadata.msg=Looking for text index metadata file",
        "SolrSearch.readingIndexes.msg=Reading text index metadata file",
        "SolrSearch.findingIndexes.msg=Looking for existing text index directories",
        "SolrSearch.creatingNewIndex.msg=Creating new text index",
        "SolrSearch.checkingForLatestIndex.msg=Looking for text index with latest Solr and schema version",
        "SolrSearch.indentifyingIndex.msg=Identifying text index to use",
        "SolrSearch.openCore.msg=Opening text index. For large cases this may take several minutes.",
        "# {0} - futureVersion", "# {1} - currentVersion",
        "SolrSearch.futureIndexVersion.msg=The text index for the case is for Solr {0}. This version of Autopsy is compatible with Solr {1}.",
        "SolrSearch.unableToFindIndex.msg=Unable to find index that can be used for this case",
        "SolrSearch.complete.msg=Text index successfully opened"})
    public void openCaseResources(CaseContext context) throws AutopsyServiceException {
        if (context.cancelRequested()) {
            return;
        }

        ProgressIndicator progress = context.getProgressIndicator();
        int totalNumProgressUnits = 7;
        int progressUnitsCompleted = 0;

        String caseDirPath = context.getCase().getCaseDirectory();
        Case theCase = context.getCase();
        List<Index> indexes = new ArrayList<>();
        progress.progress(Bundle.SolrSearch_lookingForMetadata_msg(), totalNumProgressUnits);
        if (IndexMetadata.isMetadataFilePresent(caseDirPath)) {
            try {
                // metadata file exists, get list of existing Solr cores for this case
                progressUnitsCompleted++;
                progress.progress(Bundle.SolrSearch_findingIndexes_msg(), progressUnitsCompleted);
                IndexMetadata indexMetadata = new IndexMetadata(caseDirPath);
                indexes = indexMetadata.getIndexes();
            } catch (IndexMetadata.TextIndexMetadataException ex) {
                logger.log(Level.SEVERE, String.format("Unable to read text index metadata file"), ex);
                throw new AutopsyServiceException("Unable to read text index metadata file", ex);
            }
        }

        if (context.cancelRequested()) {
            return;
        }

        // check if we found any existing indexes
        Index currentVersionIndex = null;
        if (indexes.isEmpty()) {
            // new case that doesn't have an existing index. create new index folder
            progressUnitsCompleted++;
            progress.progress(Bundle.SolrSearch_creatingNewIndex_msg(), progressUnitsCompleted);
            currentVersionIndex = IndexFinder.createLatestVersionIndex(theCase);
            // add current index to the list of indexes that exist for this case
            indexes.add(currentVersionIndex);
        } else {
            // check if one of the existing indexes is for latest Solr version and schema
            progressUnitsCompleted++;
            progress.progress(Bundle.SolrSearch_checkingForLatestIndex_msg(), progressUnitsCompleted);
            currentVersionIndex = IndexFinder.findLatestVersionIndex(indexes);
            if (currentVersionIndex == null) {
                // found existing index(es) but none were for latest Solr version and schema version
                progressUnitsCompleted++;
                progress.progress(Bundle.SolrSearch_indentifyingIndex_msg(), progressUnitsCompleted);
                Index indexToUse = IndexFinder.identifyIndexToUse(indexes);
                if (indexToUse == null) {
                    // unable to find index that can be used. check if the available index is for a "future" version of Solr, 
                    // i.e. the user is using an "old/legacy" version of Autopsy to open cases created by later versions of Autopsy.
                    String futureIndexVersion = IndexFinder.isFutureIndexPresent(indexes);
                    if (!futureIndexVersion.isEmpty()) {
                        throw new AutopsyServiceException(Bundle.SolrSearch_futureIndexVersion_msg(futureIndexVersion, IndexFinder.getCurrentSolrVersion()));
                    }
                    throw new AutopsyServiceException(Bundle.SolrSearch_unableToFindIndex_msg());
                }

                if (context.cancelRequested()) {
                    return;
                }

                // check if schema is compatible
                if (!indexToUse.isCompatible(IndexFinder.getCurrentSchemaVersion())) {
                    String msg = "Text index schema version " + indexToUse.getSchemaVersion() + " is not compatible with current schema";
                    logger.log(Level.WARNING, msg);
                    throw new AutopsyServiceException(msg);
                }
                // proceed with case open
                currentVersionIndex = indexToUse;
            }
        }

        try {
            // update text index metadata file
            if (!indexes.isEmpty()) {
                IndexMetadata indexMetadata = new IndexMetadata(caseDirPath, indexes);
            }
        } catch (IndexMetadata.TextIndexMetadataException ex) {
            throw new AutopsyServiceException("Failed to save Solr core info in text index metadata file", ex);
        }

        // open core
        try {
            progress.progress(Bundle.SolrSearch_openCore_msg(), totalNumProgressUnits - 1);
            KeywordSearch.getServer().openCoreForCase(theCase, currentVersionIndex);
        } catch (KeywordSearchModuleException ex) {
            throw new AutopsyServiceException(String.format("Failed to open or create core for %s", caseDirPath), ex);
        }
        if (context.cancelRequested()) {
            return;
        }

        theCase.getSleuthkitCase().registerForEvents(this);

        progress.progress(Bundle.SolrSearch_complete_msg(), totalNumProgressUnits);
    }

    /**
     * Closes the open core.
     *
     * @param context
     *
     * @throws
     * org.sleuthkit.autopsy.appservices.AutopsyService.AutopsyServiceException
     */
    @Override
    public void closeCaseResources(CaseContext context) throws AutopsyServiceException {
        /*
         * TODO (JIRA 2525): The following code KeywordSearch.CaseChangeListener
         * gambles that any BlackboardResultWriters (SwingWorkers) will complete
         * in less than roughly two seconds. This stuff should be reworked using
         * an ExecutorService and tasks with Futures.
         */
        AdHocSearchChildFactory.BlackboardResultWriter.stopAllWriters();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, "Unexpected interrupt while waiting for BlackboardResultWriters to terminate", ex);
        }

        try {
            KeywordSearch.getServer().closeCore();
        } catch (KeywordSearchModuleException ex) {
            throw new AutopsyServiceException(String.format("Failed to close core for %s", context.getCase().getCaseDirectory()), ex);
        }

        if (context.getCase().getSleuthkitCase() != null) {
            context.getCase().getSleuthkitCase().unregisterForEvents(this);
        }
    }

    /**
     * Adds an artifact to the keyword search text index as a concantenation of
     * all of its attributes.
     *
     * @param artifact The artifact to index.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     * @deprecated Call index(Content) instead.
     */
    @Deprecated
    @Override
    public void indexArtifact(BlackboardArtifact artifact) throws TskCoreException {
        if (artifact == null) {
            return;
        }

        // We only support artifact indexing for Autopsy versions that use
        // the negative range for artifact ids.
        if (artifact.getArtifactID() > 0) {
            return;
        }
        final Ingester ingester = Ingester.getDefault();

        try {
            String sourceName = artifact.getDisplayName() + "_" + artifact.getArtifactID();
            TextExtractor blackboardExtractor = TextExtractorFactory.getExtractor((Content) artifact, null);
            Reader blackboardExtractedTextReader = blackboardExtractor.getReader();
            ingester.indexMetaDataOnly(artifact, sourceName);
            ingester.indexText(blackboardExtractedTextReader, artifact.getId(), sourceName, artifact, null);
        } catch (Ingester.IngesterException | TextExtractorFactory.NoTextExtractorFound | TextExtractor.InitReaderException ex) {
            throw new TskCoreException(ex.getCause().getMessage(), ex);
        }
    }
}
