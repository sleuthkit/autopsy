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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import javax.swing.DefaultListSelectionModel;
import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.Collections;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import org.netbeans.api.options.OptionsDisplayer;
import org.openide.DialogDisplayer;
import org.openide.LifecycleManager;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.CaseNewAction;
import org.sleuthkit.autopsy.casemodule.CaseOpenAction;
import org.sleuthkit.autopsy.core.ServicesMonitor;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestProgressSnapshotDialog;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestManager.CaseDeletionResult;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestManager.JobsSnapshot;

/**
 * A panel for monitoring automated ingest by a cluster, and for controlling
 * automated ingest for a single node within the cluster. There can be at most
 * one such panel per node.
 */
public final class AutoIngestDashboard extends JPanel implements Observer {

    private static final long serialVersionUID = 1L;
    private static final int GENERIC_COL_MIN_WIDTH = 30;
    private static final int GENERIC_COL_MAX_WIDTH = 2000;
    private static final int PENDING_TABLE_COL_PREFERRED_WIDTH = 280;
    private static final int RUNNING_TABLE_COL_PREFERRED_WIDTH = 175;
    private static final int ACTIVITY_TIME_COL_MIN_WIDTH = 250;
    private static final int ACTIVITY_TIME_COL_MAX_WIDTH = 450;
    private static final int TIME_COL_MIN_WIDTH = 30;
    private static final int TIME_COL_MAX_WIDTH = 250;
    private static final int TIME_COL_PREFERRED_WIDTH = 140;
    private static final int NAME_COL_MIN_WIDTH = 100;
    private static final int NAME_COL_MAX_WIDTH = 250;
    private static final int NAME_COL_PREFERRED_WIDTH = 140;
    private static final int ACTIVITY_COL_MIN_WIDTH = 70;
    private static final int ACTIVITY_COL_MAX_WIDTH = 2000;
    private static final int ACTIVITY_COL_PREFERRED_WIDTH = 300;
    private static final int STATUS_COL_MIN_WIDTH = 55;
    private static final int STATUS_COL_MAX_WIDTH = 250;
    private static final int STATUS_COL_PREFERRED_WIDTH = 55;
    private static final int COMPLETED_TIME_COL_MIN_WIDTH = 30;
    private static final int COMPLETED_TIME_COL_MAX_WIDTH = 2000;
    private static final int COMPLETED_TIME_COL_PREFERRED_WIDTH = 280;
    private static final String UPDATE_TASKS_THREAD_NAME = "AID-update-tasks-%d";
    private static final String LOCAL_HOST_NAME = NetworkUtils.getLocalHostName();
    private static final Logger SYS_LOGGER = AutoIngestSystemLogger.getLogger();
    private static AutoIngestDashboard instance;
    private final DefaultTableModel pendingTableModel;
    private final DefaultTableModel runningTableModel;
    private final DefaultTableModel completedTableModel;
    private AutoIngestManager manager;
    private ExecutorService updateExecutor;
    private boolean isPaused;
    private boolean autoIngestStarted;
    private Color pendingTableBackground;
    private Color pendingTablelForeground;

    /*
     * The enum is used in conjunction with the DefaultTableModel class to
     * provide table models for the JTables used to display a view of the
     * pending jobs queue, running jobs list, and completed jobs list. The enum
     * allows the columns of the table model to be described by either an enum
     * ordinal or a column header string.
     */
    private enum JobsTableModelColumns {

