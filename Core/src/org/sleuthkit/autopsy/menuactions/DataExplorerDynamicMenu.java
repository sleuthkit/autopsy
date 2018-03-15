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

import java.util.Collection;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import org.openide.awt.DynamicMenuContent;
import org.openide.util.Lookup;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataExplorer;

/**
 * Populates the top-level menu with the list of DataExplorers.
 *
 * @author jantonius
 */
class DataExplorerDynamicMenu extends JMenuItem implements DynamicMenuContent {

    DataExplorerDynamicMenu() {
    }

    @Override
    public JComponent[] getMenuPresenters() {
        Collection<? extends DataExplorer> dataExplorers = Lookup.getDefault().lookupAll(DataExplorer.class);

        int totalItem = dataExplorers.size();
        JComponent[] comps = new JComponent[totalItem];

        int i = 0;
        for (DataExplorer dx : dataExplorers) {
            if (!dx.hasMenuOpenAction()) {
                continue;
            }
            TopComponent explorerWin = dx.getTopComponent();
            JMenuItem item = new JMenuItem(explorerWin.getName());
            item.addActionListener(new OpenTopComponentAction(explorerWin));

            try {
                Case currentCase = Case.getOpenCase();
                item.setEnabled(currentCase.hasData());
            } catch (NoCurrentCaseException ex) {
                item.setEnabled(false); // disable the menu when no case is opened
            }

            comps[i++] = item;
        }

        return comps;
    }

    @Override
    public JComponent[] synchMenuPresenters(JComponent[] jcs) {
        return getMenuPresenters();
    }

}
