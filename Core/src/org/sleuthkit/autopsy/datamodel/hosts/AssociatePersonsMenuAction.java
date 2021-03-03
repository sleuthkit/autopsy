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
import javax.swing.JSeparator;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Person;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * JMenu item to show a menu giving options to associate the given host with an
 * existing person.
 */
@Messages({
    "AssociatePersonsMenuAction_menuTitle=Associate with Person",})
public class AssociatePersonsMenuAction extends AbstractAction implements Presenter.Popup {

    private static final Logger logger = Logger.getLogger(AssociatePersonsMenuAction.class.getName());

    private final Host host;

    /**
     * Main constructor.
     *
     * @param host The host.
     */
    public AssociatePersonsMenuAction(Host host) {
        super("");
        this.host = host;
    }

    @Override
    @SuppressWarnings("NoopMethodInAbstractClass")
    public void actionPerformed(ActionEvent event) {
    }

    @Override
    public JMenuItem getPopupPresenter() {
        JMenu menu = new JMenu(Bundle.AssociatePersonsMenuAction_menuTitle());

        List<Person> existingPersons = Collections.emptyList();
        try {
            existingPersons = Case.getCurrentCaseThrows().getSleuthkitCase().getPersonManager().getPersons();
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.WARNING, "Error getting persons for case.", ex);
        }

        existingPersons.stream()
                .filter(p -> p != null && p.getName() != null)
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(p -> new JMenuItem(new AssociatePersonAction(this.host, p)))
                .forEach(menu::add);

        if (menu.getItemCount() > 0) {
            menu.add(new JSeparator());
        }

        menu.add(new JMenuItem(new AssociateNewPersonAction(this.host)));
        return menu;
    }

}
