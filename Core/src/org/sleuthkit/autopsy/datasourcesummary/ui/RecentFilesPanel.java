/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.datasourcesummary.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.RecentFilesSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.RecentFilesSummary.RecentAttachmentDetails;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.RecentFilesSummary.RecentDownloadDetails;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.RecentFilesSummary.RecentFileDetails;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.CellModelTableCellRenderer.DefaultCellModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel.ColumnModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ListTableModel;
import org.sleuthkit.datamodel.DataSource;

/**
 * Data Source Summary recent files panel.
 */
public final class RecentFilesPanel extends BaseDataSourceSummaryPanel {

    private static final long serialVersionUID = 1L;

    private final List<JTablePanel<?>> tablePanelList = new ArrayList<>();
    private final List<DataFetchWorker.DataFetchComponents<DataSource, ?>> dataFetchComponents = new ArrayList<>();

    private final RecentFilesSummary dataHandler;

    @Messages({
        "RecentFilesPanel_col_head_date=Date",
        "RecentFilePanel_col_header_domain=Domain",
        "RecentFilePanel_col_header_path=Path",
        "RecentFilePanel_col_header_sender=Sender"
    })

    /**
     * Default constructor.
     */
    public RecentFilesPanel() {
        this(new RecentFilesSummary());
    }

    /**
     * Creates new form RecentFilesPanel
     */
    public RecentFilesPanel(RecentFilesSummary dataHandler) {
        super(dataHandler);
        this.dataHandler = dataHandler;

        initComponents();
        initalizeTables();
    }

    @Override
    protected void fetchInformation(DataSource dataSource) {
        fetchInformation(dataFetchComponents, dataSource);
    }

    @Override
    protected void onNewDataSource(DataSource dataSource) {
        onNewDataSource(dataFetchComponents, tablePanelList, dataSource);
    }

    /**
     * Setup the data model and columns for the panel tables.
     */
    private void initalizeTables() {
        initalizeOpenDocsTable();
        initalizeDownloadTable();
        initalizeAttchementsTable();
    }

    @Messages({
        "RecentFilePanel_no_open_documents=No recently open documents found."
    })
    /**
     * Setup the data model and columns for the recently open table.
     */
    @SuppressWarnings("unchecked")
    private void initalizeOpenDocsTable() {
        List<ColumnModel<RecentFileDetails>> list = Arrays.asList(
                new ColumnModel<>(Bundle.RecentFilePanel_col_header_path(),
                        (prog) -> {
                            return new DefaultCellModel(prog.getPath());
                        }, 250),
                new ColumnModel<>(Bundle.RecentFilesPanel_col_head_date(),
                        (prog) -> {
                            return new DefaultCellModel(prog.getDateAsString());
                        }, 80));

        ListTableModel<RecentFileDetails> tableModel = JTablePanel.getTableModel(list);

        JTablePanel<RecentFileDetails> pane = (JTablePanel<RecentFileDetails>) openedDocPane;
        pane.setModel(tableModel);
        pane.setColumnModel(JTablePanel.getTableColumnModel(list));
        pane.setKeyFunction((recentFile) -> recentFile.getPath());
        tablePanelList.add(pane);

        DataFetchWorker.DataFetchComponents<DataSource, List<RecentFileDetails>> worker
                = new DataFetchWorker.DataFetchComponents<>(
                        (dataSource) -> dataHandler.getRecentlyOpenedDocuments(dataSource, 10),
                        (result) -> pane.showDataFetchResult(result, JTablePanel.getDefaultErrorMessage(),
                                Bundle.RecentFilePanel_no_open_documents()));

        dataFetchComponents.add(worker);
    }

