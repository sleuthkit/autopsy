/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import org.openide.awt.DynamicMenuContent;
import org.openide.util.NbBundle;
import org.openide.util.actions.SystemAction;

/**
 * This class is used to change / update the list of recent cases dynamically.
 */
class UpdateRecentCases extends JMenuItem implements DynamicMenuContent {

    private static final long serialVersionUID = 1L;
    private static int NUM_CASES_TO_DISPLAY;
    private static boolean hasRecentCase = false;

    /**
     * the constructor
     */
    UpdateRecentCases() {
        // display last 5 cases.
        NUM_CASES_TO_DISPLAY = 5;
    }

    static void setHasRecentCase(boolean value) {
        hasRecentCase = value;
    }
    
    /**
     * Creates main menu/popup menu items. Null values will be later replaced by
     * JSeparators. This method is called for popups and for menus. It's called
     * each time a popup menu is constructed and just once for the main menu.
     * Main menu updates happen through the synchMenuPresenters() method.
     *
     * @return
     */
    @Override
    public JComponent[] getMenuPresenters() {
        String[] caseName = RecentCases.getInstance().getRecentCaseNames();
        String[] casePath = RecentCases.getInstance().getRecentCasePaths();
        JComponent[] comps = new JComponent[NUM_CASES_TO_DISPLAY + 2]; // + 2 for separator and clear menu

        // if it has the recent menus, add them to the component list
        for (int i = 0; i < NUM_CASES_TO_DISPLAY; i++) {
            if ((!caseName[i].equals(""))) {
                JMenuItem menuItem = new JMenuItem(caseName[i]);
                menuItem.setActionCommand(caseName[i].toUpperCase());
                menuItem.addActionListener(new RecentItems(caseName[i], casePath[i]));
                comps[i] = menuItem;
                hasRecentCase = true;
            }
        }

        // if it has recent case, create clear menu
        if (hasRecentCase) {
            comps[NUM_CASES_TO_DISPLAY] = new JSeparator();
            JMenuItem clearMenu = new JMenuItem(
                    NbBundle.getMessage(UpdateRecentCases.class, "UpdateRecentCases.menuItem.clearRecentCases.text"));
            clearMenu.addActionListener(SystemAction.get(RecentCases.class));
            comps[NUM_CASES_TO_DISPLAY + 1] = clearMenu;
        } // otherwise, just create a disabled empty menu
        else {
            comps = new JComponent[1];
            JMenuItem emptyMenu = new JMenuItem(NbBundle.getMessage(UpdateRecentCases.class, "UpdateRecentCases.menuItem.empty"));
            emptyMenu.addActionListener(new RecentItems("", ""));
            comps[0] = emptyMenu;
            comps[0].setEnabled(false);
        }
        return comps;
    }

    /**
     * Updates the Recent Cases menu items. 
     *
     * @param menuItems A set of Recent Case menu items to be updated.
     *
     * @return A updated set of recent case menu items to show in the Recent
     *         Cases menu.
     */
    @Override
    public JComponent[] synchMenuPresenters(JComponent[] menuItems) {
        return getMenuPresenters();
    }
}
