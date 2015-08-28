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

import java.util.List;
import org.openide.nodes.Node;

/**
 * The interface for the "top right component" window.
 *
 */
public interface DataResult {

    /**
     * Sets the "selected" node in this class.
     */
    public void setNode(Node selectedNode);

    /**
     * Gets the unique TopComponent ID of this class.
     *
     * @return preferredID the unique ID
     */
    public String getPreferredID();

    /**
     * Sets the title of this TopComponent
     *
     * @param title the given title (String)
     */
    public void setTitle(String title);

    /**
     * Sets the descriptive context text at the top of the pane.
     *
     * @param pathText Descriptive text giving context for the current results
     */
    public void setPath(String pathText);

    /**
     * Checks if this is the main (uncloseable) instance of DataResult
     *
     * @return true if it is the main instance, otherwise false
     */
    public boolean isMain();

    /**
     * Get child viewers within this DataResult
     *
     * @return
     */
    public List<DataResultViewer> getViewers();
}
