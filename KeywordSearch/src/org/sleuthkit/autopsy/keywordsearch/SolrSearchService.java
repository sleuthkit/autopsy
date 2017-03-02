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
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.framework.AutopsyService;
import org.sleuthkit.autopsy.framework.ProgressIndicator;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchServiceException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An implementation of the KeywordSearchService interface that uses Solr for
 * text indexing and search.
 */
@ServiceProviders(value = {
    @ServiceProvider(service = KeywordSearchService.class),
    @ServiceProvider(service = AutopsyService.class)}
)
public class SolrSearchService implements KeywordSearchService, AutopsyService {

    private static final String BAD_IP_ADDRESS_FORMAT = "ioexception occurred when talking to server"; //NON-NLS
    private static final String SERVER_REFUSED_CONNECTION = "server refused connection"; //NON-NLS
    private static final int IS_REACHABLE_TIMEOUT_MS = 1000;
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
     * Tries to connect to the keyword search service.
     *
     * @param host The hostname or IP address of the service.
     * @param port The port used by the service.
     *
     * @throws KeywordSearchServiceException if cannot connect.
     */
    @Override
    public void tryConnect(String host, int port) throws KeywordSearchServiceException {
        HttpSolrClient solrServer = null;
        if (host == null || host.isEmpty()) {
            throw new KeywordSearchServiceException(NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.MissingHostname")); //NON-NLS
        }
        try {
            solrServer = new HttpSolrClient.Builder("http://" + host + ":" + Integer.toString(port) + "/solr").build(); //NON-NLS
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
                try {
                    solrServer.close();
                } catch (IOException ex) {
                    throw new KeywordSearchServiceException(ex.getMessage());
                }
            }
        }
    }

    /**
     * Deletes Solr core for a case.
     *
     * @param coreName The core name.
     */
    @Override
    public void deleteTextIndex(String coreName) throws KeywordSearchServiceException {
        KeywordSearch.getServer().deleteCore(coreName);
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public String getServiceName() {
        return NbBundle.getMessage(this.getClass(), "SolrSearchService.ServiceName");
    }

    /**
     * Creates/opens/upgrades the Solr core/text index for a case
     *
     * @param context The case context.
     *
     * @throws
     * org.sleuthkit.autopsy.framework.AutopsyService.AutopsyServiceException
     */
    @Override
    @NbBundle.Messages({
        "SolrSearch.lookingForMetadata.msg=Looking for text index metadata file",
        "SolrSearch.readingIndexes.msg=Reading text index metadata file",
        "SolrSearch.findingIndexes.msg=Looking for existing text index directories",
        "SolrSearch.creatingNewIndex.msg=Creating new text index",
        "SolrSearch.checkingForLatestIndex.msg=Looking for text index with latest Solr and schema version",
        "SolrSearch.indentifyingIndex.msg=Identifying text index for upgrade",
        "SolrSearch.copyIndex.msg=Copying existing text index",
        "SolrSearch.openCore.msg=Creating/Opening text index",
        "SolrSearch.complete.msg=Text index successfully opened"})
    public void openCaseResources(CaseContext context) throws AutopsyServiceException {
        ProgressIndicator progress = context.getProgressIndicator();
        int totalNumProgressUnits = 8;
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
            // do case subdirectory search to look for Solr 4 Schema 1.8 indexes that can be upgraded
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

        // check if we found an index that needs upgrade
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
                Index indexToUpgrade = IndexFinder.identifyIndexToUpgrade(indexes);
                if (indexToUpgrade == null) {
                    // unable to find index that can be upgraded
                    throw new AutopsyServiceException("Unable to find index that can be upgraded to the latest version of Solr");
                }

                if (context.cancelRequested()) {
                    return;
                }

                double currentSolrVersion = NumberUtils.toDouble(IndexFinder.getCurrentSolrVersion());
                double indexSolrVersion = NumberUtils.toDouble(indexToUpgrade.getSolrVersion());
                if (indexSolrVersion > currentSolrVersion) {
                    // oops!
                    throw new AutopsyServiceException("Unable to find index to use for Case open");
                } else if (indexSolrVersion == currentSolrVersion) {
                    // latest Solr version but not latest schema. index should be used in read-only mode and not be upgraded.
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
                    currentVersionIndex = indexToUpgrade;
                } else {
                    // index needs to be upgraded to latest supported version of Solr
                    if (RuntimeProperties.runningWithGUI()) {
                        //pop up a message box to indicate the restrictions on adding additional 
                        //text and performing regex searches
                        JOptionPane optionPane = new JOptionPane(
                                NbBundle.getMessage(this.getClass(), "SolrSearchService.IndexUpgradeDialog.msg"),
                                JOptionPane.WARNING_MESSAGE,
                                JOptionPane.YES_NO_OPTION);
                        try {
                            SwingUtilities.invokeAndWait(() -> {
                                JDialog dialog = optionPane.createDialog(NbBundle.getMessage(this.getClass(), "SolrSearchService.IndexUpgradeDialog.title"));
                                dialog.setVisible(true);
                            });
                        } catch (InterruptedException ex) {
                            // Cancelled
                            return;
                        } catch (InvocationTargetException ex) {
                            throw new AutopsyServiceException("Error displaying upgrade confirmation dialog", ex);
                        }
                        Object response = optionPane.getValue();
                        if (JOptionPane.NO_OPTION == (int) response) {
                            return;
                        }
                    }

                    // Copy the existing index and config set into ModuleOutput/keywordsearch/data/solrX_schema_Y/
                    progressUnitsCompleted++;
                    progress.progress(Bundle.SolrSearch_copyIndex_msg(), progressUnitsCompleted);
                    String newIndexDirPath = IndexFinder.copyExistingIndex(indexToUpgrade, context);
                    File newIndexVersionDir = new File(newIndexDirPath).getParentFile();
                    if (context.cancelRequested()) {
                        try {
                            FileUtils.deleteDirectory(newIndexVersionDir);
                        } catch (IOException ex) {
                            logger.log(Level.SEVERE, String.format("Failed to delete %s when upgrade cancelled", newIndexVersionDir), ex);
                        }
                        return;
                    }

                    // upgrade the existing index to the latest supported Solr version
                    IndexUpgrader indexUpgrader = new IndexUpgrader();
                    currentVersionIndex = indexUpgrader.performIndexUpgrade(newIndexDirPath, indexToUpgrade, context, progressUnitsCompleted);
                    if (currentVersionIndex == null) {
                        try {
                            FileUtils.deleteDirectory(newIndexVersionDir);
                        } catch (IOException ex) {
                            logger.log(Level.SEVERE, String.format("Failed to delete %s when upgrade cancelled", newIndexVersionDir), ex);
                        }
                        return;
                    }
                    
                    // add current index to the list of indexes that exist for this case
                    indexes.add(currentVersionIndex);
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
            progress.progress(Bundle.SolrSearch_openCore_msg(), totalNumProgressUnits - 1);
            KeywordSearch.getServer().openCoreForCase(theCase, currentVersionIndex);
        } catch (KeywordSearchModuleException ex) {
            throw new AutopsyServiceException(String.format("Failed to open or create core for %s", caseDirPath), ex);
        } 

        progress.progress(Bundle.SolrSearch_complete_msg(), totalNumProgressUnits);
    }

    /**
     *
     * @param context
     *
     * @throws
     * org.sleuthkit.autopsy.framework.AutopsyService.AutopsyServiceException
     */
    @Override
    public void closeCaseResources(CaseContext context) throws AutopsyServiceException {
        /*
         * TODO (AUT-2084): The following code KeywordSearch.CaseChangeListener
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
