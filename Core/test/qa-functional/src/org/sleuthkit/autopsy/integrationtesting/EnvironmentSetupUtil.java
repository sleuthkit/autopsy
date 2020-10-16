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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.openide.util.io.NbObjectInputStream;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.autopsy.python.FactoryClassNameNormalizer;

/**
 * Handles setting up the autopsy environment to be used with integration tests.
 */
public class EnvironmentSetupUtil {

    private static final Logger logger = Logger.getLogger(MainTestRunner.class.getName());

    private static final IngestJobSettings.IngestType DEFAULT_INGEST_TYPE = IngestJobSettings.IngestType.ALL_MODULES;

    /**
     * Gets an IngestModuleFactory instance from the canonical class name
     * provided.
     *
     * @param className The canonical class name of the factory.
     * @return The IngestModuleFactory for the class or null if can't be
     * determined.
     */
    private IngestModuleFactory getIngestModuleFactory(String className) {
        if (className == null) {
            logger.log(Level.WARNING, "No class name provided.");
            return null;
        }

        Class<?> ingestModuleFactoryClass = null;
        try {
            ingestModuleFactoryClass = Class.forName(className);
        } catch (ClassNotFoundException ex) {
            logger.log(Level.WARNING, String.format("No class found matching canonical name in config of %s.", className), ex);
            return null;
        }

        Object factoryObject = null;
        try {
            factoryObject = ingestModuleFactoryClass.newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            logger.log(Level.WARNING, String.format("Error during instantiation of %s.", className), ex);
            return null;
        }

        if (factoryObject instanceof IngestModuleFactory) {
            return (IngestModuleFactory) factoryObject;
        } else {
            logger.log(Level.WARNING, String.format("Could not properly instantiate class of: %s", className));
            return null;
        }
    }

    /**
     * Creates an IngestJobSettings instance.
     *
     * @param profileName The name of the profile.
     * @param ingestType The ingest type.
     * @param enabledFactoryClasses The list of canonical class names of
     * factories to be used in ingest.
     * @param pathToIngestModuleSettings The path to ingest module settings.
     * @return The IngestJobSettings instance.
     */
    private IngestJobSettings getIngestSettings(String profileName, IngestJobSettings.IngestType ingestType, List<String> enabledFactoryClasses, String pathToIngestModuleSettings) {
        Map<String, IngestModuleFactory> classToFactoryMap = enabledFactoryClasses.stream()
                .map(factoryName -> getIngestModuleFactory(factoryName))
                .filter(factory -> factory != null)
                .collect(Collectors.toMap(factory -> factory.getClass().getCanonicalName(), factory -> factory, (f1, f2) -> f1));

        List<IngestModuleTemplate> ingestModuleTemplates = enabledFactoryClasses.stream()
                .map(className -> {
                    IngestModuleFactory factory = classToFactoryMap.get(className);
                    if (factory == null) {
                        logger.log(Level.WARNING, "Could not find ingest module factory: " + className);
                    }
                    return factory;
                })
                .filter(factory -> factory != null)
                .map(factory -> getTemplate(pathToIngestModuleSettings, factory))
                .collect(Collectors.toList());

        return new IngestJobSettings(profileName, ingestType, ingestModuleTemplates);
    }

    /**
     * Creates an IngestModuleTemplate given a path to the module settings and
     * the factory. If that file does not exist, default settings are used.
     *
     * @param pathToIngestModuleSettings the path to the folder containing
     * settings.
     * @param factory The factory.
     * @return The template to be used.
     */
    private IngestModuleTemplate getTemplate(String pathToIngestModuleSettings, IngestModuleFactory factory) {
        String fileName = FactoryClassNameNormalizer.normalize(factory.getClass().getCanonicalName()) + ".settings";
        File settingsFile = Paths.get(pathToIngestModuleSettings, fileName).toFile();
        if (settingsFile.exists()) {
            try (NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(settingsFile.getAbsolutePath()))) {
                IngestModuleIngestJobSettings settings = (IngestModuleIngestJobSettings) in.readObject();
                return new IngestModuleTemplate(factory, settings);
            } catch (IOException | ClassNotFoundException ex) {
                logger.log(Level.WARNING, String.format("Unable to open %s as IngestModuleIngestJobSettings", settingsFile), ex);
            }
        }

        return new IngestModuleTemplate(factory, factory.getDefaultIngestJobSettings());
    }

    /**
     * Sets up the Autopsy environment based on the case config and returns the
     * ingest job settings to be used for ingest.
     *
     * @param caseConfig The case configuration.
     * @return The IngestJobSettings to be used with ingest.
     */
    public IngestJobSettings setupEnvironment(CaseConfig caseConfig) {
        return getIngestSettings(caseConfig.getCaseName(),
                DEFAULT_INGEST_TYPE,
                caseConfig.getIngestModules(), caseConfig.getIngestModuleSettingsPath());
    }
}