    /**
     * Setup the data model and columns for the recent download table.
     */
    @SuppressWarnings("unchecked")
    private void initalizeDownloadTable() {
        List<ColumnModel<RecentDownloadDetails>> list = Arrays.asList(
                new ColumnModel<>(Bundle.RecentFilePanel_col_header_domain(),
                        (prog) -> {
                            return new DefaultCellModel(prog.getWebDomain());
                        }, 100),
                new ColumnModel<>(Bundle.RecentFilePanel_col_header_path(),
                        (prog) -> {
                            return new DefaultCellModel(prog.getPath());
                        }, 250),
                new ColumnModel<>(Bundle.RecentFilesPanel_col_head_date(),
                        (prog) -> {
                            return new DefaultCellModel(prog.getDateAsString());
                        }, 80));

        ListTableModel<RecentDownloadDetails> tableModel = JTablePanel.getTableModel(list);

        JTablePanel<RecentDownloadDetails> pane = (JTablePanel<RecentDownloadDetails>) downloadsPane;
        pane.setModel(tableModel);
        pane.setKeyFunction((download) -> download.getPath());
        pane.setColumnModel(JTablePanel.getTableColumnModel(list));
        tablePanelList.add(pane);

        DataFetchWorker.DataFetchComponents<DataSource, List<RecentDownloadDetails>> worker
                = new DataFetchWorker.DataFetchComponents<>(
                        (dataSource) -> dataHandler.getRecentDownloads(dataSource, 10),
                        (result) -> pane.showDataFetchResult(result, JTablePanel.getDefaultErrorMessage(),
                                Bundle.RecentFilePanel_no_open_documents()));

        dataFetchComponents.add(worker);
    }

    /**
     * Setup the data model and columns for the recent attachments.
     */
    @SuppressWarnings("unchecked")
    private void initalizeAttchementsTable() {
        List<ColumnModel<RecentAttachmentDetails>> list = Arrays.asList(
                new ColumnModel<>(Bundle.RecentFilePanel_col_header_path(),
                        (prog) -> {
                            return new DefaultCellModel(prog.getPath());
                        }, 250),
                new ColumnModel<>(Bundle.RecentFilesPanel_col_head_date(),
                        (prog) -> {
                            return new DefaultCellModel(prog.getDateAsString());
                        }, 80),
                new ColumnModel<>(Bundle.RecentFilePanel_col_header_sender(),
                        (prog) -> {
                            return new DefaultCellModel(prog.getSender());
                        }, 150));

        ListTableModel<RecentAttachmentDetails> tableModel = JTablePanel.getTableModel(list);

        JTablePanel<RecentAttachmentDetails> pane = (JTablePanel<RecentAttachmentDetails>) attachmentsPane;
        pane.setModel(tableModel);
        pane.setKeyFunction((attachment) -> attachment.getPath());
        pane.setColumnModel(JTablePanel.getTableColumnModel(list));
        tablePanelList.add(pane);

        DataFetchWorker.DataFetchComponents<DataSource, List<RecentAttachmentDetails>> worker
                = new DataFetchWorker.DataFetchComponents<>(
                        (dataSource) -> dataHandler.getRecentAttachments(dataSource, 10),
                        (result) -> pane.showDataFetchResult(result, JTablePanel.getDefaultErrorMessage(),
                                Bundle.RecentFilePanel_no_open_documents()));

        dataFetchComponents.add(worker);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane();
        javax.swing.JPanel tablePanel = new javax.swing.JPanel();
        openedDocPane = new JTablePanel<RecentFileDetails>();
        downloadsPane = new JTablePanel<RecentDownloadDetails>();
        attachmentsPane = new JTablePanel<RecentAttachmentDetails>();
        javax.swing.JLabel openDocsLabel = new javax.swing.JLabel();
        javax.swing.JLabel downloadLabel = new javax.swing.JLabel();
        javax.swing.JLabel attachmentLabel = new javax.swing.JLabel();

        setLayout(new java.awt.BorderLayout());

        tablePanel.setMinimumSize(new java.awt.Dimension(400, 400));
        tablePanel.setPreferredSize(new java.awt.Dimension(600, 400));
        tablePanel.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        tablePanel.add(openedDocPane, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        tablePanel.add(downloadsPane, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 10, 5);
        tablePanel.add(attachmentsPane, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(openDocsLabel, org.openide.util.NbBundle.getMessage(RecentFilesPanel.class, "RecentFilesPanel.openDocsLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 0, 5);
        tablePanel.add(openDocsLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(downloadLabel, org.openide.util.NbBundle.getMessage(RecentFilesPanel.class, "RecentFilesPanel.downloadLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(15, 5, 0, 5);
        tablePanel.add(downloadLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(attachmentLabel, org.openide.util.NbBundle.getMessage(RecentFilesPanel.class, "RecentFilesPanel.attachmentLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(15, 5, 0, 5);
        tablePanel.add(attachmentLabel, gridBagConstraints);

        scrollPane.setViewportView(tablePanel);

        add(scrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel attachmentsPane;
    private javax.swing.JPanel downloadsPane;
    private javax.swing.JPanel openedDocPane;
    // End of variables declaration//GEN-END:variables
}
