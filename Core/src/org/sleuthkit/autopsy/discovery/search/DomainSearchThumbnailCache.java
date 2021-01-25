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
import java.awt.Image;
import java.util.concurrent.ExecutionException;

/**
 * Caches thumbnail requests.
 */
public class DomainSearchThumbnailCache {

    private static final int MAXIMUM_CACHE_SIZE = 500;
    private static final LoadingCache<DomainSearchThumbnailRequest, Image> cache
            = CacheBuilder.newBuilder()
                    .maximumSize(MAXIMUM_CACHE_SIZE)
                    .build(new DomainSearchThumbnailLoader());

    /**
     * Get a thumbnail for the requested domain. If the request is new, the
     * thumbnail will be automatically loaded.
     *
     * @param request Requested domain to thumbnail.
     *
     * @return The thumbnail Image instance, or null if no thumbnail is
     *         available.
     *
     * @throws DiscoveryException If any error occurs during thumbnail
     *                            generation.
     */
    public Image get(DomainSearchThumbnailRequest request) throws DiscoveryException {
        try {
            return cache.get(request);
        } catch (ExecutionException ex) {
            //throwing a new exception with the cause so that interrupted exceptions and other causes can be checked inside our wrapper
            throw new DiscoveryException("Error fetching artifacts from cache " + request.toString(), ex.getCause());
        }
    }
}
