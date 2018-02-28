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
package org.sleuthkit.autopsy.casemodule;

import java.awt.Cursor;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 * A panel that allows a user to open cases created by auto ingest.
 */
final class MultiUserCasesPanel extends JPanel{

    private static final Logger LOGGER = Logger.getLogger(MultiUserCasesPanel.class.getName());
    private static final long serialVersionUID = 1L;
    private final JDialog parentDialog;
    private final CaseBrowser caseBrowserPanel;

    /**
     * Constructs a panel that allows a user to open cases created by automated
     * ingest.
     */
    MultiUserCasesPanel(JDialog parentDialog) {
        this.parentDialog = parentDialog;
        initComponents();

        caseBrowserPanel = new CaseBrowser();
        caseExplorerScrollPane.add(caseBrowserPanel);
        caseExplorerScrollPane.setViewportView(caseBrowserPanel);
        /*
         * Listen for row selection changes and set button state for the current
         * selection.
         */
        caseBrowserPanel.addListSelectionListener((ListSelectionEvent e) -> {
            setButtons();
        });

    }

    /**
     * Gets the list of cases known to the review mode cases manager and
     * refreshes the cases table.
     */
    void refresh() {
        caseBrowserPanel.refresh();
    }

    /**
     * Enables/disables the Open and Show Log buttons based on the case selected
     * in the cases table.
     */
    void setButtons() {
        bnOpen.setEnabled(caseBrowserPanel.isRowSelected());
    }

    /**
     * Open a case.
     *
     * @param caseMetadataFilePath The path to the case metadata file.
     */
    private void openCase(String caseMetadataFilePath) {
        if (caseMetadataFilePath != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

            StartupWindowProvider.getInstance().close();
            if (parentDialog != null) {
                parentDialog.setVisible(false);
            }
            new Thread(() -> {
                try {
                    Case.openAsCurrentCase(caseMetadataFilePath);
                } catch (CaseActionException ex) {
                    if (null != ex.getCause() && !(ex.getCause() instanceof CaseActionCancelledException)) {
                        LOGGER.log(Level.SEVERE, String.format("Error opening case with metadata file path %s", caseMetadataFilePath), ex); //NON-NLS
                        MessageNotifyUtil.Message.error(ex.getCause().getLocalizedMessage());
                    }
                    SwingUtilities.invokeLater(() -> {
                        //GUI changes done back on the EDT
                        StartupWindowProvider.getInstance().open();
                    });
                } finally {
                    SwingUtilities.invokeLater(() -> {
                        //GUI changes done back on the EDT
                        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    });
                }
            }).start();
        }
    }

    /**
     * RowSorter which makes columns whose type is Date to be sorted first in
     * Descending order then in Ascending order
     */
    private static class RowSorter<M extends DefaultTableModel> extends TableRowSorter<M> {

        RowSorter(M tModel) {
            super(tModel);
        }

        @Override
        public void toggleSortOrder(int column) {
            if (!this.getModel().getColumnClass(column).equals(Date.class)) {
                super.toggleSortOrder(column);  //if it isn't a date column perform the regular sorting
            } else {
                ArrayList<RowSorter.SortKey> sortKeys = new ArrayList<>(getSortKeys());
                if (sortKeys.isEmpty() || sortKeys.get(0).getColumn() != column) {  //sort descending
                    sortKeys.add(0, new RowSorter.SortKey(column, SortOrder.DESCENDING));
                } else if (sortKeys.get(0).getSortOrder() == SortOrder.ASCENDING) {
                    sortKeys.removeIf(key -> key.getColumn() == column);
                    sortKeys.add(0, new RowSorter.SortKey(column, SortOrder.DESCENDING));
                } else {
                    sortKeys.removeIf(key -> key.getColumn() == column);
                    sortKeys.add(0, new RowSorter.SortKey(column, SortOrder.ASCENDING));
                }
                setSortKeys(sortKeys);
            }
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        bnOpen = new javax.swing.JButton();
        bnOpenSingleUserCase = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        searchLabel = new javax.swing.JLabel();
        caseExplorerScrollPane = new javax.swing.JScrollPane();

        setName("Completed Cases"); // NOI18N
        setPreferredSize(new java.awt.Dimension(960, 485));

        org.openide.awt.Mnemonics.setLocalizedText(bnOpen, org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "MultiUserCasesPanel.bnOpen.text")); // NOI18N
        bnOpen.setEnabled(false);
        bnOpen.setMaximumSize(new java.awt.Dimension(80, 23));
        bnOpen.setMinimumSize(new java.awt.Dimension(80, 23));
        bnOpen.setPreferredSize(new java.awt.Dimension(80, 23));
        bnOpen.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnOpenActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnOpenSingleUserCase, org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "MultiUserCasesPanel.bnOpenSingleUserCase.text")); // NOI18N
        bnOpenSingleUserCase.setMinimumSize(new java.awt.Dimension(156, 23));
        bnOpenSingleUserCase.setPreferredSize(new java.awt.Dimension(156, 23));
        bnOpenSingleUserCase.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnOpenSingleUserCaseActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "MultiUserCasesPanel.cancelButton.text")); // NOI18N
        cancelButton.setMaximumSize(new java.awt.Dimension(80, 23));
        cancelButton.setMinimumSize(new java.awt.Dimension(80, 23));
        cancelButton.setPreferredSize(new java.awt.Dimension(80, 23));
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(searchLabel, org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "MultiUserCasesPanel.searchLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(caseExplorerScrollPane)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(searchLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(bnOpenSingleUserCase, javax.swing.GroupLayout.PREFERRED_SIZE, 192, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(190, 190, 190)
                        .addComponent(bnOpen, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {bnOpen, cancelButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(caseExplorerScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 450, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cancelButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(bnOpen, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(bnOpenSingleUserCase, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(searchLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Open button action
     *
     * @param evt -- The event that caused this to be called
     */
    private void bnOpenActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnOpenActionPerformed
        openCase(caseBrowserPanel.getCasePath());
    }//GEN-LAST:event_bnOpenActionPerformed

    private void bnOpenSingleUserCaseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnOpenSingleUserCaseActionPerformed
        Lookup.getDefault().lookup(CaseOpenAction.class).openCaseSelectionWindow();
    }//GEN-LAST:event_bnOpenSingleUserCaseActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        if (parentDialog != null) {
            parentDialog.setVisible(false);
        }
    }//GEN-LAST:event_cancelButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnOpen;
    private javax.swing.JButton bnOpenSingleUserCase;
    private javax.swing.JButton cancelButton;
    private javax.swing.JScrollPane caseExplorerScrollPane;
    private javax.swing.JLabel searchLabel;
    // End of variables declaration//GEN-END:variables
}
