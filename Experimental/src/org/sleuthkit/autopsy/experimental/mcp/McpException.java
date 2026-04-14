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

/**
 * Checked exception for MCP protocol errors (unknown methods, unknown tools,
 * bad parameters). The message is included directly in the JSON-RPC error response.
 * The jsonRpcCode is returned verbatim in the JSON-RPC error object's "code" field.
 */
class McpException extends Exception {

    /** Unknown method or tool name. */
    static final int ERR_METHOD_NOT_FOUND = -32601;
    /** Missing or invalid parameter. */
    static final int ERR_INVALID_PARAMS   = -32602;
    /** Internal server error. */
    static final int ERR_INTERNAL_ERROR   = -32603;

    private final int jsonRpcCode;

    /** Parameter-validation error (defaults to ERR_INVALID_PARAMS). */
    McpException(String message) {
        super(message);
        this.jsonRpcCode = ERR_INVALID_PARAMS;
    }

    McpException(String message, int jsonRpcCode) {
        super(message);
        this.jsonRpcCode = jsonRpcCode;
    }

    /** Wraps a cause; defaults to ERR_INTERNAL_ERROR. */
    McpException(String message, Throwable cause) {
        super(message, cause);
        this.jsonRpcCode = ERR_INTERNAL_ERROR;
    }

    int getJsonRpcCode() {
        return jsonRpcCode;
    }
}
