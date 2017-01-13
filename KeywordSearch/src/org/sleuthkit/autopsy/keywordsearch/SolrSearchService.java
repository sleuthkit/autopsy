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
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.corecomponentinterfaces.AutopsyService;
import org.sleuthkit.autopsy.coreutils.Logger;
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

    private static final Logger logger = Logger.getLogger(IndexHandling.class.getName());
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
        IndexHandling indexHandler = new IndexHandling();
        List<String> indexDirs = indexHandler.findAllIndexDirs(context.getCase());
        
        // check if index needs upgrade
        String currentVersionIndexDir = IndexHandling.findLatestVersionIndexDir(indexDirs);
        if (currentVersionIndexDir.isEmpty()) {
            
            // ELTODO not sure what to do when there are multiple old indexes. grab the first one?
            String oldIndexDir = indexDirs.get(0);

            if (RuntimeProperties.coreComponentsAreActive()) {
                //pop up a message box to indicate the restrictions on adding additional 
                //text and performing regex searches and give the user the option to decline the upgrade
                if (!KeywordSearchUtil.displayConfirmDialog(NbBundle.getMessage(this.getClass(), "SolrSearchService.IndexUpgradeDialog.title"), 
                        NbBundle.getMessage(this.getClass(), "SolrSearchService.IndexUpgradeDialog.msg"), 
                        KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN)) {                    
                    // upgrade declined - throw exception
                    throw new AutopsyServiceException(NbBundle.getMessage(this.getClass(), "SolrSearchService.IndexUpgradeDialog.upgradeDeclinedMsg"));
                }
            }

            // ELTODO Check for cancellation at whatever points are feasible
            
            // Copy the contents (core) of ModuleOutput/keywordsearch/data/index into ModuleOutput/keywordsearch/data/solr6_schema_2.0/index
            String newIndexDir = indexHandler.createReferenceIndexCopy(context.getCase(), oldIndexDir);
            
            // Make a “reference copy” of the configset and place it in ModuleOutput/keywordsearch/data/solr6_schema_2.0/configset
            indexHandler.createReferenceConfigSetCopy(newIndexDir);
            
            // convert path to UNC path
            
            // Run the upgrade tools on the contents (core) in ModuleOutput/keywordsearch/data/solr6_schema_2.0/index
            File tmpDir = Paths.get(context.getCase().getTempDirectory(), "IndexUpgrade").toFile(); //NON-NLS
            tmpDir.mkdirs();

            boolean success = true;
            try {
                // upgrade from Solr 4 to 5. If index is newer than Solr 4 then the upgrade script will throw exception right away.
                success = indexHandler.upgradeSolrIndexVersion4to5(newIndexDir, tmpDir.getAbsolutePath());
            } catch (Exception ex) {
                // this may not be an error, for example if index is Solr 5 to begin with
                logger.log(Level.WARNING, "Exception while upgrading keyword search index from Sorl 4 to Solr 5 {0} ", newIndexDir); //NON-NLS
            }

            try {
                // upgrade from Solr 5 to 6. This one must complete successfully in order to produce a valid Solr 6 index.
                success = indexHandler.upgradeSolrIndexVersion5to6(newIndexDir, tmpDir.getAbsolutePath());
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Exception while upgrading keyword search index from Sorl 5 to Solr 6 {0} ", newIndexDir); //NON-NLS
                success = false;
            }

            success = true; // ELTODO remove
            if (!success) {
                // delete the new directories
                new File(newIndexDir).delete();
                throw new AutopsyServiceException("ELTODO");
            }

            // Open the upgraded index
            
            // execute a test query
            
            success = true; // ELTODO remove
            if (!success) {
                // delete the new directories
                new File(newIndexDir).delete();
                // ELTODO close the upgraded index?
                throw new AutopsyServiceException("ELTODO");
            }

            // set the upgraded reference index as the index to be used for this case
            currentVersionIndexDir = newIndexDir;
        }
        
        // open currentVersionIndexDir index
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
    }

    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }
}
