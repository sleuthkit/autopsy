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
package org.sleuthkit.autopsy.experimental.objectdetection;

import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * A factory that creates ingest modules which uses classifiers to detect
 * objects in pictures.
 */
@ServiceProvider(service = IngestModuleFactory.class)
public class ObjectDetectionModuleFactory extends IngestModuleFactoryAdapter {


    /**
     * Get the name of the Object Detection module
     * 
     * @return the name of the Object Detection module
     */
    static String getModuleName() {
         return Bundle.ObjectDetectionModuleFactory_moduleName_text();
    }
    
    @Messages({"ObjectDetectionModuleFactory.moduleName.text=Object Detection"})
    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }
    
    @Messages({"ObjectDetectionModuleFactory.moduleDescription.text=Use object classifiers to identify objects in pictures."})
    @Override
    public String getModuleDescription() {
        return Bundle.ObjectDetectionModuleFactory_moduleDescription_text();
    }

    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();
    }

    @Override
    public boolean isFileIngestModuleFactory() {
        return true;
    }

    @Override
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings settings) {
        return new ObjectDetectectionFileIngestModule();
    }

}
