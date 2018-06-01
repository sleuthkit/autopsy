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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.Calendar;
import java.util.GregorianCalendar;
import javax.swing.JPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.logging.Level;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.healthmonitor.EnterpriseHealthMonitor.UserData;

/**
 * Creates graphs using the given users metric data
 */
class UserMetricGraphPanel extends JPanel {
    
    private final static Logger logger = Logger.getLogger(TimingMetricGraphPanel.class.getName());
    
    private final int padding = 25;
    private final int labelPadding = 25;
    private final Color examinerColor = new Color(0x12, 0x20, 0xdb, 180);
    private final Color autoIngestColor = new Color(0x12, 0x80, 0x20, 180);
    private final Color gridColor = new Color(200, 200, 200, 200);
    private static final Stroke GRAPH_STROKE = new BasicStroke(2f);
    private static final Stroke NARROW_STROKE = new BasicStroke(1f);
    private final int pointWidth = 4;
    private final int numberYDivisions = 10;
    private final boolean doLineGraph = false;
    private final boolean doBarGraph = true;
    private final List<UserCount> dataToPlot;
    private String graphLabel;
    private final long dataInterval;
    private String yUnitString;
    private final long MILLISECONDS_PER_HOUR = 1000 * 60 * 60;
    private final long MILLISECONDS_PER_DAY = MILLISECONDS_PER_HOUR * 24;
    private final long NANOSECONDS_PER_MILLISECOND = 1000 * 1000;
    private long maxTimestamp;
    private long minTimestamp;
    private long maxCount;
    private final long minCount = 0;

    @NbBundle.Messages({"UserMetricGraphPanel.constructor.casesOpen=Cases open",
                    "UserMetricGraphPanel.constructor.loggedIn=Users logged in",
                    "UserMetricGraphPanel.constructor.restOfLabel= - Examiner nodes in blue, auto ingest nodes in green"
                    })
    UserMetricGraphPanel(List<UserData> userResults, long timestampThreshold, boolean plotCases) {
        
        maxTimestamp = System.currentTimeMillis();
        minTimestamp = timestampThreshold;
        
        // Make the label
        if (plotCases) {
            graphLabel = Bundle.UserMetricGraphPanel_constructor_casesOpen();
        } else {
            graphLabel = Bundle.UserMetricGraphPanel_constructor_loggedIn();
        }
        graphLabel += Bundle.UserMetricGraphPanel_constructor_restOfLabel();
        
        // Comparator for the set of UserData objects
        Comparator<UserData> sortOnTimestamp = new Comparator<UserData>() {
            @Override
            public int compare(UserData o1, UserData o2) {
                return Long.compare(o1.getTimestamp(), o2.getTimestamp());
            }
        };
        
        // Create a map from host name to data and get the timestamp bounds.
        // We're using TreeSets here because they support the floor function.
        Map<String, TreeSet<UserData>> userDataMap = new HashMap<>();
        for(UserData result:userResults) {
            if(userDataMap.containsKey(result.getHostname())) {
                userDataMap.get(result.getHostname()).add(result);
            } else {
                TreeSet<UserData> resultTreeSet = new TreeSet<>(sortOnTimestamp);
                resultTreeSet.add(result);
                userDataMap.put(result.getHostname(), resultTreeSet);
            }
            // TODO test what happens if two identical timestamps come in for the same host
        }
        
        // Create a list of data points to plot
        // The idea here is that starting at maxTimestamp, we go backwards in increments, 
        // see what the state of each node was at that time and make the counts of nodes
        // that are logged in/ have a case open.
        // A case is open if the last event was "case open"; closed otherwise
        // A user is logged in if the last event was anything but "log out";logged out otherwise
        dataToPlot = new ArrayList<>();
        System.out.println("Min timestamp: " + minTimestamp + ", max timestamp: " + maxTimestamp);
        dataInterval = MILLISECONDS_PER_HOUR;
        for (long timestamp = maxTimestamp;timestamp > minTimestamp;timestamp -= dataInterval) {
            UserCount userCount = new UserCount(timestamp);
            UserData timestampUserData = UserData.createDummyUserData(timestamp);
            
            for (String hostname:userDataMap.keySet()) {
                // Get the most recent record before this timestamp
                UserData lastRecord = userDataMap.get(hostname).floor(timestampUserData);
                
                if (lastRecord != null) {
                    if (plotCases) {
                        if (lastRecord.getEventType().caseIsOpen()) {
                            if(lastRecord.isExaminerNode()) {
                                userCount.addExaminer();
                            } else {
                                userCount.addAutoIngestNode();
                            }
                        }
                    } else {
                        if (lastRecord.getEventType().userIsLoggedIn()) {
                            if(lastRecord.isExaminerNode()) {
                                userCount.addExaminer();
                            } else {
                                userCount.addAutoIngestNode();
                            }
                        }
                    }
                }
            }
            
            dataToPlot.add(userCount);
        }
        
        // Get the maximum count
        maxCount = Long.MIN_VALUE;
        for (UserCount userCount:dataToPlot) {
            if(doBarGraph) {
                maxCount = Long.max(maxCount, userCount.getExaminerNodeCount() + userCount.getAutoIngestNodeCount());
            } else {
                maxCount = Long.max(maxCount, userCount.getAutoIngestNodeCount());
                maxCount = Long.max(maxCount, userCount.getExaminerNodeCount());
            }
        }
    }
    
