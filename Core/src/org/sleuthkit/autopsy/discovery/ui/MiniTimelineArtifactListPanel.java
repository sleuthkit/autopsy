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
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JPopupMenu;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.guiutils.SimpleTableCellRenderer;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.Type;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel to display list of artifacts types and descriptions.
 */
final class MiniTimelineArtifactListPanel extends AbstractArtifactListPanel {

    private static final long serialVersionUID = 1L;
    private final TypeDescriptionTableModel tableModel;
    private static final Logger logger = Logger.getLogger(MiniTimelineArtifactListPanel.class.getName());

    private static final BlackboardAttribute.ATTRIBUTE_TYPE[] DESCRIPTION_TYPES = {BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME,
        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL};

    /**
     * Creates new form DiscoveryTimelineListPanel.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    MiniTimelineArtifactListPanel() {
        tableModel = new TypeDescriptionTableModel();
        initComponents();
        // add the cell renderer to all columns
        TableCellRenderer renderer = new SimpleTableCellRenderer();
        for (int i = 0; i < tableModel.getColumnCount(); ++i) {
            artifactsTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
        setMinimumSize(new Dimension(125, 20));
        artifactsTable.getRowSorter().toggleSortOrder(0);
        artifactsTable.getRowSorter().toggleSortOrder(0);
    }

    @Override
    void addMouseListener(java.awt.event.MouseAdapter mouseListener) {
        artifactsTable.addMouseListener(mouseListener);
    }

    @Override
    void showPopupMenu(JPopupMenu popupMenu, Point point) {
        popupMenu.show(artifactsTable, point.x, point.y);
    }

    @Override
    void addSelectionListener(ListSelectionListener listener) {
        artifactsTable.getSelectionModel().addListSelectionListener(listener);
    }

    @Override
    void removeSelectionListener(ListSelectionListener listener) {
        artifactsTable.getSelectionModel().removeListSelectionListener(listener);
    }

    @Override
    boolean isEmpty() {
        return tableModel.getRowCount() <= 0;
    }

    @Override
    void selectFirst() {
        if (!isEmpty()) {
            artifactsTable.setRowSelectionInterval(0, 0);
        } else {
            artifactsTable.clearSelection();
        }
    }

    @Override
    boolean selectAtPoint(Point point) {
        boolean pointSelected = false;
        int row = artifactsTable.rowAtPoint(point);
        artifactsTable.clearSelection();
        if (row < artifactsTable.getRowCount() && row >= 0) {
            artifactsTable.addRowSelectionInterval(row, row);
            pointSelected = true;
        }
        return pointSelected;
    }

    @Override
    BlackboardArtifact getSelectedArtifact() {
        int selectedIndex = artifactsTable.getSelectionModel().getLeadSelectionIndex();
        if (selectedIndex < artifactsTable.getSelectionModel().getMinSelectionIndex()
                || artifactsTable.getSelectionModel().getMaxSelectionIndex() < 0
                || selectedIndex > artifactsTable.getSelectionModel().getMaxSelectionIndex()) {
            return null;
        }
        return tableModel.getArtifactByRow(artifactsTable.convertRowIndexToModel(selectedIndex));
    }

    @Override
    void addArtifacts(List<BlackboardArtifact> artifactList) {
        tableModel.setContents(artifactList);
        artifactsTable.validate();
        artifactsTable.repaint();
        tableModel.fireTableDataChanged();
    }

    @Override
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
        artifactsTable = new javax.swing.JTable();

        setOpaque(false);
        setPreferredSize(new java.awt.Dimension(200, 10));
        jScrollPane1.setPreferredSize(new java.awt.Dimension(200, 10));
        jScrollPane1.setBorder(null);
        jScrollPane1.setMinimumSize(new java.awt.Dimension(0, 0));

        artifactsTable.setAutoCreateRowSorter(true);
        artifactsTable.setModel(tableModel);
        artifactsTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane1.setViewportView(artifactsTable);

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
    private class TypeDescriptionTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private final List<BlackboardArtifact> artifactList = new ArrayList<>();

        /**
         * Set the list of artifacts which should be represented by this table
         * model.
         *
         * @param artifacts The list of BlackboardArtifacts to represent.
         */
        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        void setContents(List<BlackboardArtifact> artifactList) {
            artifactsTable.clearSelection();
            this.artifactList.clear();
            this.artifactList.addAll(artifactList);
        }

        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        @Override
        public int getRowCount() {
            return artifactList.size();
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
        BlackboardArtifact getArtifactByRow(int rowIndex) {
            return artifactList.get(rowIndex);
        }

        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        @NbBundle.Messages({"MiniTimelineArtifactListPanel.value.noValue=No value available."})
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            switch (columnIndex) {
                case 0:
                    return artifactList.get(rowIndex).getDisplayName();
                case 1:
                    return getDescription(artifactList.get(rowIndex));
                default:
                    return Bundle.MiniTimelineArtifactListPanel_value_noValue();
            }
        }

        private String getDescription(BlackboardArtifact artifact) {
            try {
                for (BlackboardAttribute.ATTRIBUTE_TYPE attributeType : DESCRIPTION_TYPES) {
                    BlackboardAttribute attribute = artifact.getAttribute(new Type(attributeType));
                    if (attribute != null && !StringUtils.isBlank(attribute.getDisplayString())) {
                        return attribute.getDisplayString();
                    }
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Unable to get description attribute for artifact id {0}", artifact.getArtifactID());
            }
            return Bundle.MiniTimelineArtifactListPanel_value_noValue();
        }

        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        @NbBundle.Messages({
            "MiniTimelineArtifactListPanel.typeColumn.name=Result Type",
            "MiniTimelineArtifactListPanel.descriptionColumn.name= Description"})
        @Override
        public String getColumnName(int column) {
            switch (column) {
                case 0:
                    return Bundle.MiniTimelineArtifactListPanel_typeColumn_name();
                case 1:
                    return Bundle.MiniTimelineArtifactListPanel_descriptionColumn_name();
                default:
                    return "";
            }
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTable artifactsTable;
    // End of variables declaration//GEN-END:variables
}
