/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettingsPanel;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDatabase;

/**
 * Ingest job settings panel for hash lookup file ingest modules.
 */
public final class HashLookupModuleSettingsPanel extends IngestModuleIngestJobSettingsPanel implements PropertyChangeListener {

    private static final long serialVersionUID = 1L;
    private final HashDbManager hashDbManager = HashDbManager.getInstance();
    private final List<HashSetModel> knownHashSetModels = new ArrayList<>();
    private final HashSetsTableModel knownHashSetsTableModel = new HashSetsTableModel(knownHashSetModels);
    private final List<HashSetModel> knownBadHashSetModels = new ArrayList<>();
    private final HashSetsTableModel knownBadHashSetsTableModel = new HashSetsTableModel(knownBadHashSetModels);

    HashLookupModuleSettingsPanel(HashLookupModuleSettings settings) {
        initializeHashSetModels(settings);
        initComponents();
        customizeComponents(settings);
    }

    private void initializeHashSetModels(HashLookupModuleSettings settings) {
        initializeHashSetModels(settings, hashDbManager.getKnownFileHashSetsNew(), knownHashSetModels);
        initializeHashSetModels(settings, hashDbManager.getKnownBadFileHashSetsNew(), knownBadHashSetModels);
    }

    private void initializeHashSetModels(HashLookupModuleSettings settings, List<HashDatabase> hashDbs, List<HashSetModel> hashSetModels) {
        hashSetModels.clear();
        for (HashDatabase db : hashDbs) {
            String name = db.getHashSetName();
            hashSetModels.add(new HashSetModel(db));
        }
    }

    private void customizeComponents(HashLookupModuleSettings settings) {
        customizeHashSetsTable(jScrollPane1, knownHashTable, knownHashSetsTableModel);
        customizeHashSetsTable(jScrollPane2, knownBadHashTable, knownBadHashSetsTableModel);
        alwaysCalcHashesCheckbox.setSelected(settings.shouldCalculateHashes());
        hashDbManager.addPropertyChangeListener(this);
        alwaysCalcHashesCheckbox.setText("<html>" + org.openide.util.NbBundle.getMessage(HashLookupModuleSettingsPanel.class, "HashLookupModuleSettingsPanel.alwaysCalcHashesCheckbox.text") + "</html>"); // NOI18N NON-NLS
    }

