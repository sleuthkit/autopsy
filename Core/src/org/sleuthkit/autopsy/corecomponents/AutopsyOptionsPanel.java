/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponents;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.GeneralFilter;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.report.ReportBranding;

/**
 * Options panel that allow users to set application preferences.
 */
@Messages({
    "AutopsyOptionsPanel.invalidImageFile.msg=The selected file was not able to be used as an agency logo.",
    "AutopsyOptionsPanel.invalidImageFile.title=Invalid Image File",
    "AutopsyOptionsPanel.memFieldValidationLabel.not64BitInstall.text=JVM memory settings only enabled for 64 bit version",
    "AutopsyOptionsPanel.memFieldValidationLabel.noValueEntered.text=No value entered",
    "AutopsyOptionsPanel.memFieldValidationLabel.invalidCharacters.text=Invalid characters, value must be a positive integer",
    "# {0} - minimumMemory",
    "AutopsyOptionsPanel.memFieldValidationLabel.underMinMemory.text=Value must be at least {0}GB",
    "# {0} - systemMemory",
    "AutopsyOptionsPanel.memFieldValidationLabel.overMaxMemory.text=Value must be less than the total system memory of {0}GB",
    "AutopsyOptionsPanel.memFieldValidationLabel.developerMode.text=Memory settings are not available while running in developer mode",
    "AutopsyOptionsPanel.agencyLogoPathFieldValidationLabel.invalidPath.text=Path is not valid.",
    "AutopsyOptionsPanel.agencyLogoPathFieldValidationLabel.invalidImageSpecified.text=Invalid image file specified.",
    "AutopsyOptionsPanel.agencyLogoPathFieldValidationLabel.pathNotSet.text=Agency logo path must be set.",
    "AutopsyOptionsPanel.logNumAlert.invalidInput.text=A positive integer is required here."
})

