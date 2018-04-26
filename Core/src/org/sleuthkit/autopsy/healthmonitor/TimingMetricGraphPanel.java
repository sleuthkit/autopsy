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
import java.util.Calendar;
import java.util.GregorianCalendar;
import javax.swing.JPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.logging.Level;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import org.sleuthkit.autopsy.healthmonitor.EnterpriseHealthMonitor.DatabaseTimingResult;

/**
 * Creates a graph of the given timing metric data
 */
class TimingMetricGraphPanel extends JPanel {
    
    private final static Logger logger = Logger.getLogger(TimingMetricGraphPanel.class.getName());
    
    private int padding = 25;
    private int labelPadding = 25;
    private Color lineColor = new Color(0x12, 0x20, 0xdb, 180);
    private Color gridColor = new Color(200, 200, 200, 200);
    private Color trendLineColor = new Color(150, 10, 10, 200);
    private static final Stroke GRAPH_STROKE = new BasicStroke(2f);
    private static final Stroke NARROW_STROKE = new BasicStroke(1f);
    private int pointWidth = 4;
    private int numberYDivisions = 10;
    private List<DatabaseTimingResult> timingResults;
    private TimingMetricType timingMetricType;
    private boolean doLineGraph;
    private String yUnitString;
    private TrendLine trendLine;
    private final long MILLISECONDS_PER_DAY = 1000 * 60 * 60 * 24;
    long maxTimestamp;
    long minTimestamp;
    double maxMetricTime;
    double minMetricTime;

    TimingMetricGraphPanel(List<DatabaseTimingResult> timingResultsFull, TimingMetricType timingMetricType, String hostName, boolean doLineGraph) {

        this.timingMetricType = timingMetricType;
        this.doLineGraph = doLineGraph;
        if(hostName == null || hostName.isEmpty()) {
            timingResults = timingResultsFull;
        } else {
            timingResults = timingResultsFull.stream()
                            .filter(t -> t.getHostName().equals(hostName))
                            .collect(Collectors.toList());
        }
        
        try {
            trendLine = new TrendLine(timingResults, timingMetricType);
        } catch (HealthMonitorException ex) {
            // Log it, set trendLine to null and continue on
            logger.log(Level.WARNING, "Can not generate a trend line on empty data set");
            trendLine = null;
        }
        
        // Calculate these using the full data set, to make it easier to compare the results for
        // individual hosts
        calcMaxTimestamp(timingResultsFull);
        calcMinTimestamp(timingResultsFull);
        calcMaxMetricTime(timingResultsFull);
        calcMinMetricTime(timingResultsFull);  
        
        
    }
    
    /**
     * Set the highest metric time for the given type
     */
    private void calcMaxMetricTime(List<DatabaseTimingResult> timingResultsFull) {
        // Find the highest of the values being graphed
        maxMetricTime = Double.MIN_VALUE;
        for (DatabaseTimingResult score : timingResultsFull) {
            // Use only the data we're graphing to determing the max
            switch (timingMetricType) {
                case MAX:
                    maxMetricTime = Math.max(maxMetricTime, score.getMax());
                    break;
                case MIN:
                    maxMetricTime = Math.max(maxMetricTime, score.getMin());
                    break;
                case AVERAGE:
                default:
                    maxMetricTime = Math.max(maxMetricTime, score.getAverage());
                    break;
            }
        }
    }
    
    /**
     * Set the lowest metric time for the given type
     */
    private void calcMinMetricTime(List<DatabaseTimingResult> timingResultsFull) {
        // Find the lowest of the values being graphed
        minMetricTime = Double.MAX_VALUE;
        for (DatabaseTimingResult result : timingResultsFull) {
            // Use only the data we're graphing to determing the min
            switch (timingMetricType) {
                case MAX:
                    minMetricTime = Math.min(minMetricTime, result.getMax());
                    break;
                case MIN:
                    minMetricTime = Math.min(minMetricTime, result.getMin());
                    break;
                case AVERAGE:
                default:
                    minMetricTime = Math.min(minMetricTime, result.getAverage());
                    break;
            }
        }
    }
    
