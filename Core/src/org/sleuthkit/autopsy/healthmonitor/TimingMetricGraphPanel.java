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
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;
import java.util.GregorianCalendar;
import javax.swing.JPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.logging.Level;
import java.util.TimeZone;
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
    private TrendLine trendLine;
    private final long MILLISECONDS_PER_DAY = 1000 * 60 * 60 * 24;

    TimingMetricGraphPanel(List<DatabaseTimingResult> timingResults, TimingMetricType timingMetricType, boolean doLineGraph) {
        this.timingResults = timingResults;
        this.timingMetricType = timingMetricType;
        this.doLineGraph = doLineGraph;
        try {
            trendLine = new TrendLine(timingResults, timingMetricType);
        } catch (HealthMonitorException ex) {
            // Log it, set trendLine to null and continue on
            logger.log(Level.WARNING, "Can not generate a trend line on empty data set");
            trendLine = null;
        }
    }
    
    /**
     * Get the highest metric time for the given type
     * @return the highest metric time
     */
    private double getMaxMetricTime() {
        // Find the highest of the values being graphed
        double maxScore = Double.MIN_VALUE;
        for (DatabaseTimingResult score : timingResults) {
            // Use only the data we're graphing to determing the max
            switch (timingMetricType) {
                case MAX:
                    maxScore = Math.max(maxScore, score.getMax());
                    break;
                case MIN:
                    maxScore = Math.max(maxScore, score.getMin());
                    break;
                case AVERAGE:
                default:
                    maxScore = Math.max(maxScore, score.getAverage());
                    break;
            }
        }
        return maxScore;
    }
    
    /**
     * Get the lowest metric time for the given type
     * @return the lowest metric time
     */
    private double getMinMetricTime() {
        // Find the lowest of the values being graphed
        double minScore = Double.MAX_VALUE;
        for (DatabaseTimingResult score : timingResults) {
            // Use only the data we're graphing to determing the min
            switch (timingMetricType) {
                case MAX:
                    minScore = Math.min(minScore, score.getMax());
                    break;
                case MIN:
                    minScore = Math.min(minScore, score.getMin());
                    break;
                case AVERAGE:
                default:
                    minScore = Math.min(minScore, score.getAverage());
                    break;
            }
        }
        return minScore;
    }
    
    /**
     * Get the largest timestamp in the data collection
     * @return the largest timestamp
     */
    private long getMaxTimestamp() {
        long maxTimestamp = Long.MIN_VALUE;
        for (DatabaseTimingResult score : timingResults) {
            maxTimestamp = Math.max(maxTimestamp, score.getTimestamp());
        }
        return maxTimestamp;
    }
    
    /**
     * Get the smallest timestamp in the data collection
     * @return the minimum timestamp
     */
    private long getMinTimestamp() {
        long minTimestamp = Long.MAX_VALUE;
        for (DatabaseTimingResult score : timingResults) {
            minTimestamp = Math.min(minTimestamp, score.getTimestamp());
        }
        return minTimestamp;
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
        long originalMaxTimestamp = getMaxTimestamp();
        double maxValueOnXAxis = (double) originalMaxTimestamp + 1000 * 60 *60 * 2; // Two hour buffer
        double minValueOnXAxis = getMinTimestamp() - 1000 * 60 * 60 * 2; // Two hour buffer
        
        // Get the max and min times to create the y-axis
        // We add a small buffer to each side so the data won't overwrite the axes.
        double maxValueOnYAxis = getMaxMetricTime();
        double minValueOnYAxis = getMinMetricTime();  
        minValueOnYAxis = Math.max(0, minValueOnYAxis - (maxValueOnYAxis * 0.1));
        maxValueOnYAxis = maxValueOnYAxis * 1.1;

        // The graph itself has the following corners:
        // (padding + label padding, padding) - top left
        // (padding + label padding, getHeight() - label padding - padding x 2) - bottom left
        // (getWidth() - padding, getHeight() - label padding - padding x 2) - top right ??
        // (padding + label padding, getHeight() - label padding - padding x 2) - bottom right
        int leftGraphPadding = padding + labelPadding;
        int rightGraphPadding = padding;
        int topGraphPadding = padding;
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
        
        // draw white background
        g2.setColor(Color.WHITE);
        g2.fillRect(leftGraphPadding, topGraphPadding, graphWidth, graphHeight);       

        // create hatch marks and grid lines for y axis.
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
                String yLabel = ((int) (yValue * 100)) / 100.0 + "";
                FontMetrics fontMetrics = g2.getFontMetrics();
                int labelWidth = fontMetrics.stringWidth(yLabel);
                g2.drawString(yLabel, x0 - labelWidth - 5, y0 + (fontMetrics.getHeight() / 2) - 3);
            }
            
            // Draw the small hatch mark
            g2.setColor(Color.BLACK);
            g2.drawLine(x0, y0, x1, y1);
        }
        
        // On the x-axis, the farthest right grid line should represent midnight preceding the last recorded value
        Calendar maxDate = new GregorianCalendar();
        maxDate.setTimeInMillis(originalMaxTimestamp);
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

            //long currentDivision = 
            int x0 = (int) ((double)(currentDivision - minValueOnXAxis) * xScale + leftGraphPadding);
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
            int labelWidth = metrics.stringWidth(xLabel);
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
        // draw it.
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
        
        // Draw the trend line
        // Don't draw anything if there's only one data point
        if(trendLine != null && (timingResults.size() > 1)) {
            int x0 = (int) ((double)(0) * xScale + padding + labelPadding);
            int y0 = (int) ((double)(maxValueOnYAxis - trendLine.getExpectedValueAt(minValueOnXAxis)) * yScale + padding);
            int x1 = (int) ((double)(maxValueOnXAxis - minValueOnXAxis) * xScale + padding + labelPadding);
            int y1 = (int) ((double)(maxValueOnYAxis - trendLine.getExpectedValueAt(maxValueOnXAxis)) * yScale + padding);
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
   }

}
