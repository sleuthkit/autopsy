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
 */
@OnStart
public class AutopsyMcpModule implements Runnable {

    private static final Logger logger = Logger.getLogger(AutopsyMcpModule.class.getName());

    private volatile McpServer mcpServer;

    @Override
    public void run() {
        if (!McpOptionsPanel.isMcpEnabled()) {
            return;
        }
        try {
            McpServer server = new McpServer();
            server.start();
            mcpServer = server;
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Failed to start Autopsy MCP server", ex);
            return;
        }
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), this::onCaseEvent);
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
