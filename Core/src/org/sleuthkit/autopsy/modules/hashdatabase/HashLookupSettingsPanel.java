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

import java.awt.*;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.SleuthkitHashSet;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.CentralRepoHashSet;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDb.KnownFilesType;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDb;

/**
 * Instances of this class provide a comprehensive UI for managing the hash sets
 * configuration.
 */
public final class HashLookupSettingsPanel extends IngestModuleGlobalSettingsPanel implements OptionsPanel {

    private static final String NO_SELECTION_TEXT = NbBundle
            .getMessage(HashLookupSettingsPanel.class, "HashDbConfigPanel.noSelectionText");
    private static final String ERROR_GETTING_PATH_TEXT = NbBundle
            .getMessage(HashLookupSettingsPanel.class, "HashDbConfigPanel.errorGettingPathText");
    private static final String ERROR_GETTING_INDEX_STATUS_TEXT = NbBundle
            .getMessage(HashLookupSettingsPanel.class, "HashDbConfigPanel.errorGettingIndexStatusText");
    private final HashDbManager hashSetManager = HashDbManager.getInstance();
    private final HashSetTableModel hashSetTableModel = new HashSetTableModel();
    private final List<Integer> newReferenceSetIDs = new ArrayList<>();

    public HashLookupSettingsPanel() {
        initComponents();
        customizeComponents();
        updateComponentsForNoSelection();

        // Listen to the ingest modules to refresh the enabled/disabled state of 
        // the components in sync with file ingest.
        IngestManager.getInstance().addIngestJobEventListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (isLocalIngestJobEvent(evt)) {
                    EventQueue.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            updateComponents();
                        }
                    });
                }
            }
        });
    }

    @NbBundle.Messages({"HashLookupSettingsPanel.Title=Global Hash Lookup Settings"})
    private void customizeComponents() {
        setName(Bundle.HashLookupSettingsPanel_Title());
        this.ingestWarningLabel.setVisible(false);
        this.hashSetTable.setModel(hashSetTableModel);
        this.hashSetTable.setTableHeader(null);
        hashSetTable.getParent().setBackground(hashSetTable.getBackground());
        hashSetTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hashSetTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    updateComponents();
                }
            }
        });
    }

    private void updateComponents() {
        HashDb db = ((HashSetTable) hashSetTable).getSelection();
        if (db != null) {
            updateComponentsForSelection(db);
        } else {
            updateComponentsForNoSelection();
        }
    }

    private void updateComponentsForNoSelection() {
        boolean ingestIsRunning = IngestManager.getInstance().isIngestRunning();

        // Update descriptive labels.
        hashDbNameLabel.setText(NO_SELECTION_TEXT);
        hashDbTypeLabel.setText(NO_SELECTION_TEXT);
        hashDbLocationLabel.setText(NO_SELECTION_TEXT);
        hashDbVersionLabel.setText(NO_SELECTION_TEXT);
        hashDbOrgLabel.setText(NO_SELECTION_TEXT);
        hashDbReadOnlyLabel.setText(NO_SELECTION_TEXT);
        indexPathLabel.setText(NO_SELECTION_TEXT);

        // Update indexing components.
        hashDbIndexStatusLabel.setText(NO_SELECTION_TEXT);
        hashDbIndexStatusLabel.setForeground(Color.black);
        indexButton.setText(NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexButtonText.index"));
        indexButton.setEnabled(false);
        addHashesToDatabaseButton.setEnabled(false);

        // Update ingest options.
        sendIngestMessagesCheckBox.setSelected(false);
        sendIngestMessagesCheckBox.setEnabled(false);
        optionsLabel.setEnabled(false);
        optionsSeparator.setEnabled(false);

        // Update database action buttons.
        createDatabaseButton.setEnabled(true);
        importDatabaseButton.setEnabled(true);
        deleteDatabaseButton.setEnabled(false);

        // Update ingest in progress warning label.
        ingestWarningLabel.setVisible(ingestIsRunning);
    }

    @NbBundle.Messages({"HashLookupSettingsPanel.readOnly=Read only",
        "HashLookupSettingsPanel.editable=Editable",
        "HashLookupSettingsPanel.updateStatusError=Error reading status",
        "HashLookupSettingsPanel.notApplicable=N/A",
        "HashLookupSettingsPanel.centralRepo=Central Repository"
    })
    private void updateComponentsForSelection(HashDb db) {
        boolean ingestIsRunning = IngestManager.getInstance().isIngestRunning();

        // Update descriptive labels.        
        hashDbNameLabel.setText(db.getHashSetName());
        hashDbTypeLabel.setText(db.getKnownFilesType().getDisplayName());
        try {
            if (db.isUpdateable()) {
                hashDbReadOnlyLabel.setText(Bundle.HashLookupSettingsPanel_editable());
            } else {
                hashDbReadOnlyLabel.setText(Bundle.HashLookupSettingsPanel_readOnly());
            }
        } catch (TskCoreException ex) {
            hashDbReadOnlyLabel.setText(Bundle.HashLookupSettingsPanel_updateStatusError());
        }

        try {
            addHashesToDatabaseButton.setEnabled(!ingestIsRunning && db.isUpdateable());
        } catch (TskCoreException ex) {
            Logger.getLogger(HashLookupSettingsPanel.class.getName()).log(Level.SEVERE, "Error identifying if the hash set is updateable.", ex); //NON-NLS
            addHashesToDatabaseButton.setEnabled(false);
        }

        if (db instanceof SleuthkitHashSet) {
            SleuthkitHashSet hashDb = (SleuthkitHashSet) db;

            // Disable the central repo fields
            hashDbVersionLabel.setText(Bundle.HashLookupSettingsPanel_notApplicable());
            hashDbOrgLabel.setText(Bundle.HashLookupSettingsPanel_notApplicable());

            // Enable the delete button if ingest is not running
            deleteDatabaseButton.setEnabled(!ingestIsRunning);

            try {
                hashDbLocationLabel.setText(shortenPath(db.getDatabasePath()));
            } catch (TskCoreException ex) {
                Logger.getLogger(HashLookupSettingsPanel.class.getName()).log(Level.SEVERE, "Error getting hash set path of " + db.getHashSetName() + " hash set", ex); //NON-NLS
                hashDbLocationLabel.setText(ERROR_GETTING_PATH_TEXT);
            }

            try {
                indexPathLabel.setText(shortenPath(hashDb.getIndexPath()));
            } catch (TskCoreException ex) {
                Logger.getLogger(HashLookupSettingsPanel.class.getName()).log(Level.SEVERE, "Error getting index path of " + db.getHashSetName() + " hash set", ex); //NON-NLS
                indexPathLabel.setText(ERROR_GETTING_PATH_TEXT);
            }

            // Update indexing components.
            try {
                if (hashDb.isIndexing()) {
                    indexButton.setText(
                            NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexButtonText.indexing"));
                    hashDbIndexStatusLabel.setText(
                            NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexStatusText.indexGen"));
                    hashDbIndexStatusLabel.setForeground(Color.black);
                    indexButton.setEnabled(false);
                } else if (hashDb.hasIndex()) {
                    if (hashDb.hasIndexOnly()) {
                        hashDbIndexStatusLabel.setText(
                                NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexStatusText.indexOnly"));
                    } else {
                        hashDbIndexStatusLabel.setText(
                                NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexStatusText.indexed"));
                    }
                    hashDbIndexStatusLabel.setForeground(Color.black);
                    if (hashDb.canBeReIndexed()) {
                        indexButton.setText(
                                NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexButtonText.reIndex"));
                        indexButton.setEnabled(true);
                    } else {
                        indexButton.setText(NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexButtonText.index"));
                        indexButton.setEnabled(false);
                    }
                } else {
                    hashDbIndexStatusLabel.setText(
                            NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexStatusText.noIndex"));
                    hashDbIndexStatusLabel.setForeground(Color.red);
                    indexButton.setText(NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexButtonText.index"));
                    indexButton.setEnabled(true);
                }
            } catch (TskCoreException ex) {
                Logger.getLogger(HashLookupSettingsPanel.class.getName()).log(Level.SEVERE, "Error getting index state of hash set", ex); //NON-NLS
                hashDbIndexStatusLabel.setText(ERROR_GETTING_INDEX_STATUS_TEXT);
                hashDbIndexStatusLabel.setForeground(Color.red);
                indexButton.setText(NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.indexButtonText.index"));
                indexButton.setEnabled(false);
            }
        } else {

            // Disable the file type fields/buttons
            indexPathLabel.setText(Bundle.HashLookupSettingsPanel_notApplicable());
            hashDbIndexStatusLabel.setText(Bundle.HashLookupSettingsPanel_notApplicable());
            hashDbLocationLabel.setText(Bundle.HashLookupSettingsPanel_centralRepo());
            indexButton.setEnabled(false);
            deleteDatabaseButton.setEnabled(false);

            CentralRepoHashSet crDb = (CentralRepoHashSet) db;

            hashDbVersionLabel.setText(crDb.getVersion());
            hashDbOrgLabel.setText(crDb.getOrgName());
        }

        // Disable the indexing button if ingest is in progress.
        if (ingestIsRunning) {
            indexButton.setEnabled(false);
        }

        // Update ingest option components.        
        sendIngestMessagesCheckBox.setSelected(db.getSendIngestMessages());
        sendIngestMessagesCheckBox.setEnabled(!ingestIsRunning && db.getKnownFilesType().equals(KnownFilesType.KNOWN_BAD));
        optionsLabel.setEnabled(!ingestIsRunning);
        optionsSeparator.setEnabled(!ingestIsRunning);

        // Update database action buttons.
        createDatabaseButton.setEnabled(true);
        importDatabaseButton.setEnabled(true);

        // Update ingest in progress warning label.
        ingestWarningLabel.setVisible(ingestIsRunning);
    }

    private static String shortenPath(String path) {
        String shortenedPath = path;
        if (shortenedPath.length() > 50) {
            shortenedPath = shortenedPath.substring(0, 10 + shortenedPath.substring(10).indexOf(File.separator) + 1) + "..." + shortenedPath.substring((shortenedPath.length() - 20) + shortenedPath.substring(shortenedPath.length() - 20).indexOf(File.separator));
        }
        return shortenedPath;
    }

    private boolean isLocalIngestJobEvent(PropertyChangeEvent evt) {
        if (evt instanceof AutopsyEvent) {
            AutopsyEvent event = (AutopsyEvent) evt;
            if (event.getSourceType() == AutopsyEvent.SourceType.LOCAL) {
                String eventType = event.getPropertyName();
                return (eventType.equals(IngestManager.IngestJobEvent.STARTED.toString())
                        || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())
                        || eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString()));
            }
        }
        return false;
    }

    @Override
    @Messages({"HashLookupSettingsPanel.saveFail.message=Couldn't save hash set settings.",
        "HashLookupSettingsPanel.saveFail.title=Save Fail"})
    public void saveSettings() {
        // Clear out the list of new central repo hash sets. They don't need to be
        // indexed so will all be saved on both code paths.
        newReferenceSetIDs.clear();

        //Checking for for any unindexed databases
        List<SleuthkitHashSet> unindexed = new ArrayList<>();
        for (HashDb db : hashSetManager.getAllHashSets()) {
            if (db instanceof SleuthkitHashSet) {
                try {
                    SleuthkitHashSet hashDatabase = (SleuthkitHashSet) db;
                    if (!hashDatabase.hasIndex()) {
                        unindexed.add(hashDatabase);
                    }
                } catch (TskCoreException ex) {
                    Logger.getLogger(HashLookupSettingsPanel.class.getName()).log(Level.SEVERE, "Error getting index info for hash set", ex); //NON-NLS
                }
            }
        }

        // If there are unindexed databases, give the user the option to index them now. This
        // needs to be on the EDT, and will save the hash settings after completing
        if (!unindexed.isEmpty()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    //If unindexed ones are found, show a popup box that will either index them, or remove them.
                    if (unindexed.size() == 1) {
                        showInvalidIndex(false, unindexed);
                    } else if (unindexed.size() > 1) {
                        showInvalidIndex(true, unindexed);
                    }
                }
            });
        } else {
            try {
                hashSetManager.save();
            } catch (HashDbManager.HashDbManagerException ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, Bundle.HashLookupSettingsPanel_saveFail_message(), Bundle.HashLookupSettingsPanel_saveFail_title(), JOptionPane.ERROR_MESSAGE);
                });
            }
        }
    }

    @Override
    public void load() {
        hashSetTable.clearSelection();
        hashSetTableModel.refreshModel();
    }

    @Override
    public void store() {
        saveSettings();
    }

    public void cancel() {
        /*
         * Revert back to last settings only if the user could have made
         * changes. Doing this while ingest is running causes hash dbs to be
         * closed while they are still being used.
         */
        if (IngestManager.getInstance().isIngestRunning() == false) {
            // Remove any new central repo hash sets from the database
            for (int refID : newReferenceSetIDs) {
                try {
                    if (EamDb.isEnabled()) {
                        EamDb.getInstance().deleteReferenceSet(refID);
                    } else {
                        // This is the case where the user imported a database, then switched over to the central
                        // repo panel and disabled it before cancelling. We can't delete the database at this point.
                        Logger.getLogger(HashLookupSettingsPanel.class.getName()).log(Level.WARNING, "Error reverting central repository hash sets"); //NON-NLS
                    }
                } catch (EamDbException ex) {
                    Logger.getLogger(HashLookupSettingsPanel.class.getName()).log(Level.SEVERE, "Error reverting central repository hash sets", ex); //NON-NLS
                }
            }
            HashDbManager.getInstance().loadLastSavedConfiguration();
        }
    }

    @Messages({"# {0} - hash lookup name", "HashLookupSettingsPanel.removeDatabaseFailure.message=Failed to remove hash lookup: {0}"})
    void removeThese(List<SleuthkitHashSet> toRemove) {
        for (SleuthkitHashSet hashDb : toRemove) {
            try {
                hashSetManager.removeHashDatabaseNoSave(hashDb);
            } catch (HashDbManager.HashDbManagerException ex) {
                JOptionPane.showMessageDialog(this, Bundle.HashLookupSettingsPanel_removeDatabaseFailure_message(hashDb.getHashSetName()));
            }
        }
        hashSetTableModel.refreshModel();
    }

    /**
     * Displays the popup box that tells user that some of his databases are
     * unindexed, along with solutions. This method is related to
     * ModalNoButtons, to be removed at a later date.
     *
     * @param plural    Whether or not there are multiple unindexed databases
     * @param unindexed The list of unindexed databases. Can be of size 1.
     */
    private void showInvalidIndex(boolean plural, List<SleuthkitHashSet> unindexed) {
        String total = "";
        String message;
        for (HashDb hdb : unindexed) {
            total += "\n" + hdb.getHashSetName();
        }
        if (plural) {
            message = NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.dbsNotIndexedMsg", total);
        } else {
            message = NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.dbNotIndexedMsg", total);
        }
        int res = JOptionPane.showConfirmDialog(this, message,
                NbBundle.getMessage(this.getClass(),
                        "HashDbConfigPanel.unindexedDbsMsg"),
                JOptionPane.YES_NO_OPTION);
        if (res == JOptionPane.YES_OPTION) {
            ModalNoButtons indexingDialog = new ModalNoButtons(this, new Frame(), unindexed);
            indexingDialog.setLocationRelativeTo(null);
            indexingDialog.setVisible(true);
            indexingDialog.setModal(true);
            hashSetTableModel.refreshModel();
        }
        if (res == JOptionPane.NO_OPTION) {
            JOptionPane.showMessageDialog(this, NbBundle.getMessage(this.getClass(),
                    "HashDbConfigPanel.allUnindexedDbsRmFromListMsg"));
            removeThese(unindexed);
        }
        try {
            hashSetManager.save();
        } catch (HashDbManager.HashDbManagerException ex) {
            JOptionPane.showMessageDialog(this, Bundle.HashLookupSettingsPanel_saveFail_message(), Bundle.HashLookupSettingsPanel_saveFail_title(), JOptionPane.ERROR_MESSAGE);
        }
    }

    boolean valid() {
        return true;
    }

    /**
     * This class implements a table for displaying configured hash sets.
     */
    private class HashSetTable extends JTable {

        @Override
        public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
            // Use the hash set name as the cell text.
            JComponent cellRenderer = (JComponent) super.prepareRenderer(renderer, row, column);
            cellRenderer.setToolTipText((String) getValueAt(row, column));

            // Give the user a visual indication of any hash sets with a hash
            // database that needs to be indexed by displaying the hash set name
            // in red.
            if (hashSetTableModel.isValid(row)) {
                cellRenderer.setForeground(Color.black);
            } else {
                cellRenderer.setForeground(Color.red);
            }

            return cellRenderer;
        }

        public HashDb getSelection() {
            return hashSetTableModel.getHashSetAt(getSelectionModel().getMinSelectionIndex());
        }

        public void setSelection(int index) {
            if (index >= 0 && index < hashSetTable.getRowCount()) {
                getSelectionModel().setSelectionInterval(index, index);
            }
        }

        public void selectRowByDatabase(HashDb db) {
            setSelection(hashSetTableModel.getIndexByDatabase(db));
        }

        @Deprecated
        public void selectRowByName(String name) {
            setSelection(hashSetTableModel.getIndexByName(name));
        }
    }

    /**
     * This class implements the table model for the table used to display
     * configured hash sets.
     */
    private class HashSetTableModel extends AbstractTableModel {

        List<HashDb> hashSets = HashDbManager.getInstance().getAllHashSets();

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public int getRowCount() {
            return hashSets.size();
        }

        @Override
        public String getColumnName(int column) {
            return NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.nameColLbl");
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return hashSets.get(rowIndex).getDisplayName();
        }

        private boolean isValid(int rowIndex) {
            try {
                return hashSets.get(rowIndex).isValid();
            } catch (TskCoreException ex) {
                Logger.getLogger(HashSetTableModel.class.getName()).log(Level.SEVERE, "Error getting index info for hash set", ex); //NON-NLS
                return false;
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            throw new UnsupportedOperationException(
                    NbBundle.getMessage(this.getClass(), "HashDbConfigPanel.editingCellsNotSupportedMsg"));
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        HashDb getHashSetAt(int index) {
            if (!hashSets.isEmpty() && index >= 0 && index < hashSets.size()) {
                return hashSets.get(index);
            } else {
                return null;
            }
        }

        int getIndexByDatabase(HashDb db) {
            for (int i = 0; i < hashSets.size(); ++i) {
                if (hashSets.get(i).equals(db)) {
                    return i;
                }
            }
            return -1;
        }

        @Deprecated
        int getIndexByName(String name) {
            for (int i = 0; i < hashSets.size(); ++i) {
                if (hashSets.get(i).getHashSetName().equals(name)) {
                    return i;
                }
            }
            return -1;
        }

        void refreshModel() {
            hashSets = HashDbManager.getInstance().getAllHashSets();
            refreshDisplay();
        }

        void refreshDisplay() {
            fireTableDataChanged();
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel2 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jButton3 = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        jPanel1 = new javax.swing.JPanel();
        ingestWarningLabel = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        hashSetTable = new HashSetTable();
        deleteDatabaseButton = new javax.swing.JButton();
        importDatabaseButton = new javax.swing.JButton();
        hashDatabasesLabel = new javax.swing.JLabel();
        nameLabel = new javax.swing.JLabel();
        hashDbNameLabel = new javax.swing.JLabel();
        hashDbLocationLabel = new javax.swing.JLabel();
        locationLabel = new javax.swing.JLabel();
        typeLabel = new javax.swing.JLabel();
        hashDbTypeLabel = new javax.swing.JLabel();
        hashDbIndexStatusLabel = new javax.swing.JLabel();
        indexLabel = new javax.swing.JLabel();
        indexButton = new javax.swing.JButton();
        sendIngestMessagesCheckBox = new javax.swing.JCheckBox();
        informationLabel = new javax.swing.JLabel();
        optionsLabel = new javax.swing.JLabel();
        informationSeparator = new javax.swing.JSeparator();
        optionsSeparator = new javax.swing.JSeparator();
        createDatabaseButton = new javax.swing.JButton();
        indexPathLabelLabel = new javax.swing.JLabel();
        indexPathLabel = new javax.swing.JLabel();
        addHashesToDatabaseButton = new javax.swing.JButton();
        versionLabel = new javax.swing.JLabel();
        hashDbVersionLabel = new javax.swing.JLabel();
        orgLabel = new javax.swing.JLabel();
        hashDbOrgLabel = new javax.swing.JLabel();
        readOnlyLabel = new javax.swing.JLabel();
        hashDbReadOnlyLabel = new javax.swing.JLabel();

        jLabel2.setFont(jLabel2.getFont().deriveFont(jLabel2.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.jLabel2.text")); // NOI18N

        jLabel4.setFont(jLabel4.getFont().deriveFont(jLabel4.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(jLabel4, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.jLabel4.text")); // NOI18N

        jLabel6.setFont(jLabel6.getFont().deriveFont(jLabel6.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(jLabel6, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.jLabel6.text")); // NOI18N

        jButton3.setFont(jButton3.getFont().deriveFont(jButton3.getFont().getStyle() & ~java.awt.Font.BOLD, 14));
        org.openide.awt.Mnemonics.setLocalizedText(jButton3, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.jButton3.text")); // NOI18N

        ingestWarningLabel.setFont(ingestWarningLabel.getFont().deriveFont(ingestWarningLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        ingestWarningLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/modules/hashdatabase/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(ingestWarningLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.ingestWarningLabel.text")); // NOI18N

        hashSetTable.setFont(hashSetTable.getFont().deriveFont(hashSetTable.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        hashSetTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        hashSetTable.setShowHorizontalLines(false);
        hashSetTable.setShowVerticalLines(false);
        hashSetTable.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                hashSetTableKeyPressed(evt);
            }
        });
        jScrollPane1.setViewportView(hashSetTable);

        deleteDatabaseButton.setFont(deleteDatabaseButton.getFont().deriveFont(deleteDatabaseButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        deleteDatabaseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/modules/hashdatabase/delete16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(deleteDatabaseButton, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.deleteDatabaseButton.text")); // NOI18N
        deleteDatabaseButton.setMaximumSize(new java.awt.Dimension(140, 25));
        deleteDatabaseButton.setMinimumSize(new java.awt.Dimension(140, 25));
        deleteDatabaseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteDatabaseButtonActionPerformed(evt);
            }
        });

        importDatabaseButton.setFont(importDatabaseButton.getFont().deriveFont(importDatabaseButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        importDatabaseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/modules/hashdatabase/import16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(importDatabaseButton, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.importDatabaseButton.text")); // NOI18N
        importDatabaseButton.setToolTipText(org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.importDatabaseButton.toolTipText")); // NOI18N
        importDatabaseButton.setMaximumSize(new java.awt.Dimension(140, 25));
        importDatabaseButton.setMinimumSize(new java.awt.Dimension(140, 25));
        importDatabaseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importDatabaseButtonActionPerformed(evt);
            }
        });

        hashDatabasesLabel.setFont(hashDatabasesLabel.getFont().deriveFont(hashDatabasesLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(hashDatabasesLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.hashDatabasesLabel.text")); // NOI18N

        nameLabel.setFont(nameLabel.getFont().deriveFont(nameLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(nameLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.nameLabel.text")); // NOI18N

        hashDbNameLabel.setFont(hashDbNameLabel.getFont().deriveFont(hashDbNameLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(hashDbNameLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.hashDbNameLabel.text")); // NOI18N

        hashDbLocationLabel.setFont(hashDbLocationLabel.getFont().deriveFont(hashDbLocationLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(hashDbLocationLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.hashDbLocationLabel.text")); // NOI18N

        locationLabel.setFont(locationLabel.getFont().deriveFont(locationLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(locationLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.locationLabel.text")); // NOI18N

        typeLabel.setFont(typeLabel.getFont().deriveFont(typeLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(typeLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.typeLabel.text")); // NOI18N

        hashDbTypeLabel.setFont(hashDbTypeLabel.getFont().deriveFont(hashDbTypeLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(hashDbTypeLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.hashDbTypeLabel.text")); // NOI18N

        hashDbIndexStatusLabel.setFont(hashDbIndexStatusLabel.getFont().deriveFont(hashDbIndexStatusLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(hashDbIndexStatusLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.hashDbIndexStatusLabel.text")); // NOI18N

        indexLabel.setFont(indexLabel.getFont().deriveFont(indexLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(indexLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.indexLabel.text")); // NOI18N

        indexButton.setFont(indexButton.getFont().deriveFont(indexButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(indexButton, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.indexButton.text")); // NOI18N
        indexButton.setEnabled(false);
        indexButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                indexButtonActionPerformed(evt);
            }
        });

        sendIngestMessagesCheckBox.setFont(sendIngestMessagesCheckBox.getFont().deriveFont(sendIngestMessagesCheckBox.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(sendIngestMessagesCheckBox, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.sendIngestMessagesCheckBox.text")); // NOI18N
        sendIngestMessagesCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sendIngestMessagesCheckBoxActionPerformed(evt);
            }
        });

        informationLabel.setFont(informationLabel.getFont().deriveFont(informationLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(informationLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.informationLabel.text")); // NOI18N

        optionsLabel.setFont(optionsLabel.getFont().deriveFont(optionsLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(optionsLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.optionsLabel.text")); // NOI18N

        createDatabaseButton.setFont(createDatabaseButton.getFont().deriveFont(createDatabaseButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        createDatabaseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/modules/hashdatabase/new16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(createDatabaseButton, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.createDatabaseButton.text")); // NOI18N
        createDatabaseButton.setToolTipText(org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.createDatabaseButton.toolTipText")); // NOI18N
        createDatabaseButton.setMaximumSize(new java.awt.Dimension(140, 25));
        createDatabaseButton.setMinimumSize(new java.awt.Dimension(140, 25));
        createDatabaseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                createDatabaseButtonActionPerformed(evt);
            }
        });

        indexPathLabelLabel.setFont(indexPathLabelLabel.getFont().deriveFont(indexPathLabelLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(indexPathLabelLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.indexPathLabelLabel.text")); // NOI18N

        indexPathLabel.setFont(indexPathLabel.getFont().deriveFont(indexPathLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(indexPathLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.indexPathLabel.text")); // NOI18N

        addHashesToDatabaseButton.setFont(addHashesToDatabaseButton.getFont().deriveFont(addHashesToDatabaseButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(addHashesToDatabaseButton, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.addHashesToDatabaseButton.text")); // NOI18N
        addHashesToDatabaseButton.setEnabled(false);
        addHashesToDatabaseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addHashesToDatabaseButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(versionLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.versionLabel.text_1")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(hashDbVersionLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.hashDbVersionLabel.text_1")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(orgLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.orgLabel.text_1")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(hashDbOrgLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.hashDbOrgLabel.text_1")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(readOnlyLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.readOnlyLabel.text_1")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(hashDbReadOnlyLabel, org.openide.util.NbBundle.getMessage(HashLookupSettingsPanel.class, "HashLookupSettingsPanel.hashDbReadOnlyLabel.text_1")); // NOI18N

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(1, 1, 1)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(informationLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addGap(309, 309, 309))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addGap(10, 10, 10)
                                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addGroup(jPanel1Layout.createSequentialGroup()
                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                    .addComponent(indexLabel)
                                                    .addComponent(indexPathLabelLabel))
                                                .addGap(55, 55, 55)
                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                    .addComponent(hashDbIndexStatusLabel)
                                                    .addComponent(indexPathLabel)))
                                            .addGroup(jPanel1Layout.createSequentialGroup()
                                                .addComponent(indexButton)
                                                .addGap(10, 10, 10)
                                                .addComponent(addHashesToDatabaseButton))
                                            .addGroup(jPanel1Layout.createSequentialGroup()
                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                    .addComponent(locationLabel)
                                                    .addComponent(typeLabel)
                                                    .addComponent(versionLabel)
                                                    .addComponent(orgLabel)
                                                    .addComponent(readOnlyLabel)
                                                    .addComponent(nameLabel))
                                                .addGap(55, 55, 55)
                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                    .addComponent(hashDbNameLabel)
                                                    .addComponent(hashDbTypeLabel)
                                                    .addComponent(hashDbLocationLabel)
                                                    .addComponent(hashDbVersionLabel)
                                                    .addComponent(hashDbOrgLabel)
                                                    .addComponent(hashDbReadOnlyLabel)))))
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addGap(70, 70, 70)
                                        .addComponent(informationSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, 305, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                                            .addComponent(optionsLabel)
                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                            .addComponent(optionsSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, 334, javax.swing.GroupLayout.PREFERRED_SIZE))
                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                            .addGap(25, 25, 25)
                                            .addComponent(sendIngestMessagesCheckBox))
                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                            .addGap(10, 10, 10)
                                            .addComponent(ingestWarningLabel))))
                                .addContainerGap(24, Short.MAX_VALUE))))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(hashDatabasesLabel)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(createDatabaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(importDatabaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(deleteDatabaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 0, Short.MAX_VALUE))))
        );

        jPanel1Layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {indexLabel, indexPathLabelLabel, locationLabel, nameLabel, orgLabel, readOnlyLabel, typeLabel, versionLabel});

        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(hashDatabasesLabel)
                .addGap(6, 6, 6)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(informationLabel)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGap(7, 7, 7)
                                .addComponent(informationSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, 3, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(7, 7, 7)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(nameLabel)
                            .addComponent(hashDbNameLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(typeLabel)
                            .addComponent(hashDbTypeLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(locationLabel)
                            .addComponent(hashDbLocationLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(versionLabel)
                            .addComponent(hashDbVersionLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(orgLabel)
                            .addComponent(hashDbOrgLabel))
                        .addGap(7, 7, 7)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(readOnlyLabel)
                            .addComponent(hashDbReadOnlyLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(indexPathLabelLabel)
                            .addComponent(indexPathLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(indexLabel)
                            .addComponent(hashDbIndexStatusLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(indexButton)
                            .addComponent(addHashesToDatabaseButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(optionsLabel)
                            .addComponent(optionsSeparator, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addComponent(sendIngestMessagesCheckBox)
                        .addGap(18, 18, 18)
                        .addComponent(ingestWarningLabel)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jScrollPane1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(createDatabaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(importDatabaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(deleteDatabaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        jScrollPane2.setViewportView(jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(jScrollPane2)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane2)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void addHashesToDatabaseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addHashesToDatabaseButtonActionPerformed

        HashDb hashDb = ((HashSetTable) hashSetTable).getSelection();
        AddHashValuesToDatabaseDialog dialog = new AddHashValuesToDatabaseDialog(hashDb);
    }//GEN-LAST:event_addHashesToDatabaseButtonActionPerformed

    private void createDatabaseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createDatabaseButtonActionPerformed
        HashDb hashDb = new HashDbCreateDatabaseDialog().getHashDatabase();
        if (null != hashDb) {
            if (hashDb instanceof CentralRepoHashSet) {
                int newDbIndex = ((CentralRepoHashSet) hashDb).getReferenceSetID();
                newReferenceSetIDs.add(newDbIndex);
            }

            hashSetTableModel.refreshModel();
            ((HashSetTable) hashSetTable).selectRowByDatabase(hashDb);
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_createDatabaseButtonActionPerformed

    private void sendIngestMessagesCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sendIngestMessagesCheckBoxActionPerformed
        HashDb hashDb = ((HashSetTable) hashSetTable).getSelection();
        if (hashDb != null) {
            hashDb.setSendIngestMessages(sendIngestMessagesCheckBox.isSelected());
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_sendIngestMessagesCheckBoxActionPerformed

    private void indexButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_indexButtonActionPerformed
        final HashDb hashDatabase = ((HashSetTable) hashSetTable).getSelection();
        assert hashDatabase != null;
        assert hashDatabase instanceof SleuthkitHashSet;

        // Add a listener for the INDEXING_DONE event. This listener will update
        // the UI.
        SleuthkitHashSet hashDb = (SleuthkitHashSet) hashDatabase;
        hashDb.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(SleuthkitHashSet.Event.INDEXING_DONE.toString())) {
                    HashDb selectedHashDb = ((HashSetTable) hashSetTable).getSelection();
                    if (selectedHashDb != null && hashDb != null && hashDb.equals(selectedHashDb)) {
                        updateComponents();
                    }
                    hashSetTableModel.refreshDisplay();
                }
            }
        });

        // Display a modal dialog box to kick off the indexing on a worker thread
        // and try to persuade the user to wait for the indexing task to finish.
        // TODO: If the user waits, this defeats the purpose of doing the indexing on a worker thread.
        // But if the user cancels the dialog, other operations on the database
        // may be attempted when it is not in a suitable state.
        ModalNoButtons indexDialog = new ModalNoButtons(this, new Frame(), hashDb);
        indexDialog.setLocationRelativeTo(null);
        indexDialog.setVisible(true);
        indexDialog.setModal(true);
    }//GEN-LAST:event_indexButtonActionPerformed

    private void importDatabaseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importDatabaseButtonActionPerformed
        HashDb hashDb = new HashDbImportDatabaseDialog().getHashDatabase();
        if (null != hashDb) {
            if (hashDb instanceof CentralRepoHashSet) {
                int newReferenceSetID = ((CentralRepoHashSet) hashDb).getReferenceSetID();
                newReferenceSetIDs.add(newReferenceSetID);
            }

            hashSetTableModel.refreshModel();
            ((HashSetTable) hashSetTable).selectRowByDatabase(hashDb);
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_importDatabaseButtonActionPerformed

    @Messages({
        "HashLookupSettingsPanel.promptTitle.deleteHashDb=Delete Hash Database from Configuration",
        "HashLookupSettingsPanel.promptMessage.deleteHashDb=This will make the hash database unavailable for lookup. Do you want to proceed?\n\nNote: The hash database can still be re-imported later."
    })
    private void deleteDatabaseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteDatabaseButtonActionPerformed
        if (JOptionPane.showConfirmDialog(this,
                Bundle.HashLookupSettingsPanel_promptMessage_deleteHashDb(),
                Bundle.HashLookupSettingsPanel_promptTitle_deleteHashDb(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
            HashDb hashDb = ((HashSetTable) hashSetTable).getSelection();
            if (hashDb != null) {
                try {
                    hashSetManager.removeHashDatabaseNoSave(hashDb);
                } catch (HashDbManager.HashDbManagerException ex) {
                    JOptionPane.showMessageDialog(this, Bundle.HashLookupSettingsPanel_removeDatabaseFailure_message(hashDb.getHashSetName()));
                }
                hashSetTableModel.refreshModel();
                firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            }
        }
    }//GEN-LAST:event_deleteDatabaseButtonActionPerformed

    private void hashSetTableKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_hashSetTableKeyPressed
        if (evt.getKeyCode() == KeyEvent.VK_DELETE) {
            HashDb hashDb = ((HashSetTable) hashSetTable).getSelection();
            if (hashDb != null) {
                try {
                    hashSetManager.removeHashDatabaseNoSave(hashDb);
                } catch (HashDbManager.HashDbManagerException ex) {
                    JOptionPane.showMessageDialog(this, Bundle.HashLookupSettingsPanel_removeDatabaseFailure_message(hashDb.getHashSetName()));
                }
                hashSetTableModel.refreshModel();
                firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            }
        }
    }//GEN-LAST:event_hashSetTableKeyPressed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addHashesToDatabaseButton;
    private javax.swing.JButton createDatabaseButton;
    private javax.swing.JButton deleteDatabaseButton;
    private javax.swing.JLabel hashDatabasesLabel;
    private javax.swing.JLabel hashDbIndexStatusLabel;
    private javax.swing.JLabel hashDbLocationLabel;
    private javax.swing.JLabel hashDbNameLabel;
    private javax.swing.JLabel hashDbOrgLabel;
    private javax.swing.JLabel hashDbReadOnlyLabel;
    private javax.swing.JLabel hashDbTypeLabel;
    private javax.swing.JLabel hashDbVersionLabel;
    private javax.swing.JTable hashSetTable;
    private javax.swing.JButton importDatabaseButton;
    private javax.swing.JButton indexButton;
    private javax.swing.JLabel indexLabel;
    private javax.swing.JLabel indexPathLabel;
    private javax.swing.JLabel indexPathLabelLabel;
    private javax.swing.JLabel informationLabel;
    private javax.swing.JSeparator informationSeparator;
    private javax.swing.JLabel ingestWarningLabel;
    private javax.swing.JButton jButton3;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JLabel locationLabel;
    private javax.swing.JLabel nameLabel;
    private javax.swing.JLabel optionsLabel;
    private javax.swing.JSeparator optionsSeparator;
    private javax.swing.JLabel orgLabel;
    private javax.swing.JLabel readOnlyLabel;
    private javax.swing.JCheckBox sendIngestMessagesCheckBox;
    private javax.swing.JLabel typeLabel;
    private javax.swing.JLabel versionLabel;
    // End of variables declaration//GEN-END:variables
}
