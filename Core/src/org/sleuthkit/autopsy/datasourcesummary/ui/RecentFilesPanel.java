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
import java.util.function.Supplier;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.IngestModuleCheckUtil;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.RecentFilesSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.RecentFilesSummary.RecentAttachmentDetails;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.RecentFilesSummary.RecentDownloadDetails;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.RecentFilesSummary.RecentFileDetails;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.CellModelTableCellRenderer;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.CellModelTableCellRenderer.DefaultCellModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.CellModelTableCellRenderer.MenuItem;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.IngestRunningLabel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel.ColumnModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ListTableModel;
import org.sleuthkit.datamodel.DataSource;

/**
 * Data Source Summary recent files panel.
 */
public final class RecentFilesPanel extends BaseDataSourceSummaryPanel {

    private static final long serialVersionUID = 1L;
    private static final String EMAIL_PARSER_FACTORY = "org.sleuthkit.autopsy.thunderbirdparser.EmailParserModuleFactory";
    private static final String EMAIL_PARSER_MODULE_NAME = Bundle.RecentFilePanel_emailParserModuleName();

    private final List<JTablePanel<?>> tablePanelList = new ArrayList<>();
    private final List<DataFetchWorker.DataFetchComponents<DataSource, ?>> dataFetchComponents = new ArrayList<>();

    private final IngestRunningLabel ingestRunningLabel = new IngestRunningLabel();

    private final RecentFilesSummary dataHandler;

    @Messages({
        "RecentFilesPanel_col_head_date=Date",
        "RecentFilePanel_col_header_domain=Domain",
        "RecentFilePanel_col_header_path=Path",
        "RecentFilePanel_col_header_sender=Sender",
        "RecentFilePanel_emailParserModuleName=Email Parser"
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

    /**
     * Takes a base class of RecentFileDetails and provides the pertinent menu
     * items.
     *
     * @param record The RecentFileDetails instance.
     * @return The menu items list containing one action or navigating to the
     * appropriate artifact/file and closing the data source summary dialog if
     * open.
     */
    private Supplier<List<MenuItem>> getPopupFunct(RecentFileDetails record) {
        return () -> {
            if (record == null) {
                return null;
            }

            List<MenuItem> toRet = new ArrayList<>();

            MenuItem fileNav = getFileNavigateItem(record.getPath());
            if (fileNav != null) {
                toRet.add(fileNav);
            }

            if (record.getArtifact() != null) {
                toRet.add(getArtifactNavigateItem(record.getArtifact()));
            }

            return (toRet.size() > 0) ? toRet : null;
        };
    }

    @Override
    protected void fetchInformation(DataSource dataSource) {
        fetchInformation(dataFetchComponents, dataSource);
    }

    @Override
    protected void onNewDataSource(DataSource dataSource) {
        onNewDataSource(dataFetchComponents, tablePanelList, dataSource);
    }

    @Override
    public void close() {
        ingestRunningLabel.unregister();
        super.close();
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
                            return new DefaultCellModel(prog.getPath())
                                    .setPopupMenuRetriever(getPopupFunct(prog));
                        }, 250),
                new ColumnModel<>(Bundle.RecentFilesPanel_col_head_date(),
                        (prog) -> {
                            return new DefaultCellModel(prog.getDateAsString())
                                    .setPopupMenuRetriever(getPopupFunct(prog));
                        }, 80));

        ListTableModel<RecentFileDetails> tableModel = JTablePanel.getTableModel(list);

        JTablePanel<RecentFileDetails> pane = (JTablePanel<RecentFileDetails>) openedDocPane;
        pane.setModel(tableModel);
        pane.setColumnModel(JTablePanel.getTableColumnModel(list));
        pane.setKeyFunction((recentFile) -> recentFile.getPath());
        pane.setCellListener(CellModelTableCellRenderer.getMouseListener());
        tablePanelList.add(pane);

