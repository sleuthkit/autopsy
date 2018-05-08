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

import java.awt.Component;
import org.openide.nodes.Node;

/**
 * An interface for result viewers. A result viewer uses a Swing Component to
 * provide a view of the application data represented by a NetBeans Node passed
 * to it via its setNode method.
 *
 * Result viewers are most commonly presented as a tab in a result view panel
 * (DataResultPanel) inside a result view top component (DataResultTopComponent)
 * that is docked into the upper right hand side (editor mode) of the main
 * application window.
 *
 * A result viewer is typically a JPanel that displays the child nodes of the
 * given node using a NetBeans explorer view child component. Such a result
 * viewer should use the explorer manager of the ancestor top component to
 * connect the lookups of the nodes displayed in the NetBeans explorer view to
 * the actions global context. It is strongly recommended that this type of
 * result viewer is implemented by extending the abstract base class
 * AbstractDataResultViewer, which will handle some key aspects of working with
 * the ancestor top component's explorer manager.
 *
 * This interface is an extension point, so classes that implement it should
 * provide an appropriate ServiceProvider annotation.
 */
public interface DataResultViewer {

    /**
     * Creates a new instance of this result viewer, which allows the
     * application to use the capability provided by this result viewer in more
     * than one result view. This is done by using the default instance of this
     * result viewer as a "factory" for creating other instances.
     */
    public DataResultViewer createInstance();

    /**
     * Indicates whether this result viewer is able to provide a meaningful view
     * of the application data represented by a given node. Typically, indicates
     * whether or not this result viewer can use the given node as the root node
     * of its child explorer view component.
     *
     * @param node The node.
     *
     * @return True or false.
     */
    public boolean isSupported(Node node);

    /**
     * Sets the node for which this result viewer should provide a view of the
     * underlying application data. Typically, this means using the given node
     * as the root node of this result viewer's child explorer view component.
     *
     * @param node The node, may be null. If null, the call to this method is
     *             equivalent to a call to resetComponent.
     */
    public void setNode(Node node);

    /**
     * Requests selection of the given child nodes of the node passed to
     * setNode. This method should be implemented as a no-op for result viewers
     * that do not display the child nodes of a given root node using a NetBeans
     * explorer view set up to use a given explorer manager.
     *
     * @param selectedNodes The child nodes to select.
     */
    default public void setSelectedNodes(Node[] selectedNodes) {
    }

    /**
     * Gets the title of this result viewer.
     *
     * @return The title.
     */
    public String getTitle();

    /**
     * Gets the Swing component for this viewer.
     *
     * @return The component.
     */
    public Component getComponent();

    /**
     * Resets the state of the Swing component for this viewer to its default
     * state.
     */
    default public void resetComponent() {
    }

    /**
     * Frees any resources tha have been allocated by this result viewer, in
     * preparation for permanently disposing of it.
     */
    default public void clearComponent() {
    }

    /**
     * Sets the node for which this result viewer should provide a view of the
     * underlying application data model object, and expands the node.
     *
     * @param node The node.
     *
     * @deprecated This API is not used by the application.
     */
    @Deprecated
    default public void expandNode(Node node) {
    }

    /**
     * Sets a custom content viewer to which nodes selected in this result
     * viewer should be pushed via DataContent.setNode.
     *
     * @param contentViewer The content viewer.
     *
     * @deprecated This API is not used by the application.
     */
    @Deprecated
    default public void setContentViewer(DataContent contentViewer) {
    }

}