    private void customizeHashSetsTable(JScrollPane scrollPane, JTable table, HashSetsTableModel tableModel) {
        table.setModel(tableModel);
        table.setTableHeader(null);
        table.setRowSelectionAllowed(false);
        final int width1 = scrollPane.getPreferredSize().width;
        knownHashTable.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        TableColumn column;
        for (int i = 0; i < table.getColumnCount(); i++) {
            column = table.getColumnModel().getColumn(i);
            if (i == 0) {
                column.setPreferredWidth(((int) (width1 * 0.07)));
            } else {
                column.setPreferredWidth(((int) (width1 * 0.92)));
            }
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if (event.getPropertyName().equals(HashDbManager.SetEvt.DB_ADDED.name())
                || event.getPropertyName().equals(HashDbManager.SetEvt.DB_DELETED.name())
                || event.getPropertyName().equals(HashDbManager.SetEvt.DB_INDEXED.name())) {
            update();
        }
    }

    @Override
    public IngestModuleIngestJobSettings getSettings() {
        return new HashLookupModuleSettings(alwaysCalcHashesCheckbox.isSelected());
    }

    private void getHashSetNames(List<HashSetModel> hashSetModels, List<String> enabledHashSetNames, List<String> disabledHashSetNames) {
        for (HashSetModel model : hashSetModels) {
            if (model.isEnabled() && model.isValid()) {
                enabledHashSetNames.add(model.getName());
            } else {
                disabledHashSetNames.add(model.getName());
            }
        }
    }

    void update() {
        updateHashSetModels();
        knownHashSetsTableModel.fireTableDataChanged();
        knownBadHashSetsTableModel.fireTableDataChanged();
    }

    private void updateHashSetModels() {
        updateHashSetModels(hashDbManager.getKnownFileHashSetsNew(), knownHashSetModels);
        updateHashSetModels(hashDbManager.getKnownBadFileHashSetsNew(), knownBadHashSetModels);
    }

    void updateHashSetModels(List<HashDatabase> hashDbs, List<HashSetModel> hashSetModels) {
        Map<String, HashDatabase> hashSetDbs = new HashMap<>();
        for (HashDatabase db : hashDbs) {
            hashSetDbs.put(db.getHashSetName(), db);
        }

        // Update the hash sets and detect deletions.
        List<HashSetModel> deletedHashSetModels = new ArrayList<>();
        for (HashSetModel model : hashSetModels) {
            String hashSetName = model.getName();
            if (hashSetDbs.containsKey(hashSetName)) {
                HashDatabase db = hashSetDbs.get(hashSetName);
                model.setValid(isHashDbValid(db));
                hashSetDbs.remove(hashSetName);
            } else {
                deletedHashSetModels.add(model);
            }
        }

        // Remove the deleted hash sets.
        for (HashSetModel model : deletedHashSetModels) {
            hashSetModels.remove(model);
        }

        // Add any new hash sets. All new sets are enabled by default.
        for (HashDatabase db : hashSetDbs.values()) {
            String name = db.getHashSetName();
            hashSetModels.add(new HashSetModel(db));
        }
    }

    void reset(HashLookupModuleSettings newSettings) {
        initializeHashSetModels(newSettings);
        alwaysCalcHashesCheckbox.setSelected(newSettings.shouldCalculateHashes());
        knownHashSetsTableModel.fireTableDataChanged();
        knownBadHashSetsTableModel.fireTableDataChanged();
    }

    private boolean isHashDbValid(HashDatabase hashDb) {
        boolean isValid = false;
        try {
            isValid = hashDb.isValid();
        } catch (TskCoreException ex) {
            Logger.getLogger(HashLookupModuleSettingsPanel.class.getName()).log(Level.SEVERE, "Error getting valid status info for hash set (name = " + hashDb.getHashSetName() + ")", ex); //NON-NLS
        }
        return isValid;
    }

    private static final class HashSetModel {

        private HashDatabase db;

        HashSetModel(HashDatabase db) {
            this.db = db;
        }

        String getName() {
            return db.getHashSetName();
        }

        void setEnabled(boolean enabled) {
            db.setSearchDuringIngest(enabled);
        }

        boolean isEnabled() {
            return db.getSearchDuringIngest();
        }

        void setValid(boolean valid) {
            // I don't think I need this
            //this.valid = valid;
        }

        boolean isValid() {
            try{
                return db.isValid();
            } catch (TskCoreException ex){
                Logger.getLogger(HashLookupModuleSettingsPanel.class.getName()).log(Level.SEVERE, "Error getting valid status info for hash set (name = " + db.getHashSetName() + ")", ex); //NON-NLS
                return false;
            }
        }
    }

    private static final class HashSetsTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private final List<HashSetModel> hashSets;

        HashSetsTableModel(List<HashSetModel> hashSets) {
            this.hashSets = hashSets;
        }

        @Override
        public int getRowCount() {
            return hashSets.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                return hashSets.get(rowIndex).isEnabled();
            } else {
                return hashSets.get(rowIndex).getName();
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return (columnIndex == 0 && hashSets.get(rowIndex).isValid());
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            
            if (columnIndex == 0) {
                System.out.println("Setting value of row " + rowIndex + " to " + ((Boolean) aValue));
                hashSets.get(rowIndex).setEnabled((Boolean) aValue);
                try{
                    HashDbManager.getInstance().save();
                } catch (HashDbManager.HashDbManagerException ex){
                    Logger.getLogger(HashLookupModuleSettingsPanel.class.getName()).log(Level.SEVERE, "Error saving hash database settings", ex); //NON-NLS
                }
            }
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        knownHashTable = new javax.swing.JTable();
        knownBadHashDbsLabel = new javax.swing.JLabel();
        knownHashDbsLabel = new javax.swing.JLabel();
        alwaysCalcHashesCheckbox = new javax.swing.JCheckBox();
        jScrollPane2 = new javax.swing.JScrollPane();
        knownBadHashTable = new javax.swing.JTable();

        setPreferredSize(new java.awt.Dimension(292, 150));

        jScrollPane1.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        knownHashTable.setBackground(new java.awt.Color(240, 240, 240));
        knownHashTable.setShowHorizontalLines(false);
        knownHashTable.setShowVerticalLines(false);
        jScrollPane1.setViewportView(knownHashTable);

        knownBadHashDbsLabel.setText(org.openide.util.NbBundle.getMessage(HashLookupModuleSettingsPanel.class, "HashLookupModuleSettingsPanel.knownBadHashDbsLabel.text")); // NOI18N

        knownHashDbsLabel.setText(org.openide.util.NbBundle.getMessage(HashLookupModuleSettingsPanel.class, "HashLookupModuleSettingsPanel.knownHashDbsLabel.text")); // NOI18N

        alwaysCalcHashesCheckbox.setText(org.openide.util.NbBundle.getMessage(HashLookupModuleSettingsPanel.class, "HashLookupModuleSettingsPanel.alwaysCalcHashesCheckbox.text")); // NOI18N
        alwaysCalcHashesCheckbox.setToolTipText(org.openide.util.NbBundle.getMessage(HashLookupModuleSettingsPanel.class, "HashLookupModuleSettingsPanel.alwaysCalcHashesCheckbox.toolTipText")); // NOI18N
        alwaysCalcHashesCheckbox.setMaximumSize(new java.awt.Dimension(290, 35));
        alwaysCalcHashesCheckbox.setMinimumSize(new java.awt.Dimension(290, 35));
        alwaysCalcHashesCheckbox.setPreferredSize(new java.awt.Dimension(271, 35));
        alwaysCalcHashesCheckbox.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        alwaysCalcHashesCheckbox.setVerticalTextPosition(javax.swing.SwingConstants.TOP);

        jScrollPane2.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        knownBadHashTable.setBackground(new java.awt.Color(240, 240, 240));
        knownBadHashTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        knownBadHashTable.setShowHorizontalLines(false);
        knownBadHashTable.setShowVerticalLines(false);
        jScrollPane2.setViewportView(knownBadHashTable);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(knownHashDbsLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 272, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 18, Short.MAX_VALUE))
                    .addComponent(knownBadHashDbsLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)))
                    .addComponent(alwaysCalcHashesCheckbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(2, 2, 2)
                .addComponent(knownHashDbsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 54, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(knownBadHashDbsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 53, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(alwaysCalcHashesCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0))
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox alwaysCalcHashesCheckbox;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JLabel knownBadHashDbsLabel;
    private javax.swing.JTable knownBadHashTable;
    private javax.swing.JLabel knownHashDbsLabel;
    private javax.swing.JTable knownHashTable;
    // End of variables declaration//GEN-END:variables
}
