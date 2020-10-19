/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.integrationtesting;

import java.util.List;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryService;

/**
 * Handles setting up the autopsy environment to be used with integration tests.
 */
public class IngestModuleSetupManager implements ConfigurationModule<IngestModuleSetupManager.ConfigArgs> {

    public static class ConfigArgs {
        private final List<String> modules;

        public ConfigArgs(List<String> modules) {
            this.modules = modules;
        }

        public List<String> getModules() {
            return modules;
        }
    }
    
    private static final Logger logger = Logger.getLogger(MainTestRunner.class.getName());
    private static final IngestModuleFactoryService ingestModuleFactories = new IngestModuleFactoryService();
    
    
    @Override
    public IngestJobSettings configure(IngestJobSettings curSettings, ConfigArgs parameters) {
        
        
        
    }
}
