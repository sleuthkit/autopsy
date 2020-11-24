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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.Type;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel to display list of dates and counts.
 */
class MiniTimelineArtifactListPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private final TypeDescriptionTableModel tableModel;
    private static final Logger logger = Logger.getLogger(MiniTimelineArtifactListPanel.class.getName());
    private static final BlackboardAttribute.ATTRIBUTE_TYPE[] DESCRIPTION_TYPES = {BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL};

    /**
     * Creates new form DiscoveryTimelineListPanel.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    MiniTimelineArtifactListPanel() {
        tableModel = new TypeDescriptionTableModel();
        initComponents();
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
     * The artifact which is currently selected, null if no artifact is
     * selected.
     *
     * @return The currently selected BlackboardArtifact or null if none is
     *         selected.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    BlackboardArtifact getSelectedArtifact() {
        int selectedIndex = jTable1.getSelectionModel().getLeadSelectionIndex();
        if (selectedIndex < jTable1.getSelectionModel().getMinSelectionIndex() || jTable1.getSelectionModel().getMaxSelectionIndex() < 0 || selectedIndex > jTable1.getSelectionModel().getMaxSelectionIndex()) {
            return null;
        }
        return tableModel.getArtifactByRow(jTable1.convertRowIndexToModel(selectedIndex));
    }

    /**
     * Add the specified list of dates to the list of dates which should be
     * displayed.
     *
     * @param dateCountList The list of dates to display.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void addArtifacts(List<BlackboardArtifact> dateCountList) {
        tableModel.setContents(dateCountList);
        jTable1.validate();
        jTable1.repaint();
        tableModel.fireTableDataChanged();
    }

    /**
     * Remove all artifacts from the list of artifacts displayed.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void clearDates() {
        tableModel.setContents(new ArrayList<>());
        tableModel.fireTableDataChanged();
    }

    private void initComponents() {

        javax.swing.JScrollPane jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();

        setOpaque(false);
        setPreferredSize(new java.awt.Dimension(300, 0));

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
            jTable1.clearSelection();
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
                logger.log(Level.WARNING, "Unable to get description attribute for artifact id " + artifact.getArtifactID());
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
    private javax.swing.JTable jTable1;
    // End of variables declaration//GEN-END:variables
}
