/**
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers;

import java.awt.Component;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.SwingWorker;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoAccount;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.Persona;
import org.sleuthkit.autopsy.centralrepository.datamodel.PersonaAccount;
import org.sleuthkit.autopsy.centralrepository.persona.PersonaDetailsDialog;
import org.sleuthkit.autopsy.centralrepository.persona.PersonaDetailsDialogCallback;
import org.sleuthkit.autopsy.centralrepository.persona.PersonaDetailsMode;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Background task to search for a persona for a given account.
 * 
 * When the search is complete, it updates the UI components 
 * for the persona appropriately.
 * 
 */    

 @NbBundle.Messages({
        "# {0} - Persona count",
        "PersonaDisplayTask_persona_count_suffix=(1 of {0})"
    })
class PersonaSearchAndDisplayTask extends SwingWorker<Collection<Persona>, Void> {

    private final static Logger logger = Logger.getLogger(PersonaSearchAndDisplayTask.class.getName());
    
        private final Component parentComponent;
        private final AccountPersonaSearcherData personaSearcherData;

        PersonaSearchAndDisplayTask(Component parentComponent, AccountPersonaSearcherData personaSearcherData) {
            this.parentComponent = parentComponent;
            this.personaSearcherData = personaSearcherData;
        }

        @Override
        protected Collection<Persona> doInBackground() throws Exception {

            Collection<Persona> personas = new ArrayList<>();

            if (CentralRepository.isEnabled()) {
                Collection<CentralRepoAccount> accountCandidates
                        = CentralRepoAccount.getAccountsWithIdentifier(personaSearcherData.getAccountIdentifer());

                if (accountCandidates.isEmpty() == false) {
                    CentralRepoAccount account = accountCandidates.iterator().next();

                    // get personas for the account
                    Collection<PersonaAccount> personaAccountsList = PersonaAccount.getPersonaAccountsForAccount(account.getId());
                    personas = personaAccountsList.stream().map(PersonaAccount::getPersona)
                            .collect(Collectors.toList());
                }
            }
            return personas;
        }

        @Override
        protected void done() {
            Collection<Persona> personas;
            try {
                personas = super.get();

                if (this.isCancelled()) {
                    return;
                }

                //Update the Persona label and button based on the search result
                String personaLabelText = Bundle.CommunicationArtifactViewerHelper_persona_label();
                String personaButtonText;
                ActionListener buttonActionListener;

                if (personas.isEmpty()) {
                    // No persona found
                    personaLabelText += Bundle.CommunicationArtifactViewerHelper_persona_unknown();

                    // show a 'Create' button
                    personaButtonText = Bundle.CommunicationArtifactViewerHelper_persona_button_create();
                    buttonActionListener = new CreatePersonaButtonListener(parentComponent, personaSearcherData);
                } else {
                    Persona persona = personas.iterator().next();
                    personaLabelText += persona.getName();
                    if (personas.size() > 1) {
                        personaLabelText += Bundle.PersonaDisplayTask_persona_count_suffix(Integer.toString(personas.size()));
                    }
                    // Show a 'View' button
                    personaButtonText = Bundle.CommunicationArtifactViewerHelper_persona_button_view();
                    buttonActionListener = new ViewPersonaButtonListener(parentComponent, persona);
                }

                personaSearcherData.getPersonaNameLabel().setText(personaLabelText);
                personaSearcherData.getPersonaActionButton().setText(personaButtonText);
                personaSearcherData.getPersonaActionButton().setEnabled(true);

                // set button action
                personaSearcherData.getPersonaActionButton().addActionListener(buttonActionListener);
            } catch (CancellationException ex) {
                logger.log(Level.INFO, "Persona searching was canceled."); //NON-NLS
            } catch (InterruptedException ex) {
                logger.log(Level.INFO, "Persona searching was interrupted."); //NON-NLS
            } catch (ExecutionException ex) {
                logger.log(Level.SEVERE, "Fatal error during Persona search.", ex); //NON-NLS
            }

        }
        
    /**
     * Action listener for Create persona button.
     */
    private class CreatePersonaButtonListener implements ActionListener {

        private final Component parentComponent;
        private final AccountPersonaSearcherData personaSearcherData;

        CreatePersonaButtonListener(Component parentComponent, AccountPersonaSearcherData personaSearcherData) {
            this.parentComponent = parentComponent;
            this.personaSearcherData = personaSearcherData;
        }

        @Override
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            // Launch the Persona Create dialog
            new PersonaDetailsDialog(parentComponent,
                    PersonaDetailsMode.CREATE, null, new PersonaCreateCallbackImpl(parentComponent, personaSearcherData));
        }
    }

    /**
     * Action listener for View persona button.
     */
    private class ViewPersonaButtonListener implements ActionListener {

        private final Component parentComponent;
        private final Persona persona;

        ViewPersonaButtonListener(Component parentComponent, Persona persona) {
            this.parentComponent = parentComponent;
            this.persona = persona;
        }

        @Override
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            new PersonaDetailsDialog(parentComponent,
                    PersonaDetailsMode.VIEW, persona, new PersonaViewCallbackImpl());
        }
    }

    /**
     * Callback method for the create mode of the PersonaDetailsDialog
     */
    class PersonaCreateCallbackImpl implements PersonaDetailsDialogCallback {
        
        private final Component parentComponent;
        private final AccountPersonaSearcherData personaSearcherData;

        PersonaCreateCallbackImpl(Component parentComponent, AccountPersonaSearcherData personaSearcherData) {
            this.parentComponent = parentComponent;
            this.personaSearcherData = personaSearcherData;
        }

        @Override
        public void callback(Persona persona) {
            JButton personaButton = personaSearcherData.getPersonaActionButton();
            if (persona != null) {
                // update the persona name label with newly created persona, 
                // and change the button to a "View" button
                personaSearcherData.getPersonaNameLabel().setText(Bundle.CommunicationArtifactViewerHelper_persona_label() + persona.getName());
                personaSearcherData.getPersonaActionButton().setText(Bundle.CallLogArtifactViewer_persona_button_view());

                // replace action listener with a View button listener
                for (ActionListener act : personaButton.getActionListeners()) {
                    personaButton.removeActionListener(act);
                }
                personaButton.addActionListener(new ViewPersonaButtonListener(parentComponent, persona));

            }

            personaButton.getParent().revalidate();
        }
    }

    /**
     * Callback method for the view mode of the PersonaDetailsDialog
     */
    class PersonaViewCallbackImpl implements PersonaDetailsDialogCallback {

        @Override
        public void callback(Persona persona) {
            // nothing to do 
        }
    }
}
