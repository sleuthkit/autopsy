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

import java.io.IOException;
import java.net.InetAddress;
import java.util.MissingResourceException;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchServiceException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An implementation of the KeywordSearchService interface that uses Solr for
 * text indexing and search.
 */
@ServiceProvider(service = KeywordSearchService.class)
public class SolrSearchService implements KeywordSearchService {

    private static final String BAD_IP_ADDRESS_FORMAT = "ioexception occurred when talking to server"; //NON-NLS
    private static final String SERVER_REFUSED_CONNECTION = "server refused connection"; //NON-NLS
    private static final int IS_REACHABLE_TIMEOUT_MS = 1000;

    ArtifactExtractor extractor = new ArtifactExtractor();

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

        try {
            Ingester.getDefault().indexMetaDataOnly(artifact);
        } catch (Ingester.IngesterException ex) {
            throw new TskCoreException(ex.getCause().getMessage(), ex);
        }

        try {
            Ingester.getDefault().indexText(extractor, artifact);
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
        HttpSolrServer solrServer = null;
        if (host == null || host.isEmpty()) {
            throw new KeywordSearchServiceException(NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.MissingHostname")); //NON-NLS
        }
        try {
            solrServer = new HttpSolrServer("http://" + host + ":" + Integer.toString(port) + "/solr"); //NON-NLS;
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

    @Override
    public void close() throws IOException {
    }
}
