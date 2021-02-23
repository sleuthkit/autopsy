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
package org.sleuthkit.autopsy.datamodel.persons;

import java.awt.event.ActionEvent;
import java.util.List;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import org.apache.commons.collections.CollectionUtils;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Person;

/**
 * Removes person from case.
 */
@Messages({
    "DeletePersonAction_menuTitle=Delete Person",
    "DeletePersonAction_onError_title=Error Delete Host from Person",
    "# {0} - personName",
    "DeletePersonAction_onError_description=There was an error removing person: {0}.",})
public class DeletePersonAction extends AbstractAction {

    private static final Logger logger = Logger.getLogger(DeletePersonAction.class.getName());

    private final Person person;

    /**
     * Main constructor.
     *
     * @param person The person to be deleted.
     */
    public DeletePersonAction(Person person) {
        super(Bundle.DeletePersonAction_menuTitle());
        this.person = person;
        setEnabled();
    }

    /**
     * Sets the action enabled only if no child hosts.
     */
    private void setEnabled() {
        if (person == null) {
            this.setEnabled(false);
        } else {
            try {
                List<Host> hosts = Case.getCurrentCaseThrows().getSleuthkitCase().getPersonManager().getHostsForPerson(person);
                if (CollectionUtils.isNotEmpty(hosts)) {
                    this.setEnabled(false);
                    return;
                }
            } catch (NoCurrentCaseException | TskCoreException ex) {
                logger.log(Level.WARNING, String.format("Unable to fetch hosts belonging to person: %s", person.getName() == null ? "<null>" : person.getName(), ex));
            }
            this.setEnabled(true);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (person != null && person.getName() != null) {
            try {
                Case.getCurrentCaseThrows().getSleuthkitCase().getPersonManager().deletePerson(person.getName());
            } catch (NoCurrentCaseException | TskCoreException ex) {
                String personName = this.person == null || this.person.getName() == null ? "" : this.person.getName();
                logger.log(Level.WARNING, String.format("Unable to remove parent from host: %s", personName), ex);

                JOptionPane.showMessageDialog(
                        WindowManager.getDefault().getMainWindow(),
                        Bundle.DeletePersonAction_onError_description(personName),
                        Bundle.DeletePersonAction_onError_title(),
                        JOptionPane.WARNING_MESSAGE);
            }
        }

    }

}
