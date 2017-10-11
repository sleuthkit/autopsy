/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourceprocessors;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException;

/**
 * A utility class to find Data Source Processors
 */
public class IdentifyDataSourceProcessors {

    /**
     * A utility method to find all Data Source Processors (DSP) that are able
     * to process the input data source. Only the DSPs that implement
     * AutoIngestDataSourceProcessor interface are used.
     *
     * @param dataSourcePath Full path to the data source
     * @return Hash map of all DSPs that can process the data source along with
     * their confidence score
     * @throws
     * org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException
     */
    public static Map<AutoIngestDataSourceProcessor, Integer> getDataSourceProcessor(Path dataSourcePath) throws AutoIngestDataSourceProcessorException {

        // lookup all AutomatedIngestDataSourceProcessors 
        Collection<? extends AutoIngestDataSourceProcessor> processorCandidates = Lookup.getDefault().lookupAll(AutoIngestDataSourceProcessor.class);

        Map<AutoIngestDataSourceProcessor, Integer> validDataSourceProcessorsMap = new HashMap<>();
        for (AutoIngestDataSourceProcessor processor : processorCandidates) {
            int confidence = processor.canProcess(dataSourcePath);
            if (confidence > 0) {
                validDataSourceProcessorsMap.put(processor, confidence);
            }
        }

        return validDataSourceProcessorsMap;
    }
}
