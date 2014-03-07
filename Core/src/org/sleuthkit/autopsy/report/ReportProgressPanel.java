/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report;

import org.openide.util.NbBundle;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;

public class ReportProgressPanel extends javax.swing.JPanel {
    private ReportStatus STATUS;
    
    // Enum to represent if a report is waiting,
    // running, done, or has been canceled
    public enum ReportStatus {
        QUEUING,
        RUNNING,
        COMPLETE,
        CANCELED
    }

    /**
     * Creates new form ReportProgressPanel
     */
    public ReportProgressPanel(String reportName, String reportPath) {
        initComponents();
        customInit(reportName, reportPath);
    }
    
    private void customInit(String reportName, String reportPath) {
        reportProgressBar.setIndeterminate(true);
        reportProgressBar.setMaximum(100);

        reportLabel.setText(reportName);        
        processingLabel.setText(NbBundle.getMessage(this.getClass(), "ReportProgressPanel.progress.queuing"));
        STATUS = ReportStatus.QUEUING;
        
        if (reportPath != null) {
            pathLabel.setText("<html><u>" + shortenPath(reportPath) + "</u></html>");
            pathLabel.setToolTipText(reportPath);

        // Add the "link" effect to the pathLabel
            final String linkPath = reportPath;
            pathLabel.addMouseListener(new MouseListener() {

                @Override
                public void mouseClicked(MouseEvent e) {
                }

                @Override
                public void mousePressed(MouseEvent e) {
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    File file = new File(linkPath);
                    try {
                        Desktop.getDesktop().open(file);
                    } catch (IOException ex) {
                    } catch (IllegalArgumentException ex) {
                        try {
                            // try to open the parent path if the file doens't exist
                            Desktop.getDesktop().open(file.getParentFile());
                        } catch (IOException ex1) {
                        }
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    pathLabel.setForeground(Color.DARK_GRAY);
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    pathLabel.setForeground(Color.BLACK);
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }

            });
        }
        else {
            pathLabel.setText(NbBundle.getMessage(this.getClass(), "ReportProgressPanel.initPathLabel.noFile"));
        }
    }
    
    /**
     * Return a shortened version of the given path.
     */
    private String shortenPath(String path) {
        if (path.length() > 100) {
            path = path.substring(0, 10 + path.substring(10).indexOf(File.separator) + 1) + "..."
                    + path.substring((path.length() - 70) + path.substring(path.length() - 70).indexOf(File.separator));
        }
        return path;
    }
    
    /**
     * Return the current ReportStatus of this report.
     * 
     * @return ReportStatus status of this report
     */
    public ReportStatus getStatus() {
        return STATUS;
    }
    
    /**
     * Start the JProgressBar for this report.
     * 
     * Enables the cancelButton, updates the processingLabel, and changes this
     * report's ReportStatus.
     */
    public void start() {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                cancelButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/report/images/report_cancel.png")));
                cancelButton.setToolTipText(
                        NbBundle.getMessage(this.getClass(), "ReportProgressPanel.start.cancelButton.text"));
                processingLabel.setText(NbBundle.getMessage(this.getClass(), "ReportProgressPanel.start.progress.text"));
                STATUS = ReportStatus.RUNNING;
            }
        });
    }
    
    /**
     * Set the maximum progress for this report's JProgressBar.
     * 
     * @param max maximum progress for JProgressBar
     */
    public void setMaximumProgress(final int max) {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (STATUS != ReportStatus.CANCELED) {
                    reportProgressBar.setMaximum(max);
                }
            }
        });
    }
    
    /**
     * Increment the JProgressBar for this report by one unit.
     */
    public void increment() {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (STATUS != ReportStatus.CANCELED) {
                    reportProgressBar.setValue(reportProgressBar.getValue() + 1);
                }
            }
        });
    }
    
    /**
     * Set the value of the JProgressBar for this report.
     * 
     * @param value value to be set at 
     */
    public void setProgress(final int value) {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (STATUS != ReportStatus.CANCELED) {
                    reportProgressBar.setValue(value);
                }
            }
        });
    }
    
    /**
     * Changes the status of the JProgressBar to be determinate or indeterminate.
     * 
     * @param indeterminate sets the JProgressBar to be indeterminate if true, determinate otherwise
     */
    public void setIndeterminate(final boolean indeterminate) {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (STATUS != ReportStatus.CANCELED) {
                    reportProgressBar.setIndeterminate(indeterminate);
                }
            }
        });
    }
    
    /**
     * Change the text of this report's status label. The text given will
     * be the full text used.
     * e.g. updateStatusLabel("Now processing files...")
     *      sets the label to "Now processing files..."
     * 
     * @param status String to use as status
     */
    public void updateStatusLabel(final String status) {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (STATUS != ReportStatus.CANCELED) {
                    processingLabel.setText(status);
                }
            }
        });
    }
    
    /**
     * Declare the report completed.
     * This will fill the JProgressBar, update the cancelButton to completed,
     * and disallow any cancellation of this report.
     */
    public void complete() {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (STATUS != ReportStatus.CANCELED) {
                    STATUS = ReportStatus.COMPLETE;
                    processingLabel.setText(
                            NbBundle.getMessage(this.getClass(), "ReportProgressPanel.complete.processLbl.text"));
                    reportProgressBar.setValue(reportProgressBar.getMaximum());
                    cancelButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/report/images/report_complete.png")));
                    cancelButton.setToolTipText(
                            NbBundle.getMessage(this.getClass(), "ReportProgressPanel.complete.cancelButton.text"));
                }
            }
        });
        // Do something with the button to change the icon and make not clickable
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        reportProgressBar = new javax.swing.JProgressBar();
        cancelButton = new javax.swing.JButton();
        reportLabel = new javax.swing.JLabel();
        pathLabel = new javax.swing.JLabel();
        processingLabel = new javax.swing.JLabel();
        separationLabel = new javax.swing.JLabel();

        setMinimumSize(new java.awt.Dimension(486, 68));

        cancelButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/report/images/report_loading.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(ReportProgressPanel.class, "ReportProgressPanel.cancelButton.text")); // NOI18N
        cancelButton.setToolTipText(org.openide.util.NbBundle.getMessage(ReportProgressPanel.class, "ReportProgressPanel.cancelButton.toolTipText")); // NOI18N
        cancelButton.setBorder(null);
        cancelButton.setBorderPainted(false);
        cancelButton.setContentAreaFilled(false);
        cancelButton.setFocusPainted(false);
        cancelButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                cancelButtonMouseEntered(evt);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                cancelButtonMouseExited(evt);
            }
        });
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        reportLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(reportLabel, org.openide.util.NbBundle.getMessage(ReportProgressPanel.class, "ReportProgressPanel.reportLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(pathLabel, org.openide.util.NbBundle.getMessage(ReportProgressPanel.class, "ReportProgressPanel.pathLabel.text")); // NOI18N

        processingLabel.setFont(new java.awt.Font("Tahoma", 2, 10)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(processingLabel, org.openide.util.NbBundle.getMessage(ReportProgressPanel.class, "ReportProgressPanel.processingLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(separationLabel, org.openide.util.NbBundle.getMessage(ReportProgressPanel.class, "ReportProgressPanel.separationLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(processingLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(reportProgressBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(reportLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(separationLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pathLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 548, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(cancelButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(reportProgressBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(reportLabel)
                    .addComponent(pathLabel)
                    .addComponent(separationLabel))
                .addGap(0, 0, 0)
                .addComponent(processingLabel)
                .addContainerGap(20, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        cancel();
    }//GEN-LAST:event_cancelButtonActionPerformed

    /**
     * Cancels the current report, based on it's status. If the report is
     * complete or has already been completed, nothing happens.
     */
    void cancel() {
        switch(STATUS) {
            case COMPLETE:
                break;
            case CANCELED:
                break;
            default:
                STATUS = ReportStatus.CANCELED;
                cancelButton.setEnabled(false);
                cancelButton.setToolTipText(
                        NbBundle.getMessage(this.getClass(), "ReportProgressPanel.cancel.cancelButton.toolTipText"));
                reportProgressBar.setIndeterminate(false);
                reportProgressBar.setValue(0);
                reportProgressBar.setForeground(Color.RED);
                reportProgressBar.setBackground(Color.RED);
                processingLabel.setForeground(Color.RED);
                processingLabel.setText(NbBundle.getMessage(this.getClass(), "ReportProgressPanel.cancel.procLbl.text"));
                break;
        }
    }
    
    private void cancelButtonMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_cancelButtonMouseEntered
        switch(STATUS) {
            case COMPLETE:
                break;
            case CANCELED:
                break;
            default:
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                cancelButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/report/images/report_cancel_hover.png")));
                break;
        }
    }//GEN-LAST:event_cancelButtonMouseEntered

    private void cancelButtonMouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_cancelButtonMouseExited
        switch(STATUS) {
            case COMPLETE:
                break;
            case CANCELED:
                break;
            case QUEUING:
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                cancelButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/report/images/report_loading.png")));
                break;
            case RUNNING:
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                cancelButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/report/images/report_cancel.png")));
                break;
        }
    }//GEN-LAST:event_cancelButtonMouseExited

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel pathLabel;
    private javax.swing.JLabel processingLabel;
    private javax.swing.JLabel reportLabel;
    private javax.swing.JProgressBar reportProgressBar;
    private javax.swing.JLabel separationLabel;
    // End of variables declaration//GEN-END:variables
}
