/*
 *
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
package org.sleuthkit.autopsy.commonfilesearch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Defines a value that was in the common file search results as well as
 * information about its instances.
 */
final public class CommonAttributeValue {

    private final String value;
    private final List<AbstractCommonAttributeInstance> fileInstances;

    CommonAttributeValue(String value) {
        this.value = value;
        this.fileInstances = new ArrayList<>();
    }

    public String getValue() {
        return this.value;
    }

    /**
     * concatenate cases this value was seen into a single string
     *
     * @return
     */
    public String getCases() {
        return this.fileInstances.stream().map(AbstractCommonAttributeInstance::getCaseName).collect(Collectors.joining(", "));
    }

    public String getDataSources() {
        Set<String> sources = new HashSet<>();
        for (AbstractCommonAttributeInstance data : this.fileInstances) {
            sources.add(data.getDataSource());
        }

        return String.join(", ", sources);
    }

    void addInstance(AbstractCommonAttributeInstance metadata) {
        this.fileInstances.add(metadata);
    }

    public Collection<AbstractCommonAttributeInstance> getInstances() {
        return Collections.unmodifiableCollection(this.fileInstances);
    }

    /**
     * How many distinct file instances exist for the MD5 represented by this
     * object?
     *
     * @return number of instances
     */
    public int getInstanceCount() {
        return this.fileInstances.size();
    }
}
