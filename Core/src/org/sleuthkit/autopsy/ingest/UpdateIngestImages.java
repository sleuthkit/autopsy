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
package org.sleuthkit.autopsy.ingest;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import org.openide.awt.DynamicMenuContent;

/**
 * This class is used to change / update the list of open images for ingest modules
 * dynamically.
 */
class UpdateIngestImages extends JMenuItem implements DynamicMenuContent {

     @Override
    public JComponent[] getMenuPresenters() {
        int length = 2;
        JComponent[] comps = new JComponent[length + 2]; // + 2 for separator and clear menu

        // if it has the recent menus, add them to the component list
        for (int i = 0; i < length; i++) {
            String action = "action " + i;
            JMenuItem menuItem = new JMenuItem(action);
            menuItem.setActionCommand(action.toUpperCase());
            comps[i] = menuItem;
        }

        // if it has recent case, create clear menu
        if (true) {
            comps[length] = new JSeparator();
            JMenuItem clearMenu = new JMenuItem("unclear");
            comps[length + 1] = clearMenu;
        } // otherwise, just create a disabled empty menu
        else {
            comps = new JComponent[1];
            JMenuItem emptyMenu = new JMenuItem("unempty");
            comps[0] = emptyMenu;
            comps[0].setEnabled(false);
        }
        return comps;
    }

    @Override
    public JComponent[] synchMenuPresenters(JComponent[] jcs) {
        return getMenuPresenters();
    }


}
