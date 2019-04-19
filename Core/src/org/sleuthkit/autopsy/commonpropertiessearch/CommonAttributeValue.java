/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonpropertiessearch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Defines a value that was in the common file search results as well as
 * information about its instances.
 */
final public class CommonAttributeValue {

    private final String value;
    private final List<AbstractCommonAttributeInstance> fileInstances;
    private final Map<String, Integer> fileNames = new HashMap<>();

    CommonAttributeValue(String value) {
        this.value = value;
        this.fileInstances = new ArrayList<>();
    }

    public String getValue() {
        return this.value;
    }

    /**
     * Get the file name of the first available instance of this value.
     *
     * @return the file name of an instance of this file
     */
    String getTokenFileName() {
        String tokenFileName = null;
        int maxValue = 0;
        for (String key : fileNames.keySet()){
            if (fileNames.get(key) > maxValue){
                maxValue = fileNames.get(key);
                tokenFileName = key;
            }
        }
        return tokenFileName;
    }

    /**
     * concatenate cases this value was seen into a single string
     *
     * @return
     */
    public String getCases() {
        return this.fileInstances.stream().map(AbstractCommonAttributeInstance::getCaseName).collect(Collectors.joining(", "));
    }

    /**
     * Get the set of data sources names this value exists in
     *
     * @return a set of data source names
     */
    public Set<String> getDataSources() {
        Set<String> sources = new HashSet<>();
        for (AbstractCommonAttributeInstance data : this.fileInstances) {
            sources.add(data.getDataSource());
        }
        return sources;
    }

    /**
     * Get the number of unique data sources in the current case which the value
     * appeared in.
     *
     * @return the number of unique data sources in the current case which
     *         contained the value
     */
    int getNumberOfDataSourcesInCurrentCase() {
        Set<Long> dataSourceIds = new HashSet<>();
        for (AbstractCommonAttributeInstance data : this.fileInstances) {
            AbstractFile file = data.getAbstractFile();
            if (file != null) {
                dataSourceIds.add(file.getDataSourceObjectId());
            }
        }
        return dataSourceIds.size();
    }

    void addInstance(AbstractCommonAttributeInstance metadata) {
        if (metadata.getAbstractFile() != null) {
            Integer currentValue = fileNames.get(metadata.getAbstractFile().getName());
            currentValue = currentValue == null ? 1 : currentValue+1;
            fileNames.put(metadata.getAbstractFile().getName(), currentValue);
        }
        this.fileInstances.add(metadata);
    }

    public Collection<AbstractCommonAttributeInstance> getInstances() {
        return Collections.unmodifiableCollection(this.fileInstances);
    }

    /**
     * How many distinct file instances exist for the value represented by this
     * object?
     *
     * @return number of instances
     */
    public int getInstanceCount() {
        return this.fileInstances.size();
    }
}
