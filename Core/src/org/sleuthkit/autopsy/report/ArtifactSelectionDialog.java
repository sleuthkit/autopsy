/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.event.ListDataListener;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

public class ArtifactSelectionDialog extends javax.swing.JDialog {

    private ArtifactModel model;
    private ArtifactRenderer renderer;
    private Map<BlackboardArtifact.Type, Boolean> artifactTypeSelections;
    private List<BlackboardArtifact.Type> artifactTypes;

    /**
     * Creates new form ArtifactSelectionDialog
     * 
     * @param parent The parent window
     * @param modal  Block user-input to other top-level windows.
     */
    public ArtifactSelectionDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        initComponents();
        populateList();
        customInit();
    }

    /**
     * Populate the list of artifact types with all important artifact types.
     */
    @SuppressWarnings("deprecation")
    private void populateList() {
        try {
            ArrayList<BlackboardArtifact.Type> doNotReport = new ArrayList<>();
            doNotReport.add(new BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_GEN_INFO.getTypeID(),
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_GEN_INFO.getLabel(),
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_GEN_INFO.getDisplayName()));
            doNotReport.add(new BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_TOOL_OUTPUT.getTypeID(),
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_TOOL_OUTPUT.getLabel(),
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_TOOL_OUTPUT.getDisplayName())); // output is too unstructured for table review

            artifactTypes = Case.getOpenCase().getSleuthkitCase().getArtifactTypesInUse();
            artifactTypes.removeAll(doNotReport);
            Collections.sort(artifactTypes, new Comparator<BlackboardArtifact.Type>() {
                @Override
                public int compare(BlackboardArtifact.Type o1, BlackboardArtifact.Type o2) {
                    return o1.getDisplayName().compareTo(o2.getDisplayName());
                }
            });

            artifactTypeSelections = new HashMap<>();
            for (BlackboardArtifact.Type type : artifactTypes) {
                artifactTypeSelections.put(type, Boolean.TRUE);
            }
        } catch (TskCoreException ex) {
            Logger.getLogger(ArtifactSelectionDialog.class.getName()).log(Level.SEVERE, "Error getting list of artifacts in use: {0}", ex.getLocalizedMessage()); //NON-NLS
        } catch (NoCurrentCaseException ex) {
            Logger.getLogger(ArtifactSelectionDialog.class.getName()).log(Level.SEVERE, "Exception while getting open case.", ex.getLocalizedMessage()); //NON-NLS
        }
    }

    private void customInit() {
        model = new ArtifactModel();
        renderer = new ArtifactRenderer();
        artifactList.setModel(model);
        artifactList.setCellRenderer(renderer);
        artifactList.setVisibleRowCount(-1);

        artifactList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent evt) {
                int index = artifactList.locationToIndex(evt.getPoint());
                BlackboardArtifact.Type type = model.getElementAt(index);
                artifactTypeSelections.put(type, !artifactTypeSelections.get(type));
                artifactList.repaint();
            }
        });
    }

    /**
     * Display this dialog, and return the selected artifactTypes.
     *
     * @return The state of artifact types displayed
     */
    Map<BlackboardArtifact.Type, Boolean> display() {
        this.setTitle(NbBundle.getMessage(this.getClass(), "ArtifactSelectionDialog.dlgTitle.text"));
        this.setLocationRelativeTo(getOwner());
        this.setVisible(true);
        return artifactTypeSelections;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        artifactScrollPane = new javax.swing.JScrollPane();
        artifactList = new javax.swing.JList<>();
        okButton = new javax.swing.JButton();
        titleLabel = new javax.swing.JLabel();
        selectAllButton = new javax.swing.JButton();
        deselectAllButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        artifactList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        artifactList.setLayoutOrientation(javax.swing.JList.HORIZONTAL_WRAP);
        artifactScrollPane.setViewportView(artifactList);

        org.openide.awt.Mnemonics.setLocalizedText(okButton, org.openide.util.NbBundle.getMessage(ArtifactSelectionDialog.class, "ArtifactSelectionDialog.okButton.text")); // NOI18N
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(titleLabel, org.openide.util.NbBundle.getMessage(ArtifactSelectionDialog.class, "ArtifactSelectionDialog.titleLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(selectAllButton, org.openide.util.NbBundle.getMessage(ArtifactSelectionDialog.class, "ArtifactSelectionDialog.selectAllButton.text")); // NOI18N
        selectAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectAllButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(deselectAllButton, org.openide.util.NbBundle.getMessage(ArtifactSelectionDialog.class, "ArtifactSelectionDialog.deselectAllButton.text")); // NOI18N
        deselectAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deselectAllButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(artifactScrollPane)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(deselectAllButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(selectAllButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(titleLabel)
                        .addGap(0, 221, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(okButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(titleLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(artifactScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 224, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(selectAllButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(deselectAllButton)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addGap(11, 11, 11)
                .addComponent(okButton)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        dispose();
    }//GEN-LAST:event_okButtonActionPerformed

    private void selectAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectAllButtonActionPerformed
        for (BlackboardArtifact.Type type : artifactTypes) {
            artifactTypeSelections.put(type, Boolean.TRUE);
        }
        artifactList.repaint();
    }//GEN-LAST:event_selectAllButtonActionPerformed

    private void deselectAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deselectAllButtonActionPerformed
        for (BlackboardArtifact.Type type : artifactTypes) {
            artifactTypeSelections.put(type, Boolean.FALSE);
        }
        artifactList.repaint();
    }//GEN-LAST:event_deselectAllButtonActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JList<BlackboardArtifact.Type> artifactList;
    private javax.swing.JScrollPane artifactScrollPane;
    private javax.swing.JButton deselectAllButton;
    private javax.swing.JButton okButton;
    private javax.swing.JButton selectAllButton;
    private javax.swing.JLabel titleLabel;
    // End of variables declaration//GEN-END:variables

    private class ArtifactModel implements ListModel<BlackboardArtifact.Type> {

        @Override
        public int getSize() {
            return artifactTypes.size();
        }

        @Override
        public BlackboardArtifact.Type getElementAt(int index) {
            return artifactTypes.get(index);
        }

        @Override
        public void addListDataListener(ListDataListener l) {
        }

        @Override
        public void removeListDataListener(ListDataListener l) {
        }
    }

    private class ArtifactRenderer extends JCheckBox implements ListCellRenderer<BlackboardArtifact.Type> {

        @Override
        public Component getListCellRendererComponent(JList<? extends BlackboardArtifact.Type> list, BlackboardArtifact.Type value, int index, boolean isSelected, boolean cellHasFocus) {
            if (value != null) {
                setEnabled(list.isEnabled());
                setSelected(artifactTypeSelections.get(value));
                setFont(list.getFont());
                setBackground(list.getBackground());
                setForeground(list.getForeground());
                setText(value.getDisplayName());
                return this;
            }
            return new JLabel();
        }
    }
}
