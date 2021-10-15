/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.datasourcesummary.ui;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.RecentFilesSummary.RecentAttachmentDetails;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.RecentFilesSummary.RecentDownloadDetails;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.RecentFilesSummary.RecentFileDetails;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.CellModelTableCellRenderer;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ColumnModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataFetcher;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultCellModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.GuiCellModel.MenuItem;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.IngestRunningLabel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ListTableModel;
import org.sleuthkit.datamodel.DataSource;

/**
 * Data Source Summary recent files panel.
 */
@Messages({
    "RecentFilesPanel_docsTable_tabName=Recently Opened Documents",
    "RecentFilesPanel_downloadsTable_tabName=Recent Downloads",
    "RecentFilesPanel_attachmentsTable_tabName=Recent Attachments",})
public final class RecentFilesPanel extends BaseDataSourceSummaryPanel {

    private static final long serialVersionUID = 1L;
    private static final String DATETIME_FORMAT_STR = "yyyy/MM/dd HH:mm:ss";
    private static final DateFormat DATETIME_FORMAT = new SimpleDateFormat(DATETIME_FORMAT_STR, Locale.getDefault());

    private final List<JTablePanel<?>> tablePanelList = new ArrayList<>();
    private final List<DataFetchWorker.DataFetchComponents<DataSource, ?>> dataFetchComponents = new ArrayList<>();

    private final IngestRunningLabel ingestRunningLabel = new IngestRunningLabel();

    private final DataFetcher<DataSource, List<RecentFileDetails>> docsFetcher;
    private final DataFetcher<DataSource, List<RecentDownloadDetails>> downloadsFetcher;
    private final DataFetcher<DataSource, List<RecentAttachmentDetails>> attachmentsFetcher;

    private final List<ColumnModel<RecentFileDetails, DefaultCellModel<?>>> docsTemplate = Arrays.asList(
            new ColumnModel<>(Bundle.RecentFilesPanel_col_header_path(),
                    (prog) -> {
                        return new DefaultCellModel<>(prog.getPath())
                                .setPopupMenuRetriever(getPopupFunct(prog));
                    }, 250),
            new ColumnModel<>(Bundle.RecentFilesPanel_col_head_date(),
                    getDateFunct(),
                    80));

    private final List<ColumnModel<RecentDownloadDetails, DefaultCellModel<?>>> downloadsTemplate = Arrays.asList(
            new ColumnModel<>(Bundle.RecentFilesPanel_col_header_domain(),
                    (prog) -> {
                        return new DefaultCellModel<>(prog.getWebDomain())
                                .setPopupMenuRetriever(getPopupFunct(prog));
                    }, 100),
            new ColumnModel<>(Bundle.RecentFilesPanel_col_header_path(),
                    (prog) -> {
                        return new DefaultCellModel<>(prog.getPath())
                                .setPopupMenuRetriever(getPopupFunct(prog));
                    }, 250),
            new ColumnModel<>(Bundle.RecentFilesPanel_col_head_date(),
                    getDateFunct(),
                    80));

    private final List<ColumnModel<RecentAttachmentDetails, DefaultCellModel<?>>> attachmentsTemplate = Arrays.asList(
            new ColumnModel<>(Bundle.RecentFilesPanel_col_header_path(),
                    (prog) -> {
                        return new DefaultCellModel<>(prog.getPath())
                                .setPopupMenuRetriever(getPopupFunct(prog));
                    }, 250),
            new ColumnModel<>(Bundle.RecentFilesPanel_col_head_date(),
                    getDateFunct(),
                    80),
            new ColumnModel<>(Bundle.RecentFilesPanel_col_header_sender(),
                    (prog) -> {
                        return new DefaultCellModel<>(prog.getSender())
                                .setPopupMenuRetriever(getPopupFunct(prog));
                    }, 150));

    /**
     * Default constructor.
     */
    @Messages({
        "RecentFilesPanel_col_head_date=Date",
        "RecentFilesPanel_col_header_domain=Domain",
        "RecentFilesPanel_col_header_path=Path",
        "RecentFilesPanel_col_header_sender=Sender"
    })
    public RecentFilesPanel() {
        this(new RecentFilesGetter());
    }

