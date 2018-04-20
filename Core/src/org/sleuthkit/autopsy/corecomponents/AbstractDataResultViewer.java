/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2018 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obt ain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.corecomponents;

import java.awt.Component;
import java.beans.PropertyVetoException;
import java.util.logging.Level;
import javax.swing.JPanel;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerManager.Provider;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Provides a JPanel base class that provides a default implementation of the
 * ExplorerManager.Provider interface and selected methods of
 * theDataResultViewer interface. The ExplorerManager.Provider interface is
 * implemented to supply an explorer manager to subclasses and their child
 * components. The explorer manager is expected to be the explorer manager of a
 * top component that exposes a lookup maintained by the explorer manager to the
 * actions global context. This connects the nodes displayed in the result
 * viewer to the actions global context. The explorer manager may be either
 * supplied during construction or discovered at runtime.
 */
abstract class AbstractDataResultViewer extends JPanel implements DataResultViewer, Provider {

    private static final Logger logger = Logger.getLogger(AbstractDataResultViewer.class.getName());
    private transient ExplorerManager explorerManager;

    /**
     * Constructs a JPanel base class instance that provides a default
     * implementation of selected methods of the DataResultViewer and
     * ExplorerManager.Provider interfaces. The explorer manager of this viewer
     * will be discovered at runtime.
     */
    AbstractDataResultViewer() {
    }

    /**
     * Constructs a JPanel base class instance that provides a default
     * implementation of selected methods of the DataResultViewer and
     * ExplorerManager.Provider interfaces.
     *
     * @param explorerManager
     */
    AbstractDataResultViewer(ExplorerManager explorerManager) {
        this.explorerManager = explorerManager;
    }

    @Override
    public void clearComponent() {
    }

    @Override
    public void expandNode(Node n) {
    }

    @Override
    public void resetComponent() {
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public ExplorerManager getExplorerManager() {
        if (this.explorerManager == null) {
            this.explorerManager = ExplorerManager.find(this);
        }
        return this.explorerManager;
    }

    @Override
    public void setSelectedNodes(Node[] selected) {
        try {
            this.getExplorerManager().setSelectedNodes(selected);
        } catch (PropertyVetoException ex) {
            logger.log(Level.WARNING, "Couldn't set selected nodes.", ex); //NON-NLS
        }
    }

    @Deprecated
    @Override
    public void setContentViewer(DataContent contentViewer) {
    }

}
