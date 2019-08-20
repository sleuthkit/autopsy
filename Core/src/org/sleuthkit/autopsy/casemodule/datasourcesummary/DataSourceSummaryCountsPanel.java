/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.datasourcesummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.swing.JLabel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.FileTypeUtils;
import org.sleuthkit.datamodel.DataSource;

/**
 * Panel for displaying summary information on the known files present in the
 * specified DataSource
 */
class DataSourceSummaryCountsPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private FilesByMimeTypeTableModel filesByMimeTypeTableModel = new FilesByMimeTypeTableModel(null);
    private FilesByCategoryTableModel filesByCategoryTableModel = new FilesByCategoryTableModel(null);
    private static final Logger logger = Logger.getLogger(DataSourceSummaryCountsPanel.class.getName());
    private final Map<Long, Long> allFilesCountsMap;
    private final Map<Long, Long> slackFilesCountsMap;
    private final Map<Long, Long> directoriesCountsMap;
    private final Map<Long, Long> unallocatedFilesCountsMap;
    private final Map<Long, Map<String, Long>> artifactsByTypeCountsMap;
    private final DefaultTableCellRenderer rightAlignedRenderer = new DefaultTableCellRenderer();

    /**
     * Creates new form DataSourceSummaryCountsPanel
     */
    DataSourceSummaryCountsPanel(Map<Long, Long> fileCountsMap) {
        this.allFilesCountsMap = fileCountsMap;
        this.slackFilesCountsMap = DataSourceInfoUtilities.getCountsOfSlackFiles();
        this.directoriesCountsMap = DataSourceInfoUtilities.getCountsOfDirectories();
        this.unallocatedFilesCountsMap = DataSourceInfoUtilities.getCountsOfUnallocatedFiles();
        this.artifactsByTypeCountsMap = DataSourceInfoUtilities.getCountsOfArtifactsByType();
        rightAlignedRenderer.setHorizontalAlignment(JLabel.RIGHT);
        initComponents();
        fileCountsByMimeTypeTable.getTableHeader().setReorderingAllowed(false);
        fileCountsByCategoryTable.getTableHeader().setReorderingAllowed(false);
    }

    /**
     * Specify the DataSource to display file information for
     *
     * @param selectedDataSource the DataSource to display file information for
     */
    void updateCountsTableData(DataSource selectedDataSource) {
        filesByMimeTypeTableModel = new FilesByMimeTypeTableModel(selectedDataSource);
        fileCountsByMimeTypeTable.setModel(filesByMimeTypeTableModel);
        fileCountsByMimeTypeTable.getColumnModel().getColumn(1).setCellRenderer(rightAlignedRenderer);
        fileCountsByMimeTypeTable.getColumnModel().getColumn(0).setPreferredWidth(130);
        filesByCategoryTableModel = new FilesByCategoryTableModel(selectedDataSource);
        fileCountsByCategoryTable.setModel(filesByCategoryTableModel);
        fileCountsByCategoryTable.getColumnModel().getColumn(1).setCellRenderer(rightAlignedRenderer);
        fileCountsByCategoryTable.getColumnModel().getColumn(0).setPreferredWidth(130);
        updateArtifactCounts(selectedDataSource);
        this.repaint();
    }

    /**
     * Helper method to update the artifact specific counts by clearing the
     * table and adding counts for the artifacts which exist in the selected
     * data source.
     *
     * @param selectedDataSource the data source to display artifact counts for
     */
    private void updateArtifactCounts(DataSource selectedDataSource) {
        ((DefaultTableModel) artifactCountsTable.getModel()).setRowCount(0);
        if (selectedDataSource != null && artifactsByTypeCountsMap.get(selectedDataSource.getId()) != null) {
            Map<String, Long> artifactCounts = artifactsByTypeCountsMap.get(selectedDataSource.getId());
            for (String key : artifactCounts.keySet()) {
                ((DefaultTableModel) artifactCountsTable.getModel()).addRow(new Object[]{key, artifactCounts.get(key)});
            }
        }
        artifactCountsTable.getColumnModel().getColumn(0).setPreferredWidth(230);
        artifactCountsTable.getColumnModel().getColumn(1).setCellRenderer(rightAlignedRenderer);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        fileCountsByMimeTypeScrollPane = new javax.swing.JScrollPane();
        fileCountsByMimeTypeTable = new javax.swing.JTable();
        byMimeTypeLabel = new javax.swing.JLabel();
        fileCountsByCategoryScrollPane = new javax.swing.JScrollPane();
        fileCountsByCategoryTable = new javax.swing.JTable();
        byCategoryLabel = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        artifactCountsScrollPane = new javax.swing.JScrollPane();
        artifactCountsTable = new javax.swing.JTable();

        fileCountsByMimeTypeTable.setModel(filesByMimeTypeTableModel);
        fileCountsByMimeTypeScrollPane.setViewportView(fileCountsByMimeTypeTable);

        org.openide.awt.Mnemonics.setLocalizedText(byMimeTypeLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryCountsPanel.class, "DataSourceSummaryCountsPanel.byMimeTypeLabel.text")); // NOI18N

        fileCountsByCategoryTable.setModel(filesByCategoryTableModel);
        fileCountsByCategoryScrollPane.setViewportView(fileCountsByCategoryTable);

        org.openide.awt.Mnemonics.setLocalizedText(byCategoryLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryCountsPanel.class, "DataSourceSummaryCountsPanel.byCategoryLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(DataSourceSummaryCountsPanel.class, "DataSourceSummaryCountsPanel.jLabel1.text")); // NOI18N

        artifactCountsTable.setAutoCreateRowSorter(true);
        artifactCountsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Result Type", "Count"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        artifactCountsScrollPane.setViewportView(artifactCountsTable);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(fileCountsByMimeTypeScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 140, Short.MAX_VALUE)
                    .addComponent(byMimeTypeLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(byCategoryLabel)
                    .addComponent(fileCountsByCategoryScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 82, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(artifactCountsScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 244, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {fileCountsByCategoryScrollPane, fileCountsByMimeTypeScrollPane});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(byMimeTypeLabel)
                    .addComponent(byCategoryLabel)
                    .addComponent(jLabel1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(artifactCountsScrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(fileCountsByMimeTypeScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 107, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(fileCountsByCategoryScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 86, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {fileCountsByCategoryScrollPane, fileCountsByMimeTypeScrollPane});

    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane artifactCountsScrollPane;
    private javax.swing.JTable artifactCountsTable;
    private javax.swing.JLabel byCategoryLabel;
    private javax.swing.JLabel byMimeTypeLabel;
    private javax.swing.JScrollPane fileCountsByCategoryScrollPane;
    private javax.swing.JTable fileCountsByCategoryTable;
    private javax.swing.JScrollPane fileCountsByMimeTypeScrollPane;
    private javax.swing.JTable fileCountsByMimeTypeTable;
    private javax.swing.JLabel jLabel1;
    // End of variables declaration//GEN-END:variables

    /**
     * Table model for the files table model to display counts of specific file
     * types by mime type found in the currently selected data source.
     */
    @Messages({"DataSourceSummaryCountsPanel.FilesByMimeTypeTableModel.type.header=File Type",
        "DataSourceSummaryCountsPanel.FilesByMimeTypeTableModel.count.header=Count"})
    private class FilesByMimeTypeTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private final DataSource currentDataSource;
        private final List<String> columnHeaders = new ArrayList<>();
        private static final int IMAGES_ROW_INDEX = 0;
        private static final int VIDEOS_ROW_INDEX = 1;
        private static final int AUDIO_ROW_INDEX = 2;
        private static final int DOCUMENTS_ROW_INDEX = 3;
        private static final int EXECUTABLES_ROW_INDEX = 4;

        /**
         * Create a FilesByMimeTypeTableModel for the speicified datasource.
         *
         * @param selectedDataSource the datasource which this
         *                           FilesByMimeTypeTablemodel will represent
         */
        FilesByMimeTypeTableModel(DataSource selectedDataSource) {
            columnHeaders.add(Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_type_header());
            columnHeaders.add(Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_count_header());
            currentDataSource = selectedDataSource;
        }

        @Override
        public int getRowCount() {
            //should be kept equal to the number of types we are displaying in the tables
            return 5;
        }

        @Override
        public int getColumnCount() {
            return columnHeaders.size();
        }

        @Messages({
            "DataSourceSummaryCountsPanel.FilesByMimeTypeTableModel.images.row=Images",
            "DataSourceSummaryCountsPanel.FilesByMimeTypeTableModel.videos.row=Videos",
            "DataSourceSummaryCountsPanel.FilesByMimeTypeTableModel.audio.row=Audio",
            "DataSourceSummaryCountsPanel.FilesByMimeTypeTableModel.documents.row=Documents",
            "DataSourceSummaryCountsPanel.FilesByMimeTypeTableModel.executables.row=Executables"
        })
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                switch (rowIndex) {
                    case IMAGES_ROW_INDEX:
                        return Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_images_row();
                    case VIDEOS_ROW_INDEX:
                        return Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_videos_row();
                    case AUDIO_ROW_INDEX:
                        return Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_audio_row();
                    case DOCUMENTS_ROW_INDEX:
                        return Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_documents_row();
                    case EXECUTABLES_ROW_INDEX:
                        return Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_executables_row();
                    default:
                        break;
                }
            } else if (columnIndex == 1) {
                switch (rowIndex) {
                    case 0:
                        return DataSourceInfoUtilities.getCountOfFilesForMimeTypes(currentDataSource, FileTypeUtils.FileTypeCategory.IMAGE.getMediaTypes());
                    case 1:
                        return DataSourceInfoUtilities.getCountOfFilesForMimeTypes(currentDataSource, FileTypeUtils.FileTypeCategory.VIDEO.getMediaTypes());
                    case 2:
                        return DataSourceInfoUtilities.getCountOfFilesForMimeTypes(currentDataSource, FileTypeUtils.FileTypeCategory.AUDIO.getMediaTypes());
                    case 3:
                        return DataSourceInfoUtilities.getCountOfFilesForMimeTypes(currentDataSource, FileTypeUtils.FileTypeCategory.DOCUMENTS.getMediaTypes());
                    case 4:
                        return DataSourceInfoUtilities.getCountOfFilesForMimeTypes(currentDataSource, FileTypeUtils.FileTypeCategory.EXECUTABLE.getMediaTypes());
                    default:
                        break;
                }
            }
            return null;
        }

        @Override
        public String getColumnName(int column) {
            return columnHeaders.get(column);
        }
    }

    /**
     * Table model for the files table model to display counts of specific file
     * types by category found in the currently selected data source.
     */
    @Messages({"DataSourceSummaryCountsPanel.FilesByCategoryTableModel.type.header=File Type",
        "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.count.header=Count"})
    private class FilesByCategoryTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private final DataSource currentDataSource;
        private final List<String> columnHeaders = new ArrayList<>();
        private static final int ALL_FILES_ROW_INDEX = 0;
        private static final int ALLOCATED_FILES_ROW_INDEX = 1;
        private static final int UNALLOCATED_FILES_ROW_INDEX = 2;
        private static final int SLACK_FILES_ROW_INDEX = 3;
        private static final int DIRECTORIES_ROW_INDEX = 4;
        /**
         * Create a FilesByCategoryTableModel for the speicified datasource.
         *
         * @param selectedDataSource the datasource which this
         *                           FilesByCategoryTablemodel will represent
         */
        FilesByCategoryTableModel(DataSource selectedDataSource) {
            columnHeaders.add(Bundle.DataSourceSummaryCountsPanel_FilesByCategoryTableModel_type_header());
            columnHeaders.add(Bundle.DataSourceSummaryCountsPanel_FilesByCategoryTableModel_count_header());
            currentDataSource = selectedDataSource;
        }

        @Override
        public int getRowCount() {
            //should be kept equal to the number of types we are displaying in the tables
            return 5;
        }

        @Override
        public int getColumnCount() {
            return columnHeaders.size();
        }

        @Messages({
            "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.all.row=All",
            "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.allocated.row=Allocated",
            "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.unallocated.row=Unallocated",
            "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.slack.row=Slack",
            "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.directory.row=Directory"
        })
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                switch (rowIndex) {
                    case ALL_FILES_ROW_INDEX:
                        return Bundle.DataSourceSummaryCountsPanel_FilesByCategoryTableModel_all_row();
                    case ALLOCATED_FILES_ROW_INDEX:
                        return Bundle.DataSourceSummaryCountsPanel_FilesByCategoryTableModel_allocated_row();
                    case UNALLOCATED_FILES_ROW_INDEX:
                        return Bundle.DataSourceSummaryCountsPanel_FilesByCategoryTableModel_unallocated_row();
                    case SLACK_FILES_ROW_INDEX:
                        return Bundle.DataSourceSummaryCountsPanel_FilesByCategoryTableModel_slack_row();
                    case DIRECTORIES_ROW_INDEX:
                        return Bundle.DataSourceSummaryCountsPanel_FilesByCategoryTableModel_directory_row();
                    default:
                        break;
                }
            } else if (columnIndex == 1 && currentDataSource != null) {
                switch (rowIndex) {
                    case 0:
                        return allFilesCountsMap.get(currentDataSource.getId()) == null ? 0 : allFilesCountsMap.get(currentDataSource.getId());
                    case 1:
                        //All files should be either allocated or unallocated as dir_flags only has two values so any file that isn't unallocated is allocated
                        Long unallocatedFilesCount = unallocatedFilesCountsMap.get(currentDataSource.getId());
                        Long allFilesCount = allFilesCountsMap.get(currentDataSource.getId());
                        if (allFilesCount == null) {
                            return 0;
                        } else if (unallocatedFilesCount == null) {
                            return allFilesCount;
                        } else {
                            return allFilesCount - unallocatedFilesCount;
                        }
                    case 2:
                        return unallocatedFilesCountsMap.get(currentDataSource.getId()) == null ? 0 : unallocatedFilesCountsMap.get(currentDataSource.getId());
                    case 3:
                        return slackFilesCountsMap.get(currentDataSource.getId()) == null ? 0 : slackFilesCountsMap.get(currentDataSource.getId());
                    case 4:
                        return directoriesCountsMap.get(currentDataSource.getId()) == null ? 0 : directoriesCountsMap.get(currentDataSource.getId());
                    default:
                        break;
                }
            }
            return null;
        }

        @Override
        public String getColumnName(int column) {
            return columnHeaders.get(column);
        }
    }
}
