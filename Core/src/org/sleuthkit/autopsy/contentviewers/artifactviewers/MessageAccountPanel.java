/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
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

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextPane;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
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
import org.sleuthkit.autopsy.contentviewers.layout.ContentViewerDefaults;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.guiutils.ContactCache;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.InvalidAccountIDException;
import org.sleuthkit.datamodel.DataSource;
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
     * Main constructor.
     */
    MessageAccountPanel() {
        this.setBorder(new EmptyBorder(ContentViewerDefaults.getPanelInsets()));
    }
    
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

                if (!((DataSource) (artifact.getDataSource())).getDeviceId().equals(account.getTypeSpecificID())) {
                    List<BlackboardArtifact> contactList = ContactCache.getContacts(account);
                    BlackboardArtifact contact = null;

                    if (contactList != null && !contactList.isEmpty()) {
                        contact = contactList.get(0);
                    }

                    if (CentralRepository.isEnabled()) {
                        Collection<PersonaAccount> personAccounts = PersonaAccount.getPersonaAccountsForAccount(account);
                        if (personAccounts != null && !personAccounts.isEmpty()) {
                            for (PersonaAccount personaAccount : PersonaAccount.getPersonaAccountsForAccount(account)) {
                                dataList.add(new AccountContainer(account, personaAccount, contact));
                            }
                        } else {
                            dataList.add(new AccountContainer(account, null, contact));
                        }
                    } else {
                        dataList.add(new AccountContainer(account, null, contact));
                    }
                }
            }

            return dataList;
        }

        @Messages({
            "MessageAccountPanel_no_matches=No matches found.",})
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
                                            .addGroup(getMainHorizontalGroup(layout, dataList))));

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
                    messageLabel.setFont(ContentViewerDefaults.getMessageFont());
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
                group.addComponent(o.getAccountLabel())
                        .addGroup(o.getContactLineVerticalGroup(layout))
                        .addGroup(o.getPersonLineVerticalGroup(layout));
                group.addGap(ContentViewerDefaults.getSectionSpacing());
            }

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
            for (AccountContainer o : data) {
                pgroup.addGroup(o.getPersonaSequentialGroup(layout));
                pgroup.addGroup(o.getContactSequentialGroup(layout));
            }
            group.addGap(ContentViewerDefaults.getSectionIndent())
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
        private final String contactName;

        private JTextPane accountLabel;
        private JLabel personaHeader;
        private JTextPane personaDisplayName;
        private JLabel contactHeader;
        private JTextPane contactDisplayName;
        private JButton button;

        private JMenuItem contactCopyMenuItem;
        private JMenuItem personaCopyMenuItem;
        private JMenuItem accountCopyMenuItem;
        JPopupMenu contactPopupMenu = new JPopupMenu();
        JPopupMenu personaPopupMenu = new JPopupMenu();
        JPopupMenu accountPopupMenu = new JPopupMenu();

        /**
         * Construct a new AccountContainer
         *
         * @param account
         * @param personaAccount
         */
        AccountContainer(Account account, PersonaAccount personaAccount, BlackboardArtifact contactArtifact) throws TskCoreException {
            if (contactArtifact != null && contactArtifact.getArtifactTypeID() != BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT.getTypeID()) {
                throw new IllegalArgumentException("Failed to create AccountContainer object, passed in artifact was not a TSK_CONTACT");
            }

            this.account = account;
            this.persona = personaAccount != null ? personaAccount.getPersona() : null;
            this.contactName = getNameFromContactArtifact(contactArtifact);
        }

        @Messages({
            "MessageAccountPanel_persona_label=Persona:",
            "MessageAccountPanel_unknown_label=Unknown",
            "MessageAccountPanel_button_view_label=View",
            "MessageAccountPanel_button_create_label=Create",
            "MessageAccountPanel_contact_label=Contact:",
            "MessageAccountPanel_copy_label=Copy"
        })
        /**
         * Swing components will not be initialized until this method is called.
         */
        private void initalizeSwingControls() {
            accountLabel = new JTextPane();
            accountLabel.setEditable(false);
            accountLabel.setOpaque(false);
            personaHeader = new JLabel(Bundle.MessageAccountPanel_persona_label());
            contactHeader = new JLabel(Bundle.MessageAccountPanel_contact_label());
            personaDisplayName = new JTextPane();
            personaDisplayName.setOpaque(false);
            personaDisplayName.setEditable(false);
            personaDisplayName.setPreferredSize(new Dimension(100, 26));
            personaDisplayName.setMaximumSize(new Dimension(100, 26));
            contactDisplayName = new JTextPane();
            contactDisplayName.setOpaque(false);
            contactDisplayName.setEditable(false);
            contactDisplayName.setPreferredSize(new Dimension(100, 26));
            button = new JButton();
            button.addActionListener(new PersonaButtonListener(this));

            accountLabel.setMargin(new Insets(0, 0, 0, 0));
            accountLabel.setText(account.getTypeSpecificID());
            accountLabel.setFont(ContentViewerDefaults.getHeaderFont());
            contactDisplayName.setText(contactName);
            personaDisplayName.setText(persona != null ? persona.getName() : Bundle.MessageAccountPanel_unknown_label());

            //This is a bit of a hack to size the JTextPane correctly, but it gets the job done.
            personaDisplayName.setMaximumSize((new JLabel(personaDisplayName.getText()).getMaximumSize()));
            contactDisplayName.setMaximumSize((new JLabel(contactDisplayName.getText()).getMaximumSize()));
            accountLabel.setMaximumSize((new JLabel(accountLabel.getText()).getMaximumSize()));

            button.setText(persona != null ? Bundle.MessageAccountPanel_button_view_label() : Bundle.MessageAccountPanel_button_create_label());
            
            initalizePopupMenus();
        }

        /**
         * Initialize the copy popup menus for the persona and the contact label. 
         */
        private void initalizePopupMenus() {
            contactCopyMenuItem = new JMenuItem(Bundle.MessageAccountPanel_copy_label());
            personaCopyMenuItem = new JMenuItem(Bundle.MessageAccountPanel_copy_label());
            accountCopyMenuItem = new JMenuItem(Bundle.MessageAccountPanel_copy_label());
            personaPopupMenu.add(personaCopyMenuItem);
            contactPopupMenu.add(contactCopyMenuItem);
            accountPopupMenu.add(accountCopyMenuItem);

            personaDisplayName.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent evt) {
                    if (SwingUtilities.isRightMouseButton(evt)) {
                        personaPopupMenu.show(personaDisplayName, evt.getX(), evt.getY());
                    }
                }
            });

            personaCopyMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(personaDisplayName.getText()), null);
                }
            });

            contactDisplayName.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent evt) {
                    if (SwingUtilities.isRightMouseButton(evt)) {
                        contactPopupMenu.show(contactDisplayName, evt.getX(), evt.getY());
                    }
                }
            });

            contactCopyMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(contactDisplayName.getText()), null);
                }
            });
            
            accountLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent evt) {
                    if (SwingUtilities.isRightMouseButton(evt)) {
                        accountPopupMenu.show(accountLabel, evt.getX(), evt.getY());
                    }
                }
            });

            accountCopyMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(accountLabel.getText()), null);
                }
            });

        }

        private String getNameFromContactArtifact(BlackboardArtifact contactArtifact) throws TskCoreException {
            if (contactArtifact != null) {
                BlackboardAttribute attribute = contactArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME));
                if (attribute != null) {
                    return attribute.getValueString();
                }
            }

            return Bundle.MessageAccountPanel_unknown_label();
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

                    //This is a bit of a hack to size the JTextPane correctly, but it gets the job done.
                    personaDisplayName.setMaximumSize((new JLabel(personaDisplayName.getText()).getMaximumSize()));
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
        private JTextPane getAccountLabel() {
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

        private SequentialGroup getContactSequentialGroup(GroupLayout layout) {
            SequentialGroup group = layout.createSequentialGroup();

            group
                    .addComponent(contactHeader)
                    .addPreferredGap(ComponentPlacement.RELATED)
                    .addComponent(contactDisplayName);

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
            return layout.createParallelGroup(Alignment.CENTER)
                    .addComponent(personaHeader)
                    .addComponent(personaDisplayName)
                    .addComponent(button);
        }

         /**
         * Generates the vertical layout code for the contact line.
         *
         * @param layout Instance of GroupLayout to update.
         *
         * @return A group for the personal controls.
         */
        private ParallelGroup getContactLineVerticalGroup(GroupLayout layout) {
            return layout.createParallelGroup(Alignment.CENTER)
                    .addComponent(contactHeader)
                    .addComponent(contactDisplayName);
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

        @NbBundle.Messages({
            "MessageAccountPanel.account.justification=Account found in Message artifact",
            "# {0} - accountIdentifer",
            "MessageAccountPanel_id_not_found_in_cr=Unable to find an account with identifier {0} in the Central Repository."
        })
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

                // Set a default name
                personaPanel.setPersonaName(accountContainer.getAccount().getTypeSpecificID());

                // Set up each matching account. We don't know what type of account we have, so check all the types to 
                // find any matches.
                try {
                    boolean showErrorMessage = true;
                    for (CentralRepoAccount.CentralRepoAccountType type : CentralRepository.getInstance().getAllAccountTypes()) {
                        try {
                            // Try to load any matching accounts of this type. Throws an InvalidAccountIDException if the account is the
                            // wrong format (i.e., when we try to load email accounts for a phone number-type string).
                            CentralRepoAccount account = CentralRepository.getInstance().getAccount(type, accountContainer.getAccount().getTypeSpecificID());
                            if (account != null) {
                                personaPanel.addAccount(account, Bundle.MessageAccountPanel_account_justification(), Persona.Confidence.HIGH);
                                showErrorMessage = false;
                            } 
                        } catch (InvalidAccountIDException ex2) {
                            // These are expected when the account identifier doesn't match the format of the account type.
                        }
                    }
                    if(showErrorMessage) {
                         createPersonaDialog.setStartupPopupMessage(Bundle.MessageAccountPanel_id_not_found_in_cr(accountContainer.getAccount().getTypeSpecificID()));
                    }
                } catch (CentralRepoException ex) {
                    logger.log(Level.SEVERE, "Error looking up account types in the central repository", ex);
                }

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
