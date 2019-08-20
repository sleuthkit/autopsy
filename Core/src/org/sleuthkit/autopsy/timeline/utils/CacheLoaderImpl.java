/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.utils;

import com.google.common.cache.CacheLoader;

/**
 * Extension of CacheLoader that delegates the load method to the Function
 * passed to the constructor.
 *
 * @param <K> Key type.
 * @param <V> Value type.
 */
final public class CacheLoaderImpl<K, V> extends CacheLoader<K, V> {

    private final CheckedFunction<K, V, Exception> func;

    public CacheLoaderImpl(CheckedFunction<K, V, Exception> func) {
        this.func = func;
    }

    @Override
    public V load(K key) throws Exception {
        return func.apply(key);
    }

}
