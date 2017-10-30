/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.configuration;

import java.awt.BorderLayout;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettingsPanel;
import java.awt.Dimension;
import java.nio.file.Paths;
import org.openide.util.ImageUtilities;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.experimental.autoingest.FileExporterSettingsPanel;

/**
 *
 */
public class AutoIngestSettingsPanel extends javax.swing.JPanel {

    private final AutoIngestSettingsPanelController controller;
    private final JFileChooser fc = new JFileChooser();
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(AutoIngestSettingsPanel.class.getName());
    private Integer oldIngestThreads;
    private static final String MULTI_USER_SETTINGS_MUST_BE_ENABLED = NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.validationErrMsg.MUdisabled");

    enum OptionsUiMode {

        STANDALONE, AIM, DOWNLOADING_CONFIGURATION
    };

    /**
     * Creates new form AutoIngestSettingsPanel
     *
     * @param theController Controller to notify of changes.
     */
    public AutoIngestSettingsPanel(AutoIngestSettingsPanelController theController) {
        controller = theController;
        initComponents();

        cbJoinAutoIngestCluster.setVisible(true);
        load(true);
        sharedSettingsTextField.getDocument().addDocumentListener(new MyDocumentListener());
        inputPathTextField.getDocument().addDocumentListener(new MyDocumentListener());
        outputPathTextField.getDocument().addDocumentListener(new MyDocumentListener());

        jLabelInvalidImageFolder.setText("");
        jLabelInvalidResultsFolder.setText("");
        sharedSettingsErrorTextField.setText("");
        jLabelTaskDescription.setText("");
        configButtonErrorTextField.setText("");

        pbTaskInProgress.setEnabled(false);
        jLabelCurrentTask.setEnabled(false);
        jLabelTaskDescription.setEnabled(false);

        this.oldIngestThreads = UserPreferences.numberOfFileIngestThreads();
    }

    private class MyDocumentListener implements DocumentListener {

        @Override
        public void changedUpdate(DocumentEvent e) {
            validateSettings();
            controller.changed();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            validateSettings();
            controller.changed();
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            validateSettings();
            controller.changed();
        }
    };

    /**
     * Load mode from persistent storage.
     *
     * @param inStartup True if we're doing the initial population of the UI
     */
    final void load(boolean inStartup) {

        // multi user mode must be enabled
        if (!UserPreferences.getIsMultiUserModeEnabled()) {
            tbOops.setText(MULTI_USER_SETTINGS_MUST_BE_ENABLED);
        } else {
            tbOops.setText("");
        }
        cbJoinAutoIngestCluster.setSelected(AutoIngestUserPreferences.getJoinAutoModeCluster());
        cbJoinAutoIngestCluster.setEnabled(UserPreferences.getIsMultiUserModeEnabled());

        if (inStartup) {
            UserPreferences.SelectedMode storedMode = UserPreferences.getMode();
            inputPathTextField.requestFocusInWindow();
            if (storedMode == UserPreferences.SelectedMode.AUTOINGEST) {
                enableOptionsBasedOnMode(OptionsUiMode.AIM);
            } else if (storedMode != null) {
                enableOptionsBasedOnMode(OptionsUiMode.STANDALONE);
            }
        }

        String images = AutoIngestUserPreferences.getAutoModeImageFolder();
        if (images != null) {
            inputPathTextField.setText(images);
        } else {
            inputPathTextField.setText("");
        }

        String results = AutoIngestUserPreferences.getAutoModeResultsFolder();
        if (results != null) {
            outputPathTextField.setText(results);
        } else {
            outputPathTextField.setText("");
        }

        if (inStartup) {
            sharedConfigCheckbox.setSelected(AutoIngestUserPreferences.getSharedConfigEnabled());
            String sharedSettingsFolder = AutoIngestUserPreferences.getSharedConfigFolder();
            if (sharedSettingsFolder != null) {
                sharedSettingsTextField.setText(sharedSettingsFolder);
            } else {
                String folder = getDefaultSharedFolder();
                sharedSettingsTextField.setText(folder);
            }

            masterNodeCheckBox.setSelected(AutoIngestUserPreferences.getSharedConfigMaster());
            setEnabledStateForSharedConfiguration();
        }

        validateSettings();
        enableOptionsBasedOnMode(getModeFromRadioButtons());
    }

