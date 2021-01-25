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
import com.google.common.eventbus.Subscribe;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.sleuthkit.autopsy.discovery.search.DiscoveryEventUtils.SearchStartedEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;

/**
 * Caches artifact requests.
 */
public class DomainSearchArtifactsCache {

    private static final int MAXIMUM_CACHE_SIZE = 10;
    private static final int TIME_TO_LIVE = 5; // In minutes
    private static final LoadingCache<ArtifactCacheKey, Map<String, List<BlackboardArtifact>>> cache
            = CacheBuilder.newBuilder()
                    .maximumSize(MAXIMUM_CACHE_SIZE)
                    .expireAfterWrite(TIME_TO_LIVE, TimeUnit.MINUTES)
                    .build(new DomainSearchArtifactsLoader());
    
    // Listen for new search events. When this happens, we should invalidate all the
    // entries in the cache. This, along with the 5 minutes expiration, ensures that
    // searches get up to date results during ingest.
    private static final NewSearchListener newSearchListener = new NewSearchListener();
    static {
        DiscoveryEventUtils.getDiscoveryEventBus().register(newSearchListener);
    }
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
            throw new IllegalArgumentException("Only web artifacts are valid arguments. Type provided was " + typeName);
        }
        
        try {
            Map<String, List<BlackboardArtifact>> artifactsByDomain = cache.get(new ArtifactCacheKey(request));
            final String normalizedDomain = request.getDomain().trim().toLowerCase();
            return artifactsByDomain.getOrDefault(normalizedDomain, Collections.emptyList());
        } catch (ExecutionException ex) {
            //throwing a new exception with the cause so that interrupted exceptions and other causes can be checked inside our wrapper
            throw new DiscoveryException("Error fetching artifacts from cache for " + request.toString(), ex.getCause());
        }
    }
    
    /**
     * Listener for new searches performed by the user.
     */
    static class NewSearchListener {
        
        @Subscribe
        public void listenToSearchStartedEvent(SearchStartedEvent event) {
            cache.invalidateAll();
        }
    }
    
    /**
     * Key to use for caching. Using only the artifact type and case reference
     * will result in greater utilization of the cached artifact instances.
     */
    class ArtifactCacheKey {

        private final ARTIFACT_TYPE type;
        private final SleuthkitCase caseDatabase;
        
        private ArtifactCacheKey(DomainSearchArtifactsRequest request) {
            this.type = request.getArtifactType();
            this.caseDatabase = request.getSleuthkitCase();
        }
        
        ARTIFACT_TYPE getType() {
            return this.type;
        }
        
        SleuthkitCase getSleuthkitCase() {
            return this.caseDatabase;
        }
        
        @Override
        public int hashCode() {
            int hash = 7;
            hash = 67 * hash + Objects.hashCode(this.type);
            hash = 67 * hash + Objects.hashCode(this.caseDatabase);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            
            final ArtifactCacheKey other = (ArtifactCacheKey) obj;
            
            // The artifact type and case database references must be equal.
            return this.type == other.type && 
                    Objects.equals(this.caseDatabase, other.caseDatabase);
        }
    }
}
