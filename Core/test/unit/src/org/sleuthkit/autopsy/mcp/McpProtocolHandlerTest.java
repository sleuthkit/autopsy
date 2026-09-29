/*
 * Autopsy Forensic Browser
 *
 * Copyright 2024 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * Conformance tests for the JSON-RPC responses produced by McpProtocolHandler.
 *
 * tools/list must return a ListToolsResult — an object with a "tools" array — in every published
 * schema revision (2024-11-05, 2025-06-18, 2025-11-25). Returning the bare list makes "result" an
 * array, which spec-validating clients (including the official MCP SDK) reject with a schema error,
 * leaving the server unusable even though the Node wrapper — which forwards raw JSON without
 * validating — appears to work.
 */
public class McpProtocolHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Sends a minimal JSON-RPC request for one method through the real handler and
     * returns its "result" node, failing the test if the response carries an error.
     *
     * @param method JSON-RPC method name, e.g. "tools/list"
     * @return the parsed result node
     * @throws Exception if the handler throws or the response is not valid JSON
     */
    private JsonNode call(String method) throws Exception {
        McpProtocolHandler handler = new McpProtocolHandler();
        String response = handler.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\"}");
        JsonNode parsed = mapper.readTree(response);
        Assert.assertNull("expected a result, got an error: " + response, parsed.get("error"));
        return parsed.get("result");
    }

    @Test
    /**
     * tools/list must answer with a ListToolsResult object holding a non-empty "tools"
     * array; a bare array is rejected by spec-validating clients.
     *
     * @throws Exception if the request cannot be handled
     */
    public void toolsListResultIsAnObjectWithAToolsArray() throws Exception {
        JsonNode result = call("tools/list");
        Assert.assertTrue("ListToolsResult must be a JSON object, was: " + result.getNodeType(),
                result.isObject());
        JsonNode tools = result.get("tools");
        Assert.assertNotNull("ListToolsResult.tools is required", tools);
        Assert.assertTrue("ListToolsResult.tools must be an array", tools.isArray());
        Assert.assertTrue("case-independent tools must be advertised", tools.size() > 0);
    }

    @Test
    /**
     * Every advertised tool must set "additionalProperties": false so that an unknown
     * parameter cannot be silently dropped (issue #8033).
     *
     * @throws Exception if the request cannot be handled
     */
    public void everyToolSchemaSetsAdditionalPropertiesFalse() throws Exception {
        JsonNode tools = call("tools/list").get("tools");
        List<String> offenders = new ArrayList<>();
        for (JsonNode tool : tools) {
            String name = tool.get("name").asText();
            JsonNode schema = tool.get("inputSchema");
            Assert.assertNotNull(name + ": inputSchema is required", schema);
            Assert.assertEquals(name + ": inputSchema.type", "object", schema.path("type").asText());
            Assert.assertNotNull(name + ": inputSchema.properties is required", schema.get("properties"));
            JsonNode additional = schema.get("additionalProperties");
            if (additional == null || additional.asBoolean(true)) {
                offenders.add(name);
            }
        }
        Assert.assertTrue("these tools would silently ignore unknown parameters (see issue #8033): "
                + offenders, offenders.isEmpty());
    }
}
