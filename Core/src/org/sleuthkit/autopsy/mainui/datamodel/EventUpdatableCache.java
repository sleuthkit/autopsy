/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.datamodel;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * A partial implementation of a cache of key value pairs where an event has the
 * potential to invalidate particular cache entries.
 *
 * @param <K> The key type.
 * @param <V> The value type.
 * @param <E> The event type.
 */
abstract class EventUpdatableCache<K, V, E> {

    private static final int DEFAULT_CACHE_SIZE = 15; // rule of thumb: 5 entries times number of cached SearchParams sub-types
    private static final long DEFAULT_CACHE_DURATION = 2;
    private static final TimeUnit CACHE_DURATION_UNITS = TimeUnit.MINUTES;

    private final Cache<K, V> cache;

    /**
     * Constructor with default underlying cache.
     */
    EventUpdatableCache() {
        this(CacheBuilder.newBuilder()
                .maximumSize(DEFAULT_CACHE_SIZE)
                .expireAfterAccess(DEFAULT_CACHE_DURATION, CACHE_DURATION_UNITS)
                .build());
    }

    /**
     * Constructor.
     * @param cache Non-default cache to use as underlying data source. 
     */
    EventUpdatableCache(Cache<K, V> cache) {
        this.cache = cache;
    }

    /**
     * Returns the value in the cache for the given key. If the key is not
     * present in the cache, the data is fetched and cached.
     *
     * @param key The key.
     *
     * @return The value for the key in the cache.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    V getValue(K key) throws IllegalArgumentException, ExecutionException {
        return cache.get(key, () -> fetch(key));
    }

    /**
     * Returns the value in the cache for the given key. If the key is not
     * present in the cache or hardRefresh is true, the data is fetched and
     * cached.
     *
     * @param key         The key.
     * @param hardRefresh Whether or not to re-fetch and replace the data in the
     *                    cache.
     *
     * @return The value for the key in the cache.
     *
     * @throws IllegalArgumentException
     * @throws ExecutionException
     */
    V getValue(K key, boolean hardRefresh) throws IllegalArgumentException, ExecutionException {
        validateCacheKey(key);
        if (hardRefresh) {
            cache.invalidate(key);
        }

        return cache.get(key, () -> fetch(key));
    }

    /**
     * Invalidates all cached entries.
     */
    void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * Invalidates all cached entries where this event may have affected the
     * data.
     *
     * @param eventData The event data.
     */
    void invalidate(E eventData) {
        if (!isCacheRelevantEvent(eventData)) {
            return;
        }

        cache.asMap().replaceAll((k,v) -> isInvalidatingEvent(k, eventData) ? null : v);
    }

    /**
     * Validates that the cache key meets invariants for fetching data or throws
     * an illegal argument exception. This method disallows null keys at this
     * time but can be overridden for specialized behavior.
     *
     * @param key The key.
     *
     * @throws IllegalArgumentException
     */
    protected void validateCacheKey(K key) throws IllegalArgumentException {
        if (key == null) {
            throw new IllegalArgumentException("Expected non-null key");
        }
    }

    /**
     * This method short cuts iterating over all keys to see if an event
     * invalidates a key if there is no way that the event will affect a key in
     * a cache. This method returns true by default but can be overridden for
     * specialized behavior.
     *
     * @param eventData The event data.
     *
     * @return True if this event could potentially impact the cache.
     */
    protected boolean isCacheRelevantEvent(E eventData) {
        // to be overridden
        return true;
    }

    /**
     * Fetches data from the database for the given search parameters key.
     *
     * @param key The key.
     *
     * @return The retrieved value.
     *
     * @throws Exception
     */
    protected abstract V fetch(K key) throws Exception;

    /**
     * Returns true if the event data would invalidate the data for the
     * specified key.
     *
     * @param key       The key.
     * @param eventData The event data.
     *
     * @return True if the event data invalidates the cached data for the key.
     */
    abstract boolean isInvalidatingEvent(K key, E eventData);
}
