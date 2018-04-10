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
import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;
import java.util.GregorianCalendar;
import javax.swing.JPanel;
import org.sleuthkit.autopsy.healthmonitor.ServicesHealthMonitor.DatabaseTimingResult;

/**
 *
 */
class TimingMetricGraphPanel extends JPanel {
    
    private int width = 800;
    private int heigth = 400;
    private int padding = 25;
    private int labelPadding = 25;
    private Color lineColor = new Color(44, 102, 230, 180);
    private Color pointColor = new Color(100, 100, 100, 180);
    private Color gridColor = new Color(200, 200, 200, 200);
    private static final Stroke GRAPH_STROKE = new BasicStroke(2f);
    private int pointWidth = 4;
    private int numberYDivisions = 10;
    private final List<DatabaseTimingResult> timingResults;
    private final TimingMetricType timingMetricType;
    private boolean doLineGraph = false;
    private final long MILLISECONDS_PER_DAY = 1000 * 60 * 60 * 24;

    TimingMetricGraphPanel(List<DatabaseTimingResult> timingResults, TimingMetricType timingMetricType) {
        this.timingResults = timingResults;
        this.timingMetricType = timingMetricType;
    }
    
    private double getMaxMetricTime() {
        // Find the highest of the max values
        double maxScore = Double.MIN_VALUE;
        for (DatabaseTimingResult score : timingResults) {
            // Use only the data we're graphing to determing the max
            switch (timingMetricType) {
                case MAX:
                case ALL:
                    maxScore = Math.max(maxScore, score.getMax());
                    break;
                case AVERAGE:
                    maxScore = Math.max(maxScore, score.getAverage());
                    break;
                case MIN:
                    maxScore = Math.max(maxScore, score.getMin());
                    break;
            }
        }
        return maxScore;
    }
    