        DataFetchWorker.DataFetchComponents<DataSource, List<RecentFileDetails>> worker
                = new DataFetchWorker.DataFetchComponents<>(
                        (dataSource) -> dataHandler.getRecentlyOpenedDocuments(dataSource, 10),
                        (result) -> {
                            showResultWithModuleCheck(pane, result,
                                    IngestModuleCheckUtil.RECENT_ACTIVITY_FACTORY,
                                    IngestModuleCheckUtil.RECENT_ACTIVITY_MODULE_NAME);
                        });

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
                            return new DefaultCellModel(prog.getWebDomain())
                                    .setPopupMenuRetriever(getPopupFunct(prog));
                        }, 100),
                new ColumnModel<>(Bundle.RecentFilePanel_col_header_path(),
                        (prog) -> {
                            return new DefaultCellModel(prog.getPath())
                                    .setPopupMenuRetriever(getPopupFunct(prog));
                        }, 250),
                new ColumnModel<>(Bundle.RecentFilesPanel_col_head_date(),
                        (prog) -> {
                            return new DefaultCellModel(prog.getDateAsString())
                                    .setPopupMenuRetriever(getPopupFunct(prog));
                        }, 80));

        ListTableModel<RecentDownloadDetails> tableModel = JTablePanel.getTableModel(list);

        JTablePanel<RecentDownloadDetails> pane = (JTablePanel<RecentDownloadDetails>) downloadsPane;
        pane.setModel(tableModel);
        pane.setKeyFunction((download) -> download.getPath());
        pane.setColumnModel(JTablePanel.getTableColumnModel(list));
        pane.setCellListener(CellModelTableCellRenderer.getMouseListener());
        tablePanelList.add(pane);

        DataFetchWorker.DataFetchComponents<DataSource, List<RecentDownloadDetails>> worker
                = new DataFetchWorker.DataFetchComponents<>(
                        (dataSource) -> dataHandler.getRecentDownloads(dataSource, 10),
                        (result) -> {
                            showResultWithModuleCheck(pane, result,
                                    IngestModuleCheckUtil.RECENT_ACTIVITY_FACTORY,
                                    IngestModuleCheckUtil.RECENT_ACTIVITY_MODULE_NAME);
                        });

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
                            return new DefaultCellModel(prog.getPath())
                                    .setPopupMenuRetriever(getPopupFunct(prog));
                        }, 250),
                new ColumnModel<>(Bundle.RecentFilesPanel_col_head_date(),
                        (prog) -> {
                            return new DefaultCellModel(prog.getDateAsString())
                                    .setPopupMenuRetriever(getPopupFunct(prog));
                        }, 80),
                new ColumnModel<>(Bundle.RecentFilePanel_col_header_sender(),
                        (prog) -> {
                            return new DefaultCellModel(prog.getSender())
                                    .setPopupMenuRetriever(getPopupFunct(prog));
                        }, 150));

        ListTableModel<RecentAttachmentDetails> tableModel = JTablePanel.getTableModel(list);

        JTablePanel<RecentAttachmentDetails> pane = (JTablePanel<RecentAttachmentDetails>) attachmentsPane;
        pane.setModel(tableModel);
        pane.setKeyFunction((attachment) -> attachment.getPath());
        pane.setColumnModel(JTablePanel.getTableColumnModel(list));
        pane.setCellListener(CellModelTableCellRenderer.getMouseListener());
        tablePanelList.add(pane);

        DataFetchWorker.DataFetchComponents<DataSource, List<RecentAttachmentDetails>> worker
                = new DataFetchWorker.DataFetchComponents<>(
                        (dataSource) -> dataHandler.getRecentAttachments(dataSource, 10),
                        (result) -> showResultWithModuleCheck(pane, result, EMAIL_PARSER_FACTORY, EMAIL_PARSER_MODULE_NAME)
                );

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
        javax.swing.JPanel ingestRunningPanel = ingestRunningLabel;
        openedDocPane = new JTablePanel<RecentFileDetails>();
        downloadsPane = new JTablePanel<RecentDownloadDetails>();
        attachmentsPane = new JTablePanel<RecentAttachmentDetails>();
        javax.swing.JLabel openDocsLabel = new javax.swing.JLabel();
        javax.swing.JLabel downloadLabel = new javax.swing.JLabel();
        javax.swing.JLabel attachmentLabel = new javax.swing.JLabel();
        javax.swing.JLabel rightClickForMoreOptions1 = new javax.swing.JLabel();
        javax.swing.JLabel rightClickForMoreOptions2 = new javax.swing.JLabel();
        javax.swing.JLabel rightClickForMoreOptions3 = new javax.swing.JLabel();

        setLayout(new java.awt.BorderLayout());

        tablePanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        tablePanel.setMinimumSize(new java.awt.Dimension(400, 400));
        tablePanel.setPreferredSize(new java.awt.Dimension(600, 400));
        tablePanel.setLayout(new java.awt.GridBagLayout());

        ingestRunningPanel.setAlignmentX(0.0F);
        ingestRunningPanel.setMaximumSize(new java.awt.Dimension(32767, 25));
        ingestRunningPanel.setMinimumSize(new java.awt.Dimension(10, 25));
        ingestRunningPanel.setPreferredSize(new java.awt.Dimension(10, 25));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        tablePanel.add(ingestRunningPanel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        tablePanel.add(openedDocPane, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        tablePanel.add(downloadsPane, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        tablePanel.add(attachmentsPane, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(openDocsLabel, org.openide.util.NbBundle.getMessage(RecentFilesPanel.class, "RecentFilesPanel.openDocsLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        tablePanel.add(openDocsLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(downloadLabel, org.openide.util.NbBundle.getMessage(RecentFilesPanel.class, "RecentFilesPanel.downloadLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(20, 0, 0, 0);
        tablePanel.add(downloadLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(attachmentLabel, org.openide.util.NbBundle.getMessage(RecentFilesPanel.class, "RecentFilesPanel.attachmentLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(20, 0, 0, 0);
        tablePanel.add(attachmentLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(rightClickForMoreOptions1, org.openide.util.NbBundle.getMessage(RecentFilesPanel.class, "RecentFilesPanel.rightClickForMoreOptions1.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        tablePanel.add(rightClickForMoreOptions1, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(rightClickForMoreOptions2, org.openide.util.NbBundle.getMessage(RecentFilesPanel.class, "RecentFilesPanel.rightClickForMoreOptions2.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        tablePanel.add(rightClickForMoreOptions2, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(rightClickForMoreOptions3, org.openide.util.NbBundle.getMessage(RecentFilesPanel.class, "RecentFilesPanel.rightClickForMoreOptions3.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        tablePanel.add(rightClickForMoreOptions3, gridBagConstraints);

        scrollPane.setViewportView(tablePanel);

        add(scrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel attachmentsPane;
    private javax.swing.JPanel downloadsPane;
    private javax.swing.JPanel openedDocPane;
    // End of variables declaration//GEN-END:variables
}
