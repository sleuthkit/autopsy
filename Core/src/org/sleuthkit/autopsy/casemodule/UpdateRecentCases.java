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

    int length;
    static boolean hasRecentCase = false;

    /** the constructor */
    UpdateRecentCases(){
        length = RecentCases.LENGTH;
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
        JComponent[] comps = new JComponent[length + 2]; // + 2 for separator and clear menu

        // if it has the recent menus, add them to the component list
        for (int i = 0; i < length; i++) {
            if((!caseName[i].equals(""))){
                JMenuItem menuItem = new JMenuItem(caseName[i]);
                menuItem.setActionCommand(caseName[i].toUpperCase());
                menuItem.addActionListener(new RecentItems(caseName[i], casePath[i]));
                comps[i] = menuItem;
                hasRecentCase = hasRecentCase || true;
            }
        }

        // if it has recent case, create clear menu
        if(hasRecentCase){
            comps[length] = new JSeparator();
            JMenuItem clearMenu = new JMenuItem(
                    NbBundle.getMessage(this.getClass(), "UpdateRecentCases.menuItem.clearRecentCases.text"));
            clearMenu.addActionListener(SystemAction.get(RecentCases.class));
            comps[length+1] = clearMenu;
        }
        // otherwise, just create a disabled empty menu
        else{
            comps = new JComponent[1];
            JMenuItem emptyMenu = new JMenuItem(NbBundle.getMessage(this.getClass(), "UpdateRecentCases.menuItem.empty"));
            emptyMenu.addActionListener(new RecentItems("", ""));
            comps[0] = emptyMenu;
            comps[0].setEnabled(false);
        }
        return comps;
    }

    /**
     * Updates main menu presenters. This method is called only by the main menu processing.
     *
     * @param jcs    the previously used menu items returned by previous call to getMenuPresenters() or synchMenuPresenters()
     * @return menu  a new set of items to show in menu. Can be either an updated old set of instances or a completely new one.
     */
    @Override
    public JComponent[] synchMenuPresenters(JComponent[] jcs) {
        return getMenuPresenters();
    }
}