    /**
     * Set the largest timestamp in the data collection
     */
    private void calcMaxTimestamp(List<DatabaseTimingResult> timingResultsFull) {
        maxTimestamp = Long.MIN_VALUE;
        for (DatabaseTimingResult score : timingResultsFull) {
            maxTimestamp = Math.max(maxTimestamp, score.getTimestamp());
        }
    }
    
    /**
     * Set the smallest timestamp in the data collection
     */
    private void calcMinTimestamp(List<DatabaseTimingResult> timingResultsFull) {
        minTimestamp = Long.MAX_VALUE;
        for (DatabaseTimingResult score : timingResultsFull) {
            minTimestamp = Math.min(minTimestamp, score.getTimestamp());
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
        double maxValueOnXAxis = maxTimestamp + 1000 * 60 *60 * 2; // Two hour buffer
        double minValueOnXAxis = minTimestamp - 1000 * 60 * 60 * 2; // Two hour buffer
        
        // Get the max and min times to create the y-axis
        // We add a small buffer to each side so the data won't overwrite the axes.
        double maxValueOnYAxis = maxMetricTime;
        double minValueOnYAxis = minMetricTime;  
        minValueOnYAxis = Math.max(0, minValueOnYAxis - (maxValueOnYAxis * 0.1));
        maxValueOnYAxis = maxValueOnYAxis * 1.1;

        // The graph itself has the following corners:
        // (padding + label padding, padding + font height) - top left
        // (padding + label padding, getHeight() - label padding - padding x 2) - bottom left
        // (getWidth() - padding, padding + font height) - top right 
        // (padding + label padding, getHeight() - label padding - padding x 2) - bottom right
        int leftGraphPadding = padding + labelPadding;
        int rightGraphPadding = padding;
        int topGraphPadding = padding + g2.getFontMetrics().getHeight();
        int bottomGraphPadding = padding + labelPadding;
        
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
        
        // Check if we should use a scale other than milliseconds
        long middleOfGraphNano = (long)(minValueOnYAxis + (maxValueOnYAxis - minValueOnYAxis) / 2) * 1000000;
        double yLabelScale;
        
        // The idea here is to pick the scale that would most commonly be used to 
        // represent the middle of our data. For example, if the middle of the graph
        // would be 45,000,000 nanoseconds, then we would use milliseconds for the 
        // y-axis.
        if(middleOfGraphNano < 1000) {
            yUnitString = "nanoseconds";
            yLabelScale = 1000000;
        } else if (TimeUnit.NANOSECONDS.toMicros(middleOfGraphNano) < 1000) {
            yUnitString = "microseconds";
            yLabelScale = 1000;
        } else if (TimeUnit.NANOSECONDS.toMillis(middleOfGraphNano) < 1000) {
            yUnitString = "milliseconds";
            yLabelScale = 1;
        } else if (TimeUnit.NANOSECONDS.toSeconds(middleOfGraphNano) < 60) {
            yUnitString = "seconds";
            yLabelScale = 1.0 / 1000;
        } else if (TimeUnit.NANOSECONDS.toMinutes(middleOfGraphNano) < 60) {
            yUnitString = "minutes";
            yLabelScale = 1.0 / (1000 * 60);
        } else {
            yUnitString = "hours";
            yLabelScale = 1.0 / (1000 * 60 * 60);
        }

        // Draw white background
        g2.setColor(Color.WHITE);
        g2.fillRect(leftGraphPadding, topGraphPadding, graphWidth, graphHeight); 
        
        // Create hatch marks and grid lines for y axis.
        int labelWidth;
        for (int i = 0; i < numberYDivisions + 1; i++) {
            int x0 = leftGraphPadding;
            int x1 = pointWidth + leftGraphPadding;
            int y0 = getHeight() - ((i * graphHeight) / numberYDivisions + bottomGraphPadding);
            int y1 = y0;
            
            if (timingResults.size() > 0) {
                // Draw the grid line
                g2.setColor(gridColor);
                g2.drawLine(leftGraphPadding + 1 + pointWidth, y0, getWidth() - rightGraphPadding, y1);

                // Create the label
                g2.setColor(Color.BLACK);
                double yValue = minValueOnYAxis + ((maxValueOnYAxis - minValueOnYAxis) * ((i * 1.0) / numberYDivisions));
                String yLabel = ((int) (yValue * 100 * yLabelScale)) / 100.0 + "";
                FontMetrics fontMetrics = g2.getFontMetrics();
                labelWidth = fontMetrics.stringWidth(yLabel);
                g2.drawString(yLabel, x0 - labelWidth - 5, y0 + (fontMetrics.getHeight() / 2) - 3);
                
                // The nicest looking alignment for this label seems to be left-aligned with the top
                // y-axis label
                if (i == numberYDivisions) {
                    // Write the scale
                    g2.setColor(Color.BLACK);
                    String scaleStr = "Displaying time in " + yUnitString;
                    g2.drawString(scaleStr, x0 - labelWidth - 5, padding);
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
            thisDate.setTimeZone(TimeZone.getTimeZone("GMT"));
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
            
        // Create the points to plot
        List<Point> graphPoints = new ArrayList<>();
        for (int i = 0; i < timingResults.size(); i++) {     
            double metricTime;
            switch (timingMetricType) {
            case MAX:
                metricTime = timingResults.get(i).getMax();
                break;            
            case MIN:
                metricTime = timingResults.get(i).getMin();
                break;
            case AVERAGE:
            default:
                metricTime = timingResults.get(i).getAverage();
                break;

            }
            
            int x1 = (int) ((timingResults.get(i).getTimestamp() - minValueOnXAxis) * xScale + leftGraphPadding);
            int y1 = (int) ((maxValueOnYAxis - metricTime) * yScale + topGraphPadding);
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
        g2.setColor(lineColor);
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
        
        // Draw the trend line.
        // Don't draw anything if we don't have at least two data points.
        if(trendLine != null && (timingResults.size() > 1)) {
            double x0value = minValueOnXAxis;
            double y0value = trendLine.getExpectedValueAt(x0value);
            if (y0value < minValueOnYAxis) {
                try {
                    y0value = minValueOnYAxis;
                    x0value = trendLine.getXGivenY(y0value);
                } catch (HealthMonitorException ex) {
                    // The exception is caused by a slope of zero on the trend line, which 
                    // shouldn't be able to happen at the same time as having a trend line that dips below the y-axis.
                    // If it does, log a warning but continue on with the original values.
                    logger.log(Level.WARNING, "Error plotting trend line", ex);
                }
            } else if (y0value > maxValueOnYAxis) {
                try {
                    y0value = minValueOnYAxis;
                    x0value = trendLine.getXGivenY(y0value);
                } catch (HealthMonitorException ex) {
                    // The exception is caused by a slope of zero on the trend line, which 
                    // shouldn't be able to happen at the same time as having a trend line that dips below the y-axis.
                    // If it does, log a warning but continue on with the original values.
                    logger.log(Level.WARNING, "Error plotting trend line", ex);
                }
            }
            
            int x0 = (int) ((x0value - minValueOnXAxis) * xScale) + leftGraphPadding;
            int y0 = (int) ((maxValueOnYAxis - y0value) * yScale + topGraphPadding);

            double x1value = maxValueOnXAxis;
            double y1value = trendLine.getExpectedValueAt(maxValueOnXAxis);
            if (y1value < minValueOnYAxis) {
                try {
                    y1value = minValueOnYAxis;
                    x1value = trendLine.getXGivenY(y1value);
                } catch (HealthMonitorException ex) {
                    // The exception is caused by a slope of zero on the trend line, which 
                    // shouldn't be able to happen at the same time as having a trend line that dips below the y-axis.
                    // If it does, log a warning but continue on with the original values.
                    logger.log(Level.WARNING, "Error plotting trend line", ex);
                }
            } else if (y1value > maxValueOnYAxis) {
                try {
                    y1value = maxValueOnYAxis;
                    x1value = trendLine.getXGivenY(y1value);
                } catch (HealthMonitorException ex) {
                    // The exception is caused by a slope of zero on the trend line, which 
                    // shouldn't be able to happen at the same time as having a trend line that dips below the y-axis.
                    // If it does, log a warning but continue on with the original values.
                    logger.log(Level.WARNING, "Error plotting trend line", ex);
                }
            }
            
            int x1 = (int) ((x1value - minValueOnXAxis) * xScale) + leftGraphPadding;
            int y1 = (int) ((maxValueOnYAxis - y1value) * yScale + topGraphPadding);
            
            g2.setStroke(GRAPH_STROKE);
            g2.setColor(trendLineColor);
            g2.drawLine(x0, y0, x1, y1);
        }
    }
    
    /**
     * The metric field we want to graph
     */
    enum TimingMetricType {
        AVERAGE,
        MAX,
        MIN;
    }
    
    /**
    * Class to generate a linear trend line from timing metric data.
    * 
    * Formula for the linear trend line:
    * (x,y) = (timestamp, metric time) 
    * n = total number of metrics
    * 
    * slope = ( n * Σ(xy) - Σx * Σy ) / ( n * Σ(x^2) - (Σx)^2 )
    * 
    * y intercept = ( Σy - (slope) * Σx ) / n
    */
    class TrendLine {

        double slope;
        double yInt;

        TrendLine(List<DatabaseTimingResult> timingResults, TimingMetricGraphPanel.TimingMetricType timingMetricType) throws HealthMonitorException {

            if((timingResults == null) || timingResults.isEmpty()) {
                throw new HealthMonitorException("Can not generate trend line for empty/null data set");
            }

            // Calculate intermediate values
            int n = timingResults.size();
            double sumX = 0;
            double sumY = 0;
            double sumXY = 0;
            double sumXsquared = 0;
            for(int i = 0;i < n;i++) {
                double x = timingResults.get(i).getTimestamp();
                double y;
                switch (timingMetricType) {
                    case MAX:
                        y = timingResults.get(i).getMax();
                        break;
                    case MIN:
                        y = timingResults.get(i).getMin();
                        break;
                    case AVERAGE:
                    default:
                        y = timingResults.get(i).getAverage();
                        break;
                }

                sumX += x;
                sumY += y;
                sumXY += x * y;
                sumXsquared += x * x;
            }

            // Calculate slope
            // With only one measurement, the denominator will end being zero in the formula.
            // Use a horizontal line in this case (or any case where the denominator is zero)
            double denominator = n * sumXsquared - sumX * sumX;
            if (denominator != 0) {
                slope = (n * sumXY - sumX * sumY) / denominator;
            } else {
                slope = 0;
            }

            // Calculate y intercept
            yInt = (sumY - slope * sumX) / n;
        }

        /**
         * Get the expected y value for a given x
         * @param x x coordinate of the point on the trend line
         * @return expected y coordinate of this point on the trend line
         */
        double getExpectedValueAt(double x) {
            return (slope * x + yInt);
        }

        /**
         * Get the x-coordinate for a given Y-coordinate.
         * Should only be necessary when the trend line does not fit on the graph
         * @param y the y coordinate
         * @return expected x coordinate for the given y
         * @throws HealthMonitorException 
         */
        double getXGivenY(double y) throws HealthMonitorException {
            if (slope != 0.0) {
                return (y - yInt / slope);
            } else {
                throw new HealthMonitorException("Attempted division by zero in trend line calculation");
            }
        }
   }

}
