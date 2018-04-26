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

import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Font;
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
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.modules.Places;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 *
 */
public class HealthMonitorDashboard {
    
    private final static Logger logger = Logger.getLogger(HealthMonitorDashboard.class.getName());
    
    private final static String ADMIN_ACCESS_FILE_NAME = "adminAccess";
    private final static String ADMIN_ACCESS_FILE_PATH = Places.getUserDirectory().getAbsolutePath() + File.separator + ADMIN_ACCESS_FILE_NAME;
    
    Map<String, List<EnterpriseHealthMonitor.DatabaseTimingResult>> timingData;
    
    private JPanel timingMetricPanel = null;
    private JPanel timingButtonPanel = null;
    private JPanel adminPanel = null;
    private JComboBox dateComboBox = null;
    private JComboBox hostComboBox = null;
    private JCheckBox hostCheckBox = null;
    private JButton enableButton = null;
    private JButton disableButton = null;
    private JDialog dialog = null;
    private final Container parentWindow;
    
    public HealthMonitorDashboard(Container parent) {
        timingData = new HashMap<>();
        parentWindow = parent;
    }
    
    
    private void refreshPanels() throws HealthMonitorException {
        // Update the monitor status
        EnterpriseHealthMonitor.getInstance().updateFromGlobalEnabledStatus();
        
        if(EnterpriseHealthMonitor.monitorIsEnabled()) {
            // Get a copy of the timing data from the database
            timingData =  EnterpriseHealthMonitor.getInstance().getTimingMetricsFromDatabase(); 
        }
        
        // Set up the UI
        setupAdminPanel();
        setupTimingButtonPanel();
        addActionListeners();
        
        // Initialize and populate the timing metric panel 
        initializeTimingMetricPanel();        
    }
    
