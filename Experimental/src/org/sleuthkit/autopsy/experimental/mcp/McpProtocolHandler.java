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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parses incoming MCP JSON-RPC requests, routes tool calls to TskQueryService,
 * and formats JSON-RPC responses.
 *
 * queryService is null when no case is open. tools/list always works (tool
 * definitions are static). tools/call returns a clean error when null.
 */
class McpProtocolHandler {

    private static final Logger logger = Logger.getLogger(McpProtocolHandler.class.getName());

    // JSON-RPC 2.0 reserved error codes (package-private for reuse in McpServer)
    static final int ERR_PARSE_ERROR      = -32700;
    static final int ERR_METHOD_NOT_FOUND = -32601;
    static final int ERR_INTERNAL_ERROR   = -32603;

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
        // id defaults to NullNode so parse errors return a conforming response
        // even when the request cannot be read at all (JSON-RPC spec §5).
        JsonNode id = NullNode.getInstance();
        try {
            JsonNode request = mapper.readTree(requestJson);
            String method = request.path("method").asText();
            JsonNode params = request.path("params");
            // Preserve the id as a JsonNode so its original type (number, string, null,
            // or absent) is returned unchanged in the response, as the JSON-RPC spec requires.
            id = request.has("id") ? request.get("id") : NullNode.getInstance();

            Object result = switch (method) {
                case "tools/list"   -> TOOLS_LIST_SERVICE.listTools();
                case "tools/call"   -> dispatchToolCall(params);
                case "initialize"   -> handleInitialize();
                default             -> throw new McpException("Unknown method: " + method, McpException.ERR_METHOD_NOT_FOUND);
            };
            return buildSuccess(id, result);
        } catch (JsonProcessingException ex) {
            logger.log(Level.WARNING, "MCP request contained malformed JSON", ex);
            return buildError(NullNode.getInstance(), ERR_PARSE_ERROR, "Parse error");
        } catch (McpException ex) {
            // Expected protocol-level errors (unknown method/tool, bad params, no case open)
            // are client-visible in the response — no need to flood the Autopsy log.
            return buildError(id, ex.getJsonRpcCode(), ex.getMessage());
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unexpected error handling MCP request", ex);
            return buildError(id, ERR_INTERNAL_ERROR, ex.getMessage());
        }
    }

    private Object dispatchToolCall(JsonNode params) throws Exception {
        String toolName = params.path("name").asText();
        JsonNode args = params.path("arguments");

        // Tools that work regardless of whether a case is open
        if ("get_server_status".equals(toolName)) {
            return buildServerStatus();
        }
        if ("get_case_summary".equals(toolName) && queryService == null) {
            return wrapWithCaseId(
                Map.of("message", "No case is currently open in Autopsy."), null);
        }

        TskQueryService qs = queryService;
        if (qs == null) {
            throw new McpException(
                "No case is currently open in Autopsy. Open a case first to use MCP tools.",
                McpException.ERR_INTERNAL_ERROR);
        }

        Object toolResult = switch (toolName) {
            case "query_files"                 -> qs.queryFiles(args);
            case "query_data_artifacts"        -> qs.queryDataArtifacts(args);
            case "query_analysis_results"      -> qs.queryAnalysisResults(args);
            case "get_hosts"                   -> qs.getHosts();
            case "query_data_sources"          -> qs.queryDataSources();
            case "get_data_source_tree"        -> qs.getDataSourceTree(args);
            case "get_case_summary"            -> qs.getCaseSummary();
            case "get_file_content"            -> qs.getFileContent(args);
            case "query_tags"                  -> qs.queryTags(args);
            case "query_timeline"              -> qs.queryTimeline(args);
            case "summarize_timeline"          -> qs.summarizeTimeline(args);
            case "get_os_accounts"             -> qs.getOsAccounts();
            case "get_communications_accounts" -> qs.getCommunicationsAccounts(args);
            case "get_account_relationships"   -> qs.getAccountRelationships(args);
            case "get_object_children"         -> qs.getObjectChildren(args);
            case "list_reports"                -> qs.listReports();
            case "get_report_content"          -> qs.getReportContent(args);
            default -> throw new McpException("Unknown tool: " + toolName, McpException.ERR_METHOD_NOT_FOUND);
        };

        return wrapWithCaseId(toolResult, qs.getCaseName());
    }

    private Map<String, Object> wrapWithCaseId(Object result, String caseId) {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("caseId", caseId); // null when no case is open
        wrapper.put("result", result);
        return wrapper;
    }

    private Map<String, Object> buildServerStatus() {
        TskQueryService qs = queryService;
        boolean caseOpen = qs != null;
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("server", "autopsy-mcp");
        status.put("status", "running");
        status.put("caseOpen", caseOpen);
        if (caseOpen) {
            status.put("caseName", qs.getCaseName());
        }
        return status;
    }

    private Object handleInitialize() {
        return Map.of(
            "protocolVersion", "2024-11-05",
            "serverInfo", Map.of("name", "autopsy-mcp", "version", "1.0.0"),
            "capabilities", Map.of("tools", Map.of())
        );
    }

    private String buildSuccess(JsonNode id, Object result) throws Exception {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", mapper.valueToTree(result));
        return mapper.writeValueAsString(response);
    }

    private String buildError(JsonNode id, int code, String message) throws Exception {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        ObjectNode error = mapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        response.set("error", error);
        return mapper.writeValueAsString(response);
    }
}
