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
import java.awt.Dimension;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JDialog;
import javax.swing.JComboBox;
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
    
    //Map<String, List<ServicesHealthMonitor.DatabaseTimingResult>> timingData;
    
    private JPanel timingMetricPanel = null;
    private JPanel timingButtonPanel = null;
    private JComboBox dateComboBox = null;
    
    HealthMonitorDashboard() {
        
    }
    
    void display() throws HealthMonitorException {
        
        // Initialize and populate the timing metric panel 
        populateTimingMetricPanel();
        addActionListeners();
        
        System.out.println("Creating dialog");
        JScrollPane scrollPane = new JScrollPane(timingMetricPanel);
        JDialog dialog = new JDialog();
        dialog.setPreferredSize(new Dimension(1500, 800));
        dialog.setTitle("Services Health Monitor");
        //dialog.add(graphPanel);
        dialog.add(scrollPane);
        dialog.pack();
        dialog.setVisible(true);
        System.out.println("Done displaying dialog");
    }
    
    /**
     * Initialize the panel holding the timing metric controls,
     * if it has not already been initialized.
     */
    private void initializeTimingButtonPanel() throws HealthMonitorException {
        if(timingButtonPanel == null) {
            // Create the combo box for selecting how much data to display
            String[] dateOptionStrings = Arrays.stream(DateRange.values()).map(e -> e.getLabel()).toArray(String[]::new);
            dateComboBox = new JComboBox(dateOptionStrings);
            dateComboBox.setSelectedItem(DateRange.TWO_WEEKS.getLabel());

            // Add the date range button and label to the panel
            timingButtonPanel = new JPanel();
            timingButtonPanel.setBorder(BorderFactory.createEtchedBorder()); 
            timingButtonPanel.add(new JLabel("Max days to display"));
            timingButtonPanel.add(dateComboBox);
        }
    }
    
    /**
     * Add any needed action listeners.
     * Call this after all components are initialized.
     */
    private void addActionListeners() {
        // Set up a listener on the combo box that will update the timing
        // metric graphs
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
        initializeTimingButtonPanel();
        initializeTimingMetricPanel();
        
        // Get a fresh copy of the timing data from the database
        Map<String, List<ServicesHealthMonitor.DatabaseTimingResult>> timingData =  ServicesHealthMonitor.getInstance().getTimingMetricsFromDatabase();       
        
        // Add the button controls
        timingMetricPanel.add(timingButtonPanel);
        
        for(String name:timingData.keySet()) {
            // Add the metric name
            JLabel label = new JLabel(name);
            timingMetricPanel.add(label);
            
            // If necessary, trim down the list of results to fit the selected time range
            List<ServicesHealthMonitor.DatabaseTimingResult> timingDataForDisplay;
            if(dateComboBox.getSelectedItem() != null) {
                DateRange selectedDateRange = DateRange.fromLabel(dateComboBox.getSelectedItem().toString());
                if(selectedDateRange != DateRange.ALL) {
                    long threshold = System.currentTimeMillis() - selectedDateRange.getTimestampRange();
                    timingDataForDisplay = timingData.get(name).stream()
                            .filter(t -> t.getTimestamp() > threshold)
                            .collect(Collectors.toList());
                } else {
                    timingDataForDisplay = timingData.get(name);
                }
            } else {
                timingDataForDisplay = timingData.get(name);
            }
            
            TimingMetricGraphPanel singleTimingGraphPanel = new TimingMetricGraphPanel(timingDataForDisplay, TimingMetricGraphPanel.TimingMetricType.AVERAGE, true);
            singleTimingGraphPanel.setPreferredSize(new Dimension(800,200));
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
