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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
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
import org.sleuthkit.autopsy.healthmonitor.ServicesHealthMonitor.DatabaseTimingResult;

/**
 *
 */
class TimingMetricGraphPanel extends JPanel {
    
    private final static Logger logger = Logger.getLogger(TimingMetricGraphPanel.class.getName());
    
    private int width = 800;
    private int heigth = 400;
    private int padding = 25;
    private int labelPadding = 25;
    private Color lineColor = new Color(0x12, 0x20, 0xdb, 180);
    private Color pointColor = new Color(100, 100, 100, 180);
    private Color gridColor = new Color(200, 200, 200, 200);
    private Color trendLineColor = new Color(150, 10, 10, 200);
    private static final Stroke GRAPH_STROKE = new BasicStroke(2f);
    private int pointWidth = 4;
    private int numberYDivisions = 10;
    private final List<DatabaseTimingResult> timingResults;
    private final TimingMetricType timingMetricType;
    private final boolean doLineGraph;
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
    
    private long getMaxTimestamp() {
        long maxTimestamp = Long.MIN_VALUE;
        for (DatabaseTimingResult score : timingResults) {
            maxTimestamp = Math.max(maxTimestamp, score.getTimestamp());
        }
        return maxTimestamp;
    }
    
    private long getMinTimestamp() {
        long minTimestamp = Long.MAX_VALUE;
        for (DatabaseTimingResult score : timingResults) {
            minTimestamp = Math.min(minTimestamp, score.getTimestamp());
        }
        return minTimestamp;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Get the max and min timestamps and convert to days to create the x-axis
        long originalMaxTimestamp = getMaxTimestamp();
        double maxTimestamp = (double) originalMaxTimestamp + 1000 * 60 *60 * 4; // Four hour buffer
        double minTimestamp = getMinTimestamp() - 1000 * 60 * 60 * 4; // Four hour buffer
        System.out.println("originalMax: " + originalMaxTimestamp + "  new max: " + maxTimestamp + "  new min: " + minTimestamp);
        
        // Get the max and min times to create the y-axis
        double maxMetricTime = getMaxMetricTime();
        double minMetricTime = getMinMetricTime();  
        minMetricTime = Math.max(0, minMetricTime - (maxMetricTime * 0.1));
        maxMetricTime = maxMetricTime * 1.1;

        double xScale = ((double) getWidth() - (2 * padding) - labelPadding) / (maxTimestamp - minTimestamp);
        double yScale = ((double) getHeight() - (2 * padding) - labelPadding) / (maxMetricTime - minMetricTime);
        
        System.out.println("xScale: " + xScale + ", yScale: " + yScale);

        // draw white background
        g2.setColor(Color.WHITE);
        g2.fillRect(padding + labelPadding, padding, getWidth() - (2 * padding) - labelPadding, getHeight() - 2 * padding - labelPadding);
        g2.setColor(Color.BLACK);

        // create hatch marks and grid lines for y axis.
        for (int i = 0; i < numberYDivisions + 1; i++) {
            int x0 = padding + labelPadding;
            int x1 = pointWidth + padding + labelPadding;
            int y0 = getHeight() - ((i * (getHeight() - padding * 2 - labelPadding)) / numberYDivisions + padding + labelPadding);
            int y1 = y0;
            if (timingResults.size() > 0) {
                g2.setColor(gridColor);
                g2.drawLine(padding + labelPadding + 1 + pointWidth, y0, getWidth() - padding, y1);
                g2.setColor(Color.BLACK);
                String yLabel = ((int) ((getMinMetricTime() + (maxMetricTime - minMetricTime) * ((i * 1.0) / numberYDivisions)) * 100)) / 100.0 + "";
                FontMetrics metrics = g2.getFontMetrics();
                int labelWidth = metrics.stringWidth(yLabel);
                g2.drawString(yLabel, x0 - labelWidth - 5, y0 + (metrics.getHeight() / 2) - 3);
            }
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
        System.out.println("Last timestamp: " + originalMaxTimestamp + ", last midnight: " + maxMidnightInMillis);
        
        // We don't want to display more than 20 grid lines
        long totalDays = (maxMidnightInMillis - getMinTimestamp()) / MILLISECONDS_PER_DAY;
        System.out.println("  Total days: " + totalDays);
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
        for (long currentDivision = maxMidnightInMillis; currentDivision >= minTimestamp; currentDivision -= MILLISECONDS_PER_DAY * daysPerDivision) {

            int x0 = (int) ((double)(currentDivision - minTimestamp) * xScale + padding + labelPadding);
            int x1 = x0;
            int y0 = getHeight() - padding - labelPadding;
            int y1 = y0 - pointWidth;

            // Draw the light grey grid line
            g2.setColor(gridColor);
            g2.drawLine(x0, getHeight() - padding - labelPadding - 1 - pointWidth, x1, padding);

            // Draw the hatch mark
            g2.setColor(Color.BLACK);
            g2.drawLine(x0, y0, x1, y1);

            // Draw the label
            Calendar thisDate = new GregorianCalendar();
            thisDate.setTimeInMillis(currentDivision);
            int month = thisDate.get(Calendar.MONTH) + 1;
            int day = thisDate.get(Calendar.DAY_OF_MONTH);
            String xLabel = month + "/" + day;
            FontMetrics metrics = g2.getFontMetrics();
            int labelWidth = metrics.stringWidth(xLabel);
            g2.drawString(xLabel, x0 - labelWidth / 2, y0 + metrics.getHeight() + 3);           
        }

        // create x and y axes 
        g2.drawLine(padding + labelPadding, getHeight() - padding - labelPadding, padding + labelPadding, padding);
        g2.drawLine(padding + labelPadding, getHeight() - padding - labelPadding, getWidth() - padding, getHeight() - padding - labelPadding);
            
        // Plot the timing points
        g2.setStroke(GRAPH_STROKE);
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
            
            int x1 = (int) ((double)(maxTimestamp - timingResults.get(i).getTimestamp()) * xScale + padding + labelPadding);
            int yAve = (int) ((maxMetricTime - metricTime) * yScale + padding);
            graphPoints.add(new Point(x1, yAve));   
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
        
        System.out.println("points: ");
        for(Point p:graphPoints){
            System.out.println(p.getX() + ", " + p.getY());
        }
            
        g2.setColor(lineColor);
        if(doLineGraph) {
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
        int x0 = (int) ((double)(maxTimestamp - minTimestamp) * xScale + padding + labelPadding);
        int y0 = (int) ((double)(maxMetricTime - trendLine.getExpectedValueAt(minTimestamp)) * yScale + padding);
        int x1 = (int) ((double)(0) * xScale + padding + labelPadding);
        int y1 = (int) ((double)(maxMetricTime - trendLine.getExpectedValueAt(maxTimestamp)) * yScale + padding);
        g2.setColor(trendLineColor);
        g2.drawLine(x0, y0, x1, y1);
    }
    
    enum TimingMetricType {
        AVERAGE,
        MAX,
        MIN;
    }
}
