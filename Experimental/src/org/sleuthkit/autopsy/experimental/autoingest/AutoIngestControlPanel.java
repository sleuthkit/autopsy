/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2018 Basis Technology Corp.
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
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.core.ServicesMonitor;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestManager.CaseDeletionResult;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestManager.JobsSnapshot;
import org.sleuthkit.autopsy.guiutils.DurationCellRenderer;
import org.sleuthkit.autopsy.guiutils.LongDateCellRenderer;
import org.sleuthkit.autopsy.guiutils.StatusIconCellRenderer;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestProgressSnapshotDialog;

/**
 * A panel for monitoring automated ingest by a cluster, and for controlling
 * automated ingest for a single node within the cluster. There can be at most
 * one such panel per node.
 */
@Messages({
    "AutoIngestControlPanel.bnPause.paused=Paused",
    "AutoIngestControlPanel.bnPause.running=Running",
    "AutoIngestControlPanel.bnPause.confirmHeader=Are you sure you want to pause?",
    "AutoIngestControlPanel.bnPause.warningText=Pause will occur after the current job completes processing. This could take a long time. Continue?",
    "AutoIngestControlPanel.bnPause.toolTipText=Suspend processing of Pending Jobs",
    "AutoIngestControlPanel.bnPause.toolTipTextResume=Resume processing of Pending Jobs",
    "AutoIngestControlPanel.bnPause.pausing=Pausing after current job completes...",
    "AutoIngestControlPanel.bnStart.startMessage=Waiting to start",
    "AutoIngestControlPanel.bnStart.text=Start",
    "AutoIngestControlPanel.bnStart.toolTipText=Start processing auto ingest jobs",
    "AutoIngestControlPanel.pendingTable.toolTipText=The Pending table displays the order upcoming Jobs will be processed with the top of the list first",
    "AutoIngestControlPanel.runningTable.toolTipText=The Running table displays the currently running Job and information about it",
    "AutoIngestControlPanel.completedTable.toolTipText=The Completed table shows all Jobs that have been processed already",
    "AutoIngestControlPanel.bnCancelJob.toolTipText=Cancel processing of the current Job and move on to the next Job. This functionality is only available for jobs running on current AIM node.",
    "AutoIngestControlPanel.bnDeleteCase.toolTipText=Delete the selected Case in its entirety",
    "AutoIngestControlPanel.bnResume.text=Resume",
    "AutoIngestControlPanel.bnRefresh.toolTipText=Refresh displayed tables",
    "AutoIngestControlPanel.bnCancelModule.toolTipText=Cancel processing of the current module within the Job and move on to the next module within the Job. This functionality is only available for jobs running on current AIM node.",
    "AutoIngestControlPanel.bnExit.toolTipText=Exit Application",
    "AutoIngestControlPanel.bnOptions.toolTipText=Display options panel. All processing must be paused to open the options panel.",
    "AutoIngestControlPanel.bnShowProgress.toolTipText=Show the progress of the currently running Job. This functionality is only available for jobs running on current AIM node.",
    "AutoIngestControlPanel.bnShowCaseLog.toolTipText=Display case log file for selected case",
    "AutoIngestControlPanel.Cancelling=Cancelling...",
    "AutoIngestControlPanel.AutoIngestStartupWarning.Title=Automated Ingest Warning",
    "AutoIngestControlPanel.AutoIngestStartupWarning.Message=Failed to establish remote communications with other automated ingest nodes.\nAuto ingest dashboard will only be able to display local ingest job events.\nPlease verify Multi-User settings (Options->Multi-User). See application log for details.",
    "AutoIngestControlPanel.UpdatingSharedConfig=Updating shared configuration",
    "AutoIngestControlPanel.SharedConfigurationDisabled=Shared configuration disabled",
    "AutoIngestControlPanel.EnableConfigurationSettings=Enable shared configuration from the options panel before uploading",
    "AutoIngestControlPanel.ErrorUploadingConfiguration=Error uploading configuration",
    "AutoIngestControlPanel.UploadSuccessTitle=Success",
    "AutoIngestControlPanel.UploadSuccess=Shared configuration successfully uploaded",
    "AutoIngestControlPanel.UploadFailedTitle=Failed",
    "AutoIngestControlPanel.ConfigLocked=The shared configuration directory is locked because upload from another node is in progress. \nIf this is an error, you can unlock the directory and then retry the upload.",
    "AutoIngestControlPanel.ConfigLockedTitle=Configuration directory locked",
    "AutoIngestControlPanel.PauseDueToSystemError=Paused due to system error, please consult the auto ingest system log"
})
public final class AutoIngestControlPanel extends JPanel implements Observer {

    private static final long serialVersionUID = 1L;
    private static final int GENERIC_COL_MIN_WIDTH = 30;
    private static final int GENERIC_COL_MAX_WIDTH = 2000;
    private static final int PENDING_TABLE_COL_PREFERRED_WIDTH = 280;
    private static final int RUNNING_TABLE_COL_PREFERRED_WIDTH = 175;
    private static final int PRIORITY_COLUMN_PREFERRED_WIDTH = 60;
    private static final int PRIORITY_COLUMN_MAX_WIDTH = 150;
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
    private static AutoIngestControlPanel instance;
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
    @Messages({
        "AutoIngestControlPanel.JobsTableModel.ColumnHeader.Priority=Prioritized",
        "AutoIngestControlPanel.JobsTableModel.ColumnHeader.Case=Case",
        "AutoIngestControlPanel.JobsTableModel.ColumnHeader.ImageFolder=Data Source",
        "AutoIngestControlPanel.JobsTableModel.ColumnHeader.HostName=Host Name",
        "AutoIngestControlPanel.JobsTableModel.ColumnHeader.CreatedTime=Job Created",
        "AutoIngestControlPanel.JobsTableModel.ColumnHeader.StartedTime=Stage Started",
        "AutoIngestControlPanel.JobsTableModel.ColumnHeader.CompletedTime=Job Completed",
        "AutoIngestControlPanel.JobsTableModel.ColumnHeader.Stage=Stage",
        "AutoIngestControlPanel.JobsTableModel.ColumnHeader.StageTime=Time in Stage",
        "AutoIngestControlPanel.JobsTableModel.ColumnHeader.Status=Status",
        "AutoIngestControlPanel.JobsTableModel.ColumnHeader.CaseFolder=Case Folder",
        "AutoIngestControlPanel.JobsTableModel.ColumnHeader.LocalJob= Local Job?",
        "AutoIngestControlPanel.JobsTableModel.ColumnHeader.ManifestFilePath= Manifest File Path"
    })
    private enum JobsTableModelColumns {