    private double getMinMetricTime() {
        // Find the highest of the max values
        double minScore = Double.MAX_VALUE;
        for (DatabaseTimingResult score : timingResults) {
            // Use only the data we're graphing to determing the min
            switch (timingMetricType) {
                case MAX:
                    minScore = Math.min(minScore, score.getMax());
                    break;
                case AVERAGE:
                    minScore = Math.min(minScore, score.getAverage());
                    break;
                case MIN:
                case ALL:
                    minScore = Math.min(minScore, score.getMin());
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
    
    /*
    private double convertToDaysAgo(long thisTimestamp, long maxTimestamp) {
        long diffInMilliSecs = maxTimestamp - thisTimestamp;
        //System.out.println("Diff between " + maxTimestamp + " and " + thisTimestamp + " = " + diffInMilliSecs + " (milliseconds)");
        double diffInSeconds = diffInMilliSecs / (1000L);
        //System.out.println(" Diff in seconds: " + diffInSeconds);
        double result = diffInSeconds / (60 * 60 * 24);
        //System.out.println(" Diff in days: " + result);
        return (diffInSeconds / (60 * 60 * 24));
    }*/

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
        //minTimestamp = minTimestamp * 0.995; // This is to get a small gap before the first data points
        //double maxDaysAgo = convertToDaysAgo(getMinTimestamp(), maxTimestamp);
        //maxDaysAgo = maxDaysAgo * 1.005;
        //double minDaysAgo = 0;
        
        // Get the max and min times to create the y-axis
        double maxMetricTime = getMaxMetricTime();
        double minMetricTime = getMinMetricTime();  
        minMetricTime = Math.max(0, minMetricTime - (maxMetricTime * 0.1));
        maxMetricTime = maxMetricTime * 1.1;

        
        //System.out.println("maxDaysAgo: " + minDaysAgo + ", minDaysAgo: " + maxDaysAgo);
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

        // and for x axis
        
        // Calculate the number of divisions for the X axis - this will be
        // the number of days between the first and last metrics
        //int numberOfXDivisions = -1 * (int)(maxDaysAgo);
        
        // What we want is midnight preceding the last recorded value
        Calendar maxDate = new GregorianCalendar();
        maxDate.setTimeInMillis(originalMaxTimestamp);
        maxDate.set(Calendar.HOUR_OF_DAY, 0);
        maxDate.set(Calendar.MINUTE, 0);
        maxDate.set(Calendar.SECOND, 0);
        maxDate.set(Calendar.MILLISECOND, 0);
        
        long lastMidnightInMillis = maxDate.getTimeInMillis();
        System.out.println("Last timestamp: " + originalMaxTimestamp + ", last midnight: " + lastMidnightInMillis);
        
        // We don't want to display more than 20 lines
        long totalMidnights = (lastMidnightInMillis - getMinTimestamp()) / MILLISECONDS_PER_DAY;
        System.out.println("  Total midnights: " + totalMidnights);
        long daysPerDivision;
        if(totalMidnights <= 20) {
            daysPerDivision = 1;
        } else {
            daysPerDivision = (totalMidnights / 20);
            if((totalMidnights % 20) != 0) {
                daysPerDivision++;
            }
        }

        for (long currentDivision = lastMidnightInMillis;currentDivision > 0;currentDivision -= MILLISECONDS_PER_DAY * daysPerDivision) {


                //int x0 = i * (getWidth() - padding * 2 - labelPadding) / numberOfXDivisions + padding + labelPadding;
                int x0 = (int) ((double)(currentDivision) * xScale + padding + labelPadding);
                int x1 = x0;
                int y0 = getHeight() - padding - labelPadding;
                int y1 = y0 - pointWidth;
                /*
                if ((i % ((int) ((numberOfXDivisions / 20.0)) + 1)) == 0) {
                    g2.setColor(gridColor);
                    g2.drawLine(x0, getHeight() - padding - labelPadding - 1 - pointWidth, x1, padding);
                    g2.setColor(Color.BLACK);
                    String xLabel = i + "";
                    FontMetrics metrics = g2.getFontMetrics();
                    int labelWidth = metrics.stringWidth(xLabel);
                    g2.drawString(xLabel, x0 - labelWidth / 2, y0 + metrics.getHeight() + 3);
                }*/
                g2.drawLine(x0, y0, x1, y1);
            
        }

        // create x and y axes 
        g2.drawLine(padding + labelPadding, getHeight() - padding - labelPadding, padding + labelPadding, padding);
        g2.drawLine(padding + labelPadding, getHeight() - padding - labelPadding, getWidth() - padding, getHeight() - padding - labelPadding);

        
        Stroke oldStroke = g2.getStroke();
        g2.setStroke(GRAPH_STROKE);
               
        // Plot the average timing points
        if(timingMetricType.equals(TimingMetricType.ALL) || timingMetricType.equals(TimingMetricType.AVERAGE)) {
            List<Point> averageGraphPoints = new ArrayList<>();
            for (int i = 0; i < timingResults.size(); i++) {                
                int x1 = (int) ((double)(maxTimestamp - timingResults.get(i).getTimestamp()) * xScale + padding + labelPadding);
                //int x1 = (int) ((maxDaysAgo - convertToDaysAgo(timingResults.get(i).getTimestamp(), maxTimestamp)) * xScale + padding + labelPadding);
                int yAve = (int) ((maxMetricTime - timingResults.get(i).getAverage()) * yScale + padding);
                //System.out.println("Adding point " + x1 + ", " + yAve);
                averageGraphPoints.add(new Point(x1, yAve));
            }            
            
            g2.setColor(lineColor);
            if(doLineGraph) {
                for (int i = 0; i < averageGraphPoints.size() - 1; i++) {
                    int x1 = averageGraphPoints.get(i).x;
                    int y1 = averageGraphPoints.get(i).y;
                    int x2 = averageGraphPoints.get(i + 1).x;
                    int y2 = averageGraphPoints.get(i + 1).y;
                    g2.drawLine(x1, y1, x2, y2);
                }
            } else {
                for (int i = 0; i < averageGraphPoints.size(); i++) {
                int x = averageGraphPoints.get(i).x - pointWidth / 2;
                int y = averageGraphPoints.get(i).y - pointWidth / 2;
                int ovalW = pointWidth;
                int ovalH = pointWidth;
                g2.fillOval(x, y, ovalW, ovalH);
            }
            }
        }
        
        // Plot the maximum timing points
        if(timingMetricType.equals(TimingMetricType.ALL) || timingMetricType.equals(TimingMetricType.MIN)) {
            List<Point> minGraphPoints = new ArrayList<>();
            for (int i = 0; i < timingResults.size(); i++) {
                int x1 = (int) (i * xScale + padding + labelPadding);
                int yMin = (int) ((maxMetricTime - timingResults.get(i).getMin()) * yScale + padding);
                minGraphPoints.add(new Point(x1, yMin));
            }
            
            g2.setColor(new Color(10, 200, 20));
            for (int i = 0; i < minGraphPoints.size() - 1; i++) {
                int x1 = minGraphPoints.get(i).x;
                int y1 = minGraphPoints.get(i).y;
                int x2 = minGraphPoints.get(i + 1).x;
                int y2 = minGraphPoints.get(i + 1).y;
                g2.drawLine(x1, y1, x2, y2);
            }
        }
        
        // Plot the minimum timing points
        if(timingMetricType.equals(TimingMetricType.ALL) || timingMetricType.equals(TimingMetricType.MIN)) {
            List<Point> maxGraphPoints = new ArrayList<>();
            for (int i = 0; i < timingResults.size(); i++) {
                int x1 = (int) (i * xScale + padding + labelPadding);
                int yMax = (int) ((maxMetricTime - timingResults.get(i).getMax()) * yScale + padding);
                maxGraphPoints.add(new Point(x1, yMax));
            }
            
            g2.setColor(new Color(10, 10, 200));
            for (int i = 0; i < maxGraphPoints.size() - 1; i++) {
                int x1 = maxGraphPoints.get(i).x;
                int y1 = maxGraphPoints.get(i).y;
                int x2 = maxGraphPoints.get(i + 1).x;
                int y2 = maxGraphPoints.get(i + 1).y;
                g2.drawLine(x1, y1, x2, y2);
            }
        }

        /*
        // To draw points
        g2.setStroke(oldStroke);
        g2.setColor(pointColor);
        for (int i = 0; i < graphPoints.size(); i++) {
            int x = graphPoints.get(i).x - pointWidth / 2;
            int y = graphPoints.get(i).y - pointWidth / 2;
            int ovalW = pointWidth;
            int ovalH = pointWidth;
            g2.fillOval(x, y, ovalW, ovalH);
        }*/
    }
    
    enum TimingMetricType {
        AVERAGE,
        MAX,
        MIN,
        ALL;
    }
}
