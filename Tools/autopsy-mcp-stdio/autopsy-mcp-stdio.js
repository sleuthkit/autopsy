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
// Logging
// ---------------------------------------------------------------------------

const LOG_PATH = path.join(os.homedir(), ".autopsy", "mcp-stdio.log");
const MAX_LOG_BYTES = 1 * 1024 * 1024; // 1 MB — rotate when exceeded

function log(level, message) {
    if (level !== "ERROR") return;
    try {
        // Rotate if the log has grown too large
        try {
            if (fs.statSync(LOG_PATH).size > MAX_LOG_BYTES) {
                fs.renameSync(LOG_PATH, LOG_PATH + ".1");
            }
        } catch { /* file doesn't exist yet — that's fine */ }

        const line = `${new Date().toISOString()} [${level}] ${message}\n`;
        fs.appendFileSync(LOG_PATH, line, "utf8");
    } catch { /* never let logging crash the process */ }
}

// ---------------------------------------------------------------------------
// --test mode: verify the server is reachable, token exists, and tools load
// ---------------------------------------------------------------------------

async function runTest() {
    const checks = [];
    let token;

    // 1. Token file
    const tokenPath = path.join(os.homedir(), ".autopsy", "mcp-token");
    try {
        token = fs.readFileSync(tokenPath, "utf8").trim();
        checks.push(`  [OK] Token file found: ${tokenPath}`);
        checks.push(`       Token: ${token.substring(0, 8)}...`);
    } catch {
        checks.push(`  [FAIL] Token file missing: ${tokenPath}`);
        checks.push(`         Open a case in Autopsy first.`);
        printTestResults(checks, false);
        return;
    }

    // 2. HTTP reachability
    let data;
    try {
        const res = await fetch("http://127.0.0.1:8765/mcp", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${token}`
            },
            body: JSON.stringify({ jsonrpc: "2.0", id: "1", method: "tools/list", params: {} })
        });
        if (!res.ok) {
            checks.push(`  [FAIL] HTTP ${res.status} from Autopsy MCP server.`);
            if (res.status === 401) {
                checks.push(`         Token is invalid — try reopening the case.`);
            } else {
                checks.push(`         Is Autopsy running with a case open?`);
            }
            printTestResults(checks, false);
            return;
        }
        checks.push(`  [OK] HTTP server reachable at 127.0.0.1:8765`);
        data = await res.json();
    } catch (err) {
        checks.push(`  [FAIL] Could not connect to 127.0.0.1:8765: ${err.message}`);
        checks.push(`         Is Autopsy running with the MCP server enabled?`);
        printTestResults(checks, false);
        return;
    }

    // 3. MCP response
    if (data.error) {
        checks.push(`  [FAIL] MCP error: ${data.error.message}`);
        printTestResults(checks, false);
        return;
    }
    checks.push(`  [OK] Valid MCP JSON-RPC response received`);

    // 4. Tools list
    const tools = data.result;
    if (!Array.isArray(tools) || tools.length === 0) {
        checks.push(`  [FAIL] tools/list returned no tools`);
        printTestResults(checks, false);
        return;
    }
    checks.push(`  [OK] ${tools.length} tools available:`);
    for (const t of tools) {
        checks.push(`       - ${t.name}`);
    }

    // 5. Case status — call get_case_summary to see if a case is open
    try {
        const caseRes = await fetch("http://127.0.0.1:8765/mcp", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${token}`
            },
            body: JSON.stringify({ jsonrpc: "2.0", id: "2", method: "tools/call",
                params: { name: "get_case_summary", arguments: {} } })
        });
        const caseData = await caseRes.json();
        if (caseData.error) {
            // MCP-level error — no case open
            checks.push(`  [--] No case is currently open`);
        } else {
            // Parse the text content from the tool result
            const text = caseData.result?.content?.[0]?.text;
            const summary = text ? JSON.parse(text) : null;
            const caseName = summary?.caseName ?? "(unknown)";
            checks.push(`  [OK] Case is open: ${caseName}`);
        }
    } catch {
        checks.push(`  [--] Could not determine case status`);
    }

    printTestResults(checks, true);
}

function printTestResults(checks, passed) {
    console.log("\nAutopsy MCP Server Test");
    console.log("=======================");
    for (const line of checks) console.log(line);
    console.log("");
    console.log(passed ? "Result: PASS" : "Result: FAIL");
    process.exit(passed ? 0 : 1);
}

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
    log("INFO", `-> ${method}`);
    const res = await fetch("http://127.0.0.1:8765/mcp", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${token}`
        },
        body: JSON.stringify({ jsonrpc: "2.0", id: "1", method, params })
    });
    if (!res.ok) {
        const msg = `Autopsy HTTP error ${res.status}. Is Autopsy running with a case open?`;
        log("ERROR", `<- ${method} HTTP ${res.status}`);
        throw new Error(msg);
    }
    const data = await res.json();
    if (data.error) {
        log("ERROR", `<- ${method} MCP error: ${data.error.message}`);
        throw new Error(`Autopsy MCP error: ${data.error.message}`);
    }
    log("INFO", `<- ${method} OK`);
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
    log("INFO", `tools/list returned ${tools.length} tools`);
    return { tools };
});

// Proxy tools/call straight through
server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const toolName = request.params.name;
    log("INFO", `tools/call ${toolName}`);
    try {
        const result = await callJava("tools/call", {
            name: toolName,
            arguments: request.params.arguments ?? {}
        });
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }]
        };
    } catch (err) {
        log("ERROR", `tools/call ${toolName} failed: ${err.message}`);
        throw err;
    }
});

// ---------------------------------------------------------------------------
// Start
// ---------------------------------------------------------------------------

if (process.argv.includes("--test")) {
    runTest();
} else {
    (async () => {
        log("INFO", `autopsy-mcp-stdio starting (pid ${process.pid})`);
        const transport = new StdioServerTransport();
        await server.connect(transport);
        log("INFO", "connected to stdio transport");
    })();
}
