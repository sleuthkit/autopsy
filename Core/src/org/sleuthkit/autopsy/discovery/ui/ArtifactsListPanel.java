/*
 * Autopsy
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.guiutils.SimpleTableCellRenderer;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel to display list of artifacts for selected domain.
 *
 */
final class ArtifactsListPanel extends AbstractArtifactListPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ArtifactsListPanel.class.getName());
    private final DomainArtifactTableModel tableModel;

    /**
     * Creates new form ArtifactsListPanel.
     *
     * @param artifactType The type of artifact displayed in this table.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    ArtifactsListPanel(BlackboardArtifact.ARTIFACT_TYPE artifactType) {
        tableModel = new DomainArtifactTableModel(artifactType);
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

    /**
     * Assign the focus to this panel's list.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void focusList() {
        artifactsTable.grabFocus();
    }

    @Override
    void removeSelectionListener(ListSelectionListener listener) {
        artifactsTable.getSelectionModel().removeListSelectionListener(listener);
    }

    @Override
    boolean selectAtPoint(Point point) {
        boolean pointSelected = false;
        int row = artifactsTable.rowAtPoint(point);
        if (row < artifactsTable.getRowCount() && row >= 0) {
            artifactsTable.clearSelection();
            artifactsTable.addRowSelectionInterval(row, row);
            pointSelected = true;
        }
        return pointSelected;
    }

    @Override
    BlackboardArtifact getSelectedArtifact() {
        if (artifactsTable.getModel() instanceof DomainArtifactTableModel) {
            int selectedIndex = artifactsTable.getSelectionModel().getLeadSelectionIndex();
            if (selectedIndex < artifactsTable.getSelectionModel().getMinSelectionIndex() || artifactsTable.getSelectionModel().getMaxSelectionIndex() < 0 || selectedIndex > artifactsTable.getSelectionModel().getMaxSelectionIndex()) {
                return null;
            }
            return tableModel.getArtifactByRow(artifactsTable.convertRowIndexToModel(selectedIndex));
        } else {
            return null;
        }
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

    /**
     * Add the specified list of artifacts to the list of artifacts which should
     * be displayed.
     *
     * @param artifactList The list of artifacts to display.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    @Override
    void addArtifacts(List<BlackboardArtifact> artifactList) {
        if (!artifactList.isEmpty()) {
            artifactsTable.setModel(tableModel);
            tableModel.setContents(artifactList);
        } else {
            artifactsTable.setModel(new EmptyTableModel());
        }
        artifactsTable.validate();
        artifactsTable.repaint();
        tableModel.fireTableDataChanged();
    }

    /**
     * Remove all artifacts from the list of artifacts displayed.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    @Override
    void clearList() {
        addArtifacts(new ArrayList<>());
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.JScrollPane jScrollPane1 = new javax.swing.JScrollPane();
        artifactsTable = new javax.swing.JTable();

        setOpaque(false);
        setPreferredSize(new java.awt.Dimension(350, 10));

        jScrollPane1.setBorder(null);
        jScrollPane1.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        jScrollPane1.setMinimumSize(new java.awt.Dimension(0, 0));
        jScrollPane1.setPreferredSize(new java.awt.Dimension(350, 10));

        artifactsTable.setAutoCreateRowSorter(true);
        artifactsTable.setModel(tableModel);
        artifactsTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane1.setViewportView(artifactsTable);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Table model which allows the artifact table in this panel to mimic a list
     * of artifacts.
     */
    private class DomainArtifactTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private final List<BlackboardArtifact> artifactList = new ArrayList<>();
        private final BlackboardArtifact.ARTIFACT_TYPE artifactType;

        /**
         * Construct a new DomainArtifactTableModel.
         *
         * @param artifactType The type of artifact displayed in this table.
         */
        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        DomainArtifactTableModel(BlackboardArtifact.ARTIFACT_TYPE artifactType) {
            this.artifactType = artifactType;
        }

        /**
         * Set the list of artifacts which should be represented by this table
         * model.
         *
         * @param artifacts The list of BlackboardArtifacts to represent.
         */
        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        void setContents(List<BlackboardArtifact> artifacts) {
            artifactsTable.clearSelection();
            artifactList.clear();
            artifactList.addAll(artifacts);
        }

        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        @Override
        public int getRowCount() {
            return artifactList.size();
        }

        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        @Override
        public int getColumnCount() {
            if (artifactType == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_CACHE) {
                return 3;
            } else {
                return 2;
            }
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
        @NbBundle.Messages({"ArtifactsListPanel.value.noValue=No value available."})
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex < 2 || artifactType == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_CACHE) {
                final BlackboardArtifact artifact = getArtifactByRow(rowIndex);
                try {
                    for (BlackboardAttribute bba : artifact.getAttributes()) {
                        if (!StringUtils.isBlank(bba.getDisplayString())) {
                            String stringFromAttribute = getStringForColumn(artifact, bba, columnIndex);
                            if (!StringUtils.isBlank(stringFromAttribute)) {
                                return stringFromAttribute;
                            }
                        }
                    }
                    return getFallbackValue(rowIndex, columnIndex);
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Error getting attributes for artifact " + artifact.getArtifactID(), ex);
                }
            }
            return Bundle.ArtifactsListPanel_value_noValue();
        }

        /**
         * Get the appropriate String for the specified column from the
         * BlackboardAttribute.
         *
         * @param artifact    The artifact.
         * @param bba         The BlackboardAttribute which may contain a value.
         * @param columnIndex The column the value will be displayed in.
         *
         * @return The value from the specified attribute which should be
         *         displayed in the specified column, null if the specified
         *         attribute does not contain a value for that column.
         *
         * @throws TskCoreException When unable to get abstract files based on
         *                          the TSK_PATH_ID.
         */
        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        private String getStringForColumn(BlackboardArtifact artifact, BlackboardAttribute bba, int columnIndex) throws TskCoreException {
            if (columnIndex == 0 && bba.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID()) {
                return TimeZoneUtils.getFormattedTime(bba.getValueLong());
            } else if (columnIndex == 1) {
                if (artifactType == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD || artifactType == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_CACHE) {
                    if (bba.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID()) {
                        return Case.getCurrentCase().getSleuthkitCase().getAbstractFileById(bba.getValueLong()).getName();
                    } else if (bba.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH.getTypeID()) {
                        return FilenameUtils.getName(bba.getDisplayString());
                    }
                } else if (bba.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE.getTypeID()) {
                    return bba.getDisplayString();
                }
            } else if (columnIndex == 2 && bba.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID()) {
                return Case.getCurrentCase().getSleuthkitCase().getAbstractFileById(bba.getValueLong()).getMIMEType();
            }
            return null;
        }

        /**
         * Private helper method to use when the value we want for either date
         * or title is not available.
         *
         *
         * @param rowIndex    The row the artifact to return is at.
         * @param columnIndex The column index the attribute will be displayed
         *                    at.
         *
         * @return A string that can be used in place of the accessed date time
         *         attribute title when they are not available.
         *
         * @throws TskCoreException
         */
        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        private String getFallbackValue(int rowIndex, int columnIndex) throws TskCoreException {
            final BlackboardArtifact artifact = getArtifactByRow(rowIndex);
            for (BlackboardAttribute bba : artifact.getAttributes()) {
                if (columnIndex == 0 && bba.getAttributeType().getTypeName().startsWith("TSK_DATETIME") && !StringUtils.isBlank(bba.getDisplayString())) {
                    return TimeZoneUtils.getFormattedTime(bba.getValueLong());
                } else if (columnIndex == 1 && bba.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID() && !StringUtils.isBlank(bba.getDisplayString())) {
                    return bba.getDisplayString();
                } else if (columnIndex == 1 && bba.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID() && !StringUtils.isBlank(bba.getDisplayString())) {
                    return bba.getDisplayString();
                } else if (columnIndex == 1 && bba.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT.getTypeID() && !StringUtils.isBlank(bba.getDisplayString())) {
                    return bba.getDisplayString();
                }
            }
            return Bundle.ArtifactsListPanel_value_noValue();
        }

        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        @NbBundle.Messages({"ArtifactsListPanel.titleColumn.name=Title",
            "ArtifactsListPanel.fileNameColumn.name=Name",
            "ArtifactsListPanel.dateColumn.name=Date/Time",
            "ArtifactsListPanel.urlColumn.name=URL",
            "ArtifactsListPanel.termColumn.name=Term",
            "ArtifactsListPanel.mimeTypeColumn.name=MIME Type"})
        @Override
        public String getColumnName(int column) {
            switch (column) {
                case 0:
                    return Bundle.ArtifactsListPanel_dateColumn_name();
                case 1:
                    if (artifactType != null) {
                        switch (artifactType) {
                            case TSK_WEB_CACHE:
                            case TSK_WEB_DOWNLOAD:
                                return Bundle.ArtifactsListPanel_fileNameColumn_name();
                            case TSK_WEB_COOKIE:
                                return Bundle.ArtifactsListPanel_urlColumn_name();
                            case TSK_WEB_SEARCH_QUERY:
                                return Bundle.ArtifactsListPanel_termColumn_name();
                            default:
                        }
                    }
                    return Bundle.ArtifactsListPanel_titleColumn_name();
                case 2:
                    return Bundle.ArtifactsListPanel_mimeTypeColumn_name();
                default:
                    return "";
            }
        }
    }

    /**
     * Table model which displays only that no results were found.
     */
    private class EmptyTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        @Override
        public int getRowCount() {
            return 1;
        }

        @Override
        public int getColumnCount() {
            return 1;
        }

        @NbBundle.Messages({"ArtifactsListPanel.noResultsFound.text=No results found"})
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return Bundle.ArtifactsListPanel_noResultsFound_text();
        }

        @Override
        public String getColumnName(int column) {
            switch (column) {
                case 0:
                    return Bundle.ArtifactsListPanel_dateColumn_name();
                case 1:
                    return Bundle.ArtifactsListPanel_titleColumn_name();
                case 2:
                    return Bundle.ArtifactsListPanel_mimeTypeColumn_name();
                default:
                    return "";
            }
        }

    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTable artifactsTable;
    // End of variables declaration//GEN-END:variables
}
