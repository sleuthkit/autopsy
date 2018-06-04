/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.healthmonitor;

import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JComboBox;
import javax.swing.JSeparator;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.BorderFactory;
import java.util.Map;
import javax.swing.BoxLayout;
import java.awt.GridLayout;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.modules.Places;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 * Dashboard for viewing metrics and controlling the health monitor.
 */
public class HealthMonitorDashboard {
    
    private final static Logger logger = Logger.getLogger(HealthMonitorDashboard.class.getName());
    
    private final static String ADMIN_ACCESS_FILE_NAME = "adminAccess"; // NON-NLS
    private final static String ADMIN_ACCESS_FILE_PATH = Paths.get(Places.getUserDirectory().getAbsolutePath(), ADMIN_ACCESS_FILE_NAME).toString();
    
    Map<String, List<EnterpriseHealthMonitor.DatabaseTimingResult>> timingData;
    List<EnterpriseHealthMonitor.UserData> userData;

    private JComboBox<String> timingDateComboBox = null;
    private JComboBox<String> timingHostComboBox = null;
    private JCheckBox timingHostCheckBox = null;
    private JCheckBox timingShowTrendLineCheckBox = null;
    private JCheckBox timingSkipOutliersCheckBox = null;
    private JPanel timingGraphPanel = null;
    private JComboBox<String> userDateComboBox = null;
    private JPanel userGraphPanel = null;
    private JDialog dialog = null;
    private final Container parentWindow;
    
    /**
     * Create an instance of the dashboard.
     * Call display() after creation to show the dashboard.
     * @param parent The parent container (for centering the UI)
     */
    public HealthMonitorDashboard(Container parent) {
        timingData = new HashMap<>();
        userData = new ArrayList<>();
        parentWindow = parent;
    }
    
    /**
     * Display the dashboard.
     */
    @NbBundle.Messages({"HealthMonitorDashboard.display.errorCreatingDashboard=Error creating health monitor dashboard",
                        "HealthMonitorDashboard.display.dashboardTitle=Enterprise Health Monitor"})
    public void display() {
        
        // Update the enabled status and get the timing data, then create all
        // the sub panels.
        JPanel timingPanel;
        JPanel userPanel;
        JPanel adminPanel;        
        try {
            updateData();
            timingPanel = createTimingPanel();
            userPanel = createUserPanel();
            adminPanel = createAdminPanel();
        } catch (HealthMonitorException ex) {
            logger.log(Level.SEVERE, "Error creating panels for health monitor dashboard", ex);
            MessageNotifyUtil.Message.error(Bundle.HealthMonitorDashboard_display_errorCreatingDashboard());
            return;
        }
        
        // Create the main panel for the dialog
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
               
        // Add the timing panel
        mainPanel.add(timingPanel);
        
        // Add the user panel
        mainPanel.add(userPanel);
        
        // Add the admin panel if the admin file is present
        File adminFile = new File(ADMIN_ACCESS_FILE_PATH);
        if(adminFile.exists()) {
            mainPanel.add(adminPanel);
        }
        
        // Create and show the dialog
        dialog = new JDialog();
        dialog.setTitle(Bundle.HealthMonitorDashboard_display_dashboardTitle());
        dialog.add(mainPanel);
        dialog.pack();
        dialog.setLocationRelativeTo(parentWindow);
        dialog.setVisible(true);
    }
    
    /**
     * Delete the current dialog and create a new one. This should only be
     * called after enabling or disabling the health monitor.
     */
    private void redisplay() {
        if (dialog != null) {
            dialog.setVisible(false);
            dialog.dispose();
        }
        display();
    }
    