    /**
     * Get the default location for the shared configuration folder. Currently
     * this is a subfolder of the shared images folder.
     *
     * @return The default subfolder name, or an empty string if the base folder
     *         is not set
     */
    private String getDefaultSharedFolder() {

        String images = inputPathTextField.getText().trim();
        if (images == null || images.isEmpty()) {
            return "";
        }
        File sharedFolder = new File(images, "sharedConfiguration");
        if (!sharedFolder.exists()) {
            try {
                sharedFolder.mkdir();
                return sharedFolder.getAbsolutePath();
            } catch (Exception ex) {
                sharedSettingsErrorTextField.setText(NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.ErrorSettingDefaultFolder"));
                return "";
            }
        }
        return sharedFolder.getAbsolutePath();
    }

    /**
     * Save mode to persistent storage.
     */
    void store() {
        boolean needsRestart = (cbJoinAutoIngestCluster.isSelected() != AutoIngestUserPreferences.getJoinAutoModeCluster());
        
        AutoIngestUserPreferences.setJoinAutoModeCluster(cbJoinAutoIngestCluster.isSelected());
        if (!cbJoinAutoIngestCluster.isSelected()) {
            UserPreferences.setMode(UserPreferences.SelectedMode.STANDALONE);
        } else {
            UserPreferences.setMode(UserPreferences.SelectedMode.AUTOINGEST);
            String imageFolderPath = getNormalizedFolderPath(inputPathTextField.getText().trim());
            AutoIngestUserPreferences.setAutoModeImageFolder(imageFolderPath);
            String resultsFolderPath = getNormalizedFolderPath(outputPathTextField.getText().trim());
            AutoIngestUserPreferences.setAutoModeResultsFolder(resultsFolderPath);
            AutoIngestUserPreferences.setSharedConfigEnabled(sharedConfigCheckbox.isSelected());
            if (sharedConfigCheckbox.isSelected()) {
                String globalSettingsPath = getNormalizedFolderPath(sharedSettingsTextField.getText().trim());
                AutoIngestUserPreferences.setSharedConfigFolder(globalSettingsPath);
                AutoIngestUserPreferences.setSharedConfigMaster(masterNodeCheckBox.isSelected());
            }
        }
        
        if (needsRestart) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null,
                        NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.MustRestart"),
                        NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.restartRequiredLabel.text"),
                        JOptionPane.WARNING_MESSAGE);
            });
        }

    }

    void validateSettings() {
        if (valid()) {
            if (validSharedConfigSettings()) {
                downloadButton.setEnabled(true);
                if (masterNodeCheckBox.isEnabled() && masterNodeCheckBox.isSelected()) {
                    uploadButton.setEnabled(true);
                } else {
                    uploadButton.setEnabled(false);
                }
            } else {
                downloadButton.setEnabled(false);
                uploadButton.setEnabled(false);
            }
            displaySharedConfigButtonText();
        } else {
            uploadButton.setEnabled(false);
            if (validSharedConfigSettings()) {
                downloadButton.setEnabled(true);
            } else {
                downloadButton.setEnabled(false);
            }
            displaySharedConfigButtonText();
        }
    }

    /**
     * Validate current panel settings.
     */
    boolean valid() {

        if (!cbJoinAutoIngestCluster.isSelected()) {  //hide the invalid field warnings when in stand alone mode
            jLabelInvalidImageFolder.setVisible(false);
            jLabelInvalidResultsFolder.setVisible(false);
            sharedSettingsErrorTextField.setVisible(false);
            configButtonErrorTextField.setText("");
            return true;
        }
        boolean isValidNodePanel = true;

        switch (getModeFromRadioButtons()) {
            case AIM:
                if (!validateImagePath()) {
                    isValidNodePanel = false;
                }
                if (!validateResultsPath()) {
                    isValidNodePanel = false;
                }
                if (!validateSharedSettingsPath()) {
                    isValidNodePanel = false;
                    configButtonErrorTextField.setText("Shared configuration folder is invalid");
                }
                break;
            case STANDALONE:
                break;
            default:
                break;
        }

        if (sharedConfigCheckbox.isEnabled() && sharedConfigCheckbox.isSelected() && !validSharedConfigSettings()) {
            isValidNodePanel = false;
        }
        return isValidNodePanel;
    }

    /**
     * Check everything that is needed to enable the upload or download config
     * buttons is set (except the master node checkbox for upload).
     *
     * @return
     */
    boolean validSharedConfigSettings() {
        // Check for:
        //  - shared config checkbox enabled and checked
        //  - valid shared config folder entered
        //  - mulit-user settings enabled
        return (sharedConfigCheckbox.isEnabled() && sharedConfigCheckbox.isSelected() && UserPreferences.getIsMultiUserModeEnabled());
    }

    /**
     *
     */
    void displaySharedConfigButtonText() {
        if (sharedConfigCheckbox.isEnabled() && sharedConfigCheckbox.isSelected()) {
            if (!UserPreferences.getIsMultiUserModeEnabled()) {
                configButtonErrorTextField.setText("Multi-user cases must be enabled");
            } else {
                configButtonErrorTextField.setText("");
            }
        } else {
            configButtonErrorTextField.setText("");
        }
    }

    /**
     * Determines whether settings requiring a restart have changed, and also
     * updates the UI.
     *
     * @return true if something has changed that will require a reset, false
     *         otherwise
     */
    boolean isResetNeeded() {

        boolean resetNeeded = false;

        // Save the current values from the UI
        String oldInputPath = inputPathTextField.getText().trim();
        String oldOutputPath = outputPathTextField.getText().trim();

        // Refresh UI
        load(false);

        if (!oldInputPath.equals(inputPathTextField.getText().trim())) {
            resetNeeded = true;
        }
        if (!oldOutputPath.equals(outputPathTextField.getText().trim())) {
            resetNeeded = true;
        }
        if (!this.oldIngestThreads.equals(UserPreferences.numberOfFileIngestThreads())) {
            resetNeeded = true;
        }
        return resetNeeded;
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
     * Validate image path. Display warnings if invalid.
     */
    boolean validateImagePath() {

        String inputPath = inputPathTextField.getText().trim();

        if (inputPath.isEmpty()) {
            jLabelInvalidImageFolder.setVisible(true);
            jLabelInvalidImageFolder.setText(NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.ImageDirectoryUnspecified"));
            return false;
        }

        if (!isFolderPathValid(inputPath)) {
            jLabelInvalidImageFolder.setVisible(true);
            jLabelInvalidImageFolder.setText(NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.PathInvalid"));
            return false;
        }

        if (false == permissionsAppropriate(inputPath)) {
            jLabelInvalidImageFolder.setVisible(true);
            jLabelInvalidImageFolder.setText(NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.CannotAccess")
                    + " " + inputPath + "   "
                    + NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.CheckPermissions"));
            return false;
        }

        jLabelInvalidImageFolder.setVisible(false);
        return true;
    }

    /**
     * Validate results path. Display warnings if invalid.
     */
    boolean validateResultsPath() {

        String outputPath = outputPathTextField.getText().trim();

        if (outputPath.isEmpty()) {
            jLabelInvalidResultsFolder.setVisible(true);
            jLabelInvalidResultsFolder.setText(NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.ResultsDirectoryUnspecified"));
            return false;
        }

        if (!isFolderPathValid(outputPath)) {
            jLabelInvalidResultsFolder.setVisible(true);
            jLabelInvalidResultsFolder.setText(NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.PathInvalid"));
            return false;
        }

        if (false == permissionsAppropriate(outputPath)) {
            jLabelInvalidResultsFolder.setVisible(true);
            jLabelInvalidResultsFolder.setText(NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.CannotAccess")
                    + " " + outputPath + "   "
                    + NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.CheckPermissions"));
            return false;
        }

        jLabelInvalidResultsFolder.setVisible(false);
        return true;
    }

    /**
     * Validate shared settings path. Display warnings if invalid.
     */
    boolean validateSharedSettingsPath() {
        String sharedSettingsPath = sharedSettingsTextField.getText().trim();

        // Automatically valid if shared settings aren't selected
        if (!sharedConfigCheckbox.isSelected()) {
            return true;
        }

        if (sharedSettingsPath.isEmpty()) {
            sharedSettingsErrorTextField.setVisible(true);
            sharedSettingsErrorTextField.setText(NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.EmptySettingsDirectory"));
            return false;
        }

        if (!isFolderPathValid(sharedSettingsPath)) {
            sharedSettingsErrorTextField.setVisible(true);
            sharedSettingsErrorTextField.setText(NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.PathInvalid"));
            return false;
        }

        if (false == permissionsAppropriate(sharedSettingsPath)) {
            sharedSettingsErrorTextField.setVisible(true);
            sharedSettingsErrorTextField.setText(NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.CannotAccess")
                    + " " + sharedSettingsPath + " "
                    + NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.CheckPermissions"));
            return false;
        }

        sharedSettingsErrorTextField.setText("");
        return true;
    }

    private void displayIngestJobSettingsPanel() {

        IngestJobSettings ingestJobSettings = new IngestJobSettings(AutoIngestUserPreferences.getAutoModeIngestModuleContextString());
        showWarnings(ingestJobSettings);
        IngestJobSettingsPanel ingestJobSettingsPanel = new IngestJobSettingsPanel(ingestJobSettings);

        add(ingestJobSettingsPanel, BorderLayout.PAGE_START);

        if (JOptionPane.showConfirmDialog(null, ingestJobSettingsPanel, "Ingest Module Configuration", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            // store the updated settings
            ingestJobSettings = ingestJobSettingsPanel.getSettings();
            ingestJobSettings.save();
            showWarnings(ingestJobSettings);
        }
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

    private void enableOptionsBasedOnMode(OptionsUiMode mode) {
        if (mode != OptionsUiMode.DOWNLOADING_CONFIGURATION) {
            boolean nonMasterSharedConfig = !masterNodeCheckBox.isSelected() && sharedConfigCheckbox.isSelected();
            jLabelSelectInputFolder.setEnabled(mode == OptionsUiMode.AIM && !nonMasterSharedConfig);
            inputPathTextField.setEnabled(mode == OptionsUiMode.AIM && !nonMasterSharedConfig);
            browseInputFolderButton.setEnabled(mode == OptionsUiMode.AIM && !nonMasterSharedConfig);

            jLabelSelectOutputFolder.setEnabled(mode == OptionsUiMode.AIM && !nonMasterSharedConfig);
            outputPathTextField.setEnabled(mode == OptionsUiMode.AIM && !nonMasterSharedConfig);
            browseOutputFolderButton.setEnabled(mode == OptionsUiMode.AIM && !nonMasterSharedConfig);

            bnEditIngestSettings.setEnabled(mode == OptionsUiMode.AIM && !nonMasterSharedConfig);
            bnAdvancedSettings.setEnabled(mode == OptionsUiMode.AIM && !nonMasterSharedConfig);
            bnLogging.setEnabled(mode == OptionsUiMode.AIM && !nonMasterSharedConfig);
            sharedConfigCheckbox.setEnabled(mode == OptionsUiMode.AIM);
            masterNodeCheckBox.setEnabled(mode == OptionsUiMode.AIM && sharedConfigCheckbox.isSelected());
            bnFileExport.setEnabled(mode == OptionsUiMode.AIM);
            sharedSettingsTextField.setEnabled(mode == OptionsUiMode.AIM && sharedConfigCheckbox.isSelected());
            downloadButton.setEnabled(mode == OptionsUiMode.AIM && sharedConfigCheckbox.isSelected());
            browseSharedSettingsButton.setEnabled(mode == OptionsUiMode.AIM && sharedConfigCheckbox.isSelected());
            uploadButton.setEnabled(mode == OptionsUiMode.AIM && sharedConfigCheckbox.isSelected() && masterNodeCheckBox.isSelected());
        } else {
            setEnabledState(false);
        }

    }

    private OptionsUiMode getModeFromRadioButtons() {
        if (!cbJoinAutoIngestCluster.isSelected()) {
            return OptionsUiMode.STANDALONE;
        } else {
            return OptionsUiMode.AIM;
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

        modeRadioButtons = new javax.swing.ButtonGroup();
        nodeScrollPane = new javax.swing.JScrollPane();
        nodePanel = new javax.swing.JPanel();
        cbJoinAutoIngestCluster = new javax.swing.JCheckBox();
        tbOops = new javax.swing.JTextField();
        bnEditIngestSettings = new javax.swing.JButton();
        bnAdvancedSettings = new javax.swing.JButton();
        bnFileExport = new javax.swing.JButton();
        bnLogging = new javax.swing.JButton();
        browseOutputFolderButton = new javax.swing.JButton();
        browseInputFolderButton = new javax.swing.JButton();
        inputPathTextField = new javax.swing.JTextField();
        outputPathTextField = new javax.swing.JTextField();
        jLabelInvalidResultsFolder = new javax.swing.JLabel();
        jLabelInvalidImageFolder = new javax.swing.JLabel();
        jLabelSelectInputFolder = new javax.swing.JLabel();
        jLabelSelectOutputFolder = new javax.swing.JLabel();
        sharedConfigCheckbox = new javax.swing.JCheckBox();
        sharedSettingsErrorTextField = new javax.swing.JTextField();
        sharedSettingsTextField = new javax.swing.JTextField();
        browseSharedSettingsButton = new javax.swing.JButton();
        downloadButton = new javax.swing.JButton();
        configButtonErrorTextField = new javax.swing.JTextField();
        pbTaskInProgress = new javax.swing.JProgressBar();
        jLabelTaskDescription = new javax.swing.JLabel();
        jLabelCurrentTask = new javax.swing.JLabel();
        uploadButton = new javax.swing.JButton();
        masterNodeCheckBox = new javax.swing.JCheckBox();

        nodeScrollPane.setMinimumSize(new java.awt.Dimension(0, 0));

        nodePanel.setMinimumSize(new java.awt.Dimension(100, 100));

        cbJoinAutoIngestCluster.setFont(cbJoinAutoIngestCluster.getFont().deriveFont(cbJoinAutoIngestCluster.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(cbJoinAutoIngestCluster, org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.cbJoinAutoIngestCluster.text")); // NOI18N
        cbJoinAutoIngestCluster.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cbJoinAutoIngestClusterActionPerformed(evt);
            }
        });

        tbOops.setEditable(false);
        tbOops.setFont(tbOops.getFont().deriveFont(tbOops.getFont().getStyle() | java.awt.Font.BOLD, 12));
        tbOops.setForeground(new java.awt.Color(255, 0, 0));
        tbOops.setText(org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.tbOops.text")); // NOI18N
        tbOops.setBorder(null);

        org.openide.awt.Mnemonics.setLocalizedText(bnEditIngestSettings, org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.bnEditIngestSettings.text")); // NOI18N
        bnEditIngestSettings.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.bnEditIngestSettings.toolTipText")); // NOI18N
        bnEditIngestSettings.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnEditIngestSettingsActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnAdvancedSettings, org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.bnAdvancedSettings.text")); // NOI18N
        bnAdvancedSettings.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnAdvancedSettingsActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnFileExport, org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.bnFileExport.text")); // NOI18N
        bnFileExport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnFileExportActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnLogging, org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.bnLogging.text")); // NOI18N
        bnLogging.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnLoggingActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(browseOutputFolderButton, org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.browseOutputFolderButton.text")); // NOI18N
        browseOutputFolderButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseOutputFolderButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(browseInputFolderButton, org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.browseInputFolderButton.text")); // NOI18N
        browseInputFolderButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseInputFolderButtonActionPerformed(evt);
            }
        });

        inputPathTextField.setText(org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.inputPathTextField.text")); // NOI18N
        inputPathTextField.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.inputPathTextField.toolTipText")); // NOI18N

        outputPathTextField.setText(org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.outputPathTextField.text")); // NOI18N
        outputPathTextField.setToolTipText(org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.outputPathTextField.toolTipText")); // NOI18N

        jLabelInvalidResultsFolder.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(jLabelInvalidResultsFolder, org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.jLabelInvalidResultsFolder.text")); // NOI18N

        jLabelInvalidImageFolder.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(jLabelInvalidImageFolder, org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.jLabelInvalidImageFolder.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabelSelectInputFolder, org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.jLabelSelectInputFolder.text")); // NOI18N
        jLabelSelectInputFolder.setVerticalAlignment(javax.swing.SwingConstants.BOTTOM);

        org.openide.awt.Mnemonics.setLocalizedText(jLabelSelectOutputFolder, org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.jLabelSelectOutputFolder.text")); // NOI18N
        jLabelSelectOutputFolder.setVerticalAlignment(javax.swing.SwingConstants.BOTTOM);

        org.openide.awt.Mnemonics.setLocalizedText(sharedConfigCheckbox, org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.sharedConfigCheckbox.text")); // NOI18N
        sharedConfigCheckbox.setMaximumSize(new java.awt.Dimension(191, 14));
        sharedConfigCheckbox.setMinimumSize(new java.awt.Dimension(191, 14));
        sharedConfigCheckbox.setPreferredSize(new java.awt.Dimension(191, 14));
        sharedConfigCheckbox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                sharedConfigCheckboxItemStateChanged(evt);
            }
        });

        sharedSettingsErrorTextField.setEditable(false);
        sharedSettingsErrorTextField.setForeground(new java.awt.Color(255, 0, 0));
        sharedSettingsErrorTextField.setText(org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.sharedSettingsErrorTextField.text")); // NOI18N
        sharedSettingsErrorTextField.setBorder(null);

        sharedSettingsTextField.setText(org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.sharedSettingsTextField.text")); // NOI18N
        sharedSettingsTextField.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(browseSharedSettingsButton, org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.browseSharedSettingsButton.text")); // NOI18N
        browseSharedSettingsButton.setEnabled(false);
        browseSharedSettingsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseSharedSettingsButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(downloadButton, org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.downloadButton.text")); // NOI18N
        downloadButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                downloadButtonActionPerformed(evt);
            }
        });

        configButtonErrorTextField.setEditable(false);
        configButtonErrorTextField.setForeground(new java.awt.Color(255, 0, 0));
        configButtonErrorTextField.setText(org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.configButtonErrorTextField.text")); // NOI18N
        configButtonErrorTextField.setBorder(null);

        org.openide.awt.Mnemonics.setLocalizedText(jLabelTaskDescription, org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.jLabelTaskDescription.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabelCurrentTask, org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.jLabelCurrentTask.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(uploadButton, org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.uploadButton.text")); // NOI18N
        uploadButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                uploadButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(masterNodeCheckBox, org.openide.util.NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.masterNodeCheckBox.text")); // NOI18N
        masterNodeCheckBox.setEnabled(false);
        masterNodeCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                masterNodeCheckBoxItemStateChanged(evt);
            }
        });

        javax.swing.GroupLayout nodePanelLayout = new javax.swing.GroupLayout(nodePanel);
        nodePanel.setLayout(nodePanelLayout);
        nodePanelLayout.setHorizontalGroup(
            nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nodePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(nodePanelLayout.createSequentialGroup()
                        .addComponent(jLabelCurrentTask)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jLabelTaskDescription, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(nodePanelLayout.createSequentialGroup()
                        .addGroup(nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(outputPathTextField, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(inputPathTextField, javax.swing.GroupLayout.Alignment.LEADING))
                        .addGap(10, 10, 10)
                        .addGroup(nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(browseInputFolderButton, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(browseOutputFolderButton, javax.swing.GroupLayout.Alignment.TRAILING)))
                    .addGroup(nodePanelLayout.createSequentialGroup()
                        .addComponent(jLabelSelectInputFolder)
                        .addGap(18, 18, 18)
                        .addComponent(jLabelInvalidImageFolder, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(nodePanelLayout.createSequentialGroup()
                        .addGroup(nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(uploadButton, javax.swing.GroupLayout.PREFERRED_SIZE, 143, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(pbTaskInProgress, javax.swing.GroupLayout.PREFERRED_SIZE, 695, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(masterNodeCheckBox)
                            .addGroup(nodePanelLayout.createSequentialGroup()
                                .addComponent(cbJoinAutoIngestCluster, javax.swing.GroupLayout.PREFERRED_SIZE, 171, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(tbOops, javax.swing.GroupLayout.PREFERRED_SIZE, 465, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(nodePanelLayout.createSequentialGroup()
                                .addComponent(bnEditIngestSettings, javax.swing.GroupLayout.PREFERRED_SIZE, 155, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(bnAdvancedSettings, javax.swing.GroupLayout.PREFERRED_SIZE, 155, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(bnFileExport, javax.swing.GroupLayout.PREFERRED_SIZE, 155, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(bnLogging, javax.swing.GroupLayout.PREFERRED_SIZE, 155, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(nodePanelLayout.createSequentialGroup()
                                .addComponent(jLabelSelectOutputFolder)
                                .addGap(18, 18, 18)
                                .addComponent(jLabelInvalidResultsFolder, javax.swing.GroupLayout.PREFERRED_SIZE, 544, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(nodePanelLayout.createSequentialGroup()
                                .addComponent(sharedConfigCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(sharedSettingsErrorTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(nodePanelLayout.createSequentialGroup()
                                .addComponent(sharedSettingsTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 400, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(browseSharedSettingsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 143, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(nodePanelLayout.createSequentialGroup()
                                .addComponent(downloadButton, javax.swing.GroupLayout.PREFERRED_SIZE, 143, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(configButtonErrorTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 396, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 32, Short.MAX_VALUE)))
                .addContainerGap())
        );
        nodePanelLayout.setVerticalGroup(
            nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nodePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cbJoinAutoIngestCluster)
                    .addComponent(tbOops, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelSelectInputFolder)
                    .addComponent(jLabelInvalidImageFolder))
                .addGap(1, 1, 1)
                .addGroup(nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(inputPathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(browseInputFolderButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelSelectOutputFolder, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelInvalidResultsFolder))
                .addGap(1, 1, 1)
                .addGroup(nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(browseOutputFolderButton)
                    .addComponent(outputPathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(25, 25, 25)
                .addGroup(nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bnEditIngestSettings)
                    .addComponent(bnFileExport)
                    .addComponent(bnAdvancedSettings)
                    .addComponent(bnLogging))
                .addGap(18, 18, 18)
                .addGroup(nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sharedConfigCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(sharedSettingsErrorTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sharedSettingsTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(browseSharedSettingsButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(downloadButton)
                    .addComponent(configButtonErrorTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(masterNodeCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(uploadButton)
                .addGap(8, 8, 8)
                .addGroup(nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelCurrentTask)
                    .addComponent(jLabelTaskDescription))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pbTaskInProgress, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(161, Short.MAX_VALUE))
        );

        nodeScrollPane.setViewportView(nodePanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(nodeScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(nodeScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    boolean permissionsAppropriate(String path) {
        return FileUtil.hasReadWriteAccess(Paths.get(path));
    }

    private void setSharedConfigEnable() {
        setEnabledStateForSharedConfiguration();
        if (sharedConfigCheckbox.isEnabled() && sharedConfigCheckbox.isSelected()) {
            sharedSettingsTextField.setEnabled(true);
            browseSharedSettingsButton.setEnabled(true);
            masterNodeCheckBox.setEnabled(true);
            downloadButton.setEnabled(true);
            validateSettings();
            controller.changed();
        } else {
            sharedSettingsTextField.setEnabled(false);
            browseSharedSettingsButton.setEnabled(false);
            masterNodeCheckBox.setEnabled(false);
            downloadButton.setEnabled(false);
            sharedSettingsErrorTextField.setText("");
            validateSettings();
            controller.changed();
        }
    }

    private void cbJoinAutoIngestClusterActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cbJoinAutoIngestClusterActionPerformed
        enableOptionsBasedOnMode(getModeFromRadioButtons());
        controller.changed();
    }//GEN-LAST:event_cbJoinAutoIngestClusterActionPerformed

    private void downloadButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_downloadButtonActionPerformed
        // First save the shared config folder and solr settings to the properties
        String globalSettingsPath = getNormalizedFolderPath(sharedSettingsTextField.getText().trim());
        AutoIngestUserPreferences.setSharedConfigFolder(globalSettingsPath);

        enableUI(false);
        jLabelCurrentTask.setEnabled(true);
        jLabelTaskDescription.setEnabled(true);
        pbTaskInProgress.setEnabled(true);
        pbTaskInProgress.setIndeterminate(true);

        UpdateConfigSwingWorker worker = new UpdateConfigSwingWorker(ConfigTaskType.DOWNLOAD);
        try {
            worker.execute();
        } catch (Exception ex) {
            jLabelTaskDescription.setText(ex.getLocalizedMessage());
        }
    }//GEN-LAST:event_downloadButtonActionPerformed

    private void uploadButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_uploadButtonActionPerformed
        store();

        enableUI(false);
        jLabelCurrentTask.setEnabled(true);
        jLabelTaskDescription.setEnabled(true);
        pbTaskInProgress.setEnabled(true);
        pbTaskInProgress.setIndeterminate(true);

        UpdateConfigSwingWorker worker = new UpdateConfigSwingWorker(ConfigTaskType.UPLOAD);
        try {
            worker.execute();
        } catch (Exception ex) {
            jLabelTaskDescription.setText(ex.getLocalizedMessage());
        }
    }//GEN-LAST:event_uploadButtonActionPerformed

    private void masterNodeCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_masterNodeCheckBoxItemStateChanged
        // Enable the global settings text box and browse button iff the checkbox is checked and enabled
        setEnabledStateForSharedConfiguration();
        if (masterNodeCheckBox.isEnabled() && masterNodeCheckBox.isSelected()) {
            uploadButton.setEnabled(true);
            validateSettings(); // This will disable the upload/save button if the settings aren't currently valid
            controller.changed();
        } else {
            uploadButton.setEnabled(false);
        }
    }//GEN-LAST:event_masterNodeCheckBoxItemStateChanged

    private void browseSharedSettingsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseSharedSettingsButtonActionPerformed

        String oldText = sharedSettingsTextField.getText().trim();
        // set the current directory of the FileChooser if the oldText is valid
        File currentDir = new File(oldText);
        if (currentDir.exists()) {
            fc.setCurrentDirectory(currentDir);
        }

        fc.setDialogTitle("Select shared configuration folder:");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int retval = fc.showOpenDialog(this);
        if (retval == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getPath();
            sharedSettingsTextField.setText(path);
            validateSettings();
            controller.changed();
        }
    }//GEN-LAST:event_browseSharedSettingsButtonActionPerformed

    private void sharedConfigCheckboxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_sharedConfigCheckboxItemStateChanged
        // Enable the global settings text box and browse button iff the checkbox is checked and enabled
        setSharedConfigEnable();
    }//GEN-LAST:event_sharedConfigCheckboxItemStateChanged

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
            validateSettings();
            controller.changed();
        }
    }//GEN-LAST:event_browseOutputFolderButtonActionPerformed

    private void browseInputFolderButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseInputFolderButtonActionPerformed
        String oldText = inputPathTextField.getText().trim();
        // set the current directory of the FileChooser if the oldText is valid
        File currentDir = new File(oldText);
        if (currentDir.exists()) {
            fc.setCurrentDirectory(currentDir);
        }

        fc.setDialogTitle("Select case input folder:");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int retval = fc.showOpenDialog(this);
        if (retval == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getPath();
            inputPathTextField.setText(path);
            validateSettings();
            controller.changed();
        }
    }//GEN-LAST:event_browseInputFolderButtonActionPerformed

    private void bnLoggingActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnLoggingActionPerformed
        JDialog jDialog = new JDialog();
        NodeStatusLogPanel loggingPanel = new NodeStatusLogPanel(jDialog);

        JScrollPane jScrollPane = new JScrollPane(loggingPanel);
        jScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        jScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        jScrollPane.setMinimumSize(new Dimension(100, 100));
        jDialog.add(jScrollPane);
        jDialog.setTitle(NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.NodeStatusLogging.text"));
        jDialog.setIconImage(ImageUtilities.loadImage("org/sleuthkit/autopsy/experimental/images/frame32.gif"));
        jDialog.setModalityType(java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        jDialog.pack();
        jDialog.setLocationRelativeTo(this);
        jDialog.setVisible(true);
    }//GEN-LAST:event_bnLoggingActionPerformed

    private void bnFileExportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnFileExportActionPerformed
        JDialog jDialog = new JDialog();
        FileExporterSettingsPanel fileExporterSettingsPanel = new FileExporterSettingsPanel(jDialog);
        jDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                fileExporterSettingsPanel.store();
            }
        });
        JScrollPane jScrollPane = new JScrollPane(fileExporterSettingsPanel);
        jScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        jScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        jScrollPane.setMinimumSize(new Dimension(100, 100));
        jDialog.add(jScrollPane);
        jDialog.setTitle(NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.FileExportRules.text"));
        jDialog.setIconImage(ImageUtilities.loadImage("org/sleuthkit/autopsy/experimental/images/frame32.gif"));
        jDialog.setModalityType(java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        jDialog.pack();
        jDialog.setLocationRelativeTo(this);
        jDialog.setVisible(true);
    }//GEN-LAST:event_bnFileExportActionPerformed

    private void bnAdvancedSettingsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnAdvancedSettingsActionPerformed
        AdvancedAutoIngestSettingsPanel advancedAutoIngestSettingsPanel = new AdvancedAutoIngestSettingsPanel(getModeFromRadioButtons());
        if (JOptionPane.showConfirmDialog(null, advancedAutoIngestSettingsPanel,
            NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.AdvancedAutoIngestSettingsPanel.Title"),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
        advancedAutoIngestSettingsPanel.store();
        }
    }//GEN-LAST:event_bnAdvancedSettingsActionPerformed

    private void bnEditIngestSettingsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnEditIngestSettingsActionPerformed
        displayIngestJobSettingsPanel();
    }//GEN-LAST:event_bnEditIngestSettingsActionPerformed

    private void enableUI(boolean state) {
        enableOptionsBasedOnMode(OptionsUiMode.DOWNLOADING_CONFIGURATION);
        downloadButton.setEnabled(state);
        uploadButton.setEnabled(state);
        browseSharedSettingsButton.setEnabled(state);
        sharedConfigCheckbox.setEnabled(state);
        masterNodeCheckBox.setEnabled(state);
        sharedSettingsTextField.setEnabled(state);
    }

    private void resetUI() {
        load(true);
        controller.changed();
    }

    public enum ConfigTaskType {

        DOWNLOAD, UPLOAD
    }

    /**
     * Handles running the upload configuration task. There's some added
     * complexity here in order to re-enable the update button after the update
     * completes.
     *
     */
    public class UpdateConfigSwingWorker extends SwingWorker<Void, String> {

        private String errorMessage = null;
        private SharedConfiguration.SharedConfigResult uploadResult = SharedConfiguration.SharedConfigResult.SUCCESS;
        private final SharedConfiguration sharedConfig = new SharedConfiguration(this);
        private final ConfigTaskType task;

        protected UpdateConfigSwingWorker(ConfigTaskType task) {
            this.task = task;
        }

        @Override
        protected Void doInBackground() throws InterruptedException {
            try {
                if (task == ConfigTaskType.UPLOAD) {
                    uploadResult = sharedConfig.uploadConfiguration();
                } else {
                    uploadResult = sharedConfig.downloadConfiguration();
                }
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) {
                    throw (InterruptedException) ex;
                }
                errorMessage = ex.getLocalizedMessage();
            }
            return null;
        }

        public void publishStatus(String status) {
            publish(status);
        }

        @Override
        protected void process(List<String> messages) {
            for (String status : messages) {
                jLabelTaskDescription.setText(status);
            }
        }

        @Override
        protected void done() {
            // It would be nicer to hide the progress bar, but that seems to shrink the whole panel
            pbTaskInProgress.setIndeterminate(false);

            if (uploadResult == SharedConfiguration.SharedConfigResult.LOCKED) {
                jLabelTaskDescription.setText("Transfer of shared configuration incomplete");
                JOptionPane.showMessageDialog(null, "Shared configuration folder is currently locked by another node - try again in a few minutes", "Error", JOptionPane.ERROR_MESSAGE);
            } else if (errorMessage != null) {
                //MessageNotifyUtil.Message.info(errorMessage);
                jLabelTaskDescription.setText("Transfer of shared configuration incomplete");
                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), errorMessage, "Error copying configuration", JOptionPane.ERROR_MESSAGE);
            } else {
                jLabelTaskDescription.setText("Shared configuration copied successfully");
            }

            // Check if anything requiring a reset has changed and update the UI
            if (isResetNeeded()) {
                JOptionPane.showMessageDialog(null,
                        NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.MustRestart"),
                        NbBundle.getMessage(AutoIngestSettingsPanel.class, "AutoIngestSettingsPanel.restartRequiredLabel.text"),
                        JOptionPane.WARNING_MESSAGE);
            }

            // Re-enable everything here
            resetUI();
            setEnabledStateForSharedConfiguration();
            pbTaskInProgress.setEnabled(false);
        }
    }

    void setEnabledStateForSharedConfiguration() {
        if (cbJoinAutoIngestCluster.isSelected()) {
            enableOptionsBasedOnMode(OptionsUiMode.AIM);
        }
    }

    void setEnabledState(boolean enabled) {
        bnAdvancedSettings.setEnabled(enabled);
        bnEditIngestSettings.setEnabled(enabled);
        bnFileExport.setEnabled(enabled);
        bnLogging.setEnabled(enabled);
        browseInputFolderButton.setEnabled(enabled);
        browseOutputFolderButton.setEnabled(enabled);
        browseSharedSettingsButton.setEnabled(sharedConfigCheckbox.isSelected() && cbJoinAutoIngestCluster.isSelected());
        configButtonErrorTextField.setEnabled(enabled);
        inputPathTextField.setEnabled(enabled);
        jLabelInvalidImageFolder.setEnabled(enabled);
        jLabelInvalidResultsFolder.setEnabled(enabled);
        jLabelSelectInputFolder.setEnabled(enabled);
        jLabelSelectOutputFolder.setEnabled(enabled);
        outputPathTextField.setEnabled(enabled);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnAdvancedSettings;
    private javax.swing.JButton bnEditIngestSettings;
    private javax.swing.JButton bnFileExport;
    private javax.swing.JButton bnLogging;
    private javax.swing.JButton browseInputFolderButton;
    private javax.swing.JButton browseOutputFolderButton;
    private javax.swing.JButton browseSharedSettingsButton;
    private javax.swing.JCheckBox cbJoinAutoIngestCluster;
    private javax.swing.JTextField configButtonErrorTextField;
    private javax.swing.JButton downloadButton;
    private javax.swing.JTextField inputPathTextField;
    private javax.swing.JLabel jLabelCurrentTask;
    private javax.swing.JLabel jLabelInvalidImageFolder;
    private javax.swing.JLabel jLabelInvalidResultsFolder;
    private javax.swing.JLabel jLabelSelectInputFolder;
    private javax.swing.JLabel jLabelSelectOutputFolder;
    private javax.swing.JLabel jLabelTaskDescription;
    private javax.swing.JCheckBox masterNodeCheckBox;
    private javax.swing.ButtonGroup modeRadioButtons;
    private javax.swing.JPanel nodePanel;
    private javax.swing.JScrollPane nodeScrollPane;
    private javax.swing.JTextField outputPathTextField;
    private javax.swing.JProgressBar pbTaskInProgress;
    private javax.swing.JCheckBox sharedConfigCheckbox;
    private javax.swing.JTextField sharedSettingsErrorTextField;
    private javax.swing.JTextField sharedSettingsTextField;
    private javax.swing.JTextField tbOops;
    private javax.swing.JButton uploadButton;
    // End of variables declaration//GEN-END:variables
}
