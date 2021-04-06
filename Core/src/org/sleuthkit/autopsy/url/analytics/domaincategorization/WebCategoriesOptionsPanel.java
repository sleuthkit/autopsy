/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.url.analytics.domaincategorization;

import java.awt.Cursor;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle.Messages;
import org.openide.util.WeakListeners;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ColumnModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchResult;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchResult.ResultType;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultCellModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;
import org.sleuthkit.autopsy.url.analytics.DomainCategory;

/**
 * The options panel displayed for import, export, and CRUD operations on domain
 * categories.
 */
@Messages({
    "WebCategoriesOptionsPanel_categoryTable_suffixColumnName=Domain Suffix",
    "WebCategoriesOptionsPanel_categoryTable_categoryColumnName=Category",})
public class WebCategoriesOptionsPanel extends IngestModuleGlobalSettingsPanel implements OptionsPanel, AutoCloseable {

    private static final Logger logger = Logger.getLogger(WebCategoriesOptionsPanel.class.getName());
    private static final String DEFAULT_EXTENSION = "json";
    private static final FileNameExtensionFilter DB_FILTER = new FileNameExtensionFilter("JSON", DEFAULT_EXTENSION);

    private final JFileChooser fileChooser = new JFileChooser();
    private final WebCategoriesDataModel dataModel;

    private final JTablePanel<DomainCategory> categoryTable
            = JTablePanel.getJTablePanel(Arrays.asList(
                    new ColumnModel<DomainCategory, DefaultCellModel<?>>(
                            Bundle.WebCategoriesOptionsPanel_categoryTable_suffixColumnName(),
                            (domCat) -> new DefaultCellModel<>(domCat.getHostSuffix())
                                    .setTooltip(domCat.getHostSuffix()),
                            300
                    ),
                    new ColumnModel<>(
                            Bundle.WebCategoriesOptionsPanel_categoryTable_categoryColumnName(),
                            (domCat) -> new DefaultCellModel<>(domCat.getCategory())
                                    .setTooltip(domCat.getCategory()),
                            200
                    )
            )).setKeyFunction((domCat) -> domCat.getHostSuffix());

    private final PropertyChangeListener ingestListener = (evt) -> refreshComponentStates();
    private final PropertyChangeListener weakIngestListener = WeakListeners.propertyChange(ingestListener, this);
    private Set<String> domainSuffixes = new HashSet<>();
    private boolean isRefreshing = false;

    /**
     * Main constructor.
     *
     * @param dataModel The data model that interacts with the database.
     */
    public WebCategoriesOptionsPanel(WebCategoriesDataModel dataModel) {
        initComponents();
        this.dataModel = dataModel;

        fileChooser.addChoosableFileFilter(DB_FILTER);
        fileChooser.setFileFilter(DB_FILTER);
        categoryTable.setCellListener((evt) -> refreshComponentStates());
        IngestManager.getInstance().addIngestJobEventListener(weakIngestListener);
        setDefaultCursor();
        refresh();
    }

    /**
     * Returns the items selected in the table or null if no selection.
     *
     * @return The items selected in the table or null if no selection.
     */
    private List<DomainCategory> getSelected() {
        return categoryTable.getSelectedItems();
    }

    /**
     * Triggers swing worker to fetch data and show in table.
     */
    void refresh() {
        isRefreshing = true;
        refreshComponentStates();
        categoryTable.showDefaultLoadingMessage();
        new DataFetchWorker<Void, List<DomainCategory>>(
                (noVal) -> this.dataModel.getRecords(),
                (data) -> onRefreshedData(data),
                null).execute();
    }

