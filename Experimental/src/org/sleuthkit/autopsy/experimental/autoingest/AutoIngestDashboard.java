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

import java.awt.Cursor;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.awt.Color;
import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import org.openide.modules.Places;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.core.ServicesMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.healthmonitor.HealthMonitorDashboard;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestMonitor.JobsSnapshot;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestNodeRefreshEvents.RefreshChildrenEvent;

/**
 * A dashboard for monitoring an automated ingest cluster.
 */
final class AutoIngestDashboard extends JPanel implements Observer {
    
    private final static String ADMIN_ACCESS_FILE_NAME = "adminAccess";
    private final static String ADMIN_ACCESS_FILE_PATH = Places.getUserDirectory().getAbsolutePath() + File.separator + ADMIN_ACCESS_FILE_NAME;
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(AutoIngestDashboard.class.getName());
    private AutoIngestMonitor autoIngestMonitor;
    private AutoIngestJobsPanel pendingJobsPanel;
    private AutoIngestJobsPanel runningJobsPanel;
    private AutoIngestJobsPanel completedJobsPanel;

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
        if (autoIngestMonitor != null) {
            autoIngestMonitor.shutDown();
        }
    }

    @Override
    public void update(Observable observable, Object arg) {
        if (arg instanceof JobsSnapshot) {
            EventQueue.invokeLater(() -> {
                refreshTables();
            });
        }
    }

    /**
     * Reloads the table models using a jobs snapshot and refreshes the JTables
     * that use the models.
     *
     * @param nodeStateSnapshot The jobs snapshot.
     */
    void refreshTables() {
        pendingJobsPanel.refresh(new RefreshChildrenEvent(autoIngestMonitor.getJobsSnapshot()));
        runningJobsPanel.refresh(new RefreshChildrenEvent(autoIngestMonitor.getJobsSnapshot()));
        completedJobsPanel.refresh(new RefreshChildrenEvent(autoIngestMonitor.getJobsSnapshot()));
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

    static boolean isAdminAutoIngestDashboard() {
        File f = new File(ADMIN_ACCESS_FILE_PATH);
        return f.exists();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jButton1 = new javax.swing.JButton();
        pendingScrollPane = new javax.swing.JScrollPane();
        runningScrollPane = new javax.swing.JScrollPane();
        completedScrollPane = new javax.swing.JScrollPane();
        lbPending = new javax.swing.JLabel();
        lbRunning = new javax.swing.JLabel();
        lbCompleted = new javax.swing.JLabel();
        refreshButton = new javax.swing.JButton();
        lbServicesStatus = new javax.swing.JLabel();
        tbServicesStatusMessage = new javax.swing.JTextField();
        clusterMetricsButton = new javax.swing.JButton();
        healthMonitorButton = new javax.swing.JButton();

        org.openide.awt.Mnemonics.setLocalizedText(jButton1, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.jButton1.text")); // NOI18N

        pendingScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        pendingScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        pendingScrollPane.setOpaque(false);
        pendingScrollPane.setPreferredSize(new java.awt.Dimension(2, 215));

        lbPending.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbPending, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbPending.text")); // NOI18N

        lbRunning.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbRunning, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbRunning.text")); // NOI18N

        lbCompleted.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbCompleted, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbCompleted.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(refreshButton, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.refreshButton.text")); // NOI18N
        refreshButton.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.refreshButton.toolTipText")); // NOI18N
        refreshButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshButtonActionPerformed(evt);
            }
        });

        lbServicesStatus.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbServicesStatus, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.lbServicesStatus.text")); // NOI18N

        tbServicesStatusMessage.setEditable(false);
        tbServicesStatusMessage.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        tbServicesStatusMessage.setText(org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.tbServicesStatusMessage.text")); // NOI18N
        tbServicesStatusMessage.setBorder(null);

        org.openide.awt.Mnemonics.setLocalizedText(clusterMetricsButton, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.clusterMetricsButton.text")); // NOI18N
        clusterMetricsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clusterMetricsButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(healthMonitorButton, org.openide.util.NbBundle.getMessage(AutoIngestDashboard.class, "AutoIngestDashboard.healthMonitorButton.text")); // NOI18N
        healthMonitorButton.setMaximumSize(new java.awt.Dimension(133, 23));
        healthMonitorButton.setMinimumSize(new java.awt.Dimension(133, 23));
        healthMonitorButton.setPreferredSize(new java.awt.Dimension(133, 23));
        healthMonitorButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                healthMonitorButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(pendingScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(runningScrollPane, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(completedScrollPane, javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addComponent(lbServicesStatus)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(tbServicesStatusMessage, javax.swing.GroupLayout.DEFAULT_SIZE, 861, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(lbPending, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbCompleted, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbRunning, javax.swing.GroupLayout.Alignment.LEADING))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addComponent(refreshButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(healthMonitorButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(clusterMetricsButton)))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {clusterMetricsButton, refreshButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbServicesStatus, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tbServicesStatusMessage, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lbPending, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(1, 1, 1)
                .addComponent(pendingScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lbRunning)
                .addGap(1, 1, 1)
                .addComponent(runningScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 133, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lbCompleted)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(completedScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 179, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(refreshButton)
                    .addComponent(clusterMetricsButton)
                    .addComponent(healthMonitorButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
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

    private void clusterMetricsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clusterMetricsButtonActionPerformed
        new AutoIngestMetricsDialog(this.getTopLevelAncestor());
    }//GEN-LAST:event_clusterMetricsButtonActionPerformed

    private void healthMonitorButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_healthMonitorButtonActionPerformed
        new HealthMonitorDashboard(this.getTopLevelAncestor()).display();
    }//GEN-LAST:event_healthMonitorButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton clusterMetricsButton;
    private javax.swing.JScrollPane completedScrollPane;
    private javax.swing.JButton healthMonitorButton;
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel lbCompleted;
    private javax.swing.JLabel lbPending;
    private javax.swing.JLabel lbRunning;
    private javax.swing.JLabel lbServicesStatus;
    private javax.swing.JScrollPane pendingScrollPane;
    private javax.swing.JButton refreshButton;
    private javax.swing.JScrollPane runningScrollPane;
    private javax.swing.JTextField tbServicesStatusMessage;
    // End of variables declaration//GEN-END:variables
}
