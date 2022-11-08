/*
 * Autopsy
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
package org.sleuthkit.autopsy.discovery.ui;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.discovery.search.MiniTimelineResult;
import org.sleuthkit.autopsy.guiutils.SimpleTableCellRenderer;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Panel to display list of dates and counts.
 */
final class MiniTimelineDateListPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private final DateCountTableModel tableModel = new DateCountTableModel();

    /**
     * Creates new form DiscoveryTimelineListPanel.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    MiniTimelineDateListPanel() {
        initComponents();
        // add the cell renderer to all columns
        TableCellRenderer renderer = new SimpleTableCellRenderer();
        for (int i = 0; i < tableModel.getColumnCount(); ++i) {
            jTable1.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
        setMinimumSize(new Dimension(125, 20));
        jTable1.getRowSorter().toggleSortOrder(0);
        jTable1.getRowSorter().toggleSortOrder(0);
    }

    /**
     * Add a listener to the table of dates to perform actions when a date is
     * selected.
     *
     * @param listener The listener to add to the table of artifacts.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void addSelectionListener(ListSelectionListener listener) {
        jTable1.getSelectionModel().addListSelectionListener(listener);
    }

    /**
     * Assign the focus to this panel's list.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void focusList() {
        jTable1.grabFocus();
    }

    /**
     * Remove a listener from the table of dates.
     *
     * @param listener The listener to remove from the table of dates.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void removeListSelectionListener(ListSelectionListener listener) {
        jTable1.getSelectionModel().removeListSelectionListener(listener);
    }

    /**
     * Whether the list of dates is empty.
     *
     * @return True if the list of dates is empty, false if there are dates.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    boolean isEmpty() {
        return tableModel.getRowCount() <= 0;
    }

    /**
     * Select the first available date in the list if it is not empty to
     * populate the list to the right.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void selectFirst() {
        if (!isEmpty()) {
            jTable1.setRowSelectionInterval(0, 0);
        } else {
            jTable1.clearSelection();
        }
    }

    /**
     * Retrieves the list of BlackboardArtifacts for the selected date in the
     * list.
     *
     * @return The list of BlackboardArtifacts for the selected date in the
     *         list, or an empty list if a valid selection does not exist.
     */
    List<BlackboardArtifact> getArtifactsForSelectedDate() {
        int selectedIndex = jTable1.getSelectionModel().getLeadSelectionIndex();
        if (selectedIndex < jTable1.getSelectionModel().getMinSelectionIndex()
                || jTable1.getSelectionModel().getMaxSelectionIndex() < 0
                || selectedIndex > jTable1.getSelectionModel().getMaxSelectionIndex()) {
            return new ArrayList<>();
        }
        return tableModel.getDateCountByRow(jTable1.convertRowIndexToModel(selectedIndex)).getArtifactList();
    }

    /**
     * Add the specified list of dates to the list of dates which should be
     * displayed.
     *
     * @param dateCountMap The list of dates to display.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void addArtifacts(List<MiniTimelineResult> dateArtifactList) {
        tableModel.setContents(dateArtifactList);
        jTable1.validate();
        jTable1.repaint();
        tableModel.fireTableDataChanged();
    }

    /**
     * Remove all artifacts from the list of artifacts displayed.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void clearList() {
        tableModel.setContents(new ArrayList<>());
        tableModel.fireTableDataChanged();
    }

    /**
     * Initialize the UI components.
     */
    private void initComponents() {
        //This class is a refactored copy of ArtifactsListPanel so lacks the form however the init method still constructs the proper UI elements.
        javax.swing.JScrollPane jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();

        setOpaque(false);
        setPreferredSize(new java.awt.Dimension(200, 10));
        jScrollPane1.setPreferredSize(new java.awt.Dimension(200, 10));
        jScrollPane1.setBorder(null);
        jScrollPane1.setMinimumSize(new java.awt.Dimension(0, 0));

        jTable1.setAutoCreateRowSorter(true);
        jTable1.setModel(tableModel);
        jTable1.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane1.setViewportView(jTable1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 0, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Table model which allows the artifact table in this panel to mimic a list
     * of artifacts.
     */
    private class DateCountTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private final List<MiniTimelineResult> dateCountList = new ArrayList<>();

        /**
         * Set the list of artifacts which should be represented by this table
         * model.
         *
         * @param artifacts The list of BlackboardArtifacts to represent.
         */
        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        void setContents(List<MiniTimelineResult> dateCountList) {
            jTable1.clearSelection();
            this.dateCountList.clear();
            this.dateCountList.addAll(dateCountList);
        }

        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        @Override
        public int getRowCount() {
            return dateCountList.size();
        }

        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        @Override
        public int getColumnCount() {
            return 2;
        }

        /**
         * Get the BlackboardArtifact at the specified row.
         *
         * @param rowIndex The row the artifact to return is at.
         *
         * @return The BlackboardArtifact at the specified row.
         */
        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        MiniTimelineResult getDateCountByRow(int rowIndex) {
            return dateCountList.get(rowIndex);
        }

        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        @NbBundle.Messages({"MiniTimelineDateListPanel.value.noValue=No value available."})
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            switch (columnIndex) {
                case 0:
                    return dateCountList.get(rowIndex).getDate();
                case 1:
                    return dateCountList.get(rowIndex).getCount();
                default:
                    return Bundle.MiniTimelineDateListPanel_value_noValue();
            }
        }

        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        @NbBundle.Messages({
            "MiniTimelineDateListPanel.dateColumn.name=Date",
            "MiniTimelineDateListPanel.countColumn.name=Count"})
        @Override
        public String getColumnName(int column) {
            switch (column) {
                case 0:
                    return Bundle.MiniTimelineDateListPanel_dateColumn_name();
                case 1:
                    return Bundle.MiniTimelineDateListPanel_countColumn_name();
                default:
                    return "";
            }
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTable jTable1;
    // End of variables declaration//GEN-END:variables
}
