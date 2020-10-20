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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.sleuthkit.autopsy.integrationtesting.config.ParameterizedResourceConfig.ParameterizedResourceConfigDeserializer;

/**
 *
 * @author gregd
 */
@JsonDeserialize(using = ParameterizedResourceConfigDeserializer.class)
public class ParameterizedResourceConfig {

    public static class ParameterizedResourceConfigDeserializer extends StdDeserializer<ParameterizedResourceConfig> {

        private static TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
        };

        public ParameterizedResourceConfigDeserializer() {
            this(null);
        }

        public ParameterizedResourceConfigDeserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public ParameterizedResourceConfig deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            JsonNode node = jp.getCodec().readTree(jp);

            if (node == null) {
                return null;
            } else if (node instanceof TextNode) {
                return new ParameterizedResourceConfig(((TextNode) node).textValue());
            } else {
                JsonNode resourceNode = node.get("resource");
                String resource = (resourceNode != null) ? resourceNode.asText() : null;

                Map<String, Object> parameters = null;
                JsonNode parametersNode = node.get("parameters");
                if (parametersNode != null && parametersNode.isObject()) {
                    parameters = readMap((ObjectNode) parametersNode);
                }

                return new ParameterizedResourceConfig(resource, parameters);
            }
        }

        Map<String, Object> readMap(ObjectNode node) {
            Map<String, Object> jsonObject = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> keyValIter = node.fields();
            while (keyValIter.hasNext()) {
                Map.Entry<String, JsonNode> keyVal = keyValIter.next();
                jsonObject.put(keyVal.getKey(), readItem(keyVal.getValue()));
            }
            return jsonObject;
        }

        List<Object> readList(ArrayNode node) {
            List<Object> objArr = new ArrayList<>();
            for (JsonNode childNode : node) {
                objArr.add(readItem(childNode));
            }
            return objArr;
        }

        Object readJsonPrimitive(ValueNode vNode) {
            if (vNode.isTextual()) {
                return vNode.asText();
            } else if (vNode.isBoolean()) {
                return vNode.asBoolean();
            } else if (vNode.isLong()) {
                return vNode.asLong();
            } else if (vNode.isInt()) {
                return vNode.asInt();
            } else if (vNode.isDouble()) {
                return vNode.asDouble();
            }

            return null;
        }

        Object readItem(JsonNode node) {
            if (node == null) {
                return null;
            }

            if (node.isObject()) {
                return readMap((ObjectNode) node);
            } else if (node.isArray()) {
                return readList((ArrayNode) node);
            } else if (node.isValueNode()) {
                return readJsonPrimitive((ValueNode) node);
            }

            return null;
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
