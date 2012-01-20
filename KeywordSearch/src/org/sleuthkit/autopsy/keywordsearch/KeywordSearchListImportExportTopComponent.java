/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.awt.Component;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;

/**
 * Top component which displays something.
 */
@ConvertAsProperties(dtd = "-//org.sleuthkit.autopsy.keywordsearch//KeywordSearchListImportExport//EN",
autostore = false)
@TopComponent.Description(preferredID = "KeywordSearchListImportExportTopComponent",
//iconBase="SET/PATH/TO/ICON/HERE", 
persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "explorer", openAtStartup = false)
@ActionID(category = "Window", id = "org.sleuthkit.autopsy.keywordsearch.KeywordSearchListImportExportTopComponent")
@ActionReference(path = "Menu/Window" /*, position = 333 */)
@TopComponent.OpenActionRegistration(displayName = "#CTL_KeywordSearchListImportExportAction",
preferredID = "KeywordSearchListImportExportTopComponent")
public final class KeywordSearchListImportExportTopComponent extends TopComponent implements KeywordSearchTopComponentInterface {

    private Logger logger = Logger.getLogger(KeywordSearchListImportExportTopComponent.class.getName());
    private KeywordListTableModel tableModel;

    public KeywordSearchListImportExportTopComponent() {
        tableModel = new KeywordListTableModel();
        initComponents();
        customizeComponents();
        setName(NbBundle.getMessage(KeywordSearchListImportExportTopComponent.class, "CTL_KeywordSearchListImportExportTopComponent"));
        setToolTipText(NbBundle.getMessage(KeywordSearchListImportExportTopComponent.class, "HINT_KeywordSearchListImportExportTopComponent"));


    }

