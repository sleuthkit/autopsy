/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commandlineingest;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettingsPanel;
import java.nio.file.Paths;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Configuration panel for auto ingest settings.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class CommandLineIngestSettingsPanel extends javax.swing.JPanel {

    private final CommandLineIngestSettingsPanelController controller;
    private final JFileChooser fc = new JFileChooser();
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(CommandLineIngestSettingsPanel.class.getName());

    /**
     * Creates new form AutoIngestSettingsPanel
     *
     * @param theController Controller to notify of changes.
     */
    public CommandLineIngestSettingsPanel(CommandLineIngestSettingsPanelController theController) {
        controller = theController;
        initComponents();

        load(true);
        outputPathTextField.getDocument().addDocumentListener(new MyDocumentListener());
        jLabelInvalidResultsFolder.setText("");
    }

    private class MyDocumentListener implements DocumentListener {

        @Override
        public void changedUpdate(DocumentEvent e) {
            valid();
            controller.changed();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            valid();
            controller.changed();
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            valid();
            controller.changed();
        }
    };

    /**
     * Load mode from persistent storage.
     *
     * @param inStartup True if we're doing the initial population of the UI
     */
    final void load(boolean inStartup) {

        String results = org.sleuthkit.autopsy.commandlineingest.UserPreferences.getCommandLineModeResultsFolder();
        if (results != null) {
            outputPathTextField.setText(results);
        } else {
            outputPathTextField.setText("");
        }

        valid();
    }

    /**
     * Save mode to persistent storage.
     */
    void store() {
        String resultsFolderPath = getNormalizedFolderPath(outputPathTextField.getText().trim());
        org.sleuthkit.autopsy.commandlineingest.UserPreferences.setCommandLineModeResultsFolder(resultsFolderPath);
    }

    /**
     * Validate current panel settings.
     */
    boolean valid() {

        if (validateResultsPath()) {
            return true;
        }
        return false;
    }

    /**
     * Normalizes a path to make sure there are no "space" characters at the end
     *
     * @param path Path to a directory
     *
     * @return Path without "space" characters at the end
     */
    String normalizePath(String path) {

        while (path.length() > 0) {
            if (path.charAt(path.length() - 1) == ' ') {
                path = path.substring(0, path.length() - 1);
            } else {
                break;
            }
        }
        return path;
    }

    /**
     * Validates that a path is valid and points to a folder.
     *
     * @param path A path to be validated
     *
     * @return boolean returns true if valid and points to a folder, false
     *         otherwise
     */
    boolean isFolderPathValid(String path) {
        try {
            File file = new File(normalizePath(path));

            // check if it's a symbolic link
            if (Files.isSymbolicLink(file.toPath())) {
                return true;
            }

            // local folder
            if (file.exists() && file.isDirectory()) {
                return true;
            }
        } catch (Exception ex) {
            // Files.isSymbolicLink (and other "files" methods) throw exceptions on seemingly innocent inputs.
            // For example, it will throw an exception when either " " is last character in path or
            // a path starting with ":". 
            // We can just ignore these exceptions as they occur in process of user typing in the path.
            return false;
        }
        return false;
    }

    /**
     * Returns a path that was normalized by file system.
     *
     * @param path A path to be normalized. Normalization occurs inside a call
     *             to new File().
     *
     * @return String returns normalized OS path
     */
    String getNormalizedFolderPath(String path) {
        // removes "/", "\", and " " characters at the end of path string.
        // normalizePath() removes spaces at the end of path and a call to "new File()" 
        // internally formats the path string to remove "/" and "\" characters at the end of path.
        File file = new File(normalizePath(path));
        return file.getPath();
    }

    /**
     * Validate results path. Display warnings if invalid.
     */
    boolean validateResultsPath() {

        String outputPath = outputPathTextField.getText().trim();

        if (outputPath.isEmpty()) {
            jLabelInvalidResultsFolder.setVisible(true);
            jLabelInvalidResultsFolder.setText(NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.ResultsDirectoryUnspecified"));
            return false;
        }

        if (!isFolderPathValid(outputPath)) {
            jLabelInvalidResultsFolder.setVisible(true);
            jLabelInvalidResultsFolder.setText(NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.PathInvalid"));
            return false;
        }

        if (false == permissionsAppropriate(outputPath)) {
            jLabelInvalidResultsFolder.setVisible(true);
            jLabelInvalidResultsFolder.setText(NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.CannotAccess")
                    + " " + outputPath + "   "
                    + NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.CheckPermissions"));
            return false;
        }

        jLabelInvalidResultsFolder.setVisible(false);
        return true;
    }

    private void displayIngestJobSettingsPanel() {
        this.getParent().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        IngestJobSettings ingestJobSettings = new IngestJobSettings(org.sleuthkit.autopsy.commandlineingest.UserPreferences.getCommandLineModeIngestModuleContextString());
        showWarnings(ingestJobSettings);
        IngestJobSettingsPanel ingestJobSettingsPanel = new IngestJobSettingsPanel(ingestJobSettings);

        add(ingestJobSettingsPanel, BorderLayout.PAGE_START);

        if (JOptionPane.showConfirmDialog(this, ingestJobSettingsPanel, "Ingest Module Configuration", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            // store the updated settings
            ingestJobSettings = ingestJobSettingsPanel.getSettings();
            ingestJobSettings.save();
            showWarnings(ingestJobSettings);
        }

        this.getParent().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    private static void showWarnings(IngestJobSettings ingestJobSettings) {
        List<String> warnings = ingestJobSettings.getWarnings();
        if (warnings.isEmpty() == false) {
            StringBuilder warningMessage = new StringBuilder();
            for (String warning : warnings) {
                warningMessage.append(warning).append("\n");
            }
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), warningMessage.toString());
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

        nodeScrollPane = new javax.swing.JScrollPane();
        nodePanel = new javax.swing.JPanel();
        bnEditIngestSettings = new javax.swing.JButton();
        browseOutputFolderButton = new javax.swing.JButton();
        outputPathTextField = new javax.swing.JTextField();
        jLabelInvalidResultsFolder = new javax.swing.JLabel();
        jLabelSelectOutputFolder = new javax.swing.JLabel();

        nodeScrollPane.setMinimumSize(new java.awt.Dimension(0, 0));

        nodePanel.setMinimumSize(new java.awt.Dimension(100, 100));

        org.openide.awt.Mnemonics.setLocalizedText(bnEditIngestSettings, org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.bnEditIngestSettings.text")); // NOI18N
        bnEditIngestSettings.setToolTipText(org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.bnEditIngestSettings.toolTipText")); // NOI18N
        bnEditIngestSettings.setActionCommand(org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.bnEditIngestSettings.text")); // NOI18N
        bnEditIngestSettings.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnEditIngestSettingsActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(browseOutputFolderButton, org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.browseOutputFolderButton.text")); // NOI18N
        browseOutputFolderButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseOutputFolderButtonActionPerformed(evt);
            }
        });

        outputPathTextField.setText(org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.outputPathTextField.text")); // NOI18N
        outputPathTextField.setToolTipText(org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.outputPathTextField.toolTipText")); // NOI18N

        jLabelInvalidResultsFolder.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(jLabelInvalidResultsFolder, org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.jLabelInvalidResultsFolder.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabelSelectOutputFolder, org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.jLabelSelectOutputFolder.text")); // NOI18N
        jLabelSelectOutputFolder.setVerticalAlignment(javax.swing.SwingConstants.BOTTOM);

        javax.swing.GroupLayout nodePanelLayout = new javax.swing.GroupLayout(nodePanel);
        nodePanel.setLayout(nodePanelLayout);
        nodePanelLayout.setHorizontalGroup(
            nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nodePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(nodePanelLayout.createSequentialGroup()
                        .addComponent(outputPathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 630, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(browseOutputFolderButton))
                    .addComponent(bnEditIngestSettings, javax.swing.GroupLayout.PREFERRED_SIZE, 155, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(nodePanelLayout.createSequentialGroup()
                        .addComponent(jLabelSelectOutputFolder)
                        .addGap(18, 18, 18)
                        .addComponent(jLabelInvalidResultsFolder, javax.swing.GroupLayout.PREFERRED_SIZE, 544, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(355, Short.MAX_VALUE))
        );
        nodePanelLayout.setVerticalGroup(
            nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nodePanelLayout.createSequentialGroup()
                .addGap(40, 40, 40)
                .addGroup(nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelSelectOutputFolder, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelInvalidResultsFolder))
                .addGap(1, 1, 1)
                .addGroup(nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(browseOutputFolderButton)
                    .addComponent(outputPathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(25, 25, 25)
                .addComponent(bnEditIngestSettings)
                .addContainerGap(389, Short.MAX_VALUE))
        );

        browseOutputFolderButton.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.browseOutputFolderButton.text")); // NOI18N
        jLabelInvalidResultsFolder.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.jLabelInvalidResultsFolder.text")); // NOI18N
        jLabelSelectOutputFolder.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(CommandLineIngestSettingsPanel.class, "CommandLineIngestSettingsPanel.jLabelSelectOutputFolder.text")); // NOI18N

        nodeScrollPane.setViewportView(nodePanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(nodeScrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 864, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(nodeScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 421, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void browseOutputFolderButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseOutputFolderButtonActionPerformed
        String oldText = outputPathTextField.getText().trim();
        // set the current directory of the FileChooser if the oldText is valid
        File currentDir = new File(oldText);
        if (currentDir.exists()) {
            fc.setCurrentDirectory(currentDir);
        }

        fc.setDialogTitle("Select case output folder:");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int retval = fc.showOpenDialog(this);
        if (retval == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getPath();
            outputPathTextField.setText(path);
            valid();
            controller.changed();
        }
    }//GEN-LAST:event_browseOutputFolderButtonActionPerformed

    private void bnEditIngestSettingsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnEditIngestSettingsActionPerformed
        displayIngestJobSettingsPanel();
    }//GEN-LAST:event_bnEditIngestSettingsActionPerformed

    boolean permissionsAppropriate(String path) {
        return FileUtil.hasReadWriteAccess(Paths.get(path));
    }

    private void resetUI() {
        load(true);
        controller.changed();
    }

    void setEnabledState(boolean enabled) {
        bnEditIngestSettings.setEnabled(enabled);
        browseOutputFolderButton.setEnabled(enabled);
        jLabelInvalidResultsFolder.setEnabled(enabled);
        jLabelSelectOutputFolder.setEnabled(enabled);
        outputPathTextField.setEnabled(enabled);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnEditIngestSettings;
    private javax.swing.JButton browseOutputFolderButton;
    private javax.swing.JLabel jLabelInvalidResultsFolder;
    private javax.swing.JLabel jLabelSelectOutputFolder;
    private javax.swing.JPanel nodePanel;
    private javax.swing.JScrollPane nodeScrollPane;
    private javax.swing.JTextField outputPathTextField;
    // End of variables declaration//GEN-END:variables
}
