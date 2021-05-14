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
package org.sleuthkit.autopsy.datamodel.hosts;

import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * JMenu item to show a menu allowing the selected host to be merged into another host.
 */
@Messages({
    "MergeHostMenuAction_menuTitle=Merge Into Other Host",})
public class MergeHostMenuAction extends AbstractAction implements Presenter.Popup {

    private static final Logger logger = Logger.getLogger(MergeHostMenuAction.class.getName());

    private final Host sourceHost;

    /**
     * Main constructor.
     *
     * @param host The original host.
     */
    public MergeHostMenuAction(Host host) {
        super("");
        this.sourceHost = host;
    }

    @Override
    @SuppressWarnings("NoopMethodInAbstractClass")
    public void actionPerformed(ActionEvent event) {
    }

    @Override
    public JMenuItem getPopupPresenter() {
        JMenu menu = new JMenu(Bundle.MergeHostMenuAction_menuTitle());

        // Get a list of all other hosts
        List<Host> otherHosts = Collections.emptyList();
        try {
            otherHosts = Case.getCurrentCaseThrows().getSleuthkitCase().getHostManager().getAllHosts();
            otherHosts.remove(sourceHost);
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.WARNING, "Error getting hosts for case.", ex);
        }

        // If there are no other hosts, disable the menu item. Otherwise add
        // the other hosts to the menu.
        if (otherHosts.isEmpty()) {
            menu.setEnabled(false);
        } else {
            menu.setEnabled(true);
            otherHosts.stream()
                    .filter(p -> p != null && p.getName() != null)
                    .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                    .map(p -> new JMenuItem(new MergeHostAction(sourceHost, p)))
                    .forEach(menu::add);
        }

        return menu;
    }

}

