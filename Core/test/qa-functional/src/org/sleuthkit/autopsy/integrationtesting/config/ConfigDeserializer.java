/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.integrationtesting.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

/**
 *
 * @author gregd
 */
public class ConfigDeserializer {

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
    public IntegrationTestConfig getConfigFromFile(String filePath) throws IOException {
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        IntegrationTestConfig config = gson.fromJson(new FileReader(new File(filePath)), IntegrationTestConfig.class);

        validate(config);

        // env config should be non-null after validation
        if (config.getEnvConfig().getWorkingDirectory() == null) {
            config.getEnvConfig().setWorkingDirectory(new File(configFile).getParentFile().getAbsolutePath());
        }

        return config;
    }
}
