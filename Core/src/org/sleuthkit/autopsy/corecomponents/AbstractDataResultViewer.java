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
import java.awt.Cursor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.io.IOException;
import java.util.logging.Level;
import javax.swing.JPanel;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerManager.Provider;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Holds commonalities between all DataResultViewers, such as:
 * - Pushes selection to DataContentViewers
 */
public abstract class AbstractDataResultViewer extends JPanel implements DataResultViewer, Provider {

    private static final Logger logger = Logger.getLogger(AbstractDataResultViewer.class.getName());
    protected transient ExplorerManager em;
    private PropertyChangeListener nodeSelListener;
    /**
     * Content viewer to respond to selection events Either the main one, or
     * custom one if set
     */
    protected DataContent contentViewer;

    public AbstractDataResultViewer(ExplorerManager explorerManager) {
        this.em = explorerManager;
        initialize();
    }
    
    public AbstractDataResultViewer() {
        em = new ExplorerManager();
        initialize();
    }

    private void initialize() {
        //DataContent is designed to return only the default viewer from lookup
        //use the default one unless set otherwise
        contentViewer = Lookup.getDefault().lookup(DataContent.class);

        //property listener to send nodes to content viewer    
        nodeSelListener = new PropertyChangeListener() {
            /**
             * Propagates changes in the current select node from the
             * DataResultViewer to the DataContentTopComponent
             */
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (!Case.isCaseOpen()) {
                    //handle in-between condition when case is being closed
                    //and legacy selection events are pumped
                    return;
                }

                String changed = evt.getPropertyName();

                // change that should affect view
                if (changed.equals(ExplorerManager.PROP_SELECTED_NODES)) {
                    //|| changed.equals(ExplorerManager.PROP_NODE_CHANGE)
                    //|| changed.equals(ExplorerManager.PROP_EXPLORED_CONTEXT)
                    //|| changed.equals(ExplorerManager.PROP_ROOT_CONTEXT)) {

                    // change the cursor to "waiting cursor" for this operation
                    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    try {
                        Node[] selectedNodes = getExplorerManager().getSelectedNodes();
                        if (selectedNodes.length == 1) {
                            nodeSelected(selectedNodes[0]);
                            
                            // there's a new/changed node to display
                            // push the node to default "DataContent"
                            //TODO only the active viewer should be calling setNode
                            //not all of them, otherwise it results in multiple setNode() invocations
                            //alternative is to use a single instance of the event listener
                            //, per top component and not the tab perhaps
                            contentViewer.setNode(selectedNodes[0]);
                        } else {
                            // clear the node viewer
                            contentViewer.setNode(null);
                        }
                    } finally {
                        setCursor(null);
                    }
                }

                /*
                 else if (changed.equals(ExplorerManager.PROP_NODE_CHANGE) ) {
                 }
                 else if (changed.equals(ExplorerManager.PROP_EXPLORED_CONTEXT)) {
                 }
                 else if (changed.equals(ExplorerManager.PROP_ROOT_CONTEXT)) {
                 }
                 */
            }
        };

        em.addPropertyChangeListener(nodeSelListener);        
    }
    
    @Override
    public void clearComponent() {
        em.removePropertyChangeListener(nodeSelListener);
    }

    @Deprecated    
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

    /**
     * Called when a new node has been selected in the result viewer Can update
     * the viewer, etc.
     *
     * @param selectedNode the new node currently selected
     */
    public abstract void nodeSelected(Node selectedNode);

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
            logger.log(Level.WARNING, "Couldn't set selected nodes.", ex);
        }
    }

    @Override
    public void setContentViewer(DataContent contentViewer) {
        this.contentViewer = contentViewer;
    }
}
