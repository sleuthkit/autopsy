/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import java.io.IOException;
import java.util.logging.Level;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.CoreAdminResponse;

/**
 * Checks if we can communicate with Solr.
 */
public class SolrServiceConnectionTester {

    /**
     * Checks if we can communicate with Solr using the passed-in host and port.
     * Closes the connection upon exit.
     *
     * @param host the remote hostname or IP address of the Solr server
     * @param port the remote port for Solr
     *
     * @return true if communication with Solr is functional, false otherwise
     */
    public static boolean canConnect(String host, String port) {
        try {
            // if the port value is invalid, return false
            Integer.parseInt(port);
        } catch (NumberFormatException ex) {
            Logger.getLogger(SolrServiceConnectionTester.class.getName()).log(Level.INFO, "Solr port is not valid."); //NON-NLS
            return false;
        }
        HttpSolrServer solrServer = null;
        try {
            solrServer = new HttpSolrServer("http://" + host + ":" + port + "/solr"); //NON-NLS
            CoreAdminResponse status = CoreAdminRequest.getStatus(null, solrServer);
            return true; // if we get here, it's at least up and responding
        } catch (SolrServerException | IOException ignore) {
            Logger.getLogger(SolrServiceConnectionTester.class.getName()).log(Level.INFO, "Could not communicate with Solr."); //NON-NLS
        } finally {
            if (solrServer != null) {
                solrServer.shutdown();
            }
        }
        return false;
    }
}
