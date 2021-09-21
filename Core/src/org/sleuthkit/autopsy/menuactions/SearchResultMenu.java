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

import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.openide.awt.DynamicMenuContent;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;

/**
 * Class to provide menu access to the various result viewers.
 */
class SearchResultMenu extends JMenuItem implements DynamicMenuContent {

    private static final long serialVersionUID = 1L;

    @NbBundle.Messages({"SearchResultMenu.resultViewers.text=Result Viewers"})
    @Override
    public JComponent[] getMenuPresenters() {
        JMenu submenu = new JMenu(Bundle.SearchResultMenu_resultViewers_text());
        List<String> dataResultsIds = DataResultTopComponent.getActiveComponentIds();
        if (Case.isCaseOpen()) {
            // add search results if there are any
            if (!dataResultsIds.isEmpty()) {
                for (String resultTabId : dataResultsIds) {
                    JMenuItem item = new JMenuItem(resultTabId);
                    item.addActionListener(new OpenTopComponentAction(resultTabId));
                    submenu.add(item);
                }
            }
        }
        submenu.setEnabled(!dataResultsIds.isEmpty());
        List<JComponent> menuItems = new ArrayList<>();
        menuItems.add(submenu);
        return menuItems.toArray(new JComponent[menuItems.size()]);
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
