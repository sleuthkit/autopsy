/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.multiusercases.services;

import java.util.logging.Level;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.core.ServicesMonitor;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchServiceException;

/**
 * An implementation of the monitored service interface that reports status for
 * the Solr server for multi-user cases.
 */
@ServiceProvider(service = ServicesMonitor.MonitoredService.class)
public final class SolrServer implements ServicesMonitor.MonitoredService {

    private static final Logger logger = Logger.getLogger(SolrServer.class.getName());

    @Override
    @NbBundle.Messages({
        "SolrServer_missingServiceProviderErrorMsg=Cannot find Keyword Search service provider"
    })
    public ServicesMonitor.ServiceStatusReport getStatus() {
        try {
            KeywordSearchService kwsService = Lookup.getDefault().lookup(KeywordSearchService.class);
            if (kwsService != null) {
                int port = Integer.parseUnsignedInt(UserPreferences.getIndexingServerPort());
                kwsService.tryConnect(UserPreferences.getIndexingServerHost(), port);
                return new ServicesMonitor.ServiceStatusReport(ServicesMonitor.Service.KEYWORD_SEARCH_SERVICE, ServicesMonitor.ServiceStatus.UP, "");
            } else {
                logger.log(Level.SEVERE, "No implementation of KeywordSearchService found"); //NON-NLS
                return new ServicesMonitor.ServiceStatusReport(ServicesMonitor.Service.KEYWORD_SEARCH_SERVICE, ServicesMonitor.ServiceStatus.DOWN, Bundle.SolrServer_missingServiceErrorMsg());
            }
        } catch (NumberFormatException | KeywordSearchServiceException ex) {
            logger.log(Level.SEVERE, "Error connecting to Solr server", ex); //NON-NLS
            return new ServicesMonitor.ServiceStatusReport(ServicesMonitor.Service.KEYWORD_SEARCH_SERVICE, ServicesMonitor.ServiceStatus.DOWN, ex.getMessage());
        }
    }

}
