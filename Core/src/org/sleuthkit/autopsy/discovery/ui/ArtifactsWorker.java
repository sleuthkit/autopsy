/*
 * Autopsy
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import static javax.swing.JComponent.TOOL_TIP_TEXT_KEY;
import javax.swing.SwingWorker;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.discovery.search.DiscoveryEventUtils;
import org.sleuthkit.autopsy.discovery.search.DomainSearch;
import org.sleuthkit.autopsy.discovery.search.DomainSearchArtifactsRequest;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 *
 * @author wschaefer
 */
public class ArtifactsWorker extends SwingWorker<List<BlackboardArtifact>, Void> {

    private final BlackboardArtifact.ARTIFACT_TYPE artifactType = null;
    private final static Logger logger = Logger.getLogger(ArtifactsWorker.class.getName());
    private final String domain = null;

    @Override
    protected List<BlackboardArtifact> doInBackground() throws Exception {
        if (artifactType != null && !StringUtils.isBlank(domain)) {
            DomainSearch domainSearch = new DomainSearch();
            return domainSearch.getArtifacts(new DomainSearchArtifactsRequest(Case.getCurrentCase().getSleuthkitCase(), domain, artifactType));
        }
        return new ArrayList<>();
    }

    @Override
    protected void done() {
        List<BlackboardArtifact> listOfArtifacts = new ArrayList<>();
        if (!isCancelled()) {
            try {
                listOfArtifacts.addAll(get());
            } catch (InterruptedException | ExecutionException ex) {
                logger.log(Level.SEVERE, "Exception while trying to get list of artifacts for Domain details for artifact type: "
                        + artifactType.getDisplayName() + " and domain: " + domain, ex);
            }
        }
        DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.ArtifactListRetrievedEvent(artifactType, listOfArtifacts));
    }

}
