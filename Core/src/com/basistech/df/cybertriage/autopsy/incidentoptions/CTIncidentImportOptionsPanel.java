/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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
package com.basistech.df.cybertriage.autopsy.incidentoptions;

import com.basistech.df.cybertriage.autopsy.ctoptions.subpanel.CTOptionsSubPanel;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.stream.Stream;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JFileChooser;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.apache.commons.lang3.StringUtils;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.AutopsyContentProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.guiutils.JFileChooserFactory;

/**
 * Options panel for CyberTriage options for importing a CyberTriage incident
 */
@ServiceProvider(service = CTOptionsSubPanel.class)
public class CTIncidentImportOptionsPanel extends CTOptionsSubPanel {

    private static final Logger logger = Logger.getLogger(CTIncidentImportOptionsPanel.class.getName());

    private static final String CT_IMPORTER_DOC_LINK = "https://docs.cybertriage.com/en/latest/chapters/integrations/autopsy.html";
    
    private static final String CT_STANDARD_CONTENT_PROVIDER_NAME = "CTStandardContentProvider";
    
    private final JFileChooserFactory fileRepoChooserFactory = new JFileChooserFactory();
    private final CTSettingsPersistence ctPersistence = CTSettingsPersistence.getInstance();

    private static String getHtmlLink(String url) {
        return "<html><span style=\"color: blue; text-decoration: underline\">" + url + "</span></html>";
    }

