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

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
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
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.InvalidAccountIDException;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * This class displays the TSK_CONTACT artifact.
 */
@ServiceProvider(service = ArtifactContentViewer.class)
public class ContactArtifactViewer extends javax.swing.JPanel implements ArtifactContentViewer {

    private final static Logger logger = Logger.getLogger(ContactArtifactViewer.class.getName());
    private static final long serialVersionUID = 1L;

    private GridBagLayout m_gridBagLayout = new GridBagLayout();
    private GridBagConstraints m_constraints = new GridBagConstraints();

    private JLabel personaSearchStatusLabel;

    private BlackboardArtifact contactArtifact;
    private String contactName;
    private String datasourceName;
    private String hostName;

    private List<BlackboardAttribute> phoneNumList = new ArrayList<>();
    private List<BlackboardAttribute> emailList = new ArrayList<>();
    private List<BlackboardAttribute> nameList = new ArrayList<>();
    private List<BlackboardAttribute> otherList = new ArrayList<>();
    private List<BlackboardAttribute> accountAttributesList = new ArrayList<>();

    private final static String DEFAULT_IMAGE_PATH = "/org/sleuthkit/autopsy/images/defaultContact.png";
    private final ImageIcon defaultImage;

    // A list of unique accounts matching the attributes of the contact artifact.
    private final List<CentralRepoAccount> contactUniqueAccountsList = new ArrayList<>();

    // A list of all unique personas and their account, found by searching on the 
    // account identifier attributes of the Contact artifact.
    private final Map<Persona, ArrayList<CentralRepoAccount>> contactUniquePersonasMap = new HashMap<>();

    private ContactPersonaSearcherTask personaSearchTask;

    /**
     * Creates new form ContactArtifactViewer
     */
    public ContactArtifactViewer() {
        initComponents();
        this.setBorder(new EmptyBorder(ContentViewerDefaults.getPanelInsets()));
        defaultImage = new ImageIcon(ContactArtifactViewer.class.getResource(DEFAULT_IMAGE_PATH));
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setToolTipText(""); // NOI18N
        setLayout(new java.awt.GridBagLayout());
    }// </editor-fold>//GEN-END:initComponents

    @Override
    public void setArtifact(BlackboardArtifact artifact) {
        // Reset the panel.
        resetComponent();

        if (artifact != null) {
            try {
                extractArtifactData(artifact);
            } catch (NoCurrentCaseException | TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Error getting attributes for artifact (artifact_id=%d, obj_id=%d)", artifact.getArtifactID(), artifact.getObjectID()), ex);
                return;
            }
            updateView();
        }
        this.setLayout(this.m_gridBagLayout);
        this.revalidate();
        this.repaint();
    }

