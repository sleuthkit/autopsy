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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.core.UserPreferences;

/**
 * Options panel that allow users to set application preferences.
 */
final class AutopsyOptionsPanel extends javax.swing.JPanel {

    AutopsyOptionsPanel() {
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
        // TODO listen to changes in form fields and call controller.changed()
        DocumentListener docListener = new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            }
        };
        this.jFormattedTextFieldProcTimeOutHrs.getDocument().addDocumentListener(docListener);
        
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
        numberOfFileIngestThreadsComboBox.setSelectedItem(UserPreferences.numberOfFileIngestThreads());
        if (UserPreferences.getIsTimeOutEnabled()) {
            // user specified time out
            jCheckBoxEnableProcTimeout.setSelected(true);
            jFormattedTextFieldProcTimeOutHrs.setEditable(true);
            int timeOutHrs = UserPreferences.getProcessTimeOutHrs();
            jFormattedTextFieldProcTimeOutHrs.setValue((long) timeOutHrs);
        } else {
            // never time out
            jCheckBoxEnableProcTimeout.setSelected(false);
            jFormattedTextFieldProcTimeOutHrs.setEditable(false);
            int timeOutHrs = UserPreferences.getProcessTimeOutHrs();
            jFormattedTextFieldProcTimeOutHrs.setValue((long) timeOutHrs);
        }
    }

    void store() {
        UserPreferences.setKeepPreferredContentViewer(keepCurrentViewerRB.isSelected());
        UserPreferences.setHideKnownFilesInDataSourcesTree(dataSourcesHideKnownCB.isSelected());
        UserPreferences.setHideKnownFilesInViewsTree(viewsHideKnownCB.isSelected());
        UserPreferences.setHideSlackFilesInDataSourcesTree(dataSourcesHideSlackCB.isSelected());
        UserPreferences.setHideSlackFilesInViewsTree(viewsHideSlackCB.isSelected());
        UserPreferences.setDisplayTimesInLocalTime(useLocalTimeRB.isSelected());
        UserPreferences.setNumberOfFileIngestThreads((Integer) numberOfFileIngestThreadsComboBox.getSelectedItem());

        UserPreferences.setIsTimeOutEnabled(jCheckBoxEnableProcTimeout.isSelected());
        if (jCheckBoxEnableProcTimeout.isSelected()) {
            // only store time out if it is enabled
            long timeOutHrs = (long) jFormattedTextFieldProcTimeOutHrs.getValue();
            UserPreferences.setProcessTimeOutHrs((int) timeOutHrs);
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
        numberOfFileIngestThreadsComboBox = new javax.swing.JComboBox<>();
        restartRequiredLabel = new javax.swing.JLabel();
        jLabelSetProcessTimeOut = new javax.swing.JLabel();
        jCheckBoxEnableProcTimeout = new javax.swing.JCheckBox();
        jLabelProcessTimeOutUnits = new javax.swing.JLabel();
        jFormattedTextFieldProcTimeOutHrs = new JFormattedTextField(NumberFormat.getIntegerInstance());
        dataSourcesHideSlackCB = new javax.swing.JCheckBox();
        viewsHideSlackCB = new javax.swing.JCheckBox();
        jLabelHideSlackFiles = new javax.swing.JLabel();

        jScrollPane1.setBorder(null);

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

        org.openide.awt.Mnemonics.setLocalizedText(jLabelSelectFile, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jLabelSelectFile.text")); // NOI18N

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

        org.openide.awt.Mnemonics.setLocalizedText(jLabelNumThreads, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jLabelNumThreads.text")); // NOI18N

        numberOfFileIngestThreadsComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                numberOfFileIngestThreadsComboBoxActionPerformed(evt);
            }
        });

        restartRequiredLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(restartRequiredLabel, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.restartRequiredLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabelSetProcessTimeOut, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jLabelSetProcessTimeOut.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jCheckBoxEnableProcTimeout, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jCheckBoxEnableProcTimeout.text")); // NOI18N
        jCheckBoxEnableProcTimeout.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBoxEnableProcTimeoutActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabelProcessTimeOutUnits, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jLabelProcessTimeOutUnits.text")); // NOI18N

        jFormattedTextFieldProcTimeOutHrs.setText(org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jFormattedTextFieldProcTimeOutHrs.text")); // NOI18N
        jFormattedTextFieldProcTimeOutHrs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jFormattedTextFieldProcTimeOutHrsActionPerformed(evt);
            }
        });

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

        org.openide.awt.Mnemonics.setLocalizedText(jLabelHideSlackFiles, org.openide.util.NbBundle.getMessage(AutopsyOptionsPanel.class, "AutopsyOptionsPanel.jLabelHideSlackFiles.text")); // NOI18N

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGap(10, 10, 10)
                                .addComponent(numberOfFileIngestThreadsComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(restartRequiredLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabelTimeDisplay)
                                    .addComponent(jLabelNumThreads)
                                    .addComponent(jLabelSetProcessTimeOut)
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addGap(10, 10, 10)
                                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(useLocalTimeRB)
                                            .addComponent(useGMTTimeRB)
                                            .addGroup(jPanel1Layout.createSequentialGroup()
                                                .addComponent(jCheckBoxEnableProcTimeout)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(jFormattedTextFieldProcTimeOutHrs, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(jLabelProcessTimeOutUnits)))))
                                .addGap(213, 213, 213)))
                        .addContainerGap())
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabelHideKnownFiles)
                            .addComponent(jLabelSelectFile)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGap(10, 10, 10)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(keepCurrentViewerRB)
                                    .addComponent(useBestViewerRB)
                                    .addComponent(dataSourcesHideKnownCB)
                                    .addComponent(viewsHideKnownCB))))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabelHideSlackFiles)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGap(10, 10, 10)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(dataSourcesHideSlackCB)
                                    .addComponent(viewsHideSlackCB))))
                        .addGap(0, 0, Short.MAX_VALUE))))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
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
                .addComponent(useGMTTimeRB)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabelNumThreads)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(numberOfFileIngestThreadsComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(restartRequiredLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabelSetProcessTimeOut)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jCheckBoxEnableProcTimeout)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jFormattedTextFieldProcTimeOutHrs, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jLabelProcessTimeOutUnits)))
                .addContainerGap(49, Short.MAX_VALUE))
        );

        jScrollPane1.setViewportView(jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void jCheckBoxEnableProcTimeoutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBoxEnableProcTimeoutActionPerformed
        jFormattedTextFieldProcTimeOutHrs.setEditable(jCheckBoxEnableProcTimeout.isSelected());
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_jCheckBoxEnableProcTimeoutActionPerformed

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

    private void numberOfFileIngestThreadsComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_numberOfFileIngestThreadsComboBoxActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_numberOfFileIngestThreadsComboBoxActionPerformed

    private void jFormattedTextFieldProcTimeOutHrsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jFormattedTextFieldProcTimeOutHrsActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_jFormattedTextFieldProcTimeOutHrsActionPerformed

    private void dataSourcesHideSlackCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSourcesHideSlackCBActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_dataSourcesHideSlackCBActionPerformed

    private void viewsHideSlackCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewsHideSlackCBActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_viewsHideSlackCBActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup3;
    private javax.swing.JCheckBox dataSourcesHideKnownCB;
    private javax.swing.JCheckBox dataSourcesHideSlackCB;
    private javax.swing.JCheckBox jCheckBoxEnableProcTimeout;
    private javax.swing.JFormattedTextField jFormattedTextFieldProcTimeOutHrs;
    private javax.swing.JLabel jLabelHideKnownFiles;
    private javax.swing.JLabel jLabelHideSlackFiles;
    private javax.swing.JLabel jLabelNumThreads;
    private javax.swing.JLabel jLabelProcessTimeOutUnits;
    private javax.swing.JLabel jLabelSelectFile;
    private javax.swing.JLabel jLabelSetProcessTimeOut;
    private javax.swing.JLabel jLabelTimeDisplay;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JRadioButton keepCurrentViewerRB;
    private javax.swing.JComboBox<Integer> numberOfFileIngestThreadsComboBox;
    private javax.swing.JLabel restartRequiredLabel;
    private javax.swing.JRadioButton useBestViewerRB;
    private javax.swing.JRadioButton useGMTTimeRB;
    private javax.swing.JRadioButton useLocalTimeRB;
    private javax.swing.JCheckBox viewsHideKnownCB;
    private javax.swing.JCheckBox viewsHideSlackCB;
    // End of variables declaration//GEN-END:variables
}
