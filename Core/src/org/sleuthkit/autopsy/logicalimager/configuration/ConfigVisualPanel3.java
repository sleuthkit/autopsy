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
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import org.apache.commons.io.FileUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;

public class ConfigVisualPanel3 extends javax.swing.JPanel {

    private static final Logger logger = Logger.getLogger(ConfigVisualPanel3.class.getName());
    private static final String SAVED_LOGICAL_IMAGER = "SAVED_LOGICAL_IMAGER";
    private static final long serialVersionUID = 1L;
    private boolean hasBeenSaved = false;
    private String configFilename;
    private LogicalImagerConfig config;

    /**
     * Creates new form ConfigVisualPanel3
     */
    @NbBundle.Messages({"ConfigVisualPanel3.copyStatus.notSaved=File has not been saved.",
        "ConfigVisualPanel3.copyStatus.savingInProgress=Saving file, please wait.",
        "ConfigVisualPanel3.copyStatus.saved=Saved",
        "ConfigVisualPanel3.copyStatus.error=Unable to save file."})
    public ConfigVisualPanel3() {
        initComponents();
        configStatusLabel.setText(Bundle.ConfigVisualPanel3_copyStatus_notSaved());
        executableStatusLabel.setText(Bundle.ConfigVisualPanel3_copyStatus_notSaved());
    }

    boolean isSaved() {
        return hasBeenSaved;
    }

    @NbBundle.Messages({
        "# {0} - configFilename",
        "ConfigVisualPanel3.failedToSaveConfigMsg=Failed to save configuration file: {0}",
        "# {0} - reason",
        "ConfigVisualPanel3.reason=\nReason: ",
        "ConfigVisualPanel3.failedToSaveExeMsg=Failed to save tsk_logical_imager.exe file",})
    void saveConfigFile() {
        boolean saveSuccess = true;
        executableStatusLabel.setForeground(Color.BLACK);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        configStatusLabel.setText(Bundle.ConfigVisualPanel3_copyStatus_savingInProgress());
        executableStatusLabel.setText(Bundle.ConfigVisualPanel3_copyStatus_savingInProgress());
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
            executableStatusLabel.setText(Bundle.ConfigVisualPanel3_copyStatus_error());
            executableStatusLabel.setForeground(Color.RED);
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

    private void writeTskLogicalImagerExe(Path destDir) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("tsk_logical_imager.exe")) { // NON-NLS
            File destFile = Paths.get(destDir.toString(), "tsk_logical_imager.exe").toFile(); // NON-NLS
            FileUtils.copyInputStreamToFile(in, destFile);
        }
    }

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

        saveButton = new javax.swing.JButton();
        descriptionScrollPane = new javax.swing.JScrollPane();
        descriptionTextArea = new javax.swing.JTextArea();
        configLabel = new javax.swing.JLabel();
        configStatusLabel = new javax.swing.JLabel();
        executableLabel = new javax.swing.JLabel();
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
        descriptionTextArea.setFont(new java.awt.Font("Tahoma", 0, 11)); // NOI18N
        descriptionTextArea.setLineWrap(true);
        descriptionTextArea.setRows(5);
        descriptionTextArea.setText(org.openide.util.NbBundle.getMessage(ConfigVisualPanel3.class, "ConfigVisualPanel3.descriptionTextArea.text")); // NOI18N
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
                    .addComponent(descriptionScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 380, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(saveButton, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                    .addComponent(executableLabel)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                    .addComponent(executableStatusLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                    .addComponent(configLabel)
                                    .addGap(18, 18, 18)
                                    .addComponent(configStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE))))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(descriptionScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(57, 57, 57)
                .addComponent(saveButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 64, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(configLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(configStatusLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(executableLabel)
                    .addComponent(executableStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(35, 35, 35))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed
        saveConfigFile();
    }//GEN-LAST:event_saveButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel configLabel;
    private javax.swing.JLabel configStatusLabel;
    private javax.swing.JScrollPane descriptionScrollPane;
    private javax.swing.JTextArea descriptionTextArea;
    private javax.swing.JLabel executableLabel;
    private javax.swing.JLabel executableStatusLabel;
    private javax.swing.JButton saveButton;
    // End of variables declaration//GEN-END:variables

    void setConfigInfoForSaving(String configFilename, LogicalImagerConfig config) {
        this.configFilename = configFilename;
        this.config = config;
    }
}
