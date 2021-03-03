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

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.IngestJobInfoPanel;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelExport;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelExport.ExcelExportException;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelExport.ExcelSheetExport;
import org.sleuthkit.autopsy.progress.ModalDialogProgressIndicator;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
import org.sleuthkit.datamodel.DataSource;

/**
 * A tabbed pane showing the summary of a data source including tabs of:
 * DataSourceSummaryCountsPanel, ContainerPanel, and IngestJobInfoPanel.
 */
@Messages({
    "DataSourceSummaryTabbedPane_typesTab_title=Types",
    "DataSourceSummaryTabbedPane_detailsTab_title=Container",
    "DataSourceSummaryTabbedPane_userActivityTab_title=User Activity",
    "DataSourceSummaryTabbedPane_ingestHistoryTab_title=Ingest History",
    "DataSourceSummaryTabbedPane_recentFileTab_title=Recent Files",
    "DataSourceSummaryTabbedPane_pastCasesTab_title=Past Cases",
    "DataSourceSummaryTabbedPane_analysisTab_title=Analysis",
    "DataSourceSummaryTabbedPane_geolocationTab_title=Geolocation",
    "DataSourceSummaryTabbedPane_timelineTab_title=Timeline"
})
public class DataSourceSummaryTabbedPane extends javax.swing.JPanel {

    /**
     * Records of tab information (i.e. title, component, function to call on
     * new data source).
     */
    private class DataSourceTab {

        private final String tabTitle;
        private final Component component;
        private final Consumer<DataSource> onDataSource;
        private final Function<DataSource, List<ExcelSheetExport>> excelExporter;
        private final Runnable onClose;

        /**
         * Main constructor.
         *
         * @param tabTitle The title of the tab.
         * @param panel The component to be displayed in the tab.
         * @param notifyParentClose Notifies parent to trigger a close.
         */
        DataSourceTab(String tabTitle, BaseDataSourceSummaryPanel panel) {
            this(tabTitle, panel, panel::setDataSource, panel::getExports, panel::close);
            panel.setParentCloseListener(() -> notifyParentClose());
        }

        /**
         * Main constructor.
         *
         * @param tabTitle The title of the tab.
         * @param component The component to be displayed.
         * @param onDataSource The function to be called on a new data source.
         * @param excelExporter The function that creates excel exports for a
         * particular data source for this tab. Can be null for no exports.
         * @param onClose Called to cleanup resources when closing tabs. Can be
         * null for no-op.
         */
        DataSourceTab(String tabTitle, Component component, Consumer<DataSource> onDataSource,
                Function<DataSource, List<ExcelSheetExport>> excelExporter, Runnable onClose) {
            this.tabTitle = tabTitle;
            this.component = component;
            this.onDataSource = onDataSource;
            this.excelExporter = excelExporter;
            this.onClose = onClose;
        }

        /**
         * @return The title for the tab.
         */
        String getTabTitle() {
            return tabTitle;
        }

        /**
         * @return The component to display in the tab.
         */
        Component getComponent() {
            return component;
        }

        /**
         * @return The function to be called on new data source.
         */
        Consumer<DataSource> getOnDataSource() {
            return onDataSource;
        }

        /**
         * @return The function that creates excel exports for a particular data
         * source for this tab.
         */
        Function<DataSource, List<ExcelSheetExport>> getExcelExporter() {
            return excelExporter;
        }

