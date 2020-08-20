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

import java.util.Map;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JLabel;
import javax.swing.table.DefaultTableCellRenderer;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.FileTypeUtils;
import org.sleuthkit.datamodel.DataSource;

/**
 * Panel for displaying summary information on the known files present in the
 * specified DataSource
 */
@Messages({"DataSourceSummaryCountsPanel.FilesByMimeTypeTableModel.type.header=File Type",
    "DataSourceSummaryCountsPanel.FilesByMimeTypeTableModel.count.header=Count",
    "DataSourceSummaryCountsPanel.ArtifactCountsTableModel.type.header=Result Type",
    "DataSourceSummaryCountsPanel.ArtifactCountsTableModel.count.header=Count",
    "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.type.header=File Type",
    "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.count.header=Count"
})
class DataSourceSummaryCountsPanel extends javax.swing.JPanel {

    // Result returned for a data model if no data found.
    private static final Object[][] EMPTY_PAIRS = new Object[][]{};

    // column headers for mime type table
    private static final Object[] MIME_TYPE_COLUMN_HEADERS = new Object[]{
        Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_type_header(),
        Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_count_header()
    };

    // column headers for file by category table
    private static final Object[] FILE_BY_CATEGORY_COLUMN_HEADERS = new Object[]{
        Bundle.DataSourceSummaryCountsPanel_FilesByCategoryTableModel_type_header(),
        Bundle.DataSourceSummaryCountsPanel_FilesByCategoryTableModel_count_header()
    };

