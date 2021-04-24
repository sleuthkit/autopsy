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
package org.sleuthkit.autopsy.integrationtesting.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.collections.CollectionUtils;
import org.sleuthkit.autopsy.integrationtesting.PathUtil;

/**
 * Handles deserializing configuration items.
 */
public class ConfigDeserializer {

    private static final String JSON_EXT = "json";
    private static final Logger logger = Logger.getLogger(ConfigDeserializer.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();

    // The following are the keys that must be provided as arguments (prefixed with 'integration-test.')
    // A config file key must be specifed or at least the test suites path, and output path.
    // If a config specifying the EnvConfig json exists, specify this path to load it
    private static final String CONFIG_FILE_KEY = "configFile";
    // expecting properties are marked as "integration-test.propVal"
    private static final String INTEGRATION_TEST_NAMESPACE = "integration-test";

    /**
     * Deserializes the specified json file into an EnvConfig object using
     * System.Property specified values. This is affected by build.xml test-init
     * target and values specified in this file.
     *
     * @return The deserialized file.
     * @throws IOException
     * @throws IllegalStateException If the env config file cannot be found but
     * the property has been specified or the env config object cannot be
     * validated.
     */
    public EnvConfig getEnvConfigFromSysProps() throws IOException, IllegalStateException {
        String configFileKey = String.join(".", INTEGRATION_TEST_NAMESPACE, CONFIG_FILE_KEY);
        if (System.getProperty(configFileKey) != null) {
            // try to load from file if value is present
            String fileLoc = System.getProperty(configFileKey);
            File envConfigFile = new File(fileLoc);
            if (envConfigFile.exists()) {
                return getEnvConfig(envConfigFile);
            } else {
                throw new IllegalStateException(String.format("No file exists at %s", fileLoc));
            }
        } else {
            // otherwise, try to load from properties
            try {
                Map<String, Object> integrationProperties = getOrCreate(getSysPropsMap(), INTEGRATION_TEST_NAMESPACE);
                return validate(null, convertToObj(integrationProperties, EnvConfig.class));
            } catch (IllegalStateException ex) {
                throw new IllegalStateException("EnvConfig could not be determined from system property values", ex);
            }
        }
    }

    /**
     * Creates a Map<String, Object> representing the values in System
     * Properties. Properties will be stored in nested maps for property keys
     * separated by periods. For instance, the property 'keyA.childA.childB'
     * will be located in returnObj.get("keyA").get("childA").get("childB").
     *
     * @return The generated map.
     */
    private Map<String, Object> getSysPropsMap() {
        Map<String, Object> mapToRet = new HashMap<>();

        for (String key : System.getProperties().stringPropertyNames()) {
            String value = System.getProperty(key);

            String[] keyPieces = key.split("\\.");
            Map<String, Object> mapToAddTo = mapToRet;
            for (int i = 0; i < keyPieces.length - 1; i++) {
                mapToAddTo = getOrCreate(mapToAddTo, keyPieces[i]);
            }

            mapToAddTo.put(keyPieces[keyPieces.length - 1], value);
        }

        return mapToRet;
    }

    /**
     * Extends HashMap<String, Object> to guarantee a type of
     * Map<String, Object>
     * with type erasure.
     */
    private static class StringObjMap extends HashMap<String, Object> {

        private static final long serialVersionUID = 1L;

        /**
         * Main constructor.
         */
        StringObjMap() {
        }

    }

    /**
     * Retrieves the Map<String, Object> that will be found at parent.get(key)
     * or create a new Map at that key and return that.
     *
     * @param parent The parent map.
     * @param key The key to fetch.
     * @return The child map.
     */
    private Map<String, Object> getOrCreate(Map<String, Object> parent, String key) {
        Object child = parent.get(key);
        if (child instanceof StringObjMap) {
            return (StringObjMap) child;
        } else {
            Map<String, Object> toRet = new StringObjMap();
            parent.put(key, toRet);
            return toRet;
        }
    }

    /**
     * Creates an object of type T by re-deserializing the map to the specified
     * type.
     *
     * @param toConvert The map to convert.
     * @param clazz The type of object to deserialize to.
     * @return The object converted to the specified type.
     */
    public <T> T convertToObj(Map<String, Object> toConvert, Type clazz) {
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        JsonElement jsonElement = gson.toJsonTree(toConvert);
        return gson.fromJson(jsonElement, clazz);
    }

    /**
     * Deserializes the json config specified at the given path into the java
     * equivalent IntegrationTestConfig object. This uses information in env
     * config to determine test suite locations.
     *
     * @return The java object.
     * @throws IOException If there is an error loading the config.
     * @throws IllegalStateException If the config cannot be validated.
     */
    public IntegrationTestConfig getIntegrationTestConfig() throws IOException, IllegalStateException {
        EnvConfig envConfig = getEnvConfigFromSysProps();
        String testSuiteConfigPath = PathUtil.getAbsolutePath(envConfig.getWorkingDirectory(), envConfig.getRootTestSuitesPath());

        return new IntegrationTestConfig(
                getTestSuiteConfigs(new File(testSuiteConfigPath)),
                envConfig
        );
    }

    /**
     * Deserializes the specified json file into an EnvConfig object.
     *
     * @param envConfigFile The file location for the environmental config.
     * @return The deserialized file.
     * @throws IOException
     * @throws IllegalStateException If the file cannot be validated.
     */
    public EnvConfig getEnvConfig(File envConfigFile) throws IOException, IllegalStateException {
        EnvConfig config = mapper.readValue(envConfigFile, EnvConfig.class);
        return validate(envConfigFile, config);
    }

    /**
     * Validates the environment configuration file.
     *
     * @param envConfigFile The file location of the config (used for setting
     * working directory if not specified).
     * @param config The environmental config that was deserialized.
     * @return The updated config.
     * @throws IllegalStateException If could not be validated.
     */
    private EnvConfig validate(File envConfigFile, EnvConfig config) throws IllegalStateException {
        // set working directory based off of parent of envConfigFile if that parent exists
        if (config.getWorkingDirectory() == null && envConfigFile != null && envConfigFile.getParentFile() != null) {
            config.setWorkingDirectory(envConfigFile.getParentFile().getAbsolutePath());
        }

        // env config should be non-null after validation
        if (config == null
                || StringUtils.isBlank(config.getRootCaseOutputPath())
                || StringUtils.isBlank(config.getRootTestOutputPath())) {
            throw new IllegalStateException("EnvConfig must have the root case output path and the root test output path set.");
        }

        return config;
    }

    /**
     * Derives a list of test suite config's specified in the configFile. The
     * root directory is the same or an ancestor directory of this configFile
     * used for the relative output path.
     *
     * @param rootDirectory The ancestor directory of configFile.
     * @param configFile The configFile to read.
     * @return The list of test suite configs found after invalid configs are
     * filtered.
     */
    public List<TestSuiteConfig> getTestSuiteConfig(File rootDirectory, File configFile) {
        try {
            JsonNode root = mapper.readTree(configFile);
            if (root.isArray()) {
                // Define a collection type of List<TestSuiteConfig> for the purposes of json deserialization.
                CollectionType listClass = mapper.getTypeFactory().constructCollectionType(List.class,
                        TestSuiteConfig.class
                );

                // This suppresses compiler warning for this cast.
                @SuppressWarnings("unchecked")
                List<TestSuiteConfig> testSuites = (List<TestSuiteConfig>) mapper.readValue(mapper.treeAsTokens(root), listClass);

                return validate(rootDirectory, configFile, testSuites);
            } else {
                return validate(rootDirectory, configFile, Arrays.asList(mapper.treeToValue(root, TestSuiteConfig.class
                )));
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to read test suite config at " + configFile.getPath(), ex);
            return Collections.emptyList();
        }
    }

    /**
     * Finds all test suite config json files within this directory and sub
     * directories or if a file is specified, just that file is loaded.
     *
     * @param fileOrDirectory The parent directory of test suite configurations
     * or the configuration file itself.
     * @return The list of determined test suite configurations found.
     */
    public List<TestSuiteConfig> getTestSuiteConfigs(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            Collection<File> jsonFiles = FileUtils.listFiles(fileOrDirectory, new String[]{JSON_EXT}, true);
            return jsonFiles.stream()
                    .flatMap((file) -> getTestSuiteConfig(fileOrDirectory, file).stream())
                    .collect(Collectors.toList());
        } else if (fileOrDirectory.isFile()) {
            return getTestSuiteConfig(fileOrDirectory, fileOrDirectory);
        }

        logger.log(Level.WARNING, "Unable to read file at: {0}", fileOrDirectory);
        return Collections.emptyList();
    }

    /**
     * Validates the list of test suite configurations.
     *
     * @param rootDirectory The root directory for configurations
     * (relativeOutputPath is set by determining relative path from root
     * directory to file).
     * @param file The file containing the configuration.
     * @param testSuites The list of test suite objects discovered.
     * @return The test suites with invalid items filtered and relative output
     * path set.
     */
    private List<TestSuiteConfig> validate(File rootDirectory, File file, List<TestSuiteConfig> testSuites) {
        return IntStream.range(0, testSuites.size())
                .mapToObj(idx -> validate(rootDirectory, file, idx, testSuites.get(idx)))
                .filter(c -> c != null)
                .collect(Collectors.toList());
    }

    /**
     * Validates a single test suite by returning it or null if invalid. The
     * relative output path is also determined based on the relative path from
     * rootDirectory to file.
     *
     * @param rootDirectory The root directory for configurations
     * (relativeOutputPath is set by determining relative path from root
     * directory to file).
     * @param file The file containing the configuration.
     * @param index The index within a list of test suites which this item
     * exists.
     * @param config The test suite confi.
     * @return The test suite with relative output path set or null if invalid.
     */
    private TestSuiteConfig validate(File rootDirectory, File file, int index, TestSuiteConfig config) {
        if (config == null
                || StringUtils.isBlank(config.getName())
                || config.getCaseTypes() == null
                || CollectionUtils.isEmpty(config.getDataSources())
                || config.getIntegrationTests() == null) {

            logger.log(Level.WARNING, String.format("Item in %s at index %d must contain a valid 'name', 'caseTypes', 'dataSources', and 'integrationTests'", file.toString(), index));
            return null;
        }

        if (config.getRelativeOutputPath() == null) {
            // taken from https://stackoverflow.com/questions/204784/how-to-construct-a-relative-path-in-java-from-two-absolute-paths-or-urls
            String relative = rootDirectory.toURI().relativize(file.toURI()).getPath();
            if (relative.endsWith("." + JSON_EXT)) {
                relative = relative.substring(0, relative.length() - ("." + JSON_EXT).length());
            }
            config.setRelativeOutputPath(relative);
        }

        return config;
    }
}
