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
 * Registers with the Autopsy case lifecycle and starts/stops the MCP server
 * when a case is opened or closed.
 *
 * Runs at application startup via @OnStart, then listens for Case.Events.CURRENT_CASE.
 */
@OnStart
public class AutopsyMcpModule implements Runnable {

    private static final Logger logger = Logger.getLogger(AutopsyMcpModule.class.getName());

    private volatile McpServer mcpServer;

    @Override
    public void run() {
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), this::onCaseEvent);
    }

    private void onCaseEvent(PropertyChangeEvent evt) {
        if (evt.getNewValue() != null) {
            // A case was opened — newValue is the Case object.
            Case openedCase = (Case) evt.getNewValue();
            try {
                McpServer server = new McpServer(openedCase);
                server.start();
                mcpServer = server;
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Failed to start Autopsy MCP server", ex);
            }
        } else {
            // A case was closed — newValue is null, oldValue is the closed Case.
            McpServer server = mcpServer;
            if (server != null) {
                server.stop();
                mcpServer = null;
            }
        }
    }
}
