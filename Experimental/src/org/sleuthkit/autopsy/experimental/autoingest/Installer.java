/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.util.Collection;
import org.netbeans.spi.sendopts.ArgsProcessor;
import org.openide.modules.ModuleInstall;
import org.openide.util.Lookup;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.core.ArgumentsProcessor;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * The module's lifecycle is managed in this class. The auto ingest service task
 * will start if the "--autoingestservice" argument was supplied.
 */
public class Installer extends ModuleInstall {
    
    private static final Logger LOGGER = Logger.getLogger(Installer.class.getName());
    
    private Installer() {
        super();
    }
    
    @Override
    public void restored() {
        super.restored();
        
        WindowManager.getDefault().invokeWhenUIReady(() -> {
            // Get the ArgumentsProcessor object.
            Collection<? extends ArgsProcessor> argsProcessorsList = Lookup.getDefault().lookupAll(ArgsProcessor.class);
            ArgumentsProcessor argsProcessor = null;
            for(ArgsProcessor processor : argsProcessorsList) {
                if(processor instanceof ArgumentsProcessor) {
                    argsProcessor = (ArgumentsProcessor) processor;
                    break;
                }
            }
            
            if(argsProcessor != null && ArgumentsProcessor.isAutoIngestService()) {
                // Running as a service.
                AutoIngestServiceTask autoIngestService = new AutoIngestServiceTask();
                autoIngestService.run();
            }
        });
    }
}