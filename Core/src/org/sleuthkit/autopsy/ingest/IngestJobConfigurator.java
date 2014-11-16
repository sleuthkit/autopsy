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

import java.util.List;
import javax.swing.JPanel;
import org.sleuthkit.datamodel.Content;

/**
 * Provides a mechanism for creating and persisting an ingest job configuration
 * for a particular context and for launching ingest jobs that process one or
 * more data sources using the ingest job configuration.
 *
 * @deprecated Use the IngestModuleSettings and IngestJobConfigurationPanel
 * classes and IngestManager.startIngestJob() instead.
 */
@Deprecated
public final class IngestJobConfigurator {

    private final IngestJobSettings settings;
    private final IngestJobSettingsPanel settingsPanel;

    /**
     * Constructs an ingest job launcher that creates and persists ingest job
     * settings for a particular context and launches ingest jobs that
     * process one or more data sources using the settings.
     *
     * @param context The context identifier.
     */
    @Deprecated
    public IngestJobConfigurator(String context) {
        this.settings = new IngestJobSettings(context);
        this.settingsPanel = new IngestJobSettingsPanel(settings);
    }

    /**
     * Gets any warnings generated when the persisted ingest job settings
     * for the specified context are loaded or saved.
     *
     * @return A collection of warning messages, possibly empty.
     */
    @Deprecated
    public List<String> getIngestJobConfigWarnings() {
        return this.settings.getWarnings();
    }

    /**
     * Gets the user interface panel the launcher uses to obtain the user's
     * ingest job settings for the specified context.
     *
     * @return The panel.
     */
    @Deprecated
    public JPanel getIngestJobConfigPanel() {
        return settingsPanel;
    }

    /**
     * Persists the ingest job settings for the specified context.
     */
    @Deprecated
    public void saveIngestJobConfig() {
        this.settings.save();
    }

    /**
     * Launches ingest jobs for one or more data sources using the ingest job
     * settings for the specified context.
     *
     * @param dataSources The data sources to ingest.
     */
    @Deprecated
    public void startIngestJobs(List<Content> dataSources) {
        IngestManager ingestManager = IngestManager.getInstance();
        for (Content dataSource : dataSources) {
            ingestManager.startIngestJob(dataSource, this.settings, true);
        }
    }
}
