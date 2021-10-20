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

import java.util.concurrent.ExecutionException;

/**
 * The public API for a cache of key value pairs where an event has the
 * potential to invalidate particular cache entries.
 *
 * @param <K> The key type.
 * @param <V> The value type.
 * @param <E> The event type.
 */
public interface EventUpdatableCache<K, V, E> {

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
    V getValue(K key) throws IllegalArgumentException, ExecutionException;

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
    V getValue(K key, boolean hardRefresh) throws IllegalArgumentException, ExecutionException;

    /**
     * Returns true if the event data would invalidate the data for the
     * specified key.
     *
     * @param key       The key.
     * @param eventData The event data.
     *
     * @return True if the event data invalidates the cached data for the key.
     */
    boolean isInvalidatingEvent(K key, E eventData);

}
