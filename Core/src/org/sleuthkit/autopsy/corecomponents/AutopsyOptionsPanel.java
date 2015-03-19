/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2014 Basis Technology Corp.
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

import java.awt.Color;
import javax.swing.DefaultComboBoxModel;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.core.UserPreferences;

/**
 * Options panel that allow users to set application preferences.
 */
final class AutopsyOptionsPanel extends javax.swing.JPanel {

    AutopsyOptionsPanel(AutopsyOptionsPanelController controller) {
        initComponents();
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        Integer fileIngestThreadCountChoices[];
        int recommendedFileIngestThreadCount;
        if (availableProcessors >= 16) {
            fileIngestThreadCountChoices = new Integer[]{1, 2, 4, 6, 8, 12, 16};
            if (availableProcessors >= 18) {
                recommendedFileIngestThreadCount = 16;
            } else {
                recommendedFileIngestThreadCount = 12;
            }
        } else if (availableProcessors >= 12 && availableProcessors <= 15) {
            fileIngestThreadCountChoices = new Integer[]{1, 2, 4, 6, 8, 12};
            if (availableProcessors >= 14) {
                recommendedFileIngestThreadCount = 12;
            } else {
                recommendedFileIngestThreadCount = 8;
            }
        } else if (availableProcessors >= 8 && availableProcessors <= 11) {
            fileIngestThreadCountChoices = new Integer[]{1, 2, 4, 6, 8};
            if (availableProcessors >= 10) {
                recommendedFileIngestThreadCount = 8;
            } else {
                recommendedFileIngestThreadCount = 6;
            }
        } else if (availableProcessors >= 6 && availableProcessors <= 7) {
            fileIngestThreadCountChoices = new Integer[]{1, 2, 4, 6};
            recommendedFileIngestThreadCount = 4;
        } else if (availableProcessors >= 4 && availableProcessors <= 5) {
            fileIngestThreadCountChoices = new Integer[]{1, 2, 4};
            recommendedFileIngestThreadCount = 2;
        } else if (availableProcessors >= 2 && availableProcessors <= 3) {
            fileIngestThreadCountChoices = new Integer[]{1, 2};
            recommendedFileIngestThreadCount = 1;
        } else {
            fileIngestThreadCountChoices = new Integer[]{1};
            recommendedFileIngestThreadCount = 1;
        }
        numberOfFileIngestThreadsComboBox.setModel(new DefaultComboBoxModel<>(fileIngestThreadCountChoices));
        restartRequiredLabel.setText(NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.restartRequiredLabel.text", recommendedFileIngestThreadCount));

        TextPrompt tpHostnameOrIp = new TextPrompt("Hostname or IP Address", tbHostnameOrIp);
        TextPrompt tpPortNumber = new TextPrompt("Port Number", tbPortNumber);
        TextPrompt tpDatabaseName = new TextPrompt("Database Name", tbDatabaseName);
        TextPrompt tpUsername = new TextPrompt("User Name", tbUsername);
        TextPrompt tpPassword = new TextPrompt("Password", tbPassword);

        tpHostnameOrIp.setForeground(Color.LIGHT_GRAY);
        tpPortNumber.setForeground(Color.LIGHT_GRAY);
        tpDatabaseName.setForeground(Color.LIGHT_GRAY);
        tpUsername.setForeground(Color.LIGHT_GRAY);
        tpPassword.setForeground(Color.LIGHT_GRAY);

        float alpha=0.9f; // Mostly opaque
        tpHostnameOrIp.changeAlpha(alpha);
        tpPortNumber.changeAlpha(alpha);
        tpDatabaseName.changeAlpha(alpha);
        tpUsername.changeAlpha(alpha);
        tpPassword.changeAlpha(alpha);
        
// TODO listen to changes in form fields and call controller.changed()
    }

    void load() {
        boolean keepPreferredViewer = UserPreferences.keepPreferredContentViewer();
        keepCurrentViewerRB.setSelected(keepPreferredViewer);
        useBestViewerRB.setSelected(!keepPreferredViewer);
        dataSourcesHideKnownCB.setSelected(UserPreferences.hideKnownFilesInDataSourcesTree());
        viewsHideKnownCB.setSelected(UserPreferences.hideKnownFilesInViewsTree());
        boolean useLocalTime = UserPreferences.displayTimesInLocalTime();
        useLocalTimeRB.setSelected(useLocalTime);
        useGMTTimeRB.setSelected(!useLocalTime);
        numberOfFileIngestThreadsComboBox.setSelectedItem(UserPreferences.numberOfFileIngestThreads());
        tbHostnameOrIp.setText(UserPreferences.hostNameOrIp());
        tbPortNumber.setText(UserPreferences.portNumber());
        tbDatabaseName.setText(UserPreferences.databaseName());
        tbUsername.setText(UserPreferences.userName());
        tbPassword.setText(UserPreferences.password());
    }

    void store() {
        UserPreferences.setKeepPreferredContentViewer(keepCurrentViewerRB.isSelected());
        UserPreferences.setHideKnownFilesInDataSourcesTree(dataSourcesHideKnownCB.isSelected());
        UserPreferences.setHideKnownFilesInViewsTree(viewsHideKnownCB.isSelected());
        UserPreferences.setDisplayTimesInLocalTime(useLocalTimeRB.isSelected());
        UserPreferences.setNumberOfFileIngestThreads((Integer) numberOfFileIngestThreadsComboBox.getSelectedItem());
        UserPreferences.setHostNameOrIp(tbHostnameOrIp.getText());
        UserPreferences.setPortNumber(tbPortNumber.getText());
        UserPreferences.setDatabaseName(tbDatabaseName.getText());
        UserPreferences.setUserName(tbUsername.getText());
        UserPreferences.setPassword(new String(tbPassword.getPassword()));
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
        buttonGroupProcTimeOut = new javax.swing.ButtonGroup();
        useBestViewerRB = new javax.swing.JRadioButton();
        keepCurrentViewerRB = new javax.swing.JRadioButton();
        jLabelSelectFile = new javax.swing.JLabel();
        jLabelTimeDisplay = new javax.swing.JLabel();
        useLocalTimeRB = new javax.swing.JRadioButton();
        useGMTTimeRB = new javax.swing.JRadioButton();
        jLabelHideKnownFiles = new javax.swing.JLabel();
        dataSourcesHideKnownCB = new javax.swing.JCheckBox();
        viewsHideKnownCB = new javax.swing.JCheckBox();
        jLabelNumThreads = new javax.swing.JLabel();
        numberOfFileIngestThreadsComboBox = new javax.swing.JComboBox<Integer>();
        restartRequiredLabel = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        tbHostnameOrIp = new javax.swing.JTextField();
        tbPortNumber = new javax.swing.JTextField();
        tbDatabaseName = new javax.swing.JTextField();
        tbUsername = new javax.swing.JTextField();
        tbPassword = new javax.swing.JPasswordField();
        jLabel1 = new javax.swing.JLabel();

        buttonGroup1.add(useBestViewerRB);
        useBestViewerRB.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(useBestViewerRB, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.useBestViewerRB.text")); // NOI18N
        useBestViewerRB.setToolTipText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.useBestViewerRB.toolTipText")); // NOI18N

        buttonGroup1.add(keepCurrentViewerRB);
        org.openide.awt.Mnemonics.setLocalizedText(keepCurrentViewerRB, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.keepCurrentViewerRB.text")); // NOI18N
        keepCurrentViewerRB.setToolTipText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.keepCurrentViewerRB.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabelSelectFile, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jLabelSelectFile.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabelTimeDisplay, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jLabelTimeDisplay.text")); // NOI18N

        buttonGroup3.add(useLocalTimeRB);
        useLocalTimeRB.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(useLocalTimeRB, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.useLocalTimeRB.text")); // NOI18N

        buttonGroup3.add(useGMTTimeRB);
        org.openide.awt.Mnemonics.setLocalizedText(useGMTTimeRB, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.useGMTTimeRB.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabelHideKnownFiles, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jLabelHideKnownFiles.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(dataSourcesHideKnownCB, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.dataSourcesHideKnownCB.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(viewsHideKnownCB, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.viewsHideKnownCB.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabelNumThreads, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jLabelNumThreads.text")); // NOI18N

        restartRequiredLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(restartRequiredLabel, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.restartRequiredLabel.text")); // NOI18N

        jPanel1.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        tbHostnameOrIp.setText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.tbHostnameOrIp.text")); // NOI18N
        tbHostnameOrIp.setToolTipText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.tbHostnameOrIp.toolTipText")); // NOI18N

        tbPortNumber.setText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.tbPortNumber.text")); // NOI18N
        tbPortNumber.setToolTipText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.tbPortNumber.toolTipText")); // NOI18N

        tbDatabaseName.setText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.tbDatabaseName.text")); // NOI18N
        tbDatabaseName.setToolTipText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.tbDatabaseName.toolTipText")); // NOI18N

        tbUsername.setText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.tbUsername.text")); // NOI18N
        tbUsername.setToolTipText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.tbUsername.toolTipText")); // NOI18N

        tbPassword.setText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.tbPassword.text")); // NOI18N

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tbHostnameOrIp)
                    .addComponent(tbPortNumber)
                    .addComponent(tbDatabaseName)
                    .addComponent(tbUsername)
                    .addComponent(tbPassword, javax.swing.GroupLayout.DEFAULT_SIZE, 316, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(tbHostnameOrIp, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbPortNumber, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbDatabaseName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbUsername, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jLabel1.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabelHideKnownFiles)
                            .addComponent(jLabelTimeDisplay)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(10, 10, 10)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(useLocalTimeRB)
                                    .addComponent(useGMTTimeRB)))
                            .addComponent(jLabelSelectFile)
                            .addComponent(jLabelNumThreads)
                            .addComponent(jLabel1))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(numberOfFileIngestThreadsComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(restartRequiredLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 529, Short.MAX_VALUE)
                                .addContainerGap())
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(keepCurrentViewerRB)
                                    .addComponent(useBestViewerRB)
                                    .addComponent(dataSourcesHideKnownCB)
                                    .addComponent(viewsHideKnownCB)
                                    .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(0, 0, Short.MAX_VALUE))))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
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
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabelTimeDisplay)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(useLocalTimeRB)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(useGMTTimeRB)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabelNumThreads)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(numberOfFileIngestThreadsComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(restartRequiredLabel))
                .addGap(18, 18, 18)
                .addComponent(jLabel1)
                .addGap(4, 4, 4)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(47, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup3;
    private javax.swing.ButtonGroup buttonGroupProcTimeOut;
    private javax.swing.JCheckBox dataSourcesHideKnownCB;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabelHideKnownFiles;
    private javax.swing.JLabel jLabelNumThreads;
    private javax.swing.JLabel jLabelSelectFile;
    private javax.swing.JLabel jLabelTimeDisplay;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JRadioButton keepCurrentViewerRB;
    private javax.swing.JComboBox<Integer> numberOfFileIngestThreadsComboBox;
    private javax.swing.JLabel restartRequiredLabel;
    private javax.swing.JTextField tbDatabaseName;
    private javax.swing.JTextField tbHostnameOrIp;
    private javax.swing.JPasswordField tbPassword;
    private javax.swing.JTextField tbPortNumber;
    private javax.swing.JTextField tbUsername;
    private javax.swing.JRadioButton useBestViewerRB;
    private javax.swing.JRadioButton useGMTTimeRB;
    private javax.swing.JRadioButton useLocalTimeRB;
    private javax.swing.JCheckBox viewsHideKnownCB;
    // End of variables declaration//GEN-END:variables
}
