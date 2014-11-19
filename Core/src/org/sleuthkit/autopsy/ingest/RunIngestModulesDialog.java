/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013-2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.Content;

/**
 * Dialog box that allows a user to configure and run an ingest job on one or
 * more data sources.
 */
public final class RunIngestModulesDialog extends JDialog {

    private static final String TITLE = NbBundle.getMessage(RunIngestModulesDialog.class, "IngestDialog.title.text");
    private static Dimension DIMENSIONS = new Dimension(500, 300);
    private final List<Content> dataSources = new ArrayList<>();
    private IngestJobSettingsPanel ingestJobSettingsPanel;

    /**
     * Construct a dialog box that allows a user to configure and run an ingest
     * job on one or more data sources.
     *
     * @param frame The dialog parent window.
     * @param title The title for the dialog.
     * @param modal True if the dialog should be modal, false otherwise.
     * @param dataSources The data sources to be processed.
     */
    public RunIngestModulesDialog(JFrame frame, String title, boolean modal, List<Content> dataSources) {
        super(frame, title, modal);
        this.dataSources.addAll(dataSources);
    }

    /**
     * Construct a dialog box that allows a user to configure and run an ingest
     * job on one or more data sources.
     *
     * @param dataSources The data sources to be processed.
     */
    public RunIngestModulesDialog(List<Content> dataSources) {
        this(new JFrame(TITLE), TITLE, true, dataSources);
    }

    /**
     * Construct a dialog box that allows a user to configure and run an ingest
     * job on one or more data sources.
     *
     * @param frame The dialog parent window.
     * @param title The title for the dialog.
     * @param modal True if the dialog should be modal, false otherwise.
     * @deprecated
     */
    @Deprecated
    public RunIngestModulesDialog(JFrame frame, String title, boolean modal) {
        super(frame, title, modal);
    }

    /**
     * Construct a dialog box that allows a user to configure and run an ingest
     * job on one or more data sources.
     *
     * @deprecated
     */
    @Deprecated
    public RunIngestModulesDialog() {
        this(new JFrame(TITLE), TITLE, true);
    }

    /**
     * Set the data sources to be processed.
     *
     * @param dataSources The data sources.
     * @deprecated
     */
    @Deprecated
    public void setDataSources(List<Content> dataSources) {
        this.dataSources.clear();
        this.dataSources.addAll(dataSources);
    }

    /**
     * Displays this dialog.
     */
    public void display() {
        setLayout(new BorderLayout());

        /**
         * Center the dialog.
         */
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(DIMENSIONS);
        int width = this.getSize().width;
        int height = this.getSize().height;
        setLocation((screenDimension.width - width) / 2, (screenDimension.height - height) / 2);

        /**
         * Get the default or saved ingest job settings for this context and use
         * them to create and add an ingest job settings panel.
         */
        IngestJobSettings ingestJobSettings = new IngestJobSettings(RunIngestModulesDialog.class.getCanonicalName());
        this.showWarnings(ingestJobSettings);
        this.ingestJobSettingsPanel = new IngestJobSettingsPanel(ingestJobSettings);
        add(this.ingestJobSettingsPanel, BorderLayout.PAGE_START);

        // Add a start ingest button.
        JButton startButton = new JButton(NbBundle.getMessage(this.getClass(), "IngestDialog.startButton.title"));
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doButtonAction(true);
            }
        });

        // Add a close button.
        JButton closeButton = new JButton(NbBundle.getMessage(this.getClass(), "IngestDialog.closeButton.title"));
        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doButtonAction(false);
            }
        });

        // Put the buttons in their own panel, under the settings panel.
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
        buttonPanel.add(new javax.swing.Box.Filler(new Dimension(10, 10), new Dimension(10, 10), new Dimension(10, 10)));
        buttonPanel.add(startButton);
        buttonPanel.add(new javax.swing.Box.Filler(new Dimension(10, 10), new Dimension(10, 10), new Dimension(10, 10)));
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.LINE_START);

        /**
         * Add a handler for when the dialog window is closed directly,
         * bypassing the buttons.
         */
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                doButtonAction(false);
            }
        });

        /**
         * Show the dialog.
         */
        pack();
        setResizable(false);
        setVisible(true);
    }

    /**
     * Closes this dialog.
     */
    @Deprecated
    public void close() {
        setVisible(false);
        dispose();
    }

    /**
     * Saves the ingest job settings, optionally starts an ingest job for each
     * data source, then closes the dialog
     *
     * @param startIngestJob True if ingest job(s) should be started, false
     * otherwise.
     */
    private void doButtonAction(boolean startIngestJob) {
        IngestJobSettings ingestJobSettings = this.ingestJobSettingsPanel.getSettings();
        ingestJobSettings.save();
        showWarnings(ingestJobSettings);

        if (startIngestJob) {
            IngestManager ingestManager = IngestManager.getInstance();
            for (Content dataSource : RunIngestModulesDialog.this.dataSources) {
                ingestManager.startIngestJob(dataSource, ingestJobSettings, true);
            }
        }

        setVisible(false);
        dispose();
    }

    private static void showWarnings(IngestJobSettings ingestJobSettings) {
        List<String> warnings = ingestJobSettings.getWarnings();
        if (warnings.isEmpty() == false) {
            StringBuilder warningMessage = new StringBuilder();
            for (String warning : warnings) {
                warningMessage.append(warning).append("\n");
            }
            JOptionPane.showMessageDialog(null, warningMessage.toString());
        }
    }
}