        CASE(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.Case")),
        DATA_SOURCE(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.ImageFolder")),
        HOST_NAME(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.HostName")),
        CREATED_TIME(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.CreatedTime")),
        STARTED_TIME(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.StartedTime")),
        COMPLETED_TIME(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.CompletedTime")),
        STAGE(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.Stage")),
        STAGE_TIME(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.StageTime")),
        STATUS(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.Status")),
        CASE_DIRECTORY_PATH(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.CaseFolder")),
        IS_LOCAL_JOB(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.LocalJob")),
        MANIFEST_FILE_PATH(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.JobsTableModel.ColumnHeader.ManifestFilePath"));

        private final String header;

        private JobsTableModelColumns(String header) {
            this.header = header;
        }

        private String getColumnHeader() {
            return header;
        }

        private static final String[] headers = {
            CASE.getColumnHeader(),
            DATA_SOURCE.getColumnHeader(),
            HOST_NAME.getColumnHeader(),
            CREATED_TIME.getColumnHeader(),
            STARTED_TIME.getColumnHeader(),
            COMPLETED_TIME.getColumnHeader(),
            STAGE.getColumnHeader(),
            STATUS.getColumnHeader(),
            STAGE_TIME.getColumnHeader(),
            CASE_DIRECTORY_PATH.getColumnHeader(),
            IS_LOCAL_JOB.getColumnHeader(),
            MANIFEST_FILE_PATH.getColumnHeader()};
    }

    /**
     * Gets the singleton automated ingest control and monitoring panel for this
     * cluster node.
     *
     * @return The panel.
     */
    public static AutoIngestDashboard getInstance() {
        if (null == instance) {
            /*
             * Two stage construction is used here to avoid publishing a
             * reference to the panel to the Observable auto ingest manager
             * before object construction is complete.
             */
            instance = new AutoIngestDashboard();
        }
        return instance;
    }

    /**
     * Constructs a panel for monitoring automated ingest by a cluster, and for
     * controlling automated ingest for a single node within the cluster.
     */
    private AutoIngestDashboard() {
        disableUiMenuActions();
        
        manager = AutoIngestManager.getInstance();

        pendingTableModel = new DefaultTableModel(JobsTableModelColumns.headers, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        runningTableModel = new DefaultTableModel(JobsTableModelColumns.headers, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        completedTableModel = new DefaultTableModel(JobsTableModelColumns.headers, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        initComponents(); // Generated code.
        setServicesStatusMessage();
        initPendingJobsTable();
        initRunningJobsTable();
        initCompletedJobsTable();
        initButtons();

        /*
         * Must set this flag, otherwise pop up menus don't close properly.
         */
        UIManager.put("PopupMenu.consumeEventOnClose", false);
    }
    
    private void disableUiMenuActions() {        
        /*
         * Disable the new case action in auto ingest mode.
         */
        CallableSystemAction.get(CaseNewAction.class).setEnabled(false);

        /*
         * Disable the new case action in auto ingest mode.
         */
        CallableSystemAction.get(CaseOpenAction.class).setEnabled(false);
        CallableSystemAction.get(AutoIngestCaseOpenAction.class).setEnabled(false);

        /*
         * Permanently delete the "Open Recent Cases" item in the "Case" menu.
         * This is quite drastic, as it also affects Autopsy standalone mode on
         * this machine, but we need to make sure a user can't open case in
         * automated ingest mode. "Open Recent Cases" item can't be disabled via 
         * CallableSystemAction because of how it is defined in layer.xml, i.e. 
         * it is defined as "folder", not "file". 
         */
        FileObject root = FileUtil.getConfigRoot();
        FileObject openRecentCasesMenu = root.getFileObject("Menu/Case/OpenRecentCase");
        if (openRecentCasesMenu != null) {
            try {
                openRecentCasesMenu.delete();
            } catch (IOException ignore) {
            }
        }        
    }

    /**
     * Queries the services monitor and sets the text for the services status
     * text box.
     */
    private void setServicesStatusMessage() {
        new SwingWorker<Void, Void>() {

            String caseDatabaseServerStatus = getServiceStatus(ServicesMonitor.Service.REMOTE_CASE_DATABASE);
            String keywordSearchServiceStatus = getServiceStatus(ServicesMonitor.Service.REMOTE_CASE_DATABASE);
            String messagingStatus = getServiceStatus(ServicesMonitor.Service.REMOTE_CASE_DATABASE);

            @Override
            protected Void doInBackground() throws Exception {
                caseDatabaseServerStatus = getServiceStatus(ServicesMonitor.Service.REMOTE_CASE_DATABASE);
                keywordSearchServiceStatus = getServiceStatus(ServicesMonitor.Service.REMOTE_CASE_DATABASE);
                messagingStatus = getServiceStatus(ServicesMonitor.Service.REMOTE_CASE_DATABASE);
                return null;
            }

            /**
             * Gets a status string for a given service.
             *
             * @param service The service to test.
             *
             * @return The status string.
             */
            private String getServiceStatus(ServicesMonitor.Service service) {
                String serviceStatus = NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Unknown");
                try {
                    ServicesMonitor servicesMonitor = ServicesMonitor.getInstance();
                    serviceStatus = servicesMonitor.getServiceStatus(service.toString());
                    if (serviceStatus.compareTo(ServicesMonitor.ServiceStatus.UP.toString()) == 0) {
                        serviceStatus = NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Up");
                    } else {
                        serviceStatus = NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Down");
                    }
                } catch (ServicesMonitor.ServicesMonitorException ex) {
                    SYS_LOGGER.log(Level.SEVERE, String.format("Dashboard error getting service status for %s", service), ex);
                }
                return serviceStatus;
            }

            @Override
            protected void done() {
                tbServicesStatusMessage.setText(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message", caseDatabaseServerStatus, keywordSearchServiceStatus, keywordSearchServiceStatus, messagingStatus));
                String upStatus = NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Up");
                if (caseDatabaseServerStatus.compareTo(upStatus) != 0
                        || keywordSearchServiceStatus.compareTo(upStatus) != 0
                        || messagingStatus.compareTo(upStatus) != 0) {
                    tbServicesStatusMessage.setForeground(Color.RED);
                } else {
                    tbServicesStatusMessage.setForeground(Color.BLACK);
                }
            }

        }.execute();
    }

    /**
     * Sets up the JTable that presents a view of the system-wide pending jobs
     * queue.
     */
    private void initPendingJobsTable() {
        /*
         * Remove some of the jobs table model columns from the JTable. This
         * does not remove the columns from the model, just from this table.
         */
        pendingTable.removeColumn(pendingTable.getColumn(JobsTableModelColumns.HOST_NAME.getColumnHeader()));
        pendingTable.removeColumn(pendingTable.getColumn(JobsTableModelColumns.STARTED_TIME.getColumnHeader()));
        pendingTable.removeColumn(pendingTable.getColumn(JobsTableModelColumns.COMPLETED_TIME.getColumnHeader()));
        pendingTable.removeColumn(pendingTable.getColumn(JobsTableModelColumns.STAGE.getColumnHeader()));
        pendingTable.removeColumn(pendingTable.getColumn(JobsTableModelColumns.STAGE_TIME.getColumnHeader()));
        pendingTable.removeColumn(pendingTable.getColumn(JobsTableModelColumns.CASE_DIRECTORY_PATH.getColumnHeader()));
        pendingTable.removeColumn(pendingTable.getColumn(JobsTableModelColumns.IS_LOCAL_JOB.getColumnHeader()));
        pendingTable.removeColumn(pendingTable.getColumn(JobsTableModelColumns.STATUS.getColumnHeader()));
        pendingTable.removeColumn(pendingTable.getColumn(JobsTableModelColumns.MANIFEST_FILE_PATH.getColumnHeader()));

        /*
         * Set up a column to display the cases associated with the jobs.
         */
        TableColumn column;
        column = pendingTable.getColumn(JobsTableModelColumns.CASE.getColumnHeader());
        column.setMinWidth(GENERIC_COL_MIN_WIDTH);
        column.setMaxWidth(GENERIC_COL_MAX_WIDTH);
        column.setPreferredWidth(PENDING_TABLE_COL_PREFERRED_WIDTH);
        column.setWidth(PENDING_TABLE_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the image folders associated with the
         * jobs.
         */
        column = pendingTable.getColumn(JobsTableModelColumns.DATA_SOURCE.getColumnHeader());
        column.setMaxWidth(GENERIC_COL_MAX_WIDTH);
        column.setPreferredWidth(PENDING_TABLE_COL_PREFERRED_WIDTH);
        column.setWidth(PENDING_TABLE_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the create times of the jobs.
         */
        column = pendingTable.getColumn(JobsTableModelColumns.CREATED_TIME.getColumnHeader());
        column.setCellRenderer(new LongDateCellRenderer());
        column.setMinWidth(TIME_COL_MIN_WIDTH);
        column.setMaxWidth(TIME_COL_MAX_WIDTH);
        column.setPreferredWidth(TIME_COL_PREFERRED_WIDTH);
        column.setWidth(TIME_COL_PREFERRED_WIDTH);

        /**
         * Prevent sorting when a column header is clicked.
         */
        pendingTable.setAutoCreateRowSorter(false);

        /*
         * Create a row selection listener to enable/disable the prioritize
         * folder and prioritize case buttons.
         */
        pendingTable.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int row = pendingTable.getSelectedRow();
            enablePendingTableButtons((row >= 0) && (row < pendingTable.getRowCount()));
        });

        /*
         * Save the background color of the table so it can be restored on
         * resume, after being grayed out on pause. Note the assumption that all
         * of the tables use the same background color.
         */
        pendingTableBackground = pendingTable.getBackground();
        pendingTablelForeground = pendingTable.getForeground();
    }

    /**
     * Sets up the JTable that presents a view of the system-wide running jobs
     * list.
     */
    private void initRunningJobsTable() {
        /*
         * Remove some of the jobs table model columns from the JTable. This
         * does not remove the columns from the model, just from this table.
         */
        runningTable.removeColumn(runningTable.getColumn(JobsTableModelColumns.CREATED_TIME.getColumnHeader()));
        runningTable.removeColumn(runningTable.getColumn(JobsTableModelColumns.STARTED_TIME.getColumnHeader()));
        runningTable.removeColumn(runningTable.getColumn(JobsTableModelColumns.COMPLETED_TIME.getColumnHeader()));
        runningTable.removeColumn(runningTable.getColumn(JobsTableModelColumns.STATUS.getColumnHeader()));
        runningTable.removeColumn(runningTable.getColumn(JobsTableModelColumns.CASE_DIRECTORY_PATH.getColumnHeader()));
        runningTable.removeColumn(runningTable.getColumn(JobsTableModelColumns.IS_LOCAL_JOB.getColumnHeader()));
        runningTable.removeColumn(runningTable.getColumn(JobsTableModelColumns.MANIFEST_FILE_PATH.getColumnHeader()));

        /*
         * Set up a column to display the cases associated with the jobs.
         */
        TableColumn column;
        column = runningTable.getColumn(JobsTableModelColumns.CASE.getColumnHeader());
        column.setMinWidth(GENERIC_COL_MIN_WIDTH);
        column.setMaxWidth(GENERIC_COL_MAX_WIDTH);
        column.setPreferredWidth(RUNNING_TABLE_COL_PREFERRED_WIDTH);
        column.setWidth(RUNNING_TABLE_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the image folders associated with the
         * jobs.
         */
        column = runningTable.getColumn(JobsTableModelColumns.DATA_SOURCE.getColumnHeader());
        column.setMinWidth(GENERIC_COL_MIN_WIDTH);
        column.setMaxWidth(GENERIC_COL_MAX_WIDTH);
        column.setPreferredWidth(RUNNING_TABLE_COL_PREFERRED_WIDTH);
        column.setWidth(RUNNING_TABLE_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the host names of the cluster nodes
         * processing the jobs.
         */
        column = runningTable.getColumn(JobsTableModelColumns.HOST_NAME.getColumnHeader());
        column.setMinWidth(NAME_COL_MIN_WIDTH);
        column.setMaxWidth(NAME_COL_MAX_WIDTH);
        column.setPreferredWidth(NAME_COL_PREFERRED_WIDTH);
        column.setWidth(NAME_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the ingest activities associated with the
         * jobs.
         */
        column = runningTable.getColumn(JobsTableModelColumns.STAGE.getColumnHeader());
        column.setMinWidth(ACTIVITY_COL_MIN_WIDTH);
        column.setMaxWidth(ACTIVITY_COL_MAX_WIDTH);
        column.setPreferredWidth(ACTIVITY_COL_PREFERRED_WIDTH);
        column.setWidth(ACTIVITY_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the ingest activity times associated with
         * the jobs.
         */
        column = runningTable.getColumn(JobsTableModelColumns.STAGE_TIME.getColumnHeader());
        column.setCellRenderer(new DurationCellRenderer());
        column.setMinWidth(GENERIC_COL_MIN_WIDTH);
        column.setMaxWidth(ACTIVITY_TIME_COL_MAX_WIDTH);
        column.setPreferredWidth(ACTIVITY_TIME_COL_MIN_WIDTH);
        column.setWidth(ACTIVITY_TIME_COL_MIN_WIDTH);

        /*
         * Prevent sorting when a column header is clicked.
         */
        runningTable.setAutoCreateRowSorter(false);

        /*
         * Create a row selection listener to enable/disable the cancel current
         * job, cancel current module, and show progress buttons.
         */
        runningTable.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            updateRunningTableButtonsBasedOnSelectedRow();
        });
    }

    private void updateRunningTableButtonsBasedOnSelectedRow() {
        int row = runningTable.getSelectedRow();
        if (row >= 0 && row < runningTable.getRowCount()) {
            if ((boolean) runningTableModel.getValueAt(row, JobsTableModelColumns.IS_LOCAL_JOB.ordinal())) {
                enableRunningTableButtons(true);
                return;
            }
        }
        enableRunningTableButtons(false);
    }

    /**
     * Sets up the JTable that presents a view of the system-wide competed jobs
     * list.
     */
    private void initCompletedJobsTable() {
        /*
         * Remove some of the jobs table model columns from the JTable. This
         * does not remove the columns from the model, just from this table.
         */
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.STARTED_TIME.getColumnHeader()));
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.STAGE.getColumnHeader()));
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.STAGE_TIME.getColumnHeader()));
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.IS_LOCAL_JOB.getColumnHeader()));
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.HOST_NAME.getColumnHeader()));
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.CASE_DIRECTORY_PATH.getColumnHeader()));
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.MANIFEST_FILE_PATH.getColumnHeader()));

        /*
         * Set up a column to display the cases associated with the jobs.
         */
        TableColumn column;
        column = completedTable.getColumn(JobsTableModelColumns.CASE.getColumnHeader());
        column.setMinWidth(COMPLETED_TIME_COL_MIN_WIDTH);
        column.setMaxWidth(COMPLETED_TIME_COL_MAX_WIDTH);
        column.setPreferredWidth(COMPLETED_TIME_COL_PREFERRED_WIDTH);
        column.setWidth(COMPLETED_TIME_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the image folders associated with the
         * jobs.
         */
        column = completedTable.getColumn(JobsTableModelColumns.DATA_SOURCE.getColumnHeader());
        column.setMinWidth(COMPLETED_TIME_COL_MIN_WIDTH);
        column.setMaxWidth(COMPLETED_TIME_COL_MAX_WIDTH);
        column.setPreferredWidth(COMPLETED_TIME_COL_PREFERRED_WIDTH);
        column.setWidth(COMPLETED_TIME_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the create times of the jobs.
         */
        column = completedTable.getColumn(JobsTableModelColumns.CREATED_TIME.getColumnHeader());
        column.setCellRenderer(new LongDateCellRenderer());
        column.setMinWidth(TIME_COL_MIN_WIDTH);
        column.setMaxWidth(TIME_COL_MAX_WIDTH);
        column.setPreferredWidth(TIME_COL_PREFERRED_WIDTH);
        column.setWidth(TIME_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the completed times of the jobs.
         */
        column = completedTable.getColumn(JobsTableModelColumns.COMPLETED_TIME.getColumnHeader());
        column.setCellRenderer(new LongDateCellRenderer());
        column.setMinWidth(TIME_COL_MIN_WIDTH);
        column.setMaxWidth(TIME_COL_MAX_WIDTH);
        column.setPreferredWidth(TIME_COL_PREFERRED_WIDTH);
        column.setWidth(TIME_COL_PREFERRED_WIDTH);

        /*
         * Set up a column to display the statuses of the jobs, with a cell
         * renderer that will choose an icon to represent the job status.
         */
        column = completedTable.getColumn(JobsTableModelColumns.STATUS.getColumnHeader());
        column.setCellRenderer(new CaseStatusIconCellRenderer());
        column.setMinWidth(STATUS_COL_MIN_WIDTH);
        column.setMaxWidth(STATUS_COL_MAX_WIDTH);
        column.setPreferredWidth(STATUS_COL_PREFERRED_WIDTH);
        column.setWidth(STATUS_COL_PREFERRED_WIDTH);

        /*
         * Prevent sorting when a column header is clicked.
         */
        completedTable.setAutoCreateRowSorter(false);

        /*
         * Create a row selection listener to enable/disable the delete case and
         * show log buttons.
         */
        completedTable.getSelectionModel()
                .addListSelectionListener((ListSelectionEvent e) -> {
                    if (e.getValueIsAdjusting()) {
                        return;
                    }
                    int row = completedTable.getSelectedRow();
                    boolean enabled = row >= 0 && row < completedTable.getRowCount();
                    bnDeleteCase.setEnabled(enabled);
                    bnShowCaseLog.setEnabled(enabled);
                    bnReprocessJob.setEnabled(enabled);
                });
    }

    /**
     * Sets the initial state of the buttons on the panel.
     */
    private void initButtons() {
        bnOptions.setEnabled(true);
        bnDeleteCase.setEnabled(false);
        enablePendingTableButtons(false);
        bnShowCaseLog.setEnabled(false);
        bnReprocessJob.setEnabled(false);
        bnPause.setText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnStart.text"));
        bnPause.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnStart.toolTipText"));
        bnPause.setEnabled(true);    //initial label for bnPause is 'Start' and it's enabled for user to start the process
        bnRefresh.setEnabled(false); //at initial stage, nothing to refresh
        enableRunningTableButtons(false);
        tbStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnStart.startMessage"));
    }

    /**
     * Enables or disables buttons related to the running jobs table.
     *
     * @param enable Enable/disable the buttons.
     */
    private void enableRunningTableButtons(Boolean enable) {
        bnCancelJob.setEnabled(enable);
        bnCancelModule.setEnabled(enable);
        bnShowProgress.setEnabled(enable);
    }

    /**
     * Enables or disables buttons related to pending jobs table.
     *
     * @param enable Enable/disable the buttons.
     */
    private void enablePendingTableButtons(Boolean enable) {
        bnPrioritizeCase.setEnabled(enable);
        bnPrioritizeJob.setEnabled(enable);
    }

    /**
     * Starts up the auto ingest manager and adds this panel as an observer,
     * subscribes to services monitor events and starts a task to populate the
     * auto ingest job tables. The Refresh and Pause buttons are enabled.
     */
    private void startUp() {

        /*
         * Starts up the auto ingest manager (AIM).
         */
        try {
            manager.startUp();
            autoIngestStarted = true;
        } catch (AutoIngestManager.AutoIngestManagerStartupException ex) {
            SYS_LOGGER.log(Level.SEVERE, "Dashboard error starting up auto ingest", ex);
            tbStatusMessage.setText(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.AutoIngestStartupError"));
            manager = null;

            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.AutoIngestStartupFailed.Message"),
                    NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.AutoIngestStartupFailed.Title"),
                    JOptionPane.ERROR_MESSAGE);
            bnOptions.setEnabled(true);

            /*
             * If the AIM cannot be started, there is nothing more to do.
             */
            return;
        }

        /*
         * Subscribe to services monitor events.
         */
        ServicesMonitor.getInstance().addSubscriber((PropertyChangeEvent evt) -> {
            setServicesStatusMessage();
        });

        /*
         * Register with the AIM as an observer.
         */
        manager.addObserver(this);

        /*
         * Populate the pending, running, and completed auto ingest job tables.
         */
        updateExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(UPDATE_TASKS_THREAD_NAME).build());
        updateExecutor.submit(new UpdateAllJobsTablesTask());
        manager.scanInputDirsNow();

		//bnPause.setEnabled(true);
        bnPause.setText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnPause.text"));
        bnPause.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnPause.toolTipText"));
        bnRefresh.setEnabled(true);
        bnOptions.setEnabled(false);
        
        tbStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnPause.running"));
    }

    /**
     * Shuts down auto ingest by shutting down the auto ingest manager and doing
     * an application exit.
     */
    public void shutdown() {
        /*
         * Confirm that the user wants to proceed, letting him or her no that if
         * there is a currently running job it will be cancelled. TODO (RC): If
         * a wait cursor is provided, this could perhaps be made conditional on
         * a running job check again. Or the simple check in isLocalJobRunning
         * could be used. Was this previously used and I removed it thinking it
         * was grabbing the monitor?
         */
        Object[] options = {
            NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.OK"),
            NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.Cancel")};
        int reply = JOptionPane.OK_OPTION;

        if (null != manager && IngestManager.getInstance().isIngestRunning()) {
            reply = JOptionPane.showOptionDialog(this,
                    NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.ExitConsequences"),
                    NbBundle.getMessage(AutoIngestDashboard.class, "ConfirmationDialog.ConfirmExitHeader"),
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    options[JOptionPane.NO_OPTION]);
        }
        if (reply == JOptionPane.OK_OPTION) {
            /*
             * Provide user feedback. Call setCursor on this to ensure it
             * appears (if there is time to see it).
             */
            this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            tbStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.ExitingStatus"));

            /*
             * Shut down the table refresh task executor.
             */
            if (null != updateExecutor) {
                updateExecutor.shutdownNow();
            }

            /*
             * Stop observing the auto ingest manager (AIM).
             */
            if (null != manager) {
                manager.deleteObserver(this);
            }

            /*
             * Shut down the AIM and close.
             */
            new SwingWorker<Void, Void>() {

                @Override
                protected Void doInBackground() throws Exception {
                    if (null != manager) {
                        manager.shutDown();
                    }
                    return null;
                }

                @Override
                protected void done() {
                    AutoIngestDashboard.this.setCursor(Cursor.getDefaultCursor());
                    LifecycleManager.getDefault().exit();
                }
            }.execute();
        }
    }

    /**
     * @inheritDoc
     */
    @NbBundle.Messages({
        "AutoIngestDashboard.bnPause.paused=Paused",
        "AutoIngestDashboard.PauseDueToDatabaseServiceDown=Paused, unable to communicate with case database service.",
        "AutoIngestDashboard.PauseDueToKeywordSearchServiceDown=Paused, unable to communicate with keyword search service.",
        "AutoIngestDashboard.PauseDueToCoordinationServiceDown=Paused, unable to communicate with coordination service.",
        "AutoIngestDashboard.PauseDueToWriteStateFilesFailure=Paused, unable to write to shared images or cases location.",
        "AutoIngestDashboard.PauseDueToSharedConfigError=Paused, unable to update shared configuration.",
        "AutoIngestDashboard.PauseDueToIngestJobStartFailure=Paused, unable to start ingest job processing.",
        "AutoIngestDashboard.PauseDueToFileExporterError=Paused, unable to load File Exporter settings.",
        "AutoIngestDashboard.bnPause.running=Running",
        "AutoIngestDashboard.bnStart.startMessage=Waiting to start",
        "AutoIngestDashboard.bnStart.text=Start",
        "AutoIngestDashboard.bnStart.toolTipText=Start processing auto ingest jobs"
    })
    @Override
    public void update(Observable o, Object arg) {

        if (arg instanceof AutoIngestManager.Event) {
            switch ((AutoIngestManager.Event) arg) {
                case INPUT_SCAN_COMPLETED:
                case JOB_STARTED:
                case JOB_COMPLETED:
                case CASE_DELETED:
                    updateExecutor.submit(new UpdateAllJobsTablesTask());
                    break;
                case PAUSED_BY_REQUEST:
                    EventQueue.invokeLater(() -> {
                        tbStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnPause.paused"));
                        bnOptions.setEnabled(true);
                        bnRefresh.setEnabled(false);
                        isPaused = true;
                    });
                    break;
                case PAUSED_FOR_SYSTEM_ERROR:
                    EventQueue.invokeLater(() -> {
                        tbStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.PauseDueToSystemError"));
                        bnOptions.setEnabled(true);
                        bnRefresh.setEnabled(false);
                        pause(false);
                        isPaused = true;
                        setServicesStatusMessage();
                    });
                    break;
                case RESUMED:
                    EventQueue.invokeLater(() -> {
                        tbStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnPause.running"));
                    });
                    break;
                case CASE_PRIORITIZED:
                    updateExecutor.submit(new UpdatePendingJobsTableTask());
                    break;
                case JOB_STATUS_UPDATED:
                    updateExecutor.submit(new UpdateRunningJobsTablesTask());
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Requests a pause of auto ingest processing by the auto ingest manager and
     * handles updates to the components that implement the pause and resume
     * feature. Note that this feature is needed to get around restrictions on
     * changing ingest module selections and settings while an ingest job is
     * running, and that the auto ingest manager will not actually pause until
     * the current auto ingest job completes.
     *
     * @param buttonClicked Is this pause request in response to a user gesture
     *                      or a nofification from the auto ingest manager
     *                      (AIM)?
     */
    private void pause(boolean buttonClicked) {
        /**
         * Gray out the cells in the pending table to give a visual indicator of
         * the pausing/paused state.
         */
        pendingTable.setBackground(Color.LIGHT_GRAY);
        pendingTable.setForeground(Color.DARK_GRAY);

        /**
         * Change the pause button text and tool tip to make it a resume button.
         */
        bnPause.setText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnResume.text"));
        bnPause.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnPause.toolTipTextResume"));

        if (buttonClicked) {
            /**
             * Ask the auto ingest manager to pause when it completes the
             * currently running job, if any.
             */
            manager.pause();
            bnRefresh.setEnabled(false);
        }
    }

    /**
     * Requests a resume of auto ingest processing by the auto ingest manager
     * and handles updates to the components that implement the pause and resume
     * feature. Note that this feature is needed to get around restrictions on
     * changing ingest module selections and settings while an ingest job is
     * running, and that the auto ingest manager will not actually pause until
     * the current auto ingest job completes.
     */
    private void resume() {
        /**
         * Change the resume button text and tool tip to make it a pause button.
         */
        bnOptions.setEnabled(false);
        bnPause.setText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnPause.text"));
        bnPause.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnPause.toolTipText"));
        tbStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnPause.running"));
        bnRefresh.setEnabled(true);

        /**
         * Remove the graying out of the pending table.
         */
        pendingTable.setBackground(pendingTableBackground);
        pendingTable.setForeground(pendingTablelForeground);

        /**
         * Ask the auto ingest manager to resume processing.
         */
        manager.resume();
    }

    /**
     * A runnable task that gets the pending auto ingest jobs list from the auto
     * ingest manager and queues a components refresh task for execution in the
     * EDT.
     */
    private class UpdatePendingJobsTableTask implements Runnable {

        /**
         * @inheritDoc
         */
        @Override
        public void run() {
            List<AutoIngestJob> pendingJobs = new ArrayList<>();
            manager.getJobs(pendingJobs, null, null);
            EventQueue.invokeLater(new RefreshComponentsTask(pendingJobs, null, null));
        }
    }

    /**
     * A runnable task that gets the running auto ingest jobs list from the auto
     * ingest manager and queues a components refresh task for execution in the
     * EDT.
     */
    private class UpdateRunningJobsTablesTask implements Runnable {

        /**
         * @inheritDoc
         */
        @Override
        public void run() {
            List<AutoIngestJob> runningJobs = new ArrayList<>();
            manager.getJobs(null, runningJobs, null);
            EventQueue.invokeLater(new RefreshComponentsTask(null, runningJobs, null));
        }
    }

    /**
     * A runnable task that gets the pending, running and completed auto ingest
     * jobs lists from the auto ingest manager and queues a components refresh
     * task for execution in the EDT. Note that this task is frequently used
     * when only the pending and updated lists definitely need to be updated.
     * This is because the cost of updating the running jobs list is both very
     * small and it is beneficial to keep running job status up to date if there
     * is a running job.
     */
    private class UpdateAllJobsTablesTask implements Runnable {

        /**
         * @inheritDoc
         */
        @Override
        public void run() {
            List<AutoIngestJob> pendingJobs = new ArrayList<>();
            List<AutoIngestJob> runningJobs = new ArrayList<>();
            List<AutoIngestJob> completedJobs = new ArrayList<>();
            manager.getJobs(pendingJobs, runningJobs, completedJobs);
            // Sort the completed jobs list by completed date
            Collections.sort(completedJobs, new AutoIngestJob.ReverseDateCompletedComparator());
            EventQueue.invokeLater(new RefreshComponentsTask(pendingJobs, runningJobs, completedJobs));
        }
    }

    /**
     * A runnable task that refreshes the components on this panel to reflect
     * the current state of one or more auto ingest job lists obtained from the
     * auto ingest manager.
     */
    private class RefreshComponentsTask implements Runnable {

        private final List<AutoIngestJob> pendingJobs;
        private final List<AutoIngestJob> runningJobs;
        private final List<AutoIngestJob> completedJobs;

        /**
         * Constructs a runnable task that refreshes the components on this
         * panel to reflect the current state of the auto ingest jobs.
         *
         * @param pendingJobs   A list of pending jobs, may be null if the
         *                      pending jobs are unchanged.
         * @param runningJobs   A list of running jobs, may be null if the
         *                      running jobs are unchanged.
         * @param completedJobs A list of completed jobs, may be null if the
         *                      completed jobs are unchanged.
         */
        RefreshComponentsTask(List<AutoIngestJob> pendingJobs, List<AutoIngestJob> runningJobs, List<AutoIngestJob> completedJobs) {
            this.pendingJobs = pendingJobs;
            this.runningJobs = runningJobs;
            this.completedJobs = completedJobs;
        }

        /**
         * @inheritDoc
         */
        @Override
        public void run() {
            /*
             * NOTE: There is a problem with our approach of preserving table
             * row selections - what if the number of rows has changed as result
             * of calling refreshTable(). Then it is possible for what used to
             * be (for example) row 1 to now be in some other row or be removed
             * from the table. This code will re-set the selection back to what
             * it used to be before calling refreshTable(), i.e. row 1
             */

            if (null != pendingJobs) {
                Path currentRow = getSelectedEntry(pendingTable, pendingTableModel);
                refreshTable(pendingJobs, pendingTableModel, null);
                setSelectedEntry(pendingTable, pendingTableModel, currentRow);
            }

            if (null != runningJobs) {
                if (!isLocalJobRunning()) {
                    enableRunningTableButtons(false);
                } else {
                    updateRunningTableButtonsBasedOnSelectedRow();
                }
                Path currentRow = getSelectedEntry(runningTable, runningTableModel);
                refreshTable(runningJobs, runningTableModel, null);
                setSelectedEntry(runningTable, runningTableModel, currentRow);
            }

            if (null != completedJobs) {
                Path currentRow = getSelectedEntry(completedTable, completedTableModel);
                refreshTable(completedJobs, completedTableModel, null);
                setSelectedEntry(completedTable, completedTableModel, currentRow);
            }
        }

        /**
         * Checks whether there is a job that is running on local AIN.
         *
         * @return true is local job is found, false otherwise.
         */
        private boolean isLocalJobRunning() {
            for (AutoIngestJob job : runningJobs) {
                if (isLocalJob(job)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Checks whether or not an automated ingest job is local to this node.
         *
         * @param job The job.
         *
         * @return True or fale.
         */
        private boolean isLocalJob(AutoIngestJob job) {
            return job.getNodeName().equals(LOCAL_HOST_NAME);
        }

        /**
         * Get a path representing the current selection on the table passed in.
         * If there is no selection, return null.
         *
         * @param table      The table to get
         * @param tableModel The tableModel of the table to get
         *
         * @return a path representing the current selection
         */
        Path getSelectedEntry(JTable table, DefaultTableModel tableModel) {
            try {
                int currentlySelectedRow = table.getSelectedRow();
                if (currentlySelectedRow >= 0 && currentlySelectedRow < table.getRowCount()) {
                    return Paths.get(tableModel.getValueAt(currentlySelectedRow, JobsTableModelColumns.CASE.ordinal()).toString(),
                            tableModel.getValueAt(currentlySelectedRow, JobsTableModelColumns.DATA_SOURCE.ordinal()).toString());
                }
            } catch (Exception ignored) {
                return null;
            }
            return null;
        }

        /**
         * Set the selection on the table to the passed-in path's item, if that
         * item exists in the table. If it does not, clears the table selection.
         *
         * @param table      The table to set
         * @param tableModel The tableModel of the table to set
         * @param path       The path of the item to set
         */
        void setSelectedEntry(JTable table, DefaultTableModel tableModel, Path path) {
            if (path != null) {
                try {
                    for (int row = 0; row < table.getRowCount(); ++row) {
                        Path temp = Paths.get(tableModel.getValueAt(row, JobsTableModelColumns.CASE.ordinal()).toString(),
                                tableModel.getValueAt(row, JobsTableModelColumns.DATA_SOURCE.ordinal()).toString());
                        if (temp.compareTo(path) == 0) { // found it
                            table.setRowSelectionInterval(row, row);
                            return;
                        }
                    }
                } catch (Exception ignored) {
                    table.clearSelection();
                }
            }
            table.clearSelection();
        }
    }

    /**
     * Reloads the table model for an auto ingest jobs table, refreshing the
     * JTable that uses the model.
     *
     * @param jobs       The list of auto ingest jobs.
     * @param tableModel The table model.
     * @param comparator An optional comparator (may be null) for sorting the
     *                   table model.
     */
    private void refreshTable(List<AutoIngestJob> jobs, DefaultTableModel tableModel, Comparator<AutoIngestJob> comparator) {
        try {
            if (comparator != null) {
                jobs.sort(comparator);
            }
            tableModel.setRowCount(0);
            for (AutoIngestJob job : jobs) {
                AutoIngestJob.StageDetails status = job.getStageDetails();
                tableModel.addRow(new Object[]{
                    job.getManifest().getCaseName(), // CASE
                    job.getManifest().getDataSourcePath().getFileName(), // DATA_SOURCE
                    job.getNodeName(), // HOST_NAME
                    job.getManifest().getDateFileCreated(), // CREATED_TIME
                    job.getStageStartDate(), // STARTED_TIME
                    job.getCompletedDate(), // COMPLETED_TIME
                    status.getDescription(), // ACTIVITY
                    job.hasErrors(), // STATUS
                    ((Date.from(Instant.now()).getTime()) - (status.getStartDate().getTime())), // ACTIVITY_TIME
                    job.getCaseDirectoryPath(), // CASE_DIRECTORY_PATH
                    job.getNodeName().equals(LOCAL_HOST_NAME), // IS_LOCAL_JOB
                    job.getManifest().getFilePath()}); // MANIFEST_FILE_PATH
            }
        } catch (Exception ex) {
            SYS_LOGGER.log(Level.SEVERE, "Dashboard error refreshing table", ex);
        }
    }
    
    /**
     * Get the current lists of jobs and update the UI.
     */
    private void refreshTables(){
        JobsSnapshot jobsSnapshot = manager.getCurrentJobsSnapshot();
        refreshTable(jobsSnapshot.getCompletedJobs(), completedTableModel, null);
        refreshTable(jobsSnapshot.getPendingJobs(), pendingTableModel, null);
        refreshTable(jobsSnapshot.getRunningJobs(), runningTableModel, null);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        pendingScrollPane = new javax.swing.JScrollPane();
        pendingTable = new javax.swing.JTable();
        runningScrollPane = new javax.swing.JScrollPane();
        runningTable = new javax.swing.JTable();
        completedScrollPane = new javax.swing.JScrollPane();
        completedTable = new javax.swing.JTable();
        bnCancelJob = new javax.swing.JButton();
        bnDeleteCase = new javax.swing.JButton();
        lbPending = new javax.swing.JLabel();
        lbRunning = new javax.swing.JLabel();
        lbCompleted = new javax.swing.JLabel();
        bnRefresh = new javax.swing.JButton();
        bnCancelModule = new javax.swing.JButton();
        bnExit = new javax.swing.JButton();
        bnOptions = new javax.swing.JButton();
        bnShowProgress = new javax.swing.JButton();
        bnPause = new javax.swing.JButton();
        bnPrioritizeCase = new javax.swing.JButton();
        bnShowCaseLog = new javax.swing.JButton();
        tbStatusMessage = new javax.swing.JTextField();
        lbStatus = new javax.swing.JLabel();
        bnPrioritizeJob = new javax.swing.JButton();
        lbServicesStatus = new javax.swing.JLabel();
        tbServicesStatusMessage = new javax.swing.JTextField();
        bnOpenLogDir = new javax.swing.JButton();
        bnReprocessJob = new javax.swing.JButton();

        pendingTable.setModel(pendingTableModel);
        pendingTable.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.pendingTable.toolTipText")); // NOI18N
        pendingTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        pendingTable.setRowHeight(20);
        pendingTable.setSelectionModel(new DefaultListSelectionModel() {
            private static final long serialVersionUID = 1L;
            @Override
            public void setSelectionInterval(int index0, int index1) {
                if (index0 == pendingTable.getSelectedRow()) {
                    pendingTable.clearSelection();
                } else {
                    super.setSelectionInterval(index0, index1);
                }
            }
        });
        pendingTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        pendingScrollPane.setViewportView(pendingTable);

        runningTable.setModel(runningTableModel);
        runningTable.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.runningTable.toolTipText")); // NOI18N
        runningTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        runningTable.setRowHeight(20);
        runningTable.setSelectionModel(new DefaultListSelectionModel() {
            private static final long serialVersionUID = 1L;
            @Override
            public void setSelectionInterval(int index0, int index1) {
                if (index0 == runningTable.getSelectedRow()) {
                    runningTable.clearSelection();
                } else {
                    super.setSelectionInterval(index0, index1);
                }
            }
        });
        runningTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        runningScrollPane.setViewportView(runningTable);

        completedTable.setModel(completedTableModel);
        completedTable.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.completedTable.toolTipText")); // NOI18N
        completedTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        completedTable.setRowHeight(20);
        completedTable.setSelectionModel(new DefaultListSelectionModel() {
            private static final long serialVersionUID = 1L;
            @Override
            public void setSelectionInterval(int index0, int index1) {
                if (index0 == completedTable.getSelectedRow()) {
                    completedTable.clearSelection();
                } else {
                    super.setSelectionInterval(index0, index1);
                }
            }
        });
        completedTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        completedScrollPane.setViewportView(completedTable);

        org.openide.awt.Mnemonics.setLocalizedText(bnCancelJob, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnCancelJob.text")); // NOI18N
        bnCancelJob.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnCancelJob.toolTipText")); // NOI18N
        bnCancelJob.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnCancelJobActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnDeleteCase, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnDeleteCase.text")); // NOI18N
        bnDeleteCase.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnDeleteCase.toolTipText")); // NOI18N
        bnDeleteCase.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnDeleteCaseActionPerformed(evt);
            }
        });

        lbPending.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbPending, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbPending.text")); // NOI18N

        lbRunning.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbRunning, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbRunning.text")); // NOI18N

        lbCompleted.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbCompleted, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbCompleted.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnRefresh, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnRefresh.text")); // NOI18N
        bnRefresh.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnRefresh.toolTipText")); // NOI18N
        bnRefresh.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnRefreshActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnCancelModule, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnCancelModule.text")); // NOI18N
        bnCancelModule.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnCancelModule.toolTipText")); // NOI18N
        bnCancelModule.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnCancelModuleActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnExit, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnExit.text")); // NOI18N
        bnExit.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnExit.toolTipText")); // NOI18N
        bnExit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnExitActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnOptions, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnOptions.text")); // NOI18N
        bnOptions.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnOptions.toolTipText")); // NOI18N
        bnOptions.setEnabled(false);
        bnOptions.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnOptionsActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnShowProgress, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnShowProgress.text")); // NOI18N
        bnShowProgress.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnShowProgress.toolTipText")); // NOI18N
        bnShowProgress.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnShowProgressActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnPause, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnPause.text")); // NOI18N
        bnPause.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnPause.toolTipText")); // NOI18N
        bnPause.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnPauseActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnPrioritizeCase, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnPrioritizeCase.text")); // NOI18N
        bnPrioritizeCase.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnPrioritizeCase.toolTipText")); // NOI18N
        bnPrioritizeCase.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnPrioritizeCaseActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnShowCaseLog, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnShowCaseLog.text")); // NOI18N
        bnShowCaseLog.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnShowCaseLog.toolTipText")); // NOI18N
        bnShowCaseLog.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnShowCaseLogActionPerformed(evt);
            }
        });

        tbStatusMessage.setEditable(false);
        tbStatusMessage.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        tbStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbStatusMessage.text")); // NOI18N
        tbStatusMessage.setBorder(null);

        lbStatus.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbStatus, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbStatus.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnPrioritizeJob, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnPrioritizeJob.text")); // NOI18N
        bnPrioritizeJob.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnPrioritizeJob.toolTipText")); // NOI18N
        bnPrioritizeJob.setActionCommand(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnPrioritizeJob.actionCommand")); // NOI18N
        bnPrioritizeJob.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnPrioritizeJobActionPerformed(evt);
            }
        });

        lbServicesStatus.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbServicesStatus, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbServicesStatus.text")); // NOI18N

        tbServicesStatusMessage.setEditable(false);
        tbServicesStatusMessage.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        tbServicesStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.text")); // NOI18N
        tbServicesStatusMessage.setBorder(null);

        org.openide.awt.Mnemonics.setLocalizedText(bnOpenLogDir, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnOpenLogDir.text")); // NOI18N
        bnOpenLogDir.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnOpenLogDirActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnReprocessJob, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnReprocessJob.text")); // NOI18N
        bnReprocessJob.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnReprocessJobActionPerformed(evt);
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
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(lbPending, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(pendingScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 920, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(bnPrioritizeCase, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(bnPrioritizeJob, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(bnPause)
                                .addGap(18, 18, 18)
                                .addComponent(bnRefresh, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(bnOptions)
                                .addGap(18, 18, 18)
                                .addComponent(bnOpenLogDir)
                                .addGap(18, 18, 18)
                                .addComponent(bnExit, javax.swing.GroupLayout.PREFERRED_SIZE, 94, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(runningScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 920, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(completedScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 920, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(bnCancelJob, javax.swing.GroupLayout.DEFAULT_SIZE, 117, Short.MAX_VALUE)
                                    .addComponent(bnShowProgress, javax.swing.GroupLayout.DEFAULT_SIZE, 116, Short.MAX_VALUE)
                                    .addComponent(bnCancelModule, javax.swing.GroupLayout.DEFAULT_SIZE, 117, Short.MAX_VALUE)
                                    .addComponent(bnDeleteCase, javax.swing.GroupLayout.DEFAULT_SIZE, 117, Short.MAX_VALUE)
                                    .addComponent(bnShowCaseLog, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(bnReprocessJob, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(lbStatus)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(tbStatusMessage, javax.swing.GroupLayout.PREFERRED_SIZE, 861, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(lbCompleted)
                            .addComponent(lbRunning)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(lbServicesStatus)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(tbServicesStatusMessage, javax.swing.GroupLayout.PREFERRED_SIZE, 861, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {bnCancelJob, bnCancelModule, bnDeleteCase, bnExit, bnOpenLogDir, bnOptions, bnPause, bnRefresh, bnShowProgress});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbStatus, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tbStatusMessage, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbServicesStatus, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tbServicesStatusMessage, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lbPending, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pendingScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 215, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(82, 82, 82)
                        .addComponent(bnPrioritizeCase)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bnPrioritizeJob)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lbRunning)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(34, 34, 34)
                        .addComponent(bnShowProgress)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bnCancelJob)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bnCancelModule))
                    .addGroup(layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(runningScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 133, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(68, 68, 68)
                        .addComponent(bnReprocessJob)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bnDeleteCase)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bnShowCaseLog))
                    .addGroup(layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lbCompleted)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(completedScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 179, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(bnExit)
                                .addComponent(bnOpenLogDir))
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(bnPause)
                                .addComponent(bnRefresh)
                                .addComponent(bnOptions)))))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {bnCancelJob, bnCancelModule, bnDeleteCase, bnExit, bnOpenLogDir, bnOptions, bnRefresh, bnShowProgress});

    }// </editor-fold>//GEN-END:initComponents

    /**
     * Handles a click on the refresh button. Requests an immediate scan of the
     * input folders for new jobs and queues a refresh of all three of the jobs
     * tables.
     *
     * @param evt - The button click event.
     */
    private void bnRefreshActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnRefreshActionPerformed
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        manager.scanInputDirsAndWait();
        refreshTables();
        this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }//GEN-LAST:event_bnRefreshActionPerformed

    /**
     * Handles a click on the delete case button. If an entry is selected that
     * can be deleted, pops up a confirmation dialog. Upon confirmation, asks
     * AutoIngestManager to delete the entry and asks for an updated view.
     *
     * @param evt The button click event.
     */
    private void bnDeleteCaseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnDeleteCaseActionPerformed
        if (completedTableModel.getRowCount() < 0 || completedTable.getSelectedRow() < 0) {
            return;
        }

        String caseName = (String) completedTable.getValueAt(completedTable.getSelectedRow(), JobsTableModelColumns.CASE.ordinal());
        Object[] options = {
            org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "ConfirmationDialog.Delete"),
            org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "ConfirmationDialog.DoNotDelete")
        };
        Object[] msgContent = {org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "ConfirmationDialog.DeleteAreYouSure") + "\"" + caseName + "\"?"};
        int reply = JOptionPane.showOptionDialog(this,
                msgContent,
                org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "ConfirmationDialog.ConfirmDeletionHeader"),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[JOptionPane.NO_OPTION]);
        if (reply == JOptionPane.YES_OPTION) {
            bnDeleteCase.setEnabled(false);
            bnShowCaseLog.setEnabled(false);
            if (completedTableModel.getRowCount() > 0 && completedTable.getSelectedRow() >= 0) {
                Path caseDirectoryPath = (Path) completedTableModel.getValueAt(completedTable.getSelectedRow(), JobsTableModelColumns.CASE_DIRECTORY_PATH.ordinal());
                completedTable.clearSelection();
                this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                CaseDeletionResult result = manager.deleteCase(caseName, caseDirectoryPath);
                refreshTables();
                this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                if (CaseDeletionResult.FAILED == result) {
                    JOptionPane.showMessageDialog(this,
                            String.format("Could not delete case %s. It may be in in use.", caseName),
                            org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.DeletionFailed"),
                            JOptionPane.INFORMATION_MESSAGE);
                } else if (CaseDeletionResult.PARTIALLY_DELETED == result) {
                    JOptionPane.showMessageDialog(this,
                            String.format("Could not delete case %s. See system log for details.", caseName),
                            org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.DeletionFailed"),
                            JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }
    }//GEN-LAST:event_bnDeleteCaseActionPerformed

    /**
     * Handles a click on the cancel auto ingest job button. Cancels the
     * selected job.
     *
     * @param evt The button click event.
     */
    private void bnCancelJobActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnCancelJobActionPerformed
        Object[] options = {
            org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "ConfirmationDialog.CancelJob"),
            org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "ConfirmationDialog.DoNotCancelJob")};
        int reply = JOptionPane.showOptionDialog(this,
                NbBundle.getMessage(AutoIngestDashboard.class, "ConfirmationDialog.CancelJobAreYouSure"),
                NbBundle.getMessage(AutoIngestDashboard.class, "ConfirmationDialog.ConfirmCancellationHeader"),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[1]);
        if (reply == 0) {
            /*
             * Call setCursor on this to ensure it appears (if there is time to
             * see it).
             */
            this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            manager.cancelCurrentJob();
            refreshTables();
            this.setCursor(Cursor.getDefaultCursor());
        }
    }//GEN-LAST:event_bnCancelJobActionPerformed