        /**
         * @return The action for closing resources in the tab.
         */
        public Runnable getOnClose() {
            return onClose;
        }
    }

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DataSourceSummaryTabbedPane.class.getName());

    // needs to match value provided for card layout in designed
    private static final String TABBED_PANE = "tabbedPane";
    private static final String NO_DATASOURCE_PANE = "noDataSourcePane";

    private Runnable notifyParentClose = null;
    private final IngestJobInfoPanel ingestHistoryPanel = new IngestJobInfoPanel();

    private final List<DataSourceTab> tabs = Arrays.asList(
            new DataSourceTab(Bundle.DataSourceSummaryTabbedPane_typesTab_title(), new TypesPanel()),
            new DataSourceTab(Bundle.DataSourceSummaryTabbedPane_userActivityTab_title(), new UserActivityPanel()),
            new DataSourceTab(Bundle.DataSourceSummaryTabbedPane_analysisTab_title(), new AnalysisPanel()),
            new DataSourceTab(Bundle.DataSourceSummaryTabbedPane_recentFileTab_title(), new RecentFilesPanel()),
            new DataSourceTab(Bundle.DataSourceSummaryTabbedPane_pastCasesTab_title(), new PastCasesPanel()),
            new DataSourceTab(Bundle.DataSourceSummaryTabbedPane_geolocationTab_title(), new GeolocationPanel()),
            new DataSourceTab(Bundle.DataSourceSummaryTabbedPane_timelineTab_title(), new TimelinePanel()),
            // do nothing on closing 
            new DataSourceTab(
                    Bundle.DataSourceSummaryTabbedPane_ingestHistoryTab_title(),
                    ingestHistoryPanel,
                    ingestHistoryPanel::setDataSource,
                    null,
                    null),
            new DataSourceTab(Bundle.DataSourceSummaryTabbedPane_detailsTab_title(), new ContainerPanel())
    );

    private final ExcelExport excelExport = ExcelExport.getInstance();

    private DataSource dataSource = null;
    private CardLayout cardLayout;

    /**
     * On case close, clear the currently held data source summary node.
     */
    private final PropertyChangeListener caseEventsListener = (evt) -> {
        if (evt.getPropertyName().equals(Case.Events.CURRENT_CASE.toString()) && evt.getNewValue() == null) {
            setDataSource(null);
        }
    };

    /**
     * Creates new form TabPane
     */
    public DataSourceSummaryTabbedPane() {
        initComponents();
        postInit();
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), caseEventsListener);
    }

    /**
     * Sends event that parent should close.
     */
    private void notifyParentClose() {
        if (notifyParentClose != null) {
            notifyParentClose.run();
        }
    }

    /**
     * Sets the listener for parent close events.
     *
     * @param parentCloseAction The observer.
     */
    void setParentCloseListener(Runnable parentCloseAction) {
        notifyParentClose = parentCloseAction;
    }

    /**
     * Method called right after initComponents during initialization.
     */
    private void postInit() {
        // get the card layout
        cardLayout = (CardLayout) this.getLayout();

        // set up the tabs
        for (DataSourceTab tab : tabs) {
            tabbedPane.addTab(tab.getTabTitle(), tab.getComponent());
        }

        // set this to no datasource initially
        cardLayout.show(this, NO_DATASOURCE_PANE);
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
     * Sets datasource to visualize in the tabbed panel.
     *
     * @param dataSource The datasource to use in this panel.
     */
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;

        for (DataSourceTab tab : tabs) {
            tab.getOnDataSource().accept(dataSource);
        }

        if (this.dataSource == null) {
            cardLayout.show(this, NO_DATASOURCE_PANE);
        } else {
            cardLayout.show(this, TABBED_PANE);
        }
    }

    /**
     * Handle close events on each tab.
     */
    public void close() {
        for (DataSourceTab tab : tabs) {
            if (tab.getOnClose() != null) {
                tab.getOnClose().run();
            }
        }

        Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), caseEventsListener);
    }

    @Messages({
        "DataSourceSummaryTabbedPane_promptAndExportToXLSX_fileExistsTitle=File Already Exists",
        "# {0} - path",
        "DataSourceSummaryTabbedPane_promptAndExportToXLSX_fileExistsMessage=File at {0} already exists.",})
    private void promptAndExportToXLSX() {
        DataSource ds = this.dataSource;
        if (ds == null) {
            return;
        }
        String expectedExtension = "xlsx";
        JFileChooser fc = new JFileChooser();
        FileNameExtensionFilter xmlFilter = new FileNameExtensionFilter("XLSX file (*.xlsx)", expectedExtension);
        fc.addChoosableFileFilter(xmlFilter);
        fc.setFileFilter(xmlFilter);
        DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss");
        fc.setSelectedFile(new File(String.format("%s-%s.xlsx", ds.getName() == null ? "" : FileUtil.escapeFileName(ds.getName()), dateFormat.format(new Date()))));

        int returnVal = fc.showSaveDialog(this);

        if (returnVal != JFileChooser.APPROVE_OPTION || fc.getSelectedFile() == null) {
            return;
        }

        File file = fc.getSelectedFile();
        if (!file.getAbsolutePath().endsWith("." + expectedExtension)) {
            file = new File(file.getAbsolutePath() + "." + expectedExtension);
        }

        if (file.exists()) {
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                    Bundle.DataSourceSummaryTabbedPane_promptAndExportToXLSX_fileExistsMessage(file.getAbsolutePath()),
                    Bundle.DataSourceSummaryTabbedPane_promptAndExportToXLSX_fileExistsTitle(),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        runXLSXExport(ds, file);
    }

    private class CancelExportListener implements ActionListener {

        private SwingWorker<Boolean, Void> worker = null;

        @Override
        public void actionPerformed(ActionEvent e) {
            if (worker != null && !worker.isCancelled() && !worker.isDone()) {
                worker.cancel(true);
            }
        }

        SwingWorker<Boolean, Void> getWorker() {
            return worker;
        }

        void setWorker(SwingWorker<Boolean, Void> worker) {
            this.worker = worker;
        }
    }

    @Messages({
        "# {0} - dataSource",
        "DataSourceSummaryTabbedPane_runXLSXExport_progressTitle=Exporting {0} to XLSX",
        "DataSourceSummaryTabbedPane_runXLSXExport_progressCancelTitle=Cancel",
        "DataSourceSummaryTabbedPane_runXLSXExport_progressCancelActionTitle=Cancelling..."
    })
    private void runXLSXExport(DataSource dataSource, File path) {

        CancelExportListener cancelButtonListener = new CancelExportListener();

        ProgressIndicator progressIndicator = new ModalDialogProgressIndicator(
                WindowManager.getDefault().getMainWindow(),
                Bundle.DataSourceSummaryTabbedPane_runXLSXExport_progressTitle(dataSource.getName()),
                new String[]{Bundle.DataSourceSummaryTabbedPane_runXLSXExport_progressCancelTitle()},
                Bundle.DataSourceSummaryTabbedPane_runXLSXExport_progressCancelTitle(),
                cancelButtonListener
        );

        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                exportToXLSX(progressIndicator, dataSource, path);
                return true;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (ExecutionException ex) {
                    logger.log(Level.WARNING, "Error while trying to export data source summary to xlsx.", ex);
                } catch (InterruptedException | CancellationException ex) {
                    // no op on cancellation
                } finally {
                    progressIndicator.finish();
                }
            }
        };

        cancelButtonListener.setWorker(worker);
        worker.execute();
    }

    @Messages({
        "DataSourceSummaryTabbedPane_exportToXLSX_beginExport=Beginning Export...",
        "# {0} - tabName",
        "DataSourceSummaryTabbedPane_exportToXLSX_gatheringTabData=Fetching Data for {0} Tab...",
        "DataSourceSummaryTabbedPane_exportToXLSX_writingToFile=Writing to File...",})

    private void exportToXLSX(ProgressIndicator progressIndicator, DataSource dataSource, File path)
            throws InterruptedException, IOException, ExcelExportException {

        int exportWeight = 3;
        int totalWeight = tabs.size() + exportWeight;
        progressIndicator.start(Bundle.DataSourceSummaryTabbedPane_exportToXLSX_beginExport(), totalWeight);
        List<ExcelSheetExport> sheetExports = new ArrayList<>();
        for (int i = 0; i < tabs.size(); i++) {
            if (Thread.interrupted()) {
                throw new InterruptedException("Export has been cancelled.");
            }

            DataSourceTab tab = tabs.get(i);
            progressIndicator.progress(Bundle.DataSourceSummaryTabbedPane_exportToXLSX_gatheringTabData(tab == null ? "" : tab.getTabTitle()), i);
            if (tab.getExcelExporter() != null) {
                List<ExcelSheetExport> exports = tab.getExcelExporter().apply(dataSource);
                if (exports != null) {
                    sheetExports.addAll(exports);
                }
            }
        }

        if (Thread.interrupted()) {
            throw new InterruptedException("Export has been cancelled.");
        }

        progressIndicator.progress(Bundle.DataSourceSummaryTabbedPane_exportToXLSX_writingToFile(), tabs.size());
        excelExport.writeExcel(sheetExports, path);

        progressIndicator.finish();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.JPanel noDataSourcePane = new javax.swing.JPanel();
        javax.swing.JLabel noDataSourceLabel = new javax.swing.JLabel();
        tabContentPane = new javax.swing.JPanel();
        tabbedPane = new javax.swing.JTabbedPane();
        actionsPane = new javax.swing.JPanel();
        exportXLSXButton = new javax.swing.JButton();

        setLayout(new java.awt.CardLayout());

        noDataSourcePane.setLayout(new java.awt.BorderLayout());

        noDataSourceLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        org.openide.awt.Mnemonics.setLocalizedText(noDataSourceLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryTabbedPane.class, "DataSourceSummaryTabbedPane.noDataSourceLabel.text")); // NOI18N
        noDataSourcePane.add(noDataSourceLabel, java.awt.BorderLayout.CENTER);

        add(noDataSourcePane, "noDataSourcePane");

        tabContentPane.setLayout(new java.awt.BorderLayout());
        tabContentPane.add(tabbedPane, java.awt.BorderLayout.CENTER);

        actionsPane.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 2, 2, 2));
        actionsPane.setLayout(new java.awt.BorderLayout());

        org.openide.awt.Mnemonics.setLocalizedText(exportXLSXButton, org.openide.util.NbBundle.getMessage(DataSourceSummaryTabbedPane.class, "DataSourceSummaryTabbedPane.exportXLSXButton.text")); // NOI18N
        exportXLSXButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportXLSXButtonActionPerformed(evt);
            }
        });
        actionsPane.add(exportXLSXButton, java.awt.BorderLayout.WEST);

        tabContentPane.add(actionsPane, java.awt.BorderLayout.SOUTH);

        add(tabContentPane, "tabbedPane");
    }// </editor-fold>//GEN-END:initComponents

    private void exportXLSXButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportXLSXButtonActionPerformed
        promptAndExportToXLSX();
    }//GEN-LAST:event_exportXLSXButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel actionsPane;
    private javax.swing.JButton exportXLSXButton;
    private javax.swing.JPanel tabContentPane;
    private javax.swing.JTabbedPane tabbedPane;
    // End of variables declaration//GEN-END:variables
}
