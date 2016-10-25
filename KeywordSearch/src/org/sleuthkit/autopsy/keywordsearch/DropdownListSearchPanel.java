/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2015 Basis Technology Corp.
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

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
import org.sleuthkit.autopsy.ingest.IngestManager;
import javax.swing.ImageIcon;
import static javax.swing.SwingConstants.CENTER;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Viewer panel widget for keyword lists that is used in the ingest config and
 * options area.
 */
class DropdownListSearchPanel extends KeywordSearchPanel {

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
            }
        }
        final int rightWidth = rightPane.getPreferredSize().width;
        for (int i = 0; i < keywordsTable.getColumnCount(); i++) {
            column = keywordsTable.getColumnModel().getColumn(i);
            if (i == 0) {
                column.setPreferredWidth(((int) (rightWidth * 0.78)));
            } else {
                column.setPreferredWidth(((int) (rightWidth * 0.20)));
                column.setCellRenderer(new CheckBoxRenderer());
            }
        }

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
                    EventQueue.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            ingestRunning = IngestManager.getInstance().isIngestRunning();
                            updateComponents();
                        }
                    });
                }
            }
        });

        searchAddListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (ingestRunning) {
                    SearchRunner.getInstance().addKeywordListsToAllJobs(listsTableModel.getSelectedLists());
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

        setFont(getFont().deriveFont(getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        jSplitPane1.setFont(leftPane.getFont());

        leftPane.setFont(leftPane.getFont().deriveFont(leftPane.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        leftPane.setMinimumSize(new java.awt.Dimension(150, 23));

        listsTable.setBackground(new java.awt.Color(240, 240, 240));
        listsTable.setFont(listsTable.getFont().deriveFont(listsTable.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        listsTable.setModel(listsTableModel);
        listsTable.setShowHorizontalLines(false);
        listsTable.setShowVerticalLines(false);
        listsTable.getTableHeader().setReorderingAllowed(false);
        leftPane.setViewportView(listsTable);

        jSplitPane1.setLeftComponent(leftPane);

        rightPane.setFont(rightPane.getFont().deriveFont(rightPane.getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        keywordsTable.setBackground(new java.awt.Color(240, 240, 240));
        keywordsTable.setFont(keywordsTable.getFont().deriveFont(keywordsTable.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        keywordsTable.setModel(keywordsTableModel);
        keywordsTable.setGridColor(new java.awt.Color(153, 153, 153));
        rightPane.setViewportView(keywordsTable);

        jSplitPane1.setRightComponent(rightPane);

        manageListsButton.setFont(manageListsButton.getFont().deriveFont(manageListsButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        manageListsButton.setText(org.openide.util.NbBundle.getMessage(DropdownListSearchPanel.class, "KeywordSearchListsViewerPanel.manageListsButton.text")); // NOI18N
        manageListsButton.setToolTipText(org.openide.util.NbBundle.getMessage(DropdownListSearchPanel.class, "KeywordSearchListsViewerPanel.manageListsButton.toolTipText")); // NOI18N
        manageListsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manageListsButtonActionPerformed(evt);
            }
        });

        searchAddButton.setFont(searchAddButton.getFont().deriveFont(searchAddButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        searchAddButton.setText(org.openide.util.NbBundle.getMessage(DropdownListSearchPanel.class, "KeywordSearchListsViewerPanel.searchAddButton.text")); // NOI18N
        searchAddButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchAddButtonActionPerformed(evt);
            }
        });

        ingestIndexLabel.setFont(ingestIndexLabel.getFont().deriveFont(ingestIndexLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 10));
        ingestIndexLabel.setText(org.openide.util.NbBundle.getMessage(DropdownListSearchPanel.class, "KeywordSearchListsViewerPanel.ingestIndexLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(searchAddButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 220, Short.MAX_VALUE)
                        .addComponent(manageListsButton))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(ingestIndexLabel)
                        .addGap(0, 317, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jSplitPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 268, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 7, Short.MAX_VALUE)
                .addComponent(ingestIndexLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(manageListsButton)
                    .addComponent(searchAddButton))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void manageListsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_manageListsButtonActionPerformed
        SystemAction.get(KeywordSearchConfigurationAction.class).performAction();
    }//GEN-LAST:event_manageListsButtonActionPerformed

    private void searchAddButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchAddButtonActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_searchAddButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel ingestIndexLabel;
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
            search();
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

    private class KeywordListsTableModel extends AbstractTableModel {
        //data

        private XmlKeywordSearchList listsHandle = XmlKeywordSearchList.getCurrent();
        private List<ListTableEntry> listData = new ArrayList<>();

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
                    ret = NbBundle.getMessage(this.getClass(), "KeywordSearch.regExColLbl");
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
                        ret = (Object) entry.regex;
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
            Boolean regex;

            KeywordTableEntry(Keyword keyword) {
                this.name = keyword.getQuery();
                this.regex = !keyword.isLiteral();
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
     * A cell renderer for boolean cells that shows a center-aligned green check
     * mark if true, nothing if false.
     */
    private class CheckBoxRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;
        final ImageIcon theCheck = new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/checkmark.png")); // NON-NLS

        CheckBoxRenderer() {
            setHorizontalAlignment(CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

            if ((value instanceof Boolean)) {
                if ((Boolean) value) {
                    setIcon(theCheck);
                    setToolTipText(Bundle.IsRegularExpression());
                } else {
                    setIcon(null);
                    setToolTipText(null);
                }
            }
            return this;
        }
    }
}
