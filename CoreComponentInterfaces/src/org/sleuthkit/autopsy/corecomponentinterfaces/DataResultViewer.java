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


package org.sleuthkit.autopsy.corecomponentinterfaces;

import java.awt.Component;
import org.openide.nodes.Node;

/**
 * Interface for the different viewers that show a set of nodes in the DataResult area. 
 * AbstractDataResultViewer has default implementations for the action handlers. 
 * 
 * @author jantonius
 */
public interface DataResultViewer {
    /**
     * Set the root node to display in this viewer. When called with null, must
     * clear all references to previous nodes.
     */
    public void setNode(Node selectedNode);

    /**
     * Gets the title of this viewer
     */
    public String getTitle();

    /**
     * Get a new instance of DataResultViewer
     */
    public DataResultViewer getInstance();
    
    /**
     * Get Component to display this DataResultViewer
     */
    public Component getComponent();

    /**
     * Resets the viewer.
     */
    public void resetComponent();

    /**
     * Frees the objects that have been allocated by this viewer, in
     * preparation for permanently disposing of it.
     */
    public void clearComponent();
    
    /**
     * Expand node, if supported by the viewed
     * @param n Node to expand
     */
    public void expandNode(Node n);
    
    /**
     * Select the given node array
     */
    public void setSelectedNodes(Node[] selected);
}
