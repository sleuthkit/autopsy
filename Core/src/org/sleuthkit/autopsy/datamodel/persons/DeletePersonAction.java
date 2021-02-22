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
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.TskCoreException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Person;

/**
 * Removes person from case.
 */
@Messages({
    "# {0} - personName",
    "DeletePersonAction_menuTitle=Delete Person",
    "DeletePersonAction_onError_title=Error Removing Host from Person",
    "# {0} - hostName",
    "RemoveParentPersonAction_onError_description=There was an error removing person from host: {0}.",})
public class DeletePersonAction extends AbstractAction {

    private static final Logger logger = Logger.getLogger(DeletePersonAction.class.getName());

    private final Person person;

    /**
     * Main constructor.
     *
     * @param person The person to be removed.
     */
    public DeletePersonAction(Person person) {
        super(Bundle.DeletePersonAction_menuTitle());
        this.person = person;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (person != null && person.getName() != null) {
            try {
                Case.getCurrentCaseThrows().getSleuthkitCase().getPersonManager().deletePerson(person.getName());
            } catch (NoCurrentCaseException | TskCoreException ex) {
                String hostName = this.host == null || this.host.getName() == null ? "" : this.host.getName();
                logger.log(Level.WARNING, String.format("Unable to remove parent from host: %s", hostName), ex);

                JOptionPane.showMessageDialog(
                        WindowManager.getDefault().getMainWindow(),
                        Bundle.RemoveParentPersonAction_onError_description(hostName),
                        Bundle.RemoveParentPersonAction_onError_title(),
                        JOptionPane.WARNING_MESSAGE);
            }
        }

    }

}
