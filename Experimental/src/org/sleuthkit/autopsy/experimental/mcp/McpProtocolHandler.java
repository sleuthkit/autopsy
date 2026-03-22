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
package org.sleuthkit.autopsy.experimental.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Parses incoming MCP JSON-RPC requests, routes tool calls to TskQueryService,
 * and formats JSON-RPC responses.
 */
class McpProtocolHandler {

    private final TskQueryService queryService;
    private final ObjectMapper mapper = new ObjectMapper();

    public McpProtocolHandler(TskQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Handle a raw MCP JSON-RPC request string.
     * Returns a JSON-RPC response string.
     */
    public String handle(String requestJson) throws Exception {
        JsonNode request = mapper.readTree(requestJson);
        String method = request.path("method").asText();
        JsonNode params = request.path("params");
        String id = request.path("id").asText();

        try {
            Object result = switch (method) {
                case "tools/list"   -> queryService.listTools();
                case "tools/call"   -> dispatchToolCall(params);
                case "initialize"   -> handleInitialize();
                default             -> throw new McpException("Unknown method: " + method);
            };
            return buildSuccess(id, result);
        } catch (McpException ex) {
            return buildError(id, -32601, ex.getMessage());
        }
    }

    private Object dispatchToolCall(JsonNode params) throws Exception {
        String toolName = params.path("name").asText();
        JsonNode args = params.path("arguments");

        return switch (toolName) {
            case "query_files"        -> queryService.queryFiles(args);
            case "query_artifacts"    -> queryService.queryArtifacts(args);
            case "get_hosts"            -> queryService.getHosts();
            case "query_data_sources"   -> queryService.queryDataSources();
            case "get_data_source_tree" -> queryService.getDataSourceTree(args);
            case "get_case_summary"   -> queryService.getCaseSummary();
            case "query_tags"         -> queryService.queryTags(args);
            default -> throw new McpException("Unknown tool: " + toolName);
        };
    }

    private Object handleInitialize() {
        return Map.of(
            "protocolVersion", "2024-11-05",
            "serverInfo", Map.of("name", "autopsy-mcp", "version", "1.0.0"),
            "capabilities", Map.of("tools", Map.of())
        );
    }

    private String buildSuccess(String id, Object result) throws Exception {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.set("result", mapper.valueToTree(result));
        return mapper.writeValueAsString(response);
    }

    private String buildError(String id, int code, String message) throws Exception {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        ObjectNode error = mapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        response.set("error", error);
        return mapper.writeValueAsString(response);
    }
}
