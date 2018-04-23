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
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * An abstract base class for an implementation of the result viewer interface
 * that is a JPanel that displays the child nodes of a given node using a
 * NetBeans explorer view as a child component. Such a result viewer should use
 * the explorer manager of an ancestor top component to connect the lookups of
 * the nodes displayed in the NetBeans explorer view to the actions global
 * context. This class handles some key aspects of working with the ancestor top
 * component's explorer manager.
 *
 * Instances of this class can be supplied with the top component's explorer
 * manager during construction, but the typical use case is for the result
 * viewer to find the ancestor top component's explorer manager at runtime.
 *
 * IMPORTANT: If the result viewer is going to find the ancestor top component's
 * explorer manager at runtime, the first call to the getExplorerManager method
 * of this class must be made AFTER the component hierarchy is fully
 * constructed.
 *
 */
public abstract class AbstractDataResultViewer extends JPanel implements DataResultViewer, ExplorerManager.Provider {

    private static final Logger logger = Logger.getLogger(AbstractDataResultViewer.class.getName());
    private transient ExplorerManager explorerManager;

    /**
     * Constructs an abstract base class for an implementation of the result
     * viewer interface that is a JPanel that displays the child nodes of the
     * given node using a NetBeans explorer view as a child component.
     *
     * @param explorerManager The explorer manager to use in the NetBeans
     *                        explorer view child component of this result
     *                        viewer, may be null. If null, the explorer manager
     *                        will be discovered the first time
     *                        getExplorerManager is called.
     */
    public AbstractDataResultViewer(ExplorerManager explorerManager) {
        this.explorerManager = explorerManager;
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
            logger.log(Level.SEVERE, "Couldn't set selected nodes", ex); //NON-NLS
        }
    }

    @Override
    public Component getComponent() {
        return this;
    }

}
