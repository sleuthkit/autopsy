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

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import org.openide.util.Pair;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.autopsy.integrationtesting.config.ConfigDeserializer;
import org.sleuthkit.autopsy.integrationtesting.config.ParameterizedResourceConfig;

/**
 * In charge of running configuration modules to set up the environment and then
 * running revert when the test completes.
 */
public class ConfigurationModuleManager {

    private static final IngestModuleFactoryService ingestModuleFactories = new IngestModuleFactoryService();
    private static final Logger logger = Logger.getLogger(ConfigurationModuleManager.class.getName());

    private static final IngestJobSettings.IngestType DEFAULT_INGEST_FILTER_TYPE = IngestJobSettings.IngestType.ALL_MODULES;
    private static final Set<String> DEFAULT_EXCLUDED_MODULES = Stream.of("Plaso").collect(Collectors.toSet());
    private static final ConfigDeserializer configDeserializer = new ConfigDeserializer();

    /**
     * Reverts the effects of the given configuration module objects.
     *
     * @param configModules The configuration modules.
     */
    void revertConfigurationModules(List<ConfigurationModule<?>> configModules) {
        List<ConfigurationModule<?>> reversed = new ArrayList<>(configModules);
        Collections.reverse(reversed);
        for (ConfigurationModule<?> configModule : reversed) {
            try {
                configModule.revert();
            } catch (Exception ex) {
                // firewall exception handler to ensure reverting a configuration module doesn't cause an error.
                logger.log(Level.SEVERE, "An error occurred while reverting configuration module: " + configModule.getClass().getCanonicalName(), ex);
            }
        }
    }

    /**
     * Returns a profile name to be used with IngestJobSettings for a given
     * caseName.
     *
     * @param caseName The case name.
     * @return The name of the profile.
     */
    static String getProfileName(String caseName) {
        return String.format("integrationTestProfile-%s", caseName);
    }

    /**
     * Returns a default IngestJobSettings object in the event that no
     * configuration modules are specified.
     *
     * @param caseName The name of the case used for the profile name of the
     * settings.
     * @return The default ingest job settings.
     */
    private IngestJobSettings getDefaultIngestConfig(String caseName) {
        return new IngestJobSettings(
                getProfileName(caseName),
                DEFAULT_INGEST_FILTER_TYPE,
                ingestModuleFactories.getFactories().stream()
                        .filter((f) -> !DEFAULT_EXCLUDED_MODULES.contains(f.getModuleDisplayName()))
                        .map(f -> new IngestModuleTemplate(f, f.getDefaultIngestJobSettings()))
                        .collect(Collectors.toList())
        );
    }

    /**
     * Runs configuration modules in the TestSuiteConfig.
     *
     * @param caseName The name of the case.
     * @param configModules The configuration modules to be run specified by the
     * resource and their accompanying parameters.
     * @return A tuple of the generated IngestJobSettings and a list of the
     * generated configuration modules to later be used for reverting any
     * environmental changes.
     */
    Pair<IngestJobSettings, List<ConfigurationModule<?>>> runConfigurationModules(String caseName, List<ParameterizedResourceConfig> configModules) {
        // if no config modules, return default ingest settings
        if (CollectionUtils.isEmpty(configModules)) {
            return Pair.of(getDefaultIngestConfig(caseName), Collections.emptyList());
        }

        // create a base ingest job settings object with no templates.
        IngestJobSettings curConfig = new IngestJobSettings(
                getProfileName(caseName),
                DEFAULT_INGEST_FILTER_TYPE,
                Collections.emptyList());

        List<ConfigurationModule<?>> configurationModuleCache = new ArrayList<>();

        // run through the configuration for each configuration module 
        for (ParameterizedResourceConfig configModule : configModules) {
            Pair<IngestJobSettings, ConfigurationModule<?>> ingestResult = runConfigurationModule(curConfig, configModule);
            // if there are results, update to the new ingest job settings and cache the config module.
            if (ingestResult != null) {
                curConfig = ingestResult.first() == null ? curConfig : ingestResult.first();
                if (ingestResult.second() != null) {
                    configurationModuleCache.add(ingestResult.second());
                }
            }
        }
        return Pair.of(curConfig, configurationModuleCache);
    }

    /**
     * Run a configuration module as specified by the paramerized resource
     * config returning the acquired configuration module and the updated ingest
     * job settings.
     *
     * @param curConfig The current ingest job settings.
     * @param configModule The resource identifying the config module and any
     * accompanying parameters.
     * @return A tuple containing the ingest job settings and the instantiated
     * configuration module that generated changes (used later for reverting).
     */
    private Pair<IngestJobSettings, ConfigurationModule<?>> runConfigurationModule(IngestJobSettings curConfig, ParameterizedResourceConfig configModule) {
        // acquire class described by resource
        Class<?> clazz = null;
        try {
            clazz = Class.forName(configModule.getResource());
        } catch (ClassNotFoundException ex) {
            logger.log(Level.WARNING, "Unable to find module: " + configModule.getResource(), ex);
            return null;
        }

        // assure that the class is a configuration module.
        if (clazz == null || !ConfigurationModule.class.isAssignableFrom(clazz)) {
            logger.log(Level.WARNING, String.format("%s does not seem to be an instance of a configuration module.", configModule.getResource()));
            return null;
        }

        // determine generic parameter type
        Type configurationModuleType = Stream.of(clazz.getGenericInterfaces())
                .filter(type -> type instanceof ParameterizedType && ((ParameterizedType) type).getRawType().equals(ConfigurationModule.class))
                .map(type -> ((ParameterizedType) type).getActualTypeArguments()[0])
                .findFirst()
                .orElse(null);

        if (configurationModuleType == null) {
            logger.log(Level.SEVERE, String.format("Could not determine generic type of config module: %s", configModule.getResource()));
            return null;
        }

        // instantiate the object from the class and run the configure method.
        ConfigurationModule<?> configModuleObj = null;
        Object result = null;
        try {
            configModuleObj = (ConfigurationModule<?>) clazz.newInstance();
            Method m = clazz.getMethod("configure", IngestJobSettings.class, (Class<?>) configurationModuleType);
            result = m.invoke(configModuleObj, curConfig, configDeserializer.convertToObj(configModule.getParameters(), configurationModuleType));
        } catch (Exception ex) {
            // firewall exception handler.
            logger.log(Level.SEVERE, String.format("There was an error calling configure method on Configuration Module %s", configModule.getResource()), ex);
            return null;
        }

        // return results or an error if no results returned.
        if (result instanceof IngestJobSettings) {
            return Pair.of((IngestJobSettings) result, configModuleObj);
        } else {
            logger.log(Level.SEVERE, String.format("Could not retrieve IngestJobSettings or null was returned from %s", configModule.getResource()));
            return null;
        }
    }
}
