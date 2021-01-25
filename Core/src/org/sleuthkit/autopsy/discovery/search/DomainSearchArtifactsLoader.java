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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 * Loads artifacts for the given request. Searches for TSK domain attributes and
 * organizes artifacts by those values.
 */
public class DomainSearchArtifactsLoader extends CacheLoader<DomainSearchArtifactsCache.ArtifactCacheKey, Map<String, List<BlackboardArtifact>>> {

    private static final BlackboardAttribute.Type TSK_DOMAIN = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN);
    
    @Override
    public Map<String, List<BlackboardArtifact>> load(DomainSearchArtifactsCache.ArtifactCacheKey artifactKey) throws TskCoreException, InterruptedException {
        final SleuthkitCase caseDb = artifactKey.getSleuthkitCase();
        final ARTIFACT_TYPE type = artifactKey.getType();
        List<BlackboardArtifact> artifacts = caseDb.getBlackboardArtifacts(type);
        
        Map<String, List<BlackboardArtifact>> artifactsByDomain = new HashMap<>();
            
        // Grab artifacts with matching domain names.
        for (BlackboardArtifact artifact : artifacts) {
            if(Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            final BlackboardAttribute tskDomain = artifact.getAttribute(TSK_DOMAIN);
            if (tskDomain != null) {
                final String normalizedDomain = tskDomain.getValueString().trim().toLowerCase();
                List<BlackboardArtifact> artifactsWithDomain = artifactsByDomain.getOrDefault(normalizedDomain, new ArrayList<>());
                artifactsWithDomain.add(artifact);
                artifactsByDomain.put(normalizedDomain, artifactsWithDomain);
            }
        }

        return artifactsByDomain;
    }
}