    /**
     * Display the Health Monitor dashboard.
     */
    public void display() {
        if(dialog != null) {
            dialog.setVisible(false);
            dialog.dispose();
        }
        
        try {
            refreshPanels();
        } catch (HealthMonitorException ex) {
            logger.log(Level.SEVERE, "Error creating panels for health monitor dashboard", ex);
            MessageNotifyUtil.Message.error("TEMP");
        }
        
        JScrollPane scrollPane = new JScrollPane(timingMetricPanel);
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS));
        mainPanel.add(scrollPane);
        File adminFile = new File(ADMIN_ACCESS_FILE_PATH);
        System.out.println("admin file: " + adminFile.getAbsolutePath());
        if(adminFile.exists()) {
            mainPanel.add(adminPanel);
        }
        dialog = new JDialog();
        //dialog.setPreferredSize(new Dimension(1500, 800));
        dialog.setTitle("Enterprise Health Monitor");
        
        dialog.add(mainPanel);
        
        dialog.pack();
        dialog.setLocationRelativeTo(parentWindow);
        dialog.setVisible(true);
    }
    
    /**
     * Initialize the admin panel, which allows the monitor to be enabled
     * or diabled
     * @throws HealthMonitorException 
     */
    private void setupAdminPanel() throws HealthMonitorException {
        if (adminPanel == null) {
            adminPanel = new JPanel();
            adminPanel.setBorder(BorderFactory.createEtchedBorder());
        } else {
            adminPanel.removeAll();
        }
        
        if (enableButton == null) {
            enableButton = new JButton("Enable monitor");
        }
        
        if(disableButton == null) {
            disableButton = new JButton("Disable monitor");
        }
        
        updateEnableButtons();
        
        adminPanel.add(enableButton);
        adminPanel.add(Box.createHorizontalStrut(25));
        adminPanel.add(disableButton);
        
    }
    
    /**
     * Update the enable and disable buttons
     */
    private void updateEnableButtons() {
        boolean isEnabled =  EnterpriseHealthMonitor.monitorIsEnabled();
        enableButton.setEnabled(! isEnabled);
        disableButton.setEnabled(isEnabled);
    }
    
    /**
     * Initialize the panel holding the timing metric controls and
     * update components
     */
    private void setupTimingButtonPanel() throws HealthMonitorException {
        if(timingButtonPanel == null) {
            timingButtonPanel = new JPanel();
            timingButtonPanel.setBorder(BorderFactory.createEtchedBorder());
            //timingButtonPanel.setPreferredSize(new Dimension(1500, 100));
        } else {
            timingButtonPanel.removeAll();
        }
        
        // If the monitor is not enabled, don't add any components
        if(! EnterpriseHealthMonitor.monitorIsEnabled()) {
            return;
        }
        
        // The contents of the date combo box will never change, so we only need to
        // do this once
        if(dateComboBox == null) {
            // Create the combo box for selecting how much data to display
            String[] dateOptionStrings = Arrays.stream(DateRange.values()).map(e -> e.getLabel()).toArray(String[]::new);
            dateComboBox = new JComboBox(dateOptionStrings);
            dateComboBox.setSelectedItem(DateRange.TWO_WEEKS.getLabel());
        }
        
        // Create an array of host names
        Set<String> hostNameSet = new HashSet<>();
        for(String metricType:timingData.keySet()) {
            for(EnterpriseHealthMonitor.DatabaseTimingResult result: timingData.get(metricType)) {
                hostNameSet.add(result.getHostName());
            }
        }
        
        // Load the host names into the combo box
        hostComboBox = new JComboBox(hostNameSet.toArray(new String[hostNameSet.size()]));
        
        // Create the checkbox (if needed)
        if(hostCheckBox == null) {
            hostCheckBox = new JCheckBox("Filter by host");
            hostCheckBox.setSelected(false);
            hostComboBox.setEnabled(false);
        }
        
        // Create the panel
        //timingButtonPanel = new JPanel();
        //timingButtonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        //timingButtonPanel.setBorder(BorderFactory.createEtchedBorder());
        
        // Add the date range combo box and label to the panel
        timingButtonPanel.add(new JLabel("Max days to display"));
        timingButtonPanel.add(dateComboBox);
        
        // Put some space between the elements
        timingButtonPanel.add(Box.createHorizontalStrut(100));
        
        // Add the host combo box and checkbox to the panel
        timingButtonPanel.add(hostCheckBox);
        //timingButtonPanel.add(new JLabel("Host to display"));
        timingButtonPanel.add(hostComboBox);
    }
    
    /**
     * Add any needed action listeners.
     * Call this after all components are initialized.
     */
    private void addActionListeners() {
        
        // Set up a listener on the combo box that will update the timing
        // metric graphs
        if((dateComboBox != null) && (dateComboBox.getActionListeners().length == 0)) {
            dateComboBox.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent arg0) {
                    try {
                        initializeTimingMetricPanel();
                    } catch (HealthMonitorException ex) {
                        logger.log(Level.SEVERE, "Error populating timing metric panel", ex);
                    }
                }
            });
        }
        
        // Set up a listener on the host name combo box
        if((hostComboBox != null) && (hostComboBox.getActionListeners().length == 0)) {
            hostComboBox.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent arg0) {
                    try {
                        if((hostCheckBox != null) && hostCheckBox.isSelected()) {
                            initializeTimingMetricPanel();
                        }
                    } catch (HealthMonitorException ex) {
                        logger.log(Level.SEVERE, "Error populating timing metric panel", ex);
                    }
                }
            });
        }   
        
        // Set up a listener on the host name check box
        if((hostCheckBox != null) && (hostCheckBox.getActionListeners().length == 0)) {
            hostCheckBox.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent arg0) {
                    try {
                        hostComboBox.setEnabled(hostCheckBox.isSelected()); // Why isn't this working?
                        initializeTimingMetricPanel();
                    } catch (HealthMonitorException ex) {
                        logger.log(Level.SEVERE, "Error populating timing metric panel", ex);
                    }
                }
            });
        }  
        
        // Set up a listener on the enable button
        if((enableButton != null) && (enableButton.getActionListeners().length == 0)) {
            enableButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent arg0) {
                    System.out.println("\n### In action listener for enable button");
                    try {
                        dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                        //WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                        EnterpriseHealthMonitor.setEnabled(true);
                        HealthMonitorDashboard.this.updateEnableButtons();
                        display();
                    } catch (HealthMonitorException ex) {
                        logger.log(Level.SEVERE, "Error enabling monitoring", ex);
                    } finally {
                        //WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                        dialog.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    }
                }
            });
        }  
        
        // Set up a listener on the disable button
        if((disableButton != null) && (disableButton.getActionListeners().length == 0)) {
            disableButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent arg0) {
                    System.out.println("\n### In action listener for disable button");
                    try {
                        dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                        //WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                        EnterpriseHealthMonitor.setEnabled(false);
                        HealthMonitorDashboard.this.updateEnableButtons();
                        display();
                    } catch (HealthMonitorException ex) {
                        logger.log(Level.SEVERE, "Error disabling monitoring", ex);
                    } finally {
                        //WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                        dialog.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    }
                }
            });
        }
    }
    
    /**
     * Initialize the panel holding the timing metrics.
     */
    private void initializeTimingMetricPanel() throws HealthMonitorException {
    
        timingMetricPanel = new JPanel();
        timingMetricPanel.setLayout(new BoxLayout(timingMetricPanel, BoxLayout.PAGE_AXIS));
        timingMetricPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        timingMetricPanel.setBorder(BorderFactory.createEtchedBorder());
              
        // Add title
        JLabel timingMetricTitle = new JLabel("Timing Metrics");
        timingMetricTitle.setFont(new Font("Serif", Font.BOLD, 20));
        timingMetricPanel.add(timingMetricTitle);
        
        // If the monitor isn't enabled, just add a message
        if(! EnterpriseHealthMonitor.monitorIsEnabled()) {
            timingMetricPanel.setPreferredSize(new Dimension(400,100));
            timingMetricPanel.add(new JLabel(" "));
            timingMetricPanel.add(new JLabel("No data to display - monitor is not enabled"));
            
            timingMetricPanel.revalidate();
            timingMetricPanel.repaint();
            return;
        }
        
        // Add the button controls
        timingMetricPanel.add(timingButtonPanel);
        timingMetricPanel.add(new JSeparator());
        
        for(String name:timingData.keySet()) {
            
            // If necessary, trim down the list of results to fit the selected time range
            List<EnterpriseHealthMonitor.DatabaseTimingResult> intermediateTimingDataForDisplay;
            if(dateComboBox.getSelectedItem() != null) {
                DateRange selectedDateRange = DateRange.fromLabel(dateComboBox.getSelectedItem().toString());
                if(selectedDateRange != DateRange.ALL) {
                    long threshold = System.currentTimeMillis() - selectedDateRange.getTimestampRange();
                    intermediateTimingDataForDisplay = timingData.get(name).stream()
                            .filter(t -> t.getTimestamp() > threshold)
                            .collect(Collectors.toList());
                } else {
                    intermediateTimingDataForDisplay = timingData.get(name);
                }
            } else {
                intermediateTimingDataForDisplay = timingData.get(name);
            }
            
            // Get the name of the selected host, if there is one
            String hostToDisplay = null;
            if(hostCheckBox.isSelected() && (hostComboBox.getSelectedItem() != null)) {
                hostToDisplay = hostComboBox.getSelectedItem().toString();
            }
            
            TimingMetricGraphPanel singleTimingGraphPanel = new TimingMetricGraphPanel(intermediateTimingDataForDisplay, 
                    TimingMetricGraphPanel.TimingMetricType.AVERAGE, hostToDisplay, true);
            // Add the metric name
            JLabel metricNameLabel = new JLabel(name);
            metricNameLabel.setFont(new Font("Serif", Font.BOLD, 12));
            timingMetricPanel.add(metricNameLabel);
            singleTimingGraphPanel.setPreferredSize(new Dimension(900,250));
            timingMetricPanel.add(singleTimingGraphPanel);
        }

        timingMetricPanel.revalidate();
        timingMetricPanel.repaint();
        
        if(dialog != null) {
            dialog.pack();
        }
    }
    
    enum DateRange {
        ALL("All", 0),
        TWO_WEEKS("Two weeks", 14),
        ONE_WEEK("One week", 7);
        
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
        
        public static DateRange fromLabel(String text) {
            for (DateRange dateRange : DateRange.values()) {
                if (dateRange.label.equalsIgnoreCase(text)) {
                    return dateRange;
                }
            }
            return ALL; // If the comparison failed, return the default
        }
    }
    
}
