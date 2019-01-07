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
package org.sleuthkit.autopsy.casemodule.datasourceSummary;

import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.datamodel.utils.FileTypeUtils;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.CaseDbAccessManager.CaseDbAccessQueryCallback;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.IngestJobInfo;
import org.sleuthkit.datamodel.OSInfo;
import org.sleuthkit.datamodel.OSUtility;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

public class DataSourceSummaryPanel extends javax.swing.JPanel {

    private final List<IngestJobInfo> allIngestJobs = new ArrayList<>();
    private List<IngestJobInfo> ingestJobs = new ArrayList<>();
    private DataSourceTableModel dataSourceTableModel = new DataSourceTableModel();
    private IngestJobTableModel ingestJobTableModel = new IngestJobTableModel();
    private FilesTableModel filesTableModel = new FilesTableModel(null);
    private final List<DataSource> dataSources = new ArrayList<>();
    private final DateFormat datetimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    private static final Logger logger = Logger.getLogger(DataSourceSummaryPanel.class.getName());
    private List<OSInfo> osInfoList;

    /**
     * Creates new form DataSourceSummary
     */
    @Messages({"DataSourceSummaryPanel.getDataSources.error.text=Failed to get the list of datasources for the current case.",
        "DataSourceSummaryPanel.getDataSources.error.title=Load Failure"})
    public DataSourceSummaryPanel() {
        initComponents();
        ingestJobsTable.getTableHeader().setReorderingAllowed(false);
        fileCountsTable.getTableHeader().setReorderingAllowed(false);
        dataSourcesTable.getTableHeader().setReorderingAllowed(false);
        try {
            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            allIngestJobs.addAll(skCase.getIngestJobs());
            dataSources.addAll(skCase.getDataSources());
            osInfoList = OSUtility.getOSInfo(skCase);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to load ingest jobs.", ex);
            JOptionPane.showMessageDialog(this, Bundle.DataSourceSummaryPanel_getDataSources_error_text(), Bundle.DataSourceSummaryPanel_getDataSources_error_title(), JOptionPane.ERROR_MESSAGE);
        }
        dataSourcesTable.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (!e.getValueIsAdjusting()) {
                DataSource selectedDataSource = (dataSourcesTable.getSelectedRow() < 0 ? null : dataSources.get(dataSourcesTable.getSelectedRow()));
                updateIngestJobs(selectedDataSource);
                filesTableModel = new FilesTableModel(selectedDataSource);
                fileCountsTable.setModel(filesTableModel);
                opperatingSystemValueLabel.setText(getOSName(selectedDataSource));
                this.repaint();
            }
        });
    }

    private String getOSName(DataSource selectedDataSource) {
        String osName = "";
        if (selectedDataSource != null) {
            for (OSInfo osInfo : osInfoList) {
                try {
                    if (!osInfo.getArtifacts().isEmpty() && osInfo.getArtifacts().get(0).getDataSource().getId() == selectedDataSource.getId()) {
                        osName = osInfo.getOSName();
                        if (!osName.isEmpty()) {
                            break;
                        }
                    }
                } catch (TskCoreException ex) {

                }
            }
        }
        return osName;
    }

    @Messages({"DataSourceSummaryPanel.loadIngestJob.error.text=Failed to load ingest jobs.",
        "DataSourceSummaryPanel.loadIngestJob.error.title=Load Failure"})
    private void updateIngestJobs(DataSource selectedDataSource) {
        ingestJobs.clear();
        if (selectedDataSource != null) {
            for (IngestJobInfo ingestJob : allIngestJobs) {
                if (ingestJob.getObjectId() == selectedDataSource.getId()) {
                    ingestJobs.add(ingestJob);
                }
            }
        }
        ingestJobTableModel = new IngestJobTableModel();
        ingestJobsTable.setModel(ingestJobTableModel);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jSeparator1 = new javax.swing.JSeparator();
        dataSourcesScrollPane = new javax.swing.JScrollPane();
        dataSourcesTable = new javax.swing.JTable();
        ingestJobsScrollPane = new javax.swing.JScrollPane();
        ingestJobsTable = new javax.swing.JTable();
        fileCountsScrollPane = new javax.swing.JScrollPane();
        fileCountsTable = new javax.swing.JTable();
        opperatingSystemLabel = new javax.swing.JLabel();
        opperatingSystemValueLabel = new javax.swing.JLabel();
        fileCountsLabel = new javax.swing.JLabel();
        ingestJobsLabel = new javax.swing.JLabel();
        closeButton = new javax.swing.JButton();
        openButton = new javax.swing.JButton();

        dataSourcesTable.setModel(dataSourceTableModel);
        dataSourcesTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        dataSourcesScrollPane.setViewportView(dataSourcesTable);

        ingestJobsTable.setModel(ingestJobTableModel);
        ingestJobsTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        ingestJobsScrollPane.setViewportView(ingestJobsTable);

        fileCountsTable.setModel(filesTableModel);
        fileCountsScrollPane.setViewportView(fileCountsTable);

        org.openide.awt.Mnemonics.setLocalizedText(opperatingSystemLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryPanel.class, "DataSourceSummaryPanel.opperatingSystemLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(opperatingSystemValueLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryPanel.class, "DataSourceSummaryPanel.opperatingSystemValueLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(fileCountsLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryPanel.class, "DataSourceSummaryPanel.fileCountsLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(ingestJobsLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryPanel.class, "DataSourceSummaryPanel.ingestJobsLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(closeButton, org.openide.util.NbBundle.getMessage(DataSourceSummaryPanel.class, "DataSourceSummaryPanel.closeButton.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(openButton, org.openide.util.NbBundle.getMessage(DataSourceSummaryPanel.class, "DataSourceSummaryPanel.openButton.text")); // NOI18N
        openButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSeparator1, javax.swing.GroupLayout.Alignment.TRAILING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(dataSourcesScrollPane, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(openButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(closeButton))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(fileCountsLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(fileCountsScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ingestJobsLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(ingestJobsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 474, Short.MAX_VALUE)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addComponent(opperatingSystemLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(opperatingSystemValueLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {closeButton, openButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(8, 8, 8)
                .addComponent(dataSourcesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 120, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 5, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(fileCountsLabel)
                    .addComponent(ingestJobsLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(fileCountsScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 107, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ingestJobsScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(opperatingSystemLabel)
                    .addComponent(opperatingSystemValueLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(10, 10, 10)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(closeButton)
                    .addComponent(openButton))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Adds an action listener to the Close button of the panel.
     *
     * @param action
     */
    void addCloseButtonAction(ActionListener action) {
        this.closeButton.addActionListener(action);
        this.openButton.addActionListener(action);
    }

    void selectDataSource(Long dataSourceID) {
        if (dataSourceID != null) {
            for (int i = 0; i < dataSources.size(); i++) {
                if (dataSources.get(i).getId() == dataSourceID) {
                    dataSourcesTable.setRowSelectionInterval(i, i);
                    dataSourcesTable.scrollRectToVisible(new Rectangle(dataSourcesTable.getCellRect(i, 0, true)));
                    return;
                }
            }
        } 
        if (!dataSources.isEmpty()) {
            dataSourcesTable.setRowSelectionInterval(0, 0);
        }
    }

    private void openButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openButtonActionPerformed
        DataSource selectedDataSource = (dataSourcesTable.getSelectedRow() < 0 ? null : dataSources.get(dataSourcesTable.getSelectedRow()));
        if (selectedDataSource != null) {
            new ViewContextAction("", selectedDataSource).actionPerformed(evt);
        }
    }//GEN-LAST:event_openButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton closeButton;
    private javax.swing.JScrollPane dataSourcesScrollPane;
    private javax.swing.JTable dataSourcesTable;
    private javax.swing.JLabel fileCountsLabel;
    private javax.swing.JScrollPane fileCountsScrollPane;
    private javax.swing.JTable fileCountsTable;
    private javax.swing.JLabel ingestJobsLabel;
    private javax.swing.JScrollPane ingestJobsScrollPane;
    private javax.swing.JTable ingestJobsTable;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JButton openButton;
    private javax.swing.JLabel opperatingSystemLabel;
    private javax.swing.JLabel opperatingSystemValueLabel;
    // End of variables declaration//GEN-END:variables

    @Messages({"DataSourceSummaryPanel.IngestJobTableModel.StartTime.header=Start Time",
        "DataSourceSummaryPanel.IngestJobTableModel.EndTime.header=End Time",
        "DataSourceSummaryPanel.IngestJobTableModel.IngestStatus.header=Ingest Status"})
    private class IngestJobTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private final List<String> columnHeaders = new ArrayList<>();

        IngestJobTableModel() {
            columnHeaders.add(Bundle.DataSourceSummaryPanel_IngestJobTableModel_StartTime_header());
            columnHeaders.add(Bundle.DataSourceSummaryPanel_IngestJobTableModel_EndTime_header());
            columnHeaders.add(Bundle.DataSourceSummaryPanel_IngestJobTableModel_IngestStatus_header());
        }

        @Override
        public int getRowCount() {
            return ingestJobs.size();
        }

        @Override
        public int getColumnCount() {
            return columnHeaders.size();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            IngestJobInfo currIngestJob = ingestJobs.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return datetimeFormat.format(currIngestJob.getStartDateTime());
                case 1:
                    Date endDate = currIngestJob.getEndDateTime();
                    if (endDate.getTime() == 0) {
                        return "N/A";
                    }
                    return datetimeFormat.format(currIngestJob.getEndDateTime());
                case 2:
                    return currIngestJob.getStatus().getDisplayName();
                default:
                    break;
            }
            return null;
        }

        @Override
        public String getColumnName(int column) {
            return columnHeaders.get(column);
        }

    }

    @Messages({"DataSourceSummaryPanel.FilesTableModel.type.header=File Type",
        "DataSourceSummaryPanel.FilesTableModel.count.header=Count"})
    private class FilesTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private final DataSource currentDataSource;
        private final List<String> columnHeaders = new ArrayList<>();

        FilesTableModel(DataSource selectedDataSource) {
            columnHeaders.add(Bundle.DataSourceSummaryPanel_FilesTableModel_type_header());
            columnHeaders.add(Bundle.DataSourceSummaryPanel_FilesTableModel_count_header());
            currentDataSource = selectedDataSource;
        }

        @Override
        public int getRowCount() {
            return 5;
        }

        @Override
        public int getColumnCount() {
            return columnHeaders.size();
        }

        @Messages({
            "DataSourceSummaryPanel.FilesTableModel.images.row=Images",
            "DataSourceSummaryPanel.FilesTableModel.videos.row=Videos",
            "DataSourceSummaryPanel.FilesTableModel.audio.row=Audio",
            "DataSourceSummaryPanel.FilesTableModel.documents.row=Documents",
            "DataSourceSummaryPanel.FilesTableModel.executables.row=Executables"
        })
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                switch (rowIndex) {
                    case 0:
                        return Bundle.DataSourceSummaryPanel_FilesTableModel_images_row();
                    case 1:
                        return Bundle.DataSourceSummaryPanel_FilesTableModel_videos_row();
                    case 2:
                        return Bundle.DataSourceSummaryPanel_FilesTableModel_audio_row();
                    case 3:
                        return Bundle.DataSourceSummaryPanel_FilesTableModel_documents_row();
                    case 4:
                        return Bundle.DataSourceSummaryPanel_FilesTableModel_executables_row();
                    default:
                        break;
                }
            } else if (columnIndex == 1) {
                switch (rowIndex) {
                    case 0:
                        return getCountOfFiles(FileTypeUtils.FileTypeCategory.IMAGE.getMediaTypes());
                    case 1:
                        return getCountOfFiles(FileTypeUtils.FileTypeCategory.VIDEO.getMediaTypes());
                    case 2:
                        return getCountOfFiles(FileTypeUtils.FileTypeCategory.AUDIO.getMediaTypes());
                    case 3:
                        return getCountOfFiles(FileTypeUtils.FileTypeCategory.DOCUMENTS.getMediaTypes());
                    case 4:
                        return getCountOfFiles(FileTypeUtils.FileTypeCategory.EXECUTABLE.getMediaTypes());
                    default:
                        break;
                }
            }
            return null;
        }

        private Long getCountOfFiles(Set<String> setOfMimeTypes) {
            if (currentDataSource != null) {
                try {
                    String inClause = String.join("', '", setOfMimeTypes);
                    SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
                    return skCase.countFilesWhere("data_source_obj_id=" + currentDataSource.getId()
                            + " AND type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType()
                            + " AND dir_type<>" + TskData.TSK_FS_NAME_TYPE_ENUM.VIRT_DIR.getValue()
                            + " AND mime_type IN ('" + inClause + "')"
                            + " AND name<>''");
                } catch (TskCoreException | NoCurrentCaseException ex) {

                }
            }
            return null;
        }

        @Override
        public String getColumnName(int column) {
            return columnHeaders.get(column);
        }

    }

    @Messages({"DataSourceSummaryPanel.DataSourceTableModel.dataSourceName.header=Data Source Name",
        "DataSourceSummaryPanel.DataSourceTableModel.type.header=Type",
        "DataSourceSummaryPanel.DataSourceTableModel.files.header=Files",
        "DataSourceSummaryPanel.DataSourceTableModel.results.header=Results",
        "DataSourceSummaryPanel.DataSourceTableModel.tags.header=Tags"})
    private class DataSourceTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private final List<String> columnHeaders = new ArrayList<>();
        private final Map<Long, Long> fileCountsMap;
        private final Map<Long, Long> artifactCountsMap;
        private final Map<Long, Long> tagCountsMap;

        DataSourceTableModel() {
            columnHeaders.add(Bundle.DataSourceSummaryPanel_DataSourceTableModel_dataSourceName_header());
            columnHeaders.add(Bundle.DataSourceSummaryPanel_DataSourceTableModel_type_header());
            columnHeaders.add(Bundle.DataSourceSummaryPanel_DataSourceTableModel_files_header());
            columnHeaders.add(Bundle.DataSourceSummaryPanel_DataSourceTableModel_results_header());
            columnHeaders.add(Bundle.DataSourceSummaryPanel_DataSourceTableModel_tags_header());
            fileCountsMap = getCountsOfFiles();
            artifactCountsMap = getCountsOfArtifacts();
            tagCountsMap = getCountsOfTags();

        }

        @Override
        public int getRowCount() {
            return dataSources.size();
        }

        @Override
        public int getColumnCount() {
            return columnHeaders.size();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            DataSource currentDataSource = dataSources.get(rowIndex);
            Long count;
            switch (columnIndex) {
                case 0:
                    return currentDataSource.getName();
                case 1:
                    return "";
                case 2:
                    count = fileCountsMap.get(currentDataSource.getId());
                    return count == null ? 0 : count;
                case 3:
                    count = artifactCountsMap.get(currentDataSource.getId());
                    return count == null ? 0 : count;
                case 4:
                    count = tagCountsMap.get(currentDataSource.getId());
                    return count == null ? 0 : count;
                default:
                    break;
            }
            return null;
        }

        private Map<Long, Long> getCountsOfFiles() {
            try {
                SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
                DataSourceCountsCallback callback = new DataSourceCountsCallback();
                final String countFilesQuery = "data_source_obj_id, COUNT(*) AS count"
                        + " FROM tsk_files WHERE type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType()
                        + " AND dir_type<>" + TskData.TSK_FS_NAME_TYPE_ENUM.VIRT_DIR.getValue()
                        + " AND name<>'' GROUP BY data_source_obj_id"; //NON-NLS
                skCase.getCaseDbAccessManager().select(countFilesQuery, callback);
                return callback.getMapOfCounts();
            } catch (TskCoreException | NoCurrentCaseException ex) {
                return Collections.emptyMap();
            }
        }

        private Map<Long, Long> getCountsOfArtifacts() {
            try {
                SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
                DataSourceCountsCallback callback = new DataSourceCountsCallback();
                final String countArtifactsQuery = "data_source_obj_id, COUNT(*) AS count"
                        + " FROM blackboard_artifacts WHERE review_status_id !=" + BlackboardArtifact.ReviewStatus.REJECTED.getID()
                        + " GROUP BY data_source_obj_id"; //NON-NLS
                skCase.getCaseDbAccessManager().select(countArtifactsQuery, callback);
                return callback.getMapOfCounts();
            } catch (TskCoreException | NoCurrentCaseException ex) {
                return Collections.emptyMap();
            }
        }

        private Map<Long, Long> getCountsOfTags() {
            try {
                SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
                DataSourceCountsCallback fileCountcallback = new DataSourceCountsCallback();
                final String countFileTagsQuery = "data_source_obj_id, COUNT(*) AS count"
                        + " FROM content_tags as content_tags, tsk_files as tsk_files"
                        + " WHERE content_tags.obj_id = tsk_files.obj_id"
                        + " GROUP BY data_source_obj_id"; //NON-NLS
                skCase.getCaseDbAccessManager().select(countFileTagsQuery, fileCountcallback);
                Map<Long, Long> tagCountMap = new HashMap<>(fileCountcallback.getMapOfCounts());
                DataSourceCountsCallback artifactCountcallback = new DataSourceCountsCallback();
                final String countArtifactTagsQuery = "data_source_obj_id, COUNT(*) AS count"
                        + " FROM blackboard_artifact_tags as artifact_tags,  blackboard_artifacts AS arts"
                        + " WHERE artifact_tags.artifact_id = arts.artifact_id"
                        + " GROUP BY data_source_obj_id"; //NON-NLS
                skCase.getCaseDbAccessManager().select(countArtifactTagsQuery, artifactCountcallback);
                //combine the results from the count artifact tags query into the copy of the mapped results from the count file tags query
                artifactCountcallback.getMapOfCounts().forEach((key, value) -> tagCountMap.merge(key, value, (value1, value2) -> value1 + value2));
                return tagCountMap;
            } catch (TskCoreException | NoCurrentCaseException ex) {
                return Collections.emptyMap();
            }

        }

        @Override
        public String getColumnName(int column) {
            return columnHeaders.get(column);
        }

        private class DataSourceCountsCallback implements CaseDbAccessQueryCallback {

            Map<Long, Long> dataSourceObjIdCounts = new HashMap<>();

            @Override
            public void process(ResultSet rs) {
                try {
                    while (rs.next()) {
                        try {
                            dataSourceObjIdCounts.put(rs.getLong("data_source_obj_id"), rs.getLong("count"));
                        } catch (SQLException ex) {
                            System.out.println("UNABLE TO GET COUNT FOR DS " + ex.getMessage());
                        }
                    }
                } catch (SQLException ex) {
                    System.out.println("Failed to get next result" + ex.getMessage());
                }
            }

            Map<Long, Long> getMapOfCounts() {
                return Collections.unmodifiableMap(dataSourceObjIdCounts);
            }

        }

    }

}
