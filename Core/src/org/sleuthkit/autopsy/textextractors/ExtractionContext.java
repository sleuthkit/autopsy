/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.textextractors;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.MutableClassToInstanceMap;

/**
 * Stores extraction config instances for media types.
 *
 * TextExtractors parse this class when configuring their own extraction
 * settings.
 */
public class ExtractionContext {

    ClassToInstanceMap<Object> extractionConfigs;

    public ExtractionContext() {
        extractionConfigs = MutableClassToInstanceMap.create();
    }

    /**
     * Internally stores a class-instance pair.
     *
     * @param <T>            Class type that will be stored.
     * @param configClass    The class object of the instance
     * @param configInstance Config instance of type T
     */
    public <T> void set(Class<T> configClass, T configInstance) {
        extractionConfigs.put(configClass, configInstance);
    }

    /**
     * Retrieves the config instance associated with this key.
     *
     * @param <T>         Type of the stored instance
     * @param configClass The class object of the instance
     *
     * @return The config instance of type T
     */
    public <T> T get(Class<T> configClass) {
        return configClass.cast(extractionConfigs.get(configClass));
    }

    /**
     * Indicates if this class key has been stored.
     *
     * @param <T>         Type of the stored instance
     * @param configClass The class object of the instance
     *
     * @return flag indicating the presense of this instance
     */
    public <T> boolean contains(Class<T> configClass) {
        return get(configClass) != null;
    }
}
