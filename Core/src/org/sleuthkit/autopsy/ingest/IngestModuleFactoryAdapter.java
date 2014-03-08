/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

/**
 * An adapter that provides no-op implementations of various IngestModuleFactory
 * methods.
 */
public abstract class IngestModuleFactoryAdapter implements IngestModuleFactory {

    @Override
    public abstract String getModuleDisplayName();

    @Override
    public abstract String getModuleDescription();

    @Override
    public abstract String getModuleVersionNumber();

    @Override
    public IngestModuleOptions getDefaultIngestOptions() {
        return new NoIngestOptions();
    }
    
    @Override
    public boolean providesIngestOptionsPanels() {
        return false;
    }
    
    @Override
    public IngestModuleOptionsPanel getIngestOptionsPanel(IngestModuleOptions ingestOptions) throws InvalidOptionsException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean providesResourcesConfigPanels() {
        return false;
    }

    @Override
    public IngestModuleResourcesConfigPanel getResourcesConfigPanel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDataSourceIngestModuleFactory() {
        return false;
    }

    @Override
    public DataSourceIngestModule createDataSourceIngestModule(IngestModuleOptions ingestOptions) throws InvalidOptionsException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isFileIngestModuleFactory() {
        return false;
    }

    @Override
    public FileIngestModule createFileIngestModule(IngestModuleOptions ingestOptions) throws InvalidOptionsException {
        throw new UnsupportedOperationException();
    }
}
