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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import static io.javalin.apibuilder.ApiBuilder.*;
import org.sleuthkit.autopsy.casemodule.Case;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the Javalin HTTP server instance. Starts at application startup and
 * runs for the lifetime of the application. The active case is updated via
 * updateCase() / clearCase() as cases are opened and closed. When no case is
 * open, tools/list still works and tools/call returns a clean error message.
 *
 * An ephemeral auth token is generated at server start and written to
 * %LOCALAPPDATA%\autopsy\mcp\mcp-token. It is not rotated between cases —
 * it is valid for the entire application session and removed on JVM exit.
 */
public class McpServer {

    private static final Logger logger = Logger.getLogger(McpServer.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_PORT = 8743;
    private static final String CONFIG_FILE_NAME = "mcp-config.properties";
    private static final String PORT_PROPERTY = "port";

    private final String authToken;
    private final McpProtocolHandler protocolHandler;
    private Javalin app;

    public McpServer() {
        this.authToken = generateToken();
        this.protocolHandler = new McpProtocolHandler();
    }

    /**
     * Called when a case is opened. Creates a TskQueryService for the case and
     * makes it available to the protocol handler.
     */
    public void updateCase(Case openedCase) {
        protocolHandler.setQueryService(
                new TskQueryService(openedCase.getSleuthkitCase(), openedCase.getDisplayName()));
    }

    /**
     * Called when a case is closed. Clears the query service so subsequent
     * tool calls return a "no case open" error. The HTTP server keeps running
     * and the token file remains valid so the STDIO wrapper stays connected.
     */
    public void clearCase() {
        protocolHandler.clearQueryService();
    }

    public void start() {
        app = Javalin.create(config -> {
            config.routes.apiBuilder(() -> {
                // Auth filter — every request must have valid Bearer token
                before(ctx -> {
                    String auth = ctx.header("Authorization");
                    if (auth == null || !auth.equals("Bearer " + authToken)) {
                        ctx.status(401).result("Unauthorized");
                        ctx.skipRemainingHandlers();
                    }
                });

                // MCP endpoint
                post("/mcp", this::handleMcpRequest);
            });
        });

        int port = readOrCreateConfigPort();
        app.start("127.0.0.1", port); // localhost only — never 0.0.0.0
        try {
            writeTokenFile();
        } catch (IOException ex) {
            app.stop();
            throw new RuntimeException("MCP server started but failed to write token file — aborting", ex);
        }
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
            logger.log(Level.SEVERE, "MCP protocol handler threw unexpectedly", ex);
            String msg = ex.getMessage() != null ? ex.getMessage() : "Internal error";
            Map<String, Object> errorDetail = new LinkedHashMap<>();
            errorDetail.put("code",    McpProtocolHandler.ERR_INTERNAL_ERROR);
            errorDetail.put("message", "Internal error");
            errorDetail.put("data",    msg);
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("jsonrpc", "2.0");
            envelope.put("error",   errorDetail);
            envelope.put("id",      null);
            String body;
            try {
                body = MAPPER.writeValueAsString(envelope);
            } catch (Exception jsonEx) {
                logger.log(Level.SEVERE, "Failed to serialize MCP error response", jsonEx);
                body = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":" + McpProtocolHandler.ERR_INTERNAL_ERROR + ",\"message\":\"Internal error\"},\"id\":null}";
            }
            ctx.status(500).contentType("application/json").result(body);
        }
    }

    /**
     * Reads the port from mcp-config.properties in the MCP directory. If the
     * file does not exist it is created with the default port so users have a
     * file they can edit. Returns the configured port, or DEFAULT_PORT if the
     * file cannot be read or contains an invalid value.
     */
    private int readOrCreateConfigPort() {
        Path configPath = getMcpDir().resolve(CONFIG_FILE_NAME);
        Properties props = new Properties();

        if (Files.exists(configPath)) {
            try (InputStream in = Files.newInputStream(configPath)) {
                props.load(in);
                String portStr = props.getProperty(PORT_PROPERTY, "").trim();
                int port = Integer.parseInt(portStr);
                if (port > 0 && port <= 65535) {
                    return port;
                }
                logger.log(Level.WARNING, "Invalid port in MCP config ({0}), using default {1}",
                        new Object[]{portStr, DEFAULT_PORT});
            } catch (IOException | NumberFormatException ex) {
                logger.log(Level.WARNING, "Could not read MCP config port, using default", ex);
            }
        } else {
            // Create the file so users know it exists and can edit it.
            try {
                Files.createDirectories(configPath.getParent());
                props.setProperty(PORT_PROPERTY, String.valueOf(DEFAULT_PORT));
                try (OutputStream out = Files.newOutputStream(configPath,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    props.store(out,
                            "Autopsy MCP server configuration\n"
                            + "# Change the port number below if it conflicts with another application.\n"
                            + "# Restart Autopsy after editing this file.");
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Could not create MCP config file, using default port", ex);
            }
        }
        return DEFAULT_PORT;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Path getMcpDir() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isEmpty()) {
            return Path.of(localAppData, "autopsy", "mcp");
        }
        return Path.of(System.getProperty("user.home"), "AppData", "Local", "autopsy", "mcp");
    }

    private Path getTokenPath() {
        return getMcpDir().resolve("mcp-token");
    }

    private void writeTokenFile() throws IOException {
        Path tokenPath = getTokenPath();
        Files.createDirectories(tokenPath.getParent());
        Files.writeString(tokenPath, authToken);
        tokenPath.toFile().deleteOnExit();
    }

    private void deleteTokenFile() {
        try {
            Files.deleteIfExists(getTokenPath());
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed to delete MCP token file", ex);
        }
    }
}
