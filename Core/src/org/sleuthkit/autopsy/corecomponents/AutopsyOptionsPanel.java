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

import java.text.NumberFormat;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JFormattedTextField;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.core.UserPreferences;

/**
 * Options panel that allow users to set application preferences.
 */
final class AutopsyOptionsPanel extends javax.swing.JPanel {

    AutopsyOptionsPanel(AutopsyOptionsPanelController controller) {
        initComponents();
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        Integer fileIngestThreadCountChoices[] = null;
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
        
        UserPreferences.SelectedTimeOutMode storedTimeOutMode = UserPreferences.getTimeOutMode();
        switch (storedTimeOutMode) {
            case DEFAULT:
                // default time out
                jRadioButtonDefaultTimeOut.setSelected(true);
                jFormattedTextFieldProcTimeOutHrs.setEditable(false);
                int timeOutHrs = UserPreferences.getDefaultProcessTimeOutHrs();
                jFormattedTextFieldProcTimeOutHrs.setValue((long)timeOutHrs);
                break;

            case CUSTOM:
                // user specified time out
                jRadioButtonCustomTimeOut.setSelected(true);
                jFormattedTextFieldProcTimeOutHrs.setEditable(true);
                timeOutHrs = UserPreferences.getProcessTimeOutHrs();
                jFormattedTextFieldProcTimeOutHrs.setValue((long)timeOutHrs);
                break;

            default:
                // never time out
                jRadioButtonNeverTimeOut.setSelected(true);
                jFormattedTextFieldProcTimeOutHrs.setEditable(false);
                jFormattedTextFieldProcTimeOutHrs.setValue((long)0);
        }
    }

