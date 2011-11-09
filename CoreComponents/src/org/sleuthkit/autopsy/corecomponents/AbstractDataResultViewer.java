/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponents;

import java.awt.Cursor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JPanel;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerManager.Provider;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.datamodel.ContentNode;

/**
 * Holds commonalities between all DataResultViewers
 */
public abstract class AbstractDataResultViewer extends JPanel implements DataResultViewer, Provider, PropertyChangeListener {

    /**
     * Propagates changes in the current select node from the DataResultViewer to the DataContentTopComponent
     *
     * @param evt
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String changed = evt.getPropertyName();

        // change that should affect view
        if (changed.equals(ExplorerManager.PROP_SELECTED_NODES)
                || changed.equals(ExplorerManager.PROP_NODE_CHANGE)
                || changed.equals(ExplorerManager.PROP_EXPLORED_CONTEXT)
                || changed.equals(ExplorerManager.PROP_ROOT_CONTEXT)) {

            // change the cursor to "waiting cursor" for this operation
            this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            try {
                Node originalSelectedNode = this.getOriginalSelectedNode();

                // DataContent is designed to return only the default viewer
                DataContent dataContent = Lookup.getDefault().lookup(DataContent.class);

                if (originalSelectedNode != null && originalSelectedNode instanceof ContentNode) {
                    // there's a new/changed node to display
                    ContentNode newSelectedNode = (ContentNode) originalSelectedNode; // get the selected Node on the table
                    // push the node to default "DataContent"
                    dataContent.setNode(newSelectedNode);
                } else {
                    // clear the node viewer
                    dataContent.setNode(null);
                }
            } finally {
                this.setCursor(null);
            }
        }
    }

    /**
     * Gets the current node, stripping off any FilterNode that this class might
     * have wrapped it in.
     * @return
     */
    public abstract Node getOriginalSelectedNode();
}
