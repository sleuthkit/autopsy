/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.textclassifier;

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
public class TextClassifierModuleFactory extends IngestModuleFactoryAdapter {

    /**
     * Get the name of the Object Detection module
     *
     * @return the name of the Object Detection module
     */
    static String getModuleName() {
        return Bundle.TextClassifierModuleFactory_moduleName_text();
    }

    @Messages({"TextClassifierModuleFactory.moduleName.text=Text Classifier"})
    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    @Messages({"TextClassifierModuleFactory.moduleDescription.text=Use text classifiers to label potentially interesting files."})
    @Override
    public String getModuleDescription() {
        return Bundle.TextClassifierModuleFactory_moduleDescription_text();
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
        return new TextClassifierFileIngestModule();
    }

}