    /**
     * Check the monitor enabled status and, if enabled, get the timing data.
     * @throws HealthMonitorException 
     */
    private void updateData() throws HealthMonitorException {
        
        // Update the monitor status
        EnterpriseHealthMonitor.getInstance().updateFromGlobalEnabledStatus();
        
        if(EnterpriseHealthMonitor.monitorIsEnabled()) {
            // Get a copy of the timing data from the database
            timingData =  EnterpriseHealthMonitor.getInstance().getTimingMetricsFromDatabase(DateRange.getMaximumTimestampRange()); 
            
            // Get a copy of the user data from the database
            userData = EnterpriseHealthMonitor.getInstance().getUserMetricsFromDatabase(DateRange.getMaximumTimestampRange()); 
        }
    }
    
    /**
     * Create the panel holding the timing graphs and the controls for them.
     * @return The timing panel
     * @throws HealthMonitorException 
     */
    @NbBundle.Messages({"HealthMonitorDashboard.createTimingPanel.noData=No data to display - monitor is not enabled",
                        "HealthMonitorDashboard.createTimingPanel.timingMetricsTitle=Timing Metrics"})
    private JPanel createTimingPanel() throws HealthMonitorException {
        
        // If the monitor isn't enabled, just add a message
        if(! EnterpriseHealthMonitor.monitorIsEnabled()) {
            //timingMetricPanel.setPreferredSize(new Dimension(400,100));
            JPanel emptyTimingMetricPanel = new JPanel();
            emptyTimingMetricPanel.add(new JLabel(Bundle.HealthMonitorDashboard_createTimingPanel_timingMetricsTitle()));
            emptyTimingMetricPanel.add(new JLabel(" "));
            emptyTimingMetricPanel.add(new JLabel(Bundle.HealthMonitorDashboard_createTimingPanel_noData()));
            
            return emptyTimingMetricPanel;
        }
        
        JPanel timingMetricPanel = new JPanel();
        timingMetricPanel.setLayout(new BoxLayout(timingMetricPanel, BoxLayout.PAGE_AXIS));
        timingMetricPanel.setBorder(BorderFactory.createEtchedBorder());
              
        // Add title
        JLabel timingMetricTitle = new JLabel(Bundle.HealthMonitorDashboard_createTimingPanel_timingMetricsTitle());
        timingMetricPanel.add(timingMetricTitle);
        timingMetricPanel.add(new JSeparator());   
        
        // Add the controls
        timingMetricPanel.add(createTimingControlPanel());
        timingMetricPanel.add(new JSeparator());
        
        // Create panel to hold graphs
        timingGraphPanel = new JPanel();
        timingGraphPanel.setLayout(new GridLayout(0,2));
        
        // Update the graph panel, put it in a scroll pane, and add to the timing metric panel
        updateTimingMetricGraphs();
        JScrollPane scrollPane = new JScrollPane(timingGraphPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        timingMetricPanel.add(scrollPane);
        timingMetricPanel.revalidate();
        timingMetricPanel.repaint();

        return timingMetricPanel;
    }
    
    /**
     * Create the panel with combo boxes for date range and host.
     * @return the control panel
     */
    @NbBundle.Messages({"HealthMonitorDashboard.createTimingControlPanel.filterByHost=Filter by host",
                        "HealthMonitorDashboard.createTimingControlPanel.maxDays=Max days to display",
                        "HealthMonitorDashboard.createTimingControlPanel.skipOutliers=Do not plot outliers",
                        "HealthMonitorDashboard.createTimingControlPanel.showTrendLine=Show trend line"})
    private JPanel createTimingControlPanel() {
        JPanel timingControlPanel = new JPanel();
        
        // If the monitor is not enabled, don't add any components
        if(! EnterpriseHealthMonitor.monitorIsEnabled()) {
            return timingControlPanel;
        }
        
        // Create the combo box for selecting how much data to display
        String[] dateOptionStrings = Arrays.stream(DateRange.values()).map(e -> e.getLabel()).toArray(String[]::new);
        timingDateComboBox = new JComboBox<>(dateOptionStrings);
        timingDateComboBox.setSelectedItem(DateRange.ONE_DAY.getLabel());
        
        // Set up the listener on the date combo box
        timingDateComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                try {
                    updateTimingMetricGraphs();
                } catch (HealthMonitorException ex) {
                    logger.log(Level.SEVERE, "Error updating timing metric panel", ex);
                }
            }
        });
        
        // Create an array of host names
        Set<String> hostNameSet = new HashSet<>();
        for(String metricType:timingData.keySet()) {
            for(EnterpriseHealthMonitor.DatabaseTimingResult result: timingData.get(metricType)) {
                hostNameSet.add(result.getHostName());
            }
        }
        
        // Load the host names into the combo box
        timingHostComboBox = new JComboBox<>(hostNameSet.toArray(new String[hostNameSet.size()]));
        
        // Set up the listener on the combo box
        timingHostComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                try {
                    if((timingHostCheckBox != null) && timingHostCheckBox.isSelected()) {
                        updateTimingMetricGraphs();
                    }
                } catch (HealthMonitorException ex) {
                    logger.log(Level.SEVERE, "Error populating timing metric panel", ex);
                }
            }
        });
        
        // Create the host checkbox
        timingHostCheckBox = new JCheckBox(Bundle.HealthMonitorDashboard_createTimingControlPanel_filterByHost());
        timingHostCheckBox.setSelected(false);
        timingHostComboBox.setEnabled(false);
        
        // Set up the listener on the checkbox
        timingHostCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                try {
                    timingHostComboBox.setEnabled(timingHostCheckBox.isSelected());
                    updateTimingMetricGraphs();
                } catch (HealthMonitorException ex) {
                    logger.log(Level.SEVERE, "Error populating timing metric panel", ex);
                }
            }
        });
        
        // Create the checkbox for showing the trend line
        timingShowTrendLineCheckBox = new JCheckBox(Bundle.HealthMonitorDashboard_createTimingControlPanel_showTrendLine());
        timingShowTrendLineCheckBox.setSelected(true);
        
        // Set up the listener on the checkbox
        timingShowTrendLineCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                try {
                    updateTimingMetricGraphs();
                } catch (HealthMonitorException ex) {
                    logger.log(Level.SEVERE, "Error populating timing metric panel", ex);
                }
            }
        });
        
        // Create the checkbox for omitting outliers
        timingSkipOutliersCheckBox = new JCheckBox(Bundle.HealthMonitorDashboard_createTimingControlPanel_skipOutliers());
        timingSkipOutliersCheckBox.setSelected(false);
        
        // Set up the listener on the checkbox
        timingSkipOutliersCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                try {
                    updateTimingMetricGraphs();
                } catch (HealthMonitorException ex) {
                    logger.log(Level.SEVERE, "Error populating timing metric panel", ex);
                }
            }
        });
        
        // Add the date range combo box and label to the panel
        timingControlPanel.add(new JLabel(Bundle.HealthMonitorDashboard_createTimingControlPanel_maxDays()));
        timingControlPanel.add(timingDateComboBox);
        
        // Put some space between the elements
        timingControlPanel.add(Box.createHorizontalStrut(100));
        
        // Add the host combo box and checkbox to the panel
        timingControlPanel.add(timingHostCheckBox);
        timingControlPanel.add(timingHostComboBox);
        
        // Put some space between the elements
        timingControlPanel.add(Box.createHorizontalStrut(100));
        
        // Add the skip outliers checkbox
        timingControlPanel.add(this.timingShowTrendLineCheckBox);
        
        // Put some space between the elements
        timingControlPanel.add(Box.createHorizontalStrut(100));
        
        // Add the skip outliers checkbox
        timingControlPanel.add(this.timingSkipOutliersCheckBox);
        
        return timingControlPanel;
    }
    
    /**
     * Update the timing graphs.
     * @throws HealthMonitorException 
     */
    @NbBundle.Messages({"HealthMonitorDashboard.updateTimingMetricGraphs.noData=No data to display"})
    private void updateTimingMetricGraphs() throws HealthMonitorException {
        
        // Clear out any old graphs
        timingGraphPanel.removeAll();
        
        if(timingData.keySet().isEmpty()) {
            // There are no timing metrics in the database
            timingGraphPanel.add(new JLabel(Bundle.HealthMonitorDashboard_updateTimingMetricGraphs_noData()));
            return;
        }
        
        for(String metricName:timingData.keySet()) {
            
            // If necessary, trim down the list of results to fit the selected time range
            List<EnterpriseHealthMonitor.DatabaseTimingResult> intermediateTimingDataForDisplay;
            if(timingDateComboBox.getSelectedItem() != null) {
                DateRange selectedDateRange = DateRange.fromLabel(timingDateComboBox.getSelectedItem().toString());
                long threshold = System.currentTimeMillis() - selectedDateRange.getTimestampRange();
                intermediateTimingDataForDisplay = timingData.get(metricName).stream()
                        .filter(t -> t.getTimestamp() > threshold)
                        .collect(Collectors.toList());
            } else {
                intermediateTimingDataForDisplay = timingData.get(metricName);
            }
            
            // Get the name of the selected host, if there is one.
            // The graph always uses the data from all hosts to generate the x and y scales
            // so we don't filter anything out here.
            String hostToDisplay = null;
            if(timingHostCheckBox.isSelected() && (timingHostComboBox.getSelectedItem() != null)) {
                hostToDisplay = timingHostComboBox.getSelectedItem().toString();
            }
            
            // Generate the graph
            TimingMetricGraphPanel singleTimingGraphPanel = new TimingMetricGraphPanel(intermediateTimingDataForDisplay, 
                    hostToDisplay, true, metricName, timingSkipOutliersCheckBox.isSelected(), timingShowTrendLineCheckBox.isSelected());
            singleTimingGraphPanel.setPreferredSize(new Dimension(700,200));
            
            timingGraphPanel.add(singleTimingGraphPanel);
        }
        timingGraphPanel.revalidate();
        timingGraphPanel.repaint();
    }
    
    /**
     * Create the user panel.
     * This displays cases open and users logged in
     * @return the user panel
     */
    @NbBundle.Messages({"HealthMonitorDashboard.createUserPanel.noData=No data to display - monitor is not enabled",
                    "HealthMonitorDashboard.createUserPanel.userMetricsTitle=User Metrics"})
    private JPanel createUserPanel() throws HealthMonitorException {
        // If the monitor isn't enabled, just add a message
        if(! EnterpriseHealthMonitor.monitorIsEnabled()) {
            //timingMetricPanel.setPreferredSize(new Dimension(400,100));
            JPanel emptyUserMetricPanel = new JPanel();
            emptyUserMetricPanel.add(new JLabel(Bundle.HealthMonitorDashboard_createUserPanel_userMetricsTitle()));
            emptyUserMetricPanel.add(new JLabel(" "));
            emptyUserMetricPanel.add(new JLabel(Bundle.HealthMonitorDashboard_createUserPanel_noData()));
            
            return emptyUserMetricPanel;
        }
        
        JPanel userMetricPanel = new JPanel();
        userMetricPanel.setLayout(new BoxLayout(userMetricPanel, BoxLayout.PAGE_AXIS));
        userMetricPanel.setBorder(BorderFactory.createEtchedBorder());
              
        // Add title
        JLabel userMetricTitle = new JLabel(Bundle.HealthMonitorDashboard_createUserPanel_userMetricsTitle());
        userMetricPanel.add(userMetricTitle);
        userMetricPanel.add(new JSeparator());  
        
        // Add the controls
        userMetricPanel.add(createUserControlPanel());
        userMetricPanel.add(new JSeparator());
        
        // Create panel to hold graphs
        userGraphPanel = new JPanel();
        userGraphPanel.setLayout(new GridLayout(0,2));
        
        // Update the graph panel, put it in a scroll pane, and add to the timing metric panel
        updateUserMetricGraphs();
        JScrollPane scrollPane = new JScrollPane(userGraphPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        userMetricPanel.add(scrollPane);
        userMetricPanel.revalidate();
        userMetricPanel.repaint();
        
        return userMetricPanel;
    }
    
    /**
     * Create the panel with controls for the user panel
     * @return the control panel
     */
    @NbBundle.Messages({"HealthMonitorDashboard.createUserControlPanel.maxDays=Max days to display"})
    private JPanel createUserControlPanel() {
        JPanel userControlPanel = new JPanel();
        
        // If the monitor is not enabled, don't add any components
        if(! EnterpriseHealthMonitor.monitorIsEnabled()) {
            return userControlPanel;
        }
        
        // Create the combo box for selecting how much data to display
        String[] dateOptionStrings = Arrays.stream(DateRange.values()).map(e -> e.getLabel()).toArray(String[]::new);
        userDateComboBox = new JComboBox<>(dateOptionStrings);
        userDateComboBox.setSelectedItem(DateRange.ONE_DAY.getLabel());
        
        // Set up the listener on the date combo box
        userDateComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                try {
                    updateUserMetricGraphs();
                } catch (HealthMonitorException ex) {
                    logger.log(Level.SEVERE, "Error updating user metric panel", ex);
                }
            }
        });
        
        // Add the date range combo box and label to the panel
        userControlPanel.add(new JLabel(Bundle.HealthMonitorDashboard_createUserControlPanel_maxDays()));
        userControlPanel.add(userDateComboBox);
        
        return userControlPanel;
    }
    
        /**
     * Update the timing graphs.
     * @throws HealthMonitorException 
     */
    @NbBundle.Messages({"HealthMonitorDashboard.updateUserMetricGraphs.noData=No data to display"})
    private void updateUserMetricGraphs() throws HealthMonitorException {
        
        // Clear out any old graphs
        userGraphPanel.removeAll();
        
        if(userData.isEmpty()) {
            // There are no user metrics in the database
            userGraphPanel.add(new JLabel(Bundle.HealthMonitorDashboard_updateUserMetricGraphs_noData()));
            return;
        }

        // Calculate the minimum timestamp for the graph.
        // Unlike the timing graphs, we have to pass in the full user data list here to 
        // use for the graph. This is because even if we're only displaying one day, the
        // last metric for a host may be that it logged on two days ago, so we would want
        // to show that node as logged on.
        long timestampThreshold;
        if(userDateComboBox.getSelectedItem() != null) {
            DateRange selectedDateRange = DateRange.fromLabel(userDateComboBox.getSelectedItem().toString());
            timestampThreshold = System.currentTimeMillis() - selectedDateRange.getTimestampRange();

        } else {
            timestampThreshold = System.currentTimeMillis() - DateRange.getMaximumTimestampRange();
        }
                        
        // Generate the graphs
        UserMetricGraphPanel caseGraphPanel = new UserMetricGraphPanel(userData, timestampThreshold, true);
        caseGraphPanel.setPreferredSize(new Dimension(700,200));
        
        UserMetricGraphPanel logonGraphPanel = new UserMetricGraphPanel(userData, timestampThreshold, false);
        logonGraphPanel.setPreferredSize(new Dimension(700,200));

        userGraphPanel.add(caseGraphPanel);
        userGraphPanel.add(logonGraphPanel);
        userGraphPanel.revalidate();
        userGraphPanel.repaint();
    }
    
    /**
     * Create the admin panel.
     * This allows the health monitor to be enabled and disabled.
     * @return the admin panel
     */
    @NbBundle.Messages({"HealthMonitorDashboard.createAdminPanel.enableButton=Enable monitor",
                        "HealthMonitorDashboard.createAdminPanel.disableButton=Disable monitor"})
    private JPanel createAdminPanel() {
        
        JPanel adminPanel = new JPanel();
        adminPanel.setBorder(BorderFactory.createEtchedBorder());

        // Create the buttons for enabling/disabling the monitor
        JButton enableButton = new JButton(Bundle.HealthMonitorDashboard_createAdminPanel_enableButton());
        JButton disableButton = new JButton(Bundle.HealthMonitorDashboard_createAdminPanel_disableButton());
        
        boolean isEnabled =  EnterpriseHealthMonitor.monitorIsEnabled();
        enableButton.setEnabled(! isEnabled);
        disableButton.setEnabled(isEnabled);

        // Set up a listener on the enable button
        enableButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                try {
                    dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    EnterpriseHealthMonitor.setEnabled(true);
                    redisplay();
                } catch (HealthMonitorException ex) {
                    logger.log(Level.SEVERE, "Error enabling monitoring", ex);
                } finally {
                    dialog.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });
        
        // Set up a listener on the disable button
        disableButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                try {
                    dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    EnterpriseHealthMonitor.setEnabled(false);
                    redisplay();
                } catch (HealthMonitorException ex) {
                    logger.log(Level.SEVERE, "Error disabling monitoring", ex);
                } finally {
                    dialog.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });
        
        // Add the buttons
        adminPanel.add(enableButton);
        adminPanel.add(Box.createHorizontalStrut(25));
        adminPanel.add(disableButton);
        
        return adminPanel;
    }
    
    /**
     * Possible date ranges for the metrics in the UI
     */
    @NbBundle.Messages({"HealthMonitorDashboard.DateRange.oneMonth=One month",
                        "HealthMonitorDashboard.DateRange.twoWeeks=Two weeks",
                        "HealthMonitorDashboard.DateRange.oneWeek=One week",
                        "HealthMonitorDashboard.DateRange.oneDay=One day"})
    private enum DateRange {
        ONE_DAY(Bundle.HealthMonitorDashboard_DateRange_oneDay(), 1),
        ONE_WEEK(Bundle.HealthMonitorDashboard_DateRange_oneWeek(), 7),
        TWO_WEEKS(Bundle.HealthMonitorDashboard_DateRange_twoWeeks(), 14),
        ONE_MONTH(Bundle.HealthMonitorDashboard_DateRange_oneMonth(), 31);
           
        private final String label;
        private final long numberOfDays;
        private static final long MILLISECONDS_PER_DAY = 1000 * 60 * 60 * 24;
        
        DateRange(String label, long numberOfDays) {
            this.label = label;
            this.numberOfDays = numberOfDays;
        }
        
        /**
         * Get the name for display in the UI
         * @return the name
         */
        String getLabel() {
            return label;
        }
        
        /**
         * Get the number of milliseconds represented by this date range.
         * Compare the timestamps to ((current time in millis) - (this value)) to
         * determine if they are in the range
         * @return the time range in milliseconds
         */
        long getTimestampRange() {
            if (numberOfDays > 0) {
                return numberOfDays * MILLISECONDS_PER_DAY;
            } else {
                return Long.MAX_VALUE;
            }
        }
        
        /**
         * Get the maximum range for this enum.
         * This should be used for querying the database for the timing metrics to display.
         * @return the maximum range in milliseconds
         */
        static long getMaximumTimestampRange() {
            long maxRange = Long.MIN_VALUE;
            for (DateRange dateRange : DateRange.values()) {
                if (dateRange.getTimestampRange() > maxRange) {
                    maxRange = dateRange.getTimestampRange();
                }
            }
            return maxRange;
        }
        
        static DateRange fromLabel(String text) {
            for (DateRange dateRange : DateRange.values()) {
                if (dateRange.label.equalsIgnoreCase(text)) {
                    return dateRange;
                }
            }
            return ONE_DAY; // If the comparison failed, return a default
        }
    }
    
}