    private class UserCount {
        private final long timestamp;
        private long examinerCount;
        private long autoIngestCount;
        
        UserCount(long timestamp) {
            this.timestamp = timestamp;
            this.examinerCount = 0;
            this.autoIngestCount = 0;
        }
        
        void addExaminer() {
            examinerCount++;
        }
        
        void addAutoIngestNode() {
            autoIngestCount++;
        }
        
        long getExaminerNodeCount() {
            return examinerCount;
        }
        
        long getAutoIngestNodeCount() {
            return autoIngestCount;
        }
        
        long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Setup of the graphics panel:
     * Origin (0,0) is at the top left corner
     * 
     * Horizontally (from the left): (padding)(label padding)(the graph)(padding)
     * For plotting data on the x-axis, we scale it to the size of the graph and then add the padding and label padding
     * 
     * Vertically (from the top): (padding)(the graph)(label padding)(padding)
     * For plotting data on the y-axis, we subtract from the max value in the graph and then scale to the size of the graph
     * @param g 
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Get the max and min timestamps to create the x-axis.
        // We add a small buffer to each side so the data won't overwrite the axes.
        double maxValueOnXAxis = maxTimestamp + TimeUnit.HOURS.toMillis(2); // Two hour buffer
        double minValueOnXAxis = minTimestamp - TimeUnit.HOURS.toMillis(2); // Two hour buffer
        
        // Get the max and min times to create the y-axis
        // To make the intervals even, make sure the maximum is a multiple of five
        if((maxCount % 5) != 0) {
            maxCount += (5 - (maxCount % 5));
        }
        double maxValueOnYAxis = maxCount;
        double minValueOnYAxis = minCount;  
        //minValueOnYAxis = Math.max(0, minValueOnYAxis - (maxValueOnYAxis * 0.1));
        //maxValueOnYAxis = maxValueOnYAxis * 1.1;

        // The graph itself has the following corners:
        // (padding + label padding, padding + font height) -> top left
        // (padding + label padding, getHeight() - label padding - padding) -> bottom left
        // (getWidth() - padding, padding + font height) -> top right 
        // (padding + label padding, getHeight() - label padding - padding) -> bottom right
        int leftGraphPadding = padding + labelPadding;
        int rightGraphPadding = padding;
        int topGraphPadding = padding + g2.getFontMetrics().getHeight();
        int bottomGraphPadding = labelPadding;
        
        // Calculate the scale for each axis.
        // The size of the graph area is the width/height of the panel minus any padding.
        // The scale is calculated based on this size of the graph compared to the data range.
        // For example:
        //   getWidth() = 575 => graph width = 500
        //   If our max x value to plot is 10000 and our min is 0, then the xScale would be 0.05 - i.e.,
        //   our original x values will be multipled by 0.05 to translate them to an x-coordinate in the
        //   graph (plus the padding)
        int graphWidth = getWidth() - leftGraphPadding - rightGraphPadding;
        int graphHeight = getHeight() - topGraphPadding - bottomGraphPadding;
        double xScale = ((double) graphWidth) / (maxValueOnXAxis - minValueOnXAxis);
        double yScale = ((double) graphHeight) / (maxValueOnYAxis - minValueOnYAxis);  
        
        // Draw white background
        g2.setColor(Color.WHITE);
        g2.fillRect(leftGraphPadding, topGraphPadding, graphWidth, graphHeight); 
        
