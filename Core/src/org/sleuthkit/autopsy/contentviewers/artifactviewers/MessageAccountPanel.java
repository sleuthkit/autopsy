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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.ParallelGroup;
import javax.swing.GroupLayout.SequentialGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
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
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel for displaying accounts and their persona information.
 *
 */
final class MessageAccountPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private final static Logger logger = Logger.getLogger(MessageAccountPanel.class.getName());

    private AccountFetcher currentFetcher = null;

    /**
     * Set the new artifact for the panel.
     *
     * @param artifact
     *
     * @throws TskCoreException
     */
    void setArtifact(BlackboardArtifact artifact) {
        removeAll();
        setLayout(null);
        repaint();

        if (artifact == null) {
            return;
        }

        if (currentFetcher != null && !currentFetcher.isDone()) {
            currentFetcher.cancel(true);
        }

        currentFetcher = new AccountFetcher(artifact);
        currentFetcher.execute();
    }

    /**
     * Swingworker that fetches the accounts for a given artifact
     */
    class AccountFetcher extends SwingWorker<List<AccountContainer>, Void> {

        private final BlackboardArtifact artifact;

        /**
         * Construct a new AccountFetcher.
         *
         * @param artifact The artifact to get accounts for.
         */
        AccountFetcher(BlackboardArtifact artifact) {
            this.artifact = artifact;
        }

        @Override
        protected List<AccountContainer> doInBackground() throws Exception {
            List<AccountContainer> dataList = new ArrayList<>();

            CommunicationsManager commManager = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager();
            List<Account> accountList = commManager.getAccountsRelatedToArtifact(artifact);
            for (Account account : accountList) {
                if (isCancelled()) {
                    return new ArrayList<>();
                }

                Collection<PersonaAccount> personAccounts = PersonaAccount.getPersonaAccountsForAccount(account);
                if (personAccounts != null && !personAccounts.isEmpty()) {
                    for (PersonaAccount personaAccount : PersonaAccount.getPersonaAccountsForAccount(account)) {
                        dataList.add(new AccountContainer(account, personaAccount));
                    }
                } else {
                    dataList.add(new AccountContainer(account, null));
                }
            }

            return dataList;
        }

        @Messages({
            "MessageAccountPanel_no_matches=No matches found.",
        })
        @Override
        protected void done() {
            try {
                List<AccountContainer> dataList = get();

                if (!dataList.isEmpty()) {
                    dataList.forEach(container -> {
                        container.initalizeSwingControls();
                    });

                    GroupLayout layout = new GroupLayout(MessageAccountPanel.this);
                    layout.setHorizontalGroup(
                            layout.createParallelGroup(Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                            .addContainerGap()
                                            .addGroup(getMainHorizontalGroup(layout, dataList))
                                            .addContainerGap(158, Short.MAX_VALUE)));

                    layout.setVerticalGroup(getMainVerticalGroup(layout, dataList));
                    setLayout(layout);
                    repaint();
                } else {
                    // No match found, display a message.
                    JPanel messagePanel = new javax.swing.JPanel();
                    JLabel messageLabel = new javax.swing.JLabel();

                    messagePanel.setLayout(new java.awt.BorderLayout());

                    messageLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
                    messageLabel.setText(Bundle.MessageAccountPanel_no_matches());
                    messageLabel.setEnabled(false);
                    messagePanel.add(messageLabel, java.awt.BorderLayout.CENTER);

                    setLayout(new javax.swing.OverlayLayout(MessageAccountPanel.this));

                    add(messagePanel);
                    repaint();
                }
            } catch (CancellationException ex) {
                logger.log(Level.INFO, "MessageAccoutPanel thread cancelled", ex);
            } catch (InterruptedException | ExecutionException ex) {
                logger.log(Level.WARNING, "Failed to get account list for MessageAccountPanel", ex);
            }
        }

        /**
         * Create the main horizontal ParalleGroup the encompasses all of the
         * controls.
         *
         * @return A ParallelGroup object
         */
        private ParallelGroup getMainHorizontalGroup(GroupLayout layout, List<AccountContainer> data) {
            ParallelGroup group = layout.createParallelGroup(Alignment.LEADING);
            for (AccountContainer o : data) {
                group.addComponent(o.getAccountLabel());
            }
            group.addGroup(getPersonaHorizontalGroup(layout, data));
            return group;
        }

        /**
         * Creates the main Vertical Group for the account controls.
         *
         * @return The vertical group object
         */
        private ParallelGroup getMainVerticalGroup(GroupLayout layout, List<AccountContainer> data) {
            SequentialGroup group = layout.createSequentialGroup();
            for (AccountContainer o : data) {
                group.addGap(5)
                        .addComponent(o.getAccountLabel())
                        .addGroup(o.getPersonLineVerticalGroup(layout));
            }

            group.addContainerGap(83, Short.MAX_VALUE);

            return layout.createParallelGroup().addGroup(group);

        }

        /**
         * To line up the Persona buttons they need to be in their own
         * ParalletGroup.
         *
         * @return
         */
        private ParallelGroup getButtonGroup(GroupLayout layout, List<AccountContainer> data) {
            ParallelGroup group = layout.createParallelGroup(Alignment.LEADING);
            for (AccountContainer o : data) {
                group.addComponent(o.getButton());
            }

            return group;
        }

        /**
         * Creates the group with just the persona header and the person value.
         *
         * @return
         */
        private SequentialGroup getPersonaHorizontalGroup(GroupLayout layout, List<AccountContainer> data) {
            SequentialGroup group = layout.createSequentialGroup();
            ParallelGroup pgroup = layout.createParallelGroup(Alignment.LEADING);
            group.addGap(10);
            for (AccountContainer o : data) {
                pgroup.addGroup(o.getPersonaSequentialGroup(layout));
            }
            group.addGap(10)
                    .addGroup(pgroup)
                    .addPreferredGap(ComponentPlacement.RELATED)
                    .addGroup(getButtonGroup(layout, data));

            return group;
        }

    }

    /**
     * Container for each account entry in the panel. This class holds both the
     * account objects and the ui components.
     */
    private class AccountContainer {

        private final Account account;
        private Persona persona = null;

        private JLabel accountLabel;
        private JLabel personaHeader;
        private JLabel personaDisplayName;
        private JButton button;

        /**
         * Construct a new AccountContainer
         *
         * @param account
         * @param personaAccount
         */
        AccountContainer(Account account, PersonaAccount personaAccount) {
            this.account = account;
            this.persona = personaAccount != null ? personaAccount.getPersona() : null;
        }

        @Messages({
            "MessageAccountPanel_persona_label=Persona:",
            "MessageAccountPanel_unknown_label=Unknown",
            "MessageAccountPanel_button_view_label=View",
            "MessageAccountPanel_button_create_label=Create"
        })
        /**
         * Swing components will not be initialized until this method is called.
         */
        private void initalizeSwingControls() {
            accountLabel = new JLabel();
            personaHeader = new JLabel(Bundle.MessageAccountPanel_persona_label());
            personaDisplayName = new JLabel();
            button = new JButton();
            button.addActionListener(new PersonaButtonListener(this));

            accountLabel.setText(account.getTypeSpecificID());

            personaDisplayName.setText(persona != null ? persona.getName() : Bundle.MessageAccountPanel_unknown_label());
            button.setText(persona != null ? Bundle.MessageAccountPanel_button_view_label() : Bundle.MessageAccountPanel_button_create_label());
        }

        /**
         * Sets a new persona for this object and update the controls.
         *
         * @param persona
         */
        private void setPersona(Persona persona) {
            this.persona = persona;

            // Make sure this runs in EDT
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    personaDisplayName.setText(persona != null ? persona.getName() : Bundle.MessageAccountPanel_unknown_label());
                    button.setText(persona != null ? Bundle.MessageAccountPanel_button_view_label() : Bundle.MessageAccountPanel_button_create_label());
                    revalidate();
                    repaint();
                }
            });
        }

        /**
         * Return the account object for this container.
         *
         * @return Account object.
         */
        private Account getAccount() {
            return account;
        }

        /**
         * Returns the PersonaAccount object for this container. Maybe null;
         *
         * @return PersonaAccount object or null if one was not set.
         */
        private Persona getPersona() {
            return persona;
        }

        /**
         * Returns the JLabel for that contains the Account type specific id.
         *
         * @return JLabel object
         */
        private JLabel getAccountLabel() {
            return accountLabel;
        }

        /**
         * Returns the Persona Buttons for this container.
         *
         * @return The persona button.
         */
        private JButton getButton() {
            return button;
        }

        /**
         * Generates the horizontal layout code for the person line.
         *
         * @param layout Instance of GroupLayout to update.
         *
         * @return A group for the personal controls.
         */
        private SequentialGroup getPersonaSequentialGroup(GroupLayout layout) {
            SequentialGroup group = layout.createSequentialGroup();

            group
                    .addComponent(personaHeader)
                    .addPreferredGap(ComponentPlacement.RELATED)
                    .addComponent(personaDisplayName);

            return group;
        }

        /**
         * Generates the vertical layout code for the persona line.
         *
         * @param layout Instance of GroupLayout to update.
         *
         * @return A group for the personal controls.
         */
        private ParallelGroup getPersonLineVerticalGroup(GroupLayout layout) {
            return layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(personaHeader)
                    .addComponent(personaDisplayName)
                    .addComponent(button);
        }
    }

    /**
     * ActionListner for the persona buttons.
     */
    private class PersonaButtonListener implements ActionListener {

        private final AccountContainer accountContainer;

        /**
         * Constructs the listener.
         *
         * @param accountContainer The account that does with list Listner.
         */
        PersonaButtonListener(AccountContainer accountContainer) {
            this.accountContainer = accountContainer;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Persona persona = accountContainer.getPersona();
            if (persona == null) {
                PersonaDetailsDialog createPersonaDialog = new PersonaDetailsDialog(
                        MessageAccountPanel.this,
                        PersonaDetailsMode.CREATE,
                        null,
                        new PersonaDialogCallbackImpl(accountContainer),
                        false);

                // Pre populate the persona name and accounts if we have them.
                PersonaDetailsPanel personaPanel = createPersonaDialog.getDetailsPanel();

                personaPanel.setPersonaName(accountContainer.getAccount().getTypeSpecificID());

                // display the dialog now
                createPersonaDialog.display();
            } else {
                new PersonaDetailsDialog(MessageAccountPanel.this,
                        PersonaDetailsMode.VIEW, persona, new PersonaDialogCallbackImpl(accountContainer));
            }
        }

    }

    /**
     * Call back for use by the PersonaDetailsDialog.
     */
    private class PersonaDialogCallbackImpl implements PersonaDetailsDialogCallback {

        private final AccountContainer accountContainer;

        PersonaDialogCallbackImpl(AccountContainer accountContainer) {
            this.accountContainer = accountContainer;
        }

        @Override
        public void callback(Persona persona) {
            accountContainer.setPersona(persona);
        }

    }
}