    private void customizeComponents() {

        importButton.setToolTipText("Import list(s) of keywords from an external file.");
        exportButton.setToolTipText("Export selected list(s) of keywords to an external file.");
        deleteButton.setToolTipText("Delete selected list(s) of keywords.");


        listsTable.setAutoscrolls(true);
        //listsTable.setTableHeader(null);
        listsTable.setShowHorizontalLines(false);
        listsTable.setShowVerticalLines(false);

        listsTable.getParent().setBackground(listsTable.getBackground());

        //customize column witdhs
        listsTable.setSize(260, 200);
        final int width = listsTable.getSize().width;
        TableColumn column = null;
        for (int i = 0; i < 4; i++) {
            column = listsTable.getColumnModel().getColumn(i);
            switch (i) {
                case 0:
                case 1:
                case 2:
                    column.setCellRenderer(new CellTooltipRenderer());
                    column.setPreferredWidth(((int) (width * 0.28)));
                    column.setResizable(true);
                    break;
                case 3:
                    column.setPreferredWidth(((int) (width * 0.15)));
                    column.setResizable(false);
                    break;
                default:
                    break;

            }

        }
        listsTable.setCellSelectionEnabled(false);
        tableModel.resync();
        if (KeywordSearchListsXML.getCurrent().getNumberLists() == 0) {
            exportButton.setEnabled(false);
        }

        KeywordSearchListsXML.getCurrent().addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(KeywordSearchListsXML.ListsEvt.LIST_ADDED.toString())
                        || evt.getPropertyName().equals(KeywordSearchListsXML.ListsEvt.LIST_DELETED.toString())) {
                    tableModel.resync();

                    if (Integer.valueOf((Integer) evt.getNewValue()) == 0) {
                        exportButton.setEnabled(false);
                        deleteButton.setEnabled(false);
                    } 
                    //else if (Integer.valueOf((Integer) evt.getOldValue()) == 0) {
                      //  exportButton.setEnabled(true);
                    //}
                } else if (evt.getPropertyName().equals(KeywordSearchListsXML.ListsEvt.LIST_UPDATED.toString())) {
                    tableModel.resync((String) evt.getNewValue()); //changed list name
                }
            }
        });

        initButtons();

    }

    private void initButtons() {
        if (tableModel.getSelectedLists().isEmpty()) {
            deleteButton.setEnabled(false);
            exportButton.setEnabled(false);
        } else {
            deleteButton.setEnabled(true);
            exportButton.setEnabled(true);
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainScrollPane = new javax.swing.JScrollPane();
        mainPanel = new javax.swing.JPanel();
        filesIndexedNameLabel = new javax.swing.JLabel();
        filesIndexedValLabel = new javax.swing.JLabel();
        importButton = new javax.swing.JButton();
        exportButton = new javax.swing.JButton();
        deleteButton = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        listsTable = new javax.swing.JTable();
        topLabel = new javax.swing.JLabel();

        mainScrollPane.setPreferredSize(new java.awt.Dimension(349, 433));

        mainPanel.setPreferredSize(new java.awt.Dimension(349, 433));

        org.openide.awt.Mnemonics.setLocalizedText(filesIndexedNameLabel, org.openide.util.NbBundle.getMessage(KeywordSearchListImportExportTopComponent.class, "KeywordSearchListImportExportTopComponent.filesIndexedNameLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(filesIndexedValLabel, org.openide.util.NbBundle.getMessage(KeywordSearchListImportExportTopComponent.class, "KeywordSearchListImportExportTopComponent.filesIndexedValLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(importButton, org.openide.util.NbBundle.getMessage(KeywordSearchListImportExportTopComponent.class, "KeywordSearchListImportExportTopComponent.importButton.text")); // NOI18N
        importButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(exportButton, org.openide.util.NbBundle.getMessage(KeywordSearchListImportExportTopComponent.class, "KeywordSearchListImportExportTopComponent.exportButton.text")); // NOI18N
        exportButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(deleteButton, org.openide.util.NbBundle.getMessage(KeywordSearchListImportExportTopComponent.class, "KeywordSearchListImportExportTopComponent.deleteButton.text")); // NOI18N
        deleteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteButtonActionPerformed(evt);
            }
        });

        listsTable.setModel(tableModel);
        listsTable.setShowHorizontalLines(false);
        listsTable.setShowVerticalLines(false);
        listsTable.getTableHeader().setReorderingAllowed(false);
        jScrollPane1.setViewportView(listsTable);

        topLabel.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(topLabel, org.openide.util.NbBundle.getMessage(KeywordSearchListImportExportTopComponent.class, "KeywordSearchListImportExportTopComponent.topLabel.text")); // NOI18N

        javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
        mainPanel.setLayout(mainPanelLayout);
        mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 266, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap())
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(topLabel)
                            .addGroup(mainPanelLayout.createSequentialGroup()
                                .addComponent(importButton)
                                .addGap(33, 33, 33)
                                .addComponent(exportButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 46, Short.MAX_VALUE)
                                .addComponent(deleteButton))
                            .addGroup(mainPanelLayout.createSequentialGroup()
                                .addComponent(filesIndexedNameLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(filesIndexedValLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 62, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addContainerGap(84, Short.MAX_VALUE))))
        );
        mainPanelLayout.setVerticalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(topLabel)
                .addGap(34, 34, 34)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 251, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(importButton)
                    .addComponent(deleteButton)
                    .addComponent(exportButton))
                .addGap(35, 35, 35)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(filesIndexedNameLabel)
                    .addComponent(filesIndexedValLabel))
                .addContainerGap(64, Short.MAX_VALUE))
        );

        mainScrollPane.setViewportView(mainPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 368, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 455, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    public void importButtonAction(java.awt.event.ActionEvent evt) {
        importButtonActionPerformed(evt);
    }

    private void importButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importButtonActionPerformed
        final String FEATURE_NAME = "Keyword List Import";

        JFileChooser chooser = new JFileChooser();
        final String EXTENSION = "xml";
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Keyword List XML file", EXTENSION);
        chooser.setFileFilter(filter);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int returnVal = chooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File selFile = chooser.getSelectedFile();
            if (selFile == null) {
                return;
            }

            //force append extension if not given
            String fileAbs = selFile.getAbsolutePath();

            final KeywordSearchListsXML reader = new KeywordSearchListsXML(fileAbs);
            if (!reader.load()) {
                KeywordSearchUtil.displayDialog(FEATURE_NAME, "Error importing keyword list from file " + fileAbs, KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
                return;
            }

            List<KeywordSearchList> toImport = reader.getListsL();
            List<KeywordSearchList> toImportConfirmed = new ArrayList<KeywordSearchList>();

            final KeywordSearchListsXML writer = KeywordSearchListsXML.getCurrent();

            for (KeywordSearchList list : toImport) {
                //check name collisions
                if (writer.listExists(list.getName())) {
                    Object[] options = {"Yes, overwrite",
                        "No, skip",
                        "Cancel import"};
                    int choice = JOptionPane.showOptionDialog(this,
                            "Keyword list <" + list.getName() + "> already exists locally, overwrite?",
                            "Import list conflict",
                            JOptionPane.YES_NO_CANCEL_OPTION,
                            JOptionPane.QUESTION_MESSAGE,
                            null,
                            options,
                            options[0]);
                    if (choice == JOptionPane.OK_OPTION) {
                        toImportConfirmed.add(list);
                    } else if (choice == JOptionPane.CANCEL_OPTION) {
                        break;
                    }

                } else {
                    //no conflict
                    toImportConfirmed.add(list);
                }

            }

            if (toImportConfirmed.isEmpty()) {
                return;
            }

            if (writer.writeLists(toImportConfirmed)) {
                KeywordSearchUtil.displayDialog(FEATURE_NAME, "Keyword list imported", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);
            }

            initButtons();
        }
    }//GEN-LAST:event_importButtonActionPerformed

    private void exportButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportButtonActionPerformed
        final String FEATURE_NAME = "Keyword List Export";

        List<String> toExport = tableModel.getSelectedLists();
        if (toExport.isEmpty()) {
            KeywordSearchUtil.displayDialog(FEATURE_NAME, "Please select keyword lists to export", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        final String EXTENSION = "xml";
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Keyword List XML file", EXTENSION);
        chooser.setFileFilter(filter);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int returnVal = chooser.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File selFile = chooser.getSelectedFile();
            if (selFile == null) {
                return;
            }

            //force append extension if not given
            String fileAbs = selFile.getAbsolutePath();
            if (!fileAbs.endsWith("." + EXTENSION)) {
                fileAbs = fileAbs + "." + EXTENSION;
                selFile = new File(fileAbs);
            }

            boolean shouldWrite = true;
            if (selFile.exists()) {
                shouldWrite = KeywordSearchUtil.displayConfirmDialog(FEATURE_NAME, "File " + selFile.getName() + " exists, overwrite?", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN);
            }
            if (!shouldWrite) {
                return;
            }


            final KeywordSearchListsXML reader = KeywordSearchListsXML.getCurrent();

            List<KeywordSearchList> toWrite = new ArrayList<KeywordSearchList>();
            for (String listName : toExport) {
                toWrite.add(reader.getList(listName));
            }
            final KeywordSearchListsXML exporter = new KeywordSearchListsXML(fileAbs);
            boolean written = exporter.writeLists(toWrite);
            if (written) {
                KeywordSearchUtil.displayDialog(FEATURE_NAME, "Keyword lists exported", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);
                return;
            }
        }

    }//GEN-LAST:event_exportButtonActionPerformed

    private void deleteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteButtonActionPerformed
        tableModel.deleteSelected();
        initButtons();
    }//GEN-LAST:event_deleteButtonActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton deleteButton;
    private javax.swing.JButton exportButton;
    private javax.swing.JLabel filesIndexedNameLabel;
    private javax.swing.JLabel filesIndexedValLabel;
    private javax.swing.JButton importButton;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable listsTable;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JScrollPane mainScrollPane;
    private javax.swing.JLabel topLabel;
    // End of variables declaration//GEN-END:variables

    @Override
    public void componentOpened() {
    }

    @Override
    public void componentClosed() {
    }

    void writeProperties(java.util.Properties p) {

        p.setProperty("version", "1.0");

    }

    void readProperties(java.util.Properties p) {
    }

    @Override
    public void addSearchButtonListener(ActionListener l) {
    }

    @Override
    public List<Keyword> getQueryList() {
        return null;
    }

    @Override
    public String getQueryText() {
        return null;
    }

    @Override
    public boolean isLuceneQuerySelected() {
        return false;
    }

    @Override
    public boolean isMultiwordQuery() {
        return false;
    }

    @Override
    public boolean isRegexQuerySelected() {
        return false;
    }

    @Override
    public void setFilesIndexed(int filesIndexed) {
        filesIndexedValLabel.setText(Integer.toString(filesIndexed));
    }

    private class KeywordListTableModel extends AbstractTableModel {
        //data

        private KeywordSearchListsXML listsHandle = KeywordSearchListsXML.getCurrent();
        private Set<TableEntry> listData = new TreeSet<TableEntry>();

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public int getRowCount() {
            return listData.size();
        }

        @Override
        public String getColumnName(int column) {
            String colName = null;
            switch (column) {
                case 0:
                    colName = "Name";
                    break;
                case 1:
                    colName = "Created";
                    break;
                case 2:
                    colName = "Modified";
                    break;
                case 3:
                    colName = "Sel.";
                    break;
                default:
                    ;

            }
            return colName;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Object ret = null;
            TableEntry entry = null;
            //iterate until row
            Iterator<TableEntry> it = listData.iterator();
            for (int i = 0; i <= rowIndex; ++i) {
                entry = it.next();
            }
            switch (columnIndex) {
                case 0:
                    ret = (Object) entry.name;
                    break;
                case 1:
                    ret = (Object) entry.created;
                    break;
                case 2:
                    ret = (Object) entry.modified;
                    break;
                case 3:
                    ret = (Object) entry.isActive;
                    break;
                default:
                    break;
            }
            return ret;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 3 ? true : false;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 3) {
                TableEntry entry = null;
                //iterate until row
                Iterator<TableEntry> it = listData.iterator();
                for (int i = 0; i <= rowIndex && it.hasNext(); ++i) {
                    entry = it.next();
                }
                if (entry != null)
                    entry.isActive = (Boolean) aValue;
               
                initButtons();
            }
        }

        @Override
        public Class getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        List<String> getAllLists() {
            List<String> ret = new ArrayList<String>();
            for (TableEntry e : listData) {
                ret.add(e.name);
            }
            return ret;
        }

        List<String> getSelectedLists() {
            List<String> ret = new ArrayList<String>();
            for (TableEntry e : listData) {
                if (e.isActive && !e.name.equals("")) {
                    ret.add(e.name);
                }
            }
            return ret;
        }

        boolean listExists(String list) {
            List<String> all = getAllLists();
            return all.contains(list);
        }

        //delete selected from handle, events are fired from the handle
        void deleteSelected() {
            List<TableEntry> toDel = new ArrayList<TableEntry>();
            for (TableEntry e : listData) {
                if (e.isActive && !e.name.equals("")) {
                    toDel.add(e);
                }
            }
            for (TableEntry del : toDel) {
                listsHandle.deleteList(del.name);
            }

        }

        //resync model from handle, then update table
        void resync() {
            listData.clear();
            addLists(listsHandle.getListsL());
            fireTableDataChanged();
        }

        //resync single model entry from handle, then update table
        void resync(String listName) {
            TableEntry found = null;
            for (TableEntry e : listData) {
                if (e.name.equals(listName)) {
                    found = e;
                    break;
                }
            }
            if (found != null) {
                listData.remove(found);
                addList(listsHandle.getList(listName));
            }
            fireTableDataChanged();
        }

        //add list to the model
        private void addList(KeywordSearchList list) {
            if (!listExists(list.getName())) {
                listData.add(new TableEntry(list));
            }
        }

        //add lists to the model
        private void addLists(List<KeywordSearchList> lists) {
            for (KeywordSearchList list : lists) {
                if (!listExists(list.getName())) {
                    listData.add(new TableEntry(list));
                }
            }
        }

        //single model entry
        class TableEntry implements Comparable {

            private DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            String name;
            String created;
            String modified;
            Boolean isActive;

            TableEntry(KeywordSearchList list, Boolean isActive) {
                this.name = list.getName();
                this.created = dateFormatter.format(list.getDateCreated());
                this.modified = dateFormatter.format(list.getDateModified());
                this.isActive = isActive;
            }

            TableEntry(KeywordSearchList list) {
                this.name = list.getName();
                this.created = dateFormatter.format(list.getDateCreated());
                this.modified = dateFormatter.format(list.getDateModified());
                this.isActive = false;
            }

            @Override
            public int compareTo(Object o) {
                return this.name.compareTo(((TableEntry) o).name);
            }
        }
    }

    /**
     * tooltips that show text
     */
    private static class CellTooltipRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value,
                boolean isSelected, boolean hasFocus,
                int row, int column) {

            if (column < 3) {
                String val = (String) table.getModel().getValueAt(row, column);
                setToolTipText(val);
                setText(val);
            }

            return this;
        }
    }
}