        // Create hatch marks and grid lines for y axis.
        int labelWidth;
        int positionForMetricNameLabel = 0;
        for (int i = 0; i < numberYDivisions + 1; i++) {
            int x0 = leftGraphPadding;
            int x1 = pointWidth + leftGraphPadding;
            int y0 = getHeight() - ((i * graphHeight) / numberYDivisions + bottomGraphPadding);
            int y1 = y0;
            
            if ( ! dataToPlot.isEmpty()) {
                // Draw the grid line
                g2.setColor(gridColor);
                g2.drawLine(leftGraphPadding + 1 + pointWidth, y0, getWidth() - rightGraphPadding, y1);

                // Create the label
                g2.setColor(Color.BLACK);
                double yValue = minValueOnYAxis + ((maxValueOnYAxis - minValueOnYAxis) * ((i * 1.0) / numberYDivisions));
                String yLabel = Double.toString(((int) (yValue * 100)) / 100.0);
                FontMetrics fontMetrics = g2.getFontMetrics();
                labelWidth = fontMetrics.stringWidth(yLabel);
                g2.drawString(yLabel, x0 - labelWidth - 5, y0 + (fontMetrics.getHeight() / 2) - 3);
                
                // The nicest looking alignment for this label seems to be left-aligned with the top
                // y-axis label. Save this position to be used to write the label later.
                if (i == numberYDivisions) {
                    positionForMetricNameLabel = x0 - labelWidth - 5;
                }
            }
            
            // Draw the small hatch mark
            g2.setColor(Color.BLACK);
            g2.drawLine(x0, y0, x1, y1);
        }
        
        // On the x-axis, the farthest right grid line should represent midnight preceding the last recorded value
        Calendar maxDate = new GregorianCalendar();
        maxDate.setTimeInMillis(maxTimestamp);
        maxDate.set(Calendar.HOUR_OF_DAY, 0);
        maxDate.set(Calendar.MINUTE, 0);
        maxDate.set(Calendar.SECOND, 0);
        maxDate.set(Calendar.MILLISECOND, 0);
        long maxMidnightInMillis = maxDate.getTimeInMillis();
        
        // We don't want to display more than 20 grid lines. If we have more
        // data then that, put multiple days within one division
        long totalDays = (maxMidnightInMillis - (long)minValueOnXAxis) / MILLISECONDS_PER_DAY;
        long daysPerDivision;
        if(totalDays <= 20) {
            daysPerDivision = 1;
        } else {
            daysPerDivision = (totalDays / 20);
            if((totalDays % 20) != 0) {
                daysPerDivision++;
            }
        }

        // Draw the vertical grid lines and labels
        // The vertical grid lines will be at midnight, and display the date underneath them
        // At present we use GMT because of some complications with daylight savings time.
        for (long currentDivision = maxMidnightInMillis; currentDivision >= minValueOnXAxis; currentDivision -= MILLISECONDS_PER_DAY * daysPerDivision) {

            int x0 = (int) ((currentDivision - minValueOnXAxis) * xScale + leftGraphPadding);
            int x1 = x0;
            int y0 = getHeight() - bottomGraphPadding;
            int y1 = y0 - pointWidth;

            // Draw the light grey grid line
            g2.setColor(gridColor);
            g2.drawLine(x0, getHeight() - bottomGraphPadding - 1 - pointWidth, x1, topGraphPadding);

            // Draw the hatch mark
            g2.setColor(Color.BLACK);
            g2.drawLine(x0, y0, x1, y1);

            // Draw the label
            Calendar thisDate = new GregorianCalendar();
            thisDate.setTimeZone(TimeZone.getTimeZone("GMT")); // Stick with GMT to avoid daylight savings issues
            thisDate.setTimeInMillis(currentDivision);
            int month = thisDate.get(Calendar.MONTH) + 1;
            int day = thisDate.get(Calendar.DAY_OF_MONTH);
            
            String xLabel = month + "/" + day;
            FontMetrics metrics = g2.getFontMetrics();
            labelWidth = metrics.stringWidth(xLabel);
            g2.drawString(xLabel, x0 - labelWidth / 2, y0 + metrics.getHeight() + 3);           
        }

