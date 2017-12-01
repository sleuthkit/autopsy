/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import java.util.logging.Level;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.GeneralFilter;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.report.ReportBranding;

/**
 * Options panel that allow users to set application preferences.
 */
@Messages({"AutopsyOptionsPanel.agencyLogoPreview.text=<html><div style='text-align: center;'>No logo<br>selected</div></html>",
    "AutopsyOptionsPanel.logoPanel.border.title=Logo",
    "AutopsyOptionsPanel.viewPanel.border.title=View",
    "AutopsyOptionsPanel.invalidImageFile.msg=The selected file was not able to be used as an agency logo.",
    "AutopsyOptionsPanel.invalidImageFile.title=Invalid Image File"})
final class AutopsyOptionsPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private final JFileChooser fc;
    private static final Logger logger = Logger.getLogger(AutopsyOptionsPanel.class.getName());

    AutopsyOptionsPanel() {
        initComponents();
        fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setMultiSelectionEnabled(false);
        fc.setAcceptAllFileFilterUsed(false);
        fc.setFileFilter(new GeneralFilter(GeneralFilter.GRAPHIC_IMAGE_EXTS, GeneralFilter.GRAPHIC_IMG_DECR));
    }

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
        try {
            updateAgencyLogo(path);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error loading image from previously saved agency logo path", ex);
        }
    }

    private void updateAgencyLogo(String path) throws IOException {
        agencyLogoPathField.setText(path);
        ImageIcon agencyLogoIcon = new ImageIcon();
        agencyLogoPreview.setText(Bundle.AutopsyOptionsPanel_agencyLogoPreview_text());
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

    void store() {
        UserPreferences.setKeepPreferredContentViewer(keepCurrentViewerRB.isSelected());
        UserPreferences.setHideKnownFilesInDataSourcesTree(dataSourcesHideKnownCB.isSelected());
        UserPreferences.setHideKnownFilesInViewsTree(viewsHideKnownCB.isSelected());
        UserPreferences.setHideSlackFilesInDataSourcesTree(dataSourcesHideSlackCB.isSelected());
        UserPreferences.setHideSlackFilesInViewsTree(viewsHideSlackCB.isSelected());
        UserPreferences.setDisplayTimesInLocalTime(useLocalTimeRB.isSelected());
        if (!agencyLogoPathField.getText().isEmpty()) {
            File file = new File(agencyLogoPathField.getText());
            if (file.exists()) {
                ModuleSettings.setConfigSetting(ReportBranding.MODULE_NAME, ReportBranding.AGENCY_LOGO_PATH_PROP, agencyLogoPathField.getText());
            }
        }
    }

    boolean valid() {
        return true;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        buttonGroup3 = new javax.swing.ButtonGroup();
        jScrollPane1 = new javax.swing.JScrollPane();
        jPanel1 = new javax.swing.JPanel();
        logoPanel = new javax.swing.JPanel();
        agencyLogoImageLabel = new javax.swing.JLabel();
        agencyLogoPathField = new javax.swing.JTextField();
        browseLogosButton = new javax.swing.JButton();
        agencyLogoPreview = new javax.swing.JLabel();
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

        jScrollPane1.setBorder(null);

        logoPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.logoPanel.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(agencyLogoImageLabel, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.agencyLogoImageLabel.text")); // NOI18N

        agencyLogoPathField.setEditable(false);
        agencyLogoPathField.setBackground(new java.awt.Color(255, 255, 255));
        agencyLogoPathField.setText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.agencyLogoPathField.text")); // NOI18N
        agencyLogoPathField.setFocusable(false);
        agencyLogoPathField.setRequestFocusEnabled(false);

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

        javax.swing.GroupLayout logoPanelLayout = new javax.swing.GroupLayout(logoPanel);
        logoPanel.setLayout(logoPanelLayout);
        logoPanelLayout.setHorizontalGroup(
            logoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, logoPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(logoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(agencyLogoImageLabel)
                    .addGroup(logoPanelLayout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addComponent(agencyLogoPathField, javax.swing.GroupLayout.PREFERRED_SIZE, 259, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(browseLogosButton)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(agencyLogoPreview, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(149, Short.MAX_VALUE))
        );
        logoPanelLayout.setVerticalGroup(
            logoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(logoPanelLayout.createSequentialGroup()
                .addGroup(logoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(agencyLogoPreview, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(logoPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(agencyLogoImageLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(logoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(agencyLogoPathField)
                            .addComponent(browseLogosButton))))
                .addGap(0, 0, 0))
        );

        viewPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.viewPanel.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabelSelectFile, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jLabelSelectFile.text")); // NOI18N

        buttonGroup1.add(useBestViewerRB);
        org.openide.awt.Mnemonics.setLocalizedText(useBestViewerRB, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.useBestViewerRB.text")); // NOI18N
        useBestViewerRB.setToolTipText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.useBestViewerRB.toolTipText")); // NOI18N
        useBestViewerRB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useBestViewerRBActionPerformed(evt);
            }
        });

        buttonGroup1.add(keepCurrentViewerRB);
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

        buttonGroup3.add(useLocalTimeRB);
        org.openide.awt.Mnemonics.setLocalizedText(useLocalTimeRB, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.useLocalTimeRB.text")); // NOI18N
        useLocalTimeRB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useLocalTimeRBActionPerformed(evt);
            }
        });

        buttonGroup3.add(useGMTTimeRB);
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
                            .addComponent(useGMTTimeRB)
                            .addComponent(keepCurrentViewerRB)
                            .addComponent(useBestViewerRB)
                            .addGroup(viewPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(useLocalTimeRB)
                                .addComponent(dataSourcesHideSlackCB)
                                .addComponent(viewsHideSlackCB)
                                .addComponent(dataSourcesHideKnownCB)
                                .addComponent(viewsHideKnownCB))))
                    .addComponent(jLabelHideSlackFiles)
                    .addComponent(jLabelTimeDisplay)
                    .addComponent(jLabelHideKnownFiles)
                    .addComponent(jLabelSelectFile))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
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

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(viewPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(logoPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(viewPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(logoPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0))
        );

        jScrollPane1.setViewportView(jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 672, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 489, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void useBestViewerRBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useBestViewerRBActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_useBestViewerRBActionPerformed

    private void keepCurrentViewerRBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_keepCurrentViewerRBActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_keepCurrentViewerRBActionPerformed

    private void dataSourcesHideKnownCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSourcesHideKnownCBActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_dataSourcesHideKnownCBActionPerformed

    private void viewsHideKnownCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewsHideKnownCBActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_viewsHideKnownCBActionPerformed

    private void useLocalTimeRBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useLocalTimeRBActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_useLocalTimeRBActionPerformed

    private void useGMTTimeRBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useGMTTimeRBActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_useGMTTimeRBActionPerformed

    private void dataSourcesHideSlackCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSourcesHideSlackCBActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_dataSourcesHideSlackCBActionPerformed

    private void viewsHideSlackCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewsHideSlackCBActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_viewsHideSlackCBActionPerformed

    private void browseLogosButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseLogosButtonActionPerformed
        String oldLogoPath = agencyLogoPathField.getText();
        int returnState = fc.showOpenDialog(this);
        if (returnState == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getPath();
            try {
                updateAgencyLogo(path);
                firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            } catch (IOException | IndexOutOfBoundsException ex) {
                JOptionPane.showMessageDialog(null,
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
    private javax.swing.JLabel agencyLogoImageLabel;
    private javax.swing.JTextField agencyLogoPathField;
    private javax.swing.JLabel agencyLogoPreview;
    private javax.swing.JButton browseLogosButton;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup3;
    private javax.swing.JCheckBox dataSourcesHideKnownCB;
    private javax.swing.JCheckBox dataSourcesHideSlackCB;
    private javax.swing.JLabel jLabelHideKnownFiles;
    private javax.swing.JLabel jLabelHideSlackFiles;
    private javax.swing.JLabel jLabelSelectFile;
    private javax.swing.JLabel jLabelTimeDisplay;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JRadioButton keepCurrentViewerRB;
    private javax.swing.JPanel logoPanel;
    private javax.swing.JRadioButton useBestViewerRB;
    private javax.swing.JRadioButton useGMTTimeRB;
    private javax.swing.JRadioButton useLocalTimeRB;
    private javax.swing.JPanel viewPanel;
    private javax.swing.JCheckBox viewsHideKnownCB;
    private javax.swing.JCheckBox viewsHideSlackCB;
    // End of variables declaration//GEN-END:variables
}