    @Override
    public Component getComponent() {
        // Slap a vertical scrollbar on the panel.
        return new JScrollPane(this, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    }

    @Override
    public boolean isSupported(BlackboardArtifact artifact) {
        return (artifact != null)
                && (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT.getTypeID());
    }

    /**
     * Extracts data from the artifact to be displayed in the panel.
     *
     * @param artifact Artifact to show.
     *
     * @throws TskCoreException
     */
    private void extractArtifactData(BlackboardArtifact artifact) throws NoCurrentCaseException, TskCoreException {

        this.contactArtifact = artifact;

        phoneNumList = new ArrayList<>();
        emailList = new ArrayList<>();
        nameList = new ArrayList<>();
        otherList = new ArrayList<>();
        accountAttributesList = new ArrayList<>();

        // Get all the attributes and group them by the section panels they go in
        for (BlackboardAttribute bba : contactArtifact.getAttributes()) {
            if (bba.getAttributeType().getTypeName().startsWith("TSK_PHONE")) {
                phoneNumList.add(bba);
                accountAttributesList.add(bba);
            } else if (bba.getAttributeType().getTypeName().startsWith("TSK_EMAIL")) {
                emailList.add(bba);
                accountAttributesList.add(bba);
            } else if (bba.getAttributeType().getTypeName().startsWith("TSK_NAME")) {
                nameList.add(bba);
            } else {
                otherList.add(bba);
                if (bba.getAttributeType().getTypeName().equalsIgnoreCase("TSK_ID")) {
                    accountAttributesList.add(bba);
                }
            }
        }

        datasourceName = contactArtifact.getDataSource().getName();

        hostName = Optional.ofNullable(Case.getCurrentCaseThrows().getSleuthkitCase().getHostManager().getHostByDataSource((DataSource) contactArtifact.getDataSource()))
                .map(h -> h.getName())
                .orElse(null);
    }

    /**
     * Updates the view with the data extracted from the artifact.
     */
    private void updateView() {

        // Update contact name, image, phone numbers
        updateContactDetails();

        // update artifact source panel
        updateSource();

        // show a empty Personas panel and kick off a serch for personas
        initiatePersonasSearch();

    }

    /**
     * Updates the view with contact's details.
     */
    @NbBundle.Messages({
        "ContactArtifactViewer_phones_header=Phone",
        "ContactArtifactViewer_emails_header=Email",
        "ContactArtifactViewer_others_header=Other",})
    private void updateContactDetails() {

        // update image and name.
        updateContactImage(m_gridBagLayout, m_constraints);
        updateContactName(m_gridBagLayout, m_constraints);

        // update contact attributes sections
        updateContactMethodSection(phoneNumList, Bundle.ContactArtifactViewer_phones_header(), m_gridBagLayout, m_constraints);
        updateContactMethodSection(emailList, Bundle.ContactArtifactViewer_emails_header(), m_gridBagLayout, m_constraints);
        updateContactMethodSection(otherList, Bundle.ContactArtifactViewer_others_header(), m_gridBagLayout, m_constraints);
    }

    /**
     * Updates the contact image in the view.
     *
     * @param contactPanelLayout      Panel layout.
     * @param contactPanelConstraints Layout constraints.
     *
     */
    @NbBundle.Messages({
        "ContactArtifactViewer.contactImage.text=",})
    private void updateContactImage(GridBagLayout contactPanelLayout, GridBagConstraints contactPanelConstraints) {
        // place the image on the top right corner
        Insets savedInsets = contactPanelConstraints.insets;
        contactPanelConstraints.gridy = 0;
        contactPanelConstraints.gridx = 0;
        contactPanelConstraints.insets = new Insets(0, 0, ContentViewerDefaults.getLineSpacing(), 0);
        int prevGridWidth = contactPanelConstraints.gridwidth;
        contactPanelConstraints.gridwidth = 3;
        contactPanelConstraints.anchor = GridBagConstraints.LINE_START;

        javax.swing.JLabel contactImage = new javax.swing.JLabel();
        contactImage.setIcon(getImageFromArtifact(contactArtifact));
        contactImage.setText(Bundle.ContactArtifactViewer_contactImage_text());

        // add image to top left corner of the page.
        CommunicationArtifactViewerHelper.addComponent(this, contactPanelLayout, contactPanelConstraints, contactImage);
        CommunicationArtifactViewerHelper.addLineEndGlue(this, contactPanelLayout, contactPanelConstraints);
        contactPanelConstraints.gridy++;

        contactPanelConstraints.gridwidth = prevGridWidth;
        contactPanelConstraints.insets = savedInsets;
    }

    /**
     * Updates the contact name in the view.
     *
     * @param contactPanelLayout      Panel layout.
     * @param contactPanelConstraints Layout constraints.
     *
     */
    @NbBundle.Messages({
        "ContactArtifactViewer_contactname_unknown=Unknown",})
    private void updateContactName(GridBagLayout contactPanelLayout, GridBagConstraints contactPanelConstraints) {

        boolean foundName = false;
        for (BlackboardAttribute bba : this.nameList) {
            if (StringUtils.isEmpty(bba.getValueString()) == false) {
                contactName = bba.getDisplayString();

                CommunicationArtifactViewerHelper.addHeader(this, contactPanelLayout, contactPanelConstraints, 0, contactName);
                foundName = true;
                break;
            }
        }
        if (foundName == false) {
            CommunicationArtifactViewerHelper.addHeader(this, contactPanelLayout, contactPanelConstraints, ContentViewerDefaults.getSectionSpacing(), Bundle.ContactArtifactViewer_contactname_unknown());
        }
    }

    /**
     * Updates the view by displaying the given list of attributes in the given
     * section panel.
     *
     * @param sectionAttributesList   List of attributes to display.
     * @param sectionHeader           Section name label.
     * @param contactPanelLayout      Panel layout.
     * @param contactPanelConstraints Layout constraints.
     *
     */
    private void updateContactMethodSection(List<BlackboardAttribute> sectionAttributesList, String sectionHeader, GridBagLayout contactPanelLayout, GridBagConstraints contactPanelConstraints) {

        // If there are no attributes for this section, do nothing
        if (sectionAttributesList.isEmpty()) {
            return;
        }

        CommunicationArtifactViewerHelper.addHeader(this, contactPanelLayout, contactPanelConstraints, ContentViewerDefaults.getSectionSpacing(), sectionHeader);
        for (BlackboardAttribute bba : sectionAttributesList) {
            CommunicationArtifactViewerHelper.addKey(this, contactPanelLayout, contactPanelConstraints, bba.getAttributeType().getDisplayName());
            CommunicationArtifactViewerHelper.addValue(this, contactPanelLayout, contactPanelConstraints, bba.getDisplayString());
        }
    }

    /**
     * Updates the source section.
     */
    @NbBundle.Messages({
        "ContactArtifactViewer_heading_Source=Source",
        "ContactArtifactViewer_label_datasource=Data Source",
        "ContactArtifactViewer_label_host=Host",})
    private void updateSource() {
        CommunicationArtifactViewerHelper.addHeader(this, this.m_gridBagLayout, m_constraints, ContentViewerDefaults.getSectionSpacing(), Bundle.ContactArtifactViewer_heading_Source());
        CommunicationArtifactViewerHelper.addKey(this, m_gridBagLayout, m_constraints, Bundle.ContactArtifactViewer_label_host());
        CommunicationArtifactViewerHelper.addValue(this, m_gridBagLayout, m_constraints, StringUtils.defaultString(hostName));
        CommunicationArtifactViewerHelper.addKey(this, m_gridBagLayout, m_constraints, Bundle.ContactArtifactViewer_label_datasource());
        CommunicationArtifactViewerHelper.addValue(this, m_gridBagLayout, m_constraints, datasourceName);
    }

    /**
     * Initiates a search for Personas for the accounts associated with the
     * Contact.
     *
     */
    @NbBundle.Messages({
        "ContactArtifactViewer_persona_header=Persona",
        "ContactArtifactViewer_persona_searching=Searching...",
        "ContactArtifactViewer_cr_disabled_message=Enable Central Repository to view, create and edit personas.",
        "ContactArtifactViewer_persona_unknown=Unknown"
    })
    private void initiatePersonasSearch() {

        // add a section header 
        JLabel personaHeader = CommunicationArtifactViewerHelper.addHeader(this, m_gridBagLayout, m_constraints, ContentViewerDefaults.getSectionSpacing(), Bundle.ContactArtifactViewer_persona_header());

        m_constraints.gridy++;

        // add a status label
        String personaStatusLabelText = CentralRepository.isEnabled()
                ? Bundle.ContactArtifactViewer_persona_searching()
                : Bundle.ContactArtifactViewer_persona_unknown();

        this.personaSearchStatusLabel = new javax.swing.JLabel();
        personaSearchStatusLabel.setText(personaStatusLabelText);
        personaSearchStatusLabel.setFont(ContentViewerDefaults.getMessageFont());

        m_constraints.gridx = 0;
        m_constraints.anchor = GridBagConstraints.LINE_START;

        CommunicationArtifactViewerHelper.addComponent(this, m_gridBagLayout, m_constraints, personaSearchStatusLabel);

        if (CentralRepository.isEnabled()) {
            // Kick off a background task to serach for personas for the contact
            personaSearchTask = new ContactPersonaSearcherTask(contactArtifact);
            personaSearchTask.execute();
        } else {
            personaHeader.setEnabled(false);
            personaSearchStatusLabel.setEnabled(false);

            Insets messageInsets = new Insets(ContentViewerDefaults.getSectionSpacing(), 0, ContentViewerDefaults.getLineSpacing(), 0);
            CommunicationArtifactViewerHelper.addMessageRow(this, m_gridBagLayout, messageInsets, m_constraints, Bundle.ContactArtifactViewer_cr_disabled_message());
            m_constraints.gridy++;

            CommunicationArtifactViewerHelper.addPageEndGlue(this, m_gridBagLayout, this.m_constraints);
        }

    }

    /**
     * Updates the Persona panel with the gathered persona information.
     */
    private void updatePersonas() {

        // Remove the "Searching....." label
        this.remove(personaSearchStatusLabel);

        m_constraints.gridx = 0;
        if (contactUniquePersonasMap.isEmpty()) {
            // No persona found - show a button to create one.
            showPersona(null, 0, Collections.emptyList(), this.m_gridBagLayout, this.m_constraints);
        } else {
            int matchCounter = 0;
            for (Map.Entry<Persona, ArrayList<CentralRepoAccount>> entry : contactUniquePersonasMap.entrySet()) {
                List<CentralRepoAccount> missingAccounts = new ArrayList<>();
                ArrayList<CentralRepoAccount> personaAccounts = entry.getValue();
                matchCounter++;

                // create a list of accounts missing from this persona
                for (CentralRepoAccount account : contactUniqueAccountsList) {
                    if (personaAccounts.contains(account) == false) {
                        missingAccounts.add(account);
                    }
                }

                showPersona(entry.getKey(), matchCounter, missingAccounts, m_gridBagLayout, m_constraints);
                m_constraints.gridy += 2;
            }
        }

        // add veritcal glue at the end
        CommunicationArtifactViewerHelper.addPageEndGlue(this, m_gridBagLayout, this.m_constraints);

        // redraw the panel
        this.setLayout(this.m_gridBagLayout);
        this.revalidate();
        this.repaint();
    }

    /**
     * Displays the given persona in the persona panel.
     *
     * @param persona             Persona to display.
     * @param matchNumber         Number of matches.
     * @param missingAccountsList List of contact accounts this persona may be
     *                            missing.
     * @param gridBagLayout       Layout to use.
     * @param constraints         layout constraints.
     *
     * @throws CentralRepoException
     */
    @NbBundle.Messages({
        "ContactArtifactViewer_persona_label=Persona ",
        "ContactArtifactViewer_persona_no_match=No matches found",
        "ContactArtifactViewer_persona_button_view=View",
        "ContactArtifactViewer_persona_button_new=Create",
        "ContactArtifactViewer_persona_match_num=Match ",
        "ContactArtifactViewer_missing_account_label=Missing contact account",
        "ContactArtifactViewer_found_all_accounts_label=All accounts found."
    })
    private void showPersona(Persona persona, int matchNumber, List<CentralRepoAccount> missingAccountsList, GridBagLayout gridBagLayout, GridBagConstraints constraints) {

        // save the original insets
        Insets savedInsets = constraints.insets;

        // Add a Match X label in col 0.
        constraints.gridx = 0;
        javax.swing.JLabel matchNumberLabel = CommunicationArtifactViewerHelper.addKey(this, gridBagLayout, constraints, String.format("%s %d", Bundle.ContactArtifactViewer_persona_match_num(), matchNumber).trim());

        javax.swing.JLabel personaNameLabel = new javax.swing.JLabel();
        javax.swing.JButton personaButton = new javax.swing.JButton();

        String personaName;
        String personaButtonText;
        ActionListener personaButtonListener;
        if (persona != null) {
            personaName = persona.getName();
            personaButtonText = Bundle.ContactArtifactViewer_persona_button_view();
            personaButtonListener = new ViewPersonaButtonListener(this, persona);
        } else {
            matchNumberLabel.setVisible(false);
            personaName = Bundle.ContactArtifactViewer_persona_no_match();
            personaButtonText = Bundle.ContactArtifactViewer_persona_button_new();
            personaButtonListener = new CreatePersonaButtonListener(this, new PersonaUIComponents(personaNameLabel, personaButton));
        }

        //constraints.gridwidth = 1;  // TBD: this may not be needed if we use single panel
        constraints.gridx++;
        constraints.insets = new Insets(0, ContentViewerDefaults.getColumnSpacing(), ContentViewerDefaults.getLineSpacing(), 0);
        constraints.anchor = GridBagConstraints.LINE_START;
        personaNameLabel.setText(personaName);
        gridBagLayout.setConstraints(personaNameLabel, constraints);
        CommunicationArtifactViewerHelper.addComponent(this, gridBagLayout, constraints, personaNameLabel);
        //personasPanel.add(personaNameLabel);

        // Add a Persona action button
        constraints.gridx++;
        //constraints.gridwidth = 1;
        personaButton.setText(personaButtonText);
        personaButton.addActionListener(personaButtonListener);

        // Shirnk the button height.
        personaButton.setMargin(new Insets(0, 5, 0, 5));
        constraints.insets = new Insets(0, ContentViewerDefaults.getColumnSpacing(), ContentViewerDefaults.getLineSpacing(), 0);
        constraints.anchor = GridBagConstraints.LINE_START;
        gridBagLayout.setConstraints(personaButton, constraints);
        CommunicationArtifactViewerHelper.addComponent(this, gridBagLayout, constraints, personaButton);
        CommunicationArtifactViewerHelper.addLineEndGlue(this, gridBagLayout, constraints);

        constraints.insets = savedInsets;

        // if we have a persona, indicate if any of the contact's accounts  are missing from it.
        if (persona != null) {
            if (missingAccountsList.isEmpty()) {
                constraints.gridy++;
                constraints.gridx = 1;
                //constraints.insets = labelInsets;

                javax.swing.JLabel accountsStatus = new javax.swing.JLabel(Bundle.ContactArtifactViewer_found_all_accounts_label());
                constraints.insets = new Insets(0, ContentViewerDefaults.getColumnSpacing(), ContentViewerDefaults.getLineSpacing(), 0);
                constraints.anchor = GridBagConstraints.LINE_START;
                CommunicationArtifactViewerHelper.addComponent(this, gridBagLayout, constraints, accountsStatus);
                constraints.insets = savedInsets;

                CommunicationArtifactViewerHelper.addLineEndGlue(this, gridBagLayout, constraints);
            } else {
                // show missing accounts.
                for (CentralRepoAccount missingAccount : missingAccountsList) {
                    //constraints.weightx = 0;
                    constraints.gridx = 0;
                    constraints.gridy++;

                    // this needs an extra indent
                    CommunicationArtifactViewerHelper.addKeyAtCol(this, gridBagLayout, constraints, Bundle.ContactArtifactViewer_missing_account_label(), 1);
                    constraints.insets = savedInsets;

                    CommunicationArtifactViewerHelper.addValueAtCol(this, gridBagLayout, constraints, missingAccount.getIdentifier(), 2);
                }
            }
        }

        // restore insets
        constraints.insets = savedInsets;
    }

    /**
     * Resets all artifact specific state.
     */
    private void resetComponent() {

        contactArtifact = null;
        contactName = null;
        datasourceName = null;

        contactUniqueAccountsList.clear();
        contactUniquePersonasMap.clear();

        phoneNumList.clear();
        emailList.clear();
        nameList.clear();
        otherList.clear();
        accountAttributesList.clear();

        if (personaSearchTask != null) {
            personaSearchTask.cancel(Boolean.TRUE);
            personaSearchTask = null;
        }

        // clear the panel 
        this.removeAll();
        this.setLayout(null);

        m_gridBagLayout = new GridBagLayout();
        m_constraints = new GridBagConstraints();

        m_constraints.anchor = GridBagConstraints.LINE_START;
        m_constraints.gridy = 0;
        m_constraints.gridx = 0;
        m_constraints.weighty = 0.0;
        m_constraints.weightx = 0.0;    // keep components fixed horizontally.
        m_constraints.insets = new java.awt.Insets(0, ContentViewerDefaults.getSectionIndent(), 0, 0);
        m_constraints.fill = GridBagConstraints.NONE;

    }

    /**
     * Gets an image from a TSK_CONTACT artifact.
     *
     * @param artifact
     *
     * @return Image from a TSK_CONTACT artifact or default image if none was
     *         found or the artifact is not a TSK_CONTACT
     */
    private ImageIcon getImageFromArtifact(BlackboardArtifact artifact) {
        ImageIcon imageIcon = defaultImage;

        if (artifact == null) {
            return imageIcon;
        }

        BlackboardArtifact.ARTIFACT_TYPE artifactType = BlackboardArtifact.ARTIFACT_TYPE.fromID(artifact.getArtifactTypeID());
        if (artifactType != BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT) {
            return imageIcon;
        }

        try {
            for (Content content : artifact.getChildren()) {
                if (content instanceof AbstractFile) {
                    AbstractFile file = (AbstractFile) content;

                    try {
                        BufferedImage image = ImageIO.read(new File(file.getLocalAbsPath()));
                        imageIcon = new ImageIcon(image);
                        break;
                    } catch (IOException ex) {
                        // ImageIO.read will throw an IOException if file is not an image
                        // therefore we don't need to report this exception just try
                        // the next file.
                    }
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("Unable to load image for contact: %d", artifact.getId()), ex);
        }

        return imageIcon;
    }

    /**
     * Thread to search for a personas for all account identifier attributes for
     * a contact.
     */
    private class ContactPersonaSearcherTask extends SwingWorker<Map<Persona, ArrayList<CentralRepoAccount>>, Void> {

        private final BlackboardArtifact artifact;
        private final List<CentralRepoAccount> uniqueAccountsList = new ArrayList<>();

        /**
         * Creates a persona searcher task.
         *
         * @param accountAttributesList List of attributes that may map to
         *                              accounts.
         */
        ContactPersonaSearcherTask(BlackboardArtifact artifact) {
            this.artifact = artifact;
        }

        @Override
        protected Map<Persona, ArrayList<CentralRepoAccount>> doInBackground() throws Exception {

            Map<Persona, ArrayList<CentralRepoAccount>> uniquePersonas = new HashMap<>();
            CommunicationsManager commManager = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager();
            List<Account> contactAccountsList = commManager.getAccountsRelatedToArtifact(artifact);

            for (Account account : contactAccountsList) {
                try {
                    if (isCancelled()) {
                        return new HashMap<>();
                    }

                    // make a list of all unique accounts for this contact
                    if (!account.getAccountType().equals(Account.Type.DEVICE)) {
                        Optional<CentralRepoAccount.CentralRepoAccountType> optCrAccountType = CentralRepository.getInstance().getAccountTypeByName(account.getAccountType().getTypeName());
                        if (optCrAccountType.isPresent()) {
                            CentralRepoAccount crAccount = CentralRepository.getInstance().getAccount(optCrAccountType.get(), account.getTypeSpecificID());

                            if (crAccount != null && uniqueAccountsList.contains(crAccount) == false) {
                                uniqueAccountsList.add(crAccount);
                            }
                        }
                    }

                    Collection<PersonaAccount> personaAccounts = PersonaAccount.getPersonaAccountsForAccount(account);
                    if (personaAccounts != null && !personaAccounts.isEmpty()) {
                        // get personas for the account
                        Collection<Persona> personas
                                = personaAccounts
                                        .stream()
                                        .map(PersonaAccount::getPersona)
                                        .collect(Collectors.toList());

                        // make a list of unique personas, along with all their accounts
                        for (Persona persona : personas) {
                            if (uniquePersonas.containsKey(persona) == false) {
                                Collection<CentralRepoAccount> accounts = persona.getPersonaAccounts()
                                        .stream()
                                        .map(PersonaAccount::getAccount)
                                        .collect(Collectors.toList());

                                ArrayList<CentralRepoAccount> personaAccountsList = new ArrayList<>(accounts);
                                uniquePersonas.put(persona, personaAccountsList);
                            }
                        }
                    }
                } catch (InvalidAccountIDException ex) {
                    // Do nothing, the account has an identifier that not an
                    // acceptable format for the cr.
                }
            }

            return uniquePersonas;
        }

        @Override
        protected void done() {

            Map<Persona, ArrayList<CentralRepoAccount>> personasMap;
            try {
                personasMap = super.get();

                if (this.isCancelled()) {
                    return;
                }

                contactUniquePersonasMap.clear();
                contactUniquePersonasMap.putAll(personasMap);
                contactUniqueAccountsList.clear();
                contactUniqueAccountsList.addAll(uniqueAccountsList);

                updatePersonas();

            } catch (CancellationException ex) {
                logger.log(Level.INFO, "Persona searching was canceled."); //NON-NLS
            } catch (InterruptedException ex) {
                logger.log(Level.INFO, "Persona searching was interrupted."); //NON-NLS
            } catch (ExecutionException ex) {
                logger.log(Level.SEVERE, "Fatal error during Persona search.", ex); //NON-NLS
            }

        }
    }

    /**
     * A wrapper class that bags the UI components that need to be updated when
     * a persona search task or a create dialog returns.
     */
    private class PersonaUIComponents {

        private final JLabel personaNameLabel;
        private final JButton personaActionButton;

        /**
         * Constructor.
         *
         * @param personaNameLabel    Persona name label.
         * @param personaActionButton Persona action button.
         */
        PersonaUIComponents(JLabel personaNameLabel, JButton personaActionButton) {
            this.personaNameLabel = personaNameLabel;
            this.personaActionButton = personaActionButton;
        }

        /**
         * Returns persona name label.
         *
         * @return Persona name label.
         */
        public JLabel getPersonaNameLabel() {
            return personaNameLabel;
        }

        /**
         * Returns persona action button.
         *
         * @return Persona action button.
         */
        public JButton getPersonaActionButton() {
            return personaActionButton;
        }
    }

    /**
     * Action listener for Create persona button.
     */
    private class CreatePersonaButtonListener implements ActionListener {

        private final Component parentComponent;
        private final PersonaUIComponents personaUIComponents;

        /**
         * Constructs a listener for Create persona button..
         *
         * @param personaUIComponents UI components.
         */
        CreatePersonaButtonListener(Component parentComponent, PersonaUIComponents personaUIComponents) {
            this.personaUIComponents = personaUIComponents;
            this.parentComponent = parentComponent;
        }

        @NbBundle.Messages({
            "ContactArtifactViewer_persona_account_justification=Account found in Contact artifact",
            "# {0} - accountIdentifer",
            "ContactArtifactViewer_id_not_found_in_cr=Unable to find account(s) associated with contact {0} in the Central Repository."
        })

        @Override
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            // Launch the Persona Create dialog - do not display immediately
            PersonaDetailsDialog createPersonaDialog = new PersonaDetailsDialog(parentComponent,
                    PersonaDetailsMode.CREATE, null, new PersonaCreateCallbackImpl(parentComponent, personaUIComponents), false);

            // Pre populate the persona name and accounts if we have them.
            PersonaDetailsPanel personaPanel = createPersonaDialog.getDetailsPanel();

            if (contactName != null) {
                personaPanel.setPersonaName(contactName);
            }

            // pass the list of accounts to the dialog
            for (CentralRepoAccount account : contactUniqueAccountsList) {
                personaPanel.addAccount(account, Bundle.ContactArtifactViewer_persona_account_justification(), Persona.Confidence.HIGH);
            }

            if (contactName != null && contactUniqueAccountsList.isEmpty()) {
                createPersonaDialog.setStartupPopupMessage(Bundle.ContactArtifactViewer_id_not_found_in_cr(contactName));
            }

            // display the dialog now
            createPersonaDialog.display();
        }
    }

    /**
     * Action listener for View persona button.
     */
    private class ViewPersonaButtonListener implements ActionListener {

        private final Persona persona;
        private final Component parentComponent;

        /**
         * Creates listener for View persona button.
         *
         * @param persona
         */
        ViewPersonaButtonListener(Component parentComponent, Persona persona) {
            this.persona = persona;
            this.parentComponent = parentComponent;
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
        private final PersonaUIComponents personaUIComponents;

        /**
         * Creates a callback to handle new persona creation.
         *
         * @param personaUIComponents UI Components.
         */
        PersonaCreateCallbackImpl(Component parentComponent, PersonaUIComponents personaUIComponents) {
            this.parentComponent = parentComponent;
            this.personaUIComponents = personaUIComponents;
        }

        @Override
        public void callback(Persona persona) {
            JButton personaButton = personaUIComponents.getPersonaActionButton();
            if (persona != null) {
                // update the persona name label with newly created persona, 
                // and change the button to a "View" button
                personaUIComponents.getPersonaNameLabel().setText(persona.getName());
                personaUIComponents.getPersonaActionButton().setText(Bundle.ContactArtifactViewer_persona_button_view());

                // replace action listener with a View button listener
                for (ActionListener act : personaButton.getActionListeners()) {
                    personaButton.removeActionListener(act);
                }
                personaButton.addActionListener(new ViewPersonaButtonListener(parentComponent, persona));

            }

            personaButton.getParent().revalidate();
            personaButton.getParent().repaint();
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

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
