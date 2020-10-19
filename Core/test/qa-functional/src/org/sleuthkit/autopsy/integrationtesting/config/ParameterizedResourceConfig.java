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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 *
 * @author gregd
 */
public class ParameterizedResourceConfig {

    public static class ParameterizedResourceConfigDeserializer extends StdDeserializer<ParameterizedResourceConfig> {

        public ParameterizedResourceConfigDeserializer() {
            this(null);
        }

        public ParameterizedResourceConfigDeserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public ParameterizedResourceConfig deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            JsonNode node = jp.getCodec().readTree(jp);
            if (node.isTextual()) {
                return new ParameterizedResourceConfig(node.asText());
            } else {
                return ctxt.readValue(jp, ParameterizedResourceConfig.class);
            }
        }
    }

    private final String resource;
    private final Map<String, Object> parameters;

    public ParameterizedResourceConfig(String resource, Map<String, Object> parameters) {
        this.resource = resource;
        this.parameters = (parameters == null) ? Collections.emptyMap() : parameters;
    }

    public ParameterizedResourceConfig(String resource) {
        this(resource, null);
    }

    public String getResource() {
        return resource;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }
}
