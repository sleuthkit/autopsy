/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers;

import java.awt.Component;
import org.openide.nodes.Node;

/**
 * Common interface implemented by artifact viewers.
 * 
 * An artifact viewer displays the artifact in a custom
 * layout panel suitable for the artifact type.
 * 
 */
public interface ArtifactContentViewer {
   
    /**
     * Called to display the artifact(s) associated with a content node. When
     * called with null, it must clear all references to previous nodes.
     *
     * @param selectedNode the node which is used to obtain and display the
     * artifacts.
     */
    void setNode(Node selectedNode);
    
    /**
     * Returns the panel.
     * 
     * @return display panel.
     */
    Component getComponent();

    /**
     * Checks whether the given node is supported by the viewer. This will be
     * used to enable or disable the tab for the viewer.
     *
     * @param node Node to check for support
     *
     * @return True if the node can be displayed / processed, else false
     */
    boolean isSupported(Node node);
   
}
