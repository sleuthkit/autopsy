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

import com.mchange.v2.cfg.DelayedLogItem;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Font;
import javax.swing.Box;
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
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 *
 */
// TODO: keep public?
public class HealthMonitorDashboard {
    
    private final static Logger logger = Logger.getLogger(HealthMonitorDashboard.class.getName());
    
    Map<String, List<EnterpriseHealthMonitor.DatabaseTimingResult>> timingData;
    
    private JPanel timingMetricPanel = null;
    private JPanel timingButtonPanel = null;
    private JComboBox dateComboBox = null;
    private JComboBox hostComboBox = null;
    private JCheckBox hostCheckBox = null;
    
    HealthMonitorDashboard() {
        timingData = new HashMap<>();
    }
    
    void display() throws HealthMonitorException {
        
        // Get a copy of the timing data from the database
        timingData =  EnterpriseHealthMonitor.getInstance().getTimingMetricsFromDatabase();       
        
        // Set up the buttons
        setupTimingButtonPanel();
        addActionListeners();
        
        // Initialize and populate the timing metric panel 
        populateTimingMetricPanel();
        
        JScrollPane scrollPane = new JScrollPane(timingMetricPanel);
        JDialog dialog = new JDialog();
        //dialog.setPreferredSize(new Dimension(1500, 800));
        dialog.setTitle("Enterprise Health Monitor");
        //dialog.add(graphPanel);
        dialog.add(scrollPane);
        dialog.pack();
        dialog.setVisible(true);
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
        timingButtonPanel = new JPanel();
        timingButtonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
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
                        populateTimingMetricPanel();
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
                            populateTimingMetricPanel();
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
                        populateTimingMetricPanel();
                    } catch (HealthMonitorException ex) {
                        logger.log(Level.SEVERE, "Error populating timing metric panel", ex);
                    }
                }
            });
        }  
    }
    
    /**
     * Initialize the panel holding the timing metrics.
     * If it has not been initialized, create and set up the panel.
     * Otherwise, clear any existing components out of the panel.
     */
    private void initializeTimingMetricPanel() {
        if(timingMetricPanel == null) {
            timingMetricPanel = new JPanel();
            timingMetricPanel.setLayout(new BoxLayout(timingMetricPanel, BoxLayout.PAGE_AXIS));
            timingMetricPanel.setBorder(BorderFactory.createEtchedBorder());
        } else {
            // Clear out any existing components
            timingMetricPanel.removeAll();
        }
    }
    
    private void populateTimingMetricPanel() throws HealthMonitorException {

        initializeTimingMetricPanel();
              
        // Add title
        JLabel timingMetricTitle = new JLabel("Timing Metrics");
        timingMetricTitle.setFont(new Font("Serif", Font.BOLD, 20));
        timingMetricPanel.add(timingMetricTitle);
        
        // Add the button controls
        timingMetricPanel.add(timingButtonPanel);
        timingMetricPanel.add(new JSeparator());
        
        for(String name:timingData.keySet()) {
            // Add the metric name
            JLabel metricNameLabel = new JLabel(name);
            metricNameLabel.setFont(new Font("Serif", Font.BOLD, 12));
            timingMetricPanel.add(metricNameLabel);
            
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
            
            // If necessary, trim down the resulting list to only one host name
            List<EnterpriseHealthMonitor.DatabaseTimingResult> timingDataForDisplay;
            if(hostCheckBox.isSelected() && (hostComboBox.getSelectedItem() != null)) {
                timingDataForDisplay = intermediateTimingDataForDisplay.stream()
                            .filter(t -> t.getHostName().equals(hostComboBox.getSelectedItem().toString()))
                            .collect(Collectors.toList());
            } else {
                timingDataForDisplay = intermediateTimingDataForDisplay;
            }
            
            TimingMetricGraphPanel singleTimingGraphPanel = new TimingMetricGraphPanel(timingDataForDisplay, TimingMetricGraphPanel.TimingMetricType.AVERAGE, true);
            singleTimingGraphPanel.setPreferredSize(new Dimension(900,250));
            timingMetricPanel.add(singleTimingGraphPanel);
        }

        timingMetricPanel.revalidate();
        timingMetricPanel.repaint();
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
