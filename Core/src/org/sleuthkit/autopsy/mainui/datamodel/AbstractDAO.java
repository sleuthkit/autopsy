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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Pair;
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
    abstract Set<DAOEvent> processEvent(PropertyChangeEvent evt);

    /**
     * Handles the ingest complete or cancelled event. Any events that are
     * delayed or batched are flushed and returned.
     *
     * @return The flushed events that were delayed and batched.
     */
    abstract Set<DAOEvent> handleIngestComplete();

    /**
     * Returns any categories that require a tree refresh. For instance, if web
     * cache and web bookmarks haven't updated recently, and are currently set
     * to an indeterminate amount (i.e. "..."), then broadcast an event forcing
     * tree to update to a determinate count.
     *
     * @return The categories that require a tree refresh.
     */
    abstract Set<TreeEvent> shouldRefreshTree();

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
    <T, K> void invalidateKeys(Cache<SearchParams<K>, ?> cache, Function<K, Pair<T, Long>> getKeys, Map<T, Set<Long>> itemDataSourceMapping) {
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
    <T, K> void invalidateKeys(Cache<SearchParams<K>, ?> cache, Function<K, Pair<T, Long>> getKeys, List<Map<T, Set<Long>>> itemDataSourceMapping) {
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

}
