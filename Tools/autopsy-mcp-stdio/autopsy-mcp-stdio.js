/**
 * Autopsy MCP STDIO wrapper.
 *
 * Claude Desktop and Claude Code launch this process over stdio.
 * It dynamically proxies tools/list and tools/call to the Autopsy HTTP MCP
 * server running on 127.0.0.1:8765, reading the ephemeral auth token that
 * Autopsy writes when a case is opened.
 *
 * No build step required — run directly with Node.js:
 *   node autopsy-mcp-stdio.js
 */

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { ListToolsRequestSchema, CallToolRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import * as fs from "fs";
import * as path from "path";
import * as os from "os";

// ---------------------------------------------------------------------------
// Token + HTTP proxy
// ---------------------------------------------------------------------------

function readToken() {
    const tokenPath = path.join(os.homedir(), ".autopsy", "mcp-token");
    try {
        return fs.readFileSync(tokenPath, "utf8").trim();
    } catch {
        throw new Error(
            "No Autopsy case is currently open, or the MCP token file is missing. " +
            "Please open a case in Autopsy first."
        );
    }
}

async function callJava(method, params) {
    const token = readToken(); // fresh read each call — handles case reopen
    const res = await fetch("http://127.0.0.1:8765/mcp", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${token}`
        },
        body: JSON.stringify({ jsonrpc: "2.0", id: "1", method, params })
    });
    if (!res.ok) {
        throw new Error(
            `Autopsy HTTP error ${res.status}. Is Autopsy running with a case open?`
        );
    }
    const data = await res.json();
    if (data.error) {
        throw new Error(`Autopsy MCP error: ${data.error.message}`);
    }
    return data.result;
}

// ---------------------------------------------------------------------------
// MCP server — proxies everything dynamically, no hardcoded tool definitions
// ---------------------------------------------------------------------------

const server = new Server(
    { name: "autopsy", version: "1.0.0" },
    { capabilities: { tools: {} } }
);

// Proxy tools/list straight through — tool definitions live only in Java
server.setRequestHandler(ListToolsRequestSchema, async () => {
    const tools = await callJava("tools/list", {});
    return { tools };
});

// Proxy tools/call straight through
server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const result = await callJava("tools/call", {
        name: request.params.name,
        arguments: request.params.arguments ?? {}
    });
    return {
        content: [{ type: "text", text: JSON.stringify(result, null, 2) }]
    };
});

// ---------------------------------------------------------------------------
// Start
// ---------------------------------------------------------------------------

(async () => {
    const transport = new StdioServerTransport();
    await server.connect(transport);
})();
