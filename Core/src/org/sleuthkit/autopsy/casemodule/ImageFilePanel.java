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
package org.sleuthkit.autopsy.casemodule;

import java.io.File;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import static org.sleuthkit.autopsy.casemodule.Bundle.*;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.coreutils.DriveUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PathValidator;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;

/**
 * Panel for adding an image file such as .img, .E0x, .00x, etc. Allows the user
 * to select a file as well as choose the timezone and whether to ignore orphan
 * files in FAT32.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class ImageFilePanel extends JPanel implements DocumentListener {

    private static final Logger logger = Logger.getLogger(ImageFilePanel.class.getName());
    private static final String PROP_LASTIMAGE_PATH = "LBL_LastImage_PATH"; //NON-NLS
    private static final String[] SECTOR_SIZE_CHOICES = {"Auto Detect", "512", "1024", "2048", "4096"};

    private final JFileChooser fileChooser = new JFileChooser();

    /**
     * Externally supplied name is used to store settings
     */
    private final String contextName;

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

        pathErrorLabel.setVisible(false);

        fileChooser.setDragEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooserFilters.forEach(fileChooser::addChoosableFileFilter);
        if (fileChooserFilters.isEmpty() == false) {
            fileChooser.setFileFilter(fileChooserFilters.get(0));
        }
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
        instance.pathTextField.getDocument().addDocumentListener(instance);
        return instance;
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
        descLabel = new javax.swing.JLabel();
        pathErrorLabel = new javax.swing.JLabel();
        sectorSizeLabel = new javax.swing.JLabel();
        sectorSizeComboBox = new javax.swing.JComboBox<>();

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

        org.openide.awt.Mnemonics.setLocalizedText(descLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.descLabel.text")); // NOI18N

        pathErrorLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(pathErrorLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.pathErrorLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(sectorSizeLabel, org.openide.util.NbBundle.getMessage(ImageFilePanel.class, "ImageFilePanel.sectorSizeLabel.text")); // NOI18N

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
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(timeZoneLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(timeZoneComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 215, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(pathLabel)
                    .addComponent(noFatOrphansCheckbox)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(21, 21, 21)
                        .addComponent(descLabel))
                    .addComponent(pathErrorLabel)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(sectorSizeLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(sectorSizeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(0, 20, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(pathLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(browseButton)
                    .addComponent(pathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(3, 3, 3)
                .addComponent(pathErrorLabel)
                .addGap(1, 1, 1)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(timeZoneLabel)
                    .addComponent(timeZoneComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(noFatOrphansCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(descLabel)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sectorSizeLabel)
                    .addComponent(sectorSizeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(43, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    @NbBundle.Messages({"ImageFilePanel.000.confirmationMessage=The selected file"
        + " has extenson .001 but there is a .000 file in the sequence of raw images."
        + "\nShould the .000 file be used as the start, instead of the selected .001 file?\n"})
    private void browseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseButtonActionPerformed
        String oldText = getContentPaths();
        // set the current directory of the FileChooser if the ImagePath Field is valid
        File currentDir = new File(oldText);
        if (currentDir.exists()) {
            fileChooser.setCurrentDirectory(currentDir);
        }

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = fileChooser.getSelectedFile().getPath();
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
        }

        updateHelper();
    }//GEN-LAST:event_browseButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton browseButton;
    private javax.swing.JLabel descLabel;
    private javax.swing.JCheckBox noFatOrphansCheckbox;
    private javax.swing.JLabel pathErrorLabel;
    private javax.swing.JLabel pathLabel;
    private javax.swing.JTextField pathTextField;
    private javax.swing.JComboBox<String> sectorSizeComboBox;
    private javax.swing.JLabel sectorSizeLabel;
    private javax.swing.JComboBox<String> timeZoneComboBox;
    private javax.swing.JLabel timeZoneLabel;
    // End of variables declaration//GEN-END:variables

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

    public void reset() {
        //reset the UI elements to default 
        pathTextField.setText(null);
    }

    /**
     * Should we enable the next button of the wizard?
     *
     * @return true if a proper image has been selected, false otherwise
     */
    @NbBundle.Messages({"ImageFilePanel.pathValidation.dataSourceOnCDriveError=Warning: Path to multi-user data source is on \"C:\" drive",
            "ImageFilePanel.pathValidation.getOpenCase.Error=Warning: Exception while getting open case."
            })
    public boolean validatePanel() {
        pathErrorLabel.setVisible(false);
        String path = getContentPaths();
        if (StringUtils.isBlank(path)) {
            return false;
        }

        // Display warning if there is one (but don't disable "next" button)
        try {
            if (false == PathValidator.isValidForMultiUserCase(path, Case.getCurrentCaseThrows().getCaseType())) {
                pathErrorLabel.setVisible(true);
                pathErrorLabel.setText(Bundle.ImageFilePanel_pathValidation_dataSourceOnCDriveError());
            }
        } catch (NoCurrentCaseException ex) {
            pathErrorLabel.setVisible(true);
            pathErrorLabel.setText(Bundle.ImageFilePanel_pathValidation_getOpenCase_Error());
        }

        return new File(path).isFile()
                || DriveUtils.isPhysicalDrive(path)
                || DriveUtils.isPartition(path);
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
     * to be caught by the parent panel.
     *
     */
    @NbBundle.Messages({"ImageFilePanel.moduleErr=Module Error",
        "ImageFilePanel.moduleErr.msg=A module caused an error listening to ImageFilePanel updates."
        + " See log to determine which module. Some data could be incomplete.\n"})
    private void updateHelper() {
        try {
            firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), false, true);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "ImageFilePanel listener threw exception", e); //NON-NLS
            MessageNotifyUtil.Notify.error(ImageFilePanel_moduleErr(), ImageFilePanel_moduleErr_msg());
        }
    }

    /**
     * Set the focus to the pathTextField.
     */
    public void select() {
        pathTextField.requestFocusInWindow();
    }
}