        CASE(NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.JobsTableModel.ColumnHeader.Case")),
        DATA_SOURCE(NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.JobsTableModel.ColumnHeader.ImageFolder")),
        HOST_NAME(NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.JobsTableModel.ColumnHeader.HostName")),
        CREATED_TIME(NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.JobsTableModel.ColumnHeader.CreatedTime")),
        STARTED_TIME(NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.JobsTableModel.ColumnHeader.StartedTime")),
        COMPLETED_TIME(NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.JobsTableModel.ColumnHeader.CompletedTime")),
        STAGE(NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.JobsTableModel.ColumnHeader.Stage")),
        STAGE_TIME(NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.JobsTableModel.ColumnHeader.StageTime")),
        STATUS(NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.JobsTableModel.ColumnHeader.Status")),
        CASE_DIRECTORY_PATH(NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.JobsTableModel.ColumnHeader.CaseFolder")),
        IS_LOCAL_JOB(NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.JobsTableModel.ColumnHeader.LocalJob")),
        MANIFEST_FILE_PATH(NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.JobsTableModel.ColumnHeader.ManifestFilePath")),
        PRIORITY(NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.JobsTableModel.ColumnHeader.Priority"));
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
            MANIFEST_FILE_PATH.getColumnHeader(),
            PRIORITY.getColumnHeader()};
    }

    /**
     * Gets the singleton automated ingest control and monitoring panel for this
     * cluster node.
     *
     * @return The panel.
     */
    public static AutoIngestControlPanel getInstance() {
        if (null == instance) {
            /*
             * Two stage construction is used here to avoid publishing a
             * reference to the panel to the Observable auto ingest manager
             * before object construction is complete.
             */
            instance = new AutoIngestControlPanel();
        }
        return instance;
    }

    /**
     * Constructs a panel for monitoring automated ingest by a cluster, and for
     * controlling automated ingest for a single node within the cluster.
     */
    private AutoIngestControlPanel() {

        //Disable the main window so they can only use the dashboard (if we used setVisible the taskBar icon would go away)
        WindowManager.getDefault().getMainWindow().setEnabled(false);

        manager = AutoIngestManager.getInstance();

        pendingTableModel = new AutoIngestTableModel(JobsTableModelColumns.headers, 0);

        runningTableModel = new AutoIngestTableModel(JobsTableModelColumns.headers, 0);

        completedTableModel = new AutoIngestTableModel(JobsTableModelColumns.headers, 0);

        initComponents(); // Generated code.
        setServicesStatusMessage();
        initPendingJobsTable();
        initRunningJobsTable();
        initCompletedJobsTable();
        initButtons();
        completedTable.getRowSorter().toggleSortOrder(JobsTableModelColumns.COMPLETED_TIME.ordinal());
        /*
         * Must set this flag, otherwise pop up menus don't close properly.
         */
        UIManager.put("PopupMenu.consumeEventOnClose", false);
    }

    /**
     * Queries the services monitor and sets the text for the services status
     * text box.
     */
    @Messages({
        "# {0} - case db status", "# {1} - search svc Status", "# {2} - coord svc Status", "# {3} - msg broker status",
        "AutoIngestControlPanel.tbServicesStatusMessage.Message=Case databases {0}, keyword search {1}, coordination {2}, messaging {3} ",
        "AutoIngestControlPanel.tbServicesStatusMessage.Message.Up=up",
        "AutoIngestControlPanel.tbServicesStatusMessage.Message.Down=down",
        "AutoIngestControlPanel.tbServicesStatusMessage.Message.Unknown=unknown"
    })
    private void setServicesStatusMessage() {
        new SwingWorker<Void, Void>() {

            String caseDatabaseServerStatus = ServicesMonitor.ServiceStatus.DOWN.toString();
            String keywordSearchServiceStatus = ServicesMonitor.ServiceStatus.DOWN.toString();
            String messagingStatus = ServicesMonitor.ServiceStatus.DOWN.toString();

            @Override
            protected Void doInBackground() throws Exception {
                caseDatabaseServerStatus = getServiceStatus(ServicesMonitor.Service.REMOTE_CASE_DATABASE);
                keywordSearchServiceStatus = getServiceStatus(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH);
                messagingStatus = getServiceStatus(ServicesMonitor.Service.MESSAGING);
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
                String serviceStatus = NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.tbServicesStatusMessage.Message.Unknown");
                try {
                    ServicesMonitor servicesMonitor = ServicesMonitor.getInstance();
                    serviceStatus = servicesMonitor.getServiceStatus(service.toString());
                    if (serviceStatus.compareTo(ServicesMonitor.ServiceStatus.UP.toString()) == 0) {
                        serviceStatus = NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.tbServicesStatusMessage.Message.Up");
                    } else {
                        serviceStatus = NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.tbServicesStatusMessage.Message.Down");
                    }
                } catch (ServicesMonitor.ServicesMonitorException ex) {
                    SYS_LOGGER.log(Level.SEVERE, String.format("Dashboard error getting service status for %s", service), ex);
                }
                return serviceStatus;
            }

            @Override
            protected void done() {
                tbServicesStatusMessage.setText(NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.tbServicesStatusMessage.Message", caseDatabaseServerStatus, keywordSearchServiceStatus, keywordSearchServiceStatus, messagingStatus));
                String upStatus = NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.tbServicesStatusMessage.Message.Up");
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

        column = pendingTable.getColumn(JobsTableModelColumns.PRIORITY.getColumnHeader());
        column.setCellRenderer(new PrioritizedIconCellRenderer());
        column.setMaxWidth(PRIORITY_COLUMN_MAX_WIDTH);
        column.setPreferredWidth(PRIORITY_COLUMN_PREFERRED_WIDTH);
        column.setWidth(PRIORITY_COLUMN_PREFERRED_WIDTH);

        /**
         * Allow sorting when a column header is clicked.
         */
        pendingTable.setRowSorter(new AutoIngestRowSorter<>(pendingTableModel));

        /*
         * Create a row selection listener to enable/disable the prioritize
         * folder and prioritize case buttons.
         */
        pendingTable.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int row = pendingTable.getSelectedRow();
            boolean enablePrioritizeButtons = false;
            boolean enableDeprioritizeButtons = false;
            if ((row >= 0) && (row < pendingTable.getRowCount())) {
                enablePrioritizeButtons = true;
                enableDeprioritizeButtons = ((Integer) pendingTableModel.getValueAt(row, JobsTableModelColumns.PRIORITY.ordinal()) > 0);
            }
            enablePrioritizeButtons(enablePrioritizeButtons);
            enableDeprioritizeButtons(enableDeprioritizeButtons);
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
        runningTable.removeColumn(runningTable.getColumn(JobsTableModelColumns.PRIORITY.getColumnHeader()));
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
        int row = runningTable.convertRowIndexToModel(runningTable.getSelectedRow());
        if (row >= 0 && row < runningTable.getRowCount()) {
            if ((boolean) runningTable.getModel().getValueAt(row, JobsTableModelColumns.IS_LOCAL_JOB.ordinal())) {
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
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.HOST_NAME.getColumnHeader()));
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.STAGE.getColumnHeader()));
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.STAGE_TIME.getColumnHeader()));
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.IS_LOCAL_JOB.getColumnHeader()));
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.CASE_DIRECTORY_PATH.getColumnHeader()));
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.MANIFEST_FILE_PATH.getColumnHeader()));
        completedTable.removeColumn(completedTable.getColumn(JobsTableModelColumns.PRIORITY.getColumnHeader()));
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
        column.setCellRenderer(new StatusIconCellRenderer());
        column.setMinWidth(STATUS_COL_MIN_WIDTH);
        column.setMaxWidth(STATUS_COL_MAX_WIDTH);
        column.setPreferredWidth(STATUS_COL_PREFERRED_WIDTH);
        column.setWidth(STATUS_COL_PREFERRED_WIDTH);

        /*
         * Allow sorting when a column header is clicked.
         */
        completedTable.setRowSorter(new AutoIngestRowSorter<>(completedTableModel));

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
        enablePrioritizeButtons(false);
        enableDeprioritizeButtons(false);
        bnShowCaseLog.setEnabled(false);
        bnReprocessJob.setEnabled(false);
        bnPause.setText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnStart.text"));
        bnPause.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnStart.toolTipText"));
        bnPause.setEnabled(true);    //initial label for bnPause is 'Start' and it's enabled for user to start the process
        bnRefresh.setEnabled(false); //at initial stage, nothing to refresh
        enableRunningTableButtons(false);
        tbStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnStart.startMessage"));
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
     * Enables or disables prioritize buttons related to the pending jobs table.
     *
     * @param enable Enable/disable the buttons.
     */
    private void enablePrioritizeButtons(Boolean enable) {
        bnPrioritizeCase.setEnabled(enable);
        bnPrioritizeJob.setEnabled(enable);
    }

    /**
     * Enables or disables deprioritize buttons related to the pending jobs
     * table.
     *
     * @param enable Enable/disable the buttons.
     */
    private void enableDeprioritizeButtons(Boolean enable) {
        bnDeprioritizeCase.setEnabled(enable);
        bnDeprioritizeJob.setEnabled(enable);
    }

    /**
     * Starts up the auto ingest manager and adds this panel as an observer,
     * subscribes to services monitor events and starts a task to populate the
     * auto ingest job tables. The Refresh and Pause buttons are enabled.
     */
    @Messages({
        "AutoIngestControlPanel.AutoIngestStartupError=Failed to start automated ingest. Verify Multi-user Settings.",
        "AutoIngestControlPanel.AutoIngestStartupFailed.Message=Failed to start automated ingest.\nPlease see auto ingest system log for details.",
        "AutoIngestControlPanel.AutoIngestStartupFailed.Title=Automated Ingest Error",})
    private void startUp() {

        /*
         * Starts up the auto ingest manager (AIM).
         */
        try {
            manager.startUp();
            autoIngestStarted = true;
        } catch (AutoIngestManager.AutoIngestManagerException ex) {
            SYS_LOGGER.log(Level.SEVERE, "Dashboard error starting up auto ingest", ex);
            tbStatusMessage.setText(NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.AutoIngestStartupError"));
            manager = null;

            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.AutoIngestStartupFailed.Message"),
                    NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.AutoIngestStartupFailed.Title"),
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
        bnPause.setText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnPause.text"));
        bnPause.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnPause.toolTipText"));
        bnRefresh.setEnabled(true);
        bnOptions.setEnabled(false);

        tbStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnPause.running"));
    }

    /**
     * Shuts down auto ingest by shutting down the auto ingest manager and doing
     * an application exit.
     */
    @Messages({
        "AutoIngestControlPanel.OK=OK",
        "AutoIngestControlPanel.Cancel=Cancel",
        "AutoIngestControlPanel.ExitConsequences=This will cancel any currently running job on this host. Exiting while a job is running potentially leaves the case in an inconsistent or corrupted state.",
        "AutoIngestControlPanel.ExitingStatus=Exiting..."
    })
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
            NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.OK"),
            NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.Cancel")};
        int reply = JOptionPane.OK_OPTION;

        if (null != manager && IngestManager.getInstance().isIngestRunning()) {
            reply = JOptionPane.showOptionDialog(this,
                    NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.ExitConsequences"),
                    NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.ConfirmExitHeader"),
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
            tbStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.ExitingStatus"));

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
                    AutoIngestControlPanel.this.setCursor(Cursor.getDefaultCursor());
                    LifecycleManager.getDefault().exit();
                }
            }.execute();
        }
    }

    /**
     * @inheritDoc
     */
    @Messages({
        "AutoIngestControlPanel.PauseDueToDatabaseServiceDown=Paused, unable to communicate with case database service.",
        "AutoIngestControlPanel.PauseDueToKeywordSearchServiceDown=Paused, unable to communicate with keyword search service.",
        "AutoIngestControlPanel.PauseDueToCoordinationServiceDown=Paused, unable to communicate with coordination service.",
        "AutoIngestControlPanel.PauseDueToWriteStateFilesFailure=Paused, unable to write to shared images or cases location.",
        "AutoIngestControlPanel.PauseDueToSharedConfigError=Paused, unable to update shared configuration.",
        "AutoIngestControlPanel.PauseDueToIngestJobStartFailure=Paused, unable to start ingest job processing.",
        "AutoIngestControlPanel.PauseDueToFileExporterError=Paused, unable to load File Exporter settings.",})
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
                        tbStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnPause.paused"));
                        bnOptions.setEnabled(true);
                        bnRefresh.setEnabled(false);
                        isPaused = true;
                    });
                    break;
                case PAUSED_FOR_SYSTEM_ERROR:
                    EventQueue.invokeLater(() -> {
                        tbStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.PauseDueToSystemError"));
                        bnOptions.setEnabled(true);
                        bnRefresh.setEnabled(false);
                        pause(false);
                        isPaused = true;
                        setServicesStatusMessage();
                    });
                    break;
                case RESUMED:
                    EventQueue.invokeLater(() -> {
                        tbStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnPause.running"));
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
        bnPause.setText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnResume.text"));
        bnPause.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnPause.toolTipTextResume"));

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
        bnPause.setText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnPause.text"));
        bnPause.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnPause.toolTipText"));
        tbStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnPause.running"));
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
                Path currentRow = getSelectedEntry(pendingTable);
                refreshTable(pendingJobs, (DefaultTableModel) pendingTable.getModel(), null);
                setSelectedEntry(pendingTable, currentRow);
            }

            if (null != runningJobs) {
                if (!isLocalJobRunning()) {
                    enableRunningTableButtons(false);
                } else {
                    updateRunningTableButtonsBasedOnSelectedRow();
                }
                Path currentRow = getSelectedEntry(runningTable);
                refreshTable(runningJobs, (DefaultTableModel) runningTable.getModel(), null);
                setSelectedEntry(runningTable, currentRow);
            }

            if (null != completedJobs) {
                Path currentRow = getSelectedEntry(completedTable);
                refreshTable(completedJobs, (DefaultTableModel) completedTable.getModel(), null);
                setSelectedEntry(completedTable, currentRow);
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
            return job.getProcessingHostName().equals(LOCAL_HOST_NAME);
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
        Path getSelectedEntry(JTable table) {
            try {
                int currentlySelectedRow = table.convertRowIndexToModel(table.getSelectedRow());
                if (currentlySelectedRow >= 0 && currentlySelectedRow < table.getRowCount()) {
                    return Paths.get(table.getModel().getValueAt(currentlySelectedRow, JobsTableModelColumns.CASE.ordinal()).toString(),
                            table.getModel().getValueAt(currentlySelectedRow, JobsTableModelColumns.DATA_SOURCE.ordinal()).toString());
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
        void setSelectedEntry(JTable table, Path path) {
            if (path != null) {
                try {
                    for (int row = 0; row < table.getRowCount(); ++row) {
                        Path temp = Paths.get(table.getModel().getValueAt(row, JobsTableModelColumns.CASE.ordinal()).toString(),
                                table.getModel().getValueAt(row, JobsTableModelColumns.DATA_SOURCE.ordinal()).toString());
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
                AutoIngestJob.StageDetails status = job.getProcessingStageDetails();
                tableModel.addRow(new Object[]{
                    job.getManifest().getCaseName(), // CASE
                    job.getManifest().getDataSourcePath().getFileName(), // DATA_SOURCE
                    job.getProcessingHostName(), // HOST_NAME
                    job.getManifest().getDateFileCreated(), // CREATED_TIME
                    job.getProcessingStageStartDate(), // STARTED_TIME
                    job.getCompletedDate(), // COMPLETED_TIME
                    status.getDescription(), // ACTIVITY
                    job.getErrorsOccurred() ? StatusIconCellRenderer.Status.WARNING : StatusIconCellRenderer.Status.OK, // STATUS
                    ((Date.from(Instant.now()).getTime()) - (status.getStartDate().getTime())), // ACTIVITY_TIME
                    job.getCaseDirectoryPath(), // CASE_DIRECTORY_PATH
                    job.getProcessingHostName().equals(LOCAL_HOST_NAME), // IS_LOCAL_JOB
                    job.getManifest().getFilePath(), // MANIFEST_FILE_PATH
                    job.getPriority()}); // PRIORITY 
            }
        } catch (Exception ex) {
            SYS_LOGGER.log(Level.SEVERE, "Dashboard error refreshing table", ex);
        }
    }

    /**
     * Get the current lists of jobs and update the UI.
     */
    private void refreshTables() {
        JobsSnapshot jobsSnapshot = manager.getCurrentJobsSnapshot();
        refreshTable(jobsSnapshot.getCompletedJobs(), (DefaultTableModel) completedTable.getModel(), null);
        refreshTable(jobsSnapshot.getPendingJobs(), (DefaultTableModel) pendingTable.getModel(), null);
        refreshTable(jobsSnapshot.getRunningJobs(), (DefaultTableModel) runningTable.getModel(), null);
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
        bnClusterMetrics = new javax.swing.JButton();
        bnReprocessJob = new javax.swing.JButton();
        bnDeprioritizeCase = new javax.swing.JButton();
        bnDeprioritizeJob = new javax.swing.JButton();

        pendingTable.setModel(pendingTableModel);
        pendingTable.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.pendingTable.toolTipText")); // NOI18N
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
        runningTable.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.runningTable.toolTipText")); // NOI18N
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
        completedTable.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.completedTable.toolTipText")); // NOI18N
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

        org.openide.awt.Mnemonics.setLocalizedText(bnCancelJob, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnCancelJob.text")); // NOI18N
        bnCancelJob.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnCancelJob.toolTipText")); // NOI18N
        bnCancelJob.setMaximumSize(new java.awt.Dimension(162, 23));
        bnCancelJob.setMinimumSize(new java.awt.Dimension(162, 23));
        bnCancelJob.setPreferredSize(new java.awt.Dimension(162, 23));
        bnCancelJob.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnCancelJobActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnDeleteCase, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnDeleteCase.text")); // NOI18N
        bnDeleteCase.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnDeleteCase.toolTipText")); // NOI18N
        bnDeleteCase.setMaximumSize(new java.awt.Dimension(162, 23));
        bnDeleteCase.setMinimumSize(new java.awt.Dimension(162, 23));
        bnDeleteCase.setPreferredSize(new java.awt.Dimension(162, 23));
        bnDeleteCase.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnDeleteCaseActionPerformed(evt);
            }
        });

        lbPending.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbPending, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.lbPending.text")); // NOI18N

        lbRunning.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbRunning, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.lbRunning.text")); // NOI18N

        lbCompleted.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbCompleted, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.lbCompleted.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnRefresh, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnRefresh.text")); // NOI18N
        bnRefresh.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnRefresh.toolTipText")); // NOI18N
        bnRefresh.setMaximumSize(new java.awt.Dimension(162, 23));
        bnRefresh.setMinimumSize(new java.awt.Dimension(162, 23));
        bnRefresh.setPreferredSize(new java.awt.Dimension(162, 23));
        bnRefresh.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnRefreshActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnCancelModule, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnCancelModule.text")); // NOI18N
        bnCancelModule.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnCancelModule.toolTipText")); // NOI18N
        bnCancelModule.setMaximumSize(new java.awt.Dimension(162, 23));
        bnCancelModule.setMinimumSize(new java.awt.Dimension(162, 23));
        bnCancelModule.setPreferredSize(new java.awt.Dimension(162, 23));
        bnCancelModule.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnCancelModuleActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnExit, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnExit.text")); // NOI18N
        bnExit.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnExit.toolTipText")); // NOI18N
        bnExit.setMaximumSize(new java.awt.Dimension(162, 23));
        bnExit.setMinimumSize(new java.awt.Dimension(162, 23));
        bnExit.setPreferredSize(new java.awt.Dimension(162, 23));
        bnExit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnExitActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnOptions, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnOptions.text")); // NOI18N
        bnOptions.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnOptions.toolTipText")); // NOI18N
        bnOptions.setEnabled(false);
        bnOptions.setMaximumSize(new java.awt.Dimension(162, 23));
        bnOptions.setMinimumSize(new java.awt.Dimension(162, 23));
        bnOptions.setPreferredSize(new java.awt.Dimension(162, 23));
        bnOptions.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnOptionsActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnShowProgress, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnShowProgress.text")); // NOI18N
        bnShowProgress.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnShowProgress.toolTipText")); // NOI18N
        bnShowProgress.setMaximumSize(new java.awt.Dimension(162, 23));
        bnShowProgress.setMinimumSize(new java.awt.Dimension(162, 23));
        bnShowProgress.setPreferredSize(new java.awt.Dimension(162, 23));
        bnShowProgress.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnShowProgressActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnPause, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnPause.text")); // NOI18N
        bnPause.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnPause.toolTipText")); // NOI18N
        bnPause.setMaximumSize(new java.awt.Dimension(162, 23));
        bnPause.setMinimumSize(new java.awt.Dimension(162, 23));
        bnPause.setPreferredSize(new java.awt.Dimension(162, 23));
        bnPause.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnPauseActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnPrioritizeCase, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnPrioritizeCase.text")); // NOI18N
        bnPrioritizeCase.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnPrioritizeCase.toolTipText")); // NOI18N
        bnPrioritizeCase.setMaximumSize(new java.awt.Dimension(162, 23));
        bnPrioritizeCase.setMinimumSize(new java.awt.Dimension(162, 23));
        bnPrioritizeCase.setPreferredSize(new java.awt.Dimension(162, 23));
        bnPrioritizeCase.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnPrioritizeCaseActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnShowCaseLog, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnShowCaseLog.text")); // NOI18N
        bnShowCaseLog.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnShowCaseLog.toolTipText")); // NOI18N
        bnShowCaseLog.setMaximumSize(new java.awt.Dimension(162, 23));
        bnShowCaseLog.setMinimumSize(new java.awt.Dimension(162, 23));
        bnShowCaseLog.setPreferredSize(new java.awt.Dimension(162, 23));
        bnShowCaseLog.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnShowCaseLogActionPerformed(evt);
            }
        });

        tbStatusMessage.setEditable(false);
        tbStatusMessage.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        tbStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.tbStatusMessage.text")); // NOI18N
        tbStatusMessage.setBorder(null);

        lbStatus.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbStatus, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.lbStatus.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnPrioritizeJob, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnPrioritizeJob.text")); // NOI18N
        bnPrioritizeJob.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnPrioritizeJob.toolTipText")); // NOI18N
        bnPrioritizeJob.setActionCommand(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnPrioritizeJob.actionCommand")); // NOI18N
        bnPrioritizeJob.setMaximumSize(new java.awt.Dimension(162, 23));
        bnPrioritizeJob.setMinimumSize(new java.awt.Dimension(162, 23));
        bnPrioritizeJob.setPreferredSize(new java.awt.Dimension(162, 23));
        bnPrioritizeJob.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnPrioritizeJobActionPerformed(evt);
            }
        });

        lbServicesStatus.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbServicesStatus, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.lbServicesStatus.text")); // NOI18N

        tbServicesStatusMessage.setEditable(false);
        tbServicesStatusMessage.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        tbServicesStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.tbServicesStatusMessage.text")); // NOI18N
        tbServicesStatusMessage.setBorder(null);

        org.openide.awt.Mnemonics.setLocalizedText(bnOpenLogDir, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnOpenLogDir.text")); // NOI18N
        bnOpenLogDir.setMaximumSize(new java.awt.Dimension(162, 23));
        bnOpenLogDir.setMinimumSize(new java.awt.Dimension(162, 23));
        bnOpenLogDir.setPreferredSize(new java.awt.Dimension(162, 23));
        bnOpenLogDir.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnOpenLogDirActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnClusterMetrics, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnClusterMetrics.text")); // NOI18N
        bnClusterMetrics.setMaximumSize(new java.awt.Dimension(162, 23));
        bnClusterMetrics.setMinimumSize(new java.awt.Dimension(162, 23));
        bnClusterMetrics.setPreferredSize(new java.awt.Dimension(162, 23));
        bnClusterMetrics.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnClusterMetricsActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnReprocessJob, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnReprocessJob.text")); // NOI18N
        bnReprocessJob.setMaximumSize(new java.awt.Dimension(162, 23));
        bnReprocessJob.setMinimumSize(new java.awt.Dimension(162, 23));
        bnReprocessJob.setPreferredSize(new java.awt.Dimension(162, 23));
        bnReprocessJob.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnReprocessJobActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnDeprioritizeCase, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnDeprioritizeCase.text")); // NOI18N
        bnDeprioritizeCase.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnDeprioritizeCase.toolTipText")); // NOI18N
        bnDeprioritizeCase.setMaximumSize(new java.awt.Dimension(162, 23));
        bnDeprioritizeCase.setMinimumSize(new java.awt.Dimension(162, 23));
        bnDeprioritizeCase.setPreferredSize(new java.awt.Dimension(162, 23));
        bnDeprioritizeCase.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnDeprioritizeCaseActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnDeprioritizeJob, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnDeprioritizeJob.text")); // NOI18N
        bnDeprioritizeJob.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnDeprioritizeJob.toolTipText")); // NOI18N
        bnDeprioritizeJob.setActionCommand(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnDeprioritizeJob.actionCommand")); // NOI18N
        bnDeprioritizeJob.setMaximumSize(new java.awt.Dimension(162, 23));
        bnDeprioritizeJob.setMinimumSize(new java.awt.Dimension(162, 23));
        bnDeprioritizeJob.setPreferredSize(new java.awt.Dimension(162, 23));
        bnDeprioritizeJob.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnDeprioritizeJobActionPerformed(evt);
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
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(bnPause, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(bnRefresh, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(bnOptions, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(bnOpenLogDir, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(bnClusterMetrics, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(bnExit, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
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
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbPending)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(runningScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 1021, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(completedScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 1021, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(bnCancelJob, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(bnShowProgress, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(bnCancelModule, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(bnDeleteCase, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(bnShowCaseLog, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(bnReprocessJob, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(pendingScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 1021, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(bnPrioritizeCase, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(bnPrioritizeJob, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(bnDeprioritizeCase, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(bnDeprioritizeJob, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {bnCancelJob, bnCancelModule, bnDeleteCase, bnShowProgress});

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {bnClusterMetrics, bnExit, bnOpenLogDir, bnOptions, bnPause, bnRefresh});

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
                        .addGap(48, 48, 48)
                        .addComponent(bnPrioritizeCase, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bnDeprioritizeCase, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(30, 30, 30)
                        .addComponent(bnPrioritizeJob, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bnDeprioritizeJob, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lbRunning)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(34, 34, 34)
                        .addComponent(bnShowProgress, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bnCancelJob, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bnCancelModule, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(runningScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 133, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(68, 68, 68)
                        .addComponent(bnReprocessJob, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bnDeleteCase, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bnShowCaseLog, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lbCompleted)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(completedScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 179, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(bnPause, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(bnRefresh, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(bnOptions, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(bnOpenLogDir, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(bnClusterMetrics, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(bnExit, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {bnCancelJob, bnCancelModule, bnClusterMetrics, bnDeleteCase, bnExit, bnOpenLogDir, bnOptions, bnPrioritizeCase, bnPrioritizeJob, bnRefresh, bnShowProgress});

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
    @Messages({
        "AutoIngestControlPanel.DeletionFailed=Deletion failed for job"
    })
    private void bnDeleteCaseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnDeleteCaseActionPerformed
        if (completedTable.getModel().getRowCount() < 0 || completedTable.getSelectedRow() < 0) {
            return;
        }

        String caseName = (String) completedTable.getModel().getValueAt(completedTable.convertRowIndexToModel(completedTable.getSelectedRow()), JobsTableModelColumns.CASE.ordinal());
        Object[] options = {
            org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.Delete"),
            org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.DoNotDelete")
        };
        Object[] msgContent = {org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.DeleteAreYouSure") + "\"" + caseName + "\"?"};
        int reply = JOptionPane.showOptionDialog(this,
                msgContent,
                org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.ConfirmDeletionHeader"),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[JOptionPane.NO_OPTION]);
        if (reply == JOptionPane.YES_OPTION) {
            bnDeleteCase.setEnabled(false);
            bnShowCaseLog.setEnabled(false);
            if (completedTable.getModel().getRowCount() > 0 && completedTable.getSelectedRow() >= 0) {
                Path caseDirectoryPath = (Path) completedTable.getModel().getValueAt(completedTable.convertRowIndexToModel(completedTable.getSelectedRow()), JobsTableModelColumns.CASE_DIRECTORY_PATH.ordinal());
                completedTable.clearSelection();
                this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                CaseDeletionResult result = manager.deleteCase(caseName, caseDirectoryPath);
                refreshTables();
                this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                if (CaseDeletionResult.FAILED == result) {
                    JOptionPane.showMessageDialog(this,
                            String.format("Could not delete case %s. It may be in in use.", caseName),
                            org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.DeletionFailed"),
                            JOptionPane.INFORMATION_MESSAGE);
                } else if (CaseDeletionResult.PARTIALLY_DELETED == result) {
                    JOptionPane.showMessageDialog(this,
                            String.format("Could not fully delete case %s. See system log for details.", caseName),
                            org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.DeletionFailed"),
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
            org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.CancelJob"),
            org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.DoNotCancelJob")};
        int reply = JOptionPane.showOptionDialog(this,
                NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.CancelJobAreYouSure"),
                NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.ConfirmCancellationHeader"),
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
            tbStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.bnPause.pausing"));
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
            org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.CancelModule"),
            org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.DoNotCancelModule")};
        int reply = JOptionPane.showOptionDialog(this,
                NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.CancelModuleAreYouSure"),
                NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.ConfirmCancellationHeader"),
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
    @Messages({"AutoIngestControlPanel.errorMessage.casePrioritization=An error occurred when prioritizing the case. Some or all jobs may not have been prioritized."})
    private void bnPrioritizeCaseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnPrioritizeCaseActionPerformed
        if (pendingTable.getModel().getRowCount() > 0 && pendingTable.getSelectedRow() >= 0) {
            this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

            String caseName = (pendingTable.getModel().getValueAt(pendingTable.convertRowIndexToModel(pendingTable.getSelectedRow()), JobsTableModelColumns.CASE.ordinal())).toString();
            try {
                manager.prioritizeCase(caseName);
            } catch (AutoIngestManager.AutoIngestManagerException ex) {
                SYS_LOGGER.log(Level.SEVERE, "Error prioritizing a case", ex);
                MessageNotifyUtil.Message.error(Bundle.AutoIngestControlPanel_errorMessage_casePrioritization());
            }
            refreshTables();
            pendingTable.clearSelection();
            enablePrioritizeButtons(false);
            enableDeprioritizeButtons(false);
            AutoIngestControlPanel.this.setCursor(Cursor.getDefaultCursor());
        }
    }//GEN-LAST:event_bnPrioritizeCaseActionPerformed

    /**
     * Handles a click on the show log button. Displays the auto ingest job log
     * for a case in NotePad.
     *
     * @param evt The button click event.
     */
    @Messages({
        "AutoIngestControlPanel.ShowLogFailed.Title=Unable to display case log",
        "AutoIngestControlPanel.ShowLogFailed.Message=Case log file does not exist"
    })
    private void bnShowCaseLogActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnShowCaseLogActionPerformed
        try {
            int selectedRow = completedTable.convertRowIndexToModel(completedTable.getSelectedRow());
            if (selectedRow != -1) {
                Path caseDirectoryPath = (Path) completedTable.getModel().getValueAt(selectedRow, JobsTableModelColumns.CASE_DIRECTORY_PATH.ordinal());
                if (null != caseDirectoryPath) {
                    Path pathToLog = AutoIngestJobLogger.getLogPath(caseDirectoryPath);
                    if (pathToLog.toFile().exists()) {
                        Desktop.getDesktop().edit(pathToLog.toFile());
                    } else {
                        JOptionPane.showMessageDialog(this, org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.ShowLogFailed.Message"),
                                org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.ShowLogFailed.Title"), JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    MessageNotifyUtil.Message.warn("The case directory for this job has been deleted.");
                }
            }
        } catch (IOException ex) {
            SYS_LOGGER.log(Level.SEVERE, "Dashboard error attempting to display case auto ingest log", ex);
            Object[] options = {org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "DisplayLogDialog.okay")};
            JOptionPane.showOptionDialog(this,
                    org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "DisplayLogDialog.cannotFindLog"),
                    org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "DisplayLogDialog.unableToShowLogFile"),
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    options,
                    options[0]);
        }
    }//GEN-LAST:event_bnShowCaseLogActionPerformed

    @Messages({"AutoIngestControlPanel.errorMessage.jobPrioritization=An error occurred when prioritizing the job."})
    private void bnPrioritizeJobActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnPrioritizeJobActionPerformed
        if (pendingTable.getModel().getRowCount() > 0 && pendingTable.getSelectedRow() >= 0) {
            this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            Path manifestFilePath = (Path) (pendingTable.getModel().getValueAt(pendingTable.convertRowIndexToModel(pendingTable.getSelectedRow()), JobsTableModelColumns.MANIFEST_FILE_PATH.ordinal()));
            try {
                manager.prioritizeJob(manifestFilePath);
            } catch (AutoIngestManager.AutoIngestManagerException ex) {
                SYS_LOGGER.log(Level.SEVERE, "Error prioritizing a job", ex);
                MessageNotifyUtil.Message.error(Bundle.AutoIngestControlPanel_errorMessage_jobPrioritization());
            }
            refreshTables();
            pendingTable.clearSelection();
            enablePrioritizeButtons(false);
            enableDeprioritizeButtons(false);
            AutoIngestControlPanel.this.setCursor(Cursor.getDefaultCursor());
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
        if (completedTable.getModel().getRowCount() < 0 || completedTable.getSelectedRow() < 0) {
            return;
        }
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        Path manifestPath = (Path) completedTable.getModel().getValueAt(completedTable.convertRowIndexToModel(completedTable.getSelectedRow()), JobsTableModelColumns.MANIFEST_FILE_PATH.ordinal());
        manager.reprocessJob(manifestPath);
        refreshTables();
        AutoIngestControlPanel.this.setCursor(Cursor.getDefaultCursor());
    }//GEN-LAST:event_bnReprocessJobActionPerformed

    private void bnClusterMetricsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnClusterMetricsActionPerformed
        new AutoIngestMetricsDialog(this.getTopLevelAncestor());
    }//GEN-LAST:event_bnClusterMetricsActionPerformed

    @Messages({"AutoIngestControlPanel.errorMessage.caseDeprioritization=An error occurred when deprioritizing the case. Some or all jobs may not have been deprioritized."})
    private void bnDeprioritizeCaseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnDeprioritizeCaseActionPerformed
        if (pendingTable.getModel().getRowCount() > 0 && pendingTable.getSelectedRow() >= 0) {
            this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

            String caseName = (pendingTable.getModel().getValueAt(pendingTable.convertRowIndexToModel(pendingTable.getSelectedRow()), JobsTableModelColumns.CASE.ordinal())).toString();
            try {
                manager.deprioritizeCase(caseName);
            } catch (AutoIngestManager.AutoIngestManagerException ex) {
                SYS_LOGGER.log(Level.SEVERE, "Error deprioritizing a case", ex);
                MessageNotifyUtil.Message.error(Bundle.AutoIngestControlPanel_errorMessage_caseDeprioritization());
            }
            refreshTables();
            pendingTable.clearSelection();
            enablePrioritizeButtons(false);
            enableDeprioritizeButtons(false);
            AutoIngestControlPanel.this.setCursor(Cursor.getDefaultCursor());
        }
    }//GEN-LAST:event_bnDeprioritizeCaseActionPerformed

    @Messages({"AutoIngestControlPanel.errorMessage.jobDeprioritization=An error occurred when deprioritizing the job."})
    private void bnDeprioritizeJobActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnDeprioritizeJobActionPerformed
        if (pendingTable.getModel().getRowCount() > 0 && pendingTable.getSelectedRow() >= 0) {
            this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            Path manifestFilePath = (Path) (pendingTable.getModel().getValueAt(pendingTable.convertRowIndexToModel(pendingTable.getSelectedRow()), JobsTableModelColumns.MANIFEST_FILE_PATH.ordinal()));
            try {
                manager.deprioritizeJob(manifestFilePath);
            } catch (AutoIngestManager.AutoIngestManagerException ex) {
                SYS_LOGGER.log(Level.SEVERE, "Error deprioritizing a job", ex);
                MessageNotifyUtil.Message.error(Bundle.AutoIngestControlPanel_errorMessage_jobDeprioritization());
            }
            refreshTables();
            pendingTable.clearSelection();
            enablePrioritizeButtons(false);
            enableDeprioritizeButtons(false);
            AutoIngestControlPanel.this.setCursor(Cursor.getDefaultCursor());
        }
    }//GEN-LAST:event_bnDeprioritizeJobActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnCancelJob;
    private javax.swing.JButton bnCancelModule;
    private javax.swing.JButton bnClusterMetrics;
    private javax.swing.JButton bnDeleteCase;
    private javax.swing.JButton bnDeprioritizeCase;
    private javax.swing.JButton bnDeprioritizeJob;
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

    private class AutoIngestTableModel extends DefaultTableModel {

        private static final long serialVersionUID = 1L;

        private AutoIngestTableModel(String[] headers, int i) {
            super(headers, i);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == JobsTableModelColumns.PRIORITY.ordinal()) {
                return Integer.class;
            } else if (columnIndex == JobsTableModelColumns.CREATED_TIME.ordinal()
                    || columnIndex == JobsTableModelColumns.COMPLETED_TIME.ordinal()
                    || columnIndex == JobsTableModelColumns.STARTED_TIME.ordinal()
                    || columnIndex == JobsTableModelColumns.STAGE_TIME.ordinal()) {
                return Date.class;
            } else if (columnIndex == JobsTableModelColumns.STATUS.ordinal()) {
                return Boolean.class;
            } else {
                return super.getColumnClass(columnIndex);
            }
        }
    }
}
