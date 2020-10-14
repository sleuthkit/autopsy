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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Caches artifact requests.
 */
public class DomainSearchArtifactsCache {

    private static final int MAXIMUM_CACHE_SIZE = 500;
    private static final LoadingCache<DomainSearchArtifactsRequest, List<BlackboardArtifact>> cache
            = CacheBuilder.newBuilder()
                    .maximumSize(MAXIMUM_CACHE_SIZE)
                    .build(new DomainSearchArtifactsLoader());

    /**
     * Get artifact instances that match the requested criteria. If the request
     * is new, the results will be automatically loaded.
     *
     * @param request Artifact request, specifies type, Case, and domain name.
     *
     * @return A list of matching artifacts.
     *
     * @throws DiscoveryException Any error that occurs during the loading
     *                            process.
     */
    public List<BlackboardArtifact> get(DomainSearchArtifactsRequest request) throws DiscoveryException {
        String typeName = request.getArtifactType().getLabel();
        if (!typeName.startsWith("TSK_WEB")) {
            throw new IllegalArgumentException("Only web artifacts are valid arguments");
        }
        
        try {
            return cache.get(request);
        } catch (ExecutionException ex) {
            throw new DiscoveryException("Error fetching artifacts from cache", ex);
        }
    }
}
