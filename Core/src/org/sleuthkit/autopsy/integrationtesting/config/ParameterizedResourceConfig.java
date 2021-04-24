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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A resource that potentially has parameters as well.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = ParameterizedResourceConfig.ParameterizedResourceConfigDeserializer.class)
public class ParameterizedResourceConfig {

    /**
     * Deserializes from json. If a string is specified, that will be the
     * resource, otherwise, an object of { resource: string, parameters, {...} }
     * should be specified. The parameters can be expected to be a
     * Map<String, Object>, containing nested Maps, List<Object>, or json
     * primitives of type String, Integer, Long, Boolean, or Double.
     */
    public static class ParameterizedResourceConfigDeserializer extends StdDeserializer<ParameterizedResourceConfig> {

        private static final long serialVersionUID = 1L;

        /**
         * Main constructor.
         */
        public ParameterizedResourceConfigDeserializer() {
            this(null);
        }

        /**
         * Main constructor specifying type.
         *
         * @param vc The type.
         */
        public ParameterizedResourceConfigDeserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public ParameterizedResourceConfig deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            JsonNode node = jp.getCodec().readTree(jp);

            // if no node, return null.
            if (node == null) {
                return null;
            } else if (node instanceof TextNode) {
                // if just a string, return a ParameterizedResourceConfig where the resource is the string.
                return new ParameterizedResourceConfig(node.textValue());
            } else {
                // otherwise, determine the resource and create an object
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

        /**
         * Reads an Object node into a Map<String, Object>.
         *
         * @param node The json node.
         * @return The Map<String, Object>.
         */
        Map<String, Object> readMap(ObjectNode node) {
            Map<String, Object> jsonObject = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> keyValIter = node.fields();
            while (keyValIter.hasNext()) {
                Map.Entry<String, JsonNode> keyVal = keyValIter.next();
                jsonObject.put(keyVal.getKey(), readItem(keyVal.getValue()));
            }
            return jsonObject;
        }

        /**
         * Reads an Array node into a List<Object>.
         *
         * @param node The json array node.
         * @return The list of objects.
         */
        List<Object> readList(ArrayNode node) {
            List<Object> objArr = new ArrayList<>();
            for (JsonNode childNode : node) {
                objArr.add(readItem(childNode));
            }
            return objArr;
        }

        /**
         * Reads a Json value node into an Object (text, boolean, long, int,
         * double).
         *
         * @param vNode The value node.
         * @return The created object.
         */
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

        /**
         * Reads a json node of unknown type into a java object.
         *
         * @param node The json node.
         * @return The object.
         */
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

    /**
     * Main constructor.
     *
     * @param resource The resource name.
     * @param parameters The parameters to be specified.
     */
    public ParameterizedResourceConfig(String resource, Map<String, Object> parameters) {
        this.resource = resource;
        this.parameters = (parameters == null) ? Collections.emptyMap() : parameters;
    }

    /**
     * Main constructor where parameters are null.
     *
     * @param resource The resource.
     */
    public ParameterizedResourceConfig(String resource) {
        this(resource, null);
    }

    /**
     * @return The resource identifier.
     */
    public String getResource() {
        return resource;
    }

    /**
     * @return Parameters provided for the resource. Nested objects will be
     * Map<String, Object>, List<Object> or a json primitive like boolean, int,
     * long, double, string.
     */
    public Map<String, Object> getParameters() {
        return parameters == null ? Collections.emptyMap() : Collections.unmodifiableMap(parameters);
    }
}
