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
 * Combines an ingest module factory with ingest module settings and an enabled
 * flag to create a template for creating fully configured ingest modules.
 */
final class IngestModuleTemplate {

    private final IngestModuleFactory moduleFactory;
    private IngestModuleIngestJobSettings settings = null;
    private boolean enabled = true;

    IngestModuleTemplate(IngestModuleFactory moduleFactory, IngestModuleIngestJobSettings settings) {
        this.moduleFactory = moduleFactory;
        this.settings = settings;
    }

    IngestModuleFactory getModuleFactory() {
        return moduleFactory;
    }

    String getModuleName() {
        return moduleFactory.getModuleDisplayName();
    }

    String getModuleDescription() {
        return moduleFactory.getModuleDescription();
    }

    IngestModuleIngestJobSettings getModuleSettings() {
        return settings;
    }

    void setModuleSettings(IngestModuleIngestJobSettings settings) {
        this.settings = settings;
    }

    boolean hasModuleSettingsPanel() {
        return moduleFactory.hasIngestJobSettingsPanel();
    }

    IngestModuleIngestJobSettingsPanel getModuleSettingsPanel() {
        return moduleFactory.getIngestJobSettingsPanel(settings);
    }

    boolean hasGlobalSettingsPanel() {
        return moduleFactory.hasGlobalSettingsPanel();
    }

    IngestModuleGlobalSettingsPanel getGlobalSettingsPanel() {
        return moduleFactory.getGlobalSettingsPanel();
    }

    boolean isDataSourceIngestModuleTemplate() {
        return moduleFactory.isDataSourceIngestModuleFactory();
    }

    DataSourceIngestModule createDataSourceIngestModule() {
        return moduleFactory.createDataSourceIngestModule(settings);
    }

    boolean isFileIngestModuleTemplate() {
        return moduleFactory.isFileIngestModuleFactory();
    }

    FileIngestModule createFileIngestModule() {
        return moduleFactory.createFileIngestModule(settings);
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    boolean isEnabled() {
        return enabled;
    }
}
