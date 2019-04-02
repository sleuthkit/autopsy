/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourceprocessors;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException;

/**
 * A utility class to find Data Source Processors
 */
public class DataSourceProcessorUtility {

    private DataSourceProcessorUtility() {
    }

    /**
     * A utility method to find all Data Source Processors (DSP) that are able
     * to process the input data source. Only the DSPs that implement
     * AutoIngestDataSourceProcessor interface are used.
     *
     * @param dataSourcePath Full path to the data source
     * @param processorCandidates Possible DSPs that can handle the data source
     * 
     * @return Hash map of all DSPs that can process the data source along with
     * their confidence score
     * @throws
     * org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException
     */
    public static Map<AutoIngestDataSourceProcessor, Integer> getDataSourceProcessorForFile(Path dataSourcePath, Collection<? extends AutoIngestDataSourceProcessor> processorCandidates) throws AutoIngestDataSourceProcessorException {
        Map<AutoIngestDataSourceProcessor, Integer> validDataSourceProcessorsMap = new HashMap<>();
        for (AutoIngestDataSourceProcessor processor : processorCandidates) {
            int confidence = processor.canProcess(dataSourcePath);
            if (confidence > 0) {
                validDataSourceProcessorsMap.put(processor, confidence);
            }
        }

        return validDataSourceProcessorsMap;
    }
    
    /**
     * A utility method to find all Data Source Processors (DSP) that are able
     * to process the input data source. Only the DSPs that implement
     * AutoIngestDataSourceProcessor interface are used. Returns ordered list of
     * data source processors. DSPs are ordered in descending order from highest
     * confidence to lowest.
     *
     * @param dataSourcePath Full path to the data source
     *
     * @return Ordered list of data source processors. DSPs are ordered in
     *         descending order from highest confidence to lowest.
     *
     * @throws
     * org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException
     */
    public static List<AutoIngestDataSourceProcessor> getOrderedListOfDataSourceProcessors(Path dataSourcePath) throws AutoIngestDataSourceProcessorException {
        // lookup all AutomatedIngestDataSourceProcessors 
        Collection<? extends AutoIngestDataSourceProcessor> processorCandidates = Lookup.getDefault().lookupAll(AutoIngestDataSourceProcessor.class);
        return getOrderedListOfDataSourceProcessors(dataSourcePath, processorCandidates);
    }
    
    /**
     * A utility method to find all Data Source Processors (DSP) that are able
     * to process the input data source. Only the DSPs that implement
     * AutoIngestDataSourceProcessor interface are used. Returns ordered list of
     * data source processors. DSPs are ordered in descending order from highest
     * confidence to lowest.
     *
     * @param dataSourcePath      Full path to the data source
     * @param processorCandidates Collection of AutoIngestDataSourceProcessor objects to use
     *
     * @return Ordered list of data source processors. DSPs are ordered in
     *         descending order from highest confidence to lowest.
     *
     * @throws
     * org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException
     */
    public static List<AutoIngestDataSourceProcessor> getOrderedListOfDataSourceProcessors(Path dataSourcePath, Collection<? extends AutoIngestDataSourceProcessor> processorCandidates) throws AutoIngestDataSourceProcessorException {
        Map<AutoIngestDataSourceProcessor, Integer> validDataSourceProcessorsMap = getDataSourceProcessorForFile(dataSourcePath, processorCandidates);
        return orderDataSourceProcessorsByConfidence(validDataSourceProcessorsMap);
    }   


    /**
     * A utility method to get an ordered list of data source processors. DSPs
     * are ordered in descending order from highest confidence to lowest.
     *
     * @param validDataSourceProcessorsMap Hash map of all DSPs that can process
     * the data source along with their confidence score
     * @return Ordered list of data source processors
     */
    public static List<AutoIngestDataSourceProcessor> orderDataSourceProcessorsByConfidence(Map<AutoIngestDataSourceProcessor, Integer> validDataSourceProcessorsMap) {
        List<AutoIngestDataSourceProcessor> validDataSourceProcessors = validDataSourceProcessorsMap.entrySet().stream()
                .sorted(Map.Entry.<AutoIngestDataSourceProcessor, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return validDataSourceProcessors;
    }
}
