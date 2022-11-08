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

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.persons.AddEditPersonDialog;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Person;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Allows someone to associate a new person with a parentless host.
 */
@Messages({
    "AssociateNewPersonAction_menuTitle=New Person...",
    "AssociateNewPersonAction_onError_title=Error While Associating New Person",
    "# {0} - hostName",
    "# {1} - personName",
    "AssociateNewPersonAction_onError_description=There was an error while associating host {0} with new person {1}."})
public class AssociateNewPersonAction extends AbstractAction {

    private static final Logger logger = Logger.getLogger(AssociateNewPersonAction.class.getName());

    private final Host host;

    /**
     * Main constructor.
     *
     * @param host The host to be associated with new person.
     */
    public AssociateNewPersonAction(Host host) {
        super(Bundle.AssociateNewPersonAction_menuTitle());
        this.host = host;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String newPersonName = "";
        try {
            newPersonName = getAddDialogName();
            if (StringUtils.isNotBlank(newPersonName)) {
                Person person = Case.getCurrentCaseThrows().getSleuthkitCase().getPersonManager().newPerson(newPersonName);
                Case.getCurrentCaseThrows().getSleuthkitCase().getPersonManager().addHostsToPerson(person, Collections.singletonList(host));
            }
        } catch (NoCurrentCaseException | TskCoreException ex) {
            String hostName = this.host == null || this.host.getName() == null ? "" : this.host.getName();
            logger.log(Level.WARNING, String.format("Unable to remove parent from host: %s", hostName), ex);

            JOptionPane.showMessageDialog(
                    WindowManager.getDefault().getMainWindow(),
                    Bundle.AssociateNewPersonAction_onError_description(hostName, newPersonName),
                    Bundle.AssociateNewPersonAction_onError_title(),
                    JOptionPane.WARNING_MESSAGE);
        }

    }

    /**
     * Get dialog to add new person name.
     *
     * @return The name for the new person or null to cancel.
     * @throws NoCurrentCaseException
     * @throws TskCoreException
     */
    private String getAddDialogName() throws NoCurrentCaseException, TskCoreException {
        Frame parent = WindowManager.getDefault().getMainWindow();

        AddEditPersonDialog addEditDialog
                = new AddEditPersonDialog(
                        parent,
                        Case.getCurrentCaseThrows().getSleuthkitCase().getPersonManager().getPersons(),
                        null);

        addEditDialog.setResizable(false);
        addEditDialog.setLocationRelativeTo(parent);
        addEditDialog.setVisible(true);
        addEditDialog.toFront();

        if (addEditDialog.isChanged()) {
            String newHostName = addEditDialog.getValue();
            return newHostName;
        }

        return null;
    }

}
