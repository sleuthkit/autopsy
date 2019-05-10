/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications.relationships;

import javax.swing.JPanel;
import org.openide.util.Lookup;

/**
 * Interface for Controls wishing to appear in the RelationshipBrowser tabPane.
 */
public interface RelationshipsViewer extends Lookup.Provider {

    /**
     * Returns the value to be displayed on the "tab"
     *
     * @return String display name
     */
    public String getDisplayName();

    /**
     * Returns the JPanel to be displayed in the RelationshipBrowser.
     *
     * @return JPanel to be displayed
     */
    public JPanel getPanel();

    /**
     * Sets current SelectionInfo allowing the panel to update accordingly.
     *
     * @param info SelectionInfo instance representing the currently selected
     *             accounts
     */
    public void setSelectionInfo(SelectionInfo info);
}
