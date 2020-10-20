/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.integrationtesting.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
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
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.cxf.common.util.CollectionUtils;
import org.sleuthkit.autopsy.integrationtesting.PathUtil;

/**
 *
 * @author gregd
 */
public class ConfigDeserializer {

    private static final String JSON_EXT = "json";
    private static final Logger logger = Logger.getLogger(ConfigDeserializer.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();

    public <T> T convertToObj(Map<String, Object> toConvert, Type clazz) {
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        JsonElement jsonElement = gson.toJsonTree(toConvert);
        return gson.fromJson(jsonElement, clazz);
    }

    /**
     * Deserializes the json config specified at the given path into the java
     * equivalent IntegrationTestConfig object.
     *
     * @param filePath The path to the config.
     * @return The java object.
     * @throws IOException If there is an error opening the file.
     */
    public IntegrationTestConfig getConfigFromFile(String envConfigFile) throws IOException, IllegalStateException {
        EnvConfig envConfig = getEnvConfig(new File(envConfigFile));
        String testSuiteConfigPath = PathUtil.getAbsolutePath(envConfig.getWorkingDirectory(), envConfig.getRootTestSuitesPath());

        return new IntegrationTestConfig(
                getTestSuiteConfigs(new File(testSuiteConfigPath)),
                envConfig
        );
    }

    public EnvConfig getEnvConfig(File envConfigFile) throws IOException, IllegalStateException {
        EnvConfig config = mapper.readValue(envConfigFile, EnvConfig.class);
        return validate(envConfigFile, config);
    }

    private EnvConfig validate(File envConfigFile, EnvConfig config) throws IllegalStateException {
        if (config == null || StringUtils.isBlank(config.getRootCaseOutputPath()) || StringUtils.isBlank(config.getRootTestOutputPath())) {
            throw new IllegalStateException("EnvConfig must have both the root case output path and the root test output path set.");
        }

        // env config should be non-null after validation
        if (config.getWorkingDirectory() == null) {
            config.setWorkingDirectory(envConfigFile.getParentFile().getAbsolutePath());
        }

        return config;
    }

    public List<TestSuiteConfig> getTestSuiteConfig(File rootDirectory, File configFile) {
        try {
            JsonNode root = mapper.readTree(configFile);
            if (root.isArray()) {
                CollectionType listClass = mapper.getTypeFactory().constructCollectionType(List.class, TestSuiteConfig.class);
                return validate(rootDirectory, configFile, (List<TestSuiteConfig>) mapper.readValue(mapper.treeAsTokens(root), listClass));
            } else {
                return validate(rootDirectory, configFile, Arrays.asList(mapper.treeToValue(root, TestSuiteConfig.class)));
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to read test suite config at " + configFile.getPath(), ex);
            return Collections.emptyList();
        }
    }

    public List<TestSuiteConfig> getTestSuiteConfigs(File rootDirectory) {
        Collection<File> jsonFiles = FileUtils.listFiles(rootDirectory, new String[]{JSON_EXT}, true);
        return jsonFiles.stream()
                .flatMap((file) -> getTestSuiteConfig(rootDirectory, file).stream())
                .collect(Collectors.toList());
    }

    private List<TestSuiteConfig> validate(File rootDirectory, File file, List<TestSuiteConfig> testSuites) {
        return IntStream.range(0, testSuites.size())
                .mapToObj(idx -> validate(rootDirectory, file, idx, testSuites.get(idx)))
                .filter(c -> c != null)
                .collect(Collectors.toList());
    }

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