    /**
     * Handles a click on the show auto ingest job progress button. Displays an
     * ingest job progress panel.
     *
     * @param evt The button click event.
     */
    private void bnShowProgressActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnShowProgressActionPerformed
        IngestProgressSnapshotDialog dialog = new IngestProgressSnapshotDialog(this.getTopLevelAncestor(), true);
    }//GEN-LAST:event_bnShowProgressActionPerformed

    /**
     * Handles a click on the pause/resume auto ingest job button. Sends a
     * pause/resume request to the auto ingest manager.
     *
     * @param evt The button click event.
     */
    private void bnPauseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnPauseActionPerformed
        
        if (!autoIngestStarted) {
            //put up a wait cursor during the start up operation
            this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

            startUp();

            this.setCursor(null);
            //done for startup
            return;
        }
        if (!isPaused) {
            tbStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.bnPause.pausing"));
            pause(true);
        } else {
            resume();
        }
        isPaused = !isPaused;
    }//GEN-LAST:event_bnPauseActionPerformed

    /**
     * Handles a click on the options button. Displays the options window.
     *
     * @param evt The button click event.
     */
    private void bnOptionsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnOptionsActionPerformed
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        OptionsDisplayer.getDefault().open();
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }//GEN-LAST:event_bnOptionsActionPerformed

    /**
     * Handles a click on the cancel ingest module button. Cancels the currently
     * running data source level ingest module for the selected job.
     *
     * @param evt The button click event.
     */
    private void bnCancelModuleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnCancelModuleActionPerformed
        Object[] options = {
            org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "ConfirmationDialog.CancelModule"),
            org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "ConfirmationDialog.DoNotCancelModule")};
        int reply = JOptionPane.showOptionDialog(this,
                NbBundle.getMessage(AutoIngestDashboard.class, "ConfirmationDialog.CancelModuleAreYouSure"),
                NbBundle.getMessage(AutoIngestDashboard.class, "ConfirmationDialog.ConfirmCancellationHeader"),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[1]);
        if (reply == 0) {
            /*
             * Call setCursor on this to ensure it appears (if there is time to
             * see it).
             */
            this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            manager.cancelCurrentDataSourceLevelIngestModule();
            refreshTables();
            this.setCursor(Cursor.getDefaultCursor());
        }
    }//GEN-LAST:event_bnCancelModuleActionPerformed

    /**
     * Handles a click on the exit button. Shuts down auto ingest.
     *
     * @param evt The button click event.
     */
    private void bnExitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnExitActionPerformed
        shutdown();
    }//GEN-LAST:event_bnExitActionPerformed

    /**
     * Handle a click on the prioritize case button. Requests prioritization of
     * all of the auto ingest jobs for a case.
     *
     * @param evt The button click event.
     */
    private void bnPrioritizeCaseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnPrioritizeCaseActionPerformed
        if (pendingTableModel.getRowCount() > 0 && pendingTable.getSelectedRow() >= 0) {
            this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            String caseName = (pendingTableModel.getValueAt(pendingTable.getSelectedRow(), JobsTableModelColumns.CASE.ordinal())).toString();
            manager.prioritizeCase(caseName);
            refreshTables();
            pendingTable.clearSelection();
            enablePendingTableButtons(false);
            AutoIngestDashboard.this.setCursor(Cursor.getDefaultCursor());
        }
    }//GEN-LAST:event_bnPrioritizeCaseActionPerformed

    /**
     * Handles a click on the show log button. Displays the auto ingest job log
     * for a case in NotePad.
     *
     * @param evt The button click event.
     */
    private void bnShowCaseLogActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnShowCaseLogActionPerformed
        try {
            int selectedRow = completedTable.getSelectedRow();
            if (selectedRow != -1) {
                Path caseDirectoryPath = (Path) completedTableModel.getValueAt(selectedRow, JobsTableModelColumns.CASE_DIRECTORY_PATH.ordinal());
                if (null != caseDirectoryPath) {
                    Path pathToLog = AutoIngestJobLogger.getLogPath(caseDirectoryPath);
                    if (pathToLog.toFile().exists()) {
                        Desktop.getDesktop().edit(pathToLog.toFile());
                    } else {
                        JOptionPane.showMessageDialog(this, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.ShowLogFailed.Message"),
                                org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.ShowLogFailed.Title"), JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    MessageNotifyUtil.Message.warn("The case directory for this job has been deleted.");
                }
            }
        } catch (IOException ex) {
            SYS_LOGGER.log(Level.SEVERE, "Dashboard error attempting to display case auto ingest log", ex);
            Object[] options = {org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "DisplayLogDialog.okay")};
            JOptionPane.showOptionDialog(this,
                    org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "DisplayLogDialog.cannotFindLog"),
                    org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "DisplayLogDialog.unableToShowLogFile"),
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    options,
                    options[0]);
        }
    }//GEN-LAST:event_bnShowCaseLogActionPerformed

    private void bnPrioritizeJobActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnPrioritizeJobActionPerformed
        if (pendingTableModel.getRowCount() > 0 && pendingTable.getSelectedRow() >= 0) {
            this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            Path manifestFilePath = (Path) (pendingTableModel.getValueAt(pendingTable.getSelectedRow(), JobsTableModelColumns.MANIFEST_FILE_PATH.ordinal()));
            manager.prioritizeJob(manifestFilePath);
            refreshTables();
            pendingTable.clearSelection();
            enablePendingTableButtons(false);
            AutoIngestDashboard.this.setCursor(Cursor.getDefaultCursor());
        }
    }//GEN-LAST:event_bnPrioritizeJobActionPerformed

    private void bnOpenLogDirActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnOpenLogDirActionPerformed
        Path logDirPath = Paths.get(PlatformUtil.getUserDirectory().getAbsolutePath(), "var", "log");
        File logDir = logDirPath.toFile();
        try {
            Desktop.getDesktop().open(logDir);
        } catch (IOException ex) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    String.format("Unable to open log directory %s:\n%s", logDirPath, ex.getLocalizedMessage()),
                    NotifyDescriptor.ERROR_MESSAGE));
        }
    }//GEN-LAST:event_bnOpenLogDirActionPerformed

    private void bnReprocessJobActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnReprocessJobActionPerformed
        if (completedTableModel.getRowCount() < 0 || completedTable.getSelectedRow() < 0) {
            return;
        }
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        Path manifestPath = (Path) completedTableModel.getValueAt(completedTable.getSelectedRow(), JobsTableModelColumns.MANIFEST_FILE_PATH.ordinal());
        manager.reprocessJob(manifestPath);
        refreshTables();
        AutoIngestDashboard.this.setCursor(Cursor.getDefaultCursor());
    }//GEN-LAST:event_bnReprocessJobActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnCancelJob;
    private javax.swing.JButton bnCancelModule;
    private javax.swing.JButton bnDeleteCase;
    private javax.swing.JButton bnExit;
    private javax.swing.JButton bnOpenLogDir;
    private javax.swing.JButton bnOptions;
    private javax.swing.JButton bnPause;
    private javax.swing.JButton bnPrioritizeCase;
    private javax.swing.JButton bnPrioritizeJob;
    private javax.swing.JButton bnRefresh;
    private javax.swing.JButton bnReprocessJob;
    private javax.swing.JButton bnShowCaseLog;
    private javax.swing.JButton bnShowProgress;
    private javax.swing.JScrollPane completedScrollPane;
    private javax.swing.JTable completedTable;
    private javax.swing.JLabel lbCompleted;
    private javax.swing.JLabel lbPending;
    private javax.swing.JLabel lbRunning;
    private javax.swing.JLabel lbServicesStatus;
    private javax.swing.JLabel lbStatus;
    private javax.swing.JScrollPane pendingScrollPane;
    private javax.swing.JTable pendingTable;
    private javax.swing.JScrollPane runningScrollPane;
    private javax.swing.JTable runningTable;
    private javax.swing.JTextField tbServicesStatusMessage;
    private javax.swing.JTextField tbStatusMessage;
    // End of variables declaration//GEN-END:variables

}
