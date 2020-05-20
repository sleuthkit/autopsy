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
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.RetainLocation;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.Persona;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Top component for the Personas tool
 *
 */
@TopComponent.Description(preferredID = "PersonaManagerTopComponent", persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "personamanager", openAtStartup = false)
@RetainLocation("personamanager")
@SuppressWarnings("PMD.SingularField")
public final class PersonaManagerTopComponent extends TopComponent {

    private static final Logger logger = Logger.getLogger(PersonaManagerTopComponent.class.getName());

    private ArrayList<Persona> currentResults = null;
    private Persona selectedPersona = null;

    @Messages({
        "PMTopComponent_Name=Persona Manager"
    })
    public PersonaManagerTopComponent() {
        initComponents();
        setName(Bundle.PMTopComponent_Name());
        executeSearch();

        searchBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                executeSearch();
            }
        });

        editBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                detailsPanel.setMode(PersonaManagerTopComponent.this, PersonaDetailsMode.EDIT, selectedPersona);
            }
        });

        createBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                detailsPanel.setMode(PersonaManagerTopComponent.this, PersonaDetailsMode.CREATE, null);
                resultsTable.clearSelection();
            }
        });

        // Results table
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                handleSelectionChange(e);
            }
        });
    }

    void setPersona(int index) {
        Persona persona = currentResults.get(index);
        selectedPersona = persona;
        editBtn.setEnabled(true);
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
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow != -1) {
            setPersona(resultsTable.getSelectedRow());
            detailsPanel.setMode(this, PersonaDetailsMode.VIEW, selectedPersona);
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
        "PMTopComponent_search_exception_Title=Search failure",
        "PMTopComponent_search_exception_msg=Failed to search personas",})
    private void executeSearch() {
        Collection<Persona> results;
        try {
            results = Persona.getPersonaByName(searchField.getText());
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Failed to search personas", ex);
            JOptionPane.showMessageDialog(this,
                    Bundle.PMTopComponent_search_exception_Title(),
                    Bundle.PMTopComponent_search_exception_msg(),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        resultsTable.clearSelection();
        updateResultsTable(results);
        editBtn.setEnabled(false);
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
        jSplitPane1 = new javax.swing.JSplitPane();
        searchPanel = new javax.swing.JPanel();
        searchField = new javax.swing.JTextField();
        searchNameRadio = new javax.swing.JRadioButton();
        searchAccountRadio = new javax.swing.JRadioButton();
        resultsPane = new javax.swing.JScrollPane();
        resultsTable = new javax.swing.JTable();
        searchBtn = new javax.swing.JButton();
        editBtn = new javax.swing.JButton();
        createBtn = new javax.swing.JButton();
        detailsPanel = new org.sleuthkit.autopsy.centralrepository.persona.PersonaDetailsPanel();

        setMinimumSize(new java.awt.Dimension(400, 400));

        searchField.setText(org.openide.util.NbBundle.getMessage(PersonaManagerTopComponent.class, "PersonaManagerTopComponent.searchField.text")); // NOI18N

        searchButtonGroup.add(searchNameRadio);
        searchNameRadio.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(searchNameRadio, org.openide.util.NbBundle.getMessage(PersonaManagerTopComponent.class, "PersonaManagerTopComponent.searchNameRadio.text")); // NOI18N
        searchNameRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchNameRadioActionPerformed(evt);
            }
        });

        searchButtonGroup.add(searchAccountRadio);
        org.openide.awt.Mnemonics.setLocalizedText(searchAccountRadio, org.openide.util.NbBundle.getMessage(PersonaManagerTopComponent.class, "PersonaManagerTopComponent.searchAccountRadio.text")); // NOI18N
        searchAccountRadio.setEnabled(false);

        resultsTable.setToolTipText(org.openide.util.NbBundle.getMessage(PersonaManagerTopComponent.class, "PersonaManagerTopComponent.resultsTable.toolTipText")); // NOI18N
        resultsTable.getTableHeader().setReorderingAllowed(false);
        resultsPane.setViewportView(resultsTable);
        if (resultsTable.getColumnModel().getColumnCount() > 0) {
            resultsTable.getColumnModel().getColumn(0).setMaxWidth(25);
            resultsTable.getColumnModel().getColumn(0).setHeaderValue(org.openide.util.NbBundle.getMessage(PersonaManagerTopComponent.class, "PersonaManagerTopComponent.resultsTable.columnModel.title0")); // NOI18N
            resultsTable.getColumnModel().getColumn(1).setHeaderValue(org.openide.util.NbBundle.getMessage(PersonaManagerTopComponent.class, "PersonaManagerTopComponent.resultsTable.columnModel.title1")); // NOI18N
        }

        org.openide.awt.Mnemonics.setLocalizedText(searchBtn, org.openide.util.NbBundle.getMessage(PersonaManagerTopComponent.class, "PersonaManagerTopComponent.searchBtn.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(editBtn, org.openide.util.NbBundle.getMessage(PersonaManagerTopComponent.class, "PersonaManagerTopComponent.editBtn.text")); // NOI18N
        editBtn.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(createBtn, org.openide.util.NbBundle.getMessage(PersonaManagerTopComponent.class, "PersonaManagerTopComponent.createBtn.text")); // NOI18N
        createBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                createBtnActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout searchPanelLayout = new javax.swing.GroupLayout(searchPanel);
        searchPanel.setLayout(searchPanelLayout);
        searchPanelLayout.setHorizontalGroup(
            searchPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(searchPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(searchPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(resultsPane, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(searchField)
                    .addGroup(searchPanelLayout.createSequentialGroup()
                        .addComponent(searchNameRadio)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(searchAccountRadio)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(searchBtn))
                    .addGroup(searchPanelLayout.createSequentialGroup()
                        .addComponent(editBtn)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(createBtn)))
                .addContainerGap())
        );
        searchPanelLayout.setVerticalGroup(
            searchPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(searchPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(searchField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(searchPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(searchNameRadio)
                    .addComponent(searchAccountRadio)
                    .addComponent(searchBtn))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(resultsPane, javax.swing.GroupLayout.DEFAULT_SIZE, 619, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(searchPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(editBtn)
                    .addComponent(createBtn))
                .addContainerGap())
        );

        jSplitPane1.setLeftComponent(searchPanel);
        jSplitPane1.setRightComponent(detailsPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 675, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void searchNameRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchNameRadioActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_searchNameRadioActionPerformed

    private void createBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createBtnActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_createBtnActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton createBtn;
    private org.sleuthkit.autopsy.centralrepository.persona.PersonaDetailsPanel detailsPanel;
    private javax.swing.JButton editBtn;
    private javax.swing.JSplitPane jSplitPane1;
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
