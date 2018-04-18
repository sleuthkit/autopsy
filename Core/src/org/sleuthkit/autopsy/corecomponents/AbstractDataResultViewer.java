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
 * This class provides a default implementation of selected methods of the
 * DataResultViewer interface. Derived classes will be Swing JPanel objects.
 * Additionally, the ExplorerManager.Provider interface is implemented to supply
 * an ExplorerManager to derived classes and their child components.
 */
abstract class AbstractDataResultViewer extends JPanel implements DataResultViewer, Provider {

    private static final Logger logger = Logger.getLogger(AbstractDataResultViewer.class.getName());
    private transient ExplorerManager explorerManager;

    AbstractDataResultViewer() {
    }    
    
    /**
     * This constructor is intended to allow an AbstractDataResultViewer to use
     * an ExplorerManager provided by a TopComponent, allowing Node selections
     * to be available to Actions via the action global context lookup when the
     * TopComponent has focus. The ExplorerManager must be present when the
     * object is constructed so that its child components can discover it using
     * the ExplorerManager.find() method.
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