    void store() {
        UserPreferences.setKeepPreferredContentViewer(keepCurrentViewerRB.isSelected());
        UserPreferences.setHideKnownFilesInDataSourcesTree(dataSourcesHideKnownCB.isSelected());
        UserPreferences.setHideKnownFilesInViewsTree(viewsHideKnownCB.isSelected());
        UserPreferences.setDisplayTimesInLocalTime(useLocalTimeRB.isSelected());
        UserPreferences.setNumberOfFileIngestThreads((Integer) numberOfFileIngestThreadsComboBox.getSelectedItem());
        long timeOutHrs = (long) jFormattedTextFieldProcTimeOutHrs.getValue();
        UserPreferences.setProcessTimeOutHrs((int)timeOutHrs);
        
        if (jRadioButtonDefaultTimeOut.isSelected()) {
            UserPreferences.setTimeOutMode(UserPreferences.SelectedTimeOutMode.DEFAULT);
        }
        else if (jRadioButtonNeverTimeOut.isSelected()){
            UserPreferences.setTimeOutMode(UserPreferences.SelectedTimeOutMode.NEVER);
        }
        else if (jRadioButtonCustomTimeOut.isSelected()) {
            UserPreferences.setTimeOutMode(UserPreferences.SelectedTimeOutMode.CUSTOM);
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
        jLabelSetProcessTimeOut = new javax.swing.JLabel();
        jLabelProcessTimeOutUnits = new javax.swing.JLabel();
        jFormattedTextFieldProcTimeOutHrs = new JFormattedTextField(NumberFormat.getIntegerInstance());
        jRadioButtonDefaultTimeOut = new javax.swing.JRadioButton();
        jRadioButtonNeverTimeOut = new javax.swing.JRadioButton();
        jRadioButtonCustomTimeOut = new javax.swing.JRadioButton();

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

        org.openide.awt.Mnemonics.setLocalizedText(jLabelSetProcessTimeOut, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jLabelSetProcessTimeOut.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabelProcessTimeOutUnits, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jLabelProcessTimeOutUnits.text")); // NOI18N

        jFormattedTextFieldProcTimeOutHrs.setText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jFormattedTextFieldProcTimeOutHrs.text")); // NOI18N

        buttonGroupProcTimeOut.add(jRadioButtonDefaultTimeOut);
        org.openide.awt.Mnemonics.setLocalizedText(jRadioButtonDefaultTimeOut, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jRadioButtonDefaultTimeOut.text")); // NOI18N
        jRadioButtonDefaultTimeOut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButtonDefaultTimeOutActionPerformed(evt);
            }
        });

        buttonGroupProcTimeOut.add(jRadioButtonNeverTimeOut);
        org.openide.awt.Mnemonics.setLocalizedText(jRadioButtonNeverTimeOut, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jRadioButtonNeverTimeOut.text")); // NOI18N
        jRadioButtonNeverTimeOut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButtonNeverTimeOutActionPerformed(evt);
            }
        });

        buttonGroupProcTimeOut.add(jRadioButtonCustomTimeOut);
        org.openide.awt.Mnemonics.setLocalizedText(jRadioButtonCustomTimeOut, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jRadioButtonCustomTimeOut.text")); // NOI18N
        jRadioButtonCustomTimeOut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButtonCustomTimeOutActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(keepCurrentViewerRB)
                            .addComponent(useBestViewerRB)
                            .addComponent(dataSourcesHideKnownCB)
                            .addComponent(viewsHideKnownCB)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(numberOfFileIngestThreadsComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(restartRequiredLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addContainerGap())))
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
                            .addComponent(jLabelNumThreads))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jRadioButtonDefaultTimeOut)
                            .addComponent(jLabelSetProcessTimeOut)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jFormattedTextFieldProcTimeOutHrs, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabelProcessTimeOutUnits))
                            .addComponent(jRadioButtonNeverTimeOut)
                            .addComponent(jRadioButtonCustomTimeOut))
                        .addGap(0, 0, Short.MAX_VALUE))))
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
                .addComponent(jLabelSetProcessTimeOut)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jRadioButtonDefaultTimeOut)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jRadioButtonNeverTimeOut)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jRadioButtonCustomTimeOut)
                .addGap(5, 5, 5)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelProcessTimeOutUnits)
                    .addComponent(jFormattedTextFieldProcTimeOutHrs, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(27, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void jRadioButtonDefaultTimeOutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButtonDefaultTimeOutActionPerformed
        int timeOutSec = UserPreferences.getDefaultProcessTimeOutHrs();
        jFormattedTextFieldProcTimeOutHrs.setValue((long)timeOutSec);
        jFormattedTextFieldProcTimeOutHrs.setEditable(false);
    }//GEN-LAST:event_jRadioButtonDefaultTimeOutActionPerformed

    private void jRadioButtonNeverTimeOutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButtonNeverTimeOutActionPerformed
        jFormattedTextFieldProcTimeOutHrs.setValue((long)0);
        jFormattedTextFieldProcTimeOutHrs.setEditable(false);
    }//GEN-LAST:event_jRadioButtonNeverTimeOutActionPerformed

    private void jRadioButtonCustomTimeOutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButtonCustomTimeOutActionPerformed
        jFormattedTextFieldProcTimeOutHrs.setEditable(true);
    }//GEN-LAST:event_jRadioButtonCustomTimeOutActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup3;
    private javax.swing.ButtonGroup buttonGroupProcTimeOut;
    private javax.swing.JCheckBox dataSourcesHideKnownCB;
    private javax.swing.JFormattedTextField jFormattedTextFieldProcTimeOutHrs;
    private javax.swing.JLabel jLabelHideKnownFiles;
    private javax.swing.JLabel jLabelNumThreads;
    private javax.swing.JLabel jLabelProcessTimeOutUnits;
    private javax.swing.JLabel jLabelSelectFile;
    private javax.swing.JLabel jLabelSetProcessTimeOut;
    private javax.swing.JLabel jLabelTimeDisplay;
    private javax.swing.JRadioButton jRadioButtonCustomTimeOut;
    private javax.swing.JRadioButton jRadioButtonDefaultTimeOut;
    private javax.swing.JRadioButton jRadioButtonNeverTimeOut;
    private javax.swing.JRadioButton keepCurrentViewerRB;
    private javax.swing.JComboBox<Integer> numberOfFileIngestThreadsComboBox;
    private javax.swing.JLabel restartRequiredLabel;
    private javax.swing.JRadioButton useBestViewerRB;
    private javax.swing.JRadioButton useGMTTimeRB;
    private javax.swing.JRadioButton useLocalTimeRB;
    private javax.swing.JCheckBox viewsHideKnownCB;
    // End of variables declaration//GEN-END:variables
}
