/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.awt.Desktop;
import java.nio.file.Paths;
import java.util.List;
import javax.swing.JPanel;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.Date;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JOptionPane;
import javax.swing.JDialog;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import org.sleuthkit.autopsy.casemodule.StartupWindowProvider;
import java.awt.Cursor;
import java.io.IOException;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.experimental.autoingest.ReviewModeCaseManager.ReviewModeCaseManagerException;

/**
 * A panel that allows a user to open cases created by automated ingest.
 */
public final class ReviewModeCasePanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ReviewModeCasePanel.class.getName());
    private static final AutoIngestCase.LastAccessedDateDescendingComparator reverseDateModifiedComparator = new AutoIngestCase.LastAccessedDateDescendingComparator();
    private static final int CASE_COL_MIN_WIDTH = 30;
    private static final int CASE_COL_MAX_WIDTH = 2000;
    private static final int CASE_COL_PREFERRED_WIDTH = 300;
    private static final int TIME_COL_MIN_WIDTH = 40;
    private static final int TIME_COL_MAX_WIDTH = 250;
    private static final int TIME_COL_PREFERRED_WIDTH = 160;
    private static final int STATUS_COL_MIN_WIDTH = 55;
    private static final int STATUS_COL_MAX_WIDTH = 250;
    private static final int STATUS_COL_PREFERRED_WIDTH = 60;
    private static final int MILLISECONDS_TO_WAIT_BEFORE_STARTING = 500; // RJCTODO: Shorten name
    private static final int MILLISECONDS_TO_WAIT_BETWEEN_UPDATES = 30000; // RJCTODO: Shorten name
    private ScheduledThreadPoolExecutor casesTableRefreshExecutor;

    /*
     * The JTable table model for the cases table presented by this view is
     * defined by the following string, enum, and array.
     *
     * TODO (RC): Consider unifying this stuff in an enum as in
     * AutoIngestDashboard to make it less error prone.
     */
    private static final String CASE_HEADER = org.openide.util.NbBundle.getMessage(ReviewModeCasePanel.class, "ReviewModeCasePanel.CaseHeaderText");
    private static final String CREATEDTIME_HEADER = org.openide.util.NbBundle.getMessage(ReviewModeCasePanel.class, "ReviewModeCasePanel.CreatedTimeHeaderText");
    private static final String COMPLETEDTIME_HEADER = org.openide.util.NbBundle.getMessage(ReviewModeCasePanel.class, "ReviewModeCasePanel.LastAccessedTimeHeaderText");
    private static final String STATUS_ICON_HEADER = org.openide.util.NbBundle.getMessage(ReviewModeCasePanel.class, "ReviewModeCasePanel.StatusIconHeaderText");
    private static final String OUTPUT_FOLDER_HEADER = org.openide.util.NbBundle.getMessage(ReviewModeCasePanel.class, "ReviewModeCasePanel.OutputFolderHeaderText");

    enum COLUMN_HEADERS {

        CASE,
        CREATEDTIME,
        COMPLETEDTIME,
        STATUS_ICON,
        OUTPUTFOLDER // RJCTODO: Change name
    }
    private final String[] columnNames = {CASE_HEADER, CREATEDTIME_HEADER, COMPLETEDTIME_HEADER, STATUS_ICON_HEADER, OUTPUT_FOLDER_HEADER};
    private DefaultTableModel caseTableModel;
    private Path currentlySelectedCase = null;

    /**
     * Constructs a panel that allows a user to open cases created by automated
     * ingest.
     */
    public ReviewModeCasePanel(JDialog parent) {
        caseTableModel = new DefaultTableModel(columnNames, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        initComponents();

        /*
         * Configure the columns of the cases table.
         */
        TableColumn theColumn;
        theColumn = casesTable.getColumn(CASE_HEADER);
        theColumn.setCellRenderer(new GrayableCellRenderer());
        theColumn.setMinWidth(CASE_COL_MIN_WIDTH);
        theColumn.setMaxWidth(CASE_COL_MAX_WIDTH);
        theColumn.setPreferredWidth(CASE_COL_PREFERRED_WIDTH);
        theColumn.setWidth(CASE_COL_PREFERRED_WIDTH);

        theColumn = casesTable.getColumn(CREATEDTIME_HEADER);
        theColumn.setCellRenderer(new LongDateCellRenderer());
        theColumn.setMinWidth(TIME_COL_MIN_WIDTH);
        theColumn.setMaxWidth(TIME_COL_MAX_WIDTH);
        theColumn.setPreferredWidth(TIME_COL_PREFERRED_WIDTH);
        theColumn.setWidth(TIME_COL_PREFERRED_WIDTH);

        theColumn = casesTable.getColumn(COMPLETEDTIME_HEADER);
        theColumn.setCellRenderer(new LongDateCellRenderer());
        theColumn.setMinWidth(TIME_COL_MIN_WIDTH);
        theColumn.setMaxWidth(TIME_COL_MAX_WIDTH);
        theColumn.setPreferredWidth(TIME_COL_PREFERRED_WIDTH);
        theColumn.setWidth(TIME_COL_PREFERRED_WIDTH);

        theColumn = casesTable.getColumn(STATUS_ICON_HEADER);
        theColumn.setCellRenderer(new CaseStatusIconCellRenderer());
        theColumn.setMinWidth(STATUS_COL_MIN_WIDTH);
        theColumn.setMaxWidth(STATUS_COL_MAX_WIDTH);
        theColumn.setPreferredWidth(STATUS_COL_PREFERRED_WIDTH);
        theColumn.setWidth(STATUS_COL_PREFERRED_WIDTH);

        casesTable.removeColumn(casesTable.getColumn(OUTPUT_FOLDER_HEADER));

        /*
         * Listen for row selection changes and set button state for the current
         * selection.
         */
        casesTable.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            //Ignore extra messages.
            if (e.getValueIsAdjusting()) {
                return;
            }
            setButtons();
        });

        /*
         * Add a window state listener that starts and stops refreshing of the
         * cases table.
         */
        if (parent != null) {
            parent.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    stopCasesTableRefreshes();
                }

                @Override
                public void windowActivated(WindowEvent e) {
                    startCasesTableRefreshes();
                }

                @Override
                public void windowClosed(WindowEvent e) {
                    stopCasesTableRefreshes();
                }
            });
        }
    }

    /**
     * Start doing periodic refreshes of the cases table.
     */
    private void startCasesTableRefreshes() {
        if (null == casesTableRefreshExecutor) {
            casesTableRefreshExecutor = new ScheduledThreadPoolExecutor(1);
            this.casesTableRefreshExecutor.scheduleAtFixedRate(() -> {
                refreshCasesTable();
            }, MILLISECONDS_TO_WAIT_BEFORE_STARTING, MILLISECONDS_TO_WAIT_BETWEEN_UPDATES, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stop doing periodic refreshes of the cases table.
     */
    private void stopCasesTableRefreshes() {
        if (null != casesTableRefreshExecutor) {
            casesTableRefreshExecutor.shutdown();
        }
        this.casesTableRefreshExecutor = null;
    }

    /*
     * Updates the view presented by the panel.
     */
    public void updateView() {
        Thread thread = new Thread(() -> {
            refreshCasesTable();
        });
        thread.start();
    }

    /**
     * Gets the list of cases known to the review mode cases manager and
     * refreshes the cases table.
     */
    private void refreshCasesTable() {
        try {
            currentlySelectedCase = getSelectedCase();
            List<AutoIngestCase> theModel = ReviewModeCaseManager.getInstance().getCases();
            EventQueue.invokeLater(new CaseTableRefreshTask(theModel));
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unexpected exception in refreshCasesTable", ex); //NON-NLS
        }
    }

    /**
     * Gets the current selection in the cases table.
     *
     * @return A path representing the current selected case, null if there is
     *         no selection.
     */
    private Path getSelectedCase() {
        try {
            int selectedRow = casesTable.getSelectedRow();
            if (selectedRow >= 0 && selectedRow < casesTable.getRowCount()) {
                return Paths.get(caseTableModel.getValueAt(selectedRow, COLUMN_HEADERS.CASE.ordinal()).toString());
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    /**
     * Sets the current selection in the cases table.
     *
     * @param path The case folder path of the case to select.
     */
    private void setSelectedCase(Path path) {
        if (path != null) {
            try {
                for (int row = 0; row < casesTable.getRowCount(); ++row) {
                    Path temp = Paths.get(caseTableModel.getValueAt(row, COLUMN_HEADERS.CASE.ordinal()).toString());
                    if (temp.compareTo(path) == 0) { // found it
                        casesTable.setRowSelectionInterval(row, row);
                        return;
                    }
                }
            } catch (Exception ignored) {
                casesTable.clearSelection();
            }
        }
        casesTable.clearSelection();
    }

    /**
     * Enables/disables the Open and Show Log buttons based on the case selected
     * in the cases table.
     */
    private void setButtons() {
        boolean enabled = casesTable.getSelectedRow() >= 0 && casesTable.getSelectedRow() < casesTable.getRowCount();
        bnOpen.setEnabled(enabled);
        bnShowLog.setEnabled(enabled);
    }

    /**
     * Opens a case.
     *
     * @param caseMetadataFilePath The path to the case metadata file.
     */
    private void openCase(Path caseMetadataFilePath) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            ReviewModeCaseManager.getInstance().openCaseInEDT(caseMetadataFilePath);
            stopCasesTableRefreshes();
            StartupWindowProvider.getInstance().close();
        } catch (ReviewModeCaseManagerException ex) {
            logger.log(Level.SEVERE, String.format("Error while opening case with case metadata file path %s", caseMetadataFilePath), ex);
            /*
             * ReviewModeCaseManagerExceptions have user-friendly error
             * messages.
             */
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                    ex.getMessage(),
                    org.openide.util.NbBundle.getMessage(ReviewModeCasePanel.class, "ReviewModeCasePanel.cannotOpenCase"),
                    JOptionPane.ERROR_MESSAGE);

        } finally {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }

    /**
     * A task that refreshes the cases table using a list of auto ingest cases.
     */
    private class CaseTableRefreshTask implements Runnable {

        private final List<AutoIngestCase> cases;

        CaseTableRefreshTask(List<AutoIngestCase> cases) {
            setButtons();
            this.cases = cases;
        }

        /**
         * @inheritDoc
         */
        @Override
        public void run() {
            cases.sort(reverseDateModifiedComparator);
            caseTableModel.setRowCount(0);
            long now = new Date().getTime();
            for (AutoIngestCase autoIngestCase : cases) {
                if (passesTimeFilter(now, autoIngestCase.getLastAccessedDate().getTime())) {
                    caseTableModel.addRow(new Object[]{
                        autoIngestCase.getCaseName(),
                        autoIngestCase.getCreationDate(),
                        autoIngestCase.getLastAccessedDate(),
                        (AutoIngestCase.CaseStatus.OK != autoIngestCase.getStatus()),
                        autoIngestCase.getCaseDirectoryPath().toString()});
                }
            }
            setSelectedCase(currentlySelectedCase);
        }

        /**
         * Indicates whether or not a time satisfies a time filter defined by
         * this panel's time filter radio buttons.
         *
         * @param currentTime The current date and time in milliseconds from the
         *                    Unix epoch.
         * @param inputTime   The date and time to be tested as milliseconds
         *                    from the Unix epoch.
         */
        private boolean passesTimeFilter(long currentTime, long inputTime) {
            long numberOfUnits = 10;
            long multiplier = 1;
            if (rbAllCases.isSelected()) {
                return true;
            } else {
                if (rbMonths.isSelected()) {
                    multiplier = 31;
                } else {
                    if (rbWeeks.isSelected()) {
                        multiplier = 7;
                    } else {
                        if (rbDays.isSelected()) {
                            multiplier = 1;
                        }
                    }
                }
            }
            return ((currentTime - inputTime) / (1000 * 60 * 60 * 24)) < (numberOfUnits * multiplier);
        }

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        rbGroupHistoryLength = new javax.swing.ButtonGroup();
        bnOpen = new javax.swing.JButton();
        scrollPaneTable = new javax.swing.JScrollPane();
        casesTable = new javax.swing.JTable();
        bnRefresh = new javax.swing.JButton();
        panelFilter = new javax.swing.JPanel();
        rbAllCases = new javax.swing.JRadioButton();
        rbMonths = new javax.swing.JRadioButton();
        rbWeeks = new javax.swing.JRadioButton();
        rbDays = new javax.swing.JRadioButton();
        rbGroupLabel = new javax.swing.JLabel();
        bnShowLog = new javax.swing.JButton();

        setName("Completed Cases"); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnOpen, org.openide.util.NbBundle.getMessage(ReviewModeCasePanel.class, "ReviewModeCasePanel.bnOpen.text")); // NOI18N
        bnOpen.setEnabled(false);
        bnOpen.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnOpenActionPerformed(evt);
            }
        });

        casesTable.setAutoCreateRowSorter(true);
        casesTable.setModel(caseTableModel);
        casesTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        casesTable.setRowHeight(20);
        casesTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        casesTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                casesTableMouseClicked(evt);
            }
        });
        scrollPaneTable.setViewportView(casesTable);

        org.openide.awt.Mnemonics.setLocalizedText(bnRefresh, org.openide.util.NbBundle.getMessage(ReviewModeCasePanel.class, "ReviewModeCasePanel.bnRefresh.text")); // NOI18N
        bnRefresh.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnRefreshActionPerformed(evt);
            }
        });

        rbGroupHistoryLength.add(rbAllCases);
        rbAllCases.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(rbAllCases, org.openide.util.NbBundle.getMessage(ReviewModeCasePanel.class, "ReviewModeCasePanel.rbAllCases.text")); // NOI18N
        rbAllCases.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                rbAllCasesItemStateChanged(evt);
            }
        });

        rbGroupHistoryLength.add(rbMonths);
        org.openide.awt.Mnemonics.setLocalizedText(rbMonths, org.openide.util.NbBundle.getMessage(ReviewModeCasePanel.class, "ReviewModeCasePanel.rbMonths.text")); // NOI18N
        rbMonths.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                rbMonthsItemStateChanged(evt);
            }
        });

        rbGroupHistoryLength.add(rbWeeks);
        org.openide.awt.Mnemonics.setLocalizedText(rbWeeks, org.openide.util.NbBundle.getMessage(ReviewModeCasePanel.class, "ReviewModeCasePanel.rbWeeks.text")); // NOI18N
        rbWeeks.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                rbWeeksItemStateChanged(evt);
            }
        });

        rbGroupHistoryLength.add(rbDays);
        org.openide.awt.Mnemonics.setLocalizedText(rbDays, org.openide.util.NbBundle.getMessage(ReviewModeCasePanel.class, "ReviewModeCasePanel.rbDays.text")); // NOI18N
        rbDays.setName(""); // NOI18N
        rbDays.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                rbDaysItemStateChanged(evt);
            }
        });

        rbGroupLabel.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(rbGroupLabel, org.openide.util.NbBundle.getMessage(ReviewModeCasePanel.class, "ReviewModeCasePanel.rbGroupLabel.text")); // NOI18N

        javax.swing.GroupLayout panelFilterLayout = new javax.swing.GroupLayout(panelFilter);
        panelFilter.setLayout(panelFilterLayout);
        panelFilterLayout.setHorizontalGroup(
            panelFilterLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelFilterLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panelFilterLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(rbGroupLabel)
                    .addComponent(rbAllCases)
                    .addComponent(rbMonths)
                    .addComponent(rbWeeks)
                    .addComponent(rbDays))
                .addContainerGap(34, Short.MAX_VALUE))
        );
        panelFilterLayout.setVerticalGroup(
            panelFilterLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panelFilterLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(rbGroupLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(rbDays)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(rbWeeks)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(rbMonths)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(rbAllCases)
                .addContainerGap())
        );

        org.openide.awt.Mnemonics.setLocalizedText(bnShowLog, org.openide.util.NbBundle.getMessage(ReviewModeCasePanel.class, "ReviewModeCasePanel.bnShowLog.text")); // NOI18N
        bnShowLog.setToolTipText(org.openide.util.NbBundle.getMessage(ReviewModeCasePanel.class, "ReviewModeCasePanel.bnShowLog.toolTipText")); // NOI18N
        bnShowLog.setEnabled(false);
        bnShowLog.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnShowLogActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(13, 13, 13)
                        .addComponent(bnOpen, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(bnRefresh)
                        .addGap(18, 18, 18)
                        .addComponent(bnShowLog)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(panelFilter, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(20, 20, 20))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(scrollPaneTable, javax.swing.GroupLayout.DEFAULT_SIZE, 1007, Short.MAX_VALUE)
                        .addContainerGap())))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(43, 43, 43)
                .addComponent(scrollPaneTable, javax.swing.GroupLayout.PREFERRED_SIZE, 450, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(panelFilter, javax.swing.GroupLayout.PREFERRED_SIZE, 130, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap())
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(bnOpen)
                            .addComponent(bnRefresh)
                            .addComponent(bnShowLog))
                        .addGap(36, 36, 36))))
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Open button action
     *
     * @param evt -- The event that caused this to be called
     */
    private void bnOpenActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnOpenActionPerformed
        Path caseMetadataFilePath = Paths.get((String) caseTableModel.getValueAt(casesTable.getSelectedRow(),
                COLUMN_HEADERS.OUTPUTFOLDER.ordinal()),
                caseTableModel.getValueAt(casesTable.getSelectedRow(), COLUMN_HEADERS.CASE.ordinal()) + CaseMetadata.getFileExtension());
        openCase(caseMetadataFilePath);
    }//GEN-LAST:event_bnOpenActionPerformed

    /**
     * Refresh button action
     *
     * @param evt -- The event that caused this to be called
     */
    private void bnRefreshActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_bnRefreshActionPerformed
    {//GEN-HEADEREND:event_bnRefreshActionPerformed
        updateView();
    }//GEN-LAST:event_bnRefreshActionPerformed

    private void rbDaysItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_rbDaysItemStateChanged
        if (rbDays.isSelected()) {
            updateView();
        }
    }//GEN-LAST:event_rbDaysItemStateChanged

    private void rbAllCasesItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_rbAllCasesItemStateChanged
        if (rbAllCases.isSelected()) {
            updateView();
        }
    }//GEN-LAST:event_rbAllCasesItemStateChanged

    private void rbMonthsItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_rbMonthsItemStateChanged
        if (rbMonths.isSelected()) {
            updateView();
        }
    }//GEN-LAST:event_rbMonthsItemStateChanged

    private void rbWeeksItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_rbWeeksItemStateChanged
        if (rbWeeks.isSelected()) {
            updateView();
        }
    }//GEN-LAST:event_rbWeeksItemStateChanged

    private void bnShowLogActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnShowLogActionPerformed
        int selectedRow = casesTable.getSelectedRow();
        int rowCount = casesTable.getRowCount();
        if (selectedRow >= 0 && selectedRow < rowCount) {
            String thePath = (String) caseTableModel.getValueAt(selectedRow, COLUMN_HEADERS.OUTPUTFOLDER.ordinal());
            Path pathToLog = AutoIngestJobLogger.getLogPath(Paths.get(thePath));
            try {
                if (pathToLog.toFile().exists()) {
                    Desktop.getDesktop().edit(pathToLog.toFile());
                } else {
                    JOptionPane.showMessageDialog(this, org.openide.util.NbBundle.getMessage(ReviewModeCasePanel.class, "DisplayLogDialog.cannotFindLog"),
                        org.openide.util.NbBundle.getMessage(ReviewModeCasePanel.class, "DisplayLogDialog.unableToShowLogFile"), JOptionPane.ERROR_MESSAGE);
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error attempting to open case auto ingest log file %s", pathToLog), ex);
                JOptionPane.showMessageDialog(this,
                        org.openide.util.NbBundle.getMessage(ReviewModeCasePanel.class, "DisplayLogDialog.cannotOpenLog"),
                        org.openide.util.NbBundle.getMessage(ReviewModeCasePanel.class, "DisplayLogDialog.unableToShowLogFile"),
                        JOptionPane.PLAIN_MESSAGE);
            }
        }
    }//GEN-LAST:event_bnShowLogActionPerformed

    private void casesTableMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_casesTableMouseClicked
        if (evt.getClickCount() == 2) {
            Path caseMetadataFilePath = Paths.get((String) caseTableModel.getValueAt(casesTable.getSelectedRow(),
                    COLUMN_HEADERS.OUTPUTFOLDER.ordinal()),
                    caseTableModel.getValueAt(casesTable.getSelectedRow(), COLUMN_HEADERS.CASE.ordinal()) + CaseMetadata.getFileExtension());
            openCase(caseMetadataFilePath);
        }
    }//GEN-LAST:event_casesTableMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnOpen;
    private javax.swing.JButton bnRefresh;
    private javax.swing.JButton bnShowLog;
    private javax.swing.JTable casesTable;
    private javax.swing.JPanel panelFilter;
    private javax.swing.JRadioButton rbAllCases;
    private javax.swing.JRadioButton rbDays;
    private javax.swing.ButtonGroup rbGroupHistoryLength;
    private javax.swing.JLabel rbGroupLabel;
    private javax.swing.JRadioButton rbMonths;
    private javax.swing.JRadioButton rbWeeks;
    private javax.swing.JScrollPane scrollPaneTable;
    // End of variables declaration//GEN-END:variables

}
