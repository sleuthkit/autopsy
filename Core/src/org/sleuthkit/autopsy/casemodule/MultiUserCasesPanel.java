/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.awt.Cursor;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SortOrder;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.MultiUserCaseManager.MultiUserCase;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.guiutils.LongDateCellRenderer;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.guiutils.GrayableCellRenderer;
import org.sleuthkit.autopsy.guiutils.StatusIconCellRenderer;

/**
 * A panel that allows a user to open cases created by auto ingest.
 */
@NbBundle.Messages({"MultiUSerCasesPanel.caseListLoading.message=Retrieving list of cases, please wait..."})
final class MultiUserCasesPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(MultiUserCasesPanel.class.getName());
    private static final String LOG_FILE_NAME = "auto_ingest_log.txt";
    private static final int CASE_COL_MIN_WIDTH = 30;
    private static final int CASE_COL_MAX_WIDTH = 2000;
    private static final int CASE_COL_PREFERRED_WIDTH = 300;
    private static final int TIME_COL_MIN_WIDTH = 40;
    private static final int TIME_COL_MAX_WIDTH = 250;
    private static final int TIME_COL_PREFERRED_WIDTH = 160;
    private static final int STATUS_COL_MIN_WIDTH = 55;
    private static final int STATUS_COL_MAX_WIDTH = 250;
    private static final int STATUS_COL_PREFERRED_WIDTH = 60;
    private static final String CASES_POPULATING_MESSAGE = NbBundle.getMessage(MultiUserCasesPanel.class, "MultiUSerCasesPanel.caseListLoading.message");

    /*
     * The JTable table model for the cases table presented by this view is
     * defined by the following string, enum, and array.
     *
     * TODO (RC): Consider unifying this stuff in an enum as in
     * AutoIngestDashboard to make it less error prone.
     */
    private static final String CASE_HEADER = NbBundle.getMessage(MultiUserCasesPanel.class, "ReviewModeCasePanel.CaseHeaderText");
    private static final String CREATEDTIME_HEADER = NbBundle.getMessage(MultiUserCasesPanel.class, "ReviewModeCasePanel.CreatedTimeHeaderText");
    private static final String COMPLETEDTIME_HEADER = NbBundle.getMessage(MultiUserCasesPanel.class, "ReviewModeCasePanel.LastAccessedTimeHeaderText");
    private static final String STATUS_ICON_HEADER = NbBundle.getMessage(MultiUserCasesPanel.class, "ReviewModeCasePanel.StatusIconHeaderText");
    private static final String OUTPUT_FOLDER_HEADER = NbBundle.getMessage(MultiUserCasesPanel.class, "ReviewModeCasePanel.OutputFolderHeaderText");
    private static final String METADATA_FILE_HEADER = NbBundle.getMessage(MultiUserCasesPanel.class, "ReviewModeCasePanel.MetadataFileHeaderText");

    enum COLUMN_HEADERS {

        CASE,
        CREATEDTIME,
        COMPLETEDTIME,
        STATUS_ICON,
        OUTPUTFOLDER,
        METADATA_FILE
    }
    private final String[] columnNames = {CASE_HEADER, CREATEDTIME_HEADER, COMPLETEDTIME_HEADER, STATUS_ICON_HEADER, OUTPUT_FOLDER_HEADER, METADATA_FILE_HEADER};
    private DefaultTableModel caseTableModel;
    private JDialog parentDialog;
    private LoadTableWorker tableWorker;

    /**
     * Constructs a panel that allows a user to open cases created by automated
     * ingest.
     */
    MultiUserCasesPanel(JDialog parentDialog) {
        this.parentDialog = parentDialog;
        caseTableModel = new DefaultTableModel(columnNames, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int col) {
                if (this.getColumnName(col).equals(CREATEDTIME_HEADER) || this.getColumnName(col).equals(COMPLETEDTIME_HEADER)) {
                    return Date.class;
                } else {
                    return super.getColumnClass(col);
                }
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
        theColumn.setCellRenderer(new StatusIconCellRenderer());
        theColumn.setMinWidth(STATUS_COL_MIN_WIDTH);
        theColumn.setMaxWidth(STATUS_COL_MAX_WIDTH);
        theColumn.setPreferredWidth(STATUS_COL_PREFERRED_WIDTH);
        theColumn.setWidth(STATUS_COL_PREFERRED_WIDTH);

        casesTable.removeColumn(casesTable.getColumn(OUTPUT_FOLDER_HEADER));
        casesTable.removeColumn(casesTable.getColumn(METADATA_FILE_HEADER));
        casesTable.setRowSorter(new RowSorter<>(caseTableModel));
        casesTable.getRowSorter().toggleSortOrder(casesTable.getColumn(COMPLETEDTIME_HEADER).getModelIndex());
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
    }

    /**
     * Gets the list of cases known to the review mode cases manager and
     * refreshes the cases table.
     */
    void refresh() {
        if (tableWorker == null || tableWorker.isDone()) {
            //create a new TableWorker to and execute it in a background thread if one is not currently working
            currentlySelectedCase = getSelectedCase();
            //set the table to display text informing the user that the list is being retreived and disable case selection
            caseTableModel.setRowCount(0);
            casesTable.setRowSelectionAllowed(false);
            caseTableModel.addRow(new Object[]{CASES_POPULATING_MESSAGE, null, null, null, "", ""});
            tableWorker = new LoadTableWorker();
            tableWorker.execute();
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
                return Paths.get(caseTableModel.getValueAt(casesTable.convertRowIndexToModel(selectedRow), COLUMN_HEADERS.CASE.ordinal()).toString());
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
                    Path temp = Paths.get(caseTableModel.getValueAt(casesTable.convertRowIndexToModel(row), COLUMN_HEADERS.CASE.ordinal()).toString());
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
        boolean openEnabled = casesTable.getRowSelectionAllowed() && casesTable.getSelectedRow() >= 0 && casesTable.getSelectedRow() < casesTable.getRowCount();
        bnOpen.setEnabled(openEnabled);

        Path pathToLog = getSelectedCaseLogFilePath();
        boolean showLogEnabled = openEnabled && pathToLog != null && pathToLog.toFile().exists();
        bnShowLog.setEnabled(showLogEnabled);
    }

    /**
     * Retrieves the log file path for the selected case in the cases table.
     *
     * @return The case log path.
     */
    private Path getSelectedCaseLogFilePath() {
        Path retValue = null;

        int selectedRow = casesTable.getSelectedRow();
        int rowCount = casesTable.getRowCount();
        if (selectedRow >= 0 && selectedRow < rowCount) {
            String caseDirectory = (String) caseTableModel.getValueAt(casesTable.convertRowIndexToModel(selectedRow), COLUMN_HEADERS.OUTPUTFOLDER.ordinal());
            retValue = Paths.get(caseDirectory, LOG_FILE_NAME);
        }

        return retValue;
    }

    /**
     * Open a case.
     *
     * @param caseMetadataFilePath The path to the case metadata file.
     */
    private void openCase(Path caseMetadataFilePath) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            StartupWindowProvider.getInstance().close();
            if (parentDialog != null) {
                parentDialog.setVisible(false);
            }
            MultiUserCaseManager.getInstance().openCase(caseMetadataFilePath);
        } catch (CaseActionException | MultiUserCaseManager.MultiUserCaseManagerException ex) {
            if (null != ex.getCause() && !(ex.getCause() instanceof CaseActionCancelledException)) {
                LOGGER.log(Level.SEVERE, String.format("Error opening case with metadata file path %s", caseMetadataFilePath), ex); //NON-NLS
                MessageNotifyUtil.Message.error(ex.getCause().getLocalizedMessage());
            }
            StartupWindowProvider.getInstance().open();
        } finally {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }

    /**
     * Indicates whether or not a time satisfies a time filter defined by this
     * panel's time filter radio buttons.
     *
     * @param currentTime The current date and time in milliseconds from the
     *                    Unix epoch.
     * @param inputTime   The date and time to be tested as milliseconds from
     *                    the Unix epoch.
     */
    private boolean passesTimeFilter(long currentTime, long inputTime) {
        long numberOfUnits = 10;
        long multiplier = 1;
        if (rbAllCases.isSelected()) {
            return true;
        } else if (rbMonths.isSelected()) {
            multiplier = 31;
        } else if (rbWeeks.isSelected()) {
            multiplier = 7;
        } else if (rbDays.isSelected()) {
            multiplier = 1;
        }
        return ((currentTime - inputTime) / (1000 * 60 * 60 * 24)) < (numberOfUnits * multiplier);
    }

    /**
     * RowSorter which makes columns whose type is Date to be sorted first in
     * Descending order then in Ascending order
     */
    private static class RowSorter<M extends DefaultTableModel> extends TableRowSorter<M> {

        RowSorter(M tModel) {
            super(tModel);
        }

        @Override
        public void toggleSortOrder(int column) {
            if (!this.getModel().getColumnClass(column).equals(Date.class)) {
                super.toggleSortOrder(column);  //if it isn't a date column perform the regular sorting
            } else {
                ArrayList<RowSorter.SortKey> sortKeys = new ArrayList<>(getSortKeys());
                if (sortKeys.isEmpty() || sortKeys.get(0).getColumn() != column) {  //sort descending
                    sortKeys.add(0, new RowSorter.SortKey(column, SortOrder.DESCENDING));
                } else if (sortKeys.get(0).getSortOrder() == SortOrder.ASCENDING) {
                    sortKeys.removeIf(key -> key.getColumn() == column);
                    sortKeys.add(0, new RowSorter.SortKey(column, SortOrder.DESCENDING));
                } else {
                    sortKeys.removeIf(key -> key.getColumn() == column);
                    sortKeys.add(0, new RowSorter.SortKey(column, SortOrder.ASCENDING));
                }
                setSortKeys(sortKeys);
            }
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
        bnShowLog = new javax.swing.JButton();
        rbDays = new javax.swing.JRadioButton();
        rbWeeks = new javax.swing.JRadioButton();
        rbMonths = new javax.swing.JRadioButton();
        rbGroupLabel = new javax.swing.JLabel();
        bnOpenSingleUserCase = new javax.swing.JButton();

        setName("Completed Cases"); // NOI18N
        setPreferredSize(new java.awt.Dimension(960, 485));

        org.openide.awt.Mnemonics.setLocalizedText(bnOpen, org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "MultiUserCasesPanel.bnOpen.text")); // NOI18N
        bnOpen.setEnabled(false);
        bnOpen.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnOpenActionPerformed(evt);
            }
        });

        casesTable.setModel(caseTableModel);
        casesTable.setRowHeight(20);
        casesTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        casesTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                casesTableMouseClicked(evt);
            }
        });
        scrollPaneTable.setViewportView(casesTable);

        org.openide.awt.Mnemonics.setLocalizedText(bnRefresh, org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "MultiUserCasesPanel.bnRefresh.text")); // NOI18N
        bnRefresh.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnRefreshActionPerformed(evt);
            }
        });

        rbGroupHistoryLength.add(rbAllCases);
        rbAllCases.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(rbAllCases, org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "MultiUserCasesPanel.rbAllCases.text")); // NOI18N
        rbAllCases.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                rbAllCasesItemStateChanged(evt);
            }
        });

        javax.swing.GroupLayout panelFilterLayout = new javax.swing.GroupLayout(panelFilter);
        panelFilter.setLayout(panelFilterLayout);
        panelFilterLayout.setHorizontalGroup(
            panelFilterLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelFilterLayout.createSequentialGroup()
                .addComponent(rbAllCases)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        panelFilterLayout.setVerticalGroup(
            panelFilterLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panelFilterLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(rbAllCases))
        );

        org.openide.awt.Mnemonics.setLocalizedText(bnShowLog, org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "MultiUserCasesPanel.bnShowLog.text")); // NOI18N
        bnShowLog.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "MultiUserCasesPanel.bnShowLog.toolTipText")); // NOI18N
        bnShowLog.setEnabled(false);
        bnShowLog.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnShowLogActionPerformed(evt);
            }
        });

        rbGroupHistoryLength.add(rbDays);
        org.openide.awt.Mnemonics.setLocalizedText(rbDays, org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "MultiUserCasesPanel.rbDays.text")); // NOI18N
        rbDays.setName(""); // NOI18N
        rbDays.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                rbDaysItemStateChanged(evt);
            }
        });

        rbGroupHistoryLength.add(rbWeeks);
        org.openide.awt.Mnemonics.setLocalizedText(rbWeeks, org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "MultiUserCasesPanel.rbWeeks.text")); // NOI18N
        rbWeeks.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                rbWeeksItemStateChanged(evt);
            }
        });

        rbGroupHistoryLength.add(rbMonths);
        org.openide.awt.Mnemonics.setLocalizedText(rbMonths, org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "MultiUserCasesPanel.rbMonths.text")); // NOI18N
        rbMonths.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                rbMonthsItemStateChanged(evt);
            }
        });

        rbGroupLabel.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(rbGroupLabel, org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "MultiUserCasesPanel.rbGroupLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnOpenSingleUserCase, org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "MultiUserCasesPanel.bnOpenSingleUserCase.text")); // NOI18N
        bnOpenSingleUserCase.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnOpenSingleUserCaseActionPerformed(evt);
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
                        .addGap(4, 4, 4)
                        .addComponent(bnOpen, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bnOpenSingleUserCase)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bnShowLog)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 13, Short.MAX_VALUE)
                        .addComponent(rbGroupLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(rbDays)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(rbWeeks)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(rbMonths)
                        .addGap(0, 0, 0)
                        .addComponent(panelFilter, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(14, 14, 14)
                        .addComponent(bnRefresh))
                    .addComponent(scrollPaneTable))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(scrollPaneTable, javax.swing.GroupLayout.PREFERRED_SIZE, 450, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(bnOpen)
                        .addComponent(bnOpenSingleUserCase)
                        .addComponent(bnShowLog))
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(rbDays)
                            .addComponent(rbWeeks)
                            .addComponent(rbMonths)
                            .addComponent(rbGroupLabel))
                        .addComponent(panelFilter, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(bnRefresh, javax.swing.GroupLayout.Alignment.TRAILING))
                .addGap(0, 0, 0))
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Open button action
     *
     * @param evt -- The event that caused this to be called
     */
    private void bnOpenActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnOpenActionPerformed
        int modelRow = casesTable.convertRowIndexToModel(casesTable.getSelectedRow());
        String caseDirectory = (String) caseTableModel.getValueAt(modelRow, COLUMN_HEADERS.OUTPUTFOLDER.ordinal());
        Path caseMetadataFilePath = Paths.get(caseDirectory, (String) caseTableModel.getValueAt(modelRow, COLUMN_HEADERS.METADATA_FILE.ordinal()));

        new Thread(() -> {
            openCase(caseMetadataFilePath);
        }).start();
    }//GEN-LAST:event_bnOpenActionPerformed

    /**
     * Refresh button action
     *
     * @param evt -- The event that caused this to be called
     */
    private void bnRefreshActionPerformed(java.awt.event.ActionEvent evt) {
        refresh();
    }

    private void rbDaysItemStateChanged(java.awt.event.ItemEvent evt) {
        if (rbDays.isSelected()) {
            refresh();
        }
    }

    private void rbAllCasesItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_rbAllCasesItemStateChanged
        if (rbAllCases.isSelected()) {
            refresh();
        }
    }//GEN-LAST:event_rbAllCasesItemStateChanged

    private void rbMonthsItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_rbMonthsItemStateChanged
        if (rbMonths.isSelected()) {
            refresh();
        }
    }//GEN-LAST:event_rbMonthsItemStateChanged

    private void rbWeeksItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_rbWeeksItemStateChanged
        if (rbWeeks.isSelected()) {
            refresh();
        }
    }//GEN-LAST:event_rbWeeksItemStateChanged

    private void bnShowLogActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnShowLogActionPerformed
        Path pathToLog = getSelectedCaseLogFilePath();
        if (pathToLog != null) {
            try {
                if (pathToLog.toFile().exists()) {
                    Desktop.getDesktop().edit(pathToLog.toFile());

                } else {
                    JOptionPane.showMessageDialog(this, org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "DisplayLogDialog.cannotFindLog"),
                            org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "DisplayLogDialog.unableToShowLogFile"), JOptionPane.ERROR_MESSAGE);
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, String.format("Error attempting to open case auto ingest log file %s", pathToLog), ex);
                JOptionPane.showMessageDialog(this,
                        org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "DisplayLogDialog.cannotOpenLog"),
                        org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "DisplayLogDialog.unableToShowLogFile"),
                        JOptionPane.PLAIN_MESSAGE);
            }
        }
    }//GEN-LAST:event_bnShowLogActionPerformed

    private void casesTableMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_casesTableMouseClicked
        if (evt.getClickCount() == 2 && casesTable.getRowSelectionAllowed() && casesTable.getSelectedRow() >= 0 && casesTable.getSelectedRow() < casesTable.getRowCount()) {
            int modelRow = casesTable.convertRowIndexToModel(casesTable.getSelectedRow());
            String caseDirectory = (String) caseTableModel.getValueAt(modelRow, COLUMN_HEADERS.OUTPUTFOLDER.ordinal());
            Path caseMetadataFilePath = Paths.get(caseDirectory, (String) caseTableModel.getValueAt(modelRow, COLUMN_HEADERS.METADATA_FILE.ordinal()));
            openCase(caseMetadataFilePath);
        }
    }//GEN-LAST:event_casesTableMouseClicked

    private void bnOpenSingleUserCaseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnOpenSingleUserCaseActionPerformed
        Lookup.getDefault().lookup(CaseOpenAction.class).openCaseSelectionWindow();
    }//GEN-LAST:event_bnOpenSingleUserCaseActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnOpen;
    private javax.swing.JButton bnOpenSingleUserCase;
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

    private class LoadTableWorker extends SwingWorker<Void, Void> {

        private List<MultiUserCase> cases;
        private Path currentlySelectedCase;

        @Override
        protected Void doInBackground() throws Exception {

            try {
                MultiUserCaseManager manager = MultiUserCaseManager.getInstance();
                cases = manager.getCases();
            } catch (MultiUserCaseManager.MultiUserCaseManagerException | CoordinationService.CoordinationServiceException ex) {
                LOGGER.log(Level.SEVERE, "Unexpected exception while refreshing the table.", ex); //NON-NLS
            } finally {
                //ensure the cases are able to be selected
                casesTable.setRowSelectionAllowed(true);
            }
            return null;
        }

        @Override
        protected void done() {
            caseTableModel.setRowCount(0);
            long now = new Date().getTime();
            for (MultiUserCase autoIngestCase : cases) {
                if (autoIngestCase.getLastAccessedDate() != null && passesTimeFilter(now, autoIngestCase.getLastAccessedDate().getTime())) {
                    caseTableModel.addRow(new Object[]{
                        autoIngestCase.getCaseDisplayName(),
                        autoIngestCase.getCreationDate(),
                        autoIngestCase.getLastAccessedDate(),
                        (MultiUserCaseManager.CaseStatus.OK != autoIngestCase.getStatus()) ? StatusIconCellRenderer.Status.WARNING : StatusIconCellRenderer.Status.OK,
                        autoIngestCase.getCaseDirectoryPath().toString(),
                        autoIngestCase.getMetadataFileName()});
                }
            }
            setSelectedCase(currentlySelectedCase);
            setButtons();
        }
    }
}
