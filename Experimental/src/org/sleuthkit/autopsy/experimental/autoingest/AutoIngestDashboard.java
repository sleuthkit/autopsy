/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.util.logging.Level;
import java.awt.Color;
import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.core.ServicesMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestNodeRefreshEvents.RefreshChildrenEvent;

/**
 * A dashboard for monitoring an automated ingest cluster.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class AutoIngestDashboard extends JPanel implements Observer {

    private final static String ADMIN_ACCESS_FILE_NAME = "admin"; // NON-NLS
    private final static String ADMIN_ACCESS_FILE_PATH = Paths.get(PlatformUtil.getUserConfigDirectory(), ADMIN_ACCESS_FILE_NAME).toString();
    private final static String ADMIN_EXT_ACCESS_FILE_NAME = "adminext"; // NON-NLS
    private final static String ADMIN_EXT_ACCESS_FILE_PATH = Paths.get(PlatformUtil.getUserConfigDirectory(), ADMIN_EXT_ACCESS_FILE_NAME).toString();
    private final static String AID_REFRESH_THREAD_NAME = "AID-refresh-jobs-%d";
    private final static int AID_REFRESH_INTERVAL_SECS = 30;
    private final static int AID_DELAY_BEFORE_FIRST_REFRESH = 0;
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(AutoIngestDashboard.class.getName());
    private AutoIngestMonitor autoIngestMonitor;
    private AutoIngestJobsPanel pendingJobsPanel;
    private AutoIngestJobsPanel runningJobsPanel;
    private AutoIngestJobsPanel completedJobsPanel;
    private final ScheduledThreadPoolExecutor scheduledRefreshThreadPoolExecutor;
    private AtomicBoolean scheduledRefreshStarted = new AtomicBoolean(false);

    /**
     * Maintain a mapping of each service to it's last status update.
     */
    private final ConcurrentHashMap<String, String> statusByService;

    /**
     * Creates a dashboard for monitoring an automated ingest cluster.
     *
     * @return The dashboard.
     *
     * @throws AutoIngestDashboardException If there is a problem creating the
     *                                      dashboard.
     */
    public static AutoIngestDashboard createDashboard() throws AutoIngestDashboardException {
        AutoIngestDashboard dashBoard = new AutoIngestDashboard();
        try {
            dashBoard.startUp();
        } catch (AutoIngestMonitor.AutoIngestMonitorException ex) {
            throw new AutoIngestDashboardException("Error starting up auto ingest dashboard", ex);
        }
        return dashBoard;
    }

    /**
     * Constructs a panel for monitoring an automated ingest cluster.
     */
    @Messages({"AutoIngestDashboard.pendingTable.toolTipText=The Pending table displays the order upcoming Jobs will be processed with the top of the list first",
        "AutoIngestDashboard.runningTable.toolTipText=The Running table displays the currently running Job and information about it",
        "AutoIngestDashboard.completedTable.toolTipText=The Completed table shows all Jobs that have been processed already"})

    private AutoIngestDashboard() {
        this.statusByService = new ConcurrentHashMap<>();
        scheduledRefreshThreadPoolExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat(AID_REFRESH_THREAD_NAME).build());
        initComponents();
        statusByService.put(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString(), NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Down"));
        statusByService.put(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.toString(), NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Down"));
        statusByService.put(ServicesMonitor.Service.MESSAGING.toString(), NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Down"));
        setServicesStatusMessage();
        pendingJobsPanel = new AutoIngestJobsPanel(AutoIngestJobsNode.AutoIngestJobStatus.PENDING_JOB);
        pendingJobsPanel.setSize(pendingScrollPane.getSize());
        pendingScrollPane.add(pendingJobsPanel);
        pendingScrollPane.setViewportView(pendingJobsPanel);
        pendingJobsPanel.setToolTipText(Bundle.AutoIngestDashboard_pendingTable_toolTipText());
        runningJobsPanel = new AutoIngestJobsPanel(AutoIngestJobsNode.AutoIngestJobStatus.RUNNING_JOB);
        runningJobsPanel.setSize(runningScrollPane.getSize());
        runningScrollPane.add(runningJobsPanel);
        runningScrollPane.setViewportView(runningJobsPanel);
        runningJobsPanel.setToolTipText(Bundle.AutoIngestDashboard_runningTable_toolTipText());
        completedJobsPanel = new AutoIngestJobsPanel(AutoIngestJobsNode.AutoIngestJobStatus.COMPLETED_JOB);
        completedJobsPanel.setSize(completedScrollPane.getSize());
        completedScrollPane.add(completedJobsPanel);
        completedScrollPane.setViewportView(completedJobsPanel);
        completedJobsPanel.setToolTipText(Bundle.AutoIngestDashboard_completedTable_toolTipText());
        /*
         * Must set this flag, otherwise pop up menus don't close properly.
         */

        UIManager.put("PopupMenu.consumeEventOnClose", false);
    }

    AutoIngestMonitor getMonitor() {
        return autoIngestMonitor;
    }

    AutoIngestJobsPanel getPendingJobsPanel() {
        return pendingJobsPanel;
    }

    AutoIngestJobsPanel getRunningJobsPanel() {
        return runningJobsPanel;
    }

    AutoIngestJobsPanel getCompletedJobsPanel() {
        return completedJobsPanel;
    }

    /**
     * Update status of the services on the dashboard
     */
    private void displayServicesStatus() {
        tbServicesStatusMessage.setText(NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message",
                statusByService.get(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString()),
                statusByService.get(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.toString()),
                statusByService.get(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.toString()),
                statusByService.get(ServicesMonitor.Service.MESSAGING.toString())));
        String upStatus = NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Up");
        if (statusByService.get(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString()).compareTo(upStatus) != 0
                || statusByService.get(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.toString()).compareTo(upStatus) != 0
                || statusByService.get(ServicesMonitor.Service.MESSAGING.toString()).compareTo(upStatus) != 0) {
            tbServicesStatusMessage.setForeground(Color.RED);
        } else {
            tbServicesStatusMessage.setForeground(Color.BLACK);
        }
    }

    /**
     * Queries the services monitor and sets the text for the services status
     * text box.
     */
    private void setServicesStatusMessage() {
        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() throws Exception {
                statusByService.put(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString(), getServiceStatus(ServicesMonitor.Service.REMOTE_CASE_DATABASE));
                statusByService.put(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.toString(), getServiceStatus(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH));
                statusByService.put(ServicesMonitor.Service.MESSAGING.toString(), getServiceStatus(ServicesMonitor.Service.MESSAGING));
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
                    LOGGER.log(Level.SEVERE, String.format("Dashboard error getting service status for %s", service), ex);
                }
                return serviceStatus;
            }

            @Override
            protected void done() {
                displayServicesStatus();
            }

        }.execute();
    }

    /**
     * Starts up the auto ingest monitor and adds this panel as an observer,
     * subscribes to services monitor events and starts a task to populate the
     * auto ingest job tables.
     */
    private void startUp() throws AutoIngestMonitor.AutoIngestMonitorException {

        PropertyChangeListener propChangeListener = (PropertyChangeEvent evt) -> {

            String serviceDisplayName = ServicesMonitor.Service.valueOf(evt.getPropertyName()).toString();
            String status = evt.getNewValue().toString();

            if (status.equals(ServicesMonitor.ServiceStatus.UP.toString())) {
                status = NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Up");
                LOGGER.log(Level.INFO, "Connection to {0} is up", serviceDisplayName); //NON-NLS
            } else if (status.equals(ServicesMonitor.ServiceStatus.DOWN.toString())) {
                status = NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.Message.Down");
                LOGGER.log(Level.SEVERE, "Connection to {0} is down", serviceDisplayName); //NON-NLS
            } else {
                LOGGER.log(Level.INFO, "Status for {0} is {1}", new Object[]{serviceDisplayName, status}); //NON-NLS
            }

            // if the status update is for an existing service who's status hasn't changed - do nothing.       
            if (statusByService.containsKey(serviceDisplayName) && status.equals(statusByService.get(serviceDisplayName))) {
                return;
            }

            statusByService.put(serviceDisplayName, status);
            displayServicesStatus();
        };

        // Subscribe to all multi-user services in order to display their status
        Set<String> servicesList = new HashSet<>();
        servicesList.add(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString());
        servicesList.add(ServicesMonitor.Service.REMOTE_KEYWORD_SEARCH.toString());
        servicesList.add(ServicesMonitor.Service.MESSAGING.toString());
        ServicesMonitor.getInstance().addSubscriber(servicesList, propChangeListener);

        autoIngestMonitor = new AutoIngestMonitor();
        autoIngestMonitor.addObserver(this);
        new Thread(() -> {
            try {
                autoIngestMonitor.startUp();

            } catch (AutoIngestMonitor.AutoIngestMonitorException ex) {
                LOGGER.log(Level.SEVERE, "Unable to start up Auto Ingest Monitor", ex);
            }
        }).start();
    }

    /**
     * Shut down parts of the AutoIngestDashboard which were initialized
     */
    void shutDown() {
        scheduledRefreshThreadPoolExecutor.shutdownNow();
        if (autoIngestMonitor != null) {
            autoIngestMonitor.shutDown();
        }
    }

    @Override
    public void update(Observable observable, Object arg) {
        if (!scheduledRefreshStarted.getAndSet(true)) {
            scheduledRefreshThreadPoolExecutor.scheduleWithFixedDelay(() -> {
                EventQueue.invokeLater(() -> {
                    refreshTables();
                });
            }, AID_DELAY_BEFORE_FIRST_REFRESH, AID_REFRESH_INTERVAL_SECS, TimeUnit.SECONDS);
        }
    }

    /**
     * Reloads the table models using a RefreshChildrenEvent and refreshes the
     * JTables that use the models.
     *
     */
    void refreshTables() {
        pendingJobsPanel.refresh(new RefreshChildrenEvent(autoIngestMonitor));
        runningJobsPanel.refresh(new RefreshChildrenEvent(autoIngestMonitor));
        completedJobsPanel.refresh(new RefreshChildrenEvent(autoIngestMonitor));
    }

    /**
     * Exception type thrown when there is an error completing an auto ingest
     * dashboard operation.
     */
    static final class AutoIngestDashboardException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an instance of the exception type thrown when there is an
         * error completing an auto ingest dashboard operation.
         *
         * @param message The exception message.
         */
        private AutoIngestDashboardException(String message) {
            super(message);
        }

        /**
         * Constructs an instance of the exception type thrown when there is an
         * error completing an auto ingest dashboard operation.
         *
         * @param message The exception message.
         * @param cause   A Throwable cause for the error.
         */
        private AutoIngestDashboardException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    /**
     * Determines whether or not system adminstrator features of the dashboard
     * are enabled.
     *
     * @return True or false.
     */
    static boolean isAdminAutoIngestDashboard() {
        return new File(ADMIN_ACCESS_FILE_PATH).exists() || new File(ADMIN_EXT_ACCESS_FILE_PATH).exists();
    }

    /**
     * Determines whether the extended system administrator features of the
     * cases dashboard are enabled.
     *
     * @return True or false.
     */
    static boolean extendedFeaturesAreEnabled() {
        return new File(ADMIN_EXT_ACCESS_FILE_PATH).exists();
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

        jButton1 = new javax.swing.JButton();
        mainScrollPane = new javax.swing.JScrollPane();
        mainPanel = new javax.swing.JPanel();
        pendingScrollPane = new javax.swing.JScrollPane();
        runningScrollPane = new javax.swing.JScrollPane();
        completedScrollPane = new javax.swing.JScrollPane();
        lbPending = new javax.swing.JLabel();
        lbRunning = new javax.swing.JLabel();
        lbCompleted = new javax.swing.JLabel();
        refreshButton = new javax.swing.JButton();
        lbServicesStatus = new javax.swing.JLabel();
        tbServicesStatusMessage = new javax.swing.JTextField();

        org.openide.awt.Mnemonics.setLocalizedText(jButton1, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.jButton1.text")); // NOI18N

        setLayout(new java.awt.BorderLayout());

        mainPanel.setLayout(new java.awt.GridBagLayout());

        pendingScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        pendingScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        pendingScrollPane.setOpaque(false);
        pendingScrollPane.setPreferredSize(new java.awt.Dimension(2, 150));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 10, 10);
        mainPanel.add(pendingScrollPane, gridBagConstraints);

        runningScrollPane.setPreferredSize(new java.awt.Dimension(2, 150));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 10, 10);
        mainPanel.add(runningScrollPane, gridBagConstraints);

        completedScrollPane.setPreferredSize(new java.awt.Dimension(2, 150));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 10, 10);
        mainPanel.add(completedScrollPane, gridBagConstraints);

        lbPending.setFont(lbPending.getFont().deriveFont(lbPending.getFont().getSize()+3f));
        org.openide.awt.Mnemonics.setLocalizedText(lbPending, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbPending.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 5, 10);
        mainPanel.add(lbPending, gridBagConstraints);

        lbRunning.setFont(lbRunning.getFont().deriveFont(lbRunning.getFont().getSize()+3f));
        org.openide.awt.Mnemonics.setLocalizedText(lbRunning, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbRunning.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 5, 10);
        mainPanel.add(lbRunning, gridBagConstraints);

        lbCompleted.setFont(lbCompleted.getFont().deriveFont(lbCompleted.getFont().getSize()+3f));
        org.openide.awt.Mnemonics.setLocalizedText(lbCompleted, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbCompleted.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 5, 10);
        mainPanel.add(lbCompleted, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(refreshButton, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.refreshButton.text")); // NOI18N
        refreshButton.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.refreshButton.toolTipText")); // NOI18N
        refreshButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(6, 10, 10, 10);
        mainPanel.add(refreshButton, gridBagConstraints);

        lbServicesStatus.setFont(lbServicesStatus.getFont().deriveFont(lbServicesStatus.getFont().getSize()+3f));
        org.openide.awt.Mnemonics.setLocalizedText(lbServicesStatus, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbServicesStatus.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        mainPanel.add(lbServicesStatus, gridBagConstraints);

        tbServicesStatusMessage.setEditable(false);
        tbServicesStatusMessage.setFont(tbServicesStatusMessage.getFont().deriveFont(tbServicesStatusMessage.getFont().getStyle() | java.awt.Font.BOLD, tbServicesStatusMessage.getFont().getSize()+1));
        tbServicesStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.text")); // NOI18N
        tbServicesStatusMessage.setBorder(null);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 10);
        mainPanel.add(tbServicesStatusMessage, gridBagConstraints);

        mainScrollPane.setViewportView(mainPanel);

        add(mainScrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Handles a click on the Refresh button. Requests a refreshed jobs snapshot
     * from the auto ingest monitor and uses it to refresh the UI components of
     * the panel.
     *
     * @param evt The button click event.
     */
    private void refreshButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshButtonActionPerformed
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        refreshTables();
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }//GEN-LAST:event_refreshButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane completedScrollPane;
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel lbCompleted;
    private javax.swing.JLabel lbPending;
    private javax.swing.JLabel lbRunning;
    private javax.swing.JLabel lbServicesStatus;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JScrollPane mainScrollPane;
    private javax.swing.JScrollPane pendingScrollPane;
    private javax.swing.JButton refreshButton;
    private javax.swing.JScrollPane runningScrollPane;
    private javax.swing.JTextField tbServicesStatusMessage;
    // End of variables declaration//GEN-END:variables
}
