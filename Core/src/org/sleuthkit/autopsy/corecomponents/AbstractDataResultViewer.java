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
import org.openide.explorer.ExplorerManager.Provider;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
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
    protected transient ExplorerManager em;
    /**
     * Content viewer to respond to selection events Either the main one, or
     * custom one if set
     */
    protected DataContent contentViewer;

    /**
     * This constructor is intended to allow an AbstractDataResultViewer to use
     * an ExplorerManager provided by a TopComponent, allowing Node selections
     * to be available to Actions via the action global context lookup when the
     * TopComponent has focus. The ExplorerManager must be present when the
     * object is constructed so that its child components can discover it using
     * the ExplorerManager.find() method.
     */
    public AbstractDataResultViewer(ExplorerManager explorerManager) {
        this.em = explorerManager;
        //DataContent is designed to return only the default viewer from lookup
        //use the default one unless set otherwise
        contentViewer = Lookup.getDefault().lookup(DataContent.class);
    }

    /**
     * This constructor can be used by AbstractDataResultViewers that do not
     * need to make Node selections available to Actions via the action global
     * context lookup.
     */
    public AbstractDataResultViewer() {
        this(new ExplorerManager());
    }

    @Override
    public void clearComponent() {
    }

    public Node getSelectedNode() {
        Node result = null;
        Node[] selectedNodes = this.getExplorerManager().getSelectedNodes();
        if (selectedNodes.length > 0) {
            result = selectedNodes[0];
        }
        return result;
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
        return this.em;
    }

    @Override
    public void setSelectedNodes(Node[] selected) {
        try {
            this.em.setSelectedNodes(selected);
        } catch (PropertyVetoException ex) {
            logger.log(Level.WARNING, "Couldn't set selected nodes.", ex); //NON-NLS
        }
    }

    @Override
    public void setContentViewer(DataContent contentViewer) {
        this.contentViewer = contentViewer;
    }
}
