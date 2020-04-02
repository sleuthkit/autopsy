/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.logicalimager.configuration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import java.awt.Color;
import java.awt.Cursor;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import static org.sleuthkit.autopsy.logicalimager.configuration.CreateLogicalImagerAction.getLogicalImagerExe;

/**
 * Configuration visual panel 3
 */
class ConfigVisualPanel3 extends javax.swing.JPanel {

    private static final Logger logger = Logger.getLogger(ConfigVisualPanel3.class.getName());
    private static final String SAVED_LOGICAL_IMAGER = "SAVED_LOGICAL_IMAGER";
    private static final long serialVersionUID = 1L;
    private boolean hasBeenSaved = false;
    private String configFilename;
    private LogicalImagerConfig config;

    /**
     * Creates new form ConfigVisualPanel3
     */
    @NbBundle.Messages({"ConfigVisualPanel3.copyStatus.notSaved=File has not been saved",
        "ConfigVisualPanel3.copyStatus.savingInProgress=Saving file, please wait",
        "ConfigVisualPanel3.copyStatus.saved=Saved",
        "ConfigVisualPanel3.copyStatus.error=Unable to save file"})
    ConfigVisualPanel3() {
        initComponents();
    }

    final void resetPanel() {
        hasBeenSaved = false;
        configStatusLabel.setText(Bundle.ConfigVisualPanel3_copyStatus_notSaved());
        executableStatusLabel.setText(Bundle.ConfigVisualPanel3_copyStatus_notSaved());
    }

    @NbBundle.Messages({
        "ConfigVisualPanel3.saveConfigurationFile=Save imager"
    })
    @Override
    public String getName() {
        return Bundle.ConfigVisualPanel3_saveConfigurationFile();
    }

    /**
     * Identifies whether the configuration has been saved
     *
     * @return true if it has been saved, false otherwise
     */
    boolean isSaved() {
        return hasBeenSaved;
    }

    /**
     * Save the current configuration file and copy the logical imager
     * executable to the same location.
     */
    @NbBundle.Messages({
        "# {0} - configFilename",
        "ConfigVisualPanel3.failedToSaveConfigMsg=Failed to save configuration file: {0}",
        "# {0} - reason",
        "ConfigVisualPanel3.reason=\nReason: {0}",
        "ConfigVisualPanel3.failedToSaveExeMsg=Failed to save tsk_logical_imager.exe file"
    })
    void saveConfigFile() {
        boolean saveSuccess = true;
        executableStatusLabel.setForeground(Color.BLACK);
        configStatusLabel.setText(Bundle.ConfigVisualPanel3_copyStatus_savingInProgress());
        executableStatusLabel.setText(Bundle.ConfigVisualPanel3_copyStatus_savingInProgress());
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        GsonBuilder gsonBuilder = new GsonBuilder()
                .setPrettyPrinting()
                .excludeFieldsWithoutExposeAnnotation()
                .disableHtmlEscaping();
        Gson gson = gsonBuilder.create();
        String toJson = gson.toJson(config);
        try {
            List<String> lines = Arrays.asList(toJson.split("\\n"));
            FileUtils.writeLines(new File(configFilename), "UTF-8", lines, System.getProperty("line.separator")); // NON-NLS
            configStatusLabel.setText(Bundle.ConfigVisualPanel3_copyStatus_saved());
        } catch (IOException ex) {
            saveSuccess = false;
            configStatusLabel.setText(Bundle.ConfigVisualPanel3_copyStatus_error());
            configStatusLabel.setForeground(Color.RED);
            JOptionPane.showMessageDialog(this, Bundle.ConfigVisualPanel3_failedToSaveConfigMsg(configFilename)
                    + Bundle.ConfigVisualPanel3_reason(ex.getMessage()));
        } catch (JsonIOException jioe) {
            saveSuccess = false;
            executableStatusLabel.setText(Bundle.ConfigVisualPanel3_copyStatus_error());
            executableStatusLabel.setForeground(Color.RED);
            logger.log(Level.SEVERE, "Failed to save configuration file: " + configFilename, jioe); // NON-NLS
            JOptionPane.showMessageDialog(this, Bundle.ConfigVisualPanel3_failedToSaveConfigMsg(configFilename)
                    + Bundle.ConfigVisualPanel3_reason(jioe.getMessage()));
        }
        try {
            writeTskLogicalImagerExe(Paths.get(configFilename).getParent());
            executableStatusLabel.setText(Bundle.ConfigVisualPanel3_copyStatus_saved());
        } catch (IOException ex) {
            saveSuccess = false;
            executableStatusLabel.setText(Bundle.ConfigVisualPanel3_copyStatus_error());
            executableStatusLabel.setForeground(Color.RED);
            logger.log(Level.SEVERE, "Failed to save tsk_logical_imager.exe file", ex); // NON-NLS
            JOptionPane.showMessageDialog(this, Bundle.ConfigVisualPanel3_failedToSaveExeMsg()
                    + Bundle.ConfigVisualPanel3_reason(ex.getMessage()));
        }
        if (saveSuccess) {
            hasBeenSaved = true;
            firePropertyChange(SAVED_LOGICAL_IMAGER, false, true); // NON-NLS
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    /**
     * Writes the logical imager executable to the specified location.
     *
     * @param destDir The destination directory.
     *
     * @throws IOException If the executable cannot be found or copying fails.
     */
    @NbBundle.Messages({
        "ConfigVisualPanel3.errorMsg.cannotFindLogicalImager=Cannot locate logical imager, cannot copy to destination"
    })
    private void writeTskLogicalImagerExe(Path destDir) throws IOException {
        File logicalImagerExe = getLogicalImagerExe();
        if (logicalImagerExe != null && logicalImagerExe.exists()) {
            FileUtils.copyFileToDirectory(getLogicalImagerExe(), destDir.toFile());
        } else {
            throw new IOException(Bundle.ConfigVisualPanel3_errorMsg_cannotFindLogicalImager());
        }
    }

    /**
     * The name of the event which is sent when the configuration and executable
     * have been saved.
     *
     * @return SAVED_LOGICAL_IMAGER
     */
    static String getSavedEventName() {
        return SAVED_LOGICAL_IMAGER;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.JButton saveButton = new javax.swing.JButton();
        descriptionScrollPane = new javax.swing.JScrollPane();
        descriptionTextArea = new javax.swing.JTextArea();
        javax.swing.JLabel configLabel = new javax.swing.JLabel();
        configStatusLabel = new javax.swing.JLabel();
        javax.swing.JLabel executableLabel = new javax.swing.JLabel();
        executableStatusLabel = new javax.swing.JLabel();

        org.openide.awt.Mnemonics.setLocalizedText(saveButton, org.openide.util.NbBundle.getMessage(ConfigVisualPanel3.class, "ConfigVisualPanel3.saveButton.text")); // NOI18N
        saveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveButtonActionPerformed(evt);
            }
        });

