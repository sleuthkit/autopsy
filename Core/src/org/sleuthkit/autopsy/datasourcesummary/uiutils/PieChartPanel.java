/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.text.DecimalFormat;
import java.util.List;
import javax.swing.JLabel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.PieSectionLabelGenerator;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.panel.AbstractOverlay;
import org.jfree.chart.panel.Overlay;
import org.jfree.chart.plot.PiePlot;
import org.jfree.data.general.DefaultPieDataset;

/**
 *
 * @author gregd
 */
public class PieChartPanel extends AbstractLoadableComponent<PieChartPanel.PieChartItem> {

    /**
     * 
     */
    public static class PieChartItem {
        private final String label;
        private final double value;

        public PieChartItem(String label, double value) {
            this.label = label;
            this.value = value;
        }

        public String getLabel() {
            return label;
        }

        public double getValue() {
            return value;
        }
    }
    
    private static class MessageOverlay extends AbstractOverlay implements Overlay {

        private static final long serialVersionUID = 1L;
        private BaseMessageOverlay overlay = new BaseMessageOverlay();

        /**
         * Sets this layer visible when painted. In order to be shown in UI,
         * this component needs to be repainted.
         *
         * @param visible Whether or not it is visible.
         */
        void setVisible(boolean visible) {
            overlay.setVisible(visible);
        }

        /**
         * Sets the message to be displayed in the child jlabel.
         *
         * @param message The message to be displayed.
         */
        void setMessage(String message) {
            overlay.setMessage(message);
        }
        
        @Override
        public void paintOverlay(Graphics2D gd, ChartPanel cp) {
            overlay.paintOverlay(gd, cp.getWidth(), cp.getHeight());
        }
        
    }
    
    
    private static final long serialVersionUID = 1L;

    private static final Font DEFAULT_FONT = new JLabel().getFont();
    private static final Font DEFAULT_HEADER_FONT = new Font(DEFAULT_FONT.getName(), DEFAULT_FONT.getStyle(), (int) (DEFAULT_FONT.getSize() * 1.5));
    private static final PieSectionLabelGenerator DEFAULT_LABEL_GENERATOR = 
            new StandardPieSectionLabelGenerator(
                    "{0}: {1} ({2})", new DecimalFormat("0"), new DecimalFormat("0.0%"));
    
    private final MessageOverlay overlay = new MessageOverlay();
    private final DefaultPieDataset dataset = new DefaultPieDataset();
    private final JFreeChart chart;
    
    public PieChartPanel() {
        this(null);
    }
    
    public PieChartPanel(String title) {
        // Create chart
        this.chart = ChartFactory.createPieChart(
                title,
                dataset,
                true,
                true,
                false);

        chart.setBackgroundPaint(null);
        chart.getLegend().setItemFont(DEFAULT_FONT);
        chart.getTitle().setFont(DEFAULT_HEADER_FONT);

        PiePlot plot = ((PiePlot) chart.getPlot());

        plot.setLabelGenerator(DEFAULT_LABEL_GENERATOR);
        plot.setLabelFont(DEFAULT_FONT);
        plot.setBackgroundPaint(null);
        plot.setOutlinePaint(null);

        // Create Panel
        ChartPanel panel = new ChartPanel(chart);
        panel.addOverlay(overlay);
        this.setLayout(new BorderLayout());
        this.add(panel, BorderLayout.CENTER);
    }

    public String getTitle() {
        return (this.chart == null || this.chart.getTitle() == null) ? 
                null :
                this.chart.getTitle().getText();
    }

    
    public PieChartPanel setTitle(String title) {
        this.chart.getTitle().setText(title);
        return this;
    }
    
    @Override
    protected void setOverlay(boolean visible, String message) {
        this.overlay.setVisible(visible);
        this.overlay.setMessage(message);
    }

    @Override
    protected void setResultList(List<PieChartPanel.PieChartItem> data) {
        this.dataset.clear();
        if (data != null) {
            for (PieChartPanel.PieChartItem slice : data) {
                this.dataset.setValue(slice.getLabel(), slice.getValue());
            }
        }
    }
}