    // column headers for artifact counts table
    private static final Object[] ARTIFACT_COUNTS_COLUMN_HEADERS = new Object[]{
        Bundle.DataSourceSummaryCountsPanel_ArtifactCountsTableModel_type_header(),
        Bundle.DataSourceSummaryCountsPanel_ArtifactCountsTableModel_count_header()
    };

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DataSourceSummaryCountsPanel.class.getName());
    private final DefaultTableCellRenderer rightAlignedRenderer = new DefaultTableCellRenderer();

    private DataSource dataSource;

    /**
     * Creates new form DataSourceSummaryCountsPanel
     */
    DataSourceSummaryCountsPanel() {
        rightAlignedRenderer.setHorizontalAlignment(JLabel.RIGHT);
        initComponents();
        fileCountsByMimeTypeTable.getTableHeader().setReorderingAllowed(false);
        fileCountsByCategoryTable.getTableHeader().setReorderingAllowed(false);
        artifactCountsTable.getTableHeader().setReorderingAllowed(false);
        setDataSource(null);
    }

    /**
     * The datasource currently used as the model in this panel.
     *
     * @return The datasource currently being used as the model in this panel.
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Sets datasource to visualize in the panel.
     *
     * @param dataSource The datasource to use in this panel.
     */
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
        if (dataSource == null || !Case.isCaseOpen()) {
            updateCountsTableData(EMPTY_PAIRS,
                    EMPTY_PAIRS,
                    EMPTY_PAIRS);
        } else {
            updateCountsTableData(getMimeTypeModel(dataSource),
                    getFileCategoryModel(dataSource),
                    getArtifactCountsModel(dataSource));
        }

    }

    /**
     * Specify the DataSource to display file information for.
     *
     * @param mimeTypeDataModel     The mime type data model.
     * @param fileCategoryDataModel The file category data model.
     * @param artifactDataModel     The artifact type data model.
     */
    private void updateCountsTableData(Object[][] mimeTypeDataModel, Object[][] fileCategoryDataModel, Object[][] artifactDataModel) {
        fileCountsByMimeTypeTable.setModel(new NonEditableTableModel(mimeTypeDataModel, MIME_TYPE_COLUMN_HEADERS));
        fileCountsByMimeTypeTable.getColumnModel().getColumn(1).setCellRenderer(rightAlignedRenderer);
        fileCountsByMimeTypeTable.getColumnModel().getColumn(0).setPreferredWidth(130);

        fileCountsByCategoryTable.setModel(new NonEditableTableModel(fileCategoryDataModel, FILE_BY_CATEGORY_COLUMN_HEADERS));
        fileCountsByCategoryTable.getColumnModel().getColumn(1).setCellRenderer(rightAlignedRenderer);
        fileCountsByCategoryTable.getColumnModel().getColumn(0).setPreferredWidth(130);

        artifactCountsTable.setModel(new NonEditableTableModel(artifactDataModel, ARTIFACT_COUNTS_COLUMN_HEADERS));
        artifactCountsTable.getColumnModel().getColumn(0).setPreferredWidth(230);
        artifactCountsTable.getColumnModel().getColumn(1).setCellRenderer(rightAlignedRenderer);

        this.repaint();
    }

    /**
     * Determines the JTable data model for datasource mime types.
     *
     * @param dataSource The DataSource.
     *
     * @return The model to be used with a JTable.
     */
    @Messages({
        "DataSourceSummaryCountsPanel.FilesByMimeTypeTableModel.images.row=Images",
        "DataSourceSummaryCountsPanel.FilesByMimeTypeTableModel.videos.row=Videos",
        "DataSourceSummaryCountsPanel.FilesByMimeTypeTableModel.audio.row=Audio",
        "DataSourceSummaryCountsPanel.FilesByMimeTypeTableModel.documents.row=Documents",
        "DataSourceSummaryCountsPanel.FilesByMimeTypeTableModel.executables.row=Executables"
    })
    private static Object[][] getMimeTypeModel(DataSource dataSource) {
        return new Object[][]{
            new Object[]{Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_images_row(),
                getCount(dataSource, FileTypeUtils.FileTypeCategory.IMAGE)},
            new Object[]{Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_videos_row(),
                getCount(dataSource, FileTypeUtils.FileTypeCategory.VIDEO)},
            new Object[]{Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_audio_row(),
                getCount(dataSource, FileTypeUtils.FileTypeCategory.AUDIO)},
            new Object[]{Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_documents_row(),
                getCount(dataSource, FileTypeUtils.FileTypeCategory.DOCUMENTS)},
            new Object[]{Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_executables_row(),
                getCount(dataSource, FileTypeUtils.FileTypeCategory.EXECUTABLE)}
        };
    }

    /**
     * Retrieves the counts of files of a particular mime type for a particular
     * DataSource.
     *
     * @param dataSource The DataSource.
     * @param category   The mime type category.
     *
     * @return The count.
     */
    private static Long getCount(DataSource dataSource, FileTypeUtils.FileTypeCategory category) {
        return DataSourceInfoUtilities.getCountOfFilesForMimeTypes(dataSource, category.getMediaTypes());
    }

    /**
     * Determines the JTable data model for datasource file categories.
     *
     * @param dataSource The DataSource.
     *
     * @return The model to be used with a JTable.
     */
    @Messages({
        "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.all.row=All",
        "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.allocated.row=Allocated",
        "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.unallocated.row=Unallocated",
        "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.slack.row=Slack",
        "DataSourceSummaryCountsPanel.FilesByCategoryTableModel.directory.row=Directory"
    })
    private static Object[][] getFileCategoryModel(DataSource selectedDataSource) {
        Long fileCount = zeroIfNull(DataSourceInfoUtilities.getCountOfFiles(selectedDataSource));
        Long unallocatedFiles = zeroIfNull(DataSourceInfoUtilities.getCountOfUnallocatedFiles(selectedDataSource));
        Long allocatedFiles = zeroIfNull(getAllocatedCount(fileCount, unallocatedFiles));
        Long slackFiles = zeroIfNull(DataSourceInfoUtilities.getCountOfSlackFiles(selectedDataSource));
        Long directories = zeroIfNull(DataSourceInfoUtilities.getCountOfDirectories(selectedDataSource));

        return new Object[][]{
            new Object[]{Bundle.DataSourceSummaryCountsPanel_FilesByCategoryTableModel_all_row(), fileCount},
            new Object[]{Bundle.DataSourceSummaryCountsPanel_FilesByCategoryTableModel_allocated_row(), allocatedFiles},
            new Object[]{Bundle.DataSourceSummaryCountsPanel_FilesByCategoryTableModel_unallocated_row(), unallocatedFiles},
            new Object[]{Bundle.DataSourceSummaryCountsPanel_FilesByCategoryTableModel_slack_row(), slackFiles},
            new Object[]{Bundle.DataSourceSummaryCountsPanel_FilesByCategoryTableModel_directory_row(), directories}
        };
    }

    /**
     * Returns 0 if value is null.
     *
     * @param origValue The original value.
     *
     * @return The value or 0 if null.
     */
    private static Long zeroIfNull(Long origValue) {
        return origValue == null ? 0 : origValue;
    }

    /**
     * Safely gets the allocated files count.
     *
     * @param allFilesCount         The count of all files.
     * @param unallocatedFilesCount The count of unallocated files.
     *
     * @return The count of allocated files.
     */
    private static long getAllocatedCount(Long allFilesCount, Long unallocatedFilesCount) {
        if (allFilesCount == null) {
            return 0;
        } else if (unallocatedFilesCount == null) {
            return allFilesCount;
        } else {
            return allFilesCount - unallocatedFilesCount;
        }
    }

    /**
     * The counts of different artifact types found in a DataSource.
     *
     * @param selectedDataSource The DataSource.
     *
     * @return The JTable data model of counts of artifact types.
     */
    private static Object[][] getArtifactCountsModel(DataSource selectedDataSource) {
        Map<String, Long> artifactMapping = DataSourceInfoUtilities.getCountsOfArtifactsByType(selectedDataSource);
        if (artifactMapping == null) {
            return EMPTY_PAIRS;
        }

        return artifactMapping.entrySet().stream()
                .filter((entrySet) -> entrySet != null && entrySet.getKey() != null)
                .sorted((a, b) -> a.getKey().compareTo(b.getKey()))
                .map((entrySet) -> new Object[]{entrySet.getKey(), entrySet.getValue()})
                .toArray(Object[][]::new);
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

        fileCountsByMimeTypeScrollPane.setViewportView(fileCountsByMimeTypeTable);

        org.openide.awt.Mnemonics.setLocalizedText(byMimeTypeLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryCountsPanel.class, "DataSourceSummaryCountsPanel.byMimeTypeLabel.text")); // NOI18N

        fileCountsByCategoryScrollPane.setViewportView(fileCountsByCategoryTable);

        org.openide.awt.Mnemonics.setLocalizedText(byCategoryLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryCountsPanel.class, "DataSourceSummaryCountsPanel.byCategoryLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(DataSourceSummaryCountsPanel.class, "DataSourceSummaryCountsPanel.jLabel1.text")); // NOI18N

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
}
