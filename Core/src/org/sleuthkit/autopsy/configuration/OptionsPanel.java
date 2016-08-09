/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.configuration;

import java.awt.BorderLayout;
import java.awt.Color;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.corecomponents.TextPrompt;
import org.sleuthkit.autopsy.events.MessageServiceConnectionInfo;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettingsPanel;
import org.sleuthkit.datamodel.TskData;
import java.awt.Cursor;
import java.awt.Dimension;
import java.nio.file.Paths;
import javax.swing.ImageIcon;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchServiceException;
import org.sleuthkit.autopsy.events.MessageServiceException;
//ELTODO import viking.autoingest.FileExporterSettingsPanel;
import javax.swing.JScrollPane;

/**
 *
 */
public class OptionsPanel extends javax.swing.JPanel {

    private final JDialog parent;
    private final JFileChooser fc = new JFileChooser();
    private final Collection<JTextField> textBoxes = new ArrayList<>();
    private TextBoxChangedListener textBoxChangedListener;
    private static final String HOST_NAME_OR_IP_PROMPT = NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbDbHostname.toolTipText");
    private static final String PORT_PROMPT = NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbDbPort.toolTipText");
    private static final String USER_NAME_PROMPT = NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbDbUsername.toolTipText");
    private static final String PASSWORD_PROMPT = NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbDbPassword.toolTipText");
    private static final String USER_NAME_PROMPT_OPT = NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbMsgUsername.toolTipText");
    private static final String PASSWORD_PROMPT_OPT = NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbMsgPassword.toolTipText");
    private static final String INCOMPLETE_SETTINGS_MSG = NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.validationErrMsg.incomplete");
    private static final String INVALID_DB_PORT_MSG = NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.validationErrMsg.invalidDatabasePort");
    private static final String INVALID_MESSAGE_SERVICE_PORT_MSG = NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.validationErrMsg.invalidMessageServicePort");
    private static final String INVALID_INDEXING_SERVER_PORT_MSG = NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.validationErrMsg.invalidIndexingServerPort");
    private static final long serialVersionUID = 1L;
    private final ImageIcon goodIcon;
    private final ImageIcon badIcon;
    private static final Logger logger = Logger.getLogger(OptionsPanel.class.getName());
    private Integer oldIngestThreads;

    enum OptionsUiMode {

        STANDALONE, UTILITY, AIM, REVIEW, DOWNLOADING_CONFIGURATION
    };

    /**
     * Creates new form OptionsPanel
     */
    public OptionsPanel(JDialog parent) {
        this.parent = parent;
        initComponents();
        initMultiUserPanel();

        load(true);
        sharedSettingsTextField.getDocument().addDocumentListener(new MyDocumentListener());
        inputPathTextField.getDocument().addDocumentListener(new MyDocumentListener());
        outputPathTextField.getDocument().addDocumentListener(new MyDocumentListener());
        tbSolrHostname.getDocument().addDocumentListener(new MyDocumentListener());
        tbSolrPort.getDocument().addDocumentListener(new MyDocumentListener());

        jLabelInvalidImageFolder.setText("");
        jLabelInvalidResultsFolder.setText("");
        sharedSettingsErrorTextField.setText("");
        jLabelTaskDescription.setText("");
        configButtonErrorTextField.setText("");
        multiUserErrorTextField.setText("");

        pbTaskInProgress.setEnabled(false);
        jLabelCurrentTask.setEnabled(false);
        jLabelTaskDescription.setEnabled(false);

        goodIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/good.png", false));
        badIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/bad.png", false));

        this.oldIngestThreads = UserPreferences.numberOfFileIngestThreads();
        cbEnableMultiUserItemStateChanged(null);
    }

    private class MyDocumentListener implements DocumentListener {

        @Override
        public void changedUpdate(DocumentEvent e) {
            validateSettings();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            validateSettings();
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            validateSettings();
        }
    };

