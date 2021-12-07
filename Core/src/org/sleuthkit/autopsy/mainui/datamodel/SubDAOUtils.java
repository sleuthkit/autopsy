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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeCounts;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;

/**
 * Utilities for common actions in the sub DAOs.
 */
public class SubDAOUtils {

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
    static <T, K> void invalidateKeys(Cache<SearchParams<K>, ?> cache, Function<K, Pair<T, Long>> getKeys, Map<T, Set<Long>> itemDataSourceMapping) {
        invalidateKeys(cache, getKeys, Collections.singletonList(itemDataSourceMapping));
    }

    /**
     * Using a digest of event information, clears keys in a cache that may be
     * effected by events.
     *
     * @param cache                 The cache.
     * @param getKeys               Using a key from a cache, provides a tuple
     *                              of the relevant key in the data source
     *                              mapping and the data source id (or null if
     *                              no data source filtering).
     * @param itemDataSourceMapping The list of event digests.
     */
    static <T, K> void invalidateKeys(Cache<SearchParams<K>, ?> cache, Function<K, Pair<T, Long>> getKeys, List<Map<T, Set<Long>>> itemDataSourceMapping) {
        ConcurrentMap<SearchParams<K>, ?> concurrentMap = cache.asMap();
        concurrentMap.forEach((k, v) -> {
            Pair<T, Long> pairItems = getKeys.apply(k.getParamData());
            T searchParamsKey = pairItems.getLeft();
            Long searchParamsDsId = pairItems.getRight();
            for (Map<T, Set<Long>> itemDsMapping : itemDataSourceMapping) {
                Set<Long> dsIds = itemDsMapping.get(searchParamsKey);
                if (dsIds != null && (searchParamsDsId == null || dsIds.contains(searchParamsDsId))) {
                    concurrentMap.remove(k);
                }
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
    static <E, T> Set<TreeEvent> getIngestCompleteEvents(TreeCounts<E> treeCounts, Function<E, TreeResultsDTO.TreeItemDTO<T>> converter) {
        return treeCounts.flushEvents().stream()
                .map(daoEvt -> new TreeEvent(converter.apply(daoEvt), true))
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
    static <E, T> Set<TreeEvent> getRefreshEvents(TreeCounts<E> treeCounts, Function<E, TreeResultsDTO.TreeItemDTO<T>> converter) {
        return treeCounts.getEventTimeouts().stream()
                .map(daoEvt -> new TreeEvent(converter.apply(daoEvt), true))
                .collect(Collectors.toSet());
    }
}
