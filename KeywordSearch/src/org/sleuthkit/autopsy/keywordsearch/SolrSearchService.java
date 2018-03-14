/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.logging.Level;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.appservices.AutopsyService;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchServiceException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An implementation of the KeywordSearchService interface that uses Solr for
 * text indexing and search.
 */
@ServiceProviders(value = {
    @ServiceProvider(service = KeywordSearchService.class)
    ,
    @ServiceProvider(service = AutopsyService.class)}
)
public class SolrSearchService implements KeywordSearchService, AutopsyService {

    private static final String BAD_IP_ADDRESS_FORMAT = "ioexception occurred when talking to server"; //NON-NLS
    private static final String SERVER_REFUSED_CONNECTION = "server refused connection"; //NON-NLS
    private static final int IS_REACHABLE_TIMEOUT_MS = 1000;
    private static final int LARGE_INDEX_SIZE_GB = 50;
    private static final int GIANT_INDEX_SIZE_GB = 500;
    private static final Logger logger = Logger.getLogger(SolrSearchService.class.getName());

    /**
     * Adds an artifact to the keyword search text index as a concantenation of
     * all of its attributes.
     *
     * @param artifact The artifact to index.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
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
            ingester.indexMetaDataOnly(artifact);
            ingester.indexText(new ArtifactTextExtractor(), artifact, null);
        } catch (Ingester.IngesterException ex) {
            throw new TskCoreException(ex.getCause().getMessage(), ex);
        }
    }

    /**
     * Add the given Content object to the text index.
     * @param content The content to index.
     * @throws TskCoreException 
     */
    @Override
    public void index(Content content) throws TskCoreException {
        if (content == null) {
            return;
        }
        final Ingester ingester = Ingester.getDefault();

        try {
            ingester.indexText(new TikaTextExtractor(), content, null);
        } catch (Ingester.IngesterException ex) {
            try {
                // Try the StringsTextExtractor if Tika extractions fails.
                ingester.indexText(new StringsTextExtractor(), content, null);
            } catch (Ingester.IngesterException ex1) {
                throw new TskCoreException(ex.getCause().getMessage(), ex1);
            }        
        }
        
        // TODO: Review whether this is the right thing to do. We typically use
        // a combination of autoCommit and the SearchRunner to ensure that data
        // is committed but that might not be sufficient for reports (or artifacts).
        ingester.commit();
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
        HttpSolrServer solrServer = null;
        if (host == null || host.isEmpty()) {
            throw new KeywordSearchServiceException(NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.MissingHostname")); //NON-NLS
        }
        try {
            solrServer = new HttpSolrServer("http://" + host + ":" + Integer.toString(port) + "/solr"); //NON-NLS
            KeywordSearch.getServer().connectToSolrServer(solrServer);
        } catch (SolrServerException ex) {
            throw new KeywordSearchServiceException(NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.HostnameOrPort")); //NON-NLS
        } catch (IOException ex) {
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
            throw new KeywordSearchServiceException(Bundle.SolrConnectionCheck_Port());
        } catch (IllegalArgumentException ex) {
            throw new KeywordSearchServiceException(ex.getMessage());
        } finally {
            if (null != solrServer) {
                solrServer.shutdown();
            }
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
        //find the index for the current version of solr (the one we are connected to) and delete its core using the index name
        String currentSchema = IndexFinder.getCurrentSchemaVersion();
        String currentSolr = IndexFinder.getCurrentSolrVersion();
        for (Index index : indexMetadata.getIndexes()) {
            if (index.getSolrVersion().equals(currentSolr) && index.getSchemaVersion().equals(currentSchema)) {
                /*
                 * Unload/delete the core on the server and then delete the text
                 * index files.
                 */
                KeywordSearch.getServer().deleteCore(index.getIndexName(), metadata);
                if (!FileUtil.deleteDir(new File(index.getIndexPath()).getParentFile())) {
                    throw new KeywordSearchServiceException(Bundle.SolrSearchService_exceptionMessage_failedToDeleteIndexFiles(index.getIndexPath()));                    
                }
            }
            return; //only one core exists for each combination of solr and schema version
        }

        //this code this code will only execute if an index for the current core was not found 
        logger.log(Level.WARNING, NbBundle.getMessage(SolrSearchService.class,
                 "SolrSearchService.exceptionMessage.noCurrentSolrCore"));
        throw new KeywordSearchServiceException(NbBundle.getMessage(SolrSearchService.class,
                 "SolrSearchService.exceptionMessage.noCurrentSolrCore"));
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public String getServiceName() {
        return NbBundle.getMessage(this.getClass(), "SolrSearchService.ServiceName");
    }

    /**
     * Creates/opens the Solr core/text index for a case
     *
     * @param context The case context.
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
        "SolrSearch.openCore.msg=Opening text index",
        "SolrSearch.openLargeCore.msg=Opening text index. This may take several minutes.",
        "SolrSearch.openGiantCore.msg=Opening text index. Text index for this case is very large and may take long time to load.",
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
        progress.start(Bundle.SolrSearch_lookingForMetadata_msg(), totalNumProgressUnits);
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
        } else {
            // metadata file doesn't exist.
            // do case subdirectory search to look for Solr 4 Schema 1.8 indexes
            progressUnitsCompleted++;
            progress.progress(Bundle.SolrSearch_findingIndexes_msg(), progressUnitsCompleted);
            Index oldIndex = IndexFinder.findOldIndexDir(theCase);
            if (oldIndex != null) {
                // add index to the list of indexes that exist for this case
                indexes.add(oldIndex);
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
            currentVersionIndex = IndexFinder.createLatestVersionIndexDir(theCase);
            // add current index to the list of indexes that exist for this case
            indexes.add(currentVersionIndex);
        } else {
            // check if one of the existing indexes is for latest Solr version and schema
            progressUnitsCompleted++;
            progress.progress(Bundle.SolrSearch_checkingForLatestIndex_msg(), progressUnitsCompleted);
            currentVersionIndex = IndexFinder.findLatestVersionIndexDir(indexes);
            if (currentVersionIndex == null) {
                // found existing index(es) but none were for latest Solr version and schema version
                progressUnitsCompleted++;
                progress.progress(Bundle.SolrSearch_indentifyingIndex_msg(), progressUnitsCompleted);
                Index indexToUse = IndexFinder.identifyIndexToUse(indexes);
                if (indexToUse == null) {
                    // unable to find index that can be used
                    throw new AutopsyServiceException("Unable to find index that can be used for this case");
                }

                if (context.cancelRequested()) {
                    return;
                }

                double currentSolrVersion = NumberUtils.toDouble(IndexFinder.getCurrentSolrVersion());
                double indexSolrVersion = NumberUtils.toDouble(indexToUse.getSolrVersion());
                if (indexSolrVersion == currentSolrVersion) {
                    // latest Solr version but not latest schema. index should be used in read-only mode
                    if (RuntimeProperties.runningWithGUI()) {
                        // pop up a message box to indicate the read-only restrictions.
                        JOptionPane optionPane = new JOptionPane(
                                NbBundle.getMessage(this.getClass(), "SolrSearchService.IndexReadOnlyDialog.msg"),
                                JOptionPane.WARNING_MESSAGE,
                                JOptionPane.DEFAULT_OPTION);
                        try {
                            SwingUtilities.invokeAndWait(() -> {
                                JDialog dialog = optionPane.createDialog(NbBundle.getMessage(this.getClass(), "SolrSearchService.IndexReadOnlyDialog.title"));
                                dialog.setVisible(true);
                            });
                        } catch (InterruptedException ex) {
                            // Cancelled
                            return;
                        } catch (InvocationTargetException ex) {
                            throw new AutopsyServiceException("Error displaying limited search features warning dialog", ex);
                        }
                    }
                    // proceed with case open
                    currentVersionIndex = indexToUse;
                } else {
                    // index needs to be upgraded to latest supported version of Solr
                    throw new AutopsyServiceException("Unable to find index to use for Case open");
                }
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
            // check text index size to gauge estimated time to open/load the index
            long indexSizeInBytes = FileUtils.sizeOfDirectory(new File(currentVersionIndex.getIndexPath()));
            long sizeInGb = indexSizeInBytes / 1000000000;
            if (sizeInGb < LARGE_INDEX_SIZE_GB) {
                progress.progress(Bundle.SolrSearch_openCore_msg(), totalNumProgressUnits - 1);
            } else if (sizeInGb >= LARGE_INDEX_SIZE_GB && sizeInGb < GIANT_INDEX_SIZE_GB) {
                progress.switchToIndeterminate(Bundle.SolrSearch_openLargeCore_msg());
            } else {
                progress.switchToIndeterminate(Bundle.SolrSearch_openGiantCore_msg());
            }
            
            KeywordSearch.getServer().openCoreForCase(theCase, currentVersionIndex);
        } catch (KeywordSearchModuleException ex) {
            throw new AutopsyServiceException(String.format("Failed to open or create core for %s", caseDirPath), ex);
        }

        progress.progress(Bundle.SolrSearch_complete_msg(), totalNumProgressUnits);
    }

    /**
     * Closes the open core.
     *
     * @param context
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
        KeywordSearchResultFactory.BlackboardResultWriter.stopAllWriters();
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
    }
}
