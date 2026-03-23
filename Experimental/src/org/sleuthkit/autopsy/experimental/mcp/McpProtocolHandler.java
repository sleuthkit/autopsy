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
 *
 * queryService is null when no case is open. tools/list always works (tool
 * definitions are static). tools/call returns a clean error when null.
 */
class McpProtocolHandler {

    // Used solely for tools/list — listTools() has no case dependency.
    private static final TskQueryService TOOLS_LIST_SERVICE = new TskQueryService(null, null);

    private volatile TskQueryService queryService; // null = no case open
    private final ObjectMapper mapper = new ObjectMapper();

    McpProtocolHandler() { }

    void setQueryService(TskQueryService qs) {
        this.queryService = qs;
    }

    void clearQueryService() {
        this.queryService = null;
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
                case "tools/list"   -> TOOLS_LIST_SERVICE.listTools();
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

        TskQueryService qs = queryService;
        if (qs == null) {
            throw new McpException(
                "No case is currently open in Autopsy. Open a case first to use MCP tools.");
        }

        return switch (toolName) {
            case "query_files"        -> qs.queryFiles(args);
            case "query_data_artifacts"    -> qs.queryDataArtifacts(args);
            case "query_analysis_results"  -> qs.queryAnalysisResults(args);
            case "get_hosts"            -> qs.getHosts();
            case "query_data_sources"   -> qs.queryDataSources();
            case "get_data_source_tree" -> qs.getDataSourceTree(args);
            case "get_case_summary"   -> qs.getCaseSummary();
            case "get_file_content"            -> qs.getFileContent(args);
            case "query_tags"                  -> qs.queryTags(args);
            case "query_timeline"              -> qs.queryTimeline(args);
            case "summarize_timeline"          -> qs.summarizeTimeline(args);
            case "get_os_accounts"             -> qs.getOsAccounts();
            case "get_communications_accounts" -> qs.getCommunicationsAccounts(args);
            case "get_account_relationships"   -> qs.getAccountRelationships(args);
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
