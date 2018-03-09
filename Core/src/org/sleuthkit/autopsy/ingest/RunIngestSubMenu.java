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
package org.sleuthkit.autopsy.ingest;

import org.sleuthkit.autopsy.ingest.runIngestModuleWizard.RunIngestModulesAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import org.openide.awt.DynamicMenuContent;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * This class is used to populate the list of open dataSources to run ingest on
 * them
 */
final class RunIngestSubMenu extends JMenuItem implements DynamicMenuContent {

    /**
     * Creates main menu/popup menu items. It's called each time a popup menu is
     * constructed and just once for the main menu. Main menu updates happen
     * through the synchMenuPresenters() method.
     *
     * @return
     */
    @Override
    public JComponent[] getMenuPresenters() {
        List<Content> dataSources = new ArrayList<>();

        try {
            dataSources = Case.getOpenCase().getDataSources();
        } catch (IllegalStateException ex) {
            // No open Cases, create a disabled empty menu
            return getEmpty();
        } catch (TskCoreException | NoCurrentCaseException e) {
            System.out.println("Exception getting images: " + e.getMessage()); //NON-NLS
        }
        JComponent[] comps = new JComponent[dataSources.size()];

        // Add Images to the component list
        for (int i = 0; i < dataSources.size(); i++) {
            String action = dataSources.get(i).getName();
            JMenuItem menuItem = new JMenuItem(action);
            menuItem.setActionCommand(action.toUpperCase());
            menuItem.addActionListener(new RunIngestModulesAction(Collections.<Content>singletonList(dataSources.get(i))));
            comps[i] = menuItem;
        }
        // If no dataSources are open, create a disabled empty menu
        if (dataSources.isEmpty()) {
            return getEmpty();
        }
        return comps;
    }

    // returns a disabled empty menu
    private JComponent[] getEmpty() {
        JComponent[] comps = new JComponent[1];
        JMenuItem emptyMenu = new JMenuItem(NbBundle.getMessage(RunIngestSubMenu.class, "RunIngestSubMenu.menuItem.empty"));
        comps[0] = emptyMenu;
        comps[0].setEnabled(false);
        return comps;
    }

    /**
     * Updates main menu presenters. This method is called only by the main menu
     * processing.
     *
     * @param jcs the previously used menu items returned by previous call to
     *            getMenuPresenters() or synchMenuPresenters()
     *
     * @return menu a new set of items to show in menu. Can be either an updated
     *         old set of instances or a completely new one.
     */
    @Override
    public JComponent[] synchMenuPresenters(JComponent[] jcs) {
        return getMenuPresenters();
    }

}
