/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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

package org.sleuthkit.autopsy.corecomponentinterfaces;

import java.awt.Component;
import org.openide.nodes.Node;

/**
 * Interface for the viewers that show a set of nodes in the data result mode 
 * (area). Note that the AbstractDataResultViewer class provides a default 
 * implementation of this interface. 
 */
public interface DataResultViewer {
    /**
     * Sets the root node for the viewer to display. May be called with null
     * to indicate there is currently no root node to display.
     */
    public void setNode(Node selectedNode);

    /**
     * Gets the title of the viewer.
     */
    public String getTitle();

    /**
     * Gets a new instance of the viewer.
     */
    public DataResultViewer createInstance();
    
    /**
     * Gets the visual component (e.g., a JPanel) of the viewer.
     */
    public Component getComponent();

    /**
     * Resets the display of the viewer to an empty state.
     */
    public void resetComponent();

    /**
     * Frees the objects that have been allocated by the viewer, in
     * preparation for permanently disposing of it.
     */
    public void clearComponent();
    
    /**
     * Directs the viewer to expand the indicated node, if the viewer supports 
     * Node expansion.
     */
    public void expandNode(Node node);
    
    /**
     * Notifies the viewer of a node selection event.
     */
    public void setSelectedNodes(Node[] selectedNodes);
    
    /**
     * Returns true if the viewer supports displaying the indicated node, false
     * otherwise.
     */
    public boolean isSupported(Node selectedNode);

    /**
     * Sets a custom content viewer (i.e., a data content mode (area) component) 
     * to which the viewer is expected to push node selection events.
     * @deprecated
     */
    @Deprecated
    public void setContentViewer(DataContent contentViewer);
}