        // Create x and y axes 
        g2.setColor(Color.BLACK);
        g2.drawLine(leftGraphPadding, getHeight() - bottomGraphPadding, leftGraphPadding, topGraphPadding);
        g2.drawLine(leftGraphPadding, getHeight() - bottomGraphPadding, getWidth() - rightGraphPadding, getHeight() - bottomGraphPadding);
            
        if(! doBarGraph) {
            // Create the points to plot
            List<Point> graphPoints = new ArrayList<>(); 
            for(UserCount userCount:dataToPlot) {
                int x1 = (int) ((userCount.getTimestamp() - minValueOnXAxis) * xScale + leftGraphPadding);
                int y1 = (int) ((maxValueOnYAxis - userCount.getExaminerNodeCount()) * yScale + topGraphPadding);
                //int x1 = (int) ((timingResults.get(i).getTimestamp() - minValueOnXAxis) * xScale + leftGraphPadding);
                //int y1 = (int) ((maxValueOnYAxis - metricTime) * yScale + topGraphPadding);
                graphPoints.add(new Point(x1, y1));   
            }
        
            // Sort the points
            Collections.sort(graphPoints, new Comparator<Point>() {
                @Override
                public int compare(Point o1, Point o2) {
                    if(o1.getX() > o2.getX()) {
                        return 1;
                    } else if (o1.getX() < o2.getX()) {
                        return -1;
                    }
                    return 0;
                }
            });
            
            // Draw the selected type of graph. If there's only one data point,
            // draw that single point.
            g2.setStroke(NARROW_STROKE);
            g2.setColor(examinerColor);
            if(doLineGraph && graphPoints.size() > 1) {
                for (int i = 0; i < graphPoints.size() - 1; i++) {
                    int x1 = graphPoints.get(i).x;
                    int y1 = graphPoints.get(i).y;
                    int x2 = graphPoints.get(i + 1).x;
                    int y2 = graphPoints.get(i + 1).y;
                    g2.drawLine(x1, y1, x2, y2);
                }
            } else {
                for (int i = 0; i < graphPoints.size(); i++) {
                    int x = graphPoints.get(i).x - pointWidth / 2;
                    int y = graphPoints.get(i).y - pointWidth / 2;
                    int ovalW = pointWidth;
                    int ovalH = pointWidth;
                    g2.fillOval(x, y, ovalW, ovalH);
                }
            }
        } else {
            
            // Sort dataToPlot on timestamp
            Collections.sort(dataToPlot, new Comparator<UserCount>(){
                @Override
                public int compare(UserCount o1, UserCount o2){
                    return Long.compare(o1.getTimestamp(), o2.getTimestamp());
                }
            });
            
            for(int i = 0;i < dataToPlot.size();i++) {
                UserCount userCount = dataToPlot.get(i);
                int x = (int) ((userCount.getTimestamp() - minValueOnXAxis) * xScale + leftGraphPadding);
                int yTopOfExaminerBox = (int) ((maxValueOnYAxis - userCount.getExaminerNodeCount() 
                        - userCount.getAutoIngestNodeCount()) * yScale + topGraphPadding);
                int width;
                if(i < dataToPlot.size() - 1) {
                    width = Integer.max((int)((dataToPlot.get(i + 1).getTimestamp() - minValueOnXAxis) * xScale + leftGraphPadding) - x - 1,
                            1);
                } else {
                    width = Integer.max((int)(dataInterval * xScale), 1);
                }
                int heightExaminerBox = (int)(userCount.getExaminerNodeCount() * yScale);
                
                g2.setColor(examinerColor);
                g2.fillRect(x, yTopOfExaminerBox, width, heightExaminerBox);
                
                int yTopOfAutoIngestBox = yTopOfExaminerBox + heightExaminerBox;
                int heightAutoIngestBox = (int)(userCount.getAutoIngestNodeCount() * yScale);
                
                g2.setColor(autoIngestColor);
                g2.fillRect(x, yTopOfAutoIngestBox, width, heightAutoIngestBox);
                
            }
        }
        
        // The graph lines may have extended up past the bounds of the graph. Overwrite that
        // area with the original background color.
        g2.setColor(this.getBackground());
        g2.fillRect(leftGraphPadding, 0, graphWidth, topGraphPadding); 

        // Write the scale. Do this after we erase the top block of the graph.
        g2.setColor(Color.BLACK);
        String titleStr = graphLabel;
        g2.drawString(titleStr, positionForMetricNameLabel, padding);
    }
    

}
