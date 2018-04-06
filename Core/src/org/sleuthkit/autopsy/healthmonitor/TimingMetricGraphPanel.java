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
    private List<DatabaseTimingResult> timingResults;

    TimingMetricGraphPanel(List<DatabaseTimingResult> timingResults) {
        this.timingResults = timingResults;
    }
    
    private double getMaxScore() {
        // Find the highest of the max values
        double maxScore = Double.MIN_VALUE;
        for (DatabaseTimingResult score : timingResults) {
            maxScore = Math.max(maxScore, score.getMax());
        }
        return maxScore;
    }
    
    private double getMinScore() {
        // Find the highest of the max values
        double minScore = Double.MAX_VALUE;
        for (DatabaseTimingResult score : timingResults) {
            minScore = Math.min(minScore, score.getMin());
        }
        return minScore;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        double maxScore = getMaxScore();
        double minScore = getMinScore();
        

        double xScale = ((double) getWidth() - (2 * padding) - labelPadding) / (timingResults.size() - 1); // TODO: make this based on timestamps
        double yScale = ((double) getHeight() - 2 * padding - labelPadding) / (maxScore - minScore);

        //List<Point> graphPoints = new ArrayList<>();
        List<Point> averageGraphPoints = new ArrayList<>();
        List<Point> maxGraphPoints = new ArrayList<>();
        List<Point> minGraphPoints = new ArrayList<>();
        for (int i = 0; i < timingResults.size(); i++) {
            int x1 = (int) (i * xScale + padding + labelPadding);
            int yAve = (int) ((maxScore - timingResults.get(i).getAverage()) * yScale + padding);
            int yMax = (int) ((maxScore - timingResults.get(i).getMax()) * yScale + padding);
            int yMin = (int) ((maxScore - timingResults.get(i).getMin()) * yScale + padding);
            averageGraphPoints.add(new Point(x1, yAve));
            maxGraphPoints.add(new Point(x1, yMax));
            minGraphPoints.add(new Point(x1, yMin));
        }

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
                String yLabel = ((int) ((getMinScore() + (maxScore - minScore) * ((i * 1.0) / numberYDivisions)) * 100)) / 100.0 + "";
                FontMetrics metrics = g2.getFontMetrics();
                int labelWidth = metrics.stringWidth(yLabel);
                g2.drawString(yLabel, x0 - labelWidth - 5, y0 + (metrics.getHeight() / 2) - 3);
            }
            g2.drawLine(x0, y0, x1, y1);
        }

        // and for x axis
        for (int i = 0; i < timingResults.size(); i++) {
            if (timingResults.size() > 1) {
                int x0 = i * (getWidth() - padding * 2 - labelPadding) / (timingResults.size() - 1) + padding + labelPadding;
                int x1 = x0;
                int y0 = getHeight() - padding - labelPadding;
                int y1 = y0 - pointWidth;
                if ((i % ((int) ((timingResults.size() / 20.0)) + 1)) == 0) {
                    g2.setColor(gridColor);
                    g2.drawLine(x0, getHeight() - padding - labelPadding - 1 - pointWidth, x1, padding);
                    g2.setColor(Color.BLACK);
                    String xLabel = i + "";
                    FontMetrics metrics = g2.getFontMetrics();
                    int labelWidth = metrics.stringWidth(xLabel);
                    g2.drawString(xLabel, x0 - labelWidth / 2, y0 + metrics.getHeight() + 3);
                }
                g2.drawLine(x0, y0, x1, y1);
            }
        }

        // create x and y axes 
        g2.drawLine(padding + labelPadding, getHeight() - padding - labelPadding, padding + labelPadding, padding);
        g2.drawLine(padding + labelPadding, getHeight() - padding - labelPadding, getWidth() - padding, getHeight() - padding - labelPadding);

        Stroke oldStroke = g2.getStroke();
        g2.setColor(lineColor);
        g2.setStroke(GRAPH_STROKE);
        for (int i = 0; i < averageGraphPoints.size() - 1; i++) {
            int x1 = averageGraphPoints.get(i).x;
            int y1 = averageGraphPoints.get(i).y;
            int x2 = averageGraphPoints.get(i + 1).x;
            int y2 = averageGraphPoints.get(i + 1).y;
            g2.drawLine(x1, y1, x2, y2);
        }
        
        g2.setColor(new Color(10, 10, 200));
        for (int i = 0; i < maxGraphPoints.size() - 1; i++) {
            int x1 = maxGraphPoints.get(i).x;
            int y1 = maxGraphPoints.get(i).y;
            int x2 = maxGraphPoints.get(i + 1).x;
            int y2 = maxGraphPoints.get(i + 1).y;
            g2.drawLine(x1, y1, x2, y2);
        }
        
        g2.setColor(new Color(10, 200, 20));
        for (int i = 0; i < minGraphPoints.size() - 1; i++) {
            int x1 = minGraphPoints.get(i).x;
            int y1 = minGraphPoints.get(i).y;
            int x2 = minGraphPoints.get(i + 1).x;
            int y2 = minGraphPoints.get(i + 1).y;
            g2.drawLine(x1, y1, x2, y2);
        }

        g2.setStroke(oldStroke);
        g2.setColor(pointColor);
        /*for (int i = 0; i < graphPoints.size(); i++) {
            int x = graphPoints.get(i).x - pointWidth / 2;
            int y = graphPoints.get(i).y - pointWidth / 2;
            int ovalW = pointWidth;
            int ovalH = pointWidth;
            g2.fillOval(x, y, ovalW, ovalH);
        }*/
    }
    
}
