/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.ingest.IngestJobSettings.IngestType;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Directory;

/**
 *
 * A dialog box that allows a user to configure and execute analysis of one or
 * more data sources with ingest modules or analysis of the contents of a
 * directory with file-level ingest modules.
 */
public final class RunIngestModulesDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final String TITLE = NbBundle.getMessage(RunIngestModulesDialog.class, "IngestDialog.title.text");
    private final IngestType ingestType;
    private final List<Content> dataSources = new ArrayList<>();
    private IngestJobSettingsPanel ingestJobSettingsPanel;

    /**
     * Constructs a dialog box that allows a user to configure and execute
     * analysis of one or more data sources with ingest modules.
     *
     * @param frame       The dialog parent window.
     * @param title       The title for the dialog.
     * @param modal       True if the dialog should be modal, false otherwise.
     * @param dataSources The data sources to be analyzed.
     */
    public RunIngestModulesDialog(JFrame frame, String title, boolean modal, List<Content> dataSources) {
        super(frame, title, modal);
        this.dataSources.addAll(dataSources);
        this.ingestType = IngestType.ALL_MODULES;
    }

    /**
     * Constructs a dialog box that allows a user to configure and execute
     * analysis of one or more data sources with ingest modules.
     *
     * @param dataSources The data sources to be processed.
     */
    public RunIngestModulesDialog(List<Content> dataSources) {
        this((JFrame) WindowManager.getDefault().getMainWindow(), TITLE, true, dataSources);
    }

    /**
     * Constructs a dialog box that allows a user to configure and execute
     * analysis of the contents of a directory with file-level ingest modules.
     *
     * @param dir
     */
    public RunIngestModulesDialog(Directory dir) {
        this.dataSources.add(dir);
        this.ingestType = IngestType.FILES_ONLY;
    }

    /**
     * Displays this dialog.
     */
    public void display() {
        setLayout(new BorderLayout());

        /**
         * Center the dialog.
         */
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();;

        /**
         * Get the default or saved ingest job settings for this context and use
         * them to create and add an ingest job settings panel.
         */
        IngestJobSettings ingestJobSettings = new IngestJobSettings(RunIngestModulesDialog.class.getCanonicalName(), this.ingestType);
        RunIngestModulesDialog.showWarnings(ingestJobSettings);
        this.ingestJobSettingsPanel = new IngestJobSettingsPanel(ingestJobSettings, dataSources);
        setPreferredSize(this.ingestJobSettingsPanel.getPreferredSize());
        add(this.ingestJobSettingsPanel, BorderLayout.CENTER);

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
        buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        buttonPanel.add(startButton);
        buttonPanel.add(new javax.swing.Box.Filler(new Dimension(5, 10), new Dimension(5, 10), new Dimension(5, 10)));
        buttonPanel.add(closeButton);

        add(buttonPanel, BorderLayout.SOUTH);

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
        int width = this.getPreferredSize().width;
        int height = this.getPreferredSize().height;
        setLocation((screenDimension.width - width) / 2, (screenDimension.height - height) / 2);
        pack();
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
     *                       otherwise.
     */
    private void doButtonAction(boolean startIngestJob) {
        IngestJobSettings ingestJobSettings = this.ingestJobSettingsPanel.getSettings();
        ingestJobSettings.save();
        showWarnings(ingestJobSettings);
        if (startIngestJob) {
            IngestManager.getInstance().queueIngestJob(RunIngestModulesDialog.this.dataSources, ingestJobSettings);
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

    /**
     * Constructs a dialog box that allows a user to configure and execute
     * analysis of one or more data sources with ingest modules.
     *
     * @param frame The dialog parent window.
     * @param title The title for the dialog.
     * @param modal True if the dialog should be modal, false otherwise.
     *
     * @deprecated
     */
    @Deprecated
    public RunIngestModulesDialog(JFrame frame, String title, boolean modal) {
        super(frame, title, modal);
        this.ingestType = IngestType.ALL_MODULES;
    }

    /**
     * Constructs a dialog box that allows a user to configure and run an ingest
     * job on one or more data sources.
     *
     * @deprecated
     */
    @Deprecated
    public RunIngestModulesDialog() {
        this(new JFrame(TITLE), TITLE, true);
    }

    /**
     * Sets the data sources to be processed.
     *
     * @param dataSources The data sources.
     *
     * @deprecated
     */
    @Deprecated
    public void setDataSources(List<Content> dataSources) {
        this.dataSources.clear();
        this.dataSources.addAll(dataSources);
    }

}
