/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2019 Basis Technology Corp.
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
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * A panel used by a report generation module to show progress.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class ReportProgressPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ReportProgressPanel.class.getName());
    private static final Color GREEN = new Color(50, 205, 50);
    private static final Color RED = new Color(178, 34, 34);
    private volatile ReportStatus status;

    /**
     * Used by a report generation module to communicate report generation
     * status to this panel and its listeners.
     */
    @NbBundle.Messages({
        "ReportProgressPanel.progress.queuing=Queuing...",
        "ReportProgressPanel.progress.running=Running...",
        "ReportProgressPanel.progress.complete=Complete",
        "ReportProgressPanel.progress.canceled=Canceled",
        "ReportProgressPanel.progress.error=Error",})
    public enum ReportStatus {

        QUEUING(Bundle.ReportProgressPanel_progress_queuing()),
        RUNNING(Bundle.ReportProgressPanel_progress_running()),
        COMPLETE(Bundle.ReportProgressPanel_progress_complete()),
        CANCELED(Bundle.ReportProgressPanel_progress_canceled()),
        ERROR(Bundle.ReportProgressPanel_progress_error());

        private final String displayName;

        ReportStatus(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Gets the display name of the report status.
         *
         * @return The display name.
         */
        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Constructs a panel used by report generation module to show progress.
     */
    public ReportProgressPanel() {
        initComponents();
        reportProgressBar.setIndeterminate(true);
        reportProgressBar.setMaximum(100);
        statusMessageLabel.setText(Bundle.ReportProgressPanel_progress_queuing());
        status = ReportStatus.QUEUING;
        reportLabel.setText("");
        pathLabel.setText(""); //NON-NLS
    }

    /**
     * Sets label text.
     *
     * @param reportName The name of the report being generated.
     * @param reportPath The path to the report file.
     */
    public final void setLabels(String reportName, String reportPath) {
        reportLabel.setText(reportName);
        if (null != reportPath) {
            pathLabel.setText("<html><u>" + shortenPath(reportPath) + "</u></html>"); //NON-NLS
            pathLabel.setToolTipText(reportPath);
            String linkPath = reportPath;
            pathLabel.addMouseListener(new MouseListener() {

                @Override
                public void mouseClicked(MouseEvent mouseEvent) {
                    /*
                     * Do nothing for this event.
                     */
                }

                @Override
                public void mousePressed(MouseEvent mouseEvent) {
                    /*
                     * Do nothing for this event.
                     */
                }

                @Override
                public void mouseReleased(MouseEvent mouseEvent) {
                    File file = new File(linkPath);
                    try {
                        Desktop.getDesktop().open(file);
                    } catch (IOException ioex) {
                        logger.log(Level.SEVERE, "Error opening report file", ioex);
                    } catch (IllegalArgumentException iaEx) {
                        logger.log(Level.SEVERE, "Error opening report file", iaEx);
                        try {
                            Desktop.getDesktop().open(file.getParentFile());
                        } catch (IOException ioEx2) {
                            logger.log(Level.SEVERE, "Error opening report file parent", ioEx2);
                        }
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e3) {
                    pathLabel.setForeground(Color.DARK_GRAY);
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }

                @Override
                public void mouseExited(MouseEvent e4) {
                    pathLabel.setForeground(Color.BLACK);
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            });
        } else {
            pathLabel.setText(NbBundle.getMessage(this.getClass(), "ReportProgressPanel.initPathLabel.noFile"));
        }
    }

    /**
     * Gets the current status of the generation of the report.
     *
     * @return The report generation status as a ReportStatus enum.
     */
    public ReportStatus getStatus() {
        return status;
    }

    /**
     * Sets the current status of the generation of the report.
     *
     * @param status The current status.
     */
    protected void setStatus(ReportStatus status) {
        this.status = status;
    }

    /**
     * Starts the progress bar component of this panel.
     */
    public void start() {
        EventQueue.invokeLater(() -> {
            statusMessageLabel.setText(NbBundle.getMessage(this.getClass(), "ReportProgressPanel.start.progress.text"));
            status = ReportStatus.RUNNING;
        });
    }

    /**
     * Sets the maximum value of the progress bar component of this panel.
     *
     * @param max The maximum value.
     */
    public void setMaximumProgress(int max) {
        EventQueue.invokeLater(() -> {
            if (status != ReportStatus.CANCELED) {
                reportProgressBar.setMaximum(max);
            }
        });
    }

    /**
     * Increments the current value of the progress bar component of this panel
     * by one unit.
     */
    public void increment() {
        EventQueue.invokeLater(() -> {
            if (status != ReportStatus.CANCELED) {
                reportProgressBar.setValue(reportProgressBar.getValue() + 1);
            }
        });
    }

    /**
     * Sets the current value of the progress bar component of this panel.
     *
     * @param value The value to be set.
     */
    public void setProgress(int value) {
        EventQueue.invokeLater(() -> {
            if (status != ReportStatus.CANCELED) {
                reportProgressBar.setValue(value);
            }
        });
    }

    /**
     * Changes the the progress bar component of this panel to be determinate or
     * indeterminate.
     *
     * @param indeterminate True if the progress bar should be set to
     *                      indeterminate.
     */
    public void setIndeterminate(boolean indeterminate) {
        EventQueue.invokeLater(() -> {
            if (status != ReportStatus.CANCELED) {
                reportProgressBar.setIndeterminate(indeterminate);
            }
        });
    }

    /**
     * Changes the status message label component of this panel to show a given
     * processing status message. For example, updateStatusLabel("Now processing
     * files...") sets the label text to "Now processing files..."
     *
     * @param statusMessage String to use as label text.
     */
    public void updateStatusLabel(String statusMessage) {
        EventQueue.invokeLater(() -> {
            if (status != ReportStatus.CANCELED) {
                statusMessageLabel.setText(statusMessage);
            }
        });
    }

    /**
     * Makes the components of this panel indicate the final status of
     * generation of the report.
     *
     * @param reportStatus The final status, must be COMPLETE or ERROR.
     */
    public void complete(ReportStatus reportStatus) {

        switch (reportStatus) {
            case COMPLETE:
                complete(reportStatus, NbBundle.getMessage(this.getClass(), "ReportProgressPanel.complete.processLbl.text"));
                break;
            case ERROR:
                complete(reportStatus, NbBundle.getMessage(this.getClass(), "ReportProgressPanel.complete.processLb2.text"));
                break;
            default:
                complete(reportStatus, "");
                break;
        }
    }

    /**
     * Makes the components of this panel indicate the final status of
     * generation of the report.
     *
     * @param reportStatus  The final status, must be COMPLETE or ERROR.
     * @param statusMessage String to use as label or error text.
     */
    public void complete(ReportStatus reportStatus, String statusMessage) {
        EventQueue.invokeLater(() -> {
            reportProgressBar.setIndeterminate(false);
            if (status != ReportStatus.CANCELED) {
                switch (reportStatus) {
                    case COMPLETE: {
                        ReportStatus oldValue = status;
                        status = ReportStatus.COMPLETE;
                        statusMessageLabel.setForeground(Color.BLACK);
                        statusMessageLabel.setText(statusMessage);
                        reportProgressBar.setValue(reportProgressBar.getMaximum());
                        reportProgressBar.setStringPainted(true);
                        reportProgressBar.setForeground(GREEN);
                        reportProgressBar.setString(ReportStatus.COMPLETE.getDisplayName());
                        firePropertyChange(ReportStatus.COMPLETE.toString(), oldValue, status);
                        break;
                    }
                    case ERROR: {
                        ReportStatus oldValue = status;
                        status = ReportStatus.ERROR;
                        statusMessageLabel.setForeground(RED);
                        statusMessageLabel.setText(statusMessage);
                        reportProgressBar.setValue(reportProgressBar.getMaximum());
                        reportProgressBar.setStringPainted(true);
                        reportProgressBar.setForeground(RED);
                        reportProgressBar.setString(ReportStatus.ERROR.getDisplayName());
                        firePropertyChange(ReportStatus.COMPLETE.toString(), oldValue, status);
                        break;
                    }
                    default: {
                        break;
                    }
                }
            }
        });
    }

    /**
     * Makes the components of this panel indicate generation of the report was
     * cancelled.
     */
    public void cancel() {
        switch (status) {
            case COMPLETE:
                break;
            case CANCELED:
                break;
            case ERROR:
                break;
            default:
                ReportStatus oldValue = status;
                status = ReportStatus.CANCELED;
                reportProgressBar.setIndeterminate(false);
                reportProgressBar.setValue(0);
                reportProgressBar.setStringPainted(true);
                reportProgressBar.setForeground(RED); // Red
                reportProgressBar.setString(ReportStatus.CANCELED.getDisplayName());
                firePropertyChange(ReportStatus.CANCELED.toString(), oldValue, status);
                statusMessageLabel.setForeground(RED);
                statusMessageLabel.setText(NbBundle.getMessage(this.getClass(), "ReportProgressPanel.cancel.procLbl.text"));
                break;
        }
    }

    /**
     * Gets a shortened version of a file path.
     *
     * @param path The path to shorten.
     *
     * @return The shortened path.
     */
    private String shortenPath(String path) {
        if (path.length() > 100) {
            return path.substring(0, 10 + path.substring(10).indexOf(File.separator) + 1) + "..."
                    + path.substring((path.length() - 70) + path.substring(path.length() - 70).indexOf(File.separator));
        } else {
            return path;
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

        reportProgressBar = new javax.swing.JProgressBar();
        reportLabel = new javax.swing.JLabel();
        pathLabel = new javax.swing.JLabel();
        separationLabel = new javax.swing.JLabel();
        statusMessageLabel = new javax.swing.JLabel();

        setMinimumSize(new java.awt.Dimension(486, 68));

        reportLabel.setFont(reportLabel.getFont().deriveFont(reportLabel.getFont().getStyle() | java.awt.Font.BOLD));
        org.openide.awt.Mnemonics.setLocalizedText(reportLabel, org.openide.util.NbBundle.getMessage(ReportProgressPanel.class, "ReportProgressPanel.reportLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(pathLabel, org.openide.util.NbBundle.getMessage(ReportProgressPanel.class, "ReportProgressPanel.pathLabel.text")); // NOI18N
        pathLabel.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        org.openide.awt.Mnemonics.setLocalizedText(separationLabel, org.openide.util.NbBundle.getMessage(ReportProgressPanel.class, "ReportProgressPanel.separationLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(statusMessageLabel, org.openide.util.NbBundle.getMessage(ReportProgressPanel.class, "ReportProgressPanel.statusMessageLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(statusMessageLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(reportProgressBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
                .addComponent(reportProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(reportLabel)
                    .addComponent(pathLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(separationLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(statusMessageLabel)
                .addGap(13, 13, 13))
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel pathLabel;
    private javax.swing.JLabel reportLabel;
    private javax.swing.JProgressBar reportProgressBar;
    private javax.swing.JLabel separationLabel;
    private javax.swing.JLabel statusMessageLabel;
    // End of variables declaration//GEN-END:variables

    /**
     * Constructs a panel used by a report generation module to show progress.
     *
     * @param reportName The report name.
     * @param reportPath The report path.
     *
     * @deprecated Use {@link #ReportProgressPanel()} and {@link #setLabels()}
     * instead.
     */
    @Deprecated
    public ReportProgressPanel(String reportName, String reportPath) {
        this();
        setLabels(reportName, reportPath);
    }

    /**
     * Makes the components of this panel indicate the generation of the report
     * is completed.
     *
     * @deprecated Use {@link #complete(ReportStatus)}
     */
    @Deprecated
    public void complete() {
        complete(ReportStatus.COMPLETE);
    }

}
