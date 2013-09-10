/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * This class provides a default implementation of the DataResultViewer interface.
 * Derived classes will be Swing JPanel objects. Additionally, the ExplorerManager.Provider 
 * interface is implemented to allow derived classes and their child components 
 * to obtain an ExplorerManager.
 */
public abstract class AbstractDataResultViewer extends JPanel implements DataResultViewer, ExplorerManager.Provider {
    private static final Logger logger = Logger.getLogger(AbstractDataResultViewer.class.getName());
    private ExplorerManager explorerManager;

    /**
     * This constructor is intended to allow an AbstractDataResultViewer to use
     * an ExplorerManager provided by a DataResultTopComponent, allowing Node
     * selections to be available to Actions via the action global context lookup when 
     * the DataResultTopComponent has focus. The ExplorerManager must be present
     * when the object is constructed so that its child components can discover
     * it using the ExplorerManager.find() method. 
     */
    public AbstractDataResultViewer(ExplorerManager explorerManager) {
        this.explorerManager = explorerManager;
    }
    
    /**
     * This constructor can be used by AbstractDataResultViewers that do not
     * need to make Node selections available to Actions via the action global 
     * context lookup.
     */
    public AbstractDataResultViewer() {
        explorerManager = new ExplorerManager();
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
    public void setSelectedNodes(Node[] selected) {
        try {
            explorerManager.setSelectedNodes(selected);
        } 
        catch (PropertyVetoException ex) {
            logger.log(Level.WARNING, "Couldn't set selected nodes", ex);
        }
    }

    @Deprecated    
    @Override
    public void setContentViewer(DataContent contentViewer) {
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
    }
        
    /**
     * Returns the first of the selected nodes in the viewer, or null if no nodes are selected.
     * @deprecated
     */
    @Deprecated    
    public Node getSelectedNode() {
        Node result = null;
        Node[] selectedNodes = this.getExplorerManager().getSelectedNodes();
        if (selectedNodes.length > 0) {
            result = selectedNodes[0];
        }
        return result;
    }
    
    /**
     * Notifies the viewer of the first node in a single selection event.
     * @deprecated
     */
    @Deprecated    
    public abstract void nodeSelected(Node selectedNode);    
}
