/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.net.InetAddress;
import java.nio.file.Paths;
import java.util.List;
import java.util.MissingResourceException;
import java.util.logging.Level;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.corecomponentinterfaces.AutopsyService;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchServiceException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An implementation of the KeywordSearchService interface that uses Solr for
 * text indexing and search.
 */
@ServiceProviders(value={
    @ServiceProvider(service=KeywordSearchService.class),
    @ServiceProvider(service=AutopsyService.class)}
)
public class SolrSearchService implements KeywordSearchService, AutopsyService  {

    private static final Logger logger = Logger.getLogger(IndexFinder.class.getName());
    private static final String BAD_IP_ADDRESS_FORMAT = "ioexception occurred when talking to server"; //NON-NLS
    private static final String SERVER_REFUSED_CONNECTION = "server refused connection"; //NON-NLS
    private static final int IS_REACHABLE_TIMEOUT_MS = 1000;
    private static final String SERVICE_NAME = "Solr Keyword Search Service";

    ArtifactTextExtractor extractor = new ArtifactTextExtractor();

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
            ingester.indexText(extractor, artifact, null);
        } catch (Ingester.IngesterException ex) {
            throw new TskCoreException(ex.getCause().getMessage(), ex);
        }
    }

    /**
     * Checks if we can communicate with Solr using the passed-in host and port.
     * Closes the connection upon exit. Throws if it cannot communicate with
     * Solr.
     *
     * When issues occur, it attempts to diagnose them by looking at the
     * exception messages, returning the appropriate user-facing text for the
     * exception received. This method expects the Exceptions messages to be in
     * English and compares against English text.
     *
     * @param host the remote hostname or IP address of the Solr server
     * @param port the remote port for Solr
     *
     * @throws
     * org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchServiceException
     *
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

    @Override
    public void close() throws IOException {
    }
    
     /**
     *
     * @param context
     *
     * @throws
     * org.sleuthkit.autopsy.corecomponentinterfaces.AutopsyService.AutopsyServiceException
     */
    @Override
    public void openCaseResources(CaseContext context) throws AutopsyServiceException {
        /*
         * Autopsy service providers may not have case-level resources.
         */
        
        // do a case subdirectory search to check for the existence and upgrade status of KWS indexes
        IndexFinder indexFinder = new IndexFinder();
        List<String> indexDirs = indexFinder.findAllIndexDirs(context.getCase());
        
        // check if index needs upgrade
        String schemaVer; 
        String currentVersionIndexDir = IndexFinder.findLatestVersionIndexDir(indexDirs);
        if (currentVersionIndexDir.isEmpty()) {
            if (indexDirs.isEmpty()) {
                // new case that doesn't have an existing index. create new index folder
                currentVersionIndexDir = IndexFinder.createLatestVersionIndexDir(context.getCase());
                schemaVer = IndexFinder.getCurrentSchemaVersion();
            } else {
                // ELTODO not sure what to do when there are multiple old indexes. grab the first one?
                // ELTODO need to handle here a case where it is Solr 6 index but not latest schema
                // ELTODO figure out the schema version of the "old" index
                String oldIndexDir = indexDirs.get(0);

                if (RuntimeProperties.coreComponentsAreActive()) {
                    //pop up a message box to indicate the restrictions on adding additional 
                    //text and performing regex searches and give the user the option to decline the upgrade
                    if (!KeywordSearchUtil.displayConfirmDialog(NbBundle.getMessage(this.getClass(), "SolrSearchService.IndexUpgradeDialog.title"),
                            NbBundle.getMessage(this.getClass(), "SolrSearchService.IndexUpgradeDialog.msg"),
                            KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN)) {
                        // upgrade declined - throw exception
                        throw new AutopsyServiceException("Index upgrade was declined by user");
                    }
                }

                // ELTODO Check for cancellation at whatever points are feasible
                
                // Copy the "old" index and config set into ModuleOutput/keywordsearch/data/solrX_schema_Y/
                String newIndexDir = indexFinder.copyIndexAndConfigSet(context.getCase(), oldIndexDir);

                // upgrade the "old" index to the latest supported Solr version
                IndexUpgrader indexUpgrader = new IndexUpgrader();
                indexUpgrader.performIndexUpgrade(newIndexDir, context.getCase().getTempDirectory());

                // set the upgraded reference index as the index to be used for this case
                currentVersionIndexDir = newIndexDir;
                // schemaVer = ; // ELTODO
            }
        } else {
            // found index that is latest version of Solr and schema
            schemaVer = IndexFinder.getCurrentSchemaVersion();
        }
                
        // open core
        try {
            // ELTODO figure out the schema version of the core that's being opened
            //KeywordSearch.getServer().openCoreForCase(context.getCase(), currentVersionIndexDir, schemaVer);
            KeywordSearch.getServer().openCoreForCase(context.getCase());
        } catch (Exception ex) {
            logger.log(Level.SEVERE, String.format("Failed to open or create core for %s", context.getCase().getCaseDirectory()), ex); //NON-NLS
            if (RuntimeProperties.coreComponentsAreActive()) {
                MessageNotifyUtil.Notify.error(NbBundle.getMessage(KeywordSearch.class, "KeywordSearch.openCore.notification.msg"), ex.getMessage());
            }
        }

        // ELTODO execute a test query
        // ELTODO if failed, close the upgraded index?
    }

    /**
     *
     * @param context
     *
     * @throws
     * org.sleuthkit.autopsy.corecomponentinterfaces.AutopsyService.AutopsyServiceException
     */
    @Override
    public void closeCaseResources(CaseContext context) throws AutopsyServiceException {
        /*
         * Autopsy service providers may not have case-level resources.
         */
        try {
            KeywordSearchResultFactory.BlackboardResultWriter.stopAllWriters();
            /*
            * TODO (AUT-2084): The following code
            * KeywordSearch.CaseChangeListener gambles that any
            * BlackboardResultWriters (SwingWorkers) will complete
            * in less than roughly two seconds
            */
            Thread.sleep(2000);
            KeywordSearch.getServer().closeCore();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, String.format("Failed to close core for %s", context.getCase().getCaseDirectory()), ex); //NON-NLS
            if (RuntimeProperties.coreComponentsAreActive()) {
                MessageNotifyUtil.Notify.error(NbBundle.getMessage(KeywordSearch.class, "KeywordSearch.closeCore.notification.msg"), ex.getMessage());
            }
        }
    }

    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }
}
