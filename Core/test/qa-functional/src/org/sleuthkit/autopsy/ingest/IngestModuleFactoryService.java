/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.ingest;

import java.util.List;

/**
 *
 * @author gregd
 */
public class IngestModuleFactoryService {
    public List<IngestModuleFactory> getFactories() {
        return IngestModuleFactoryLoader.getIngestModuleFactories();
    }
}
