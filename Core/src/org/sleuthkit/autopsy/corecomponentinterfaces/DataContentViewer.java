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
 * Interface that DataContentViewer modules must implement. These modules
 * analyze an individual file that the user has selected and display results in
 * some form of JPanel. We find it easiest to use the NetBeans IDE to first make
 * a "JPanel Form" class and then have it implement DataContentViewer. This
 * allows you to easily use the UI builder for the layout.
 * 
 * DataContentViewer panels should handle their own vertical scrolling, the horizontal 
 * scrolling when under their panel's preferred size will be handled by the DataContentPanel
 * which contains them.
 */
public interface DataContentViewer {

    /**
     * Autopsy will call this when this panel is focused with the file that
     * should be analyzed. When called with null, must clear all references to
     * previous nodes.
     */
    public void setNode(Node selectedNode);

    /**
     * Returns the title of this viewer to display in the tab.
     */
    public String getTitle();

    /**
     * Returns a short description of this viewer to use as a tool tip for its
     * tab.
     */
    public String getToolTip();

    /**
     * Create and return a new instance of your viewer. The reason that this is
     * needed is because the specific viewer modules will be found via NetBeans
     * Lookup and the type will only be DataContentViewer. This method is used
     * to get an instance of your specific type.
     *
     * @returns A new instance of the viewer
     */
    public DataContentViewer createInstance();

    /**
     * Return the Swing Component to display. Implementations of this method
     * that extend JPanel and do a 'return this;'. Otherwise return an internal
     * instance of the JPanel.
     */
    public Component getComponent();

    /**
     * Resets the contents of the viewer / component.
     */
    public void resetComponent();

    /**
     * Checks whether the given node is supported by the viewer. This will be
     * used to enable or disable the tab for the viewer.
     *
     * @param node Node to check for support
     *
     * @return True if the node can be displayed / processed, else false
     */
    public boolean isSupported(Node node);

    /**
     * Checks whether the given viewer is preferred for the Node. This is a bit
     * subjective, but the idea is that Autopsy wants to display the most
     * relevant tab. The more generic the viewer, the lower the return value
     * should be. This will only be called on viewers that support the given
     * node.
     *
     * @param node Node to check for preference
     *
     * @return an int (0-10) higher return means the viewer has higher priority
     *         0 means not supported 1 to 2 means the module will display all
     *         file types (such as the hex viewer) 3-10 are prioritized by
     *         Content viewer developer. Modules that operate on very few file
     *         types should be towards 10.
     */
    public int isPreferred(Node node);
}
