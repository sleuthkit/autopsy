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

import java.io.File;
import java.util.Calendar;
import java.util.List;
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
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PathValidator;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.guiutils.JFileChooserFactory;
import org.sleuthkit.datamodel.HashUtility;

/**
 * Panel for adding an image file such as .img, .E0x, .00x, etc. Allows the user
 * to select a file as well as choose the timezone and whether to ignore orphan
 * files in FAT32.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class ImageFilePanel extends JPanel implements DocumentListener {

    private static final long serialVersionUID = 1L;
    private static final String PROP_LASTIMAGE_PATH = "LBL_LastImage_PATH"; //NON-NLS
    private static final String[] SECTOR_SIZE_CHOICES = {"Auto Detect", "512", "1024", "2048", "4096"};
    private final JFileChooserFactory fileChooserHelper = new JFileChooserFactory();
    private JFileChooser fileChooser;
    private final String contextName;
    private final List<FileFilter> fileChooserFilters;

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
        // post-constructor initialization of listener support without leaking references of uninitialized objects
        instance.getPathTextField().getDocument().addDocumentListener(instance);
        instance.getMd5TextFieldField().getDocument().addDocumentListener(instance);
        instance.getSha1TextField().getDocument().addDocumentListener(instance);
        instance.getSha256TextField().getDocument().addDocumentListener(instance);
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

        setMinimumSize(new java.awt.Dimension(0, 65));
        setPreferredSize(new java.awt.Dimension(403, 65));

        org.openide.awt.Mnemonics.setLocalizedText(pathLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.pathLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(browseButton, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.browseButton.text")); // NOI18N
        browseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseButtonActionPerformed(evt);
            }
        });

        pathTextField.setText(org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.pathTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(timeZoneLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.timeZoneLabel.text")); // NOI18N

        timeZoneComboBox.setMaximumRowCount(30);

        org.openide.awt.Mnemonics.setLocalizedText(noFatOrphansCheckbox, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.noFatOrphansCheckbox.text")); // NOI18N
        noFatOrphansCheckbox.setToolTipText(org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.noFatOrphansCheckbox.toolTipText")); // NOI18N

        errorLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(errorLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.errorLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(sectorSizeLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.sectorSizeLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(sha256HashLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.sha256HashLabel.text")); // NOI18N
        sha256HashLabel.setEnabled(false);

        sha256HashTextField.setText(org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.sha256HashTextField.text")); // NOI18N
        sha256HashTextField.setEnabled(false);

        sha1HashTextField.setText(org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.sha1HashTextField.text")); // NOI18N
        sha1HashTextField.setEnabled(false);

        md5HashTextField.setText(org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.md5HashTextField.text")); // NOI18N
        md5HashTextField.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(sha1HashLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.sha1HashLabel.text")); // NOI18N
        sha1HashLabel.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(md5HashLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.md5HashLabel.text")); // NOI18N
        md5HashLabel.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(hashValuesLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.hashValuesLabel.text")); // NOI18N
        hashValuesLabel.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(hashValuesNoteLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.hashValuesNoteLabel.text")); // NOI18N
        hashValuesNoteLabel.setEnabled(false);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(pathTextField)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(browseButton)
                .addGap(2, 2, 2))
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(pathLabel)
                    .addComponent(noFatOrphansCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, 262, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 368, Short.MAX_VALUE))
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(errorLabel)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addGroup(layout.createSequentialGroup()
                            .addComponent(timeZoneLabel)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(timeZoneComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 455, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGroup(layout.createSequentialGroup()
                            .addComponent(sectorSizeLabel)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                            .addComponent(sectorSizeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 455, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGroup(layout.createSequentialGroup()
                            .addComponent(md5HashLabel)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(md5HashTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 455, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGroup(layout.createSequentialGroup()
                            .addComponent(sha1HashLabel)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(sha1HashTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 455, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGroup(layout.createSequentialGroup()
                            .addComponent(sha256HashLabel)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(sha256HashTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 455, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(hashValuesNoteLabel)
                    .addComponent(hashValuesLabel))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(pathLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(browseButton)
                    .addComponent(pathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(noFatOrphansCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(timeZoneComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(timeZoneLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sectorSizeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(sectorSizeLabel))
                .addGap(39, 39, 39)
                .addComponent(hashValuesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(md5HashTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(md5HashLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sha1HashTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(sha1HashLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sha256HashTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(sha256HashLabel))
                .addGap(18, 18, 18)
                .addComponent(hashValuesNoteLabel)
                .addGap(18, 18, 18)
                .addComponent(errorLabel)
                .addContainerGap(51, Short.MAX_VALUE))
        );
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

    public void reset() {
        //reset the UI elements to default 
        pathTextField.setText(null);
        this.md5HashTextField.setText(null);
        this.sha1HashTextField.setText(null);
        this.sha256HashTextField.setText(null);
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
        "ImageFilePanel.validatePanel.invalidSHA256=Invalid SHA256 hash",})
    public boolean validatePanel() {
        errorLabel.setVisible(false);

        String path = getContentPaths();
        if (!isImagePathValid()) {
            return false;
        }

        if (!StringUtils.isBlank(getMd5()) && !HashUtility.isValidMd5Hash(getMd5())) {
            errorLabel.setVisible(true);
            errorLabel.setText(Bundle.ImageFilePanel_validatePanel_invalidMD5());
            return false;
        }

        if (!StringUtils.isBlank(getSha1()) && !HashUtility.isValidSha1Hash(getSha1())) {
            errorLabel.setVisible(true);
            errorLabel.setText(Bundle.ImageFilePanel_validatePanel_invalidSHA1());
            return false;
        }

        if (!StringUtils.isBlank(getSha256()) && !HashUtility.isValidSha256Hash(getSha256())) {
            errorLabel.setVisible(true);
            errorLabel.setText(Bundle.ImageFilePanel_validatePanel_invalidSHA256());
            return false;
        }

        if (!PathValidator.isValidForCaseType(path, Case.getCurrentCase().getCaseType())) {
            errorLabel.setVisible(true);
            errorLabel.setText(Bundle.ImageFilePanel_validatePanel_dataSourceOnCDriveError());
        }

        return true;
    }
    
    private boolean isImagePathValid() {
        String path = getContentPaths();
        
        if (StringUtils.isBlank(path) || (!(new File(path).isFile() || DriveUtils.isPhysicalDrive(path) || DriveUtils.isPartition(path)))) {
            return false;
        }
        
        return true;
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

    @Override
    public void insertUpdate(DocumentEvent e) {
        updateHelper();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        updateHelper();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        updateHelper();
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
}
