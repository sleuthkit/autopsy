/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2018 Basis Technology Corp.
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

import java.util.List;
import org.openide.nodes.Node;

/**
 * An interface for result view components. A result view component provides
 * multiple views of the application data represented by a given NetBeans Node.
 * The differing views of the node are supplied by a collection of result
 * viewers (implementations of the DataResultViewer interface).
 *
 * A typical implementation of this interface are the NetBeans TopComponents
 * (DataResultTopComponents) that use a child result view component
 * (DataResultPanel) for displaying their result viewers, and are docked into
 * the upper right hand side (editor mode) of the main application window.
 */
public interface DataResult {

    /**
     * Sets the node for which this result view component should provide
     * multiple views of the underlying application data.
     *
     * @param node The node, may be null. If null, the call to this method is
     *             equivalent to a call to resetComponent on this result view
     *             component's result viewers.
     */
    public void setNode(Node node);

    /**
     * Gets the preferred identifier for this result view panel in the window
     * system.
     *
     * @return The preferred identifier.
     */
    public String getPreferredID();

    /**
     * Sets the title of this result view component.
     *
     * @param title The title.
     */
    public void setTitle(String title);

    /**
     * Sets the descriptive text about the source of the nodes displayed in this
     * result view component.
     *
     * @param pathText The text to display.
     */
    public void setPath(String pathText);

    /**
     * Gets whether or not this result view panel is the "main" result view
     * panel used to view the child nodes of a node selected in the application
     * tree view (DirectoryTreeTopComponent) that is normally docked into the
     * left hand side of the main window.
     *
     * @return True or false.
     */
    public boolean isMain();

    /**
     * Get child viewers within this DataResult
     *
     * @return
     */
    public List<DataResultViewer> getViewers();
}
