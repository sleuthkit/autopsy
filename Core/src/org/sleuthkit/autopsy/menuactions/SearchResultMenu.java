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

package org.sleuthkit.autopsy.menuactions;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.openide.awt.DynamicMenuContent;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;

/**
 * Menu item lists DataResult tabs.
 */
 class SearchResultMenu extends JMenuItem implements DynamicMenuContent {

    SearchResultMenu(){

    }

    @Override
    public JComponent[] getMenuPresenters() {
        List<String> dataResultsIds = DataResultTopComponent.getActiveComponentIds();
        DirectoryTreeTopComponent directoryTree = DirectoryTreeTopComponent.findInstance();
        DataResultTopComponent directoryListing = directoryTree.getDirectoryListing();



        List<JComponent> menuItems = new ArrayList<JComponent>();
        
        // add the main "DirectoryListing"
        JMenuItem dlItem = new JMenuItem(directoryListing.getName());
        dlItem.addActionListener(new OpenTopComponentAction(directoryListing));
        dlItem.setEnabled(directoryTree.isOpened());

        menuItems.add(dlItem);


        // add search results if there are any
        if(dataResultsIds.size() > 0){
            JMenu submenu = new JMenu(NbBundle.getMessage(this.getClass(), "SearchResultMenu.menu.dataRes.text"));
            for(String resultTabId : dataResultsIds){
                JMenuItem item = new JMenuItem(resultTabId);
                item.addActionListener(new OpenTopComponentAction(resultTabId));
                submenu.add(item);
            }

            menuItems.add(submenu);
        }

        return menuItems.toArray(new JComponent[menuItems.size()]);
    }

    @Override
    public JComponent[] synchMenuPresenters(JComponent[] jcs) {
        return getMenuPresenters();
    }
}
