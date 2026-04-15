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
package org.sleuthkit.autopsy.mcp;

import java.beans.PropertyChangeEvent;
import java.util.EnumSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.modules.OnStart;
import org.sleuthkit.autopsy.casemodule.Case;

/**
 * Starts the MCP HTTP server at application startup (if enabled) and keeps it
 * running for the lifetime of the application. Case open/close events update
 * the active case reference inside the server — the HTTP listener itself never
 * stops. When no case is open, tools/list still works but tools/call returns a
 * clean "no case open" error message.
 *
 * A static singleton is held so that the options panel can enable or disable
 * the server at runtime without requiring an application restart.
 */
@OnStart
public class AutopsyMcpModule implements Runnable {

    private static final Logger logger = Logger.getLogger(AutopsyMcpModule.class.getName());

    private static AutopsyMcpModule instance;

    private volatile McpServer mcpServer;
    private volatile boolean caseListenerRegistered = false;

    /**
     * Returns the singleton instance created by the {@code @OnStart} machinery,
     * or {@code null} if the module has not yet been initialized.
     */
    public static AutopsyMcpModule getInstance() {
        return instance;
    }

    @Override
    public void run() {
        instance = this;
        if (McpOptionsPanel.isMcpEnabled()) {
            try {
                startServer();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Failed to start Autopsy MCP server", ex);
            }
        }
    }

    /**
     * Starts the MCP server and wires up case-event tracking. No-op if the
     * server is already running.
     *
     * @throws Exception if the server fails to bind or write its token file
     */
    public synchronized void enableServer() throws Exception {
        if (mcpServer == null) {
            startServer();
        }
    }

    /**
     * Stops the MCP server and removes the token file. No-op if the server is
     * not running.
     */
    public synchronized void disableServer() {
        McpServer server = mcpServer;
        mcpServer = null;
        if (server != null) {
            server.stop();
        }
    }

    /**
     * Starts the server and wires up case-event tracking. Propagates any
     * startup exception to the caller so the UI can report it.
     */
    private synchronized void startServer() throws Exception {
        McpServer server = new McpServer();
        server.start();   // throws on port-bind failure or token-file error
        mcpServer = server;

        // Register the case listener once; it guards against a null server internally.
        if (!caseListenerRegistered) {
            Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), this::onCaseEvent);
            caseListenerRegistered = true;
        }

        // Seed with any case already open (e.g. auto-reopen on launch, or enabled mid-session).
        try {
            mcpServer.updateCase(Case.getCurrentCase());
        } catch (IllegalStateException ex) {
            // No case open — normal state, nothing to seed.
        }
    }

    private void onCaseEvent(PropertyChangeEvent evt) {
        McpServer server = mcpServer;
        if (server == null) {
            return;
        }
        if (evt.getNewValue() != null) {
            // Case opened — wire up the query service.
            server.updateCase((Case) evt.getNewValue());
        } else {
            // Case closed — clear the query service; server keeps listening.
            server.clearCase();
        }
    }
}
