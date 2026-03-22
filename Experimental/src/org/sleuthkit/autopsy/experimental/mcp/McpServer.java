/*
 * Autopsy 
 *
 * Copyright 2026 Sleuth Kit Labs
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

import io.javalin.Javalin;
import io.javalin.http.Context;
import org.sleuthkit.autopsy.casemodule.Case;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Owns the Javalin HTTP server instance. Starts on case open, stops on case close.
 * Generates an ephemeral per-session auth token written to ~/.autopsy/mcp-token.
 * Binds exclusively to 127.0.0.1 (never 0.0.0.0).
 */
public class McpServer {

    private static final int DEFAULT_PORT = 8765;

    private final String authToken;
    private final McpProtocolHandler protocolHandler;
    private Javalin app;

    public McpServer(Case currentCase) {
        this.authToken = generateToken();
        this.protocolHandler = new McpProtocolHandler(
                new TskQueryService(currentCase.getSleuthkitCase(), currentCase.getDisplayName()));
        writeTokenFile();
    }

    public void start() {
        app = Javalin.create(config -> {
            config.jetty.defaultHost = "127.0.0.1"; // localhost only — never 0.0.0.0
        });

        // Auth filter — every request must have valid Bearer token
        /* TODO @@@ ADD THIS BACK IN
        app.before(ctx -> {
            String auth = ctx.header("Authorization");
            if (auth == null || !auth.equals("Bearer " + authToken)) {
                ctx.status(401).result("Unauthorized");
                ctx.skipRemainingHandlers();
            }
        });*/

        // MCP endpoint
        app.post("/mcp", this::handleMcpRequest);

        // SSE endpoint for streaming (MCP spec)
        app.get("/mcp/sse", ctx -> {
            // TODO: implement SSE transport if needed
        });

        app.start(DEFAULT_PORT);
    }

    public void stop() {
        if (app != null) {
            app.stop();
            app = null;
        }
        deleteTokenFile();
    }

    private void handleMcpRequest(Context ctx) {
        try {
            String requestBody = ctx.body();
            String response = protocolHandler.handle(requestBody);
            ctx.contentType("application/json").result(response);
        } catch (Exception ex) {
            ctx.status(500).result("{\"error\": \"Internal server error\"}");
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Path getTokenPath() {
        return Path.of(System.getProperty("user.home"), ".autopsy", "mcp-token");
    }

    private void writeTokenFile() {
        try {
            Path tokenPath = getTokenPath();
            Files.createDirectories(tokenPath.getParent());
            Files.writeString(tokenPath, authToken);
            tokenPath.toFile().deleteOnExit();
        } catch (IOException ex) {
            // TODO: log
        }
    }

    private void deleteTokenFile() {
        try {
            Files.deleteIfExists(getTokenPath());
        } catch (IOException ex) {
            // TODO: log
        }
    }
}