        descriptionTextArea.setEditable(false);
        descriptionTextArea.setBackground(new java.awt.Color(240, 240, 240));
        descriptionTextArea.setColumns(20);
        descriptionTextArea.setLineWrap(true);
        descriptionTextArea.setRows(5);
        descriptionTextArea.setWrapStyleWord(true);
        descriptionTextArea.setEnabled(false);
        descriptionScrollPane.setViewportView(descriptionTextArea);

        org.openide.awt.Mnemonics.setLocalizedText(configLabel, org.openide.util.NbBundle.getMessage(ConfigVisualPanel3.class, "ConfigVisualPanel3.configLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(executableLabel, org.openide.util.NbBundle.getMessage(ConfigVisualPanel3.class, "ConfigVisualPanel3.executableLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(executableStatusLabel, org.openide.util.NbBundle.getMessage(ConfigVisualPanel3.class, "ConfigVisualPanel3.executableStatusLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(configLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(executableLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(configStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 237, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(executableStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 238, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 10, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(descriptionScrollPane)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(saveButton, javax.swing.GroupLayout.PREFERRED_SIZE, 101, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, Short.MAX_VALUE)))
                        .addContainerGap())))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(descriptionScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(saveButton)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(configLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(configStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(executableLabel)
                    .addComponent(executableStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(100, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed
        saveConfigFile();
    }//GEN-LAST:event_saveButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel configStatusLabel;
    private javax.swing.JScrollPane descriptionScrollPane;
    private javax.swing.JTextArea descriptionTextArea;
    private javax.swing.JLabel executableStatusLabel;
    // End of variables declaration//GEN-END:variables

    /**
     * Set the information necessary to save the configuration
     *
     * @param configFile the path to the json configuration file
     * @param config     the configuration to save
     */
    @NbBundle.Messages({
        "# {0} - configurationLocation",
        "ConfigVisualPanel3.description.text=Press Save to write the imaging tool and configuration file to the destination.\nDestination: {0}"
    })
    void setConfigInfoForSaving(String configFile, LogicalImagerConfig config) {
        this.configFilename = configFile;
        this.config = config;
        descriptionTextArea.setText(Bundle.ConfigVisualPanel3_description_text(FilenameUtils.getFullPath(configFilename)));
    }
}
