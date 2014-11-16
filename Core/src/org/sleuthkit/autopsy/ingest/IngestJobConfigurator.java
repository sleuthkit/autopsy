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

import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import org.sleuthkit.datamodel.Content;

/**
 * Provides a mechanism for creating and persisting an ingest job configuration
 * for a particular context and for launching ingest jobs that process one or
 * more data sources using the ingest job configuration.
 *
 * @deprecated Use the IngestModuleConfiguration and IngestJobConfigurationPanel
 * and IngestManager.startIngestJob() or IngestManager.startIngestJobNoUI() instead. // RJCTODO
 */
@Deprecated
public final class IngestJobConfigurator {

    private final IngestJobConfiguration config;
    private final IngestJobConfigurationPanel ingestConfigPanel;
    private IngestJobConfiguration.Messages messages;

    /**
     * Constructs an ingest job launcher that creates and persists an ingest job
     * configuration for a particular context and launches ingest jobs that
     * process one or more data sources using the ingest job configuration.
     *
     * @param context The context identifier.
     */
    @Deprecated
    public IngestJobConfigurator(String context) {
        this.config = new IngestJobConfiguration(context);
        this.ingestConfigPanel = new IngestJobConfigurationPanel(config);
    }

    /**
     * Gets any warnings generated when the persisted ingest job configuration
     * for the specified context is retrieved and loaded.
     *
     * @return A collection of warning messages.
     */
    @Deprecated
    public List<String> getIngestJobConfigWarnings() {
        List<String> warnings = new ArrayList<>();
        warnings.addAll(this.messages.getWarningMessages());
        warnings.addAll(this.messages.getErrorMessages());
        return warnings;
    }

    /**
     * Gets the user interface panel the launcher uses to obtain the user's
     * ingest job configuration for the specified context.
     *
     * @return A JPanel with components that can be used to create an ingest job
     * configuration.
     */
    @Deprecated
    public JPanel getIngestJobConfigPanel() {
        return ingestConfigPanel;
    }

    /**
     * Persists the ingest job configuration for the specified context.
     */
    @Deprecated
    public void saveIngestJobConfig() {
        this.messages = this.config.save();
    }

    /**
     * Launches ingest jobs for one or more data sources using the ingest job
     * configuration for the selected context.
     *
     * @param dataSources The data sources to ingest.
     */
    @Deprecated
    public void startIngestJobs(List<Content> dataSources) {
        // Filter out the disabled ingest module templates.
        List<IngestModuleTemplate> enabledModuleTemplates = new ArrayList<>();
        List<IngestModuleTemplate> moduleTemplates = this.config.getIngestModuleTemplates();
        for (IngestModuleTemplate moduleTemplate : moduleTemplates) {
            if (moduleTemplate.isEnabled()) {
                enabledModuleTemplates.add(moduleTemplate);
            }
        }

        if ((!enabledModuleTemplates.isEmpty()) && (dataSources != null) && (!dataSources.isEmpty())) {
            IngestManager.getInstance().startIngestJobs(dataSources, enabledModuleTemplates, this.config.shouldProcessUnallocatedSpace());
        }
    }
}
