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
package org.sleuthkit.autopsy.keywordsearch;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.JCheckBox;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import org.openide.util.NbBundle;
import org.openide.util.actions.SystemAction;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.guiutils.SimpleTableCellRenderer;
import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 * Viewer panel widget for keyword lists that is used in the ingest config and
 * options area.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class DropdownListSearchPanel extends AdHocSearchPanel {

    private static final Logger logger = Logger.getLogger(DropdownListSearchPanel.class.getName());
    private static DropdownListSearchPanel instance;
    private XmlKeywordSearchList loader;
    private final KeywordListsTableModel listsTableModel;
    private final KeywordsTableModel keywordsTableModel;
    private ActionListener searchAddListener;
    private boolean ingestRunning;

    /**
     * Creates new form DropdownListSearchPanel
     */
    private DropdownListSearchPanel() {
        listsTableModel = new KeywordListsTableModel();
        keywordsTableModel = new KeywordsTableModel();
        initComponents();
        customizeComponents();
        dataSourceList.setModel(getDataSourceListModel());

        dataSourceList.addListSelectionListener((ListSelectionEvent evt) -> {
            firePropertyChange(Bundle.DropdownSingleTermSearchPanel_selected(), null, null);
        });
    }

    static synchronized DropdownListSearchPanel getDefault() {
        if (instance == null) {
            instance = new DropdownListSearchPanel();
        }
        return instance;
    }

    private void customizeComponents() {
        listsTable.setTableHeader(null);
        listsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        //customize column witdhs
        final int leftWidth = leftPane.getPreferredSize().width;
        TableColumn column;
        for (int i = 0; i < listsTable.getColumnCount(); i++) {
            column = listsTable.getColumnModel().getColumn(i);
            if (i == 0) {
                column.setPreferredWidth(((int) (leftWidth * 0.10)));
                column.setCellRenderer(new LeftCheckBoxRenderer());
            } else {
                column.setPreferredWidth(((int) (leftWidth * 0.89)));
                column.setCellRenderer(new SimpleTableCellRenderer());
            }
        }
        final int rightWidth = rightPane.getPreferredSize().width;
        for (int i = 0; i < keywordsTable.getColumnCount(); i++) {
            column = keywordsTable.getColumnModel().getColumn(i);
            if (i == 0) {
                column.setPreferredWidth(((int) (rightWidth * 0.60)));
            } else {
                column.setPreferredWidth(((int) (rightWidth * 0.38)));
            }
        }
        keywordsTable.setDefaultRenderer(String.class, new SimpleTableCellRenderer());

        loader = XmlKeywordSearchList.getCurrent();
        listsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel listSelectionModel = (ListSelectionModel) e.getSource();
                if (!listSelectionModel.isSelectionEmpty()) {
                    int index = listSelectionModel.getMinSelectionIndex();
                    KeywordList list = listsTableModel.getListAt(index);
                    keywordsTableModel.resync(list);
                } else {
                    keywordsTableModel.deleteAll();
                }
            }
        });

        ingestRunning = IngestManager.getInstance().isIngestRunning();
        updateComponents();

        IngestManager.getInstance().addIngestJobEventListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Object source = evt.getSource();
                if (source instanceof String && ((String) source).equals("LOCAL")) { //NON-NLS
                    EventQueue.invokeLater(() -> {
                        ingestRunning = IngestManager.getInstance().isIngestRunning();
                        updateComponents();
                    });
                }
            }
        });

        searchAddListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (ingestRunning) {
                    IngestSearchRunner.getInstance().addKeywordListsToAllJobs(listsTableModel.getSelectedLists());
                    logger.log(Level.INFO, "Submitted enqueued lists to ingest"); //NON-NLS
                } else {
                    searchAction(e);
                }
            }
        };

        searchAddButton.addActionListener(searchAddListener);
    }

    private void updateComponents() {
        ingestRunning = IngestManager.getInstance().isIngestRunning();
        if (ingestRunning) {
            searchAddButton.setText(NbBundle.getMessage(this.getClass(), "KeywordSearchListsViewerPanel.initIngest.addIngestTitle"));
            searchAddButton.setToolTipText(NbBundle.getMessage(this.getClass(), "KeywordSearchListsViewerPanel.initIngest.addIngestMsg"));

        } else {
            searchAddButton.setText(NbBundle.getMessage(this.getClass(), "KeywordSearchListsViewerPanel.initIngest.searchIngestTitle"));
            searchAddButton.setToolTipText(NbBundle.getMessage(this.getClass(), "KeywordSearchListsViewerPanel.initIngest.addIdxSearchMsg"));
        }
        listsTableModel.resync();
        updateIngestIndexLabel();
        
        jSaveSearchResults.setSelected(true);
    }

    private void updateIngestIndexLabel() {
        if (ingestRunning) {
            ingestIndexLabel.setText(NbBundle.getMessage(this.getClass(), "KeywordSearchListsViewerPanel.initIngest.ongoingIngestMsg", filesIndexed));
        } else {
            ingestIndexLabel.setText(NbBundle.getMessage(this.getClass(), "KeywordSearchListsViewerPanel.initIngest.fileIndexCtMsg", filesIndexed));
        }
    }

    @Override
    protected void postFilesIndexedChange() {
        updateIngestIndexLabel();
    }

    /**
     * Force resync the data view
     */
    void resync() {
        listsTableModel.resync();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jSplitPane1 = new javax.swing.JSplitPane();
        leftPane = new javax.swing.JScrollPane();
        listsTable = new javax.swing.JTable();
        rightPane = new javax.swing.JScrollPane();
        keywordsTable = new javax.swing.JTable();
        manageListsButton = new javax.swing.JButton();
        searchAddButton = new javax.swing.JButton();
        ingestIndexLabel = new javax.swing.JLabel();
        dataSourceCheckBox = new javax.swing.JCheckBox();
        jScrollPane1 = new javax.swing.JScrollPane();
        dataSourceList = new javax.swing.JList<>();
        jSaveSearchResults = new javax.swing.JCheckBox();

        leftPane.setMinimumSize(new java.awt.Dimension(150, 23));
        leftPane.setOpaque(false);

        listsTable.setBackground(new java.awt.Color(240, 240, 240));
        listsTable.setModel(listsTableModel);
        listsTable.setShowHorizontalLines(false);
        listsTable.setShowVerticalLines(false);
        listsTable.getTableHeader().setReorderingAllowed(false);
        leftPane.setViewportView(listsTable);

        jSplitPane1.setLeftComponent(leftPane);

        rightPane.setOpaque(false);

        keywordsTable.setBackground(new java.awt.Color(240, 240, 240));
        keywordsTable.setModel(keywordsTableModel);
        keywordsTable.setGridColor(new java.awt.Color(153, 153, 153));
        rightPane.setViewportView(keywordsTable);

        jSplitPane1.setRightComponent(rightPane);

        manageListsButton.setText(org.openide.util.NbBundle.getMessage(DropdownListSearchPanel.class, "KeywordSearchListsViewerPanel.manageListsButton.text")); // NOI18N
        manageListsButton.setToolTipText(org.openide.util.NbBundle.getMessage(DropdownListSearchPanel.class, "KeywordSearchListsViewerPanel.manageListsButton.toolTipText")); // NOI18N
        manageListsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manageListsButtonActionPerformed(evt);
            }
        });

        searchAddButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/search-icon.png"))); // NOI18N
        searchAddButton.setText(org.openide.util.NbBundle.getMessage(DropdownListSearchPanel.class, "KeywordSearchListsViewerPanel.searchAddButton.text")); // NOI18N
        searchAddButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchAddButtonActionPerformed(evt);
            }
        });

        ingestIndexLabel.setFont(ingestIndexLabel.getFont().deriveFont(ingestIndexLabel.getFont().getSize()-1f));
        ingestIndexLabel.setText(org.openide.util.NbBundle.getMessage(DropdownListSearchPanel.class, "KeywordSearchListsViewerPanel.ingestIndexLabel.text")); // NOI18N

        dataSourceCheckBox.setText(org.openide.util.NbBundle.getMessage(DropdownListSearchPanel.class, "DropdownListSearchPanel.dataSourceCheckBox.text")); // NOI18N
        dataSourceCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSourceCheckBoxActionPerformed(evt);
            }
        });

        dataSourceList.setMinimumSize(new java.awt.Dimension(0, 200));
        jScrollPane1.setViewportView(dataSourceList);

        jSaveSearchResults.setText(org.openide.util.NbBundle.getMessage(DropdownListSearchPanel.class, "DropdownListSearchPanel.jSaveSearchResults.text")); // NOI18N
        jSaveSearchResults.setToolTipText(org.openide.util.NbBundle.getMessage(DropdownListSearchPanel.class, "DropdownListSearchPanel.jSaveSearchResults.toolTipText")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
            .addComponent(jScrollPane1)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(dataSourceCheckBox)
                    .addComponent(jSaveSearchResults)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(searchAddButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(manageListsButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(ingestIndexLabel)))
                .addGap(0, 120, Short.MAX_VALUE))
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {manageListsButton, searchAddButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jSplitPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 183, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(dataSourceCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jSaveSearchResults)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(manageListsButton)
                    .addComponent(searchAddButton)
                    .addComponent(ingestIndexLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 13, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(23, 23, 23))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void manageListsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_manageListsButtonActionPerformed
        SystemAction.get(KeywordSearchConfigurationAction.class).performAction();
    }//GEN-LAST:event_manageListsButtonActionPerformed

    private void dataSourceCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSourceCheckBoxActionPerformed
        updateDataSourceListModel();
    }//GEN-LAST:event_dataSourceCheckBoxActionPerformed

    private void searchAddButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchAddButtonActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_searchAddButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox dataSourceCheckBox;
    private javax.swing.JList<String> dataSourceList;
    private javax.swing.JLabel ingestIndexLabel;
    private javax.swing.JCheckBox jSaveSearchResults;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JTable keywordsTable;
    private javax.swing.JScrollPane leftPane;
    private javax.swing.JTable listsTable;
    private javax.swing.JButton manageListsButton;
    private javax.swing.JScrollPane rightPane;
    private javax.swing.JButton searchAddButton;
    // End of variables declaration//GEN-END:variables

    private void searchAction(ActionEvent e) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        try {
            search(jSaveSearchResults.isSelected());
        } finally {
            setCursor(null);
        }
    }

    @Override
    List<KeywordList> getKeywordLists() {
        return listsTableModel.getSelectedListsL();
    }

    void addSearchButtonActionListener(ActionListener al) {
        searchAddButton.addActionListener(al);
    }

   /**
     * Get a set of data source object ids that are selected.
     * @return A set of selected object ids. 
     */
    @Override
    Set<Long> getDataSourcesSelected() {
        Set<Long> dataSourceObjIdSet = new HashSet<>();
        for (Long key : getDataSourceMap().keySet()) {
            String value = getDataSourceMap().get(key);
            for (String dataSource : this.dataSourceList.getSelectedValuesList()) {
                if (value.equals(dataSource)) {
                    dataSourceObjIdSet.add(key);
                }
            }
        }
        return dataSourceObjIdSet;
    }

    private class KeywordListsTableModel extends AbstractTableModel {
        //data

        private final XmlKeywordSearchList listsHandle = XmlKeywordSearchList.getCurrent();
        private final List<ListTableEntry> listData = new ArrayList<>();

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public int getRowCount() {
            return listData.size();
        }

        @Override
        public String getColumnName(int column) {
            String ret = null;
            switch (column) {
                case 0:
                    ret = NbBundle.getMessage(this.getClass(), "KeywordSearch.selectedColLbl");
                    break;
                case 1:
                    ret = NbBundle.getMessage(this.getClass(), "KeywordSearch.nameColLbl");
                    break;
                default:
                    break;
            }
            return ret;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Object ret = null;
            ListTableEntry entry = null;
            //iterate until row
            Iterator<ListTableEntry> it = listData.iterator();
            for (int i = 0; i <= rowIndex; ++i) {
                entry = it.next();
            }
            if (null != entry) {
                switch (columnIndex) {
                    case 0:
                        ret = (Object) entry.selected;
                        break;
                    case 1:
                        ret = (Object) entry.name;
                        break;
                    default:
                        break;
                }
            }
            return ret;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return (columnIndex == 0 && !ingestRunning);
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                ListTableEntry entry = null;
                Iterator<ListTableEntry> it = listData.iterator();
                for (int i = 0; i <= rowIndex; i++) {
                    entry = it.next();
                }
                if (entry != null) {
                    entry.selected = (Boolean) aValue;
                    if (ingestRunning) {
                        //updateUseForIngest(getListAt(rowIndex), (Boolean) aValue);
                    }
                }

            }
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        List<String> getAllLists() {
            List<String> ret = new ArrayList<>();
            for (ListTableEntry e : listData) {
                ret.add(e.name);
            }
            return ret;
        }

        KeywordList getListAt(int rowIndex) {
            return listsHandle.getList((String) getValueAt(rowIndex, 1));
        }

        List<String> getSelectedLists() {
            List<String> ret = new ArrayList<>();
            for (ListTableEntry e : listData) {
                if (e.selected) {
                    ret.add(e.name);
                }
            }
            return ret;
        }

        List<KeywordList> getSelectedListsL() {
            List<KeywordList> ret = new ArrayList<>();
            for (String s : getSelectedLists()) {
                ret.add(listsHandle.getList(s));
            }
            return ret;
        }

        boolean listExists(String list) {
            List<String> all = getAllLists();
            return all.contains(list);
        }

        //resync model from handle, then update table
        void resync() {
            listData.clear();
            addLists(listsHandle.getListsL());
            fireTableDataChanged();
        }

        //add lists to the model
        private void addLists(List<KeywordList> lists) {
            for (KeywordList list : lists) {
                if (!listExists(list.getName())) {
                    listData.add(new ListTableEntry(list, ingestRunning));
                }
            }
        }

        //single model entry
        private class ListTableEntry implements Comparable<ListTableEntry> {

            String name;
            Boolean selected;

            ListTableEntry(KeywordList list, boolean ingestRunning) {
                this.name = list.getName();
                if (ingestRunning) {
                    this.selected = list.getUseForIngest();
                } else {
                    this.selected = false;
                }
            }

            @Override
            public int compareTo(ListTableEntry e) {
                return this.name.compareTo(e.name);
            }
        }
    }

    private class KeywordsTableModel extends AbstractTableModel {

        List<KeywordTableEntry> listData = new ArrayList<>();

        @Override
        public int getRowCount() {
            return listData.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            String ret = null;
            switch (column) {
                case 0:
                    ret = NbBundle.getMessage(this.getClass(), "KeywordSearch.nameColLbl");
                    break;
                case 1:
                    ret = NbBundle.getMessage(this.getClass(), "KeywordSearch.typeColLbl");
                    break;
                default:
                    break;
            }
            return ret;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Object ret = null;
            KeywordTableEntry entry = null;
            //iterate until row
            Iterator<KeywordTableEntry> it = listData.iterator();
            for (int i = 0; i <= rowIndex; ++i) {
                entry = it.next();
            }
            if (null != entry) {
                switch (columnIndex) {
                    case 0:
                        ret = (Object) entry.name;
                        break;
                    case 1:
                        ret = (Object) entry.keywordType;
                        break;
                    default:
                        break;
                }
            }
            return ret;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        void resync(KeywordList list) {
            listData.clear();
            for (Keyword k : list.getKeywords()) {
                listData.add(new KeywordTableEntry(k));
            }
            fireTableDataChanged();
        }

        void deleteAll() {
            listData.clear();
            fireTableDataChanged();
        }

        //single model entry
        private class KeywordTableEntry implements Comparable<KeywordTableEntry> {

            String name;
            String keywordType;

            KeywordTableEntry(Keyword keyword) {
                this.name = keyword.getSearchTerm();
                this.keywordType = keyword.getSearchTermType();
            }

            @Override
            public int compareTo(KeywordTableEntry e) {
                return this.name.compareTo(e.name);
            }
        }
    }

    private class LeftCheckBoxRenderer extends JCheckBox implements TableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value,
                boolean isSelected, boolean hasFocus,
                int row, int column) {

            this.setHorizontalAlignment(JCheckBox.CENTER);
            this.setVerticalAlignment(JCheckBox.CENTER);

            setEnabled(!ingestRunning);

            boolean selected = (Boolean) table.getModel().getValueAt(row, 0);
            setSelected(selected);

            if (isSelected) {
                setBackground(listsTable.getSelectionBackground());
            } else {
                setBackground(listsTable.getBackground());
            }

            return this;
        }
    }
    
    /**
     * Update the dataSourceListModel
     */
    @NbBundle.Messages({"DropdownListSearchPanel.selected=Ad Hoc Search data source filter is selected"})
    void updateDataSourceListModel() {
        getDataSourceListModel().removeAllElements();
        for (String dsName : getDataSourceArray()) {
            getDataSourceListModel().addElement(dsName);
        }
        setComponentsEnabled();
        firePropertyChange(Bundle.DropdownListSearchPanel_selected(), null, null);
        
    }
    
    /**
     * Set the dataSourceList enabled if the dataSourceCheckBox is selected
     */
    private void setComponentsEnabled() {
        
        if (getDataSourceListModel().size() > 1) {
            this.dataSourceCheckBox.setEnabled(true);
            
            boolean enabled = this.dataSourceCheckBox.isSelected();
            this.dataSourceList.setEnabled(enabled);
            if (enabled) {
                this.dataSourceList.setSelectionInterval(0, this.dataSourceList.getModel().getSize()-1);
            } else {
                this.dataSourceList.setSelectedIndices(new int[0]);
            }
        } else {
            this.dataSourceCheckBox.setEnabled(false);
            this.dataSourceCheckBox.setSelected(false);
            this.dataSourceList.setEnabled(false);
            this.dataSourceList.setSelectedIndices(new int[0]);
        }
    }

}