    /**
     * Load mode from persistent storage.
     *
     * @param inStartup True if we're doing the initial population of the UI
     */
    final void load(boolean inStartup) {
        loadMultiUserSettings();
        if (inStartup) {
            UserPreferences.SelectedMode storedMode = UserPreferences.getMode();
            inputPathTextField.requestFocusInWindow();
            if (null != storedMode) switch (storedMode) {
                case REVIEW:
                    jRadioButtonReview.setSelected(true);
                    enableOptionsBasedOnMode(OptionsUiMode.REVIEW);
                    break;
                case AUTOMATED:
                    jRadioButtonAutomated.setSelected(true);
                    enableOptionsBasedOnMode(OptionsUiMode.AIM);
                    break;
                case COPYFILES:
                    jRadioButtonCopyFilesMode.setSelected(true);
                    enableOptionsBasedOnMode(OptionsUiMode.UTILITY);
                    break;
                default:
                    jRadioButtonStandalone.setSelected(true);
                    enableOptionsBasedOnMode(OptionsUiMode.STANDALONE);
                    break;
            }
        }

        String images = UserPreferences.getAutoModeImageFolder();
        if (images != null) {
            inputPathTextField.setText(images);
        } else {
            inputPathTextField.setText("");
        }

        String results = UserPreferences.getAutoModeResultsFolder();
        if (results != null) {
            outputPathTextField.setText(results);
        } else {
            outputPathTextField.setText("");
        }

        if (inStartup) {
            sharedConfigCheckbox.setSelected(UserPreferences.getSharedConfigEnabled());
            String sharedSettingsFolder = UserPreferences.getSharedConfigFolder();
            if (sharedSettingsFolder != null) {
                sharedSettingsTextField.setText(sharedSettingsFolder);
            } else {
                String folder = getDefaultSharedFolder();
                sharedSettingsTextField.setText(folder);
            }

            masterNodeCheckBox.setSelected(UserPreferences.getSharedConfigMaster());
            setEnabledStateForSharedConfiguration();
        }

        validateSettings();
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
                sharedSettingsErrorTextField.setText(NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.ErrorSettingDefaultFolder"));
                return "";
            }
        }
        return sharedFolder.getAbsolutePath();
    }

    /**
     * Save mode to persistent storage.
     */
    void store() {
        if (jRadioButtonStandalone.isSelected()) {
            UserPreferences.setMode(UserPreferences.SelectedMode.STANDALONE);
        } else if (jRadioButtonAutomated.isSelected()) {
            boolean needsSaving = false;
            String thePath = UserPreferences.getAutoModeImageFolder();
            if (thePath != null && 0 != inputPathTextField.getText().compareTo(thePath)) {
                needsSaving = true;
            }
            thePath = UserPreferences.getAutoModeResultsFolder();
            if (thePath != null && 0 != outputPathTextField.getText().compareTo(thePath)) {
                needsSaving = true;
            }
            if (needsSaving) {
                JOptionPane.showMessageDialog(null,
                        NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.MustRestart"),
                        NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.restartRequiredLabel.text"),
                        JOptionPane.WARNING_MESSAGE);
            }

            UserPreferences.setMode(UserPreferences.SelectedMode.AUTOMATED);
            String imageFolderPath = getNormalizedFolderPath(inputPathTextField.getText().trim());
            UserPreferences.setAutoModeImageFolder(imageFolderPath);
            String resultsFolderPath = getNormalizedFolderPath(outputPathTextField.getText().trim());
            UserPreferences.setAutoModeResultsFolder(resultsFolderPath);
            UserPreferences.setSharedConfigEnabled(sharedConfigCheckbox.isSelected());
            if (sharedConfigCheckbox.isSelected()) {
                String globalSettingsPath = getNormalizedFolderPath(sharedSettingsTextField.getText().trim());
                UserPreferences.setSharedConfigFolder(globalSettingsPath);
                UserPreferences.setSharedConfigMaster(masterNodeCheckBox.isSelected());
            }
        } else if (jRadioButtonCopyFilesMode.isSelected()) {

            boolean needsSaving = false;
            String thePath = UserPreferences.getAutoModeImageFolder();
            if (thePath != null && 0 != inputPathTextField.getText().compareTo(thePath)) {
                needsSaving = true;
            }
            thePath = UserPreferences.getAutoModeResultsFolder();
            if (thePath != null && 0 != outputPathTextField.getText().compareTo(thePath)) {
                needsSaving = true;
            }
            if (needsSaving) {
                JOptionPane.showMessageDialog(null,
                        NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.MustRestart"),
                        NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.restartRequiredLabel.text"),
                        JOptionPane.WARNING_MESSAGE);
            }

            UserPreferences.setMode(UserPreferences.SelectedMode.COPYFILES);
            String imageFolderPath = getNormalizedFolderPath(inputPathTextField.getText().trim());
            UserPreferences.setAutoModeImageFolder(imageFolderPath);
            String resultsFolderPath = getNormalizedFolderPath(outputPathTextField.getText().trim());
            UserPreferences.setAutoModeResultsFolder(resultsFolderPath);
            UserPreferences.setSharedConfigEnabled(sharedConfigCheckbox.isSelected());
            if (sharedConfigCheckbox.isSelected()) {
                String globalSettingsPath = getNormalizedFolderPath(sharedSettingsTextField.getText().trim());
                UserPreferences.setSharedConfigFolder(globalSettingsPath);
                UserPreferences.setSharedConfigMaster(masterNodeCheckBox.isSelected());
            }
        } else if (jRadioButtonReview.isSelected()) {
            String thePath = UserPreferences.getAutoModeResultsFolder();
            if (thePath != null && 0 != outputPathTextField.getText().compareTo(thePath)) {
                JOptionPane.showMessageDialog(null,
                        NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.MustRestart"),
                        NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.restartRequiredLabel.text"),
                        JOptionPane.WARNING_MESSAGE);
            }

            UserPreferences.setMode(UserPreferences.SelectedMode.REVIEW);
            String resultsFolderPath = getNormalizedFolderPath(outputPathTextField.getText().trim());
            UserPreferences.setAutoModeResultsFolder(resultsFolderPath);
        }

        storeMultiUserSettings();
    }

    void validateSettings() {
        if (valid()) {
            bnSave.setEnabled(true);

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
            bnSave.setEnabled(false);
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
        jLabelInvalidImageFolder.setVisible(false);
        jLabelInvalidResultsFolder.setVisible(false);
        boolean isValidNodePanel = true;

        boolean isValidMultiuserPanel = validateMultiUserPanel();

        if (jRadioButtonAutomated.isSelected() || jRadioButtonCopyFilesMode.isSelected()) {
            if (sharedConfigCheckbox.isEnabled() && sharedConfigCheckbox.isSelected() && !validSharedConfigSettings()) {
                isValidNodePanel = false;
            }
        }

        if (!isValidMultiuserPanel) {
            mainTabPane.setForegroundAt(0, Color.red);
        } else {
            mainTabPane.setForegroundAt(0, Color.black);
        }

        if (!isValidNodePanel) {
            mainTabPane.setForegroundAt(1, Color.red);
        } else {
            mainTabPane.setForegroundAt(1, Color.black);
        }

        return isValidNodePanel && isValidMultiuserPanel;
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
        //  - solr address
        //  - solr port
        return (sharedConfigCheckbox.isEnabled() && sharedConfigCheckbox.isSelected()
                && cbEnableMultiUser.isSelected() && indexingServerSettingsAreValid()
                && !tbSolrHostname.getText().trim().isEmpty());
    }

    /**
     *
     */
    void displaySharedConfigButtonText() {
        if (sharedConfigCheckbox.isEnabled() && sharedConfigCheckbox.isSelected()) {
            if (!cbEnableMultiUser.isSelected()) {
                configButtonErrorTextField.setText("Multi-user cases must be enabled");
            } else if (!indexingServerSettingsAreValid() || tbSolrHostname.getText().trim().isEmpty()) {
                configButtonErrorTextField.setText("Multi-user Settings->Solr Settings are missing/invalid");
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

        boolean oldMultiUserSelected = cbEnableMultiUser.isSelected();
        String oldHostnameOrIp = tbDbHostname.getText().trim();
        String oldPortNumber = tbDbPort.getText().trim();
        String oldUsername = tbDbUsername.getText().trim();
        char[] oldPassword = tbDbPassword.getPassword();
        String oldMsgHost = tbMsgHostname.getText().trim();
        String oldMsgPort = tbMsgPort.getText().trim();
        String oldMsgUserName = tbMsgUsername.getText().trim();
        char[] oldMsgPassword = tbMsgPassword.getPassword();
        String oldIndexingServerHost = tbSolrHostname.getText().trim();
        String oldIndexingServerPort = tbSolrPort.getText().trim();

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

        if (oldMultiUserSelected != cbEnableMultiUser.isSelected()
                || !oldHostnameOrIp.equals(tbDbHostname.getText().trim())
                || !oldPortNumber.equals(tbDbPort.getText().trim())
                || !oldUsername.equals(tbDbUsername.getText().trim())
                || !oldMsgHost.equals(tbMsgHostname.getText().trim())
                || !oldMsgPort.equals(tbMsgPort.getText().trim())
                || !oldMsgUserName.equals(tbMsgUsername.getText().trim())
                || !oldIndexingServerHost.equals(tbSolrHostname.getText().trim())
                || !oldIndexingServerPort.equals(tbSolrPort.getText().trim())
                || !Arrays.equals(oldPassword, tbDbPassword.getPassword())
                || !Arrays.equals(oldMsgPassword, tbMsgPassword.getPassword())) {
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
            jLabelInvalidImageFolder.setText(NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.ImageDirectoryUnspecified"));
            return false;
        }

        if (!isFolderPathValid(inputPath)) {
            jLabelInvalidImageFolder.setVisible(true);
            jLabelInvalidImageFolder.setText(NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.PathInvalid"));
            return false;
        }

        if (false == permissionsAppropriate(inputPath)) {
            jLabelInvalidImageFolder.setVisible(true);
            jLabelInvalidImageFolder.setText(NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.CannotAccess")
                    + " " + inputPath + "   "
                    + NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.CheckPermissions"));
            return false;
        }

        jLabelInvalidImageFolder.setText("");
        return true;
    }

    /**
     * Validate results path. Display warnings if invalid.
     */
    boolean validateResultsPath() {

        String outputPath = outputPathTextField.getText().trim();

        if (outputPath.isEmpty()) {
            jLabelInvalidResultsFolder.setVisible(true);
            jLabelInvalidResultsFolder.setText(NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.ResultsDirectoryUnspecified"));
            return false;
        }

        if (!isFolderPathValid(outputPath)) {
            jLabelInvalidResultsFolder.setVisible(true);
            jLabelInvalidResultsFolder.setText(NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.PathInvalid"));
            return false;
        }

        if (false == permissionsAppropriate(outputPath)) {
            jLabelInvalidResultsFolder.setVisible(true);
            jLabelInvalidResultsFolder.setText(NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.CannotAccess")
                    + " " + outputPath + "   "
                    + NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.CheckPermissions"));
            return false;
        }

        jLabelInvalidResultsFolder.setText("");
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
            sharedSettingsErrorTextField.setText(NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.EmptySettingsDirectory"));
            return false;
        }

        if (!isFolderPathValid(sharedSettingsPath)) {
            sharedSettingsErrorTextField.setVisible(true);
            sharedSettingsErrorTextField.setText(NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.PathInvalid"));
            return false;
        }

        if (false == permissionsAppropriate(sharedSettingsPath)) {
            sharedSettingsErrorTextField.setVisible(true);
            sharedSettingsErrorTextField.setText(NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.CannotAccess")
                    + " " + sharedSettingsPath + " "
                    + NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.CheckPermissions"));
            return false;
        }

        sharedSettingsErrorTextField.setText("");
        return true;
    }

    private void displayIngestJobSettingsPanel() {

        IngestJobSettings ingestJobSettings = new IngestJobSettings(UserPreferences.getAutoModeIngestModuleContextString());
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
            jLabelSelectInputFolder.setEnabled(mode == OptionsUiMode.UTILITY || mode == OptionsUiMode.AIM);
            inputPathTextField.setEnabled(mode == OptionsUiMode.UTILITY || mode == OptionsUiMode.AIM);
            browseInputFolderButton.setEnabled(mode == OptionsUiMode.UTILITY || mode == OptionsUiMode.AIM);

            jLabelSelectOutputFolder.setEnabled(mode == OptionsUiMode.UTILITY || mode == OptionsUiMode.AIM || mode == OptionsUiMode.REVIEW);
            outputPathTextField.setEnabled(mode == OptionsUiMode.UTILITY || mode == OptionsUiMode.AIM || mode == OptionsUiMode.REVIEW);
            browseOutputFolderButton.setEnabled(mode == OptionsUiMode.UTILITY || mode == OptionsUiMode.AIM || mode == OptionsUiMode.REVIEW);

            jPanelSharedConfig.setEnabled(mode == OptionsUiMode.UTILITY || mode == OptionsUiMode.AIM);

            jPanelIngestSettings.setEnabled(mode == OptionsUiMode.AIM);
            bnEditIngestSettings.setEnabled(mode == OptionsUiMode.AIM);
            bnAdvancedSettings.setEnabled(mode == OptionsUiMode.AIM);
            jPanelSharedConfig.setEnabled(mode == OptionsUiMode.AIM);
            sharedConfigCheckbox.setEnabled(mode == OptionsUiMode.AIM);
            masterNodeCheckBox.setEnabled(mode == OptionsUiMode.AIM && sharedConfigCheckbox.isSelected());
            bnFileExport.setEnabled(mode == OptionsUiMode.AIM);
            sharedSettingsTextField.setEnabled(mode == OptionsUiMode.AIM);
            downloadButton.setEnabled(mode == OptionsUiMode.AIM);
        } else {
            setEnabledState(false);
        }

    }

    private OptionsUiMode getModeFromRadioButtons() {
        if (jRadioButtonStandalone.isSelected()) {
            return OptionsUiMode.STANDALONE;
        } else if (jRadioButtonCopyFilesMode.isSelected()) {
            return OptionsUiMode.UTILITY;
        } else if (jRadioButtonAutomated.isSelected()) {
            return OptionsUiMode.AIM;
        } else {
            return OptionsUiMode.REVIEW;
        }
    }

    // Multi-user panel code
    // Copied from org.sleuthkit.autopsy.corecomponents.MultiUserSettingsPanel to
    // prevent having to expose methods.
    /**
     * Used to listen for changes in text boxes. It lets the panel know things
     * have been updated and that validation needs to happen.
     */
    class TextBoxChangedListener implements DocumentListener {

        @Override
        public void changedUpdate(DocumentEvent e) {
            Object statusIcon = e.getDocument().getProperty("statusIcon");
            if (statusIcon != null) {
                ((javax.swing.JLabel) statusIcon).setIcon(null);
            }
            validateSettings();
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            Object statusIcon = e.getDocument().getProperty("statusIcon");
            if (statusIcon != null) {
                ((javax.swing.JLabel) statusIcon).setIcon(null);
            }
            validateSettings();
        }

        @Override

        public void removeUpdate(DocumentEvent e) {
            Object statusIcon = e.getDocument().getProperty("statusIcon");
            if (statusIcon != null) {
                ((javax.swing.JLabel) statusIcon).setIcon(null);
            }
            validateSettings();
        }
    }

    private void initMultiUserPanel() {
        /**
         * Add text prompts to all of the text fields.
         */
        Collection<TextPrompt> textPrompts = new ArrayList<>();
        textPrompts.add(new TextPrompt(HOST_NAME_OR_IP_PROMPT, tbDbHostname));
        textPrompts.add(new TextPrompt(PORT_PROMPT, tbDbPort));
        textPrompts.add(new TextPrompt(USER_NAME_PROMPT, tbDbUsername));
        textPrompts.add(new TextPrompt(PASSWORD_PROMPT, tbDbPassword));
        textPrompts.add(new TextPrompt(HOST_NAME_OR_IP_PROMPT, tbMsgHostname));
        textPrompts.add(new TextPrompt(PORT_PROMPT, tbMsgPort));
        textPrompts.add(new TextPrompt(USER_NAME_PROMPT_OPT, tbMsgUsername));
        textPrompts.add(new TextPrompt(PASSWORD_PROMPT_OPT, tbMsgPassword));
        textPrompts.add(new TextPrompt(HOST_NAME_OR_IP_PROMPT, tbSolrHostname));
        textPrompts.add(new TextPrompt(PORT_PROMPT, tbSolrPort));
        configureTextPrompts(textPrompts);

        /*
         * Set each textbox with a "statusIcon" property enabling the
         * DocumentListeners to know which icon to erase when changes are made
         */
        tbDbHostname.getDocument().putProperty("statusIcon", lbTestDatabase);
        tbDbPort.getDocument().putProperty("statusIcon", lbTestDatabase);
        tbDbUsername.getDocument().putProperty("statusIcon", lbTestDatabase);
        tbDbPassword.getDocument().putProperty("statusIcon", lbTestDatabase);

        tbSolrHostname.getDocument().putProperty("statusIcon", lbTestSolr);
        tbSolrPort.getDocument().putProperty("statusIcon", lbTestSolr);

        tbMsgHostname.getDocument().putProperty("statusIcon", lbTestMessageService);
        tbMsgPort.getDocument().putProperty("statusIcon", lbTestMessageService);
        tbMsgUsername.getDocument().putProperty("statusIcon", lbTestMessageService);
        tbMsgPassword.getDocument().putProperty("statusIcon", lbTestMessageService);

        /// Register for notifications when the text boxes get updated.
        textBoxChangedListener = new TextBoxChangedListener();
        textBoxes.add(tbDbHostname);
        textBoxes.add(tbDbPort);
        textBoxes.add(tbDbUsername);
        textBoxes.add(tbDbPassword);
        textBoxes.add(tbMsgHostname);
        textBoxes.add(tbMsgPort);
        textBoxes.add(tbMsgUsername);
        textBoxes.add(tbMsgPassword);
        textBoxes.add(tbSolrHostname);
        textBoxes.add(tbSolrPort);
        addMultiUserDocumentListeners(textBoxes, textBoxChangedListener);

        lbTestDatabase.setIcon(null);
        lbTestSolr.setIcon(null);
        lbTestMessageService.setIcon(null);
        lbTestDbWarning.setText("");
        lbTestSolrWarning.setText("");
        lbTestMessageWarning.setText("");
        enableMultiUserComponents(textBoxes, cbEnableMultiUser.isSelected());
    }

    void loadMultiUserSettings() {
        lbTestDatabase.setIcon(null);
        lbTestSolr.setIcon(null);
        lbTestMessageService.setIcon(null);
        lbTestDbWarning.setText("");
        lbTestSolrWarning.setText("");
        lbTestMessageWarning.setText("");
        try {
            CaseDbConnectionInfo dbInfo = UserPreferences.getDatabaseConnectionInfo();
            tbDbHostname.setText(dbInfo.getHost().trim());
            tbDbPort.setText(dbInfo.getPort().trim());
            tbDbUsername.setText(dbInfo.getUserName().trim());
            tbDbPassword.setText(dbInfo.getPassword());
        } catch (UserPreferencesException ex) {
            logger.log(Level.SEVERE, "Error accessing case database connection info", ex); //NON-NLS
        }

        try {
            MessageServiceConnectionInfo msgServiceInfo = UserPreferences.getMessageServiceConnectionInfo();
            tbMsgHostname.setText(msgServiceInfo.getHost().trim());
            tbMsgPort.setText(Integer.toString(msgServiceInfo.getPort()));
            tbMsgUsername.setText(msgServiceInfo.getUserName().trim());
            tbMsgPassword.setText(msgServiceInfo.getPassword());
        } catch (UserPreferencesException ex) {
            logger.log(Level.SEVERE, "Error accessing case database connection info", ex); //NON-NLS
        }

        String indexingServerHost = UserPreferences.getIndexingServerHost().trim();
        if (!indexingServerHost.isEmpty()) {
            tbSolrHostname.setText(indexingServerHost);
        }
        String indexingServerPort = UserPreferences.getIndexingServerPort().trim();
        if (portNumberIsValid(indexingServerPort)) {
            tbSolrPort.setText(indexingServerPort);
        }

        lbTestDatabase.setIcon(null);
        lbTestSolr.setIcon(null);
        lbTestMessageService.setIcon(null);

        bnTestDatabase.setEnabled(false);
        bnTestSolr.setEnabled(false);
        bnTestMessageService.setEnabled(false);

        cbEnableMultiUser.setSelected(UserPreferences.getIsMultiUserModeEnabled());
        this.valid(); // trigger validation to enable buttons based on current settings
    }

    void storeMultiUserSettings() {

        boolean multiUserCasesEnabled = cbEnableMultiUser.isSelected();
        UserPreferences.setIsMultiUserModeEnabled(multiUserCasesEnabled);
        if (multiUserCasesEnabled == false) {
            return;
        }

        /*
         * Currently only supporting multi-user cases with PostgreSQL case
         * databases.
         */
        TskData.DbType dbType = TskData.DbType.POSTGRESQL;
        CaseDbConnectionInfo info = new CaseDbConnectionInfo(
                tbDbHostname.getText().trim(),
                tbDbPort.getText().trim(),
                tbDbUsername.getText().trim(),
                new String(tbDbPassword.getPassword()),
                dbType);

        try {
            UserPreferences.setDatabaseConnectionInfo(info);
        } catch (UserPreferencesException ex) {
            logger.log(Level.SEVERE, "Error saving case database connection info", ex); //NON-NLS
        }

        int msgServicePort = 0;
        try {
            msgServicePort = Integer.parseInt(this.tbMsgPort.getText().trim());
        } catch (NumberFormatException ex) {
            logger.log(Level.SEVERE, "Could not parse messaging service port setting", ex);
        }

        MessageServiceConnectionInfo msgServiceInfo = new MessageServiceConnectionInfo(
                tbMsgHostname.getText().trim(),
                msgServicePort,
                tbMsgUsername.getText().trim(),
                new String(tbMsgPassword.getPassword()));

        try {
            UserPreferences.setMessageServiceConnectionInfo(msgServiceInfo);
        } catch (UserPreferencesException ex) {
            logger.log(Level.SEVERE, "Error saving messaging service connection info", ex); //NON-NLS
        }
        UserPreferences.setIndexingServerHost(tbSolrHostname.getText().trim());
        UserPreferences.setIndexingServerPort(Integer.parseInt(tbSolrPort.getText().trim()));
    }

    private boolean validateMultiUserPanel() {
        tbOops.setText("");
        multiUserErrorTextField.setText("");
        if ((jRadioButtonAutomated.isSelected() || jRadioButtonReview.isSelected() || jRadioButtonCopyFilesMode.isSelected())
                && !cbEnableMultiUser.isSelected()) {
            // AIM and Review mode both require multi-user settings to be enabled
            tbOops.setText("Multi-user settings must be enabled in non-standalone modes");
            multiUserErrorTextField.setText("Multi-user settings must be enabled");
            return false;
        }

        if (cbEnableMultiUser.isSelected()) {
            return checkFieldsAndEnableButtons()
                    && databaseSettingsAreValid()
                    && indexingServerSettingsAreValid()
                    && messageServiceSettingsAreValid();
        } else {
            return true;
        }
    }

    /**
     * Tests whether or not values have been entered in all of the database
     * settings text fields.
     *
     * @return True or false.
     */
    private boolean databaseFieldsArePopulated() {
        return !tbDbHostname.getText().trim().isEmpty()
                && !tbDbPort.getText().trim().isEmpty()
                && !tbDbUsername.getText().trim().isEmpty()
                && tbDbPassword.getPassword().length != 0;
    }

    /**
     * Tests whether or not values have been entered in all of the Solr settings
     * text fields.
     *
     * @return True or false.
     */
    private boolean solrFieldsArePopulated() {
        return !tbSolrHostname.getText().trim().isEmpty()
                && !tbSolrPort.getText().trim().isEmpty();
    }

    /**
     * Tests whether or not values have been entered in all of the required
     * message service settings text fields.
     *
     * @return True or false.
     */
    private boolean messageServiceFieldsArePopulated() {

        if ((tbMsgHostname.getText().trim().isEmpty())
                || (tbMsgPort.getText().trim().isEmpty())) {
            return false;
        }

        // user name and pw are optional, but make sure they are both set or both empty
        boolean isUserSet = (tbMsgUsername.getText().trim().isEmpty() == false);
        boolean isPwSet = (tbMsgPassword.getPassword().length != 0);
        return (isUserSet == isPwSet);
    }

    /**
     * Tests whether or not all of the settings components are populated and
     * sets the test buttons appropriately.
     *
     * @return True or false.
     */
    boolean checkFieldsAndEnableButtons() {
        boolean result = true;

        boolean dbPopulated = databaseFieldsArePopulated();
        boolean solrPopulated = solrFieldsArePopulated();
        boolean messageServicePopulated = messageServiceFieldsArePopulated();

        // PostgreSQL Database
        bnTestDatabase.setEnabled(dbPopulated && tbDbHostname.isEnabled());

        // Solr Indexing
        bnTestSolr.setEnabled(solrPopulated && tbSolrHostname.isEnabled());

        // ActiveMQ Messaging
        bnTestMessageService.setEnabled(messageServicePopulated && tbMsgHostname.isEnabled());

        if (!dbPopulated || !solrPopulated || !messageServicePopulated) {
            // We don't even have everything filled out
            if (sharedConfigCheckbox.isSelected() && !masterNodeCheckBox.isSelected() && solrPopulated) {
                result = true;
            } else {
                result = false;
                tbOops.setText(INCOMPLETE_SETTINGS_MSG);
            }
        }
        return result;
    }

    /**
     * Tests whether or not the database settings are valid.
     *
     * @return True or false.
     */
    boolean databaseSettingsAreValid() {
        if (portNumberIsValid(tbDbPort.getText().trim())) {
            return true;
        } else {
            tbOops.setText(INVALID_DB_PORT_MSG);
            return false;
        }
    }

    /**
     * Tests whether or not the message service settings are valid.
     *
     * @return True or false.
     */
    boolean messageServiceSettingsAreValid() {
        if (!portNumberIsValid(tbMsgPort.getText().trim())) {
            tbOops.setText(INVALID_MESSAGE_SERVICE_PORT_MSG);
            return false;
        }
        return true;
    }

    /**
     * Tests whether or not the indexing server settings are valid.
     *
     * @return True or false.
     */
    boolean indexingServerSettingsAreValid() {
        if (!portNumberIsValid(tbSolrPort.getText().trim())) {
            tbOops.setText(INVALID_INDEXING_SERVER_PORT_MSG);
            return false;
        }

        return true;
    }

    /**
     * Determines whether or not a port number is within the range of valid port
     * numbers.
     *
     * @param portNumber The port number as a string.
     *
     * @return True or false.
     */
    private static boolean portNumberIsValid(String portNumber) {
        try {
            int value = Integer.parseInt(portNumber);
            if (value < 0 || value > 65535) { // invalid port numbers
                return false;
            }
        } catch (NumberFormatException detailsNotImportant) {
            return false;
        }
        return true;
    }

    /**
     * Sets the foreground color and transparency of a collection of text
     * prompts.
     *
     * @param textPrompts The text prompts to configure.
     */
    private static void configureTextPrompts(Collection<TextPrompt> textPrompts) {
        float alpha = 0.9f; // Mostly opaque
        for (TextPrompt textPrompt : textPrompts) {
            textPrompt.setForeground(Color.LIGHT_GRAY);
            textPrompt.changeAlpha(alpha);
        }
    }

    /**
     * Adds a change listener to a collection of text fields.
     *
     * @param textFields The text fields.
     * @param listener   The change listener.
     */
    private static void addMultiUserDocumentListeners(Collection<JTextField> textFields, TextBoxChangedListener listener) {
        for (JTextField textField : textFields) {
            textField.getDocument().addDocumentListener(listener);
        }
    }

    /**
     * Enables/disables the multi-user settings, based upon input provided
     *
     * @param enabled true means enable, false means disable
     */
    private static void enableMultiUserComponents(Collection<JTextField> textFields, boolean enabled) {
        for (JTextField textField : textFields) {
            textField.setEnabled(enabled);
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
        bnSave = new javax.swing.JButton();
        bnCancel = new javax.swing.JButton();
        mainTabPane = new javax.swing.JTabbedPane();
        multiUserPanel = new javax.swing.JPanel();
        pnMessagingSettings = new javax.swing.JPanel();
        lbMessageServiceSettings = new javax.swing.JLabel();
        tbMsgHostname = new javax.swing.JTextField();
        tbMsgUsername = new javax.swing.JTextField();
        tbMsgPort = new javax.swing.JTextField();
        tbMsgPassword = new javax.swing.JPasswordField();
        bnTestMessageService = new javax.swing.JButton();
        lbTestMessageService = new javax.swing.JLabel();
        lbTestMessageWarning = new javax.swing.JLabel();
        pnSolrSettings = new javax.swing.JPanel();
        lbSolrSettings = new javax.swing.JLabel();
        tbSolrHostname = new javax.swing.JTextField();
        tbSolrPort = new javax.swing.JTextField();
        bnTestSolr = new javax.swing.JButton();
        lbTestSolr = new javax.swing.JLabel();
        lbTestSolrWarning = new javax.swing.JLabel();
        pnDatabaseSettings = new javax.swing.JPanel();
        tbDbHostname = new javax.swing.JTextField();
        tbDbPort = new javax.swing.JTextField();
        tbDbUsername = new javax.swing.JTextField();
        tbDbPassword = new javax.swing.JPasswordField();
        lbDatabaseSettings = new javax.swing.JLabel();
        bnTestDatabase = new javax.swing.JButton();
        lbTestDatabase = new javax.swing.JLabel();
        lbTestDbWarning = new javax.swing.JLabel();
        cbEnableMultiUser = new javax.swing.JCheckBox();
        tbOops = new javax.swing.JTextField();
        multiUserRestartLabel = new javax.swing.JLabel();
        nodePanel = new javax.swing.JPanel();
        jPanelNodeType = new javax.swing.JPanel();
        jLabelSelectMode = new javax.swing.JLabel();
        restartRequiredNodeLabel = new javax.swing.JLabel();
        jRadioButtonStandalone = new javax.swing.JRadioButton();
        jRadioButtonCopyFilesMode = new javax.swing.JRadioButton();
        jRadioButtonAutomated = new javax.swing.JRadioButton();
        jRadioButtonReview = new javax.swing.JRadioButton();
        jLabelSelectInputFolder = new javax.swing.JLabel();
        inputPathTextField = new javax.swing.JTextField();
        browseInputFolderButton = new javax.swing.JButton();
        jLabelSelectOutputFolder = new javax.swing.JLabel();
        outputPathTextField = new javax.swing.JTextField();
        browseOutputFolderButton = new javax.swing.JButton();
        jLabelAimDiagram = new javax.swing.JLabel();
        jLabelInvalidImageFolder = new javax.swing.JLabel();
        jLabelInvalidResultsFolder = new javax.swing.JLabel();
        multiUserErrorTextField = new javax.swing.JTextField();
        jPanelSharedConfig = new javax.swing.JPanel();
        sharedConfigCheckbox = new javax.swing.JCheckBox();
        sharedSettingsTextField = new javax.swing.JTextField();
        browseSharedSettingsButton = new javax.swing.JButton();
        sharedSettingsErrorTextField = new javax.swing.JTextField();
        masterNodeCheckBox = new javax.swing.JCheckBox();
        uploadButton = new javax.swing.JButton();
        downloadButton = new javax.swing.JButton();
        jLabelCurrentTask = new javax.swing.JLabel();
        pbTaskInProgress = new javax.swing.JProgressBar();
        jLabelTaskDescription = new javax.swing.JLabel();
        configButtonErrorTextField = new javax.swing.JTextField();
        jSeparator1 = new javax.swing.JSeparator();
        jPanelIngestSettings = new javax.swing.JPanel();
        bnEditIngestSettings = new javax.swing.JButton();
        bnAdvancedSettings = new javax.swing.JButton();
        bnFileExport = new javax.swing.JButton();

        org.openide.awt.Mnemonics.setLocalizedText(bnSave, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.bnSave.text")); // NOI18N
        bnSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnSaveActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnCancel, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.bnCancel.text")); // NOI18N
        bnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnCancelActionPerformed(evt);
            }
        });

        multiUserPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        pnMessagingSettings.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        lbMessageServiceSettings.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbMessageServiceSettings, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.lbMessageServiceSettings.text")); // NOI18N

        tbMsgHostname.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbMsgHostname.setText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbMsgHostname.text")); // NOI18N
        tbMsgHostname.setToolTipText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbMsgHostname.toolTipText")); // NOI18N

        tbMsgUsername.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbMsgUsername.setText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbMsgUsername.text")); // NOI18N
        tbMsgUsername.setToolTipText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbMsgUsername.toolTipText")); // NOI18N

        tbMsgPort.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbMsgPort.setText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbMsgPort.text")); // NOI18N
        tbMsgPort.setToolTipText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbMsgPort.toolTipText")); // NOI18N

        tbMsgPassword.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbMsgPassword.setText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbMsgPassword.text")); // NOI18N
        tbMsgPassword.setToolTipText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbMsgPassword.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnTestMessageService, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.bnTestMessageService.text")); // NOI18N
        bnTestMessageService.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnTestMessageServiceActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbTestMessageService, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.lbTestMessageService.text")); // NOI18N

        lbTestMessageWarning.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(lbTestMessageWarning, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.lbTestMessageWarning.text")); // NOI18N

        javax.swing.GroupLayout pnMessagingSettingsLayout = new javax.swing.GroupLayout(pnMessagingSettings);
        pnMessagingSettings.setLayout(pnMessagingSettingsLayout);
        pnMessagingSettingsLayout.setHorizontalGroup(
            pnMessagingSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnMessagingSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnMessagingSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnMessagingSettingsLayout.createSequentialGroup()
                        .addComponent(lbMessageServiceSettings)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(bnTestMessageService)
                        .addGap(18, 18, 18)
                        .addComponent(lbTestMessageService, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(tbMsgHostname, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(tbMsgUsername, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(tbMsgPort, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(tbMsgPassword)
                    .addGroup(pnMessagingSettingsLayout.createSequentialGroup()
                        .addComponent(lbTestMessageWarning)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        pnMessagingSettingsLayout.setVerticalGroup(
            pnMessagingSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnMessagingSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnMessagingSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbMessageServiceSettings)
                    .addComponent(bnTestMessageService)
                    .addComponent(lbTestMessageService, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbMsgHostname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbMsgPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbMsgUsername, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbMsgPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lbTestMessageWarning, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pnSolrSettings.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        lbSolrSettings.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbSolrSettings, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.lbSolrSettings.text")); // NOI18N

        tbSolrHostname.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbSolrHostname.setToolTipText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbSolrHostname.toolTipText")); // NOI18N

        tbSolrPort.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbSolrPort.setToolTipText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbSolrPort.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnTestSolr, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.bnTestSolr.text")); // NOI18N
        bnTestSolr.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnTestSolrActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbTestSolr, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.lbTestSolr.text")); // NOI18N

        lbTestSolrWarning.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(lbTestSolrWarning, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.lbTestSolrWarning.text")); // NOI18N

        javax.swing.GroupLayout pnSolrSettingsLayout = new javax.swing.GroupLayout(pnSolrSettings);
        pnSolrSettings.setLayout(pnSolrSettingsLayout);
        pnSolrSettingsLayout.setHorizontalGroup(
            pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                        .addComponent(lbSolrSettings)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(bnTestSolr)
                        .addGap(18, 18, 18)
                        .addComponent(lbTestSolr, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(tbSolrHostname, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(tbSolrPort, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                        .addComponent(lbTestSolrWarning)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        pnSolrSettingsLayout.setVerticalGroup(
            pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbSolrSettings)
                    .addComponent(bnTestSolr)
                    .addComponent(lbTestSolr, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbSolrHostname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 8, Short.MAX_VALUE)
                .addComponent(tbSolrPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lbTestSolrWarning, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        pnDatabaseSettings.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        tbDbHostname.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbDbHostname.setText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbDbHostname.text")); // NOI18N
        tbDbHostname.setToolTipText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbDbHostname.toolTipText")); // NOI18N

        tbDbPort.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbDbPort.setText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbDbPort.text")); // NOI18N
        tbDbPort.setToolTipText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbDbPort.toolTipText")); // NOI18N

        tbDbUsername.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbDbUsername.setText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbDbUsername.text")); // NOI18N
        tbDbUsername.setToolTipText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbDbUsername.toolTipText")); // NOI18N

        tbDbPassword.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbDbPassword.setText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbDbPassword.text")); // NOI18N
        tbDbPassword.setToolTipText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbDbPassword.toolTipText")); // NOI18N

        lbDatabaseSettings.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbDatabaseSettings, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.lbDatabaseSettings.text")); // NOI18N
        lbDatabaseSettings.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        org.openide.awt.Mnemonics.setLocalizedText(bnTestDatabase, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.bnTestDatabase.text")); // NOI18N
        bnTestDatabase.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnTestDatabaseActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbTestDatabase, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.lbTestDatabase.text")); // NOI18N
        lbTestDatabase.setAutoscrolls(true);

        lbTestDbWarning.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(lbTestDbWarning, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.lbTestDbWarning.text")); // NOI18N

        javax.swing.GroupLayout pnDatabaseSettingsLayout = new javax.swing.GroupLayout(pnDatabaseSettings);
        pnDatabaseSettings.setLayout(pnDatabaseSettingsLayout);
        pnDatabaseSettingsLayout.setHorizontalGroup(
            pnDatabaseSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnDatabaseSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnDatabaseSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tbDbHostname)
                    .addComponent(tbDbPort)
                    .addComponent(tbDbUsername)
                    .addComponent(tbDbPassword)
                    .addGroup(pnDatabaseSettingsLayout.createSequentialGroup()
                        .addComponent(lbDatabaseSettings)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(bnTestDatabase)
                        .addGap(18, 18, 18)
                        .addComponent(lbTestDatabase, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(pnDatabaseSettingsLayout.createSequentialGroup()
                        .addComponent(lbTestDbWarning)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        pnDatabaseSettingsLayout.setVerticalGroup(
            pnDatabaseSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnDatabaseSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnDatabaseSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lbTestDatabase, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(pnDatabaseSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(lbDatabaseSettings)
                        .addComponent(bnTestDatabase)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbDbHostname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbDbPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbDbUsername, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbDbPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lbTestDbWarning, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        org.openide.awt.Mnemonics.setLocalizedText(cbEnableMultiUser, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.cbEnableMultiUser.text")); // NOI18N
        cbEnableMultiUser.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cbEnableMultiUserItemStateChanged(evt);
            }
        });

        tbOops.setEditable(false);
        tbOops.setForeground(new java.awt.Color(255, 0, 0));
        tbOops.setText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.tbOops.text")); // NOI18N
        tbOops.setBorder(null);
        tbOops.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tbOopsActionPerformed(evt);
            }
        });

        multiUserRestartLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(multiUserRestartLabel, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.multiUserRestartLabel.text")); // NOI18N

        javax.swing.GroupLayout multiUserPanelLayout = new javax.swing.GroupLayout(multiUserPanel);
        multiUserPanel.setLayout(multiUserPanelLayout);
        multiUserPanelLayout.setHorizontalGroup(
            multiUserPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(multiUserPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(multiUserPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(pnDatabaseSettings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(multiUserPanelLayout.createSequentialGroup()
                        .addComponent(multiUserRestartLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 496, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 246, Short.MAX_VALUE))
                    .addComponent(pnSolrSettings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(pnMessagingSettings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(multiUserPanelLayout.createSequentialGroup()
                        .addComponent(cbEnableMultiUser)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(tbOops)))
                .addContainerGap())
        );
        multiUserPanelLayout.setVerticalGroup(
            multiUserPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(multiUserPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(multiUserRestartLabel)
                .addGap(15, 15, 15)
                .addGroup(multiUserPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cbEnableMultiUser)
                    .addComponent(tbOops, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(pnDatabaseSettings, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pnSolrSettings, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pnMessagingSettings, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        mainTabPane.addTab(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.multiUserPanel.TabConstraints.tabTitle"), multiUserPanel); // NOI18N

        nodePanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        jPanelNodeType.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.jPanelNodeType.border.title"))); // NOI18N
        jPanelNodeType.setMinimumSize(new java.awt.Dimension(50, 50));

        org.openide.awt.Mnemonics.setLocalizedText(jLabelSelectMode, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.jLabelSelectMode.text")); // NOI18N

        restartRequiredNodeLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(restartRequiredNodeLabel, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.restartRequiredNodeLabel.text")); // NOI18N

        modeRadioButtons.add(jRadioButtonStandalone);
        org.openide.awt.Mnemonics.setLocalizedText(jRadioButtonStandalone, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.jRadioButtonStandalone.text")); // NOI18N
        jRadioButtonStandalone.setToolTipText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.jRadioButtonStandalone.toolTipText")); // NOI18N
        jRadioButtonStandalone.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButtonStandaloneActionPerformed(evt);
            }
        });

        modeRadioButtons.add(jRadioButtonCopyFilesMode);
        org.openide.awt.Mnemonics.setLocalizedText(jRadioButtonCopyFilesMode, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.jRadioButtonCopyFilesMode.text")); // NOI18N
        jRadioButtonCopyFilesMode.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButtonCopyFilesModeActionPerformed(evt);
            }
        });

        modeRadioButtons.add(jRadioButtonAutomated);
        org.openide.awt.Mnemonics.setLocalizedText(jRadioButtonAutomated, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.jRadioButtonAutomated.text")); // NOI18N
        jRadioButtonAutomated.setToolTipText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.jRadioButtonAutomated.toolTipText")); // NOI18N
        jRadioButtonAutomated.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButtonAutomatedActionPerformed(evt);
            }
        });

        modeRadioButtons.add(jRadioButtonReview);
        org.openide.awt.Mnemonics.setLocalizedText(jRadioButtonReview, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.jRadioButtonReview.text")); // NOI18N
        jRadioButtonReview.setToolTipText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.jRadioButtonReview.toolTipText")); // NOI18N
        jRadioButtonReview.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButtonReviewActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabelSelectInputFolder, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.jLabelSelectInputFolder.text")); // NOI18N
        jLabelSelectInputFolder.setVerticalAlignment(javax.swing.SwingConstants.BOTTOM);

        inputPathTextField.setText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.inputPathTextField.text")); // NOI18N
        inputPathTextField.setToolTipText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.inputPathTextField.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(browseInputFolderButton, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.browseInputFolderButton.text")); // NOI18N
        browseInputFolderButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseInputFolderButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabelSelectOutputFolder, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.jLabelSelectOutputFolder.text")); // NOI18N
        jLabelSelectOutputFolder.setVerticalAlignment(javax.swing.SwingConstants.BOTTOM);

        outputPathTextField.setText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.outputPathTextField.text")); // NOI18N
        outputPathTextField.setToolTipText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.outputPathTextField.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(browseOutputFolderButton, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.browseOutputFolderButton.text")); // NOI18N
        browseOutputFolderButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseOutputFolderButtonActionPerformed(evt);
            }
        });

        jLabelAimDiagram.setIcon(new javax.swing.ImageIcon(getClass().getResource("/viking/images/viking.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(jLabelAimDiagram, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.jLabelAimDiagram.text")); // NOI18N

        jLabelInvalidImageFolder.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(jLabelInvalidImageFolder, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.jLabelInvalidImageFolder.text")); // NOI18N

        jLabelInvalidResultsFolder.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(jLabelInvalidResultsFolder, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.jLabelInvalidResultsFolder.text")); // NOI18N

        multiUserErrorTextField.setEditable(false);
        multiUserErrorTextField.setForeground(new java.awt.Color(255, 0, 0));
        multiUserErrorTextField.setText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.multiUserErrorTextField.text")); // NOI18N
        multiUserErrorTextField.setBorder(null);

        javax.swing.GroupLayout jPanelNodeTypeLayout = new javax.swing.GroupLayout(jPanelNodeType);
        jPanelNodeType.setLayout(jPanelNodeTypeLayout);
        jPanelNodeTypeLayout.setHorizontalGroup(
            jPanelNodeTypeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelNodeTypeLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelNodeTypeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelNodeTypeLayout.createSequentialGroup()
                        .addGroup(jPanelNodeTypeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(outputPathTextField, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(inputPathTextField, javax.swing.GroupLayout.Alignment.LEADING))
                        .addGap(10, 10, 10)
                        .addGroup(jPanelNodeTypeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(browseInputFolderButton, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(browseOutputFolderButton, javax.swing.GroupLayout.Alignment.TRAILING)))
                    .addGroup(jPanelNodeTypeLayout.createSequentialGroup()
                        .addGroup(jPanelNodeTypeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jRadioButtonCopyFilesMode)
                            .addComponent(jRadioButtonAutomated)
                            .addComponent(jRadioButtonReview)
                            .addComponent(jRadioButtonStandalone)
                            .addGroup(jPanelNodeTypeLayout.createSequentialGroup()
                                .addComponent(jLabelSelectMode)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(restartRequiredNodeLabel))
                            .addComponent(multiUserErrorTextField))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jLabelAimDiagram))
                    .addGroup(jPanelNodeTypeLayout.createSequentialGroup()
                        .addGroup(jPanelNodeTypeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanelNodeTypeLayout.createSequentialGroup()
                                .addComponent(jLabelSelectInputFolder)
                                .addGap(18, 18, 18)
                                .addComponent(jLabelInvalidImageFolder, javax.swing.GroupLayout.PREFERRED_SIZE, 543, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanelNodeTypeLayout.createSequentialGroup()
                                .addComponent(jLabelSelectOutputFolder)
                                .addGap(18, 18, 18)
                                .addComponent(jLabelInvalidResultsFolder, javax.swing.GroupLayout.PREFERRED_SIZE, 544, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanelNodeTypeLayout.setVerticalGroup(
            jPanelNodeTypeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelNodeTypeLayout.createSequentialGroup()
                .addGroup(jPanelNodeTypeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelNodeTypeLayout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(jPanelNodeTypeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabelSelectMode)
                            .addComponent(restartRequiredNodeLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jRadioButtonStandalone)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jRadioButtonCopyFilesMode)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jRadioButtonAutomated)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jRadioButtonReview)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(multiUserErrorTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jLabelAimDiagram))
                .addGap(6, 6, 6)
                .addGroup(jPanelNodeTypeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelSelectInputFolder, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelInvalidImageFolder))
                .addGap(1, 1, 1)
                .addGroup(jPanelNodeTypeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(inputPathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(browseInputFolderButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelNodeTypeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelSelectOutputFolder, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelInvalidResultsFolder))
                .addGap(1, 1, 1)
                .addGroup(jPanelNodeTypeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(browseOutputFolderButton)
                    .addComponent(outputPathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(6, 6, 6))
        );

        jPanelSharedConfig.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.jPanelSharedConfig.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(sharedConfigCheckbox, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.sharedConfigCheckbox.text")); // NOI18N
        sharedConfigCheckbox.setMaximumSize(new java.awt.Dimension(191, 14));
        sharedConfigCheckbox.setMinimumSize(new java.awt.Dimension(191, 14));
        sharedConfigCheckbox.setPreferredSize(new java.awt.Dimension(191, 14));
        sharedConfigCheckbox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                sharedConfigCheckboxItemStateChanged(evt);
            }
        });

        sharedSettingsTextField.setText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.sharedSettingsTextField.text")); // NOI18N
        sharedSettingsTextField.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(browseSharedSettingsButton, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.browseSharedSettingsButton.text")); // NOI18N
        browseSharedSettingsButton.setEnabled(false);
        browseSharedSettingsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseSharedSettingsButtonActionPerformed(evt);
            }
        });

        sharedSettingsErrorTextField.setEditable(false);
        sharedSettingsErrorTextField.setForeground(new java.awt.Color(255, 0, 0));
        sharedSettingsErrorTextField.setText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.sharedSettingsErrorTextField.text")); // NOI18N
        sharedSettingsErrorTextField.setBorder(null);

        org.openide.awt.Mnemonics.setLocalizedText(masterNodeCheckBox, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.masterNodeCheckBox.text")); // NOI18N
        masterNodeCheckBox.setEnabled(false);
        masterNodeCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                masterNodeCheckBoxItemStateChanged(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(uploadButton, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.uploadButton.text")); // NOI18N
        uploadButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                uploadButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(downloadButton, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.downloadButton.text")); // NOI18N
        downloadButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                downloadButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabelCurrentTask, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.jLabelCurrentTask.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabelTaskDescription, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.jLabelTaskDescription.text")); // NOI18N

        configButtonErrorTextField.setEditable(false);
        configButtonErrorTextField.setForeground(new java.awt.Color(255, 0, 0));
        configButtonErrorTextField.setText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.configButtonErrorTextField.text")); // NOI18N
        configButtonErrorTextField.setBorder(null);

        javax.swing.GroupLayout jPanelSharedConfigLayout = new javax.swing.GroupLayout(jPanelSharedConfig);
        jPanelSharedConfig.setLayout(jPanelSharedConfigLayout);
        jPanelSharedConfigLayout.setHorizontalGroup(
            jPanelSharedConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSharedConfigLayout.createSequentialGroup()
                .addGap(10, 10, 10)
                .addComponent(jLabelCurrentTask)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabelTaskDescription, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
            .addGroup(jPanelSharedConfigLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelSharedConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelSharedConfigLayout.createSequentialGroup()
                        .addComponent(sharedSettingsTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 400, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(browseSharedSettingsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 143, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(uploadButton, javax.swing.GroupLayout.PREFERRED_SIZE, 143, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanelSharedConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanelSharedConfigLayout.createSequentialGroup()
                            .addComponent(downloadButton, javax.swing.GroupLayout.PREFERRED_SIZE, 143, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                            .addComponent(configButtonErrorTextField))
                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanelSharedConfigLayout.createSequentialGroup()
                            .addComponent(sharedConfigCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                            .addComponent(sharedSettingsErrorTextField))
                        .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 692, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(pbTaskInProgress, javax.swing.GroupLayout.PREFERRED_SIZE, 695, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(masterNodeCheckBox))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanelSharedConfigLayout.setVerticalGroup(
            jPanelSharedConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSharedConfigLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelSharedConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sharedConfigCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(sharedSettingsErrorTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelSharedConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sharedSettingsTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(browseSharedSettingsButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelSharedConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(downloadButton)
                    .addComponent(configButtonErrorTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(6, 6, 6)
                .addComponent(masterNodeCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(uploadButton)
                .addGap(8, 8, 8)
                .addGroup(jPanelSharedConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelCurrentTask)
                    .addComponent(jLabelTaskDescription))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(pbTaskInProgress, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanelIngestSettings.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.jPanelIngestSettings.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnEditIngestSettings, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.bnEditIngestSettings.text")); // NOI18N
        bnEditIngestSettings.setToolTipText(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.bnEditIngestSettings.toolTipText")); // NOI18N
        bnEditIngestSettings.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnEditIngestSettingsActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnAdvancedSettings, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.bnAdvancedSettings.text")); // NOI18N
        bnAdvancedSettings.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnAdvancedSettingsActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnFileExport, org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.bnFileExport.text")); // NOI18N
        bnFileExport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnFileExportActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelIngestSettingsLayout = new javax.swing.GroupLayout(jPanelIngestSettings);
        jPanelIngestSettings.setLayout(jPanelIngestSettingsLayout);
        jPanelIngestSettingsLayout.setHorizontalGroup(
            jPanelIngestSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelIngestSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(bnEditIngestSettings, javax.swing.GroupLayout.PREFERRED_SIZE, 155, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bnAdvancedSettings, javax.swing.GroupLayout.PREFERRED_SIZE, 155, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bnFileExport, javax.swing.GroupLayout.PREFERRED_SIZE, 155, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanelIngestSettingsLayout.setVerticalGroup(
            jPanelIngestSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelIngestSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelIngestSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bnEditIngestSettings)
                    .addComponent(bnFileExport)
                    .addComponent(bnAdvancedSettings))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout nodePanelLayout = new javax.swing.GroupLayout(nodePanel);
        nodePanel.setLayout(nodePanelLayout);
        nodePanelLayout.setHorizontalGroup(
            nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nodePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanelNodeType, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanelSharedConfig, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanelIngestSettings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        nodePanelLayout.setVerticalGroup(
            nodePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nodePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanelNodeType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jPanelIngestSettings, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jPanelSharedConfig, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        mainTabPane.addTab(org.openide.util.NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.nodePanel.TabConstraints.tabTitle"), nodePanel); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(mainTabPane, javax.swing.GroupLayout.PREFERRED_SIZE, 771, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap())
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(bnSave, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bnCancel, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(15, 15, 15))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(mainTabPane, javax.swing.GroupLayout.PREFERRED_SIZE, 705, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bnSave)
                    .addComponent(bnCancel))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void bnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnCancelActionPerformed
        parent.dispose();
    }//GEN-LAST:event_bnCancelActionPerformed

    private void bnEditIngestSettingsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnEditIngestSettingsActionPerformed
        displayIngestJobSettingsPanel();
    }//GEN-LAST:event_bnEditIngestSettingsActionPerformed

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
        }
    }//GEN-LAST:event_browseSharedSettingsButtonActionPerformed

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
        }
    }//GEN-LAST:event_browseInputFolderButtonActionPerformed

    private void jRadioButtonReviewActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButtonReviewActionPerformed
        enableOptionsBasedOnMode(OptionsUiMode.REVIEW);
        setSharedConfigEnable();
        validateSettings();
    }//GEN-LAST:event_jRadioButtonReviewActionPerformed

    private void jRadioButtonAutomatedActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButtonAutomatedActionPerformed
        enableOptionsBasedOnMode(OptionsUiMode.AIM);
        setSharedConfigEnable();
        cbEnableMultiUserItemStateChanged(null);
        validateSettings();
    }//GEN-LAST:event_jRadioButtonAutomatedActionPerformed

    private void jRadioButtonCopyFilesModeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButtonCopyFilesModeActionPerformed
        enableOptionsBasedOnMode(OptionsUiMode.UTILITY);
        setSharedConfigEnable();
        validateSettings();
    }//GEN-LAST:event_jRadioButtonCopyFilesModeActionPerformed

    private void jRadioButtonStandaloneActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButtonStandaloneActionPerformed
        enableOptionsBasedOnMode(OptionsUiMode.STANDALONE);
        setSharedConfigEnable();
        validateSettings();
    }//GEN-LAST:event_jRadioButtonStandaloneActionPerformed

    boolean permissionsAppropriate(String path) {
        return FileUtil.hasReadWriteAccess(Paths.get(path));
    }

    private void bnSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnSaveActionPerformed
        boolean isValidNodePanel = true;

        switch (getModeFromRadioButtons()) {
            case UTILITY:
                if (!validateImagePath()) {
                    isValidNodePanel = false;
                }
                if (!validateResultsPath()) {
                    isValidNodePanel = false;
                }
                break;
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
            case REVIEW:
                if (!validateResultsPath()) {
                    isValidNodePanel = false;
                }
                break;

            case STANDALONE:
                break;
            default:
                break;
        }
        if (isValidNodePanel) {
            store();
            parent.dispose();
        }
    }//GEN-LAST:event_bnSaveActionPerformed

    private void cbEnableMultiUserItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cbEnableMultiUserItemStateChanged
        if (!cbEnableMultiUser.isSelected()) {
            tbOops.setText("");
            bnTestDatabase.setEnabled(false);
            lbTestDatabase.setIcon(null);
            bnTestSolr.setEnabled(false);
            lbTestSolr.setIcon(null);
            bnTestMessageService.setEnabled(false);
            lbTestMessageService.setIcon(null);
            lbTestDbWarning.setText("");
            lbTestSolrWarning.setText("");
            lbTestMessageWarning.setText("");
            lbSolrSettings.setEnabled(false);
            enableMultiUserComponents(textBoxes, cbEnableMultiUser.isSelected());
        } else if (sharedConfigCheckbox.isEnabled() && sharedConfigCheckbox.isSelected() && !masterNodeCheckBox.isSelected()) {
            bnTestDatabase.setEnabled(false);
            lbTestDatabase.setIcon(null);
            lbTestSolr.setIcon(null);
            bnTestMessageService.setEnabled(false);
            lbTestMessageService.setIcon(null);
            lbTestDbWarning.setText("");
            lbTestSolrWarning.setText("");
            lbTestMessageWarning.setText("");
            enableMultiUserComponents(textBoxes, false);
            bnTestSolr.setEnabled(true);
            lbTestSolr.setEnabled(true);
            tbSolrHostname.setEnabled(true);
            tbSolrPort.setEnabled(true);
            lbSolrSettings.setEnabled(true);
        } else {
            enableMultiUserComponents(textBoxes, cbEnableMultiUser.isSelected());
        }

        validateSettings();
    }//GEN-LAST:event_cbEnableMultiUserItemStateChanged

    private void tbOopsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tbOopsActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_tbOopsActionPerformed

    private void downloadButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_downloadButtonActionPerformed
        // First save the shared config folder and solr settings to the properties
        String globalSettingsPath = getNormalizedFolderPath(sharedSettingsTextField.getText().trim());
        UserPreferences.setSharedConfigFolder(globalSettingsPath);
        UserPreferences.setIndexingServerHost(tbSolrHostname.getText().trim());
        UserPreferences.setIndexingServerPort(Integer.parseInt(tbSolrPort.getText().trim()));

        disableUI();
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

        disableUI();
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

    private void sharedConfigCheckboxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_sharedConfigCheckboxItemStateChanged
        // Enable the global settings text box and browse button iff the checkbox is checked and enabled
        setSharedConfigEnable();
    }//GEN-LAST:event_sharedConfigCheckboxItemStateChanged

    private void setSharedConfigEnable() {
        setEnabledStateForSharedConfiguration();
        if (sharedConfigCheckbox.isEnabled() && sharedConfigCheckbox.isSelected() && jRadioButtonAutomated.isSelected()) {
            sharedSettingsTextField.setEnabled(true);
            browseSharedSettingsButton.setEnabled(true);
            masterNodeCheckBox.setEnabled(true);
            downloadButton.setEnabled(true);
            validateSettings();
        } else {
            sharedSettingsTextField.setEnabled(false);
            browseSharedSettingsButton.setEnabled(false);
            masterNodeCheckBox.setEnabled(false);
            downloadButton.setEnabled(false);
            sharedSettingsErrorTextField.setText("");
            validateSettings();
        }
        cbEnableMultiUserItemStateChanged(null);
    }

    private void masterNodeCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_masterNodeCheckBoxItemStateChanged
        // Enable the global settings text box and browse button iff the checkbox is checked and enabled
        setEnabledStateForSharedConfiguration();
        if (masterNodeCheckBox.isEnabled() && masterNodeCheckBox.isSelected()) {
            uploadButton.setEnabled(true);
            validateSettings(); // This will disable the upload/save button if the settings aren't currently valid
        } else {
            uploadButton.setEnabled(false);
        }
        if (sharedConfigCheckbox.isEnabled()) {
            jRadioButtonAutomated.setEnabled(false);
            jRadioButtonCopyFilesMode.setEnabled(false);
            jRadioButtonReview.setEnabled(false);
            jRadioButtonStandalone.setEnabled(false);
        }
        cbEnableMultiUserItemStateChanged(null);
    }//GEN-LAST:event_masterNodeCheckBoxItemStateChanged

    private void bnTestDatabaseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnTestDatabaseActionPerformed
        lbTestDatabase.setIcon(null);
        lbTestDbWarning.setText("");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            CaseDbConnectionInfo info = new CaseDbConnectionInfo(
                    this.tbDbHostname.getText().trim(),
                    this.tbDbPort.getText().trim(),
                    this.tbDbUsername.getText().trim(),
                    new String(this.tbDbPassword.getPassword()),
                    TskData.DbType.POSTGRESQL);

            SleuthkitCase.tryConnect(info);
            lbTestDatabase.setIcon(goodIcon);
            lbTestDbWarning.setText("");
        } catch (TskCoreException ex) {
            lbTestDatabase.setIcon(badIcon);
            lbTestDbWarning.setText(ex.getMessage());
        } finally {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }//GEN-LAST:event_bnTestDatabaseActionPerformed

    private void bnTestSolrActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnTestSolrActionPerformed
        lbTestSolr.setIcon(null);
        lbTestSolrWarning.setText("");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        KeywordSearchService kwsService = Lookup.getDefault().lookup(KeywordSearchService.class);
        try {
            if (kwsService != null) {
                int port = Integer.parseInt(tbSolrPort.getText().trim());
                kwsService.tryConnect(tbSolrHostname.getText().trim(), port);
                lbTestSolr.setIcon(goodIcon);
                lbTestSolrWarning.setText("");
            } else {
                lbTestSolr.setIcon(badIcon);
                lbTestSolrWarning.setText(NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.KeywordSearchNull"));
            }
        } catch (NumberFormatException ex) {
            lbTestSolr.setIcon(badIcon);
            lbTestSolrWarning.setText(NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.InvalidPortNumber"));
        } catch (KeywordSearchServiceException ex) {
            lbTestSolr.setIcon(badIcon);
            lbTestSolrWarning.setText(ex.getMessage());
        } finally {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }//GEN-LAST:event_bnTestSolrActionPerformed

    private void bnTestMessageServiceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnTestMessageServiceActionPerformed
        lbTestMessageService.setIcon(null);
        lbTestMessageWarning.setText("");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        int port;
        try {
            port = Integer.parseInt(this.tbMsgPort.getText().trim());
        } catch (NumberFormatException ex) {
            lbTestMessageService.setIcon(badIcon);
            lbTestMessageWarning.setText(NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.InvalidPortNumber"));
            return;
        }

        MessageServiceConnectionInfo info = new MessageServiceConnectionInfo(
                this.tbMsgHostname.getText().trim(),
                port,
                this.tbMsgUsername.getText().trim(),
                new String(this.tbMsgPassword.getPassword()));
        try {
            info.tryConnect();
            lbTestMessageService.setIcon(goodIcon);
            lbTestMessageWarning.setText("");
        } catch (MessageServiceException ex) {
            lbTestMessageService.setIcon(badIcon);
            lbTestMessageWarning.setText(ex.getMessage());
        } finally {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }//GEN-LAST:event_bnTestMessageServiceActionPerformed

    private void bnAdvancedSettingsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnAdvancedSettingsActionPerformed
        AdvancedOptionsPanel advancedOptionsPanel = new AdvancedOptionsPanel(getModeFromRadioButtons());
        if (JOptionPane.showConfirmDialog(null, advancedOptionsPanel,
                NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.AdvancedOptionsPanel.Title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            advancedOptionsPanel.store();
        }
    }//GEN-LAST:event_bnAdvancedSettingsActionPerformed

    private void bnFileExportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnFileExportActionPerformed
        JDialog jDialog = new JDialog();
        /*ELTODO FileExporterSettingsPanel fileExporterSettingsPanel = new FileExporterSettingsPanel(jDialog);
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
        jDialog.setTitle(NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.FileExportRules.text"));
        //ELTODO jDialog.setIconImage(ImageUtilities.loadImage("/viking/images/viking32.gif"));
        jDialog.setModalityType(java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        jDialog.pack();
        jDialog.setLocationRelativeTo(this);
        jDialog.setVisible(true);*/
    }//GEN-LAST:event_bnFileExportActionPerformed

    private void disableUI() {
        enableOptionsBasedOnMode(OptionsUiMode.DOWNLOADING_CONFIGURATION);
        downloadButton.setEnabled(false);
        uploadButton.setEnabled(false);
        browseSharedSettingsButton.setEnabled(false);
        sharedConfigCheckbox.setEnabled(false);
        masterNodeCheckBox.setEnabled(false);
        sharedSettingsTextField.setEnabled(false);
        // Disable multi-user settings
        cbEnableMultiUser.setEnabled(false);
        enableMultiUserComponents(textBoxes, false);
        bnTestSolr.setEnabled(false);
        bnSave.setEnabled(false);
        bnCancel.setEnabled(false);
    }

    private void resetUI() {
        enableOptionsBasedOnMode(getModeFromRadioButtons());
        setSharedConfigEnable();
        // Enable multi-user settings
        cbEnableMultiUser.setEnabled(true);
        enableMultiUserComponents(textBoxes, cbEnableMultiUser.isSelected());
        bnTestSolr.setEnabled(true);
        bnCancel.setEnabled(true);

        validateSettings(); // Will re-enable the save button if everything is valid
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
                bnSave.setEnabled(true);
            }

            // Check if anything requiring a reset has changed and update the UI
            if (isResetNeeded()) {
                JOptionPane.showMessageDialog(null,
                        NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.MustRestart"),
                        NbBundle.getMessage(OptionsPanel.class, "OptionsPanel.restartRequiredLabel.text"),
                        JOptionPane.WARNING_MESSAGE);
            }

            // Re-enable everything here
            resetUI();
            setEnabledStateForSharedConfiguration();
            pbTaskInProgress.setEnabled(false);
        }
    }

    void setEnabledStateForSharedConfiguration() {
        if (jRadioButtonAutomated.isSelected()) {
            if (sharedConfigCheckbox.isEnabled() && sharedConfigCheckbox.isSelected()) {
                setEnabledState(masterNodeCheckBox.isSelected());
            } else {
                // If we are in AIM mode and shared config is not enabled, allow this 
                setEnabledState(true);
            }
        }
    }

    void setEnabledState(boolean enabled) {
        bnAdvancedSettings.setEnabled(enabled);
        bnEditIngestSettings.setEnabled(enabled);
        bnFileExport.setEnabled(enabled);
        bnTestDatabase.setEnabled(enabled);
        bnTestMessageService.setEnabled(enabled);
        browseInputFolderButton.setEnabled(enabled);
        browseOutputFolderButton.setEnabled(enabled);
        browseSharedSettingsButton.setEnabled(sharedConfigCheckbox.isSelected() && jRadioButtonAutomated.isSelected());
        configButtonErrorTextField.setEnabled(enabled);
        inputPathTextField.setEnabled(enabled);
        jLabelInvalidImageFolder.setEnabled(enabled);
        jLabelInvalidResultsFolder.setEnabled(enabled);
        jLabelSelectInputFolder.setEnabled(enabled);
        jLabelSelectMode.setEnabled(enabled);
        jLabelSelectOutputFolder.setEnabled(enabled);
        jPanelIngestSettings.setEnabled(enabled);
        jPanelNodeType.setEnabled(enabled);
        jPanelSharedConfig.setEnabled(enabled);
        jRadioButtonAutomated.setEnabled(enabled);
        jRadioButtonCopyFilesMode.setEnabled(enabled);
        jRadioButtonReview.setEnabled(enabled);
        jRadioButtonStandalone.setEnabled(enabled);
        lbDatabaseSettings.setEnabled(enabled);
        lbMessageServiceSettings.setEnabled(enabled);
        lbTestDatabase.setEnabled(enabled);
        lbTestDbWarning.setEnabled(enabled);
        lbTestMessageService.setEnabled(enabled);
        lbTestMessageWarning.setEnabled(enabled);
        multiUserErrorTextField.setEnabled(enabled);
        outputPathTextField.setEnabled(enabled);
        pnDatabaseSettings.setEnabled(enabled);
        pnMessagingSettings.setEnabled(enabled);
        pnSolrSettings.setEnabled(enabled);
        restartRequiredNodeLabel.setEnabled(enabled);
        bnTestDatabase.setEnabled(enabled);
        bnTestMessageService.setEnabled(enabled);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnAdvancedSettings;
    private javax.swing.JButton bnCancel;
    private javax.swing.JButton bnEditIngestSettings;
    private javax.swing.JButton bnFileExport;
    private javax.swing.JButton bnSave;
    private javax.swing.JButton bnTestDatabase;
    private javax.swing.JButton bnTestMessageService;
    private javax.swing.JButton bnTestSolr;
    private javax.swing.JButton browseInputFolderButton;
    private javax.swing.JButton browseOutputFolderButton;
    private javax.swing.JButton browseSharedSettingsButton;
    private javax.swing.JCheckBox cbEnableMultiUser;
    private javax.swing.JTextField configButtonErrorTextField;
    private javax.swing.JButton downloadButton;
    private javax.swing.JTextField inputPathTextField;
    private javax.swing.JLabel jLabelAimDiagram;
    private javax.swing.JLabel jLabelCurrentTask;
    private javax.swing.JLabel jLabelInvalidImageFolder;
    private javax.swing.JLabel jLabelInvalidResultsFolder;
    private javax.swing.JLabel jLabelSelectInputFolder;
    private javax.swing.JLabel jLabelSelectMode;
    private javax.swing.JLabel jLabelSelectOutputFolder;
    private javax.swing.JLabel jLabelTaskDescription;
    private javax.swing.JPanel jPanelIngestSettings;
    private javax.swing.JPanel jPanelNodeType;
    private javax.swing.JPanel jPanelSharedConfig;
    private javax.swing.JRadioButton jRadioButtonAutomated;
    private javax.swing.JRadioButton jRadioButtonCopyFilesMode;
    private javax.swing.JRadioButton jRadioButtonReview;
    private javax.swing.JRadioButton jRadioButtonStandalone;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JLabel lbDatabaseSettings;
    private javax.swing.JLabel lbMessageServiceSettings;
    private javax.swing.JLabel lbSolrSettings;
    private javax.swing.JLabel lbTestDatabase;
    private javax.swing.JLabel lbTestDbWarning;
    private javax.swing.JLabel lbTestMessageService;
    private javax.swing.JLabel lbTestMessageWarning;
    private javax.swing.JLabel lbTestSolr;
    private javax.swing.JLabel lbTestSolrWarning;
    private javax.swing.JTabbedPane mainTabPane;
    private javax.swing.JCheckBox masterNodeCheckBox;
    private javax.swing.ButtonGroup modeRadioButtons;
    private javax.swing.JTextField multiUserErrorTextField;
    private javax.swing.JPanel multiUserPanel;
    private javax.swing.JLabel multiUserRestartLabel;
    private javax.swing.JPanel nodePanel;
    private javax.swing.JTextField outputPathTextField;
    private javax.swing.JProgressBar pbTaskInProgress;
    private javax.swing.JPanel pnDatabaseSettings;
    private javax.swing.JPanel pnMessagingSettings;
    private javax.swing.JPanel pnSolrSettings;
    private javax.swing.JLabel restartRequiredNodeLabel;
    private javax.swing.JCheckBox sharedConfigCheckbox;
    private javax.swing.JTextField sharedSettingsErrorTextField;
    private javax.swing.JTextField sharedSettingsTextField;
    private javax.swing.JTextField tbDbHostname;
    private javax.swing.JPasswordField tbDbPassword;
    private javax.swing.JTextField tbDbPort;
    private javax.swing.JTextField tbDbUsername;
    private javax.swing.JTextField tbMsgHostname;
    private javax.swing.JPasswordField tbMsgPassword;
    private javax.swing.JTextField tbMsgPort;
    private javax.swing.JTextField tbMsgUsername;
    private javax.swing.JTextField tbOops;
    private javax.swing.JTextField tbSolrHostname;
    private javax.swing.JTextField tbSolrPort;
    private javax.swing.JButton uploadButton;
    // End of variables declaration//GEN-END:variables
}
