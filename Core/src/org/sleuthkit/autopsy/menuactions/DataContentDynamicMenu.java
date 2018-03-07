/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

import java.util.List;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.openide.awt.DynamicMenuContent;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;
import org.sleuthkit.autopsy.corecomponents.DataContentTopComponent;

/**
 *
 * @author jantonius
 */
class DataContentDynamicMenu extends JMenuItem implements DynamicMenuContent {

    @Override
    public JComponent[] getMenuPresenters() {
        List<DataContentTopComponent> newWindowLists = DataContentTopComponent.getNewWindowList();

        // Get DataContent provider to include in the menu
        int totalItems = newWindowLists.size() > 0 ? 2 : 1;
        JComponent[] comps = new JComponent[totalItems];
        int counter = 0;

        TopComponent contentWin = DataContentTopComponent.findInstance();
        JMenuItem defaultItem = new JMenuItem(contentWin.getName()); // set the main name

        defaultItem.addActionListener(new OpenTopComponentAction(contentWin));

        try {
            Case currentCase = Case.getOpenCase();
            defaultItem.setEnabled(currentCase.hasData());
        } catch (NoCurrentCaseException ex) {
            defaultItem.setEnabled(false); // disable the menu when no case is opened
        }

        comps[counter++] = defaultItem;

        // add the submenu
        if (newWindowLists != null) {
            if (newWindowLists.size() > 0) {

                JMenu submenu = new JMenu(
                        NbBundle.getMessage(this.getClass(), "DataContentDynamicMenu.menu.dataContentWin.text"));
                for (int i = 0; i < newWindowLists.size(); i++) {
                    DataContentTopComponent dctc = newWindowLists.get(i);
                    JMenuItem item = new JMenuItem(dctc.getName());
                    item.addActionListener(new OpenTopComponentAction(dctc));
                    submenu.add(item);
                }

                comps[counter++] = submenu;
            }
        }

        return comps;
    }

    @Override
    public JComponent[] synchMenuPresenters(JComponent[] jcs) {
        return getMenuPresenters();
    }
}