final class AutopsyOptionsPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private final JFileChooser fileChooser;
    private final TextFieldListener textFieldListener;
    private static final String ETC_FOLDER_NAME = "etc";
    private static final String CONFIG_FILE_EXTENSION = ".conf";
    private static final long ONE_BILLION = 1000000000L;  //used to roughly convert system memory from bytes to gigabytes
    private static final long MEGA_IN_GIGA = 1024; //used to convert memory settings saved as megabytes to gigabytes
    private static final int MIN_MEMORY_IN_GB = 2; //the enforced minimum memory in gigabytes
    private static final Logger logger = Logger.getLogger(AutopsyOptionsPanel.class.getName());
    private String initialMemValue = Long.toString(Runtime.getRuntime().maxMemory() / ONE_BILLION);

    /**
     * Instantiate the Autopsy options panel.
     */
    AutopsyOptionsPanel() {
        initComponents();
        fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.setFileFilter(new GeneralFilter(GeneralFilter.GRAPHIC_IMAGE_EXTS, GeneralFilter.GRAPHIC_IMG_DECR));
        if (!PlatformUtil.is64BitJVM() || Version.getBuildType() == Version.Type.DEVELOPMENT) {
            //32 bit JVM has a max heap size of 1.4 gb to 4 gb depending on OS
            //So disabling the setting of heap size when the JVM is not 64 bit 
            //Is the safest course of action
            //And the file won't exist in the install folder when running through netbeans
            memField.setEnabled(false);
        }
        systemMemoryTotal.setText(Long.toString(getSystemMemoryInGB()));

        textFieldListener = new TextFieldListener();
        agencyLogoPathField.getDocument().addDocumentListener(textFieldListener);
        logFileCount.setText(String.valueOf(UserPreferences.getLogFileCount()));
    }

    /**
     * Get the total system memory in gigabytes which exists on the machine
     * which the application is running.
     *
     * @return the total system memory
     */
    private long getSystemMemoryInGB() {
        long memorySize = ((com.sun.management.OperatingSystemMXBean) ManagementFactory
                .getOperatingSystemMXBean()).getTotalPhysicalMemorySize();
        return memorySize / ONE_BILLION;
    }

    /**
     * Gets the currently saved max java heap space in gigabytes.
     *
     * @return @throws IOException when unable to get a valid setting
     */
    private long getCurrentJvmMaxMemoryInGB() throws IOException {
        String currentXmx = getCurrentXmxValue();
        char units = '-';
        Long value = 0L;
        if (currentXmx.length() > 1) {
            units = currentXmx.charAt(currentXmx.length() - 1);
            value = Long.parseLong(currentXmx.substring(0, currentXmx.length() - 1));
        } else {
            throw new IOException("No memory setting found in String: " + currentXmx);
        }
        //some older .conf files might have the units as megabytes instead of gigabytes
        switch (units) {
            case 'g':
            case 'G':
                return value;
            case 'm':
            case 'M':
                return value / MEGA_IN_GIGA;
            default:
                throw new IOException("Units were not recognized as parsed: " + units);
        }
    }

    /*
     * The value currently saved in the conf file as the max java heap space
     * available to this application. Form will be an integer followed by a
     * character indicating units. Helper method for
     * getCurrentJvmMaxMemoryInGB()
     *
     * @return the saved value for the max java heap space
     *
     * @throws IOException if the conf file does not exist in either the user
     * directory or the install directory
     */
    private String getCurrentXmxValue() throws IOException {
        String[] settings;
        String currentSetting = "";
        File userConfFile = getUserFolderConfFile();
        if (!userConfFile.exists()) {
            settings = getDefaultsFromFileContents(readConfFile(getInstallFolderConfFile()));
        } else {
            settings = getDefaultsFromFileContents(readConfFile(userConfFile));
        }
        for (String option : settings) {
            if (option.startsWith("-J-Xmx")) {
                currentSetting = option.replace("-J-Xmx", "").trim();
            }
        }
        return currentSetting;
    }

    /**
     * Get the conf file from the install directory which stores the default
     * values for the settings.
     *
     * @return the file which has the applications default .conf file
     *
     * @throws IOException when the file does not exist.
     */
    private static File getInstallFolderConfFile() throws IOException {
        String confFileName = UserPreferences.getAppName() + CONFIG_FILE_EXTENSION;
        String installFolder = PlatformUtil.getInstallPath();
        File installFolderEtc = new File(installFolder, ETC_FOLDER_NAME);
        File installFolderConfigFile = new File(installFolderEtc, confFileName);
        if (!installFolderConfigFile.exists()) {
            throw new IOException("Conf file could not be found" + installFolderConfigFile.toString());
        }
        return installFolderConfigFile;
    }

    /**
     * Get the conf file from the directory which stores the currently in use
     * settings. Creates the directory for the file if the directory does not
     * exist.
     *
     * @return the file which has the applications current .conf file
     */
    private static File getUserFolderConfFile() {
        String confFileName = UserPreferences.getAppName() + CONFIG_FILE_EXTENSION;
        File userFolder = PlatformUtil.getUserDirectory();
        File userEtcFolder = new File(userFolder, ETC_FOLDER_NAME);
        if (!userEtcFolder.exists()) {
            userEtcFolder.mkdir();
        }
        return new File(userEtcFolder, confFileName);
    }

    /**
     * Take the conf file in the install directory and save a copy of it to the
     * user directory. The copy will be modified to include the current memory
     * setting.
     *
     * @throws IOException when unable to write a conf file or access the
     *                     install folders conf file
     */
    private void writeEtcConfFile() throws IOException {
        StringBuilder content = new StringBuilder();
        List<String> confFile = readConfFile(getInstallFolderConfFile());
        for (String line : confFile) {
            if (line.contains("-J-Xmx")) {
                String[] splitLine = line.split(" ");
                StringJoiner modifiedLine = new StringJoiner(" ");
                for (String piece : splitLine) {
                    if (piece.contains("-J-Xmx")) {
                        piece = "-J-Xmx" + memField.getText() + "g";
                    }
                    modifiedLine.add(piece);
                }
                content.append(modifiedLine.toString());
            } else {
                content.append(line);
            }
            content.append("\n");
        }
        Files.write(getUserFolderConfFile().toPath(), content.toString().getBytes());
    }

    /**
     * Reads a conf file line by line putting each line into a list of strings
     * which will be returned.
     *
     * @param configFile the .conf file which you wish to read.
     *
     * @return a list of strings with a string for each line in the conf file
     *         specified.
     */
    private static List<String> readConfFile(File configFile) {
        List<String> lines = new ArrayList<>();
        if (null != configFile) {
            Path filePath = configFile.toPath();
            Charset charset = Charset.forName("UTF-8");
            try {
                lines = Files.readAllLines(filePath, charset);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error reading config file contents. {}", configFile.getAbsolutePath());
            }
        }
        return lines;
    }

    /**
     * Find the string in the list of strings which contains the default options
     * settings and split it into an array of strings containing one element for
     * each setting specified.
     *
     * @param list a list of string representing lines of a .conf file
     *
     * @return an array of strings for each argument on the line which has the
     *         default options, returns an empty array of Strings if default
     *         options is not present.
     */
    private static String[] getDefaultsFromFileContents(List<String> list) {
        Optional<String> defaultSettings = list.stream().filter(line -> line.startsWith("default_options=")).findFirst();

        if (defaultSettings.isPresent()) {
            return defaultSettings.get().replace("default_options=", "").replaceAll("\"", "").split(" ");
        }
        return new String[]{};
    }

    /**
     * Load the saved user preferences.
     */
    void load() {
        boolean keepPreferredViewer = UserPreferences.keepPreferredContentViewer();
        keepCurrentViewerRB.setSelected(keepPreferredViewer);
        useBestViewerRB.setSelected(!keepPreferredViewer);
        dataSourcesHideKnownCB.setSelected(UserPreferences.hideKnownFilesInDataSourcesTree());
        viewsHideKnownCB.setSelected(UserPreferences.hideKnownFilesInViewsTree());
        dataSourcesHideSlackCB.setSelected(UserPreferences.hideSlackFilesInDataSourcesTree());
        viewsHideSlackCB.setSelected(UserPreferences.hideSlackFilesInViewsTree());
        boolean useLocalTime = UserPreferences.displayTimesInLocalTime();
        useLocalTimeRB.setSelected(useLocalTime);
        useGMTTimeRB.setSelected(!useLocalTime);
        String path = ModuleSettings.getConfigSetting(ReportBranding.MODULE_NAME, ReportBranding.AGENCY_LOGO_PATH_PROP);
        boolean useDefault = (path == null || path.isEmpty());
        defaultLogoRB.setSelected(useDefault);
        specifyLogoRB.setSelected(!useDefault);
        agencyLogoPathField.setEnabled(!useDefault);
        browseLogosButton.setEnabled(!useDefault);
        logFileCount.setText(String.valueOf(UserPreferences.getLogFileCount()));
        try {
            updateAgencyLogo(path);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error loading image from previously saved agency logo path", ex);
        }
        if (memField.isEnabled()) {
            try {
                initialMemValue = Long.toString(getCurrentJvmMaxMemoryInGB());
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Can't read current Jvm max memory setting from file", ex);
                memField.setEnabled(false);
            }
            memField.setText(initialMemValue);
        }
        
        valid(); //ensure the error messages are up to date
    }

    /**
     * Update the agency logo with the image specified by the path.
     *
     * @param path The path to the image.
     *
     * @throws IOException Thrown when there is a problem reading the file.
     */
    private void updateAgencyLogo(String path) throws IOException {
        agencyLogoPathField.setText(path);
        ImageIcon agencyLogoIcon = new ImageIcon();
        agencyLogoPreview.setText(NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.agencyLogoPreview.text"));
        if (!agencyLogoPathField.getText().isEmpty()) {
            File file = new File(agencyLogoPathField.getText());
            if (file.exists()) {
                BufferedImage image = ImageIO.read(file); //create it as an image first to support BMP files 
                if (image == null) {
                    throw new IOException("Unable to read file as a BufferedImage for file " + file.toString());
                }
                agencyLogoIcon = new ImageIcon(image.getScaledInstance(64, 64, 4));
                agencyLogoPreview.setText("");
            }
        }
        agencyLogoPreview.setIcon(agencyLogoIcon);
        agencyLogoPreview.repaint();
    }

    /**
     * Store the current user preferences.
     */
    void store() {
        UserPreferences.setKeepPreferredContentViewer(keepCurrentViewerRB.isSelected());
        UserPreferences.setHideKnownFilesInDataSourcesTree(dataSourcesHideKnownCB.isSelected());
        UserPreferences.setHideKnownFilesInViewsTree(viewsHideKnownCB.isSelected());
        UserPreferences.setHideSlackFilesInDataSourcesTree(dataSourcesHideSlackCB.isSelected());
        UserPreferences.setHideSlackFilesInViewsTree(viewsHideSlackCB.isSelected());
        UserPreferences.setDisplayTimesInLocalTime(useLocalTimeRB.isSelected());
        UserPreferences.setLogFileCount(Integer.parseInt(logFileCount.getText()));
        if (!agencyLogoPathField.getText().isEmpty()) {
            File file = new File(agencyLogoPathField.getText());
            if (file.exists()) {
                ModuleSettings.setConfigSetting(ReportBranding.MODULE_NAME, ReportBranding.AGENCY_LOGO_PATH_PROP, agencyLogoPathField.getText());
            }
        } else {
            ModuleSettings.setConfigSetting(ReportBranding.MODULE_NAME, ReportBranding.AGENCY_LOGO_PATH_PROP, "");
        }
        if (memField.isEnabled()) {  //if the field could of been changed we need to try and save it
            try {
                writeEtcConfFile();
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Unable to save config file to " + PlatformUtil.getUserDirectory() + "\\" + ETC_FOLDER_NAME, ex);
            }
        }
    }

    /**
     * Checks to see if the memory and agency logo field inputs are valid.
     *
     * @return True if valid; false otherwise.
     */
    boolean valid() {
        boolean valid = true;

        if (!isAgencyLogoPathValid()) {
            valid = false;
        }
        if (!isMemFieldValid()) {
            valid = false;
        }
        if (!isLogNumFieldValid()) {
            valid = false;
        }

        return valid;
    }

    /**
     * Validates the agency logo path to ensure it is valid.
     *
     * @return True if the default logo is being used or if the path is valid;
     *         otherwise false.
     */
    boolean isAgencyLogoPathValid() {
        boolean valid = true;

        if (defaultLogoRB.isSelected()) {
            agencyLogoPathFieldValidationLabel.setText("");
        } else {
            String agencyLogoPath = agencyLogoPathField.getText();
            if (agencyLogoPath.isEmpty()) {
                agencyLogoPathFieldValidationLabel.setText(Bundle.AutopsyOptionsPanel_agencyLogoPathFieldValidationLabel_pathNotSet_text());
                valid = false;
            } else {
                File file = new File(agencyLogoPathField.getText());
                if (file.exists() && file.isFile()) {
                    BufferedImage image;
                    try {  //ensure the image can be read
                        image = ImageIO.read(file); //create it as an image first to support BMP files
                        if (image == null) {
                            throw new IOException("Unable to read file as a BufferedImage for file " + file.toString());
                        }
                        agencyLogoPathFieldValidationLabel.setText("");
                    } catch (IOException | IndexOutOfBoundsException ignored) {
                        agencyLogoPathFieldValidationLabel.setText(Bundle.AutopsyOptionsPanel_agencyLogoPathFieldValidationLabel_invalidImageSpecified_text());
                        valid = false;
                    }
                } else {
                    agencyLogoPathFieldValidationLabel.setText(Bundle.AutopsyOptionsPanel_agencyLogoPathFieldValidationLabel_invalidPath_text());
                    valid = false;
                }
            }
        }

        return valid;
    }
    
    /**
     * Checks that if the mem field is enabled it has a valid value.
     *
     * @return true if the memfield is valid false if it is not
     */
    private boolean isMemFieldValid() {
        String memText = memField.getText();
        memFieldValidationLabel.setText("");
        if (!PlatformUtil.is64BitJVM()) {
            memFieldValidationLabel.setText(Bundle.AutopsyOptionsPanel_memFieldValidationLabel_not64BitInstall_text());
            //the panel should be valid when it is a 32 bit jvm because the memfield will be disabled.
            return true;
        }
        if (Version.getBuildType() == Version.Type.DEVELOPMENT) {
            memFieldValidationLabel.setText(Bundle.AutopsyOptionsPanel_memFieldValidationLabel_developerMode_text());
            //the panel should be valid when you are running in developer mode because the memfield will be disabled
            return true;
        }
        if (memText.length() == 0) {
            memFieldValidationLabel.setText(Bundle.AutopsyOptionsPanel_memFieldValidationLabel_noValueEntered_text());
            return false;
        }
        if (memText.replaceAll("[^\\d]", "").length() != memText.length()) {
            memFieldValidationLabel.setText(Bundle.AutopsyOptionsPanel_memFieldValidationLabel_invalidCharacters_text());
            return false;
        }
        int parsedInt = Integer.parseInt(memText);
        if (parsedInt < MIN_MEMORY_IN_GB) {
            memFieldValidationLabel.setText(Bundle.AutopsyOptionsPanel_memFieldValidationLabel_underMinMemory_text(MIN_MEMORY_IN_GB));
            return false;
        }
        if (parsedInt > getSystemMemoryInGB()) {
            memFieldValidationLabel.setText(Bundle.AutopsyOptionsPanel_memFieldValidationLabel_overMaxMemory_text(getSystemMemoryInGB()));
            return false;
        }
        return true;
    }

    /**
     * Check if the logFileCount field is valid.
     * 
     * @return true if the logFileCount is valid false if it is not
     */
    private boolean isLogNumFieldValid() {
        String count = logFileCount.getText();
        logNumAlert.setText("");
        try {
            int count_num = Integer.parseInt(count);
            if (count_num < 1) {
                logNumAlert.setText(Bundle.AutopsyOptionsPanel_logNumAlert_invalidInput_text());
                return false;
            }
        } catch (NumberFormatException e) {
            logNumAlert.setText(Bundle.AutopsyOptionsPanel_logNumAlert_invalidInput_text());
            return false;
        }
        return true;      
    }
    /**
     * Listens for registered text fields that have changed and fires a
     * PropertyChangeEvent accordingly.
     */
    private class TextFieldListener implements DocumentListener {

        @Override
        public void insertUpdate(DocumentEvent e) {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        fileSelectionButtonGroup = new javax.swing.ButtonGroup();
        displayTimesButtonGroup = new javax.swing.ButtonGroup();
        logoSourceButtonGroup = new javax.swing.ButtonGroup();
        jScrollPane1 = new javax.swing.JScrollPane();
        jPanel1 = new javax.swing.JPanel();
        logoPanel = new javax.swing.JPanel();
        agencyLogoPathField = new javax.swing.JTextField();
        browseLogosButton = new javax.swing.JButton();
        agencyLogoPreview = new javax.swing.JLabel();
        defaultLogoRB = new javax.swing.JRadioButton();
        specifyLogoRB = new javax.swing.JRadioButton();
        agencyLogoPathFieldValidationLabel = new javax.swing.JLabel();
        viewPanel = new javax.swing.JPanel();
        jLabelSelectFile = new javax.swing.JLabel();
        useBestViewerRB = new javax.swing.JRadioButton();
        keepCurrentViewerRB = new javax.swing.JRadioButton();
        jLabelHideKnownFiles = new javax.swing.JLabel();
        dataSourcesHideKnownCB = new javax.swing.JCheckBox();
        viewsHideKnownCB = new javax.swing.JCheckBox();
        jLabelHideSlackFiles = new javax.swing.JLabel();
        dataSourcesHideSlackCB = new javax.swing.JCheckBox();
        viewsHideSlackCB = new javax.swing.JCheckBox();
        jLabelTimeDisplay = new javax.swing.JLabel();
        useLocalTimeRB = new javax.swing.JRadioButton();
        useGMTTimeRB = new javax.swing.JRadioButton();
        runtimePanel = new javax.swing.JPanel();
        maxMemoryLabel = new javax.swing.JLabel();
        maxMemoryUnitsLabel = new javax.swing.JLabel();
        totalMemoryLabel = new javax.swing.JLabel();
        systemMemoryTotal = new javax.swing.JLabel();
        restartNecessaryWarning = new javax.swing.JLabel();
        memField = new javax.swing.JTextField();
        memFieldValidationLabel = new javax.swing.JLabel();
        maxMemoryUnitsLabel1 = new javax.swing.JLabel();
        maxLogFileCount = new javax.swing.JLabel();
        logFileCount = new javax.swing.JTextField();
        logNumAlert = new javax.swing.JTextField();

        setPreferredSize(new java.awt.Dimension(1022, 488));

        jScrollPane1.setBorder(null);
        jScrollPane1.setPreferredSize(new java.awt.Dimension(1022, 407));

        jPanel1.setPreferredSize(new java.awt.Dimension(1022, 407));

        logoPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.logoPanel.border.title"))); // NOI18N
        logoPanel.setPreferredSize(new java.awt.Dimension(533, 87));

        agencyLogoPathField.setText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.agencyLogoPathField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(browseLogosButton, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.browseLogosButton.text")); // NOI18N
        browseLogosButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseLogosButtonActionPerformed(evt);
            }
        });

        agencyLogoPreview.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        org.openide.awt.Mnemonics.setLocalizedText(agencyLogoPreview, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.agencyLogoPreview.text")); // NOI18N
        agencyLogoPreview.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        agencyLogoPreview.setMaximumSize(new java.awt.Dimension(64, 64));
        agencyLogoPreview.setMinimumSize(new java.awt.Dimension(64, 64));
        agencyLogoPreview.setPreferredSize(new java.awt.Dimension(64, 64));

        logoSourceButtonGroup.add(defaultLogoRB);
        org.openide.awt.Mnemonics.setLocalizedText(defaultLogoRB, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.defaultLogoRB.text")); // NOI18N
        defaultLogoRB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                defaultLogoRBActionPerformed(evt);
            }
        });

        logoSourceButtonGroup.add(specifyLogoRB);
        org.openide.awt.Mnemonics.setLocalizedText(specifyLogoRB, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.specifyLogoRB.text")); // NOI18N
        specifyLogoRB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                specifyLogoRBActionPerformed(evt);
            }
        });

        agencyLogoPathFieldValidationLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(agencyLogoPathFieldValidationLabel, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.agencyLogoPathFieldValidationLabel.text")); // NOI18N

        javax.swing.GroupLayout logoPanelLayout = new javax.swing.GroupLayout(logoPanel);
        logoPanel.setLayout(logoPanelLayout);
        logoPanelLayout.setHorizontalGroup(
            logoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, logoPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(logoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(specifyLogoRB)
                    .addComponent(defaultLogoRB))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(logoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(logoPanelLayout.createSequentialGroup()
                        .addComponent(agencyLogoPathField, javax.swing.GroupLayout.PREFERRED_SIZE, 259, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(browseLogosButton))
                    .addComponent(agencyLogoPathFieldValidationLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(agencyLogoPreview, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        logoPanelLayout.setVerticalGroup(
            logoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(logoPanelLayout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addGroup(logoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(defaultLogoRB)
                    .addComponent(agencyLogoPathFieldValidationLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(logoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(specifyLogoRB)
                    .addComponent(agencyLogoPathField)
                    .addComponent(browseLogosButton)))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, logoPanelLayout.createSequentialGroup()
                .addComponent(agencyLogoPreview, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        viewPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.viewPanel.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabelSelectFile, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jLabelSelectFile.text")); // NOI18N

        fileSelectionButtonGroup.add(useBestViewerRB);
        org.openide.awt.Mnemonics.setLocalizedText(useBestViewerRB, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.useBestViewerRB.text")); // NOI18N
        useBestViewerRB.setToolTipText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.useBestViewerRB.toolTipText")); // NOI18N
        useBestViewerRB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useBestViewerRBActionPerformed(evt);
            }
        });

        fileSelectionButtonGroup.add(keepCurrentViewerRB);
        org.openide.awt.Mnemonics.setLocalizedText(keepCurrentViewerRB, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.keepCurrentViewerRB.text")); // NOI18N
        keepCurrentViewerRB.setToolTipText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.keepCurrentViewerRB.toolTipText")); // NOI18N
        keepCurrentViewerRB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                keepCurrentViewerRBActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabelHideKnownFiles, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jLabelHideKnownFiles.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(dataSourcesHideKnownCB, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.dataSourcesHideKnownCB.text")); // NOI18N
        dataSourcesHideKnownCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSourcesHideKnownCBActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(viewsHideKnownCB, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.viewsHideKnownCB.text")); // NOI18N
        viewsHideKnownCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewsHideKnownCBActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabelHideSlackFiles, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jLabelHideSlackFiles.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(dataSourcesHideSlackCB, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.dataSourcesHideSlackCB.text")); // NOI18N
        dataSourcesHideSlackCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSourcesHideSlackCBActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(viewsHideSlackCB, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.viewsHideSlackCB.text")); // NOI18N
        viewsHideSlackCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewsHideSlackCBActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabelTimeDisplay, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jLabelTimeDisplay.text")); // NOI18N

        displayTimesButtonGroup.add(useLocalTimeRB);
        org.openide.awt.Mnemonics.setLocalizedText(useLocalTimeRB, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.useLocalTimeRB.text")); // NOI18N
        useLocalTimeRB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useLocalTimeRBActionPerformed(evt);
            }
        });

        displayTimesButtonGroup.add(useGMTTimeRB);
        org.openide.awt.Mnemonics.setLocalizedText(useGMTTimeRB, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.useGMTTimeRB.text")); // NOI18N
        useGMTTimeRB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useGMTTimeRBActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout viewPanelLayout = new javax.swing.GroupLayout(viewPanel);
        viewPanel.setLayout(viewPanelLayout);
        viewPanelLayout.setHorizontalGroup(
            viewPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, viewPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(viewPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(viewPanelLayout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addGroup(viewPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(viewPanelLayout.createSequentialGroup()
                                .addGroup(viewPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(useGMTTimeRB)
                                    .addComponent(keepCurrentViewerRB)
                                    .addComponent(useBestViewerRB)
                                    .addComponent(dataSourcesHideKnownCB)
                                    .addComponent(viewsHideKnownCB))
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addGroup(viewPanelLayout.createSequentialGroup()
                                .addGroup(viewPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(dataSourcesHideSlackCB)
                                    .addComponent(viewsHideSlackCB)
                                    .addComponent(useLocalTimeRB))
                                .addContainerGap(158, Short.MAX_VALUE))))
                    .addGroup(viewPanelLayout.createSequentialGroup()
                        .addGroup(viewPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabelHideSlackFiles)
                            .addComponent(jLabelTimeDisplay)
                            .addComponent(jLabelHideKnownFiles)
                            .addComponent(jLabelSelectFile))
                        .addGap(30, 30, 30))))
        );
        viewPanelLayout.setVerticalGroup(
            viewPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, viewPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabelSelectFile)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(useBestViewerRB)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(keepCurrentViewerRB)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabelHideKnownFiles)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(dataSourcesHideKnownCB)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(viewsHideKnownCB)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabelHideSlackFiles)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(dataSourcesHideSlackCB)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(viewsHideSlackCB)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabelTimeDisplay)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(useLocalTimeRB)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(useGMTTimeRB))
        );

        runtimePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.runtimePanel.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(maxMemoryLabel, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.maxMemoryLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(maxMemoryUnitsLabel, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.maxMemoryUnitsLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(totalMemoryLabel, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.totalMemoryLabel.text")); // NOI18N

        systemMemoryTotal.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);

        restartNecessaryWarning.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(restartNecessaryWarning, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.restartNecessaryWarning.text")); // NOI18N

        memField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        memField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                memFieldKeyReleased(evt);
            }
        });

        memFieldValidationLabel.setForeground(new java.awt.Color(255, 0, 0));

        org.openide.awt.Mnemonics.setLocalizedText(maxMemoryUnitsLabel1, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.maxMemoryUnitsLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(maxLogFileCount, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.maxLogFileCount.text")); // NOI18N

        logFileCount.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        logFileCount.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                logFileCountKeyReleased(evt);
            }
        });

        logNumAlert.setEditable(false);
        logNumAlert.setFont(logNumAlert.getFont().deriveFont(logNumAlert.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        logNumAlert.setForeground(new java.awt.Color(255, 0, 0));
        logNumAlert.setText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.logNumAlert.text")); // NOI18N
        logNumAlert.setBorder(null);

        javax.swing.GroupLayout runtimePanelLayout = new javax.swing.GroupLayout(runtimePanel);
        runtimePanel.setLayout(runtimePanelLayout);
        runtimePanelLayout.setHorizontalGroup(
            runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(runtimePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(runtimePanelLayout.createSequentialGroup()
                        .addComponent(totalMemoryLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(systemMemoryTotal, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(6, 6, 6)
                        .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(runtimePanelLayout.createSequentialGroup()
                                .addComponent(maxMemoryUnitsLabel1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(restartNecessaryWarning, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addGroup(runtimePanelLayout.createSequentialGroup()
                                .addComponent(maxMemoryUnitsLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(memFieldValidationLabel)
                                .addGap(0, 0, Short.MAX_VALUE))))
                    .addGroup(runtimePanelLayout.createSequentialGroup()
                        .addComponent(maxMemoryLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(memField, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(runtimePanelLayout.createSequentialGroup()
                        .addComponent(maxLogFileCount)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(logFileCount, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(logNumAlert)))
                .addContainerGap())
        );

        runtimePanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {maxLogFileCount, maxMemoryLabel, totalMemoryLabel});

        runtimePanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {logFileCount, memField});

        runtimePanelLayout.setVerticalGroup(
            runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(runtimePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(totalMemoryLabel)
                    .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(restartNecessaryWarning)
                        .addComponent(maxMemoryUnitsLabel1))
                    .addComponent(systemMemoryTotal, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(maxMemoryLabel)
                        .addComponent(memField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(maxMemoryUnitsLabel))
                    .addComponent(memFieldValidationLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(maxLogFileCount)
                    .addComponent(logFileCount, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(logNumAlert, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(logoPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 1010, Short.MAX_VALUE)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(viewPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(runtimePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(viewPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(runtimePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(logoPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jScrollPane1.setViewportView(jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 479, Short.MAX_VALUE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void logFileCountKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_logFileCountKeyReleased
        String count = logFileCount.getText();
        if (count.equals(String.valueOf(UserPreferences.getDefaultLogFileCount()))) {
            //if it is still the default value don't fire change
            return;
        }
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_logFileCountKeyReleased

    private void memFieldKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_memFieldKeyReleased
        String memText = memField.getText();
        if (memText.equals(initialMemValue)) {
            //if it is still the initial value don't fire change
            return;
        }
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_memFieldKeyReleased

    private void useGMTTimeRBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useGMTTimeRBActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_useGMTTimeRBActionPerformed

    private void useLocalTimeRBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useLocalTimeRBActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_useLocalTimeRBActionPerformed

    private void viewsHideSlackCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewsHideSlackCBActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_viewsHideSlackCBActionPerformed

    private void dataSourcesHideSlackCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSourcesHideSlackCBActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_dataSourcesHideSlackCBActionPerformed

    private void viewsHideKnownCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewsHideKnownCBActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_viewsHideKnownCBActionPerformed

    private void dataSourcesHideKnownCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSourcesHideKnownCBActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_dataSourcesHideKnownCBActionPerformed

    private void keepCurrentViewerRBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_keepCurrentViewerRBActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_keepCurrentViewerRBActionPerformed

    private void useBestViewerRBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useBestViewerRBActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_useBestViewerRBActionPerformed

    private void specifyLogoRBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_specifyLogoRBActionPerformed
        agencyLogoPathField.setEnabled(true);
        browseLogosButton.setEnabled(true);
        try {
            if (agencyLogoPathField.getText().isEmpty()) {
                String path = ModuleSettings.getConfigSetting(ReportBranding.MODULE_NAME, ReportBranding.AGENCY_LOGO_PATH_PROP);
                if (path != null && !path.isEmpty()) {
                    updateAgencyLogo(path);
                }
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error loading image from previously saved agency logo path.", ex);
        }
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_specifyLogoRBActionPerformed

    private void defaultLogoRBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_defaultLogoRBActionPerformed
        agencyLogoPathField.setEnabled(false);
        browseLogosButton.setEnabled(false);
        try {
            updateAgencyLogo("");
        } catch (IOException ex) {
            // This should never happen since we're not reading from a file.
            logger.log(Level.SEVERE, "Unexpected error occurred while updating the agency logo.", ex);
        }
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_defaultLogoRBActionPerformed

    private void browseLogosButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseLogosButtonActionPerformed
        String oldLogoPath = agencyLogoPathField.getText();
        int returnState = fileChooser.showOpenDialog(this);
        if (returnState == JFileChooser.APPROVE_OPTION) {
            String path = fileChooser.getSelectedFile().getPath();
            try {
                updateAgencyLogo(path);
                firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            } catch (IOException | IndexOutOfBoundsException ex) {
                JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(this.getClass(),
                        "AutopsyOptionsPanel.invalidImageFile.msg"),
                    NbBundle.getMessage(this.getClass(), "AutopsyOptionsPanel.invalidImageFile.title"),
                    JOptionPane.ERROR_MESSAGE);
                try {
                    updateAgencyLogo(oldLogoPath); //restore previous setting if new one is invalid
                } catch (IOException ex1) {
                    logger.log(Level.WARNING, "Error loading image from previously saved agency logo path", ex1);
                }
            }
        }
    }//GEN-LAST:event_browseLogosButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField agencyLogoPathField;
    private javax.swing.JLabel agencyLogoPathFieldValidationLabel;
    private javax.swing.JLabel agencyLogoPreview;
    private javax.swing.JButton browseLogosButton;
    private javax.swing.JCheckBox dataSourcesHideKnownCB;
    private javax.swing.JCheckBox dataSourcesHideSlackCB;
    private javax.swing.JRadioButton defaultLogoRB;
    private javax.swing.ButtonGroup displayTimesButtonGroup;
    private javax.swing.ButtonGroup fileSelectionButtonGroup;
    private javax.swing.JLabel jLabelHideKnownFiles;
    private javax.swing.JLabel jLabelHideSlackFiles;
    private javax.swing.JLabel jLabelSelectFile;
    private javax.swing.JLabel jLabelTimeDisplay;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JRadioButton keepCurrentViewerRB;
    private javax.swing.JTextField logFileCount;
    private javax.swing.JTextField logNumAlert;
    private javax.swing.JPanel logoPanel;
    private javax.swing.ButtonGroup logoSourceButtonGroup;
    private javax.swing.JLabel maxLogFileCount;
    private javax.swing.JLabel maxMemoryLabel;
    private javax.swing.JLabel maxMemoryUnitsLabel;
    private javax.swing.JLabel maxMemoryUnitsLabel1;
    private javax.swing.JTextField memField;
    private javax.swing.JLabel memFieldValidationLabel;
    private javax.swing.JLabel restartNecessaryWarning;
    private javax.swing.JPanel runtimePanel;
    private javax.swing.JRadioButton specifyLogoRB;
    private javax.swing.JLabel systemMemoryTotal;
    private javax.swing.JLabel totalMemoryLabel;
    private javax.swing.JRadioButton useBestViewerRB;
    private javax.swing.JRadioButton useGMTTimeRB;
    private javax.swing.JRadioButton useLocalTimeRB;
    private javax.swing.JPanel viewPanel;
    private javax.swing.JCheckBox viewsHideKnownCB;
    private javax.swing.JCheckBox viewsHideSlackCB;
    // End of variables declaration//GEN-END:variables

}
