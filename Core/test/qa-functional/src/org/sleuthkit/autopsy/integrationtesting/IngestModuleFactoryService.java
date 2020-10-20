/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.integrationtesting;

import java.util.ArrayList;
import java.util.List;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;

/**
 *
 * @author gregd
 */
public class IngestModuleFactoryService {
    public List<IngestModuleFactory> getFactories() {
        return new ArrayList<>(Lookup.getDefault().lookupAll(IngestModuleFactory.class));
    }
}
