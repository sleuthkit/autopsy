/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline;

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import javax.swing.JPanel;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;
import org.sleuthkit.autopsy.corecomponents.DataContentPanel;

/**
 * Panel that wraps a DataContentPanel and implements ExplorerManager.Provider.
 * This allows the explorer manager found by the DataContentPanel to be
 * controlled easily.
 *
 * @see org.sleuthkit.autopsy.communications.MessageDataContent for another
 * solution to a very similar problem.
 */
final class DataContentExplorerPanel extends JPanel implements ExplorerManager.Provider, DataContent {

    private final ExplorerManager explorerManager = new ExplorerManager();
    private final DataContentPanel wrapped;

    DataContentExplorerPanel() {
        super(new BorderLayout());
        wrapped = DataContentPanel.createInstance();
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
    }

    @Override
    public void setNode(Node selectedNode) {
        wrapped.setNode(selectedNode);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        wrapped.propertyChange(evt);
    }

    /**
     * Initialize the contents of this panel for use. Specifically add the
     * wrapped DataContentPanel to the AWT/Swing containment hierarchy. This
     * will trigger the addNotify() method of the embeded Message
     * MessageContentViewer causing it to look for a ExplorerManager; it should
     * find the one provided by this DataContentExplorerPanel.
     */
    void initialize() {
        add(wrapped, BorderLayout.CENTER);
    }
}
