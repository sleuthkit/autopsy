/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.actions;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;
import org.sleuthkit.datamodel.OsAccount;

/**
 * An action that navigates to an os account.
 */
public class ViewOsAccountAction extends AbstractAction {

    private final OsAccount osAccount;

    /**
     * Main constructor.
     *
     * @param osAccount   The os account to navigate to in the action.
     * @param displayName The display name of the menu item.
     */
    public ViewOsAccountAction(OsAccount osAccount, String displayName) {
        super(displayName);
        this.osAccount = osAccount;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        SwingUtilities.invokeLater(() -> DirectoryTreeTopComponent.findInstance().viewOsAccount(osAccount));
    }
    
}
