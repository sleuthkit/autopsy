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
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
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
    
    private static int VALIDATE_TIMEOUT_MILLIS = 1200;
    static ScheduledThreadPoolExecutor delayedValidationService = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("ImageFilePanel delayed validation").build());

    private final Object validateActionLock = new Object();
    private Runnable validateAction = null;
    private Future<?> validateFuture = null;

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
        
        // post-constructor initialization of listener support without leaking references of uninitialized objects
        for (JTextField textField: List.of(
                instance.getPathTextField(),
                instance.getMd5TextFieldField(), 
                instance.getSha1TextField(), 
                instance.getSha256TextField(), 
                instance.getPasswordTextField())) {
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
    
    private JTextField getPasswordTextField() {
        return passwordTextField;
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
        passwordLabel = new javax.swing.JLabel();
        passwordTextField = new javax.swing.JTextField();
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
        errorLabel.setMaximumSize(new java.awt.Dimension(500, 60));
        errorLabel.setMinimumSize(new java.awt.Dimension(200, 20));
        errorLabel.setPreferredSize(new java.awt.Dimension(200, 60));
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

        org.openide.awt.Mnemonics.setLocalizedText(passwordLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.passwordLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
        add(passwordLabel, gridBagConstraints);

        passwordTextField.setText(org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.passwordTextField.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 5);
        add(passwordTextField, gridBagConstraints);

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
        gridBagConstraints.gridy = 12;
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
                String zeroX3_path = StringUtils.removeEnd(path, ".001") + ".000";
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
        }

        updateHelper();
    }//GEN-LAST:event_browseButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton browseButton;
    private javax.swing.JLabel errorLabel;
    private javax.swing.JLabel hashValuesLabel;
    private javax.swing.JLabel hashValuesNoteLabel;
    private javax.swing.JLabel loadingLabel;
    private javax.swing.JLabel md5HashLabel;
    private javax.swing.JTextField md5HashTextField;
    private javax.swing.JCheckBox noFatOrphansCheckbox;
    private javax.swing.JLabel passwordLabel;
    private javax.swing.JTextField passwordTextField;
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
    
    String getPassword() {
        return this.passwordTextField.getText();
    }

    public void reset() {
        //reset the UI elements to default 
        pathTextField.setText(null);
        this.md5HashTextField.setText(null);
        this.sha1HashTextField.setText(null);
        this.sha256HashTextField.setText(null);
        this.passwordTextField.setText(null);
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
        "ImageFilePanel_validatePanel_imageOpenError=<html><body><p>An error occurred while opening the image:{0}</p></body></html>",
        "ImageFilePanel_validatePanel_unknownErrorMsg=<unknown>",
        "ImageFilePanel_validatePanel_unknownError=<html><body><p>An unknown error occurred while attempting to validate the image</p></body></html>"
    })
    public boolean validatePanel() {
        String path = getContentPaths();
        if (!isImagePathValid()) {
            showError(null);
            return false;
        }

        if (!StringUtils.isBlank(getMd5()) && !HashUtility.isValidMd5Hash(getMd5())) {
            showError(Bundle.ImageFilePanel_validatePanel_invalidMD5());
            return false;
        }

        if (!StringUtils.isBlank(getSha1()) && !HashUtility.isValidSha1Hash(getSha1())) {
            showError(Bundle.ImageFilePanel_validatePanel_invalidSHA1());
            return false;
        }

        if (!StringUtils.isBlank(getSha256()) && !HashUtility.isValidSha256Hash(getSha256())) {
            showError(Bundle.ImageFilePanel_validatePanel_invalidSHA256());
            return false;
        }
        
        try {
            String password = this.getPassword();
            TestOpenImageResult testResult = SleuthkitJNI.testOpenImage(path, password);
            if (!testResult.wasSuccessful()) {
                showError(Bundle.ImageFilePanel_validatePanel_imageOpenError(
                        StringUtils.defaultIfBlank(
                                testResult.getMessage(), 
                                Bundle.ImageFilePanel_validatePanel_unknownErrorMsg())));
                return false;
            }
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
    
    private boolean isImagePathValid() {
        String path = getContentPaths();
        
        if (StringUtils.isBlank(path) || (!(new File(path).isFile() || DriveUtils.isPhysicalDrive(path) || DriveUtils.isPartition(path)))) {
            return false;
        }
        
        return true;
    }
    
    /**
     * @return True if the panel is on a delay for validating (i.e. typing a
     * password for bitlocker).
     */
    public boolean isValidationLoading() {
        synchronized (this.validateActionLock) {
            return this.validateAction != null;
        }
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
        if (isImagePathValid() && !getContentPaths().toLowerCase().endsWith(".e01")) {
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
            boolean triggerUpdate = false;
            synchronized (ImageFilePanel.this.validateActionLock) {
                if (ImageFilePanel.this.validateFuture != null && 
                        !ImageFilePanel.this.validateFuture.isCancelled() &&
                        !ImageFilePanel.this.validateFuture.isDone()) {
                    ImageFilePanel.this.validateFuture.cancel(true);
                    triggerUpdate = true;
                }
                
                ImageFilePanel.this.validateAction = new ValidationRunnable();

                ImageFilePanel.this.validateFuture = ImageFilePanel.this.delayedValidationService.schedule(
                        ImageFilePanel.this.validateAction,
                        VALIDATE_TIMEOUT_MILLIS,
                        TimeUnit.MILLISECONDS);
            }
            
            // trigger invalidation after setting up new runnable if not already triggered
            if (triggerUpdate) {
                firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), false, true);
            }

            errorLabel.setVisible(false);
            if (!ImageFilePanel.this.loadingLabel.isVisible()) {
                ImageFilePanel.this.loadingLabel.setVisible(true);
            }
        }
        
        /**
         * Runnable to run the updateHelper if the validation action remains
         * this runnable.
         */
        private class ValidationRunnable implements Runnable {

            @Override
            public void run() {
                if (Thread.interrupted()) {
                    return;
                }
                
                synchronized (ImageFilePanel.this.validateActionLock) {
                    if (ImageFilePanel.this.validateAction != this) {
                        return;
                    }

                    // set the validation action to null to indicate that this is done running and can be validated.
                    ImageFilePanel.this.validateAction = null;
                    ImageFilePanel.this.validateFuture = null;
                }
                
                ImageFilePanel.this.updateHelper();
            }
            
        }
    }
}
