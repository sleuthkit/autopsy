/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2018 Basis Technology Corp.
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
 * Combines an ingest module factory with ingest module settings and an enabled
 * flag to create a template for creating fully configured ingest modules.
 */
public final class IngestModuleTemplate {

    private final IngestModuleFactory moduleFactory;
    private IngestModuleIngestJobSettings settings = null;
    private boolean enabled = true;

    public IngestModuleTemplate(IngestModuleFactory moduleFactory, IngestModuleIngestJobSettings settings) {
        this.moduleFactory = moduleFactory;
        this.settings = settings;
    }

    public IngestModuleFactory getModuleFactory() {
        return moduleFactory;
    }

    public String getModuleName() {
        return moduleFactory.getModuleDisplayName();
    }

    public String getModuleDescription() {
        return moduleFactory.getModuleDescription();
    }

    public IngestModuleIngestJobSettings getModuleSettings() {
        return settings;
    }

    public void setModuleSettings(IngestModuleIngestJobSettings settings) {
        this.settings = settings;
    }

    public boolean hasModuleSettingsPanel() {
        return moduleFactory.hasIngestJobSettingsPanel();
    }

    public IngestModuleIngestJobSettingsPanel getModuleSettingsPanel() {
        return moduleFactory.getIngestJobSettingsPanel(settings);
    }

    public boolean hasGlobalSettingsPanel() {
        return moduleFactory.hasGlobalSettingsPanel();
    }

    public IngestModuleGlobalSettingsPanel getGlobalSettingsPanel() {
        return moduleFactory.getGlobalSettingsPanel();
    }

    public boolean isDataSourceIngestModuleTemplate() {
        return moduleFactory.isDataSourceIngestModuleFactory();
    }

    public DataSourceIngestModule createDataSourceIngestModule() {
        return moduleFactory.createDataSourceIngestModule(settings);
    }

    public boolean isFileIngestModuleTemplate() {
        return moduleFactory.isFileIngestModuleFactory();
    }

    public FileIngestModule createFileIngestModule() {
        return moduleFactory.createFileIngestModule(settings);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
    
}
