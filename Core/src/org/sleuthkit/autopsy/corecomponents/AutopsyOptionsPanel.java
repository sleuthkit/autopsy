/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.types.Commandline;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.GeneralFilter;
import org.sleuthkit.autopsy.machinesettings.UserMachinePreferences;
import org.sleuthkit.autopsy.machinesettings.UserMachinePreferencesException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.guiutils.JFileChooserFactory;
import org.sleuthkit.autopsy.machinesettings.UserMachinePreferences.TempDirChoice;
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
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class AutopsyOptionsPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_HEAP_DUMP_FILE_FIELD = "";
    private JFileChooser logoFileChooser;
    private JFileChooser tempDirChooser;
    private static final String ETC_FOLDER_NAME = "etc";
    private static final String CONFIG_FILE_EXTENSION = ".conf";
    private static final long ONE_BILLION = 1000000000L;  //used to roughly convert system memory from bytes to gigabytes
    private static final int MEGA_IN_GIGA = 1024; //used to convert memory settings saved as megabytes to gigabytes
    private static final int JVM_MEMORY_STEP_SIZE_MB = 512;
    private static final int MIN_MEMORY_IN_GB = 2; //the enforced minimum memory in gigabytes
    private static final Logger logger = Logger.getLogger(AutopsyOptionsPanel.class.getName());
    private String initialMemValue = Long.toString(Runtime.getRuntime().maxMemory() / ONE_BILLION);
    
    private final ReportBranding reportBranding;
    private JFileChooser heapFileChooser;
    
    private final JFileChooserFactory logoChooserHelper = new JFileChooserFactory();
    private final JFileChooserFactory heapChooserHelper = new JFileChooserFactory();
    private final JFileChooserFactory tempChooserHelper = new JFileChooserFactory();

    /**
     * Instantiate the Autopsy options panel.
     */
    AutopsyOptionsPanel() {
        initComponents();
        if (!isJVMHeapSettingsCapable()) {
            //32 bit JVM has a max heap size of 1.4 gb to 4 gb depending on OS
            //So disabling the setting of heap size when the JVM is not 64 bit 
            //Is the safest course of action
            //And the file won't exist in the install folder when running through netbeans
            memField.setEnabled(false);
            heapDumpFileField.setEnabled(false);
            heapDumpBrowseButton.setEnabled(false);
            solrMaxHeapSpinner.setEnabled(false);
        }
        systemMemoryTotal.setText(Long.toString(getSystemMemoryInGB()));
        // The cast to int in the following is to ensure that the correct SpinnerNumberModel
        // constructor is called.
        solrMaxHeapSpinner.setModel(new javax.swing.SpinnerNumberModel(UserPreferences.getMaxSolrVMSize(),
                JVM_MEMORY_STEP_SIZE_MB, ((int) getSystemMemoryInGB()) * MEGA_IN_GIGA, JVM_MEMORY_STEP_SIZE_MB));

        agencyLogoPathField.getDocument().addDocumentListener(new TextFieldListener(null));
        heapDumpFileField.getDocument().addDocumentListener(new TextFieldListener(this::isHeapPathValid));
        tempCustomField.getDocument().addDocumentListener(new TextFieldListener(this::evaluateTempDirState));
        logFileCount.setText(String.valueOf(UserPreferences.getLogFileCount()));
        
        reportBranding = new ReportBranding();
    }
    
    /**
     * Returns whether or not the jvm runtime heap settings can effectively be changed.
     * @return Whether or not the jvm runtime heap settings can effectively be changed.
     */
    private static boolean isJVMHeapSettingsCapable() {
        return PlatformUtil.is64BitJVM() && Version.getBuildType() != Version.Type.DEVELOPMENT;
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
     * @param The conf file memory value (i.e. 4G).
     *
     * @return The value in gigabytes.
     * @throws IOException when unable to get a valid setting
     */
    private long getCurrentJvmMaxMemoryInGB(String confFileMemValue) throws IOException {
        char units = '-';
        Long value = 0L;
        if (confFileMemValue.length() > 1) {
            units = confFileMemValue.charAt(confFileMemValue.length() - 1);
            try {
                value = Long.parseLong(confFileMemValue.substring(0, confFileMemValue.length() - 1));
            } catch (NumberFormatException ex) {
                throw new IOException("Unable to properly parse memory number.", ex);
            }
        } else {
            throw new IOException("No memory setting found in String: " + confFileMemValue);
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
    
    private static final String JVM_SETTINGS_REGEX_PARAM = "options";
    private static final String JVM_SETTINGS_REGEX_STR = "^\\s*default_options\\s*=\\s*\"?(?<" + JVM_SETTINGS_REGEX_PARAM + ">.+?)\"?\\s*$";
    private static final Pattern JVM_SETTINGS_REGEX = Pattern.compile(JVM_SETTINGS_REGEX_STR);
    private static final String XMX_REGEX_PARAM = "mem";
    private static final String XMX_REGEX_STR = "^\\s*\\-J\\-Xmx(?<" + XMX_REGEX_PARAM + ">.+?)\\s*$";
    private static final Pattern XMX_REGEX = Pattern.compile(XMX_REGEX_STR);
    private static final String HEAP_DUMP_REGEX_PARAM = "path";
    private static final String HEAP_DUMP_REGEX_STR = "^\\s*\\-J\\-XX:HeapDumpPath=(\\\")?\\s*(?<" + HEAP_DUMP_REGEX_PARAM + ">.+?)\\s*(\\\")?$";
    private static final Pattern HEAP_DUMP_REGEX = Pattern.compile(HEAP_DUMP_REGEX_STR);
    
    /**
     * Parse the autopsy conf file line.  If the line is the default_options line, 
     * then replaces with current memory and heap path value.  Otherwise, returns 
     * the line provided as parameter.
     * 
     * @param line The line.
     * @param memText The text to add as an argument to be used as memory with -J-Xmx.
     * @param heapText The text to add as an argument to be used as the heap dump path with
     * -J-XX:HeapDumpPath.
     * @return The line modified to contain memory and heap arguments.
     */
    private static String updateConfLine(String line, String memText, String heapText) {
        Matcher match = JVM_SETTINGS_REGEX.matcher(line);
        if (match.find()) {
            // split on command line arguments
            String[] parsedArgs = Commandline.translateCommandline(match.group(JVM_SETTINGS_REGEX_PARAM));
            
            String memString = "-J-Xmx" + memText.replaceAll("[^\\d]", "") + "g";
            
            // only add in heap path argument if a heap path is specified
            String heapString = null;
            if (StringUtils.isNotBlank(heapText)) {
                while (heapText.endsWith("\\") && heapText.length() > 0) {
                    heapText = heapText.substring(0, heapText.length() - 1);
                }
                
                heapString = String.format("-J-XX:HeapDumpPath=\"%s\"", heapText);
            }
            
            Stream<String> argsNoMemHeap = Stream.of(parsedArgs)
                    // remove saved version of memory and heap dump path
                    .filter(s -> !s.matches(XMX_REGEX_STR) && !s.matches(HEAP_DUMP_REGEX_STR));
                    
            String newArgs = Stream.concat(argsNoMemHeap, Stream.of(memString, heapString))
                    .filter(s -> s != null)
                    .collect(Collectors.joining(" "));
            
            return String.format("default_options=\"%s\"", newArgs);
        };
            
        return line;
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
        String fileText = readConfFile(getInstallFolderConfFile()).stream()
                .map((line) -> updateConfLine(line, memField.getText(), heapDumpFileField.getText()))
                .collect(Collectors.joining("\n"));
        
        FileUtils.writeStringToFile(getUserFolderConfFile(), fileText, "UTF-8");
    }
    
    
    /**
     * Values for configuration located in the /etc/\*.conf file.
     */
    private static class ConfValues {
        private final String XmxVal;
        private final String heapDumpPath;

        /**
         * Main constructor.
         * @param XmxVal The heap memory size.
         * @param heapDumpPath The heap dump path.
         */
        ConfValues(String XmxVal, String heapDumpPath) {
            this.XmxVal = XmxVal;
            this.heapDumpPath = heapDumpPath;
        }

        /**
         * Returns the heap memory value specified in the conf file.
         * @return The heap memory value specified in the conf file.
         */
        String getXmxVal() {
            return XmxVal;
        }

        /**
         * Returns path to the heap dump specified in the conf file.
         * @return Path to the heap dump specified in the conf file.
         */
        String getHeapDumpPath() {
            return heapDumpPath;
        }
    }
    
    /**
     * Retrieve the /etc/\*.conf file values pertinent to settings.
     * @return The conf file values.
     * @throws IOException 
     */
    private ConfValues getEtcConfValues() throws IOException {
        File userConfFile = getUserFolderConfFile();
        String[] args = userConfFile.exists() ? 
                getDefaultsFromFileContents(readConfFile(userConfFile)) : 
                getDefaultsFromFileContents(readConfFile(getInstallFolderConfFile()));
        
        String heapFile = "";
        String memSize = "";

        for (String arg : args) {
            Matcher memMatch = XMX_REGEX.matcher(arg);
            if (memMatch.find()) {
                memSize = memMatch.group(XMX_REGEX_PARAM);
                continue;
            }

            Matcher heapFileMatch = HEAP_DUMP_REGEX.matcher(arg);
            if (heapFileMatch.find()) {
                heapFile = heapFileMatch.group(HEAP_DUMP_REGEX_PARAM);
                continue;
            }
        }
        
        return new ConfValues(memSize, heapFile);
    }
    
    
    
    /**
     * Checks current heap path value to see if it is valid, and displays an error message if invalid.
     * @return True if the heap path is valid.
     */
    @Messages({
        "AutopsyOptionsPanel_isHeapPathValid_selectValidDirectory=Please select an existing directory.",
        "AutopsyOptionsPanel_isHeapPathValid_developerMode=Cannot change heap dump path while in developer mode.",
        "AutopsyOptionsPanel_isHeapPathValid_not64BitMachine=Changing heap dump path settings only enabled for 64 bit version.",
        "AutopsyOPtionsPanel_isHeapPathValid_illegalCharacters=Please select a path with no quotes."
    })
    private boolean isHeapPathValid() {
        if (Version.getBuildType() == Version.Type.DEVELOPMENT) {
            heapFieldValidationLabel.setVisible(true);
            heapFieldValidationLabel.setText(Bundle.AutopsyOptionsPanel_isHeapPathValid_developerMode());
            return true;
        } 
        
        if (!PlatformUtil.is64BitJVM()) {
            heapFieldValidationLabel.setVisible(true);
            heapFieldValidationLabel.setText(Bundle.AutopsyOptionsPanel_isHeapPathValid_not64BitMachine());
            return true;
        }
        
        //allow blank field as the default will be used
        if (StringUtils.isNotBlank(heapDumpFileField.getText())) { 
            String heapText = heapDumpFileField.getText().trim();
            if (heapText.contains("\"") || heapText.contains("'")) {
                heapFieldValidationLabel.setVisible(true);
                heapFieldValidationLabel.setText(Bundle.AutopsyOPtionsPanel_isHeapPathValid_illegalCharacters());
                return false;
            }
            
            File curHeapFile = new File(heapText);
            if (!curHeapFile.exists() || !curHeapFile.isDirectory()) {
                heapFieldValidationLabel.setVisible(true);
                heapFieldValidationLabel.setText(Bundle.AutopsyOptionsPanel_isHeapPathValid_selectValidDirectory());
                return false;
            }
        }
            
        heapFieldValidationLabel.setVisible(false);
        heapFieldValidationLabel.setText("");
        return true;
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
        Optional<String> defaultSettings = list.stream()
                .filter(line -> line.matches(JVM_SETTINGS_REGEX_STR))
                .findFirst();

        if (defaultSettings.isPresent()) {
            Matcher match = JVM_SETTINGS_REGEX.matcher(defaultSettings.get());
            if (match.find()) {
                return Commandline.translateCommandline(match.group(JVM_SETTINGS_REGEX_PARAM));
            }
        }
        
        return new String[]{};
    }
    
    private void evaluateTempDirState() {
        boolean caseOpen = Case.isCaseOpen();
        boolean customSelected = tempCustomRadio.isSelected();
        
        tempDirectoryBrowseButton.setEnabled(!caseOpen && customSelected);
        tempCustomField.setEnabled(!caseOpen && customSelected);
        
        tempOnCustomNoPath.setVisible(customSelected && StringUtils.isBlank(tempCustomField.getText()));
    }

    /**
     * Load the saved user preferences.
     */
    void load() {
        String path = reportBranding.getAgencyLogoPath();
        boolean useDefault = (path == null || path.isEmpty());
        defaultLogoRB.setSelected(useDefault);
        specifyLogoRB.setSelected(!useDefault);
        agencyLogoPathField.setEnabled(!useDefault);
        browseLogosButton.setEnabled(!useDefault);
        
        tempCustomField.setText(UserMachinePreferences.getCustomTempDirectory());
        switch (UserMachinePreferences.getTempDirChoice()) {
            case CASE: 
                tempCaseRadio.setSelected(true);
                break;
            case CUSTOM: 
                tempCustomRadio.setSelected(true);
                break;
            default:
            case SYSTEM: 
                tempLocalRadio.setSelected(true);
                break;
        }
        
        evaluateTempDirState();
        
        logFileCount.setText(String.valueOf(UserPreferences.getLogFileCount()));
        solrMaxHeapSpinner.setValue(UserPreferences.getMaxSolrVMSize());
        try {
            updateAgencyLogo(path);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error loading image from previously saved agency logo path", ex);
        }
        
        boolean confLoaded = false;
        if (isJVMHeapSettingsCapable()) {
            try {
                ConfValues confValues = getEtcConfValues();
                heapDumpFileField.setText(confValues.getHeapDumpPath());
                initialMemValue = Long.toString(getCurrentJvmMaxMemoryInGB(confValues.getXmxVal()));
                confLoaded = true;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Can't read current Jvm max memory setting from file", ex);
                memField.setEnabled(false);
                heapDumpFileField.setText(DEFAULT_HEAP_DUMP_FILE_FIELD);
            }
            memField.setText(initialMemValue);
        }
        
        heapDumpBrowseButton.setEnabled(confLoaded);
        heapDumpFileField.setEnabled(confLoaded);
        
        setTempDirEnabled();
        valid(); //ensure the error messages are up to date
    }

    private void setTempDirEnabled() {
        boolean enabled = !Case.isCaseOpen();
        
        this.tempCaseRadio.setEnabled(enabled);
        this.tempCustomRadio.setEnabled(enabled);
        this.tempLocalRadio.setEnabled(enabled);
        
        this.tempDirectoryWarningLabel.setVisible(!enabled);
        evaluateTempDirState();
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

    @Messages({
        "AutopsyOptionsPanel_storeTempDir_onError_title=Error Saving Temporary Directory",
        "# {0} - path",
        "AutopsyOptionsPanel_storeTempDir_onError_description=There was an error creating the temporary directory on the filesystem at: {0}.",
        "AutopsyOptionsPanel_storeTempDir_onChoiceError_title=Error Saving Temporary Directory Choice",
        "AutopsyOptionsPanel_storeTempDir_onChoiceError_description=There was an error updating temporary directory choice selection.",})
    private void storeTempDir() {
        String tempDirectoryPath = tempCustomField.getText();
        if (!UserMachinePreferences.getCustomTempDirectory().equals(tempDirectoryPath)) {
            try {
                UserMachinePreferences.setCustomTempDirectory(tempDirectoryPath);
            } catch (UserMachinePreferencesException ex) {
                logger.log(Level.WARNING, "There was an error creating the temporary directory defined by the user: " + tempDirectoryPath, ex);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            String.format("<html>%s</html>", Bundle.AutopsyOptionsPanel_storeTempDir_onError_description(tempDirectoryPath)),
                            Bundle.AutopsyOptionsPanel_storeTempDir_onError_title(),
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        }
        
        TempDirChoice choice;
        if (tempCaseRadio.isSelected()) {
            choice = TempDirChoice.CASE;
        } else if (tempCustomRadio.isSelected()) {
            choice = TempDirChoice.CUSTOM;
        } else {
            choice = TempDirChoice.SYSTEM;
        }
        
        if (!choice.equals(UserMachinePreferences.getTempDirChoice())) {
            try {
                UserMachinePreferences.setTempDirChoice(choice);
            } catch (UserMachinePreferencesException ex) {
                logger.log(Level.WARNING, "There was an error updating choice to: " + choice.name(), ex);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            String.format("<html>%s</html>", Bundle.AutopsyOptionsPanel_storeTempDir_onChoiceError_description()),
                            Bundle.AutopsyOptionsPanel_storeTempDir_onChoiceError_title(),
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        }
    }

    /**
     * Store the current user preferences.
     */
    void store() {
        UserPreferences.setLogFileCount(Integer.parseInt(logFileCount.getText()));
        storeTempDir();

        if (!agencyLogoPathField.getText().isEmpty()) {
            File file = new File(agencyLogoPathField.getText());
            if (file.exists()) {
                reportBranding.setAgencyLogoPath(agencyLogoPathField.getText());
            }
        } else {
            reportBranding.setAgencyLogoPath("");
        }
        UserPreferences.setMaxSolrVMSize((int) solrMaxHeapSpinner.getValue());
        if (isJVMHeapSettingsCapable()) {  //if the field could of been changed we need to try and save it
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
        boolean agencyValid = isAgencyLogoPathValid();
        boolean memFieldValid = isMemFieldValid();
        boolean logNumValid = isLogNumFieldValid();
        boolean heapPathValid = isHeapPathValid();
        
        return agencyValid && memFieldValid && logNumValid && heapPathValid;
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
     * PropertyChangeEvent accordingly as well as firing an optional additional listener.
     */
    private class TextFieldListener implements DocumentListener {
        private final Runnable onChange;

        
        /**
         * Main constructor.
         * @param onChange Additional listener for change events.
         */
        TextFieldListener(Runnable onChange) {
            this.onChange = onChange;
        }
        
        private void baseOnChange() {
            if (onChange != null) {
                onChange.run();    
            }
            
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
        
        @Override
        public void changedUpdate(DocumentEvent e) {
            baseOnChange();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            baseOnChange();
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            baseOnChange();
        }
        
        
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        fileSelectionButtonGroup = new javax.swing.ButtonGroup();
        displayTimesButtonGroup = new javax.swing.ButtonGroup();
        logoSourceButtonGroup = new javax.swing.ButtonGroup();
        tempDirChoiceGroup = new javax.swing.ButtonGroup();
        jScrollPane1 = new javax.swing.JScrollPane();
        javax.swing.JPanel mainPanel = new javax.swing.JPanel();
        logoPanel = new javax.swing.JPanel();
        agencyLogoPathField = new javax.swing.JTextField();
        browseLogosButton = new javax.swing.JButton();
        agencyLogoPreview = new javax.swing.JLabel();
        defaultLogoRB = new javax.swing.JRadioButton();
        specifyLogoRB = new javax.swing.JRadioButton();
        agencyLogoPathFieldValidationLabel = new javax.swing.JLabel();
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
        maxSolrMemoryLabel = new javax.swing.JLabel();
        maxMemoryUnitsLabel2 = new javax.swing.JLabel();
        solrMaxHeapSpinner = new javax.swing.JSpinner();
        solrJVMHeapWarning = new javax.swing.JLabel();
        heapFileLabel = new javax.swing.JLabel();
        heapDumpFileField = new javax.swing.JTextField();
        heapDumpBrowseButton = new javax.swing.JButton();
        heapFieldValidationLabel = new javax.swing.JLabel();
        tempDirectoryPanel = new javax.swing.JPanel();
        tempCustomField = new javax.swing.JTextField();
        tempDirectoryBrowseButton = new javax.swing.JButton();
        tempDirectoryWarningLabel = new javax.swing.JLabel();
        tempLocalRadio = new javax.swing.JRadioButton();
        tempCaseRadio = new javax.swing.JRadioButton();
        tempCustomRadio = new javax.swing.JRadioButton();
        tempOnCustomNoPath = new javax.swing.JLabel();
        rdpPanel = new javax.swing.JPanel();
        javax.swing.JScrollPane sizingScrollPane = new javax.swing.JScrollPane();
        javax.swing.JTextPane sizingTextPane = new javax.swing.JTextPane();
        javax.swing.Box.Filler filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));
        javax.swing.Box.Filler filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 32767));

        jScrollPane1.setBorder(null);

        mainPanel.setMinimumSize(new java.awt.Dimension(648, 382));
        mainPanel.setLayout(new java.awt.GridBagLayout());

        logoPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.logoPanel.border.title"))); // NOI18N

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
                    .addComponent(agencyLogoPathFieldValidationLabel)
                    .addGroup(logoPanelLayout.createSequentialGroup()
                        .addComponent(agencyLogoPathField, javax.swing.GroupLayout.PREFERRED_SIZE, 270, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(browseLogosButton)))
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

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        mainPanel.add(logoPanel, gridBagConstraints);

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
        logNumAlert.setForeground(new java.awt.Color(255, 0, 0));
        logNumAlert.setText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.logNumAlert.text")); // NOI18N
        logNumAlert.setBorder(null);

        org.openide.awt.Mnemonics.setLocalizedText(maxSolrMemoryLabel, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.maxSolrMemoryLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(maxMemoryUnitsLabel2, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.maxMemoryUnitsLabel2.text")); // NOI18N

        solrMaxHeapSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                solrMaxHeapSpinnerStateChanged(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(solrJVMHeapWarning, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.solrJVMHeapWarning.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(heapFileLabel, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.heapFileLabel.text")); // NOI18N

        heapDumpFileField.setText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.heapDumpFileField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(heapDumpBrowseButton, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.heapDumpBrowseButton.text")); // NOI18N
        heapDumpBrowseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                heapDumpBrowseButtonActionPerformed(evt);
            }
        });

        heapFieldValidationLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(heapFieldValidationLabel, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.heapFieldValidationLabel.text")); // NOI18N

        javax.swing.GroupLayout runtimePanelLayout = new javax.swing.GroupLayout(runtimePanel);
        runtimePanel.setLayout(runtimePanelLayout);
        runtimePanelLayout.setHorizontalGroup(
            runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(runtimePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(runtimePanelLayout.createSequentialGroup()
                        .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(totalMemoryLabel)
                            .addComponent(maxSolrMemoryLabel)
                            .addComponent(maxMemoryLabel)
                            .addComponent(maxLogFileCount))
                        .addGap(12, 12, 12)
                        .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(logFileCount, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(solrMaxHeapSpinner, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(memField, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(systemMemoryTotal, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(maxMemoryUnitsLabel1)
                            .addComponent(maxMemoryUnitsLabel)
                            .addComponent(maxMemoryUnitsLabel2))
                        .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(runtimePanelLayout.createSequentialGroup()
                                .addGap(23, 23, 23)
                                .addComponent(memFieldValidationLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 478, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap(117, Short.MAX_VALUE))
                            .addGroup(runtimePanelLayout.createSequentialGroup()
                                .addGap(18, 18, 18)
                                .addComponent(solrJVMHeapWarning, javax.swing.GroupLayout.PREFERRED_SIZE, 331, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(44, 44, 44)
                                .addComponent(logNumAlert)
                                .addContainerGap())))
                    .addGroup(runtimePanelLayout.createSequentialGroup()
                        .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(restartNecessaryWarning, javax.swing.GroupLayout.PREFERRED_SIZE, 615, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(runtimePanelLayout.createSequentialGroup()
                                .addComponent(heapFileLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(heapFieldValidationLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 478, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGroup(runtimePanelLayout.createSequentialGroup()
                                        .addComponent(heapDumpFileField, javax.swing.GroupLayout.PREFERRED_SIZE, 415, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(heapDumpBrowseButton)))))
                        .addGap(0, 0, Short.MAX_VALUE))))
        );

        runtimePanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {maxLogFileCount, maxMemoryLabel, totalMemoryLabel});

        runtimePanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {logFileCount, memField});

        runtimePanelLayout.setVerticalGroup(
            runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(runtimePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(totalMemoryLabel)
                    .addComponent(maxMemoryUnitsLabel1)
                    .addComponent(systemMemoryTotal, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(memFieldValidationLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(maxMemoryLabel)
                        .addComponent(memField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(maxMemoryUnitsLabel)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(logNumAlert, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(maxSolrMemoryLabel)
                        .addComponent(maxMemoryUnitsLabel2)
                        .addComponent(solrMaxHeapSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(solrJVMHeapWarning)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(maxLogFileCount)
                    .addComponent(logFileCount, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(runtimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(heapFileLabel)
                    .addComponent(heapDumpFileField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(heapDumpBrowseButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(heapFieldValidationLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(restartNecessaryWarning)
                .addContainerGap())
        );

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        mainPanel.add(runtimePanel, gridBagConstraints);

        tempDirectoryPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.tempDirectoryPanel.border.title"))); // NOI18N
        tempDirectoryPanel.setName(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.tempDirectoryPanel.name")); // NOI18N

        tempCustomField.setText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.tempCustomField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(tempDirectoryBrowseButton, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.tempDirectoryBrowseButton.text")); // NOI18N
        tempDirectoryBrowseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tempDirectoryBrowseButtonActionPerformed(evt);
            }
        });

        tempDirectoryWarningLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(tempDirectoryWarningLabel, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.tempDirectoryWarningLabel.text")); // NOI18N

        tempDirChoiceGroup.add(tempLocalRadio);
        org.openide.awt.Mnemonics.setLocalizedText(tempLocalRadio, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.tempLocalRadio.text")); // NOI18N
        tempLocalRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tempLocalRadioActionPerformed(evt);
            }
        });

        tempDirChoiceGroup.add(tempCaseRadio);
        org.openide.awt.Mnemonics.setLocalizedText(tempCaseRadio, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.tempCaseRadio.text")); // NOI18N
        tempCaseRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tempCaseRadioActionPerformed(evt);
            }
        });

        tempDirChoiceGroup.add(tempCustomRadio);
        org.openide.awt.Mnemonics.setLocalizedText(tempCustomRadio, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.tempCustomRadio.text")); // NOI18N
        tempCustomRadio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tempCustomRadioActionPerformed(evt);
            }
        });

        tempOnCustomNoPath.setForeground(java.awt.Color.RED);
        org.openide.awt.Mnemonics.setLocalizedText(tempOnCustomNoPath, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.tempOnCustomNoPath.text")); // NOI18N

        javax.swing.GroupLayout tempDirectoryPanelLayout = new javax.swing.GroupLayout(tempDirectoryPanel);
        tempDirectoryPanel.setLayout(tempDirectoryPanelLayout);
        tempDirectoryPanelLayout.setHorizontalGroup(
            tempDirectoryPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tempDirectoryPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(tempDirectoryPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tempLocalRadio)
                    .addComponent(tempCaseRadio)
                    .addComponent(tempDirectoryWarningLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 615, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(tempDirectoryPanelLayout.createSequentialGroup()
                        .addComponent(tempCustomRadio)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(tempDirectoryPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(tempOnCustomNoPath)
                            .addGroup(tempDirectoryPanelLayout.createSequentialGroup()
                                .addComponent(tempCustomField, javax.swing.GroupLayout.PREFERRED_SIZE, 459, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(tempDirectoryBrowseButton)))))
                .addContainerGap(269, Short.MAX_VALUE))
        );
        tempDirectoryPanelLayout.setVerticalGroup(
            tempDirectoryPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tempDirectoryPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(tempLocalRadio)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tempCaseRadio)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(tempDirectoryPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tempCustomRadio)
                    .addComponent(tempCustomField)
                    .addComponent(tempDirectoryBrowseButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(tempOnCustomNoPath)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(tempDirectoryWarningLabel)
                .addGap(14, 14, 14))
        );

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        mainPanel.add(tempDirectoryPanel, gridBagConstraints);
        tempDirectoryPanel.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.tempDirectoryPanel.AccessibleContext.accessibleName")); // NOI18N

        rdpPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.rdpPanel.border.title"))); // NOI18N
        rdpPanel.setMaximumSize(new java.awt.Dimension(300, 175));
        rdpPanel.setMinimumSize(new java.awt.Dimension(300, 175));
        rdpPanel.setPreferredSize(new java.awt.Dimension(300, 175));
        rdpPanel.setLayout(new java.awt.GridBagLayout());

        sizingScrollPane.setBorder(null);

        sizingTextPane.setEditable(false);
        sizingTextPane.setBorder(null);
        sizingTextPane.setText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.sizingTextPane.text")); // NOI18N
        sizingTextPane.setAutoscrolls(false);
        sizingTextPane.setMinimumSize(new java.awt.Dimension(453, 220));
        sizingTextPane.setOpaque(false);
        sizingTextPane.setPreferredSize(new java.awt.Dimension(453, 220));
        sizingTextPane.setSelectionStart(0);
        sizingScrollPane.setViewportView(sizingTextPane);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        rdpPanel.add(sizingScrollPane, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        mainPanel.add(rdpPanel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        mainPanel.add(filler1, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.weighty = 1.0;
        mainPanel.add(filler2, gridBagConstraints);

        jScrollPane1.setViewportView(mainPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 955, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 699, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    @Messages({
        "AutopsyOptionsPanel_tempDirectoryBrowseButtonActionPerformed_onInvalidPath_title=Path cannot be used",
        "# {0} - path",
        "AutopsyOptionsPanel_tempDirectoryBrowseButtonActionPerformed_onInvalidPath_description=Unable to create temporary directory within specified path: {0}",})
    private void tempDirectoryBrowseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tempDirectoryBrowseButtonActionPerformed
        if(tempDirChooser == null) {
            tempDirChooser = tempChooserHelper.getChooser();
            tempDirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            tempDirChooser.setMultiSelectionEnabled(false);
        }
        int returnState = tempDirChooser.showOpenDialog(this);
        if (returnState == JFileChooser.APPROVE_OPTION) {
            String specifiedPath = tempDirChooser.getSelectedFile().getPath();
            try {
                File f = new File(specifiedPath);
                if (!f.exists() && !f.mkdirs()) {
                    throw new InvalidPathException(specifiedPath, "Unable to create parent directories leading to " + specifiedPath);
                }
                tempCustomField.setText(specifiedPath);
                firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            } catch (InvalidPathException ex) {
                logger.log(Level.WARNING, "Unable to create temporary directory in " + specifiedPath, ex);
                JOptionPane.showMessageDialog(this,
                        Bundle.AutopsyOptionsPanel_tempDirectoryBrowseButtonActionPerformed_onInvalidPath_description(specifiedPath),
                        Bundle.AutopsyOptionsPanel_tempDirectoryBrowseButtonActionPerformed_onInvalidPath_title(),
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }//GEN-LAST:event_tempDirectoryBrowseButtonActionPerformed

    private void solrMaxHeapSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_solrMaxHeapSpinnerStateChanged
        int value = (int) solrMaxHeapSpinner.getValue();
        if (value == UserPreferences.getMaxSolrVMSize()) {
            // if the value hasn't changed there's nothing to do.
            return;
        }
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_solrMaxHeapSpinnerStateChanged

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

    private void specifyLogoRBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_specifyLogoRBActionPerformed
        agencyLogoPathField.setEnabled(true);
        browseLogosButton.setEnabled(true);
        try {
            if (agencyLogoPathField.getText().isEmpty()) {
                String path = reportBranding.getAgencyLogoPath();
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
        if(logoFileChooser == null) {
            logoFileChooser = logoChooserHelper.getChooser();
            logoFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            logoFileChooser.setMultiSelectionEnabled(false);
            logoFileChooser.setAcceptAllFileFilterUsed(false);
            logoFileChooser.setFileFilter(new GeneralFilter(GeneralFilter.GRAPHIC_IMAGE_EXTS, GeneralFilter.GRAPHIC_IMG_DECR));
        }
        String oldLogoPath = agencyLogoPathField.getText();
        int returnState = logoFileChooser.showOpenDialog(this);
        if (returnState == JFileChooser.APPROVE_OPTION) {
            String path = logoFileChooser.getSelectedFile().getPath();
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

    private void tempLocalRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tempLocalRadioActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        evaluateTempDirState();
    }//GEN-LAST:event_tempLocalRadioActionPerformed

    private void tempCaseRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tempCaseRadioActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        evaluateTempDirState();
    }//GEN-LAST:event_tempCaseRadioActionPerformed

    private void tempCustomRadioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tempCustomRadioActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        evaluateTempDirState();
    }//GEN-LAST:event_tempCustomRadioActionPerformed

    @Messages({
        "AutopsyOptionsPanel_heapDumpBrowseButtonActionPerformed_fileAlreadyExistsTitle=File Already Exists",
        "AutopsyOptionsPanel_heapDumpBrowseButtonActionPerformed_fileAlreadyExistsMessage=File already exists.  Please select a new location."
    })
    private void heapDumpBrowseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_heapDumpBrowseButtonActionPerformed
        if(heapFileChooser == null) {
            heapFileChooser = heapChooserHelper.getChooser();
            heapFileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            heapFileChooser.setMultiSelectionEnabled(false);
        }
        String oldHeapPath = heapDumpFileField.getText();
        if (!StringUtils.isBlank(oldHeapPath)) {
            heapFileChooser.setCurrentDirectory(new File(oldHeapPath));
        }
        
        int returnState = heapFileChooser.showOpenDialog(this);
        if (returnState == JFileChooser.APPROVE_OPTION) {
            File selectedDirectory = heapFileChooser.getSelectedFile();
            heapDumpFileField.setText(selectedDirectory.getAbsolutePath());
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_heapDumpBrowseButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField agencyLogoPathField;
    private javax.swing.JLabel agencyLogoPathFieldValidationLabel;
    private javax.swing.JLabel agencyLogoPreview;
    private javax.swing.JButton browseLogosButton;
    private javax.swing.JRadioButton defaultLogoRB;
    private javax.swing.ButtonGroup displayTimesButtonGroup;
    private javax.swing.ButtonGroup fileSelectionButtonGroup;
    private javax.swing.JButton heapDumpBrowseButton;
    private javax.swing.JTextField heapDumpFileField;
    private javax.swing.JLabel heapFieldValidationLabel;
    private javax.swing.JLabel heapFileLabel;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextField logFileCount;
    private javax.swing.JTextField logNumAlert;
    private javax.swing.JPanel logoPanel;
    private javax.swing.ButtonGroup logoSourceButtonGroup;
    private javax.swing.JLabel maxLogFileCount;
    private javax.swing.JLabel maxMemoryLabel;
    private javax.swing.JLabel maxMemoryUnitsLabel;
    private javax.swing.JLabel maxMemoryUnitsLabel1;
    private javax.swing.JLabel maxMemoryUnitsLabel2;
    private javax.swing.JLabel maxSolrMemoryLabel;
    private javax.swing.JTextField memField;
    private javax.swing.JLabel memFieldValidationLabel;
    private javax.swing.JPanel rdpPanel;
    private javax.swing.JLabel restartNecessaryWarning;
    private javax.swing.JPanel runtimePanel;
    private javax.swing.JLabel solrJVMHeapWarning;
    private javax.swing.JSpinner solrMaxHeapSpinner;
    private javax.swing.JRadioButton specifyLogoRB;
    private javax.swing.JLabel systemMemoryTotal;
    private javax.swing.JRadioButton tempCaseRadio;
    private javax.swing.JTextField tempCustomField;
    private javax.swing.JRadioButton tempCustomRadio;
    private javax.swing.ButtonGroup tempDirChoiceGroup;
    private javax.swing.JButton tempDirectoryBrowseButton;
    private javax.swing.JPanel tempDirectoryPanel;
    private javax.swing.JLabel tempDirectoryWarningLabel;
    private javax.swing.JRadioButton tempLocalRadio;
    private javax.swing.JLabel tempOnCustomNoPath;
    private javax.swing.JLabel totalMemoryLabel;
    // End of variables declaration//GEN-END:variables

}
