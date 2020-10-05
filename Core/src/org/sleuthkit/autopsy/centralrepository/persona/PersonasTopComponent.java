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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.AncestorListener;
import javax.swing.event.AncestorEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.RetainLocation;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.Persona;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Top component for the Personas tool
 *
 */
@TopComponent.Description(preferredID = "PersonasTopComponent", persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "personas", openAtStartup = false)
@RetainLocation("personas")
@SuppressWarnings("PMD.SingularField")
public final class PersonasTopComponent extends TopComponent {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(PersonasTopComponent.class.getName());

    private List<Persona> currentResults = null;
    private Persona selectedPersona = null;

    @Messages({
        "PersonasTopComponent_Name=Personas",
        "PersonasTopComponent_delete_exception_Title=Delete failure",
        "PersonasTopComponent_delete_exception_msg=Failed to delete persona.",
        "PersonasTopComponent_delete_confirmation_Title=Are you sure?",
        "PersonasTopComponent_delete_confirmation_msg=Are you sure you want to delete this persona?",})
    public PersonasTopComponent() {
        initComponents();
        setName(Bundle.PersonasTopComponent_Name());

        searchBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                executeSearch();
            }
        });

        editBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                new PersonaDetailsDialog(PersonasTopComponent.this,
                        PersonaDetailsMode.EDIT, selectedPersona, new CreateEditCallbackImpl());
            }
        });

        createBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                new PersonaDetailsDialog(PersonasTopComponent.this,
                        PersonaDetailsMode.CREATE, selectedPersona, new CreateEditCallbackImpl());
            }
        });

        deleteBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                NotifyDescriptor confirm = new NotifyDescriptor.Confirmation(
                        Bundle.PersonasTopComponent_delete_confirmation_msg(),
                        Bundle.PersonasTopComponent_delete_confirmation_Title(),
                        NotifyDescriptor.YES_NO_OPTION);
                DialogDisplayer.getDefault().notify(confirm);
                if (confirm.getValue().equals(NotifyDescriptor.YES_OPTION)) {
                    try {
                        if (selectedPersona != null) {
                            selectedPersona.delete();
                        }
                    } catch (CentralRepoException ex) {
                        logger.log(Level.SEVERE, "Failed to delete persona: " + selectedPersona.getName(), ex);
                        JOptionPane.showMessageDialog(PersonasTopComponent.this,
                                Bundle.PersonasTopComponent_delete_exception_msg(),
                                Bundle.PersonasTopComponent_delete_exception_Title(),
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    executeSearch();
                }
            }
        });

        // Results table
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                handleSelectionChange(e);
            }
        });
        
        createAccountBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                new CreatePersonaAccountDialog(detailsPanel);
            }
        });

        /**
         * Listens for when this component will be rendered and executes a
         * search to update gui when it is displayed.
         */
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                resetSearchControls();
                setKeywordSearchEnabled(false, true);
            }
        });
    }

    /**
     * Callback method for the create/edit mode of the PersonaDetailsDialog
     */
    class CreateEditCallbackImpl implements PersonaDetailsDialogCallback {

        @Override
        public void callback(Persona persona) {
            if (persona != null) {
                searchField.setText("");
                executeSearch();
                int personaRow = currentResults.indexOf(persona);
                resultsTable.getSelectionModel().setSelectionInterval(personaRow, personaRow);
                handleSelectionChange();
            }
            createBtn.setEnabled(true);
        }
    }

    /**
     * Resets search controls to default state.
     */
    private void resetSearchControls() {
        searchField.setText("");
        searchNameRadio.setSelected(true);
        searchAccountRadio.setSelected(false);
    }

    /**
     * Sets up the GUI for appropriate state for keyword search enabled state.
     *
     * @param selected    Whether or not keyword search is enabled.
     * @param setFilterCb Whether or not the filter checkbox should be
     *                    manipulated as a part of this change.
     */
    private void setKeywordSearchEnabled(boolean selected, boolean setFilterCb) {
        if (setFilterCb && cbFilterByKeyword.isSelected() != selected) {
            cbFilterByKeyword.setSelected(selected);
        }

        searchField.setEnabled(selected);
        searchNameRadio.setEnabled(selected);
        searchAccountRadio.setEnabled(selected);

        executeSearch();
    }

    void setPersona(int index) {
        Persona persona = currentResults.get(index);
        selectedPersona = persona;
        editBtn.setEnabled(true);
        deleteBtn.setEnabled(true);
    }

    /**
     * Table model for the persona search results
     */
    final class PersonaFilterTableModel extends DefaultTableModel {

        private static final long serialVersionUID = 1L;

        PersonaFilterTableModel(Object[][] rows, String[] colNames) {
            super(rows, colNames);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }

    private void handleSelectionChange(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
            return;
        }
        handleSelectionChange();
    }

    private void handleSelectionChange() {
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow != -1) {
            setPersona(resultsTable.getSelectedRow());
            detailsPanel.setMode(this, PersonaDetailsMode.VIEW, selectedPersona);
        } else {
            detailsPanel.clear();
        }
    }

    private void updateResultsTable(Collection<Persona> results) {
        Object[][] rows = new Object[results.size()][2];
        int i = 0;
        for (Persona result : results) {
            rows[i] = new Object[]{result.getId(), result.getName()};
            i++;
        }
        PersonaFilterTableModel updatedTableModel = new PersonaFilterTableModel(
                rows,
                new String[]{"ID", "Name"}
        );

        resultsTable.setModel(updatedTableModel);
        currentResults = new ArrayList<>(results);

        // Formatting
        resultsTable.getColumnModel().getColumn(0).setMaxWidth(100);
        resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    }

    @Messages({
        "PersonasTopComponent_search_exception_Title=There was a failure during the search.  Try opening a case to fully initialize the central repository database.",
        "PersonasTopComponent_search_exception_msg=Failed to search personas.",
        "PersonasTopComponent_noCR_msg=Central Repository is not enabled.",})
    private void executeSearch() {
        // To prevent downstream failures, only execute search if central repository is enabled
        if (!CentralRepository.isEnabled()) {
            logger.log(Level.SEVERE, "Central Repository is not enabled, but execute search was called.");
            JOptionPane.showMessageDialog(this,
                    Bundle.PersonasTopComponent_search_exception_Title(),
                    Bundle.PersonasTopComponent_noCR_msg(),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        Collection<Persona> results;
        try {
            if (cbFilterByKeyword.isSelected()) {
                if (searchNameRadio.isSelected()) {
                    results = Persona.getPersonaByName(searchField.getText());
                } else {
                    results = Persona.getPersonaByAccountIdentifierLike(searchField.getText());
                }
            } else {
                results = Persona.getPersonaByName("");
            }
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Failed to search personas", ex);
            JOptionPane.showMessageDialog(this,
                    Bundle.PersonasTopComponent_search_exception_Title(),
                    Bundle.PersonasTopComponent_search_exception_msg(),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        resultsTable.clearSelection();
        updateResultsTable(results);
        editBtn.setEnabled(false);
        deleteBtn.setEnabled(false);
    }

    @Override
    public void componentOpened() {
        super.componentOpened();
        WindowManager.getDefault().setTopComponentFloating(this, true);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        searchButtonGroup = new javax.swing.ButtonGroup();
        introTextScrollPane = new javax.swing.JScrollPane();
        introText = new javax.swing.JTextArea();
        mainSplitPane = new javax.swing.JSplitPane();
        searchPanel = new javax.swing.JPanel();
        searchField = new javax.swing.JTextField();
        searchNameRadio = new javax.swing.JRadioButton();
        searchAccountRadio = new javax.swing.JRadioButton();
        searchBtn = new javax.swing.JButton();
        resultsPane = new javax.swing.JScrollPane();
        resultsTable = new javax.swing.JTable();
        createAccountBtn = new javax.swing.JButton();
        editBtn = new javax.swing.JButton();
        deleteBtn = new javax.swing.JButton();
        createButtonSeparator = new javax.swing.JSeparator();
        createBtn = new javax.swing.JButton();
        cbFilterByKeyword = new javax.swing.JCheckBox();
        detailsScrollPane = new javax.swing.JScrollPane();
        detailsPanel = new org.sleuthkit.autopsy.centralrepository.persona.PersonaDetailsPanel();

        setName(""); // NOI18N

        introTextScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);

        introText.setBackground(getBackground());
        introText.setColumns(20);
        introText.setLineWrap(true);
        introText.setRows(5);
        introText.setText(org.openide.util.NbBundle.getMessage(PersonasTopComponent.class, "PersonasTopComponent.introText.text")); // NOI18N
        introText.setWrapStyleWord(true);
        introText.setFocusable(false);
        introTextScrollPane.setViewportView(introText);

        mainSplitPane.setDividerLocation(400);

        searchField.setText(org.openide.util.NbBundle.getMessage(PersonasTopComponent.class, "PersonasTopComponent.searchField.text")); // NOI18N

        searchButtonGroup.add(searchNameRadio);
        searchNameRadio.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(searchNameRadio, org.openide.util.NbBundle.getMessage(PersonasTopComponent.class, "PersonasTopComponent.searchNameRadio.text")); // NOI18N

        searchButtonGroup.add(searchAccountRadio);
        org.openide.awt.Mnemonics.setLocalizedText(searchAccountRadio, org.openide.util.NbBundle.getMessage(PersonasTopComponent.class, "PersonasTopComponent.searchAccountRadio.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(searchBtn, org.openide.util.NbBundle.getMessage(PersonasTopComponent.class, "PersonasTopComponent.searchBtn.text")); // NOI18N

        resultsTable.setToolTipText(org.openide.util.NbBundle.getMessage(PersonasTopComponent.class, "PersonasTopComponent.resultsTable.toolTipText")); // NOI18N
        resultsTable.getTableHeader().setReorderingAllowed(false);
        resultsPane.setViewportView(resultsTable);
        if (resultsTable.getColumnModel().getColumnCount() > 0) {
            resultsTable.getColumnModel().getColumn(0).setMaxWidth(25);
            resultsTable.getColumnModel().getColumn(0).setHeaderValue(org.openide.util.NbBundle.getMessage(PersonasTopComponent.class, "PersonasTopComponent.resultsTable.columnModel.title0")); // NOI18N
            resultsTable.getColumnModel().getColumn(1).setHeaderValue(org.openide.util.NbBundle.getMessage(PersonasTopComponent.class, "PersonasTopComponent.resultsTable.columnModel.title1")); // NOI18N
        }

        org.openide.awt.Mnemonics.setLocalizedText(createAccountBtn, org.openide.util.NbBundle.getMessage(PersonasTopComponent.class, "PersonasTopComponent.createAccountBtn.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(editBtn, org.openide.util.NbBundle.getMessage(PersonasTopComponent.class, "PersonasTopComponent.editBtn.text")); // NOI18N
        editBtn.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(deleteBtn, org.openide.util.NbBundle.getMessage(PersonasTopComponent.class, "PersonasTopComponent.deleteBtn.text")); // NOI18N
        deleteBtn.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(createBtn, org.openide.util.NbBundle.getMessage(PersonasTopComponent.class, "PersonasTopComponent.createBtn.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(cbFilterByKeyword, org.openide.util.NbBundle.getMessage(PersonasTopComponent.class, "PersonasTopComponent.cbFilterByKeyword.text")); // NOI18N
        cbFilterByKeyword.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cbFilterByKeywordActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout searchPanelLayout = new javax.swing.GroupLayout(searchPanel);
        searchPanel.setLayout(searchPanelLayout);
        searchPanelLayout.setHorizontalGroup(
            searchPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(searchPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(searchPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(createButtonSeparator)
                    .addComponent(resultsPane, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(searchField)
                    .addGroup(searchPanelLayout.createSequentialGroup()
                        .addComponent(searchNameRadio)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(searchAccountRadio)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(searchBtn))
                    .addGroup(searchPanelLayout.createSequentialGroup()
                        .addGroup(searchPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(createAccountBtn)
                            .addGroup(searchPanelLayout.createSequentialGroup()
                                .addComponent(createBtn)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(editBtn)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(deleteBtn))
                            .addComponent(cbFilterByKeyword))
                        .addGap(0, 50, Short.MAX_VALUE)))
                .addContainerGap())
        );
        searchPanelLayout.setVerticalGroup(
            searchPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(searchPanelLayout.createSequentialGroup()
                .addComponent(cbFilterByKeyword)
                .addGap(1, 1, 1)
                .addComponent(searchField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(searchPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(searchNameRadio)
                    .addComponent(searchAccountRadio)
                    .addComponent(searchBtn))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(resultsPane, javax.swing.GroupLayout.PREFERRED_SIZE, 302, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(searchPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(editBtn)
                    .addComponent(createBtn)
                    .addComponent(deleteBtn))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(createButtonSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, 4, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(createAccountBtn, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        mainSplitPane.setLeftComponent(searchPanel);

        detailsScrollPane.setViewportView(detailsPanel);

        mainSplitPane.setRightComponent(detailsScrollPane);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(introTextScrollPane)
            .addComponent(mainSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 724, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(introTextScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(mainSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 489, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void cbFilterByKeywordActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cbFilterByKeywordActionPerformed
        setKeywordSearchEnabled(cbFilterByKeyword.isSelected(), false);
    }//GEN-LAST:event_cbFilterByKeywordActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox cbFilterByKeyword;
    private javax.swing.JButton createAccountBtn;
    private javax.swing.JButton createBtn;
    private javax.swing.JSeparator createButtonSeparator;
    private javax.swing.JButton deleteBtn;
    private org.sleuthkit.autopsy.centralrepository.persona.PersonaDetailsPanel detailsPanel;
    private javax.swing.JScrollPane detailsScrollPane;
    private javax.swing.JButton editBtn;
    private javax.swing.JTextArea introText;
    private javax.swing.JScrollPane introTextScrollPane;
    private javax.swing.JSplitPane mainSplitPane;
    private javax.swing.JScrollPane resultsPane;
    private javax.swing.JTable resultsTable;
    private javax.swing.JRadioButton searchAccountRadio;
    private javax.swing.JButton searchBtn;
    private javax.swing.ButtonGroup searchButtonGroup;
    private javax.swing.JTextField searchField;
    private javax.swing.JRadioButton searchNameRadio;
    private javax.swing.JPanel searchPanel;
    // End of variables declaration//GEN-END:variables

}
