/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponents.DataContentTopComponent;

/**
 * Class to provide menu access to the various instances of the content viewer
 * suite.
 */
class DataContentDynamicMenu extends JMenuItem implements DynamicMenuContent {

    private static final long serialVersionUID = 1L;

    @NbBundle.Messages({"DataContentDynamicMenu.mainContentViewer.name=Main",
        "DataContentDynamicMenu.contentViewers.text=Content Viewers"})
    @Override
    public JComponent[] getMenuPresenters() {
        JMenu submenu = new JMenu(Bundle.DataContentDynamicMenu_contentViewers_text());
        if (Case.isCaseOpen()) {

            List<DataContentTopComponent> newWindowLists = DataContentTopComponent.getNewWindowList();

            TopComponent contentWin = DataContentTopComponent.findInstance();
            JMenuItem defaultItem = new JMenuItem(Bundle.DataContentDynamicMenu_mainContentViewer_name()); // set the main name
            defaultItem.addActionListener(new OpenTopComponentAction(contentWin));
            try {
                Case currentCase = Case.getCurrentCaseThrows();
                defaultItem.setEnabled(currentCase.hasData());
            } catch (NoCurrentCaseException ex) {
                defaultItem.setEnabled(false); // disable the menu when no case is opened
            }
            submenu.add(defaultItem);
            // add the submenu
            if (!newWindowLists.isEmpty()) {
                for (int i = 0; i < newWindowLists.size(); i++) {
                    DataContentTopComponent dctc = newWindowLists.get(i);
                    JMenuItem item = new JMenuItem(dctc.getName());
                    item.addActionListener(new OpenTopComponentAction(dctc));
                    submenu.add(item);
                }
            }

        }
        submenu.setEnabled(submenu.getItemCount() > 0);
        JComponent[] comps = new JComponent[1];
        comps[0] = submenu;
        return comps;
    }

    @Override
    public JComponent[] synchMenuPresenters(JComponent[] jcs) {
        return getMenuPresenters();
    }

    @Override
    public boolean isEnabled() {
        return Case.isCaseOpen();
    }
}
