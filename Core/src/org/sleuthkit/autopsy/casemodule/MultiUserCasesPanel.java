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
import java.awt.EventQueue;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.JDialog;
import javax.swing.SortOrder;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.coordinationservice.CaseNodeData;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A panel that allows a user to open cases created by auto ingest.
 */
@NbBundle.Messages({"MultiUSerCasesPanel.caseListLoading.message=Retrieving list of cases, please wait..."})
final class MultiUserCasesPanel extends TopComponent implements ExplorerManager.Provider {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(MultiUserCasesPanel.class.getName());
    private final JDialog parentDialog;
    private LoadCaseListWorker tableWorker;
    private final CaseBrowser caseListPanel;
    private final ExplorerManager explorerManager;

    /**
     * Constructs a panel that allows a user to open cases created by automated
     * ingest.
     */
    MultiUserCasesPanel(JDialog parentDialog) {
        this.parentDialog = parentDialog;
        explorerManager = new ExplorerManager();
        associateLookup(ExplorerUtils.createLookup(explorerManager, getActionMap()));
        initComponents();

        caseListPanel = new CaseBrowser();
        caseListPanel.open();

        caseExplorerScrollPane.add(caseListPanel);
        caseExplorerScrollPane.setViewportView(caseListPanel);
        /*
         * Listen for row selection changes and set button state for the current
         * selection.
         */
        caseListPanel.addListSelectionListener((ListSelectionEvent e) -> {
            setButtons();
        });

    }

    /**
     * Gets the list of cases known to the review mode cases manager and
     * refreshes the cases table.
     */
    void refresh() {
        if (tableWorker == null || tableWorker.isDone()) {
            //create a new TableWorker to and execute it in a background thread if one is not currently working
            //set the table to display text informing the user that the list is being retreived and disable case selection
            tableWorker = new LoadCaseListWorker();
            tableWorker.execute();

        }

    }

    /**
     * Enables/disables the Open and Show Log buttons based on the case selected
     * in the cases table.
     */
    private void setButtons() {
        boolean openEnabled = caseListPanel.isRowSelected();
        bnOpen.setEnabled(openEnabled);
    }

    /**
     * Open a case.
     *
     * @param caseMetadataFilePath The path to the case metadata file.
     */
    private void openCase(String caseMetadataFilePath) {
        if (caseMetadataFilePath != null) {
            System.out.println("OPENENING CASE: " + caseMetadataFilePath);
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

    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
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
        bnOpen.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnOpenActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnOpenSingleUserCase, org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "MultiUserCasesPanel.bnOpenSingleUserCase.text")); // NOI18N
        bnOpenSingleUserCase.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnOpenSingleUserCaseActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(MultiUserCasesPanel.class, "MultiUserCasesPanel.cancelButton.text")); // NOI18N
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
                        .addComponent(searchLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 555, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 175, Short.MAX_VALUE)
                        .addComponent(bnOpen, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bnOpenSingleUserCase)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(caseExplorerScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 450, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cancelButton)
                    .addComponent(bnOpen)
                    .addComponent(bnOpenSingleUserCase)
                    .addComponent(searchLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Open button action
     *
     * @param evt -- The event that caused this to be called
     */
    private void bnOpenActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnOpenActionPerformed
        openCase(caseListPanel.getCasePath());
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

    private class LoadCaseListWorker extends SwingWorker<Void, Void> {

        private static final String ALERT_FILE_NAME = "autoingest.alert";
        private Map<CaseMetadata, Boolean> cases;

        /**
         * Gets a list of the cases in the top level case folder
         *
         * @return List of cases.
         *
         * @throws CoordinationServiceException
         */
        private Map<CaseMetadata, Boolean> getCases() throws CoordinationService.CoordinationServiceException {
            Map<CaseMetadata, Boolean> cases = new HashMap<>();
            List<String> nodeList = CoordinationService.getInstance().getNodeList(CoordinationService.CategoryNode.CASES);

            for (String node : nodeList) {
                Path casePath = Paths.get(node);
                File caseFolder = casePath.toFile();
                if (caseFolder.exists()) {
                    /*
                     * Search for '*.aut' and 'autoingest.alert' files.
                     */
                    File[] fileArray = caseFolder.listFiles();
                    if (fileArray == null) {
                        continue;
                    }
                    String autFilePath = null;
                    boolean alertFileFound = false;
                    for (File file : fileArray) {
                        String name = file.getName().toLowerCase();
                        if (autFilePath == null && name.endsWith(".aut")) {
                            autFilePath = file.getAbsolutePath();
                            if (!alertFileFound) {
                                continue;
                            }
                        }
                        if (!alertFileFound && name.endsWith(ALERT_FILE_NAME)) {
                            alertFileFound = true;
                        }
                        if (autFilePath != null && alertFileFound) {
                            break;
                        }
                    }

                    if (autFilePath != null) {
                        try {
                            boolean hasAlertStatus = false;
                            if (alertFileFound) {
                                /*
                                 * When an alert file exists, ignore the node
                                 * data and use the ALERT status.
                                 */
                                hasAlertStatus = true;
                            } else {
                                byte[] rawData = CoordinationService.getInstance().getNodeData(CoordinationService.CategoryNode.CASES, node);
                                if (rawData != null && rawData.length > 0) {
                                    /*
                                     * When node data exists, use the status
                                     * stored in the node data.
                                     */
                                    CaseNodeData caseNodeData = new CaseNodeData(rawData);
                                    if (caseNodeData.getErrorsOccurred()) {
                                        hasAlertStatus = true;
                                    }
                                }
                            }

                            CaseMetadata caseMetadata = new CaseMetadata(Paths.get(autFilePath));
                            cases.put(caseMetadata, hasAlertStatus);
                        } catch (CaseMetadata.CaseMetadataException ex) {
                            LOGGER.log(Level.SEVERE, String.format("Error reading case metadata file '%s'.", autFilePath), ex);
                        } catch (InterruptedException | CaseNodeData.InvalidDataException ex) {
                            LOGGER.log(Level.SEVERE, String.format("Error reading case node data for '%s'.", node), ex);
                        }
                    }
                }
            }
            return cases;
        }

        @Override
        protected Void doInBackground() throws Exception {

            try {
                cases = getCases();
            } catch (CoordinationService.CoordinationServiceException ex) {
                LOGGER.log(Level.SEVERE, "Unexpected exception while refreshing the table.", ex); //NON-NLS
            }
            return null;
        }

        @Override
        protected void done() {

            EventQueue.invokeLater(() -> {
                CaseNode caseListNode = new CaseNode(cases);
                explorerManager.setRootContext(caseListNode);
                String displayName = "";
                Content content = caseListNode.getLookup().lookup(Content.class);
                if (content != null) {
                    try {
                        displayName = content.getUniquePath();
                    } catch (TskCoreException ex) {
                        LOGGER.log(Level.SEVERE, "Exception while calling Content.getUniquePath() for node: {0}", caseListNode); //NON-NLS
                    }
                } else if (caseListNode.getLookup().lookup(String.class) != null) {
                    displayName = caseListNode.getLookup().lookup(String.class);
                }
                System.out.println("GET CASES DONE");
                setButtons();
            });
        }
    }
}
