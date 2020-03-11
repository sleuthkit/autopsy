/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.infrastructure;

import java.awt.*;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import javax.swing.Box;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.report.ReportProgressPanel.ReportStatus;
import org.sleuthkit.autopsy.report.ReportProgressPanel;

/**
 * A panel that displays a panel used by a report generation module to show
 * progress. It provides OK and Cancel buttons.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class ReportGenerationPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private final GridBagConstraints constraints;
    private final Component glue;
    private ActionListener actionListener;
    private final ReportProgressPanel progressPanel;

    /**
     * Constructs a panel that displays a panel used by a report generation
     * module to show progress. It provides OK and Cancel buttons.
     */
    ReportGenerationPanel() {
        initComponents();
        reportPanel.setLayout(new GridBagLayout());
        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        glue = Box.createVerticalGlue();
        progressPanel = new ReportProgressPanel();
    }

    ReportProgressPanel getProgressPanel() {
        return progressPanel;
    }

    /**
     * Adds a panel used by a report generation module to show progress to this
     * panel.
     *
     * @param reportName The report name.
     * @param reportPath The report file path
     */
    void addReport(String reportName, String reportPath) {
        /*
         * Remove the "glue."
         */
        reportPanel.remove(glue);

        progressPanel.setLabels(reportName, reportPath);
        constraints.weighty = 0.0;
        constraints.anchor = GridBagConstraints.NORTH;
        reportPanel.add(progressPanel, constraints);
        constraints.gridy++;

        /*
         * Add the "glue" back to the bottom of the panel.
         */
        constraints.weighty = 1.0;
        constraints.anchor = GridBagConstraints.PAGE_END;
        reportPanel.add(glue, constraints);

        /*
         * Use 80 pixels per progress panel. This is a leftover from when this
         * panel used to show multiple report progress panels.
         */
        reportPanel.setPreferredSize(new Dimension(600, 1 * 80));
        reportPanel.repaint();
        progressPanel.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            String propName = evt.getPropertyName();
            if (propName.equals(ReportProgressPanel.ReportStatus.COMPLETE.toString())
                    || propName.equals(ReportProgressPanel.ReportStatus.CANCELED.toString())) {
                SwingUtilities.invokeLater(() -> {
                    cancelButton.setEnabled(false);
                });
            }
        });
    }

    /**
     * Closes this panel and its dialog if all reports are done.
     */
    void close() {
        boolean closeable = true;
        if (progressPanel.getStatus() != ReportStatus.CANCELED && progressPanel.getStatus() != ReportStatus.COMPLETE && progressPanel.getStatus() != ReportStatus.ERROR) {
            closeable = false;
        }
        if (closeable) {
            actionListener.actionPerformed(null);
        } else {
            int result = JOptionPane.showConfirmDialog(this,
                    NbBundle.getMessage(this.getClass(),
                            "ReportGenerationPanel.confDlg.sureToClose.msg"),
                    NbBundle.getMessage(this.getClass(),
                            "ReportGenerationPanel.confDlg.title.closing"),
                    JOptionPane.YES_NO_OPTION);
            if (result == 0) {
                progressPanel.cancel();
                actionListener.actionPerformed(null);
            }
        }
    }

    /**
     * Adds a close action listener to this panel.
     *
     * @param listener The listener to add.
     */
    void addCloseAction(ActionListener listener) {
        this.actionListener = listener;
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
        cancelButton = new javax.swing.JButton();
        reportPanel = new javax.swing.JPanel();

        setPreferredSize(new java.awt.Dimension(700, 400));

        org.openide.awt.Mnemonics.setLocalizedText(closeButton, org.openide.util.NbBundle.getMessage(ReportGenerationPanel.class, "ReportGenerationPanel.closeButton.text")); // NOI18N
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(ReportGenerationPanel.class, "ReportGenerationPanel.cancelButton.text")); // NOI18N
        cancelButton.setActionCommand(org.openide.util.NbBundle.getMessage(ReportGenerationPanel.class, "ReportGenerationPanel.cancelButton.actionCommand")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        reportPanel.setPreferredSize(new java.awt.Dimension(600, 400));

        javax.swing.GroupLayout reportPanelLayout = new javax.swing.GroupLayout(reportPanel);
        reportPanel.setLayout(reportPanelLayout);
        reportPanelLayout.setHorizontalGroup(
            reportPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        reportPanelLayout.setVerticalGroup(
            reportPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 344, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 546, Short.MAX_VALUE)
                        .addComponent(cancelButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(closeButton))
                    .addComponent(reportPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 680, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(reportPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 344, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(closeButton)
                    .addComponent(cancelButton))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
        close();
    }//GEN-LAST:event_closeButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        if (progressPanel.getStatus() == ReportStatus.QUEUING || progressPanel.getStatus() == ReportStatus.RUNNING) {
            int result = JOptionPane.showConfirmDialog(this, NbBundle.getMessage(this.getClass(),
                    "ReportGenerationPanel.confDlg.cancelReport.msg"),
                    NbBundle.getMessage(this.getClass(),
                            "ReportGenerationPanel.cancelButton.text"),
                    JOptionPane.YES_NO_OPTION);
            if (result == 0) {
                progressPanel.cancel();
            }
        }
    }//GEN-LAST:event_cancelButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JButton closeButton;
    private javax.swing.JPanel reportPanel;
    // End of variables declaration//GEN-END:variables

}