    /**
     * Creates new form CTIncidentImportOptionsPanel
     */
    public CTIncidentImportOptionsPanel() {
        initComponents();
        this.fileRepoPathField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                fireSettingsChanged();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                fireSettingsChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                fireSettingsChanged();
            }
        });

        Case.addEventTypeSubscriber(Collections.singleton(Case.Events.CURRENT_CASE), (evt) -> {
            CTIncidentImportOptionsPanel.this.setEnabledItems(evt.getNewValue() != null);
        });
    }

    private void setCTSettingsDisplay(CTSettings ctSettings) {
        this.fileRepoPathField.setText(ctSettings.getFileRepoPath());
    }

    @Override
    public synchronized void saveSettings() {
        ctPersistence.saveCTSettings(getSettings());
    }

    @Override
    public synchronized void loadSettings() {
        CTSettings ctSettings = ctPersistence.loadCTSettings();
        setCTSettingsDisplay(ctSettings);
        setModuleDetected();
        setEnabledItems(Case.isCaseOpen());
    }
    
    @Messages({
        "CTIncidentImportOptionsPanel_setModuleDetected_detected=Detected",
        "CTIncidentImportOptionsPanel_setModuleDetected_notDetected=Not Detected"
    })
    private void setModuleDetected() {
        Collection<? extends AutopsyContentProvider> contentProviders = Lookup.getDefault().lookupAll(AutopsyContentProvider.class);
        boolean detected = ((Collection<? extends AutopsyContentProvider>) (contentProviders != null ? contentProviders : Collections.emptyList())).stream()
                .anyMatch(p -> p != null && StringUtils.defaultString(p.getName()).toUpperCase().startsWith(CT_STANDARD_CONTENT_PROVIDER_NAME.toUpperCase()));
        
        this.importModuleDetected.setText(detected 
                ? Bundle.CTIncidentImportOptionsPanel_setModuleDetected_detected() 
                : Bundle.CTIncidentImportOptionsPanel_setModuleDetected_notDetected());
    }

    private void setEnabledItems(boolean caseOpen) {
        this.caseOpenWarningLabel.setVisible(caseOpen);
        this.fileRepoBrowseButton.setEnabled(!caseOpen);
        this.fileRepoPathField.setEnabled(!caseOpen);
    }

    private void fireSettingsChanged() {
        this.firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }

    private CTSettings getSettings() {
        return new CTSettings().setFileRepoPath(this.fileRepoPathField.getText());
    }

    @Override
    public boolean valid() {
        return new File(this.fileRepoPathField.getText()).isDirectory();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        incidentTextPanel = new javax.swing.JPanel();
        incidentTextLabel = new javax.swing.JLabel();
        javax.swing.JLabel importModule = new javax.swing.JLabel();
        importModuleDetected = new javax.swing.JLabel();
        instructionsPanel = new javax.swing.JPanel();
        instructionsTextLabel = new javax.swing.JLabel();
        instructionsLinkLabel = new javax.swing.JLabel();
        repoPanel = new javax.swing.JPanel();
        javax.swing.JLabel fileRepoPathLabel = new javax.swing.JLabel();
        fileRepoPathField = new javax.swing.JTextField();
        fileRepoBrowseButton = new javax.swing.JButton();
        caseOpenWarningLabel = new javax.swing.JLabel();

        setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(CTIncidentImportOptionsPanel.class, "CTIncidentImportOptionsPanel.border.title_1"))); // NOI18N
        setMaximumSize(new java.awt.Dimension(650, 2147483647));
        setPreferredSize(new java.awt.Dimension(650, 176));
        setLayout(new java.awt.GridBagLayout());

        incidentTextPanel.setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(incidentTextLabel, org.openide.util.NbBundle.getMessage(CTIncidentImportOptionsPanel.class, "CTIncidentImportOptionsPanel.incidentTextLabel.text")); // NOI18N
        incidentTextLabel.setMaximumSize(new java.awt.Dimension(600, 32));
        incidentTextLabel.setPreferredSize(null);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        incidentTextPanel.add(incidentTextLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(importModule, org.openide.util.NbBundle.getMessage(CTIncidentImportOptionsPanel.class, "CTIncidentImportOptionsPanel.importModule.text")); // NOI18N
        importModule.setPreferredSize(null);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 3);
        incidentTextPanel.add(importModule, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(importModuleDetected, org.openide.util.NbBundle.getMessage(CTIncidentImportOptionsPanel.class, "CTIncidentImportOptionsPanel.importModuleDetected.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 5);
        incidentTextPanel.add(importModuleDetected, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        add(incidentTextPanel, gridBagConstraints);

        instructionsPanel.setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(instructionsTextLabel, org.openide.util.NbBundle.getMessage(CTIncidentImportOptionsPanel.class, "CTIncidentImportOptionsPanel.instructionsTextLabel.text")); // NOI18N
        instructionsTextLabel.setPreferredSize(null);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
        instructionsPanel.add(instructionsTextLabel, gridBagConstraints);
        instructionsTextLabel.getAccessibleContext().setAccessibleName("For instructions on obtaining the module refer to:");

        org.openide.awt.Mnemonics.setLocalizedText(instructionsLinkLabel, getHtmlLink(CT_IMPORTER_DOC_LINK));
        instructionsLinkLabel.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        instructionsLinkLabel.setPreferredSize(null);
        instructionsLinkLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                instructionsLinkLabelMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        instructionsPanel.add(instructionsLinkLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        add(instructionsPanel, gridBagConstraints);

        repoPanel.setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(fileRepoPathLabel, org.openide.util.NbBundle.getMessage(CTIncidentImportOptionsPanel.class, "CTIncidentImportOptionsPanel.fileRepoPathLabel.text")); // NOI18N
        fileRepoPathLabel.setMaximumSize(new java.awt.Dimension(600, 16));
        fileRepoPathLabel.setPreferredSize(null);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        repoPanel.add(fileRepoPathLabel, gridBagConstraints);

        fileRepoPathField.setText(org.openide.util.NbBundle.getMessage(CTIncidentImportOptionsPanel.class, "CTIncidentImportOptionsPanel.fileRepoPathField.text")); // NOI18N
        fileRepoPathField.setPreferredSize(null);
        fileRepoPathField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fileRepoPathFieldActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
        repoPanel.add(fileRepoPathField, gridBagConstraints);
        fileRepoPathField.getAccessibleContext().setAccessibleName("");

        org.openide.awt.Mnemonics.setLocalizedText(fileRepoBrowseButton, org.openide.util.NbBundle.getMessage(CTIncidentImportOptionsPanel.class, "CTIncidentImportOptionsPanel.fileRepoBrowseButton.text")); // NOI18N
        fileRepoBrowseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fileRepoBrowseButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
        repoPanel.add(fileRepoBrowseButton, gridBagConstraints);

        caseOpenWarningLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/modules/hashdatabase/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(caseOpenWarningLabel, org.openide.util.NbBundle.getMessage(CTIncidentImportOptionsPanel.class, "CTIncidentImportOptionsPanel.caseOpenWarningLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
        repoPanel.add(caseOpenWarningLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        add(repoPanel, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents
    private void fileRepoBrowseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileRepoBrowseButtonActionPerformed
        JFileChooser fileChooser = fileRepoChooserFactory.getChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);

        File curSelectedDir = StringUtils.isBlank(this.fileRepoPathField.getText()) ? null : new File(this.fileRepoPathField.getText());
        if (curSelectedDir == null || !curSelectedDir.isDirectory()) {
            curSelectedDir = new File(CTSettings.getDefaultFileRepoPath());
        }

        fileChooser.setCurrentDirectory(curSelectedDir);
        fileChooser.setDialogTitle(org.openide.util.NbBundle.getMessage(CTIncidentImportOptionsPanel.class, "CTIncidentImportOptionsPanel.fileRepoFileChooser.title"));
        int retVal = fileChooser.showOpenDialog(this);
        if (retVal == JFileChooser.APPROVE_OPTION) {
            this.fileRepoPathField.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }//GEN-LAST:event_fileRepoBrowseButtonActionPerformed

    private void fileRepoPathFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileRepoPathFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_fileRepoPathFieldActionPerformed

    private void instructionsLinkLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_instructionsLinkLabelMouseClicked
        gotoLink(CT_IMPORTER_DOC_LINK);
    }//GEN-LAST:event_instructionsLinkLabelMouseClicked

    private void gotoLink(String url) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (IOException | URISyntaxException e) {
                logger.log(Level.SEVERE, "Error opening link to: " + url, e);
            }
        } else {
            logger.log(Level.WARNING, "Desktop API is not supported.  Link cannot be opened.");
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel caseOpenWarningLabel;
    private javax.swing.JButton fileRepoBrowseButton;
    private javax.swing.JTextField fileRepoPathField;
    private javax.swing.JLabel importModuleDetected;
    private javax.swing.JLabel incidentTextLabel;
    private javax.swing.JPanel incidentTextPanel;
    private javax.swing.JLabel instructionsLinkLabel;
    private javax.swing.JPanel instructionsPanel;
    private javax.swing.JLabel instructionsTextLabel;
    private javax.swing.JPanel repoPanel;
    // End of variables declaration//GEN-END:variables
}
