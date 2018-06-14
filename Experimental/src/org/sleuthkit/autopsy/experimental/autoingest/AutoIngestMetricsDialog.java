/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.awt.Container;
import java.awt.Cursor;
import java.awt.Window;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.util.List;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestMetricsCollector.JobMetric;

/**
 * Displays auto ingest metrics for a cluster.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class AutoIngestMetricsDialog extends javax.swing.JDialog {

    private static final int GIGABYTE_SIZE = 1073741824;

    private AutoIngestMetricsCollector autoIngestMetricsCollector;

    /**
     * Creates an instance of AutoIngestMetricsDialog.
     *
     * @param parent The parent container.
     */
    @Messages({
        "AutoIngestMetricsDialog.title.text=Auto Ingest Metrics",
        "AutoIngestMetricsDialog.initReportText=Select a date above and click the 'Generate Metrics Report' button to generate\na metrics report."
    })
    AutoIngestMetricsDialog(Container parent) {
        super((Window) parent, NbBundle.getMessage(AutoIngestMetricsDialog.class, "AutoIngestMetricsDialog.title.text"), ModalityType.MODELESS);
        initComponents();
        reportTextArea.setText(NbBundle.getMessage(AutoIngestMetricsDialog.class, "AutoIngestMetricsDialog.initReportText"));
        setModal(true);
        setSize(getPreferredSize());
        setLocationRelativeTo(parent);
        setAlwaysOnTop(false);
        setVisible(true);
    }

    /**
     * Update the metrics shown in the report text area.
     *
     * @throws AutoIngestMetricsDialogException When the initialization of the
     *                                          AutoIngestMetricsCollector
     *                                          fails.
     */
    private void updateMetrics() throws AutoIngestMetricsDialogException {
        if (datePicker.getDate() == null) {
            return;
        }
        
        if(autoIngestMetricsCollector == null) {
            try {
                autoIngestMetricsCollector = new AutoIngestMetricsCollector();
            } catch (AutoIngestMetricsCollector.AutoIngestMetricsCollectorException ex) {
                throw new AutoIngestMetricsDialogException("Error initializing the auto ingest metrics collector.", ex);
            }
        }

        AutoIngestMetricsCollector.MetricsSnapshot metricsSnapshot = autoIngestMetricsCollector.queryCoordinationServiceForMetrics();
        List<JobMetric> completedJobMetrics = metricsSnapshot.getCompletedJobMetrics();
        int jobsCompleted = 0;
        long dataSourceSizeTotal = 0;
        long pickedDate = datePicker.getDate().atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000;

        for (JobMetric jobMetric : completedJobMetrics) {
            if (jobMetric.getCompletedDate() >= pickedDate) {
                jobsCompleted++;
                dataSourceSizeTotal += jobMetric.getDataSourceSize();
            }
        }

        SimpleDateFormat dateFormatter = new SimpleDateFormat("MMM d, yyyy");
        reportTextArea.setText(String.format(
                "Since %s:\n"
                + "Number of Jobs Completed: %d\n"
                + "Total Size of Data Sources: %.1f GB\n",
                dateFormatter.format(Date.valueOf(datePicker.getDate())),
                jobsCompleted,
                (double) dataSourceSizeTotal / GIGABYTE_SIZE
        ));
    }

    /**
     * Exception type thrown when there is an error completing an auto ingest
     * metrics dialog operation.
     */
    static final class AutoIngestMetricsDialogException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an instance of the exception type thrown when there is an
         * error completing an auto ingest metrics dialog operation.
         *
         * @param message The exception message.
         */
        private AutoIngestMetricsDialogException(String message) {
            super(message);
        }

        /**
         * Constructs an instance of the exception type thrown when there is an
         * error completing an auto ingest metrics dialog operation.
         *
         * @param message The exception message.
         * @param cause   A Throwable cause for the error.
         */
        private AutoIngestMetricsDialogException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        closeButton = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        reportTextArea = new javax.swing.JTextArea();
        metricsButton = new javax.swing.JButton();
        startingDataLabel = new javax.swing.JLabel();
        datePicker = new com.github.lgooddatepicker.components.DatePicker();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setAlwaysOnTop(true);
        setResizable(false);

        org.openide.awt.Mnemonics.setLocalizedText(closeButton, org.openide.util.NbBundle.getMessage(AutoIngestMetricsDialog.class, "AutoIngestMetricsDialog.closeButton.text")); // NOI18N
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
            }
        });

        reportTextArea.setEditable(false);
        reportTextArea.setColumns(20);
        reportTextArea.setRows(5);
        reportTextArea.setText(org.openide.util.NbBundle.getMessage(AutoIngestMetricsDialog.class, "AutoIngestMetricsDialog.reportTextArea.text")); // NOI18N
        jScrollPane1.setViewportView(reportTextArea);

        org.openide.awt.Mnemonics.setLocalizedText(metricsButton, org.openide.util.NbBundle.getMessage(AutoIngestMetricsDialog.class, "AutoIngestMetricsDialog.metricsButton.text")); // NOI18N
        metricsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                metricsButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(startingDataLabel, org.openide.util.NbBundle.getMessage(AutoIngestMetricsDialog.class, "AutoIngestMetricsDialog.startingDataLabel.text")); // NOI18N

        datePicker.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestMetricsDialog.class, "AutoIngestMetricsDialog.datePicker.toolTipText")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(startingDataLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(datePicker, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(metricsButton))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(closeButton, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(metricsButton)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(startingDataLabel)
                        .addComponent(datePicker, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(closeButton)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
        setVisible(false);
        dispose();
    }//GEN-LAST:event_closeButtonActionPerformed

    private void metricsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_metricsButtonActionPerformed
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            updateMetrics();
        } catch (AutoIngestMetricsDialogException ex) {
            MessageNotifyUtil.Message.error(ex.getMessage());
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }//GEN-LAST:event_metricsButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton closeButton;
    private com.github.lgooddatepicker.components.DatePicker datePicker;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JButton metricsButton;
    private javax.swing.JTextArea reportTextArea;
    private javax.swing.JLabel startingDataLabel;
    // End of variables declaration//GEN-END:variables
}