    /**
     * When the result of loading the data is returned, this function handles
     * updating the GUI.
     *
     * @param categoriesResult The result of attempting to fetch the data.
     */
    private void onRefreshedData(DataFetchResult<List<DomainCategory>> categoriesResult) {
        categoryTable.showDataFetchResult(categoriesResult);
        if (categoriesResult.getResultType() == ResultType.SUCCESS && categoriesResult.getData() != null) {
            domainSuffixes = categoriesResult.getData().stream()
                    .map((dc) -> dc.getHostSuffix())
                    .collect(Collectors.toSet());
        } else {
            domainSuffixes = new HashSet<>();
        }
        isRefreshing = false;
        refreshComponentStates();
    }

    /**
     * Refreshes the state of the components based on whether or not an item is
     * selected as well as whether or not data is loading or ingest is
     * happening.
     */
    private void refreshComponentStates() {
        List<DomainCategory> selectedItems = getSelected();
        int selectedCount = CollectionUtils.isEmpty(selectedItems) ? 0 : selectedItems.size();
        boolean isIngestRunning = IngestManager.getInstance().isIngestRunning();
        boolean operationsPermitted = !isIngestRunning && !isRefreshing;

        deleteEntryButton.setEnabled(selectedCount > 0 && operationsPermitted);
        editEntryButton.setEnabled(selectedCount == 1 && operationsPermitted);

        newEntryButton.setEnabled(operationsPermitted);
        exportSetButton.setEnabled(operationsPermitted);
        importSetButton.setEnabled(operationsPermitted);

        ingestRunningWarning.setVisible(isIngestRunning);
    }

    /**
     * Shows the AddEditCategoryDialog to the user and returns the user-inputted
     * DomainCategory or null if nothing was saved.
     *
     * @param original If editing a value, this is the original value being
     * edited. If adding a new value, this should be null.
     * @return
     */
    private DomainCategory getAddEditValue(DomainCategory original) {
        JFrame parent = (this.getRootPane() != null && this.getRootPane().getParent() instanceof JFrame)
                ? (JFrame) this.getRootPane().getParent()
                : null;

        AddEditCategoryDialog addEditDialog = new AddEditCategoryDialog(parent, domainSuffixes, original);
        addEditDialog.setResizable(false);
        addEditDialog.setLocationRelativeTo(parent);
        addEditDialog.setVisible(true);
        addEditDialog.toFront();

        if (addEditDialog.isChanged()) {
            return addEditDialog.getValue();
        } else {
            return null;
        }
    }

