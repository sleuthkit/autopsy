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
package org.sleuthkit.autopsy.discovery.search;

import com.google.common.cache.CacheLoader;
import java.util.List;
import java.util.ArrayList;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute.Type;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Loads artifacts for the given request. Searches TSK_DOMAIN and TSK_URL
 * attributes for the requested domain name. TSK_DOMAIN is exact match (ignoring
 * case). TSK_URL is sub-string match (ignoring case).
 */
public class DomainSearchArtifactsLoader extends CacheLoader<DomainSearchArtifactsRequest, List<BlackboardArtifact>> {

    private static final Type TSK_DOMAIN = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DOMAIN);
    private static final Type TSK_URL = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_URL);

    @Override
    public List<BlackboardArtifact> load(DomainSearchArtifactsRequest artifactsRequest) throws TskCoreException {
        final SleuthkitCase caseDb = artifactsRequest.getSleuthkitCase();
        final String normalizedDomain = artifactsRequest.getDomain().toLowerCase();
        final List<BlackboardArtifact> artifacts = caseDb.getBlackboardArtifacts(artifactsRequest.getArtifactType());
        final List<BlackboardArtifact> matchingDomainArtifacts = new ArrayList<>();

        for (BlackboardArtifact artifact : artifacts) {
            final BlackboardAttribute tskDomain = artifact.getAttribute(TSK_DOMAIN);
            final BlackboardAttribute tskUrl = artifact.getAttribute(TSK_URL);

            if (tskDomain != null && tskDomain.getValueString().toLowerCase().equals(normalizedDomain)) {
                matchingDomainArtifacts.add(artifact);
            } else if (tskUrl != null && tskUrl.getValueString().toLowerCase().contains(normalizedDomain)) {
                matchingDomainArtifacts.add(artifact);
            }
        }

        return matchingDomainArtifacts;
    }
}
