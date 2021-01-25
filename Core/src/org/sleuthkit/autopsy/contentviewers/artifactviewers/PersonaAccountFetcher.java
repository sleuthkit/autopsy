/*
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
package org.sleuthkit.autopsy.contentviewers.artifactviewers;

import java.awt.Component;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JButton;
import javax.swing.SwingWorker;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoAccount;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.Persona;
import org.sleuthkit.autopsy.centralrepository.datamodel.PersonaAccount;
import org.sleuthkit.autopsy.centralrepository.persona.PersonaDetailsDialog;
import org.sleuthkit.autopsy.centralrepository.persona.PersonaDetailsDialogCallback;
import org.sleuthkit.autopsy.centralrepository.persona.PersonaDetailsMode;
import org.sleuthkit.autopsy.centralrepository.persona.PersonaDetailsPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.InvalidAccountIDException;

/**
 * SwingWorker for fetching and updating Persona controls.
 */
class PersonaAccountFetcher extends SwingWorker<Map<String, Collection<Persona>>, Void> {

    private final static Logger logger = Logger.getLogger(PersonaAccountFetcher.class.getName());

    private final BlackboardArtifact artifact;
    private final List<AccountPersonaSearcherData> personaSearchDataList;
    private final Component parentComponent;

    /**
     * Construct the SwingWorker.
     *
     * @param artifact              The artifact to search account for.
     * @param personaSearchDataList List of PersonaSerarcherData objects.
     * @param parentComponent       The parent panel.
     */
    PersonaAccountFetcher(BlackboardArtifact artifact, List<AccountPersonaSearcherData> personaSearchDataList, Component parentComponent) {
        this.artifact = artifact;
        this.personaSearchDataList = personaSearchDataList;
        this.parentComponent = parentComponent;
    }

    @Override
    protected Map<String, Collection<Persona>> doInBackground() throws Exception {
        Map<String, Collection<Persona>> accountMap = new HashMap<>();

        CommunicationsManager commManager = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager();

        List<Account> relatedAccountList = commManager.getAccountsRelatedToArtifact(artifact);

        for (Account account : relatedAccountList) {

            if (isCancelled()) {
                return new HashMap<>();
            }

            Collection<PersonaAccount> personaAccountList = PersonaAccount.getPersonaAccountsForAccount(account);
            Collection<Persona> personaList = new ArrayList<>();
            for (PersonaAccount pAccount : personaAccountList) {
                personaList.add(pAccount.getPersona());
            }

            accountMap.put(account.getTypeSpecificID(), personaList);
        }

        return accountMap;
    }

    @Override
    protected void done() {
        if (isCancelled()) {
            return;
        }

        try {
            Map<String, Collection<Persona>> accountMap = get();

            for (AccountPersonaSearcherData searcherData : personaSearchDataList) {
                Collection<Persona> persona = accountMap.get(searcherData.getAccountIdentifer());
                updatePersonaControls(searcherData, persona);
            }

        } catch (CancellationException ex) {
            logger.log(Level.INFO, "Persona searching was canceled."); //NON-NLS
        } catch (InterruptedException ex) {
            logger.log(Level.INFO, "Persona searching was interrupted."); //NON-NLS
        } catch (ExecutionException ex) {
            logger.log(Level.SEVERE, "Fatal error during Persona search.", ex); //NON-NLS
        }

        parentComponent.repaint();
    }
    
    @Messages({
        "# {0} - Persona count",
        "PersonaDisplayTask_persona_count_suffix=(1 of {0})"
    })

    /**
     * Update the Persona gui controls.
     *
     * @param personaSearcherData The data objects with persona controls
     * @param personas            Collection of persona objects
     */
    private void updatePersonaControls(AccountPersonaSearcherData personaSearcherData, Collection<Persona> personas) {
        //Update the Persona label and button based on the search result
        String personaLabelText = Bundle.CommunicationArtifactViewerHelper_persona_label();
        String personaButtonText;
        ActionListener buttonActionListener;

        if (personas == null || personas.isEmpty()) {
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

        @NbBundle.Messages({
            "PersonaAccountFetcher.account.justification=Account found in Call Log artifact",
            "# {0} - accountIdentifer",
            "PersonaAccountFetcher_not_account_in_cr=Unable to find an account with identifier {0} in the Central Repository."
        })
        @Override
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            // Launch the Persona Create dialog
            
            PersonaDetailsDialog dialog = new PersonaDetailsDialog(parentComponent,
                    PersonaDetailsMode.CREATE, null, new PersonaCreateCallbackImpl(parentComponent, personaSearcherData), false);
            
            // Pre populate the persona name and accounts if we have them.
            PersonaDetailsPanel personaPanel = dialog.getDetailsPanel();
            
            // Set a default name
            personaPanel.setPersonaName(personaSearcherData.getAccountIdentifer());
            
            // Set up each matching account. We don't know what type of account we have, so check all the types to 
            // find any matches.
            try {
                boolean showErrorMessage = true;
                for (CentralRepoAccount.CentralRepoAccountType type : CentralRepository.getInstance().getAllAccountTypes()) {
                    try {
                        // Try to load any matching accounts of this type. Throws an InvalidAccountIDException if the account is the
                        // wrong format (i.e., when we try to load email accounts for a phone number-type string).
                        CentralRepoAccount account = CentralRepository.getInstance().getAccount(type, personaSearcherData.getAccountIdentifer());
                        if (account != null) {
                            personaPanel.addAccount(account, Bundle.PersonaAccountFetcher_account_justification(), Persona.Confidence.HIGH);
                            showErrorMessage = false;
                        }
                        
                    } catch (InvalidAccountIDException ex2) {
                        // These are expected when the account identifier doesn't match the format of the account type.
                    }
                }
                if ((personaSearcherData.getAccountIdentifer() != null
                        && !personaSearcherData.getAccountIdentifer().isEmpty()) && showErrorMessage) {
                    dialog.setStartupPopupMessage(Bundle.PersonaAccountFetcher_not_account_in_cr(personaSearcherData.getAccountIdentifer()));
                }
            } catch (CentralRepoException ex) {
                logger.log(Level.SEVERE, "Error looking up account types in the central repository", ex);
            }
            
            // display the dialog now
            dialog.display();
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
                personaSearcherData.getPersonaActionButton().setText(Bundle.CommunicationArtifactViewerHelper_persona_button_view());

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