    /**
     * Set cursor to waiting.
     */
    private void setWaitingCursor() {
        SwingUtilities.invokeLater(() -> this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)));
    }

    /**
     * Set cursor to default.
     */
    private void setDefaultCursor() {
        SwingUtilities.invokeLater(() -> this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)));
    }

    /**
     * An action for updating or altering data in the custom configuration.
     */
    private interface UpdateAction {

        /**
         * A runnable action to update custom configuration.
         *
         * @throws IllegalArgumentException
         * @throws IOException
         * @throws SQLException
         */
        void run() throws IllegalArgumentException, IOException, SQLException;
    }

    /**
     * Runs an action to update the state of the configuration and runs refresh
     * when complete.
     *
     * @param runnable The runnable action.
     * @throws IllegalArgumentException
     * @throws IOException
     * @throws SQLException
     */
    private void runUpdateAction(UpdateAction runnable) throws IllegalArgumentException, IOException, SQLException {
        setWaitingCursor();
        runnable.run();
        setDefaultCursor();
        refresh();
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

        javax.swing.JLabel panelDescription = new javax.swing.JLabel();
        javax.swing.JLabel categoriesTitle = new javax.swing.JLabel();
        javax.swing.JPanel categoryTablePanel = categoryTable;
        newEntryButton = new javax.swing.JButton();
        editEntryButton = new javax.swing.JButton();
        deleteEntryButton = new javax.swing.JButton();
        importSetButton = new javax.swing.JButton();
        exportSetButton = new javax.swing.JButton();
        javax.swing.JPanel bottomStrut = new javax.swing.JPanel();
        ingestRunningWarning = new javax.swing.JLabel();

        setLayout(new java.awt.GridBagLayout());

        panelDescription.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        panelDescription.setText(org.openide.util.NbBundle.getMessage(WebCategoriesOptionsPanel.class, "WebCategoriesOptionsPanel.panelDescription.text")); // NOI18N
        panelDescription.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createEtchedBorder(), javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 10, 0);
        add(panelDescription, gridBagConstraints);

        categoriesTitle.setText(org.openide.util.NbBundle.getMessage(WebCategoriesOptionsPanel.class, "WebCategoriesOptionsPanel.categoriesTitle.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        add(categoriesTitle, gridBagConstraints);

        categoryTablePanel.setAutoscrolls(true);
        categoryTablePanel.setMaximumSize(new java.awt.Dimension(400, 32767));
        categoryTablePanel.setMinimumSize(new java.awt.Dimension(400, 300));
        categoryTablePanel.setPreferredSize(new java.awt.Dimension(400, 600));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 10, 10, 0);
        add(categoryTablePanel, gridBagConstraints);

        newEntryButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/add16.png"))); // NOI18N
        newEntryButton.setText(org.openide.util.NbBundle.getMessage(WebCategoriesOptionsPanel.class, "WebCategoriesOptionsPanel.newEntryButton.text")); // NOI18N
        newEntryButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newEntryButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 5, 5);
        add(newEntryButton, gridBagConstraints);

        editEntryButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/edit16.png"))); // NOI18N
        editEntryButton.setText(org.openide.util.NbBundle.getMessage(WebCategoriesOptionsPanel.class, "WebCategoriesOptionsPanel.editEntryButton.text")); // NOI18N
        editEntryButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editEntryButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 5);
        add(editEntryButton, gridBagConstraints);

        deleteEntryButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/delete16.png"))); // NOI18N
        deleteEntryButton.setText(org.openide.util.NbBundle.getMessage(WebCategoriesOptionsPanel.class, "WebCategoriesOptionsPanel.deleteEntryButton.text")); // NOI18N
        deleteEntryButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteEntryButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 5);
        add(deleteEntryButton, gridBagConstraints);

        importSetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/import16.png"))); // NOI18N
        importSetButton.setText(org.openide.util.NbBundle.getMessage(WebCategoriesOptionsPanel.class, "WebCategoriesOptionsPanel.importSetButton.text")); // NOI18N
        importSetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importSetButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 5, 5);
        add(importSetButton, gridBagConstraints);

        exportSetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/export16.png"))); // NOI18N
        exportSetButton.setText(org.openide.util.NbBundle.getMessage(WebCategoriesOptionsPanel.class, "WebCategoriesOptionsPanel.exportSetButton.text")); // NOI18N
        exportSetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportSetButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 5);
        add(exportSetButton, gridBagConstraints);

        bottomStrut.setPreferredSize(new java.awt.Dimension(10, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        add(bottomStrut, gridBagConstraints);

        ingestRunningWarning.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/warning16.png"))); // NOI18N
        ingestRunningWarning.setText(org.openide.util.NbBundle.getMessage(WebCategoriesOptionsPanel.class, "WebCategoriesOptionsPanel.ingestRunningWarning.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 10, 10);
        add(ingestRunningWarning, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

    private void deleteEntryButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteEntryButtonActionPerformed
        List<DomainCategory> selectedItems = getSelected();
        if (!CollectionUtils.isEmpty(selectedItems)) {
            setWaitingCursor();
            for (DomainCategory selected : selectedItems) {
                if (selected != null && selected.getHostSuffix() != null) {
                    try {
                        dataModel.deleteRecord(selected.getHostSuffix());
                    } catch (IllegalArgumentException | SQLException ex) {
                        logger.log(Level.WARNING, "There was an error while deleting: " + selected.getHostSuffix(), ex);
                    }
                }
            }
            setDefaultCursor();
            refresh();
        }
    }//GEN-LAST:event_deleteEntryButtonActionPerformed

    private void newEntryButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newEntryButtonActionPerformed
        DomainCategory newCategory = getAddEditValue(null);
        if (newCategory != null) {
            try {
                runUpdateAction(() -> dataModel.insertUpdateSuffix(newCategory));
            } catch (IllegalArgumentException | SQLException | IOException ex) {
                setDefaultCursor();
                logger.log(Level.WARNING, "There was an error while adding new record: " + newCategory.getHostSuffix(), ex);
            }
        }
    }//GEN-LAST:event_newEntryButtonActionPerformed

    private void editEntryButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editEntryButtonActionPerformed
        List<DomainCategory> selectedItems = getSelected();
        if (CollectionUtils.isNotEmpty(selectedItems)) {
            DomainCategory selected = selectedItems.get(0);
            if (selected != null && selected.getHostSuffix() != null) {
                try {
                    DomainCategory newCategory = getAddEditValue(selected);
                    if (newCategory != null) {
                        runUpdateAction(() -> dataModel.insertUpdateSuffix(newCategory));
                    }
                } catch (IllegalArgumentException | SQLException | IOException ex) {
                    setDefaultCursor();
                    logger.log(Level.WARNING, "There was an error while editing: " + selected.getHostSuffix(), ex);
                }
            }
        }
    }//GEN-LAST:event_editEntryButtonActionPerformed

    @Messages({
        "WebCategoriesOptionsPanel_importSetButtonActionPerformed_errorMessage=There was an error importing this json file.",
        "WebCategoriesOptionsPanel_importSetButtonActionPerformed_errorTitle=Import Error",
        "WebCategoriesOptionsPanel_importSetButtonActionPerformed_onConflictTitle=Domain Suffix Already Exists",
        "# {0} - domainSuffix",
        "WebCategoriesOptionsPanel_importSetButtonActionPerformed_onConflictMessage=Domain suffix {0} already exists. What would you like to do?",
        "WebCategoriesOptionsPanel_importSetButtonActionPerformed_onConflictOverwrite=Overwrite",
        "WebCategoriesOptionsPanel_importSetButtonActionPerformed_onConflictSkip=Skip",
        "WebCategoriesOptionsPanel_importSetButtonActionPerformed_onConflictCancel=Cancel"})
    private void importSetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importSetButtonActionPerformed
        fileChooser.setSelectedFile(new File(""));
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            if (selectedFile != null && selectedFile.exists()) {
                try {
                    runUpdateAction(() -> {
                        List<DomainCategory> categories = dataModel.getJsonEntries(selectedFile);

                        for (DomainCategory domcat : categories) {
                            String normalizedCategory = domcat == null ? "" : WebCategoriesDataModel.getNormalizedCategory(domcat.getCategory());
                            String normalizedSuffix = domcat == null ? "" : WebCategoriesDataModel.getNormalizedSuffix(domcat.getHostSuffix());

                            if (StringUtils.isBlank(normalizedCategory) || StringUtils.isBlank(normalizedSuffix)) {
                                logger.log(Level.WARNING, String.format("Invalid entry [category: %s, domain suffix: %s]", normalizedCategory, normalizedSuffix));
                                continue;
                            }

                            DomainCategory currentCategory = dataModel.getRecordBySuffix(normalizedSuffix);
                            // if a mapping for the domain suffix already exists and the value will change, prompt the user on what to do.
                            if (currentCategory != null) {
                                if (normalizedCategory.equalsIgnoreCase(currentCategory.getCategory()))  {
                                    // do nothing if import item is same as already present
                                    continue;
                                } else {

                                    String[] options = {
                                        Bundle.WebCategoriesOptionsPanel_importSetButtonActionPerformed_onConflictOverwrite(),
                                        Bundle.WebCategoriesOptionsPanel_importSetButtonActionPerformed_onConflictSkip(),
                                        Bundle.WebCategoriesOptionsPanel_importSetButtonActionPerformed_onConflictCancel()
                                    };

                                    int optionItem = JOptionPane.showOptionDialog(null,
                                            Bundle.WebCategoriesOptionsPanel_importSetButtonActionPerformed_onConflictMessage(normalizedSuffix),
                                            Bundle.WebCategoriesOptionsPanel_importSetButtonActionPerformed_onConflictTitle(),
                                            JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);

                                    switch (optionItem) {
                                        case 0:
                                            break;
                                        case 1:
                                            continue;
                                        case 2:
                                            return;
                                    }
                                }
                            }

                            dataModel.insertUpdateSuffix(new DomainCategory(normalizedSuffix, normalizedCategory));
                        }
                    });
                } catch (IllegalArgumentException | SQLException | IOException ex) {
                    setDefaultCursor();
                    JOptionPane.showMessageDialog(
                            this,
                            Bundle.WebCategoriesOptionsPanel_importSetButtonActionPerformed_errorMessage(),
                            Bundle.WebCategoriesOptionsPanel_importSetButtonActionPerformed_errorTitle(),
                            JOptionPane.ERROR_MESSAGE);
                    logger.log(Level.WARNING, "There was an error on import.", ex);
                }
            }
        }
    }//GEN-LAST:event_importSetButtonActionPerformed

    @Messages({
        "WebCategoriesOptionsPanel_exportSetButtonActionPerformed_duplicateMessage=A file already exists at the selected path.  The categories will not be exported.",
        "WebCategoriesOptionsPanel_exportSetButtonActionPerformed_duplicateTitle=File Already Exists",
        "WebCategoriesOptionsPanel_exportSetButtonActionPerformed_errorMessage=There was an error exporting.",
        "WebCategoriesOptionsPanel_exportSetButtonActionPerformed_errorTitle=Export Error",
        "WebCategoriesOptionsPanel_exportSetButtonActionPerformed_defaultFileName=Custom Categories Export"
    })
    private void exportSetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportSetButtonActionPerformed
        fileChooser.setSelectedFile(new File(String.format("%s.json", Bundle.WebCategoriesOptionsPanel_exportSetButtonActionPerformed_defaultFileName())));
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            if (selectedFile != null) {
                if (selectedFile.exists()) {
                    JOptionPane.showMessageDialog(
                            this,
                            Bundle.WebCategoriesOptionsPanel_exportSetButtonActionPerformed_duplicateMessage(),
                            Bundle.WebCategoriesOptionsPanel_exportSetButtonActionPerformed_duplicateTitle(),
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                try {
                    setWaitingCursor();
                    dataModel.exportToJson(selectedFile);
                    setDefaultCursor();
                } catch (SQLException | IOException ex) {
                    setDefaultCursor();
                    JOptionPane.showMessageDialog(
                            this,
                            Bundle.WebCategoriesOptionsPanel_importSetButtonActionPerformed_errorMessage(),
                            Bundle.WebCategoriesOptionsPanel_importSetButtonActionPerformed_errorTitle(),
                            JOptionPane.ERROR_MESSAGE);
                    logger.log(Level.WARNING, "There was an error on export.", ex);
                }
            }
        }
    }//GEN-LAST:event_exportSetButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton deleteEntryButton;
    private javax.swing.JButton editEntryButton;
    private javax.swing.JButton exportSetButton;
    private javax.swing.JButton importSetButton;
    private javax.swing.JLabel ingestRunningWarning;
    private javax.swing.JButton newEntryButton;
    // End of variables declaration//GEN-END:variables

    @Override
    public void saveSettings() {
        // NO OP since saves happen whenever there is a change.
    }

    @Override
    public void store() {
        // NO OP since saves happen whenever there is a change.
    }

    @Override
    public void load() {
        refresh();
    }

    @Override
    public void close() {
        IngestManager.getInstance().removeIngestJobEventListener(weakIngestListener);
    }
}