    /**
     * Creates new form RecentFilesPanel
     */
    public RecentFilesPanel(RecentFilesGetter dataHandler) {
        super(dataHandler);
        docsFetcher = (dataSource) -> dataHandler.getRecentlyOpenedDocuments(dataSource, 10);
        downloadsFetcher = (dataSource) -> dataHandler.getRecentDownloads(dataSource, 10);
        attachmentsFetcher = (dataSource) -> dataHandler.getRecentAttachments(dataSource, 10);

        initComponents();
        initalizeTables();
    }

    /**
     * Returns a function that gets the date from the RecentFileDetails object
     * and converts into a DefaultCellModel to be displayed in a table.
     *
     * @return The function that determines the date cell from a
     *         RecentFileDetails object.
     */
    private <T extends RecentFileDetails> Function<T, DefaultCellModel<?>> getDateFunct() {
        return (T lastAccessed) -> {
            Function<Date, String> dateParser = (dt) -> dt == null ? "" : DATETIME_FORMAT.format(dt);
            return new DefaultCellModel<>(new Date(lastAccessed.getDateAsLong() * 1000), dateParser)
                    .setPopupMenuRetriever(getPopupFunct(lastAccessed));
        };
    }

    /**
     * Takes a base class of RecentFileDetails and provides the pertinent menu
     * items.
     *
     * @param record The RecentFileDetails instance.
     *
     * @return The menu items list containing one action or navigating to the
     *         appropriate artifact/file and closing the data source summary
     *         dialog if open.
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
        "RecentFilesPanel_no_open_documents=No recently open documents found."
    })
    /**
     * Setup the data model and columns for the recently open table.
     */
    @SuppressWarnings("unchecked")
    private void initalizeOpenDocsTable() {
        ListTableModel<RecentFileDetails> tableModel = JTablePanel.getTableModel(docsTemplate);

        JTablePanel<RecentFileDetails> pane = (JTablePanel<RecentFileDetails>) openedDocPane;
        pane.setModel(tableModel);
        pane.setColumnModel(JTablePanel.getTableColumnModel(docsTemplate));
        pane.setKeyFunction((recentFile) -> recentFile.getPath());
        pane.setCellListener(CellModelTableCellRenderer.getMouseListener());
        tablePanelList.add(pane);

        DataFetchWorker.DataFetchComponents<DataSource, List<RecentFileDetails>> worker
                = new DataFetchWorker.DataFetchComponents<>(
                        docsFetcher,
                        (result) -> pane.showDataFetchResult(result));

        dataFetchComponents.add(worker);
    }

    /**
     * Setup the data model and columns for the recent download table.
     */
    @SuppressWarnings("unchecked")
    private void initalizeDownloadTable() {
        ListTableModel<RecentDownloadDetails> tableModel = JTablePanel.getTableModel(downloadsTemplate);

        JTablePanel<RecentDownloadDetails> pane = (JTablePanel<RecentDownloadDetails>) downloadsPane;
        pane.setModel(tableModel);
        pane.setKeyFunction((download) -> download.getPath());
        pane.setColumnModel(JTablePanel.getTableColumnModel(downloadsTemplate));
        pane.setCellListener(CellModelTableCellRenderer.getMouseListener());
        tablePanelList.add(pane);

        DataFetchWorker.DataFetchComponents<DataSource, List<RecentDownloadDetails>> worker
                = new DataFetchWorker.DataFetchComponents<>(
                        downloadsFetcher,
                        (result) -> pane.showDataFetchResult(result));

        dataFetchComponents.add(worker);
    }

    /**
     * Setup the data model and columns for the recent attachments.
     */
    @SuppressWarnings("unchecked")
    private void initalizeAttchementsTable() {
        ListTableModel<RecentAttachmentDetails> tableModel = JTablePanel.getTableModel(attachmentsTemplate);

        JTablePanel<RecentAttachmentDetails> pane = (JTablePanel<RecentAttachmentDetails>) attachmentsPane;
        pane.setModel(tableModel);
        pane.setKeyFunction((attachment) -> attachment.getPath());
        pane.setColumnModel(JTablePanel.getTableColumnModel(attachmentsTemplate));
        pane.setCellListener(CellModelTableCellRenderer.getMouseListener());
        tablePanelList.add(pane);

        DataFetchWorker.DataFetchComponents<DataSource, List<RecentAttachmentDetails>> worker
                = new DataFetchWorker.DataFetchComponents<>(
                        attachmentsFetcher,
                        (result) -> pane.showDataFetchResult(result)
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
