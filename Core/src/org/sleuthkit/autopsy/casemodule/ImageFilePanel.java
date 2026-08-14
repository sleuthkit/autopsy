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
package org.sleuthkit.autopsy.casemodule;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.coreutils.DriveUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PathValidator;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.guiutils.JFileChooserFactory;
import org.sleuthkit.datamodel.HashUtility;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.SleuthkitJNI.TestOpenImageResult;

/**
 * Panel for adding an image file such as .img, .E0x, .00x, etc. Allows the user
 * to select a file as well as choose the timezone and whether to ignore orphan
 * files in FAT32.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class ImageFilePanel extends JPanel {

    private static final Logger logger = Logger.getLogger(AddImageTask.class.getName());
    
    private static final long serialVersionUID = 1L;
    private static final String PROP_LASTIMAGE_PATH = "LBL_LastImage_PATH"; //NON-NLS
    private static final String[] SECTOR_SIZE_CHOICES = {"Auto Detect", "512", "1024", "2048", "4096"};
    private final JFileChooserFactory fileChooserHelper = new JFileChooserFactory();
    private JFileChooser fileChooser;
    private final String contextName;
    private final List<FileFilter> fileChooserFilters;
    
    private static int VALIDATE_TIMEOUT_MILLIS = 1000;
    static ScheduledThreadPoolExecutor delayedValidationService = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("ImageFilePanel delayed validation").build());

    private final ReentrantLock validationWaitingLock = new ReentrantLock();
    private final ReentrantLock validationLock = new ReentrantLock();

    private Runnable validateAction = null;
    private Future<?> validateFuture = null;

    private static final String BITLOCKER_LINE_MARKER = "BitLocker status - "; //NON-NLS
    private static final Pattern BITLOCKER_GUID_PATTERN = Pattern.compile("Recovery key identifier: ([^)]*)\\)"); //NON-NLS
    private static final Pattern BITLOCKER_OFFSET_PATTERN = Pattern.compile("\\(Volume offset: (\\d+)\\)"); //NON-NLS
    // Matches the whole optional "(Recovery key identifier: ...)" annotation
    // (including its parentheses) so it can be stripped when isolating a
    // line's plain-text description; BITLOCKER_GUID_PATTERN above only
    // captures the identifier itself, not the surrounding parentheses.
    private static final Pattern BITLOCKER_GUID_ANNOTATION_PATTERN = Pattern.compile("\\(Recovery key identifier: [^)]*\\)"); //NON-NLS
    private static final java.awt.Color BITLOCKER_STATUS_LOCKED_COLOR = new java.awt.Color(255, 0, 0);
    private static final java.awt.Color BITLOCKER_STATUS_UNLOCKED_COLOR = new java.awt.Color(0, 128, 0);

    /**
     * One password field per locked BitLocker volume, keyed by volume offset
     * and recovery key identifier. Guarded by its own monitor because it is
     * read by the background validation thread and updated on the EDT.
     */
    private final Map<String, BitlockerVolumeRow> bitlockerVolumeRows = new LinkedHashMap<>();
    private DocumentListener delayedValidationListener = null;
    private String bitlockerVolumesImagePath = null;

    /**
     * Creates new form ImageFilePanel
     *
     * @param context            A string context name used to read/store last
     *                           used settings.
     * @param fileChooserFilters A list of filters to be used with the
     *                           FileChooser.
     */
    private ImageFilePanel(String context, List<FileFilter> fileChooserFilters) {
        this.contextName = context;
        initComponents();

        // Populate the drop down list of time zones
        createTimeZoneList();

        // Populate the drop down list of sector size options
        for (String choice : SECTOR_SIZE_CHOICES) {
            sectorSizeComboBox.addItem(choice);
        }
        sectorSizeComboBox.setSelectedIndex(0);

        errorLabel.setVisible(false);
        loadingLabel.setVisible(false);
        bitlockerVolumesPanel.setVisible(false);
        this.fileChooserFilters = fileChooserFilters;
    }

    /**
     * Creates the drop down list for the time zones and defaults the selection
     * to the local machine time zone.
     */
    private void createTimeZoneList() {
        List<String> timeZoneList = TimeZoneUtils.createTimeZoneList();
        for (String timeZone : timeZoneList) {
            timeZoneComboBox.addItem(timeZone);
        }

        // set the selected timezone
        timeZoneComboBox.setSelectedItem(TimeZoneUtils.createTimeZoneString(Calendar.getInstance().getTimeZone()));
    }

    /**
     * Creates and returns an instance of a ImageFilePanel.
     *
     * @param context            A string context name used to read/store last
     *                           used settings.
     * @param fileChooserFilters A list of filters to be used with the
     *                           FileChooser.
     *
     * @return instance of the ImageFilePanel
     */
    public static synchronized ImageFilePanel createInstance(String context, List<FileFilter> fileChooserFilters) {
        ImageFilePanel instance = new ImageFilePanel(context, fileChooserFilters);
        DocumentListener delayedValidationListener = instance.new DelayedValidationDocListener();
        instance.delayedValidationListener = delayedValidationListener;

        // post-constructor initialization of listener support without leaking references of uninitialized objects
        for (JTextField textField: List.of(
                instance.getPathTextField(),
                instance.getMd5TextFieldField(),
                instance.getSha1TextField(),
                instance.getSha256TextField())) {
            textField.getDocument().addDocumentListener(delayedValidationListener);
        }
        return instance;
    }

    private JTextField getPathTextField() {
        return pathTextField;
    }

    private JTextField getMd5TextFieldField() {
        return md5HashTextField;
    }

    private JTextField getSha1TextField() {
        return sha1HashTextField;
    }

    private JTextField getSha256TextField() {
        return sha256HashTextField;
    }
    
    private JFileChooser getChooser() {
        if(fileChooser == null) {
            fileChooser = fileChooserHelper.getChooser();
            fileChooser.setDragEnabled(false);
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setMultiSelectionEnabled(false);
            fileChooserFilters.forEach(fileChooser::addChoosableFileFilter);
            if (fileChooserFilters.isEmpty() == false) {
                fileChooser.setFileFilter(fileChooserFilters.get(0));
            }
        }
        
        return fileChooser;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        pathLabel = new javax.swing.JLabel();
        browseButton = new javax.swing.JButton();
        pathTextField = new javax.swing.JTextField();
        timeZoneLabel = new javax.swing.JLabel();
        timeZoneComboBox = new javax.swing.JComboBox<>();
        noFatOrphansCheckbox = new javax.swing.JCheckBox();
        errorLabel = new javax.swing.JLabel();
        sectorSizeLabel = new javax.swing.JLabel();
        sectorSizeComboBox = new javax.swing.JComboBox<>();
        sha256HashLabel = new javax.swing.JLabel();
        sha256HashTextField = new javax.swing.JTextField();
        sha1HashTextField = new javax.swing.JTextField();
        md5HashTextField = new javax.swing.JTextField();
        sha1HashLabel = new javax.swing.JLabel();
        md5HashLabel = new javax.swing.JLabel();
        hashValuesLabel = new javax.swing.JLabel();
        hashValuesNoteLabel = new javax.swing.JLabel();
        bitlockerVolumesPanel = new javax.swing.JPanel();
        javax.swing.JPanel spacer = new javax.swing.JPanel();
        loadingLabel = new javax.swing.JLabel();

        setMinimumSize(new java.awt.Dimension(0, 65));
        setPreferredSize(new java.awt.Dimension(403, 65));
        setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(pathLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.pathLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        add(pathLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(browseButton, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.browseButton.text")); // NOI18N
        browseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 5);
        add(browseButton, gridBagConstraints);

        pathTextField.setText(org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.pathTextField.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
        add(pathTextField, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(timeZoneLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.timeZoneLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
        add(timeZoneLabel, gridBagConstraints);

        timeZoneComboBox.setMaximumRowCount(30);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 5);
        add(timeZoneComboBox, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(noFatOrphansCheckbox, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.noFatOrphansCheckbox.text")); // NOI18N
        noFatOrphansCheckbox.setToolTipText(org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.noFatOrphansCheckbox.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
        add(noFatOrphansCheckbox, gridBagConstraints);

        errorLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(errorLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.errorLabel.text")); // NOI18N
        errorLabel.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        errorLabel.setMinimumSize(new java.awt.Dimension(200, 20));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        add(errorLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(sectorSizeLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.sectorSizeLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
        add(sectorSizeLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 5);
        add(sectorSizeComboBox, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(sha256HashLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.sha256HashLabel.text")); // NOI18N
        sha256HashLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
        add(sha256HashLabel, gridBagConstraints);

        sha256HashTextField.setText(org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.sha256HashTextField.text")); // NOI18N
        sha256HashTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 5);
        add(sha256HashTextField, gridBagConstraints);

        sha1HashTextField.setText(org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.sha1HashTextField.text")); // NOI18N
        sha1HashTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 5);
        add(sha1HashTextField, gridBagConstraints);

        md5HashTextField.setText(org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.md5HashTextField.text")); // NOI18N
        md5HashTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 5);
        add(md5HashTextField, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(sha1HashLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.sha1HashLabel.text")); // NOI18N
        sha1HashLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
        add(sha1HashLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(md5HashLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.md5HashLabel.text")); // NOI18N
        md5HashLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
        add(md5HashLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(hashValuesLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.hashValuesLabel.text")); // NOI18N
        hashValuesLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        add(hashValuesLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(hashValuesNoteLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.hashValuesNoteLabel.text")); // NOI18N
        hashValuesNoteLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
        add(hashValuesNoteLabel, gridBagConstraints);

        bitlockerVolumesPanel.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
        add(bitlockerVolumesPanel, gridBagConstraints);

        javax.swing.GroupLayout spacerLayout = new javax.swing.GroupLayout(spacer);
        spacer.setLayout(spacerLayout);
        spacerLayout.setHorizontalGroup(
            spacerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        spacerLayout.setVerticalGroup(
            spacerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.weighty = 1.0;
        add(spacer, gridBagConstraints);

        loadingLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/working_spinner.gif"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(loadingLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.loadingLabel.text")); // NOI18N
        loadingLabel.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        loadingLabel.setMaximumSize(new java.awt.Dimension(500, 60));
        loadingLabel.setMinimumSize(new java.awt.Dimension(200, 20));
        loadingLabel.setPreferredSize(new java.awt.Dimension(200, 60));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        add(loadingLabel, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

    @NbBundle.Messages({"ImageFilePanel.000.confirmationMessage=The selected file"
        + " has extenson .001 but there is a .000 file in the sequence of raw images."
        + "\nShould the .000 file be used as the start, instead of the selected .001 file?\n"})
    private void browseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseButtonActionPerformed
        String oldText = getContentPaths();
        // set the current directory of the FileChooser if the ImagePath Field is valid
        File currentDir = new File(oldText);
        JFileChooser chooser = getChooser();
        if (currentDir.exists()) {
            chooser.setCurrentDirectory(currentDir);
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getPath();
            if (path.endsWith(".001")) {
                String zeroX3_path = path.substring(0, path.length() - 4) + ".000";
                if (new File(zeroX3_path).exists()) {
                    int showConfirmDialog = JOptionPane.showConfirmDialog(this,
                            Bundle.ImageFilePanel_000_confirmationMessage(),
                            "Choose .001 file?", JOptionPane.YES_NO_OPTION);
                    if (showConfirmDialog == JOptionPane.YES_OPTION) {
                        path = zeroX3_path;
                    }
                }
            }
            
            setContentPath(path);
            
            /**
             * Automatically clear out the hash values if a new image was
             * selected.
             */
            if (!oldText.equals(getContentPaths())) {
                md5HashTextField.setText(null);
                sha1HashTextField.setText(null);
                sha256HashTextField.setText(null);
            }

            // Only update after a selection: nothing changed on cancel, and
            // with no delayed validation pending this call would otherwise
            // run the image test synchronously on the EDT.
            updateHelper();
        }
    }//GEN-LAST:event_browseButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel bitlockerVolumesPanel;
    private javax.swing.JButton browseButton;
    private javax.swing.JLabel errorLabel;
    private javax.swing.JLabel hashValuesLabel;
    private javax.swing.JLabel hashValuesNoteLabel;
    private javax.swing.JLabel loadingLabel;
    private javax.swing.JLabel md5HashLabel;
    private javax.swing.JTextField md5HashTextField;
    private javax.swing.JCheckBox noFatOrphansCheckbox;
    private javax.swing.JLabel pathLabel;
    private javax.swing.JTextField pathTextField;
    private javax.swing.JComboBox<String> sectorSizeComboBox;
    private javax.swing.JLabel sectorSizeLabel;
    private javax.swing.JLabel sha1HashLabel;
    private javax.swing.JTextField sha1HashTextField;
    private javax.swing.JLabel sha256HashLabel;
    private javax.swing.JTextField sha256HashTextField;
    private javax.swing.JComboBox<String> timeZoneComboBox;
    private javax.swing.JLabel timeZoneLabel;
    // End of variables declaration//GEN-END:variables

    /**
     * Enable or disable all of the hash values components.
     * 
     * @param enabled Enable components if true; otherwise disable.
     */
    private void setHashValuesComponentsEnabled(boolean enabled) {
        hashValuesLabel.setEnabled(enabled);
        hashValuesNoteLabel.setEnabled(enabled);

        md5HashLabel.setEnabled(enabled);
        md5HashTextField.setEnabled(enabled);

        sha1HashLabel.setEnabled(enabled);
        sha1HashTextField.setEnabled(enabled);

        sha256HashLabel.setEnabled(enabled);
        sha256HashTextField.setEnabled(enabled);
    }
    
    /**
     * Get the path of the user selected image.
     *
     * @return the image path
     */
    public String getContentPaths() {
        return pathTextField.getText();
    }

    /**
     * Set the path of the image file.
     *
     * @param s path of the image file
     */
    public void setContentPath(String s) {
        pathTextField.setText(s);
    }

    /**
     * Get the sector size.
     *
     * @return 0 if autodetect; otherwise the value selected.
     */
    public int getSectorSize() {
        int sectorSizeSelectionIndex = sectorSizeComboBox.getSelectedIndex();

        if (sectorSizeSelectionIndex == 0) {
            return 0;
        }

        return Integer.valueOf((String) sectorSizeComboBox.getSelectedItem());
    }

    public String getTimeZone() {
        String tz = timeZoneComboBox.getSelectedItem().toString();
        return tz.substring(tz.indexOf(')') + 2).trim();
    }

    public boolean getNoFatOrphans() {
        return noFatOrphansCheckbox.isSelected();
    }

    String getMd5() {
        return this.md5HashTextField.getText();
    }

    String getSha1() {
        return this.sha1HashTextField.getText();
    }

    String getSha256() {
        return this.sha256HashTextField.getText();
    }
    
    /**
     * Gets all candidate passwords entered by the user via the per-volume
     * BitLocker password fields. Each will be tried when opening the
     * encrypted volumes in the image.
     *
     * @return De-duplicated list of non-empty candidate passwords.
     */
    List<String> getPasswords() {
        List<String> passwords = new ArrayList<>();
        synchronized (bitlockerVolumeRows) {
            // Row passwords belong to the image they were shown for; skip
            // them if the selected path has changed since (the rows
            // themselves clear asynchronously on the EDT).
            if (Objects.equals(getContentPaths(), bitlockerVolumesImagePath)) {
                for (BitlockerVolumeRow volumeRow : bitlockerVolumeRows.values()) {
                    String volumePassword = StringUtils.defaultString(volumeRow.passwordField.getText());
                    if (!volumePassword.isEmpty() && !passwords.contains(volumePassword)) {
                        passwords.add(volumePassword);
                    }
                }
            }
        }
        return passwords;
    }

    /**
     * A locked BitLocker volume parsed from the test open image result
     * message.
     */
    private static class BitlockerVolumeInfo {

        private final String volumeOffset;   // null if not in the message
        private final String recoveryKeyId;  // null if not in the message
        private final String description;    // null if not parsed from the message

        BitlockerVolumeInfo(String volumeOffset, String recoveryKeyId, String description) {
            this.volumeOffset = volumeOffset;
            this.recoveryKeyId = recoveryKeyId;
            this.description = description;
        }

        String getKey() {
            // The offset suffix is only present in the message while more
            // than one volume is locked, so it is not stable across
            // re-validations; key on the recovery key GUID when there is
            // one and fall back to the offset.
            if (recoveryKeyId != null) {
                return recoveryKeyId;
            }
            if (volumeOffset != null) {
                return volumeOffset;
            }
            return "";
        }
    }

    /**
     * The Swing components of one per-volume BitLocker password row.
     */
    private static class BitlockerVolumeRow {

        private final JLabel titleLabel;
        private final JTextField passwordField;
        private final JLabel statusLabel;

        BitlockerVolumeRow(JLabel titleLabel, JTextField passwordField, JLabel statusLabel) {
            this.titleLabel = titleLabel;
            this.passwordField = passwordField;
            this.statusLabel = statusLabel;
        }
    }

    /**
     * Parses the locked BitLocker volumes out of a test open image result
     * message. The message contains one line per locked volume in the form
     * "BitLocker status - ... (Recovery key identifier: GUID) (Volume offset: N)"
     * where the identifier may be blank and the offset is only present when
     * there are multiple locked volumes.
     *
     * @param message The test open image result message.
     *
     * @return The locked volumes, empty if none were found.
     */
    private List<BitlockerVolumeInfo> parseBitlockerVolumes(String message) {
        List<BitlockerVolumeInfo> volumes = new ArrayList<>();
        if (StringUtils.isBlank(message)) {
            return volumes;
        }
        for (String line : message.split("\n")) {
            if (!line.contains(BITLOCKER_LINE_MARKER)) {
                continue;
            }
            Matcher guidMatcher = BITLOCKER_GUID_PATTERN.matcher(line);
            String recoveryKeyId = guidMatcher.find() ? StringUtils.trimToNull(guidMatcher.group(1)) : null;
            Matcher offsetMatcher = BITLOCKER_OFFSET_PATTERN.matcher(line);
            String volumeOffset = offsetMatcher.find() ? offsetMatcher.group(1) : null;
            // The identifier/offset annotations are optional (e.g. a
            // password-only-protected volume has neither), so the
            // description is derived by stripping them rather than
            // anchoring on either being present.
            String description = StringUtils.trimToNull(
                    line.substring(line.indexOf(BITLOCKER_LINE_MARKER) + BITLOCKER_LINE_MARKER.length())
                            .replaceAll(BITLOCKER_GUID_ANNOTATION_PATTERN.pattern(), "")
                            .replaceAll(BITLOCKER_OFFSET_PATTERN.pattern(), ""));
            volumes.add(new BitlockerVolumeInfo(volumeOffset, recoveryKeyId, description));
        }
        return volumes;
    }

    @NbBundle.Messages({
        "# {0} - recoveryKeyId",
        "ImageFilePanel_bitlockerVolume_labelWithId=BitLocker volume — Recovery key ID: {0}:",
        "# {0} - volumeOffset",
        "ImageFilePanel_bitlockerVolume_labelNoId=BitLocker volume at offset {0} (user password):",
        "ImageFilePanel_bitlockerVolume_labelPlain=BitLocker volume password:",
        "ImageFilePanel_bitlockerVolume_statusLocked=Password required",
        "ImageFilePanel_bitlockerVolume_statusUnlocked=✓ Unlocked"
    })
    private static String getBitlockerVolumeLabel(BitlockerVolumeInfo volumeInfo) {
        // The recovery key ID is what BitLocker users record/reference; the
        // volume offset is only shown as a fallback when there is no ID to
        // tell two locked volumes apart (e.g. a user-password-only volume).
        if (volumeInfo.recoveryKeyId != null) {
            return Bundle.ImageFilePanel_bitlockerVolume_labelWithId(volumeInfo.recoveryKeyId);
        } else if (volumeInfo.volumeOffset != null) {
            return Bundle.ImageFilePanel_bitlockerVolume_labelNoId(volumeInfo.volumeOffset);
        }
        return Bundle.ImageFilePanel_bitlockerVolume_labelPlain();
    }

    /**
     * Clears the per-volume BitLocker rows if the given path is no longer the
     * currently selected image path. Unlike {@link #updateBitlockerVolumeRows},
     * this never creates rows or updates their lock status — it is meant to
     * be called defensively before a real test-open-image result is known
     * (e.g. at the top of {@code validatePanel()}), so it cannot flash a
     * false "unlocked" status on an in-progress validation.
     *
     * @param imagePath The currently selected image path.
     */
    private void clearStaleBitlockerVolumeRows(String imagePath) {
        SwingUtilities.invokeLater(() -> {
            if (!Objects.equals(imagePath, getContentPaths())) {
                return;
            }
            synchronized (bitlockerVolumeRows) {
                if (!Objects.equals(imagePath, bitlockerVolumesImagePath)) {
                    clearBitlockerVolumeRows();
                    bitlockerVolumesImagePath = imagePath;
                }
            }
        });
    }

    /**
     * Shows a labeled password field for each locked BitLocker volume, and
     * refreshes every row's live status. Fields for volumes that are already
     * shown keep their contents; a volume that unlocks is no longer reported
     * in the message but its field (and password) is kept so it remains part
     * of the candidate list — its row instead switches to an "Unlocked"
     * status. Call only with a real (possibly empty, on success) list of
     * currently locked volumes from a completed test-open-image result.
     *
     * @param imagePath The image the volumes belong to; switching images
     *                  clears all fields.
     * @param volumes   The locked volumes parsed from the latest validation.
     */
    private void updateBitlockerVolumeRows(String imagePath, List<BitlockerVolumeInfo> volumes) {
        SwingUtilities.invokeLater(() -> {
            if (!Objects.equals(imagePath, getContentPaths())) {
                // Result of a validation for a path that is no longer
                // selected (the path changed or the panel was reset while
                // the image test was running).
                return;
            }
            synchronized (bitlockerVolumeRows) {
                if (!Objects.equals(imagePath, bitlockerVolumesImagePath)) {
                    clearBitlockerVolumeRows();
                    bitlockerVolumesImagePath = imagePath;
                }
                Map<String, BitlockerVolumeInfo> lockedByKey = new HashMap<>();
                for (BitlockerVolumeInfo volumeInfo : volumes) {
                    String volumeKey = volumeInfo.getKey();
                    lockedByKey.put(volumeKey, volumeInfo);
                    if (bitlockerVolumeRows.containsKey(volumeKey)) {
                        continue;
                    }
                    if (volumeKey.isEmpty()) {
                        if (!bitlockerVolumeRows.isEmpty()) {
                            // A line with neither a recovery key GUID nor an
                            // offset cannot be matched to a specific existing
                            // row; every field is pooled into the candidate
                            // list anyway, so do not add an unidentifiable
                            // duplicate.
                            continue;
                        }
                    } else {
                        // A volume first reported without any identifier gets
                        // its row re-keyed and relabeled once an identified
                        // report arrives, instead of gaining a second row.
                        BitlockerVolumeRow orphanRow = bitlockerVolumeRows.remove("");
                        if (orphanRow != null) {
                            orphanRow.titleLabel.setText(getBitlockerVolumeLabel(volumeInfo));
                            bitlockerVolumeRows.put(volumeKey, orphanRow);
                            continue;
                        }
                    }

                    int row = bitlockerVolumeRows.size();
                    int gridY = row * 2;

                    JLabel volumeLabel = new JLabel(getBitlockerVolumeLabel(volumeInfo));
                    java.awt.GridBagConstraints labelConstraints = new java.awt.GridBagConstraints();
                    labelConstraints.gridx = 0;
                    labelConstraints.gridy = gridY;
                    labelConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
                    labelConstraints.insets = new java.awt.Insets(0, 0, 5, 5);
                    bitlockerVolumesPanel.add(volumeLabel, labelConstraints);

                    JTextField volumeField = new JTextField();
                    if (delayedValidationListener != null) {
                        volumeField.getDocument().addDocumentListener(delayedValidationListener);
                    }
                    java.awt.GridBagConstraints fieldConstraints = new java.awt.GridBagConstraints();
                    fieldConstraints.gridx = 1;
                    fieldConstraints.gridy = gridY;
                    fieldConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                    fieldConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
                    fieldConstraints.weightx = 1.0;
                    fieldConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
                    bitlockerVolumesPanel.add(volumeField, fieldConstraints);

                    JLabel statusLabel = new JLabel(" ");
                    java.awt.GridBagConstraints statusConstraints = new java.awt.GridBagConstraints();
                    statusConstraints.gridx = 0;
                    statusConstraints.gridy = gridY + 1;
                    statusConstraints.gridwidth = 2;
                    statusConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
                    statusConstraints.insets = new java.awt.Insets(0, 20, 8, 0);
                    bitlockerVolumesPanel.add(statusLabel, statusConstraints);

                    bitlockerVolumeRows.put(volumeKey, new BitlockerVolumeRow(volumeLabel, volumeField, statusLabel));
                }

                // A locked volume whose line had neither a recovery key GUID
                // nor an offset could not be matched to (or create) a row
                // above; when that happens, any of the existing rows could be
                // the one still locked, so no row may claim to be unlocked.
                boolean hasUnmatchedLockedVolume = false;
                for (String lockedKey : lockedByKey.keySet()) {
                    if (!bitlockerVolumeRows.containsKey(lockedKey)) {
                        hasUnmatchedLockedVolume = true;
                        break;
                    }
                }

                // Refresh every row's status, including rows not present in
                // `volumes` this time (they are now unlocked) and rows whose
                // description changed (e.g. "Password required" to
                // "Incorrect password entered"). Runs every call, not just
                // when a row was added, since the same set of locked volumes
                // can be re-validated with a different failure reason.
                for (Map.Entry<String, BitlockerVolumeRow> entry : bitlockerVolumeRows.entrySet()) {
                    BitlockerVolumeInfo lockedInfo = lockedByKey.get(entry.getKey());
                    BitlockerVolumeRow volumeRow = entry.getValue();
                    if (lockedInfo != null) {
                        volumeRow.statusLabel.setText(StringUtils.defaultIfBlank(
                                lockedInfo.description, Bundle.ImageFilePanel_bitlockerVolume_statusLocked()));
                        volumeRow.statusLabel.setForeground(BITLOCKER_STATUS_LOCKED_COLOR);
                    } else if (!hasUnmatchedLockedVolume) {
                        volumeRow.statusLabel.setText(Bundle.ImageFilePanel_bitlockerVolume_statusUnlocked());
                        volumeRow.statusLabel.setForeground(BITLOCKER_STATUS_UNLOCKED_COLOR);
                    }
                    // else: an unidentifiable volume is still locked and this
                    // row might be its; keep the row's previous status rather
                    // than show a possibly false "Unlocked".
                }

                bitlockerVolumesPanel.setVisible(!bitlockerVolumeRows.isEmpty());
                bitlockerVolumesPanel.revalidate();
                bitlockerVolumesPanel.repaint();
                this.revalidate();
                this.repaint();
            }
        });
    }

    /**
     * Removes all per-volume BitLocker password fields. Must be called on the
     * EDT while holding the bitlockerVolumeRows monitor.
     */
    private void clearBitlockerVolumeRows() {
        bitlockerVolumeRows.clear();
        bitlockerVolumesPanel.removeAll();
        bitlockerVolumesPanel.setVisible(false);
        bitlockerVolumesPanel.revalidate();
        bitlockerVolumesPanel.repaint();
        this.revalidate();
        this.repaint();
    }

    public void reset() {
        //reset the UI elements to default
        pathTextField.setText(null);
        this.md5HashTextField.setText(null);
        this.sha1HashTextField.setText(null);
        this.sha256HashTextField.setText(null);
        SwingUtilities.invokeLater(() -> {
            synchronized (bitlockerVolumeRows) {
                clearBitlockerVolumeRows();
                bitlockerVolumesImagePath = null;
            }
        });
    }

    /**
     * Sets UI enabled state.
     *
     * @param enabled True
     */
    private void setUIEnabled(boolean enabled, boolean validNonE01) {
        SwingUtilities.invokeLater(() -> {
            this.browseButton.setEnabled(enabled);
            this.noFatOrphansCheckbox.setEnabled(enabled);
            setTextFieldEnabled(this.pathTextField, enabled);
            this.sectorSizeComboBox.setEnabled(enabled);
            setTextFieldEnabled(this.md5HashTextField, enabled && validNonE01);
            setTextFieldEnabled(this.sha1HashTextField, enabled && validNonE01);
            setTextFieldEnabled(this.sha256HashTextField, enabled && validNonE01);
            this.timeZoneComboBox.setEnabled(enabled);
            synchronized (bitlockerVolumeRows) {
                for (BitlockerVolumeRow volumeRow : bitlockerVolumeRows.values()) {
                    setTextFieldEnabled(volumeRow.passwordField, enabled);
                }
            }
        });
    }

    /**
     * Enables or disables a text field, leaving the field the user is typing
     * in editable. Validation snapshots all field values before it starts and
     * any edit during validation schedules a re-validation, so an edit to the
     * focused field cannot produce a stale accepted state, while disabling it
     * mid-typing would drop keystrokes and move the focus away.
     */
    private static void setTextFieldEnabled(JTextField field, boolean enabled) {
        if (enabled || !field.isFocusOwner()) {
            field.setEnabled(enabled);
        }
    }

    /**
     * Should we enable the next button of the wizard?
     *
     * @return true if a proper image has been selected, false otherwise
     */
    @NbBundle.Messages({
        "ImageFilePanel.validatePanel.dataSourceOnCDriveError=Warning: Path to multi-user data source is on \"C:\" drive",
        "ImageFilePanel.validatePanel.invalidMD5=Invalid MD5 hash",
        "ImageFilePanel.validatePanel.invalidSHA1=Invalid SHA1 hash",
        "ImageFilePanel.validatePanel.invalidSHA256=Invalid SHA256 hash",
        "# {0} - imageOpenError",
        "ImageFilePanel_validatePanel_imageOpenError=<html><body style=\"width:450px\"><p>An error occurred while opening the image:{0}</p></body></html>",
        "ImageFilePanel_validatePanel_unknownErrorMsg=<unknown>",
        "ImageFilePanel_validatePanel_unknownError=<html><body><p>An unknown error occurred while attempting to validate the image</p></body></html>",
        "ImageFilePanel_validatePanel_bitlockerLocked=<html><body><p>One or more BitLocker volumes require a password to open this "
        + "image. Enter a password or recovery key for each locked volume below.</p></body></html>"
    })
    public boolean validatePanel() {
        return runWithLock(this.validationLock, () -> {
            boolean validNonE01 = true;
            try {

                // acquire field values at the beginning to minimize chance of changing while validating.
                String path = getContentPaths();

                validNonE01 = isValidNonE01(path);
                setUIEnabled(false, validNonE01);

                String md5 = getMd5();
                String sha1 = getSha1();
                String sha256 = getSha256();
                List<String> passwords = getPasswords();

                // A path change clears the rows of the previous image even
                // when validation exits early below; for an unchanged path
                // this is a no-op that keeps the rows. Uses the lightweight
                // clear-only path so it can't flash a false "unlocked"
                // status before the real test-open-image result is in.
                clearStaleBitlockerVolumeRows(path);

                if (!isImagePathValid(path)) {
                    showError(null);
                    return false;
                }

                if (!StringUtils.isBlank(md5) && !HashUtility.isValidMd5Hash(md5)) {
                    showError(Bundle.ImageFilePanel_validatePanel_invalidMD5());
                    return false;
                }

                if (!StringUtils.isBlank(sha1) && !HashUtility.isValidSha1Hash(sha1)) {
                    showError(Bundle.ImageFilePanel_validatePanel_invalidSHA1());
                    return false;
                }

                if (!StringUtils.isBlank(sha256) && !HashUtility.isValidSha256Hash(sha256)) {
                    showError(Bundle.ImageFilePanel_validatePanel_invalidSHA256());
                    return false;
                }

                try {
                    TestOpenImageResult testResult = SleuthkitJNI.testOpenImage(path, passwords);
                    if (!testResult.wasSuccessful()) {
                        // Show a password field for each locked BitLocker volume
                        // reported in the message.
                        List<BitlockerVolumeInfo> volumes = parseBitlockerVolumes(testResult.getMessage());
                        updateBitlockerVolumeRows(path, volumes);
                        if (volumes.isEmpty()) {
                            // Not a BitLocker failure; show the detailed message.
                            String message = StringUtils.defaultIfBlank(
                                    testResult.getMessage(),
                                    Bundle.ImageFilePanel_validatePanel_unknownErrorMsg());
                            // The error label renders HTML, so multiple locked
                            // volumes need <br> tags to show as separate lines.
                            showError(Bundle.ImageFilePanel_validatePanel_imageOpenError(message.replace("\n", "<br>")));
                        } else {
                            // The per-volume rows below already show each
                            // volume's identifier, so a short prompt is enough.
                            showError(Bundle.ImageFilePanel_validatePanel_bitlockerLocked());
                        }
                        return false;
                    }
                    updateBitlockerVolumeRows(path, new ArrayList<>());
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "An unknown error occurred test opening image: " + path, t);
                    showError(Bundle.ImageFilePanel_validatePanel_unknownError());
                    return false;
                }

                if (!PathValidator.isValidForCaseType(path, Case.getCurrentCase().getCaseType())) {
                    showError(Bundle.ImageFilePanel_validatePanel_dataSourceOnCDriveError());
                } else {
                    showError(null);
                }
                return true;
            } finally {
                setUIEnabled(true, validNonE01);
            }
        });
    }
    
    /**
     * Show an error message if error message is non-empty. Otherwise, hide
     * error message.  Either way, hide loading label.
     *
     * @param errorMessage The error message to show or null for no error.
     */
    private void showError(String errorMessage) {
        loadingLabel.setVisible(false);
        if (StringUtils.isNotBlank(errorMessage)) {
            errorLabel.setVisible(true);
            errorLabel.setText(errorMessage);
        } else {
            errorLabel.setVisible(false);
            errorLabel.setText("");
        }
    }

    /**
     * Returns true if path is valid for processing.
     *
     * @param path The path.
     * @return True if valid for processing.
     */
    private boolean isImagePathValid(String path) {
        if (StringUtils.isBlank(path) || (!(new File(path).isFile() || DriveUtils.isPhysicalDrive(path) || DriveUtils.isPartition(path)))) {
            return false;
        }

        return true;
    }
    
    /**
     * Returns true if the path is a valid image that is not an E01.
     * @param path The path.
     * @return True if valid image and not E01.
     */
    private boolean isValidNonE01(String path) {
        return StringUtils.isNotBlank(path) && isImagePathValid(path) && !path.toLowerCase().endsWith(".e01");
    }

    public void storeSettings() {
        String imagePathName = getContentPaths();
        if (null != imagePathName) {
            String imagePath = imagePathName.substring(0, imagePathName.lastIndexOf(File.separator) + 1);
            ModuleSettings.setConfigSetting(contextName, PROP_LASTIMAGE_PATH, imagePath);
        }
    }

    public void readSettings() {
        String lastImagePath = ModuleSettings.getConfigSetting(contextName, PROP_LASTIMAGE_PATH);
        if (StringUtils.isNotBlank(lastImagePath)) {
            setContentPath(lastImagePath);
        }
    }

    /**
     * Update functions are called by the pathTextField which has this set as
     * it's DocumentEventListener. Each update function fires a property change
     * to be caught by the parent panel. Additionally, the hash values will be
     * enabled or disabled depending on the pathTextField input.
     */
    @NbBundle.Messages({"ImageFilePanel.moduleErr=Module Error",
        "ImageFilePanel.moduleErr.msg=A module caused an error listening to ImageFilePanel updates."
        + " See log to determine which module. Some data could be incomplete.\n"})
    private void updateHelper() {
        String path = getContentPaths();
        if (isValidNonE01(path)) {
            setHashValuesComponentsEnabled(true);
        } else {
            setHashValuesComponentsEnabled(false);
        }

        firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), false, true);
    }

    /**
     * Set the focus to the pathTextField.
     */
    public void select() {
        pathTextField.requestFocusInWindow();
    }
    
    /**
     * Runs the supplier action with the reentrant lock or blocks until
     * acquired.
     *
     * @param <T>
     * @param lock The reentrant lock.
     * @param action The action to run.
     * @return The value of the supplier.
     */
    private <T> T runWithLock(ReentrantLock lock, Supplier<T> action) {
        try {
            lock.lock();
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return True if the panel is on a delay for validating (i.e. typing a
     * password for bitlocker).
     */
    public boolean isValidationLoading() {
        return runWithLock(this.validationWaitingLock, () -> this.validateFuture != null
                && !this.validateFuture.isCancelled()
                && !this.validateFuture.isDone());
    }

    /**
     * This class validates on a delay canceling any tasks previously scheduled
     * so that password validation doesn't lock up the system.
     */
    private class DelayedValidationDocListener implements DocumentListener {

        @Override
        public void insertUpdate(DocumentEvent e) {
            delayValidate();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            delayValidate();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            delayValidate();
        }

        /**
         * Run validation on a delay to avoid password checking too many times
         * while typing.
         */
        private void delayValidate() {

            boolean triggerUpdate = runWithLock(validationWaitingLock, () -> {

                boolean toRetTriggerUpdate = false;
                if (isValidationLoading()) {
                    validateFuture.cancel(true);
                    toRetTriggerUpdate = true;
                }

                validateAction = new ValidationRunnable();

                validateFuture = delayedValidationService.schedule(
                        validateAction,
                        VALIDATE_TIMEOUT_MILLIS,
                        TimeUnit.MILLISECONDS);

                return toRetTriggerUpdate;
            });

            errorLabel.setVisible(false);
            loadingLabel.setVisible(true);

            // trigger invalidation after setting up new runnable if not already triggered
            if (triggerUpdate) {
                firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), false, true);
            }
        }
     
        /**
         * Runnable to run the updateHelper if the validation action remains
         * this runnable.
         */
        private class ValidationRunnable implements Runnable {

            @Override
            public void run() {

                boolean isRunningAction = runWithLock(validationWaitingLock, () -> {
                    if (validateAction != this) {
                        return false;
                    }

                    // set the validation action to null to indicate that this is done running and can be validated.
                    validateAction = null;
                    validateFuture = null;

                    return true;
                });

                if (!isRunningAction) {
                    return;
                } else if (Thread.interrupted()) {
                    return;
                }

                ImageFilePanel.this.updateHelper();
            }

        }
    }
}
