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
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sleuthkit.autopsy.coreutils.Logger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitResult;
import reactor.util.concurrent.Queues;

/**
 * A partial implementation of a cache of key value pairs where an event has the
 * potential to invalidate particular cache entries.
 *
 * @param <K> The key type.
 * @param <V> The value type.
 * @param <E> The event type.
 */
abstract class EventUpdatableCacheImpl<K, V, E> implements EventUpdatableCache<K, V, E> {

    private static final Logger logger = Logger.getLogger(EventUpdatableCacheImpl.class.getName());

    private final Cache<K, V> cache = CacheBuilder.newBuilder().maximumSize(1000).build();

    // taken from https://stackoverflow.com/questions/66671636/why-is-sinks-many-multicast-onbackpressurebuffer-completing-after-one-of-t
    private final Sinks.Many<Set<K>> invalidatedKeyMulticast = Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

    @Override
    public V getValue(K key) throws IllegalArgumentException, ExecutionException {
        return cache.get(key, () -> fetch(key));
    }

    @Override
    public V getValue(K key, boolean hardRefresh) throws IllegalArgumentException, ExecutionException {
        validateCacheKey(key);
        if (hardRefresh) {
            // GVDTODO handle as transaction
            V value;
            try {
                value = fetch(key);
            } catch (Exception ex) {
                throw new ExecutionException("Unable to fetch key: " + key, ex);
            }
            cache.put(key, value);
            return value;
        } else {
            return cache.get(key, () -> fetch(key));
        }
    }

    private V getValueLoggedError(K key) {
        try {
            return getValue(key);
        } catch (IllegalArgumentException | ExecutionException ex) {
            logger.log(Level.WARNING, "An error occurred while fetching results for key: " + key, ex);
            return null;
        }
    }

    public Flux<V> getInitialAndUpdates(K key) throws IllegalArgumentException {
        validateCacheKey(key);

        // GVDTODO handle in one transaction
        Flux<V> initial = Flux.fromStream(Stream.of(getValueLoggedError(key)));

        Flux<V> updates = this.invalidatedKeyMulticast.asFlux()
                .filter(invalidatedKeys -> invalidatedKeys.contains(key))
                .map((matchingInvalidatedKey) -> getValueLoggedError(key));

        return Flux.concat(initial, updates)
                .filter((data) -> data != null);
    }

    /**
     * Invalidates all cached entries.
     */
    void invalidateAll() {
        Set<K> keys = new HashSet<>(cache.asMap().keySet());
        invalidateAndBroadcast(keys);
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

        Set<K> keys = cache.asMap().keySet().stream()
                .filter((key) -> isInvalidatingEvent(key, eventData))
                .collect(Collectors.toSet());
        invalidateAndBroadcast(keys);
    }

    private void invalidateAndBroadcast(Set<K> keys) {
        if (keys.isEmpty()) {
            return;
        }

        cache.invalidateAll(keys);
        EmitResult emitResult = invalidatedKeyMulticast.tryEmitNext(keys);
        if (emitResult.isFailure()) {
            logger.log(Level.WARNING, MessageFormat.format("There was an error broadcasting invalidated keys: {0}", emitResult.name()));
        }

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

}
