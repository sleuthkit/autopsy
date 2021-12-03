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
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import java.beans.PropertyChangeEvent;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeDisplayCount;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeCounts;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;

/**
 * Internal methods that DAOs implement.
 */
abstract class AbstractDAO {

    /**
     * Clear any cached data (Due to change in view).
     */
    abstract void clearCaches();

    /**
     * Handles an autopsy event (i.e. ingest, case, etc.). This method is
     * responsible for clearing internal caches that are effected by the event
     * and returning one or more DAOEvents that should be broadcasted to the
     * views.
     *
     * @param evt The Autopsy event that recently came in from Ingest/Case.
     *
     * @return The list of DAOEvents that should be broadcasted to the views or
     *         an empty list if the Autopsy events are irrelevant to this DAO.
     */
    abstract Set<? extends DAOEvent> processEvent(PropertyChangeEvent evt);

    /**
     * Handles the ingest complete or cancelled event. Any events that are
     * delayed or batched are flushed and returned.
     *
     * @return The flushed events that were delayed and batched.
     */
    abstract Set<? extends DAOEvent> handleIngestComplete();

    /**
     * Returns any categories that require a tree refresh. For instance, if web
     * cache and web bookmarks haven't updated recently, and are currently set
     * to an indeterminate amount (i.e. "..."), then broadcast an event forcing
     * tree to update to a determinate count.
     *
     * @return The categories that require a tree refresh.
     */
    abstract Set<? extends TreeEvent> shouldRefreshTree();

    /**
     * Using a digest of event information, clears keys in a cache that may be
     * effected by events.
     *
     * @param cache                 The cache.
     * @param getKeys               Using a key from a cache, provides a tuple
     *                              of the relevant key in the data source
     *                              mapping and the data source id (or null if
     *                              no data source filtering).
     * @param itemDataSourceMapping The event digest.
     */
    static <T, K> void invalidateKeys(Cache<SearchParams<K>, ?> cache, Function<K, Pair<T, Long>> getKeys, Map<T, Set<Long>> itemDsMapping) {
        invalidateKeys(cache, (keyParams) -> {
            Pair<T, Long> pairItems = getKeys.apply(keyParams);
            T searchParamsKey = pairItems.getLeft();
            Long searchParamsDsId = pairItems.getRight();
            Set<Long> dsIds = itemDsMapping.get(searchParamsKey);
            return (dsIds != null && (searchParamsDsId == null || dsIds.contains(searchParamsDsId)));
        });
    }

    /**
     * Determines what keys should be kept in the cache while iterating through
     * all the keys.
     *
     * @param cache            The cache.
     * @param shouldInvalidate If the key should be removed from the cache.
     */
    static <K> void invalidateKeys(Cache<SearchParams<K>, ?> cache, Predicate<K> shouldInvalidate) {
        ConcurrentMap<SearchParams<K>, ?> concurrentMap = cache.asMap();
        concurrentMap.forEach((k, v) -> {
            if (shouldInvalidate.test(k.getParamData())) {
                concurrentMap.remove(k);
            }
        });
    }

    /**
     * Returns a set of tree events gathered from the TreeCounts instance after
     * calling flushEvents.
     *
     * @param treeCounts The tree counts instance.
     * @param converter  The means of acquiring a tree item dto to be placed in
     *                   the TreeEvent.
     *
     * @return The generated tree events.
     */
    static <E, T> Set<TreeEvent> getIngestCompleteEvents(TreeCounts<E> treeCounts, BiFunction<E, TreeDisplayCount, TreeItemDTO<T>> converter) {
        return treeCounts.flushEvents().stream()
                .map(daoEvt -> new TreeEvent(converter.apply(daoEvt, TreeDisplayCount.UNSPECIFIED), true))
                .filter(evt -> evt != null)
                .collect(Collectors.toSet());
    }

    /**
     * Returns a set of tree events gathered from the TreeCounts instance after
     * calling getEventTimeouts.
     *
     * @param treeCounts The tree counts instance.
     * @param converter  The means of acquiring a tree item dto to be placed in
     *                   the TreeEvent.
     *
     * @return The generated tree events.
     */
    static <E, T> Set<TreeEvent> getRefreshEvents(TreeCounts<E> treeCounts, BiFunction<E, TreeDisplayCount, TreeItemDTO<T>> converter) {
        return treeCounts.getEventTimeouts().stream()
                .map(daoEvt -> new TreeEvent(converter.apply(daoEvt, TreeDisplayCount.UNSPECIFIED), true))
                .filter(evt -> evt != null)
                .collect(Collectors.toSet());
    }
}
