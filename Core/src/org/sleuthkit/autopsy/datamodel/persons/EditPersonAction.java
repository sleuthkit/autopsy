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

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Person;

/**
 * Action to edit the specified person.
 */
@Messages({
    "EditPersonAction_menuTitle=Edit Person...",
    "EditPersonAction_onError_title=Error Editing Person",
    "# {0} - personName",
    "EditPersonAction_onError_description=There was an error editing person: {0}.",})
public class EditPersonAction extends AbstractAction {

    private static final Logger logger = Logger.getLogger(EditPersonAction.class.getName());

    private final Person person;

    /**
     * Main constructor.
     *
     * @param person The person to be edited.
     */
    public EditPersonAction(Person person) {
        super(Bundle.EditPersonAction_menuTitle());
        this.person = person;
    }


    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            String newPersonName = getEditedPersonName(person);
            if (newPersonName != null) {
                person.setName(newPersonName);
            }
            Case.getCurrentCaseThrows().getSleuthkitCase().getPersonManager().updatePerson(person);
        } catch (NoCurrentCaseException | TskCoreException ex) {
            String personName = this.person == null || this.person.getName() == null ? "" : this.person.getName();
            logger.log(Level.WARNING, String.format("Unable to update person: %s", personName), ex);

            JOptionPane.showMessageDialog(
                    WindowManager.getDefault().getMainWindow(),
                    Bundle.EditPersonAction_onError_description(personName),
                    Bundle.EditPersonAction_onError_title(),
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Presents a dialog that will allow the user to edit the person's name.
     * @param person The person.
     * @return The edited person's name or null if cancelled..
     * @throws NoCurrentCaseException
     * @throws TskCoreException 
     */
    private String getEditedPersonName(Person person) throws NoCurrentCaseException, TskCoreException {
        Frame parent = WindowManager.getDefault().getMainWindow();

        AddEditPersonDialog addEditDialog
                = new AddEditPersonDialog(
                        parent,
                        Case.getCurrentCaseThrows().getSleuthkitCase().getPersonManager().getPersons(),
                        person);

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
