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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;

/**
 * Handles setting up the autopsy environment to be used with integration tests.
 */
public class IngestModuleSetupManager implements ConfigurationModule<IngestModuleSetupManager.ConfigArgs> {

    /**
     * The parameters used when calling 'configure' in this class.
     */
    public static class ConfigArgs {

        private final List<String> modules;

        /**
         * Main constructor.
         *
         * @param modules The ingest module factories to be loaded.
         */
        public ConfigArgs(List<String> modules) {
            this.modules = modules;
        }

        /**
         * @return The ingest module factories to be loaded.
         */
        public List<String> getModules() {
            return modules;
        }
    }

    private static final IngestModuleFactoryService ingestModuleFactories = new IngestModuleFactoryService();

    @Override
    public IngestJobSettings configure(IngestJobSettings curSettings, IngestModuleSetupManager.ConfigArgs parameters) {

        // get the profile from the IngestJobSettings
        String context = curSettings.getExecutionContext();
        if (StringUtils.isNotBlank(context) && context.indexOf('.') > 0) {
            context = context.substring(0, context.indexOf('.'));
        }

        // get current templates in job settings
        Map<String, IngestModuleTemplate> curTemplates = curSettings.getEnabledIngestModuleTemplates().stream()
                .collect(Collectors.toMap(t -> t.getModuleFactory().getClass().getCanonicalName(), t -> t, (t1, t2) -> t1));

        // get all the factories determined by canonical name
        Map<String, IngestModuleFactory> allFactories = ingestModuleFactories.getFactories().stream()
                .collect(Collectors.toMap(f -> f.getClass().getCanonicalName(), f -> f, (f1, f2) -> f1));

        // add current templates to the list of templates to return.
        List<IngestModuleTemplate> newTemplates = new ArrayList<>(curTemplates.values());

        // if there are parameters, add any relevant ingest module factories
        if (parameters != null && !CollectionUtils.isEmpty(parameters.getModules())) {
            List<IngestModuleTemplate> templatesToAdd = parameters.getModules().stream()
                    // ensure only one of each type of factory is added
                    .distinct()
                    // if the factory to be added is already contained in curTemplates or allFactories does not contain item, null is returned
                    .map((className) -> curTemplates.containsKey(className) ? null : allFactories.get(className))
                    // filter out any null items
                    .filter((factory) -> factory != null)
                    // create a template for any remaining items
                    .map((factory) -> new IngestModuleTemplate(factory, factory.getDefaultIngestJobSettings()))
                    .collect(Collectors.toList());

            newTemplates.addAll(templatesToAdd);
        }

        return new IngestJobSettings(
                context,
                IngestJobSettings.IngestType.ALL_MODULES,
                newTemplates
        );
    }
}
