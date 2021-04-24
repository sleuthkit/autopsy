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
package org.sleuthkit.autopsy.centralrepository.persona;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import org.apache.commons.lang.StringUtils;
import org.openide.windows.TopComponent;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.RetainLocation;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoAccount;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoExaminer;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.Persona;
import org.sleuthkit.autopsy.centralrepository.datamodel.PersonaAccount;
import org.sleuthkit.autopsy.centralrepository.datamodel.PersonaAlias;
import org.sleuthkit.autopsy.centralrepository.datamodel.PersonaMetadata;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * JPanel for persona details
 */
@TopComponent.Description(preferredID = "PersonaDetailsTopComponent", persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "personadetails", openAtStartup = false)
@RetainLocation("personadetails")
@SuppressWarnings("PMD.SingularField")
public final class PersonaDetailsPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(PersonaDetailsPanel.class.getName());

    private PersonaDetailsMode mode;

    private final List<PAccount> accountsToAdd = new ArrayList<>();
    private final List<PMetadata> metadataToAdd = new ArrayList<>();
    private final List<PAlias> aliasesToAdd = new ArrayList<>();

    private final List<PersonaAccount> accountsToRemove = new ArrayList<>();
    private final List<PersonaMetadata> metadataToRemove = new ArrayList<>();
    private final List<PersonaAlias> aliasesToRemove = new ArrayList<>();

    private final Map<PersonaAccount, PAccount> accountsToEdit = new HashMap<>();
    private final Map<PersonaMetadata, PMetadata> metadataToEdit = new HashMap<>();
    private final Map<PersonaAlias, PAlias> aliasesToEdit = new HashMap<>();

    private Persona currentPersona;
    private List<PersonaAccount> currentAccounts = new ArrayList<>();
    private List<PersonaMetadata> currentMetadata = new ArrayList<>();
    private List<PersonaAlias> currentAliases = new ArrayList<>();
    private List<CorrelationCase> currentCases = new ArrayList<>();

    private PersonaDetailsTableModel accountsModel;
    private PersonaDetailsTableModel metadataModel;
    private PersonaDetailsTableModel aliasesModel;
    private PersonaDetailsTableModel casesModel;

    @Messages({
        "PersonaDetailsPanel_empty_justification_Title=Empty justification",
        "PersonaDetailsPanel_empty_justification_msg=The justification field cannot be empty",})
    public PersonaDetailsPanel() {
        initComponents();
        clear();

        // Accounts
        addAccountBtn.addActionListener((ActionEvent e) -> {
            new PersonaAccountDialog(this);
        });
        editAccountBtn.addActionListener((ActionEvent e) -> {
            int selectedRow = accountsTable.getSelectedRow();
            if (selectedRow != -1) {
                if (selectedRow >= currentAccounts.size()) {
                    PAccount acc = accountsToAdd.get(selectedRow - currentAccounts.size());
                    new PersonaAccountDialog(this, acc);
                } else {
                    PersonaAccount personaAccount = currentAccounts.get(selectedRow);
                    accountsToEdit.putIfAbsent(personaAccount, new PAccount(personaAccount.getAccount(), personaAccount.getJustification(), personaAccount.getConfidence()));
                    new PersonaAccountDialog(this, accountsToEdit.get(personaAccount));
                }
            }
        });
        deleteAccountBtn.addActionListener((ActionEvent e) -> {
            int selectedRow = accountsTable.getSelectedRow();
            if (selectedRow != -1) {
                // We're keeping accounts in two separate data structures
                if (selectedRow >= currentAccounts.size()) {
                    accountsToAdd.remove(selectedRow - currentAccounts.size());
                } else {
                    PersonaAccount toRemove = currentAccounts.get(selectedRow);
                    accountsToEdit.remove(toRemove);
                    accountsToRemove.add(toRemove);
                    currentAccounts.remove(toRemove);
                }
                updateAccountsTable();
            }
        });
        accountsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        accountsTable.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            handleSelectionChange(e, editAccountBtn, deleteAccountBtn, accountsTable);
        });

        // Metadata
        addMetadataBtn.addActionListener((ActionEvent e) -> {
            new PersonaMetadataDialog(this);
        });
        editMetadataBtn.addActionListener((ActionEvent e) -> {
            int selectedRow = metadataTable.getSelectedRow();
            if (selectedRow != -1) {
                if (selectedRow >= currentMetadata.size()) {
                    PMetadata md = metadataToAdd.get(selectedRow - currentMetadata.size());
                    new PersonaMetadataDialog(this, md);
                } else {
                    PersonaMetadata personaMetadata = currentMetadata.get(selectedRow);
                    metadataToEdit.putIfAbsent(personaMetadata, new PMetadata(personaMetadata.getName(), personaMetadata.getValue(), personaMetadata.getJustification(), personaMetadata.getConfidence()));
                    new PersonaMetadataDialog(this, metadataToEdit.get(personaMetadata));
                }
            }
        });
        deleteMetadataBtn.addActionListener((ActionEvent e) -> {
            int selectedRow = metadataTable.getSelectedRow();
            if (selectedRow != -1) {
                // We're keeping metadata in two separate data structures
                if (selectedRow >= currentMetadata.size()) {
                    metadataToAdd.remove(selectedRow - currentMetadata.size());
                } else {
                    PersonaMetadata toRemove = currentMetadata.get(selectedRow);
                    metadataToEdit.remove(toRemove);
                    metadataToRemove.add(toRemove);
                    currentMetadata.remove(toRemove);
                }
                updateMetadataTable();
            }
        });
        metadataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        metadataTable.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            handleSelectionChange(e, editMetadataBtn, deleteMetadataBtn, metadataTable);
        });

        // Aliases
        addAliasBtn.addActionListener((ActionEvent e) -> {
            new PersonaAliasDialog(this);
        });
        editAliasBtn.addActionListener((ActionEvent e) -> {
            int selectedRow = aliasesTable.getSelectedRow();
            if (selectedRow != -1) {
                if (selectedRow >= currentAliases.size()) {
                    PAlias pa = aliasesToAdd.get(selectedRow - currentAliases.size());
                    new PersonaAliasDialog(this, pa);
                } else {
                    PersonaAlias personaAlias = currentAliases.get(selectedRow);
                    aliasesToEdit.putIfAbsent(personaAlias, new PAlias(personaAlias.getAlias(), personaAlias.getJustification(), personaAlias.getConfidence()));
                    new PersonaAliasDialog(this, aliasesToEdit.get(personaAlias));
                }
            }
        });
        deleteAliasBtn.addActionListener((ActionEvent e) -> {
            int selectedRow = aliasesTable.getSelectedRow();
            if (selectedRow != -1) {
                // We're keeping aliases in two separate data structures
                if (selectedRow >= currentAliases.size()) {
                    aliasesToAdd.remove(selectedRow - currentAliases.size());
                } else {
                    PersonaAlias toRemove = currentAliases.get(selectedRow);
                    aliasesToEdit.remove(toRemove);
                    aliasesToRemove.add(toRemove);
                    currentAliases.remove(toRemove);
                }
                updateAliasesTable();
            }
        });
        aliasesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        aliasesTable.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            handleSelectionChange(e, editAliasBtn, deleteAliasBtn, aliasesTable);
        });
    }

    private void handleSelectionChange(ListSelectionEvent e, JButton editBtn, JButton deleteBtn, JTable table) {
        if (e.getValueIsAdjusting()) {
            return;
        }
        editBtn.setEnabled(mode != PersonaDetailsMode.VIEW && table.getSelectedRow() != -1);
        deleteBtn.setEnabled(mode != PersonaDetailsMode.VIEW && table.getSelectedRow() != -1);
    }

    void addEditExistingAccount(PersonaAccount account, String justification, Persona.Confidence confidence) {
        accountsToEdit.put(account, new PAccount(account.getAccount(), justification, confidence));
    }

    void addEditExistingMetadata(PersonaMetadata metadata, String justification, Persona.Confidence confidence) {
        metadataToEdit.put(metadata, new PMetadata(metadata.getName(), metadata.getValue(), justification, confidence));
    }

    void addEditExistingAlias(PersonaAlias alias, String justification, Persona.Confidence confidence) {
        aliasesToEdit.put(alias, new PAlias(alias.getAlias(), justification, confidence));
    }
    
    PersonaDetailsMode getMode() {
        return mode;
    }

    /**
     * A data bucket class for yet-to-be-created PersonaAccount
     */
    class PAccount {

        CentralRepoAccount account;
        String justification;
        Persona.Confidence confidence;

        PAccount(CentralRepoAccount account, String justification, Persona.Confidence confidence) {
            this.account = account;
            this.justification = justification;
            this.confidence = confidence;
        }
    }

    boolean accountExists(CentralRepoAccount account) {
        for (PersonaAccount acc : currentAccounts) {
            if (acc.getAccount().getId() == account.getId()) {
                return true;
            }
        }
        for (PAccount acc : accountsToAdd) {
            if (acc.account.getId() == account.getId()) {
                return true;
            }
        }
        return false;
    }

    public boolean addAccount(CentralRepoAccount account, String justification, Persona.Confidence confidence) {
        if (!accountExists(account)) {
            accountsToAdd.add(new PAccount(account, justification, confidence));
            updateAccountsTable();
            return true;
        }
        return false;
    }

    /**
     * A data bucket class for yet-to-be-created PersonaMetadata
     */
    class PMetadata {

        String name;
        String value;
        String justification;
        Persona.Confidence confidence;

        PMetadata(String name, String value, String justification, Persona.Confidence confidence) {
            this.name = name;
            this.value = value;
            this.justification = justification;
            this.confidence = confidence;
        }
    }

    boolean metadataExists(String name) {
        for (PersonaMetadata pm : currentMetadata) {
            if (pm.getName().equals(name)) {
                return true;
            }
        }
        for (PMetadata pm : metadataToAdd) {
            if (pm.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    boolean addMetadata(String name, String value, String justification, Persona.Confidence confidence) {
        if (!metadataExists(name)) {
            metadataToAdd.add(new PMetadata(name, value, justification, confidence));
            updateMetadataTable();
            return true;
        }
        return false;
    }

    /**
     * A data bucket class for yet-to-be-created PersonaAlias
     */
    class PAlias {

        String alias;
        String justification;
        Persona.Confidence confidence;

        PAlias(String alias, String justification, Persona.Confidence confidence) {
            this.alias = alias;
            this.justification = justification;
            this.confidence = confidence;
        }
    }

    boolean aliasExists(String alias) {
        for (PersonaAlias pa : currentAliases) {
            if (pa.getAlias().equals(alias)) {
                return true;
            }
        }
        for (PAlias pa : aliasesToAdd) {
            if (pa.alias.equals(alias)) {
                return true;
            }
        }
        return false;
    }

    boolean addAlias(String alias, String justification, Persona.Confidence confidence) {
        if (!aliasExists(alias)) {
            aliasesToAdd.add(new PAlias(alias, justification, confidence));
            updateAliasesTable();
            return true;
        }
        return false;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        detailsPanel = new javax.swing.JPanel();
        examinerLbl = new javax.swing.JLabel();
        examinerField = new javax.swing.JTextField();
        creationDateLbl = new javax.swing.JLabel();
        creationDateField = new javax.swing.JTextField();
        commentLbl = new javax.swing.JLabel();
        commentField = new javax.swing.JTextField();
        nameLbl = new javax.swing.JLabel();
        nameField = new javax.swing.JTextField();
        accountsLbl = new javax.swing.JLabel();
        accountsTablePane = new javax.swing.JScrollPane();
        accountsTable = new javax.swing.JTable();
        addAccountBtn = new javax.swing.JButton();
        editAccountBtn = new javax.swing.JButton();
        deleteAccountBtn = new javax.swing.JButton();
        metadataLabel = new javax.swing.JLabel();
        metadataTablePane = new javax.swing.JScrollPane();
        metadataTable = new javax.swing.JTable();
        addMetadataBtn = new javax.swing.JButton();
        editMetadataBtn = new javax.swing.JButton();
        deleteMetadataBtn = new javax.swing.JButton();
        aliasesLabel = new javax.swing.JLabel();
        aliasesTablePane = new javax.swing.JScrollPane();
        aliasesTable = new javax.swing.JTable();
        addAliasBtn = new javax.swing.JButton();
        editAliasBtn = new javax.swing.JButton();
        deleteAliasBtn = new javax.swing.JButton();
        casesLbl = new javax.swing.JLabel();
        casesTablePane = new javax.swing.JScrollPane();
        casesTable = new javax.swing.JTable();

        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentShown(java.awt.event.ComponentEvent evt) {
                formComponentShown(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(examinerLbl, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.examinerLbl.text")); // NOI18N

        examinerField.setEditable(false);
        examinerField.setText(org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.examinerField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(creationDateLbl, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.creationDateLbl.text")); // NOI18N

        creationDateField.setEditable(false);
        creationDateField.setText(org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.creationDateField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(commentLbl, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.commentLbl.text")); // NOI18N

        commentField.setEditable(false);
        commentField.setText(org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.commentField.text")); // NOI18N

        nameLbl.setFont(nameLbl.getFont().deriveFont(nameLbl.getFont().getStyle() | java.awt.Font.BOLD));
        org.openide.awt.Mnemonics.setLocalizedText(nameLbl, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.nameLbl.text")); // NOI18N

        nameField.setEditable(false);
        nameField.setText(org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.nameField.text")); // NOI18N

        accountsLbl.setFont(accountsLbl.getFont().deriveFont(accountsLbl.getFont().getStyle() | java.awt.Font.BOLD));
        org.openide.awt.Mnemonics.setLocalizedText(accountsLbl, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.accountsLbl.text")); // NOI18N

        accountsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {},
                {},
                {},
                {}
            },
            new String [] {

            }
        ));
        accountsTablePane.setViewportView(accountsTable);

        org.openide.awt.Mnemonics.setLocalizedText(addAccountBtn, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.addAccountBtn.text")); // NOI18N
        addAccountBtn.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(editAccountBtn, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.editAccountBtn.text")); // NOI18N
        editAccountBtn.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(deleteAccountBtn, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.deleteAccountBtn.text")); // NOI18N
        deleteAccountBtn.setEnabled(false);

        metadataLabel.setFont(metadataLabel.getFont().deriveFont(metadataLabel.getFont().getStyle() | java.awt.Font.BOLD));
        org.openide.awt.Mnemonics.setLocalizedText(metadataLabel, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.metadataLabel.text")); // NOI18N

        metadataTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {},
                {},
                {},
                {}
            },
            new String [] {

            }
        ));
        metadataTablePane.setViewportView(metadataTable);

        org.openide.awt.Mnemonics.setLocalizedText(addMetadataBtn, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.addMetadataBtn.text")); // NOI18N
        addMetadataBtn.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(editMetadataBtn, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.editMetadataBtn.text")); // NOI18N
        editMetadataBtn.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(deleteMetadataBtn, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.deleteMetadataBtn.text")); // NOI18N
        deleteMetadataBtn.setEnabled(false);

        aliasesLabel.setFont(aliasesLabel.getFont().deriveFont(aliasesLabel.getFont().getStyle() | java.awt.Font.BOLD));
        org.openide.awt.Mnemonics.setLocalizedText(aliasesLabel, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.aliasesLabel.text")); // NOI18N

        aliasesTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {},
                {},
                {},
                {}
            },
            new String [] {

            }
        ));
        aliasesTablePane.setViewportView(aliasesTable);

        org.openide.awt.Mnemonics.setLocalizedText(addAliasBtn, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.addAliasBtn.text")); // NOI18N
        addAliasBtn.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(editAliasBtn, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.editAliasBtn.text")); // NOI18N
        editAliasBtn.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(deleteAliasBtn, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.deleteAliasBtn.text")); // NOI18N
        deleteAliasBtn.setEnabled(false);

        casesLbl.setFont(casesLbl.getFont().deriveFont(casesLbl.getFont().getStyle() | java.awt.Font.BOLD));
        org.openide.awt.Mnemonics.setLocalizedText(casesLbl, org.openide.util.NbBundle.getMessage(PersonaDetailsPanel.class, "PersonaDetailsPanel.casesLbl.text")); // NOI18N

        casesTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {},
                {},
                {},
                {}
            },
            new String [] {

            }
        ));
        casesTablePane.setViewportView(casesTable);

        javax.swing.GroupLayout detailsPanelLayout = new javax.swing.GroupLayout(detailsPanel);
        detailsPanel.setLayout(detailsPanelLayout);
        detailsPanelLayout.setHorizontalGroup(
            detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(detailsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(accountsLbl, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(metadataLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(aliasesLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(casesLbl, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(detailsPanelLayout.createSequentialGroup()
                        .addComponent(nameLbl)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(nameField))
                    .addGroup(detailsPanelLayout.createSequentialGroup()
                        .addComponent(examinerLbl)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(examinerField, javax.swing.GroupLayout.PREFERRED_SIZE, 161, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(creationDateLbl)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(creationDateField))
                    .addGroup(detailsPanelLayout.createSequentialGroup()
                        .addComponent(commentLbl)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(commentField))
                    .addGroup(detailsPanelLayout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(aliasesTablePane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 595, Short.MAX_VALUE)
                            .addComponent(metadataTablePane, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(accountsTablePane, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(detailsPanelLayout.createSequentialGroup()
                                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(detailsPanelLayout.createSequentialGroup()
                                        .addComponent(addAliasBtn)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(editAliasBtn)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(deleteAliasBtn))
                                    .addGroup(detailsPanelLayout.createSequentialGroup()
                                        .addComponent(addMetadataBtn)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(editMetadataBtn)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(deleteMetadataBtn))
                                    .addGroup(detailsPanelLayout.createSequentialGroup()
                                        .addComponent(addAccountBtn)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(editAccountBtn)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(deleteAccountBtn)))
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addComponent(casesTablePane))))
                .addContainerGap())
        );
        detailsPanelLayout.setVerticalGroup(
            detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(detailsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(nameLbl)
                    .addComponent(nameField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(examinerLbl)
                    .addComponent(examinerField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(creationDateLbl)
                    .addComponent(creationDateField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(commentField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(commentLbl))
                .addGap(18, 18, 18)
                .addComponent(accountsLbl)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(accountsTablePane, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addAccountBtn)
                    .addComponent(deleteAccountBtn)
                    .addComponent(editAccountBtn))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(metadataLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(metadataTablePane, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addMetadataBtn)
                    .addComponent(deleteMetadataBtn)
                    .addComponent(editMetadataBtn))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(aliasesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(aliasesTablePane, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addAliasBtn)
                    .addComponent(deleteAliasBtn)
                    .addComponent(editAliasBtn))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(casesLbl)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(casesTablePane, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(detailsPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGap(0, 617, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 583, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(detailsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(0, 0, Short.MAX_VALUE)))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void formComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentShown
       
    }//GEN-LAST:event_formComponentShown

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel accountsLbl;
    private javax.swing.JTable accountsTable;
    private javax.swing.JScrollPane accountsTablePane;
    private javax.swing.JButton addAccountBtn;
    private javax.swing.JButton addAliasBtn;
    private javax.swing.JButton addMetadataBtn;
    private javax.swing.JLabel aliasesLabel;
    private javax.swing.JTable aliasesTable;
    private javax.swing.JScrollPane aliasesTablePane;
    private javax.swing.JLabel casesLbl;
    private javax.swing.JTable casesTable;
    private javax.swing.JScrollPane casesTablePane;
    private javax.swing.JTextField commentField;
    private javax.swing.JLabel commentLbl;
    private javax.swing.JTextField creationDateField;
    private javax.swing.JLabel creationDateLbl;
    private javax.swing.JButton deleteAccountBtn;
    private javax.swing.JButton deleteAliasBtn;
    private javax.swing.JButton deleteMetadataBtn;
    private javax.swing.JPanel detailsPanel;
    private javax.swing.JButton editAccountBtn;
    private javax.swing.JButton editAliasBtn;
    private javax.swing.JButton editMetadataBtn;
    private javax.swing.JTextField examinerField;
    private javax.swing.JLabel examinerLbl;
    private javax.swing.JLabel metadataLabel;
    private javax.swing.JTable metadataTable;
    private javax.swing.JScrollPane metadataTablePane;
    private javax.swing.JTextField nameField;
    private javax.swing.JLabel nameLbl;
    // End of variables declaration//GEN-END:variables

    @Messages({
        "PersonaDetailsPanel_load_exception_Title=Initialization failure",
        "PersonaDetailsPanel_load_exception_msg=Failed to load persona.",})
    private void loadPersona(Component parent, Persona persona) {
        String examiner;
        String creationDate;
        String comment;
        String name;
        Collection<PersonaAccount> accounts;
        Collection<PersonaMetadata> metadata;
        Collection<PersonaAlias> aliases;
        Collection<CorrelationCase> cases;
        try {
            examiner = persona.getExaminer().getLoginName();
            
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Date cDate = new Date(persona.getCreatedDate());
            creationDate = dateFormat.format(cDate);
            
            comment = persona.getComment();
            name = persona.getName();
            accounts = persona.getPersonaAccounts();
            metadata = persona.getMetadata();
            aliases = persona.getAliases();
            cases = persona.getCases();
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Failed to load persona", ex);
            JOptionPane.showMessageDialog(parent,
                    Bundle.PersonaDetailsPanel_load_exception_Title(),
                    Bundle.PersonaDetailsPanel_load_exception_msg(),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        this.currentPersona = persona;
        this.examinerField.setText(examiner);
        this.creationDateField.setText(creationDate);
        this.commentField.setText(comment);
        this.nameField.setText(name);
        this.currentAccounts.addAll(accounts);
        this.currentMetadata.addAll(metadata);
        this.currentAliases.addAll(aliases);
        this.currentCases.addAll(cases);
    }

    void clear() {
        currentPersona = null;
        examinerField.setText("");
        creationDateField.setText("");
        commentField.setText("");
        nameField.setText(mode == PersonaDetailsMode.CREATE ? Persona.getDefaultName() : "");
        currentAccounts = new ArrayList<>();
        currentMetadata = new ArrayList<>();
        currentAliases = new ArrayList<>();
        currentCases = new ArrayList<>();
        accountsToAdd.clear();
        metadataToAdd.clear();
        aliasesToAdd.clear();
        nameField.setEditable(false);
        commentField.setEditable(false);

        initializeFields();

        addAccountBtn.setEnabled(false);
        addMetadataBtn.setEnabled(false);
        addAliasBtn.setEnabled(false);
        deleteAccountBtn.setEnabled(false);
        deleteMetadataBtn.setEnabled(false);
        deleteAliasBtn.setEnabled(false);
        editAccountBtn.setEnabled(false);
        editMetadataBtn.setEnabled(false);
        editAliasBtn.setEnabled(false);
    }

    /**
     * Table model for persona details
     */
    final class PersonaDetailsTableModel extends DefaultTableModel {

        private static final long serialVersionUID = 1L;

        PersonaDetailsTableModel(Object[][] rows, String[] colNames) {
            super(rows, colNames);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }

    private void updateAccountsTable() {
        Object[][] rows = new Object[currentAccounts.size() + accountsToAdd.size()][2];
        int i = 0;
        for (PersonaAccount acc : currentAccounts) {
            rows[i] = new Object[]{
                acc.getAccount().getAccountType().getAcctType().getDisplayName(),
                acc.getAccount().getIdentifier()
            };
            i++;
        }
        for (PAccount acc : accountsToAdd) {
            rows[i] = new Object[]{
                acc.account.getAccountType().getAcctType().getDisplayName(),
                acc.account.getIdentifier()
            };
            i++;
        }
        accountsModel = new PersonaDetailsTableModel(
                rows,
                new String[]{"Type", "Identifier"}
        );
        accountsTable.setModel(accountsModel);

        // Formatting
        accountsTable.getColumnModel().getColumn(0).setMaxWidth(100);
        accountsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    }

    private void updateMetadataTable() {
        Object[][] rows = new Object[currentMetadata.size() + metadataToAdd.size()][2];
        int i = 0;
        for (PersonaMetadata md : currentMetadata) {
            rows[i] = new Object[]{md.getName(), md.getValue()};
            i++;
        }
        for (PMetadata md : metadataToAdd) {
            rows[i] = new Object[]{md.name, md.value};
            i++;
        }
        metadataModel = new PersonaDetailsTableModel(
                rows,
                new String[]{"Name", "Value"}
        );
        metadataTable.setModel(metadataModel);
    }

    private void updateAliasesTable() {
        Object[][] rows = new Object[currentAliases.size() + aliasesToAdd.size()][1];
        int i = 0;
        for (PersonaAlias alias : currentAliases) {
            rows[i] = new Object[]{alias.getAlias()};
            i++;
        }
        for (PAlias alias : aliasesToAdd) {
            rows[i] = new Object[]{alias.alias};
            i++;
        }
        aliasesModel = new PersonaDetailsTableModel(
                rows,
                new String[]{"Alias"}
        );
        aliasesTable.setModel(aliasesModel);
    }

    private void updateCasesTable() {
        Object[][] rows = new Object[currentCases.size()][1];
        int i = 0;
        for (CorrelationCase c : currentCases) {
            rows[i] = new Object[]{c.getDisplayName()};
            i++;
        }
        casesModel = new PersonaDetailsTableModel(
                rows,
                new String[]{"Case"}
        );
        casesTable.setModel(casesModel);
    }

    void configureEditComponents(boolean enabled) {
        commentField.setEditable(enabled);
        nameField.setEditable(enabled);
        addAccountBtn.setEnabled(enabled);
        addMetadataBtn.setEnabled(enabled);
        addAliasBtn.setEnabled(enabled);

        addAccountBtn.setVisible(enabled);
        editAccountBtn.setVisible(enabled);
        deleteAccountBtn.setVisible(enabled);
        addMetadataBtn.setVisible(enabled);
        editMetadataBtn.setVisible(enabled);
        deleteMetadataBtn.setVisible(enabled);
        addAliasBtn.setVisible(enabled);
        editAliasBtn.setVisible(enabled);
        deleteAliasBtn.setVisible(enabled);
    }

    void initializeFields() {
        if (mode == PersonaDetailsMode.CREATE) {
            try {
                CentralRepoExaminer examiner = CentralRepository.getInstance().getOrInsertExaminer(System.getProperty("user.name"));
                examinerField.setText(examiner.getLoginName());
            } catch (CentralRepoException e) {
                logger.log(Level.SEVERE, "Failed to access central repository", e);
                JOptionPane.showMessageDialog(this,
                        Bundle.PersonaDetailsPanel_CentralRepoErr_msg(),
                        Bundle.PersonaDetailsPanel_CentralRepoErr_Title(),
                        JOptionPane.ERROR_MESSAGE);
            }
        }
        updateAccountsTable();
        updateMetadataTable();
        updateAliasesTable();
        updateCasesTable();
    }

    void setMode(Component parent, PersonaDetailsMode mode, Persona persona) {
        clear();
        this.mode = mode;
        switch (mode) {
            case CREATE:
                configureEditComponents(true);
                break;
            case EDIT:
                loadPersona(parent, persona);
                configureEditComponents(true);
                break;
            case VIEW:
                loadPersona(parent, persona);
                configureEditComponents(false);
                break;
            default:
                logger.log(Level.WARNING, "Unsupported mode: {0}", mode);
                break;
        }
        initializeFields();
    }

    @Messages({
        "PersonaDetailsPanel_NotEnoughAccounts_msg=A persona needs at least one account.",
        "PersonaDetailsPanel_NotEnoughAccounts_Title=Missing account",
        "PersonaDetailsPanel_CentralRepoErr_msg=Failure to write to Central Repository.",
        "PersonaDetailsPanel_CentralRepoErr_Title=Central Repository failure",
        "PersonaDetailsPanel_EmptyName_msg=Persona name cannot be empty.",
        "PersonaDetailsPanel_EmptyName_Title=Empty persona name",
        "PersonaDetailsPanel_EmptyComment_msg=Persona comment cannot be empty.",
        "PersonaDetailsPanel_EmptyComment_Title=Empty persona comment",})
    Persona okHandler() {
        if (accountsToAdd.size() + currentAccounts.size() < 1) {
            JOptionPane.showMessageDialog(this,
                    Bundle.PersonaDetailsPanel_NotEnoughAccounts_msg(),
                    Bundle.PersonaDetailsPanel_NotEnoughAccounts_Title(),
                    JOptionPane.ERROR_MESSAGE);
            return null;

        }
        if (StringUtils.isBlank(commentField.getText())) {
            JOptionPane.showMessageDialog(this,
                    Bundle.PersonaDetailsPanel_EmptyComment_msg(),
                    Bundle.PersonaDetailsPanel_EmptyComment_Title(),
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        if (StringUtils.isBlank(nameField.getText())) {
            JOptionPane.showMessageDialog(this,
                    Bundle.PersonaDetailsPanel_EmptyName_msg(),
                    Bundle.PersonaDetailsPanel_EmptyName_Title(),
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }

        Persona ret = null;
        switch (mode) {
            case CREATE:
                try {
                    PAccount firstAccount = accountsToAdd.get(0);
                    ret = Persona.createPersonaForAccount(nameField.getText(),
                            commentField.getText(), Persona.PersonaStatus.ACTIVE, firstAccount.account,
                            firstAccount.justification, firstAccount.confidence);
                    for (int i = 1; i < accountsToAdd.size(); i++) {
                        ret.addAccount(accountsToAdd.get(i).account,
                                accountsToAdd.get(i).justification,
                                accountsToAdd.get(i).confidence);
                    }
                    for (PMetadata md : metadataToAdd) {
                        ret.addMetadata(md.name, md.value, md.justification, md.confidence);
                    }
                    for (PAlias pa : aliasesToAdd) {
                        ret.addAlias(pa.alias, pa.justification, pa.confidence);
                    }
                } catch (CentralRepoException e) {
                    logger.log(Level.SEVERE, "Failed to access central repository", e);
                    JOptionPane.showMessageDialog(this,
                            Bundle.PersonaDetailsPanel_CentralRepoErr_msg(),
                            Bundle.PersonaDetailsPanel_CentralRepoErr_Title(),
                            JOptionPane.ERROR_MESSAGE);
                    break;
                }
                break;
            case EDIT:
                try {
                    ret = currentPersona;
                    currentPersona.setComment(commentField.getText());
                    currentPersona.setName(nameField.getText());
                    for (PAccount acc : accountsToAdd) {
                        ret.addAccount(acc.account, acc.justification, acc.confidence);
                    }
                    for (PersonaAccount acc : accountsToRemove) {
                        ret.removeAccount(acc);
                    }
                    for (HashMap.Entry<PersonaAccount, PAccount> entry : accountsToEdit.entrySet()) {
                        ret.modifyAccount(entry.getKey(), entry.getValue().confidence, entry.getValue().justification);
                    }
                    for (PMetadata md : metadataToAdd) {
                        ret.addMetadata(md.name, md.value, md.justification, md.confidence);
                    }
                    for (PersonaMetadata md : metadataToRemove) {
                        ret.removeMetadata(md);
                    }
                    for (HashMap.Entry<PersonaMetadata, PMetadata> entry : metadataToEdit.entrySet()) {
                        ret.modifyMetadata(entry.getKey(), entry.getValue().confidence, entry.getValue().justification);
                    }
                    for (PAlias pa : aliasesToAdd) {
                        ret.addAlias(pa.alias, pa.justification, pa.confidence);
                    }
                    for (PersonaAlias pa : aliasesToRemove) {
                        ret.removeAlias(pa);
                    }
                    for (HashMap.Entry<PersonaAlias, PAlias> entry : aliasesToEdit.entrySet()) {
                        ret.modifyAlias(entry.getKey(), entry.getValue().confidence, entry.getValue().justification);
                    }
                } catch (CentralRepoException e) {
                    logger.log(Level.SEVERE, "Failed to access central repository", e);
                    JOptionPane.showMessageDialog(this,
                            Bundle.PersonaDetailsPanel_CentralRepoErr_msg(),
                            Bundle.PersonaDetailsPanel_CentralRepoErr_Title(),
                            JOptionPane.ERROR_MESSAGE);
                    break;
                }
                break;
            case VIEW:
                ret = currentPersona;
                break;
            default:
                logger.log(Level.SEVERE, "Unsupported mode: {0}", mode);
        }
        return ret;
    }

    /**
     * Sets the persona name field.
     *
     * @param name Persona name.
     */
    public void setPersonaName(String name) {
        nameField.setText(name);
    }

}
