/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.integrationtesting;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.cxf.common.util.CollectionUtils;
import org.openide.util.Pair;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.autopsy.integrationtesting.config.ConfigDeserializer;
import org.sleuthkit.autopsy.integrationtesting.config.ParameterizedResourceConfig;
import org.sleuthkit.autopsy.integrationtesting.config.TestSuiteConfig;

/**
 *
 * @author gregd
 */
public class ConfigurationModuleManager {
    private static final IngestModuleFactoryService ingestModuleFactories = new IngestModuleFactoryService();
    private static final Logger logger = Logger.getLogger(ConfigurationModuleManager.class.getName());
    
    private static final IngestJobSettings.IngestType DEFAULT_INGEST_FILTER_TYPE = IngestJobSettings.IngestType.ALL_MODULES;
    private static final Set<String> DEFAULT_EXCLUDED_MODULES = Stream.of("Plaso").collect(Collectors.toSet());
    private static final ConfigDeserializer configDeserializer = new ConfigDeserializer();
    
    void revertConfigurationModules(List<ConfigurationModule<?>> configModules) {
        List<ConfigurationModule<?>> reversed = new ArrayList<>(configModules);
        Collections.reverse(reversed);
        for (ConfigurationModule<?> configModule : reversed) {
            configModule.revert();
        }
    }

    static String getProfileName(String caseName) {
        return String.format("integrationTestProfile-%s", caseName);
    }

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

    Pair<IngestJobSettings, List<ConfigurationModule<?>>> runConfigurationModules(String caseName, TestSuiteConfig config) {
        if (CollectionUtils.isEmpty(config.getConfigurationModules())) {
            return Pair.of(getDefaultIngestConfig(caseName), Collections.emptyList());
        }

        IngestJobSettings curConfig = new IngestJobSettings(
                getProfileName(caseName),
                DEFAULT_INGEST_FILTER_TYPE,
                Collections.emptyList());

        List<ConfigurationModule<?>> configurationModuleCache = new ArrayList<>();

        for (ParameterizedResourceConfig configModule : config.getConfigurationModules()) {
            Pair<IngestJobSettings, ConfigurationModule<?>> ingestResult = runConfigurationModule(curConfig, configModule);
            if (ingestResult != null) {
                curConfig = ingestResult.first() == null ? curConfig : ingestResult.first();
                if (ingestResult.second() != null) {
                    configurationModuleCache.add(ingestResult.second());
                }
            }
        }
        return Pair.of(curConfig, configurationModuleCache);
    }

    private Pair<IngestJobSettings, ConfigurationModule<?>> runConfigurationModule(IngestJobSettings curConfig, ParameterizedResourceConfig configModule) {
        Class<?> clazz = null;
        try {
            clazz = Class.forName(configModule.getResource());
        } catch (ClassNotFoundException ex) {
            logger.log(Level.WARNING, "Unable to find module: " + configModule.getResource(), ex);
            return null;
        }

        if (clazz == null || !ConfigurationModule.class.isAssignableFrom(clazz)) {
            logger.log(Level.WARNING, String.format("%s does not seem to be an instance of a configuration module.", configModule.getResource()));
            return null;
        }

        Type configurationModuleType = Stream.of(clazz.getGenericInterfaces())
                .filter(type -> type instanceof ParameterizedType && ((ParameterizedType) type).getRawType().equals(ConfigurationModule.class))
                .map(type -> ((ParameterizedType) type).getActualTypeArguments()[0])
                .findFirst()
                .orElse(null);

        if (configurationModuleType == null) {
            logger.log(Level.SEVERE, String.format("Could not determine generic type of config module: %s", configModule.getResource()));
            return null;
        }

        ConfigurationModule<?> configModuleObj = null;
        Object result = null;
        try {
            configModuleObj = (ConfigurationModule<?>) clazz.newInstance();
            Method m = clazz.getMethod("configure", IngestJobSettings.class, (Class<?>) configurationModuleType);
            result = m.invoke(configModuleObj, curConfig, configDeserializer.convertToObj(configModule.getParameters(), configurationModuleType));
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException ex) {
            logger.log(Level.SEVERE, String.format("There was an error calling configure method on Configuration Module %s", configModule.getResource()), ex);
        }

        if (result instanceof IngestJobSettings) {
            return Pair.of((IngestJobSettings) result, configModuleObj);
        } else {
            logger.log(Level.SEVERE, String.format("Could not retrieve IngestJobSettings from %s", configModule.getResource()));
            return null;
        }
    }
}
