/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import org.apache.commons.lang.StringUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettingsPanel;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDb;

/**
 * Ingest job settings panel for hash lookup file ingest modules.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public final class HashLookupModuleSettingsPanel extends IngestModuleIngestJobSettingsPanel implements PropertyChangeListener {

    private static final long serialVersionUID = 1L;
    private final HashDbManager hashDbManager = HashDbManager.getInstance();
    private final List<HashSetModel> hashSetModels = new ArrayList<>();
    private final HashSetsTableModel hashSetsTableModel = new HashSetsTableModel(hashSetModels);

    HashLookupModuleSettingsPanel(HashLookupModuleSettings settings) {
        initializeHashSetModels(settings);
        initComponents();
        customizeComponents(settings);
    }

    private void initializeHashSetModels(HashLookupModuleSettings settings) {
        List<HashDb> hashDbs = validSetsOnly(hashDbManager.getAllHashSets());
        hashSetModels.clear();
        for (HashDb db : hashDbs) {
            hashSetModels.add(new HashSetModel(db, settings.isHashSetEnabled(db), isHashDbValid(db)));
        }
    }

    private void customizeComponents(HashLookupModuleSettings settings) {
        customizeHashSetsTable(hashDbsScrollPane, hashTable, hashSetsTableModel);
        alwaysCalcHashesCheckbox.setSelected(settings.shouldCalculateHashes());
        hashDbManager.addPropertyChangeListener(this);
        alwaysCalcHashesCheckbox.setText("<html>" + org.openide.util.NbBundle.getMessage(HashLookupModuleSettingsPanel.class, "HashLookupModuleSettingsPanel.alwaysCalcHashesCheckbox.text") + "</html>"); // NOI18N NON-NLS
    }

    private void customizeHashSetsTable(JScrollPane scrollPane, JTable table, HashSetsTableModel tableModel) {
        table.setModel(tableModel);
        table.setTableHeader(null);
        table.setRowSelectionAllowed(false);
        final int width1 = scrollPane.getPreferredSize().width;
        hashTable.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        TableColumn column;
        for (int i = 0; i < table.getColumnCount(); i++) {
            column = table.getColumnModel().getColumn(i);
            if (i == 0) {
                column.setPreferredWidth(((int) (width1 * 0.07)));
            } else {
                column.setCellRenderer(new HashSetTableCellRenderer());
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
        List<HashDb> enabledHashSets = new ArrayList<>();
        List<HashDb> disabledHashSets = new ArrayList<>();
        addHashSets(hashSetModels, enabledHashSets, disabledHashSets);
        return new HashLookupModuleSettings(alwaysCalcHashesCheckbox.isSelected(),
                enabledHashSets, disabledHashSets);
    }

    private void addHashSets(List<HashSetModel> hashSetModels, List<HashDb> enabledHashSets, List<HashDb> disabledHashSets) {
        for (HashSetModel model : hashSetModels) {
            if (model.isEnabled() && model.isValid()) {
                enabledHashSets.add(model.getDatabase());
            } else {
                disabledHashSets.add(model.getDatabase());
            }
        }
    }

    void update() {
        updateHashSetModels();
        hashSetsTableModel.fireTableDataChanged();
    }

    private List<HashDb> validSetsOnly(List<HashDb> hashDbs) {
        List<HashDb> validDbs = new ArrayList<>();
        for (HashDb db : hashDbs) {
            try {
                if (db.isValid()) {
                    validDbs.add(db);
                }
            } catch (TskCoreException ex) {
                Logger.getLogger(HashLookupModuleSettingsPanel.class.getName()).log(Level.SEVERE, "Error checking validity for hash set (name = " + db.getHashSetName() + ")", ex); //NON-NLS
            }
        }
        return validDbs;
    }

    void updateHashSetModels() {
        List<HashDb> hashDbs = validSetsOnly(hashDbManager.getAllHashSets());

        List<HashDb> hashDatabases = new ArrayList<>(hashDbs);

        // Update the hash sets and detect deletions.
        List<HashSetModel> deletedHashSetModels = new ArrayList<>();
        for (HashSetModel model : hashSetModels) {
            boolean foundDatabase = false;
            for (HashDb db : hashDatabases) {
                if (model.getDatabase().equals(db)) {
                    model.setValid(isHashDbValid(db));
                    hashDatabases.remove(db);
                    foundDatabase = true;
                    break;
                }
            }
            if (!foundDatabase) {
                deletedHashSetModels.add(model);
            }
        }

        // Remove the deleted hash sets.
        for (HashSetModel model : deletedHashSetModels) {
            hashSetModels.remove(model);
        }

        // Add any new hash sets. All new sets are enabled by default.
        for (HashDb db : hashDatabases) {
            hashSetModels.add(new HashSetModel(db, true, isHashDbValid(db)));
        }
    }

    void reset(HashLookupModuleSettings newSettings) {
        initializeHashSetModels(newSettings);
        alwaysCalcHashesCheckbox.setSelected(newSettings.shouldCalculateHashes());
        hashSetsTableModel.fireTableDataChanged();
    }

    private boolean isHashDbValid(HashDb hashDb) {
        boolean isValid = false;
        try {
            isValid = hashDb.isValid();
        } catch (TskCoreException ex) {
            Logger.getLogger(HashLookupModuleSettingsPanel.class.getName()).log(Level.SEVERE, "Error checking validity for hash set (name = " + hashDb.getHashSetName() + ")", ex); //NON-NLS
        }
        return isValid;
    }

    private static final class HashSetModel {

        private final HashDb db;
        private boolean valid;
        private boolean enabled;

        HashSetModel(HashDb db, boolean enabled, boolean valid) {
            this.db = db;
            this.enabled = enabled;
            this.valid = valid;
        }

        HashDb getDatabase() {
            return db;
        }

        String getName() {
            return db.getDisplayName();
        }

        String getFormattedName() {
            String knownTypeName = (db != null && db.getKnownFilesType() != null) ? db.getKnownFilesType().getDisplayName() : "";
            if (!StringUtils.isBlank(knownTypeName)) {
                knownTypeName = String.format(" (%s)", knownTypeName);
            }

            String displayName = db != null ? db.getDisplayName() : "";
            return displayName + knownTypeName;
        }

        void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        boolean isEnabled() {
            return enabled;
        }

        void setValid(boolean valid) {
            this.valid = valid;
        }

        boolean isValid() {
            return valid;
        }
    }
    
    private static final class HashSetTableCellRenderer extends DefaultTableCellRenderer{
        
        private static final long serialVersionUID = 1L;
        @Override
        public Component getTableCellRendererComponent(
                        JTable table, Object value,
                        boolean isSelected, boolean hasFocus,
                        int row, int column) {
          JLabel label = (JLabel)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          label.setToolTipText(label.getText());
          return label;
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
                return hashSets.get(rowIndex).getFormattedName();
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return (columnIndex == 0 && hashSets.get(rowIndex).isValid());
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                hashSets.get(rowIndex).setEnabled((Boolean) aValue);
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

        hashDbsLabel = new javax.swing.JLabel();
        hashDbsScrollPane = new javax.swing.JScrollPane();
        hashTable = new javax.swing.JTable();
        alwaysCalcHashesCheckbox = new javax.swing.JCheckBox();

        setPreferredSize(new java.awt.Dimension(292, 150));

        hashDbsLabel.setText(org.openide.util.NbBundle.getMessage(HashLookupModuleSettingsPanel.class, "HashLookupModuleSettingsPanel.hashDbsLabel.text")); // NOI18N

        hashDbsScrollPane.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        hashTable.setBackground(new java.awt.Color(240, 240, 240));
        hashTable.setShowHorizontalLines(false);
        hashTable.setShowVerticalLines(false);
        hashDbsScrollPane.setViewportView(hashTable);

        alwaysCalcHashesCheckbox.setText(org.openide.util.NbBundle.getMessage(HashLookupModuleSettingsPanel.class, "HashLookupModuleSettingsPanel.alwaysCalcHashesCheckbox.text")); // NOI18N
        alwaysCalcHashesCheckbox.setToolTipText(org.openide.util.NbBundle.getMessage(HashLookupModuleSettingsPanel.class, "HashLookupModuleSettingsPanel.alwaysCalcHashesCheckbox.toolTipText")); // NOI18N
        alwaysCalcHashesCheckbox.setMaximumSize(new java.awt.Dimension(290, 35));
        alwaysCalcHashesCheckbox.setMinimumSize(new java.awt.Dimension(290, 35));
        alwaysCalcHashesCheckbox.setPreferredSize(new java.awt.Dimension(271, 35));
        alwaysCalcHashesCheckbox.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        alwaysCalcHashesCheckbox.setVerticalTextPosition(javax.swing.SwingConstants.TOP);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(hashDbsLabel)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addComponent(hashDbsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 494, Short.MAX_VALUE))
                    .addComponent(alwaysCalcHashesCheckbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(2, 2, 2)
                .addComponent(hashDbsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(hashDbsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 207, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(alwaysCalcHashesCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox alwaysCalcHashesCheckbox;
    private javax.swing.JLabel hashDbsLabel;
    private javax.swing.JScrollPane hashDbsScrollPane;
    private javax.swing.JTable hashTable;
    // End of variables declaration//GEN-END:variables
}
