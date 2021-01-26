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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Integration Test results for a case to be written to disk in a text format.
 */
class OutputResults {
    private static final Logger logger = Logger.getLogger(IntegrationTestService.class.getName());

    /**
     * Returns the value for a particular key in a map. Creates a default value
     * for a key in a map if none exists.
     *
     * @param map The map.
     * @param key The key.
     * @param onNotPresent The means of creating a value if no value exists for
     * that key.
     * @return The value for that key or the default value.
     */
    private static <K, V> V getOrCreate(Map<K, V> map, K key, Supplier<V> onNotPresent) {
        V curValue = map.get(key);
        if (curValue == null) {
            curValue = onNotPresent.get();
            map.put(key, curValue);
        }
        return curValue;
    }

    /**
     * A mapping of package -> test suite -> test -> output data
     */
    private final Map<String, Map<String, Map<String, Object>>> data = new HashMap<>();

    /**
     * Adds a result for a particular test in a test suite.
     *
     * @param pkgName The package name of the test suite.
     * @param className The name of the test suite.
     * @param methodName The test in the test suite.
     * @param result The result to set for this test.
     */
    void addResult(String pkgName, String className, String methodName, Object result) {
        Map<String, Map<String, Object>> packageClasses = getOrCreate(data, pkgName, () -> new HashMap<>());
        Map<String, Object> classMethods = getOrCreate(packageClasses, className, () -> new HashMap<>());
        Object toWrite = result instanceof Throwable ? new ExceptionObject((Throwable) result) : result;
        classMethods.put(methodName, toWrite);
    }

    /**
     * @return The data to be serialized.
     */
    Object getSerializableData() {
        return Collections.unmodifiableMap(data);
    }

    /**
     * The name of the CaseType to be used in the filename during serialization.
     *
     * @param caseType The case type.
     * @return The identifier to be used in a file name.
     */
    private String getCaseTypeId(Case.CaseType caseType) {
        if (caseType == null) {
            return "";
        }

        switch (caseType) {
            case SINGLE_USER_CASE:
                return "singleUser";
            case MULTI_USER_CASE:
                return "multiUser";
            default:
                throw new IllegalArgumentException("Unknown case type: " + caseType);
        }
    }

    /**
     * Used by yaml serialization to properly represent objects.
     */
    private static final Representer MAP_REPRESENTER = new Representer() {
        @Override
        protected MappingNode representJavaBean(Set<Property> properties, Object javaBean) {
            // don't show class name in yaml
            if (!classTags.containsKey(javaBean.getClass())) {
                addClassTag(javaBean.getClass(), Tag.MAP);
            }

            return super.representJavaBean(properties, javaBean);
        }
    };

    /**
     * Used by yaml serialization to properly represent objects.
     */
    private static final DumperOptions DUMPER_OPTS = new DumperOptions() {
        {
            // Show each property on a new line.
            setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            // allow for serializing properties that only have getters.
            setAllowReadOnlyProperties(true);
        }
    };

    /**
     * The actual yaml serializer that is used.
     */
    private static final Yaml YAML_SERIALIZER = new Yaml(MAP_REPRESENTER, DUMPER_OPTS);

    /**
     * Serializes results of a test to a yaml file.
     *
     * @param outputFolder The folder where the yaml should be written.
     * @param caseName The name of the case (used to determine filename).
     * @param caseType The type of case (used to determine filename).
     */
    public void serializeToFile(String outputFolder, String caseName, Case.CaseType caseType) {
        serializeToFile(getSerializationPath(outputFolder, caseName, caseType));
    }

    private String getSerializationPath(String outputFolder, String caseName, Case.CaseType caseType) {
        String outputExtension = ".yml";
        Path outputPath = Paths.get(outputFolder, String.format("%s-%s%s", caseName, getCaseTypeId(caseType), outputExtension));
        return outputPath.toString();
    }

    /**
     * Serializes results of a test to a yaml file.
     *
     * @param outputPath The output path.
     */
    void serializeToFile(String outputPath) {
        File outputFile = new File(outputPath);
        
        if (!outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }
        
        try {
            FileWriter writer = new FileWriter(outputFile);
            YAML_SERIALIZER.dump(getSerializableData(), writer);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "There was an error writing results to outputPath: " + outputPath, ex);
        }
    }
}
