/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.ui;

import java.beans.PropertyChangeEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.table.DefaultTableModel;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.ContainerSummary.ContainerDetails;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.ContainerSummary.ImageDetails;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchResult.ResultType;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker.DataFetchComponents;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataFetcher;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultUpdateGovernor;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.UpdateGovernor;
import org.sleuthkit.datamodel.DataSource;

/**
 * Panel to display additional details associated with a specific DataSource
 */
@Messages({
    "ContainerPanel_tabName=Container"
})
class ContainerPanel extends BaseDataSourceSummaryPanel {

    // set of case events for which to call update (if the name changes, that will impact data shown)
    private static final Set<Case.Events> CASE_EVENT_SET = new HashSet<>(Arrays.asList(
            Case.Events.DATA_SOURCE_NAME_CHANGED
    ));

    // governor for handling these updates
    private static final UpdateGovernor CONTAINER_UPDATES = new DefaultUpdateGovernor() {

        @Override
        public Set<Case.Events> getCaseEventUpdates() {
            return CASE_EVENT_SET;
        }

        @Override
        public boolean isRefreshRequiredForCaseEvent(PropertyChangeEvent evt) {
            return true;
        }

    };

    //Because this panel was made using the gridbaglayout and netbean's Customize Layout tool it will be best to continue to modify it through that
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ContainerPanel.class.getName());

    private final List<DataFetchComponents<DataSource, ?>> dataFetchComponents;
    private final DataFetcher<DataSource, ContainerDetails> containerDataFetcher;

    /**
     * Creates a new form ContainerPanel.
     */
    ContainerPanel() {
        this(new ContainerSummaryGetter());
    }

    /**
     * Creates new form ContainerPanel.
     */
    ContainerPanel(ContainerSummaryGetter containerSummary) {
        super(containerSummary, CONTAINER_UPDATES);

        containerDataFetcher = (dataSource) -> containerSummary.getContainerDetails(dataSource);

        dataFetchComponents = Arrays.asList(
                new DataFetchComponents<>(
                        containerDataFetcher,
                        (result) -> {
                            if (result != null && result.getResultType() == ResultType.SUCCESS) {
                                ContainerDetails data = result.getData();
                                updateDetailsPanelData(data);
                            } else {
                                if (result == null) {
                                    logger.log(Level.WARNING, "No data fetch result was provided to the ContainerPanel.");
                                } else {
                                    logger.log(Level.WARNING, "An exception occurred while attempting to fetch data for the ContainerPanel.",
                                            result.getException());
                                }
                                updateDetailsPanelData(null);
                            }
                        }
                )
        );

        initComponents();
        setDataSource(null);
    }

    @Override
    protected void onNewDataSource(DataSource dataSource) {
        fetchInformation(dataSource);
    }

    @Override
    protected void fetchInformation(DataSource dataSource) {
        fetchInformation(dataFetchComponents, dataSource);
    }

    /**
     * Update the swing components with fetched data.
     *
     * @param viewModel The data source view model data.
     */
    private void updateDetailsPanelData(ContainerDetails viewModel) {
        clearTableValues();
        if (viewModel == null) {
            return;
        }

        displayNameValue.setText(viewModel.getDisplayName());
        originalNameValue.setText(viewModel.getOriginalName());
        deviceIdValue.setText(viewModel.getDeviceId());
        acquisitionDetailsTextArea.setText(viewModel.getAcquisitionDetails());

        if (viewModel.getImageDetails() != null) {
            setFieldsForImage(viewModel.getImageDetails());
        } else {
            setFieldsForNonImageDataSource();
        }

        this.repaint();
    }

    /**
     * Sets image-only fields to N/A.
     */
    @Messages({
        "ContainerPanel_setFieldsForNonImageDataSource_na=N/A"
    })
    private void setFieldsForNonImageDataSource() {
        String NA = Bundle.ContainerPanel_setFieldsForNonImageDataSource_na();

        unallocatedSizeValue.setText(NA);
        imageTypeValue.setText(NA);
        sizeValue.setText(NA);
        sectorSizeValue.setText(NA);
        timeZoneValue.setText(NA);

        ((DefaultTableModel) filePathsTable.getModel()).addRow(new Object[]{NA});

        md5HashValue.setText(NA);
        sha1HashValue.setText(NA);
        sha256HashValue.setText(NA);
    }

    /**
     * Sets fields for images.
     *
     * @param viewModel The image view model data.
     */
    private void setFieldsForImage(ImageDetails viewModel) {
        Long unallocatedSize = viewModel.getUnallocatedSize();
        if (unallocatedSize == null) {
            unallocatedSizeValue.setText(Bundle.ContainerPanel_setFieldsForNonImageDataSource_na());
        } else {
            unallocatedSizeValue.setText(SizeRepresentationUtil.getSizeString(unallocatedSize));
        }

        imageTypeValue.setText(viewModel.getImageType());
        sizeValue.setText(SizeRepresentationUtil.getSizeString(viewModel.getSize()));
        sectorSizeValue.setText(SizeRepresentationUtil.getSizeString(viewModel.getSectorSize()));
        timeZoneValue.setText(viewModel.getTimeZone());

        for (String path : viewModel.getPaths()) {
            ((DefaultTableModel) filePathsTable.getModel()).addRow(new Object[]{path});
        }

        md5HashValue.setText(viewModel.getMd5Hash());
        sha1HashValue.setText(viewModel.getSha1Hash());
        sha256HashValue.setText(viewModel.getSha256Hash());
    }

    /**
     * Set the contents of all fields to be empty.
     */
    private void clearTableValues() {
        displayNameValue.setText("");
        originalNameValue.setText("");
        deviceIdValue.setText("");
        timeZoneValue.setText("");
        acquisitionDetailsTextArea.setText("");
        imageTypeValue.setText("");
        sizeValue.setText("");
        sectorSizeValue.setText("");
        md5HashValue.setText("");
        sha1HashValue.setText("");
        sha256HashValue.setText("");
        unallocatedSizeValue.setText("");
        ((DefaultTableModel) filePathsTable.getModel()).setRowCount(0);
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

        jScrollPane1 = new javax.swing.JScrollPane();
        jPanel1 = new javax.swing.JPanel();
        displayNameLabel = new javax.swing.JLabel();
        originalNameLabel = new javax.swing.JLabel();
        sha1HashValue = new javax.swing.JLabel();
        displayNameValue = new javax.swing.JLabel();
        sha256HashValue = new javax.swing.JLabel();
        originalNameValue = new javax.swing.JLabel();
        deviceIdValue = new javax.swing.JLabel();
        filePathsScrollPane = new javax.swing.JScrollPane();
        filePathsTable = new javax.swing.JTable();
        timeZoneValue = new javax.swing.JLabel();
        imageTypeValue = new javax.swing.JLabel();
        md5HashValue = new javax.swing.JLabel();
        sectorSizeValue = new javax.swing.JLabel();
        sizeValue = new javax.swing.JLabel();
        filePathsLabel = new javax.swing.JLabel();
        sha256HashLabel = new javax.swing.JLabel();
        sha1HashLabel = new javax.swing.JLabel();
        md5HashLabel = new javax.swing.JLabel();
        sectorSizeLabel = new javax.swing.JLabel();
        sizeLabel = new javax.swing.JLabel();
        imageTypeLabel = new javax.swing.JLabel();
        acquisitionDetailsLabel = new javax.swing.JLabel();
        timeZoneLabel = new javax.swing.JLabel();
        deviceIdLabel = new javax.swing.JLabel();
        acquisitionDetailsScrollPane = new javax.swing.JScrollPane();
        acquisitionDetailsTextArea = new javax.swing.JTextArea();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 32767));
        filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 32767));
        unallocatedSizeLabel = new javax.swing.JLabel();
        unallocatedSizeValue = new javax.swing.JLabel();

        jPanel1.setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(displayNameLabel, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.displayNameLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 4);
        jPanel1.add(displayNameLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(originalNameLabel, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.originalNameLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 4);
        jPanel1.add(originalNameLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(sha1HashValue, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.sha1HashValue.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        jPanel1.add(sha1HashValue, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(displayNameValue, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.displayNameValue.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 10);
        jPanel1.add(displayNameValue, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(sha256HashValue, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.sha256HashValue.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 6, 10);
        jPanel1.add(sha256HashValue, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(originalNameValue, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.originalNameValue.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        jPanel1.add(originalNameValue, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(deviceIdValue, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.deviceIdValue.text")); // NOI18N
        deviceIdValue.setToolTipText(org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.deviceIdValue.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 6, 10);
        jPanel1.add(deviceIdValue, gridBagConstraints);

        filePathsScrollPane.setPreferredSize(new java.awt.Dimension(80, 50));

        filePathsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                ""
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        filePathsTable.setTableHeader(null);
        filePathsScrollPane.setViewportView(filePathsTable);
        if (filePathsTable.getColumnModel().getColumnCount() > 0) {
            filePathsTable.getColumnModel().getColumn(0).setHeaderValue(org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.filePathsTable.columnModel.title0")); // NOI18N
        }

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 1.2;
        gridBagConstraints.insets = new java.awt.Insets(6, 0, 10, 10);
        jPanel1.add(filePathsScrollPane, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(timeZoneValue, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.timeZoneValue.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 6, 10);
        jPanel1.add(timeZoneValue, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(imageTypeValue, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.imageTypeValue.text")); // NOI18N
        imageTypeValue.setToolTipText(org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.imageTypeValue.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(6, 0, 0, 10);
        jPanel1.add(imageTypeValue, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(md5HashValue, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.md5HashValue.text")); // NOI18N
        md5HashValue.setToolTipText(org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.md5HashValue.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        jPanel1.add(md5HashValue, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(sectorSizeValue, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.sectorSizeValue.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        jPanel1.add(sectorSizeValue, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(sizeValue, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.sizeValue.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        jPanel1.add(sizeValue, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(filePathsLabel, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.filePathsLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weighty = 1.2;
        gridBagConstraints.insets = new java.awt.Insets(6, 10, 10, 4);
        jPanel1.add(filePathsLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(sha256HashLabel, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.sha256HashLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 6, 4);
        jPanel1.add(sha256HashLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(sha1HashLabel, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.sha1HashLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 4);
        jPanel1.add(sha1HashLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(md5HashLabel, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.md5HashLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 4);
        jPanel1.add(md5HashLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(sectorSizeLabel, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.sectorSizeLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 4);
        jPanel1.add(sectorSizeLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(sizeLabel, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.sizeLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 4);
        jPanel1.add(sizeLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(imageTypeLabel, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.imageTypeLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(6, 10, 0, 4);
        jPanel1.add(imageTypeLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(acquisitionDetailsLabel, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.acquisitionDetailsLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weighty = 0.6;
        gridBagConstraints.insets = new java.awt.Insets(6, 10, 6, 4);
        jPanel1.add(acquisitionDetailsLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(timeZoneLabel, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.timeZoneLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 6, 4);
        jPanel1.add(timeZoneLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(deviceIdLabel, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.deviceIdLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 6, 4);
        jPanel1.add(deviceIdLabel, gridBagConstraints);

        acquisitionDetailsTextArea.setEditable(false);
        acquisitionDetailsTextArea.setBackground(javax.swing.UIManager.getDefaults().getColor("TextArea.disabledBackground"));
        acquisitionDetailsTextArea.setColumns(20);
        acquisitionDetailsTextArea.setRows(4);
        acquisitionDetailsTextArea.setText(org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.acquisitionDetailsTextArea.text")); // NOI18N
        acquisitionDetailsTextArea.setBorder(null);
        acquisitionDetailsScrollPane.setViewportView(acquisitionDetailsTextArea);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.6;
        gridBagConstraints.insets = new java.awt.Insets(6, 0, 6, 10);
        jPanel1.add(acquisitionDetailsScrollPane, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 15;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weighty = 0.1;
        jPanel1.add(filler1, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 15;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weighty = 0.1;
        jPanel1.add(filler2, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(unallocatedSizeLabel, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.unallocatedSizeLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 4);
        jPanel1.add(unallocatedSizeLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(unallocatedSizeValue, org.openide.util.NbBundle.getMessage(ContainerPanel.class, "ContainerPanel.unallocatedSizeValue.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        jPanel1.add(unallocatedSizeValue, gridBagConstraints);

        jScrollPane1.setViewportView(jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 380, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel acquisitionDetailsLabel;
    private javax.swing.JScrollPane acquisitionDetailsScrollPane;
    private javax.swing.JTextArea acquisitionDetailsTextArea;
    private javax.swing.JLabel deviceIdLabel;
    private javax.swing.JLabel deviceIdValue;
    private javax.swing.JLabel displayNameLabel;
    private javax.swing.JLabel displayNameValue;
    private javax.swing.JLabel filePathsLabel;
    private javax.swing.JScrollPane filePathsScrollPane;
    private javax.swing.JTable filePathsTable;
    private javax.swing.Box.Filler filler1;
    private javax.swing.Box.Filler filler2;
    private javax.swing.JLabel imageTypeLabel;
    private javax.swing.JLabel imageTypeValue;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel md5HashLabel;
    private javax.swing.JLabel md5HashValue;
    private javax.swing.JLabel originalNameLabel;
    private javax.swing.JLabel originalNameValue;
    private javax.swing.JLabel sectorSizeLabel;
    private javax.swing.JLabel sectorSizeValue;
    private javax.swing.JLabel sha1HashLabel;
    private javax.swing.JLabel sha1HashValue;
    private javax.swing.JLabel sha256HashLabel;
    private javax.swing.JLabel sha256HashValue;
    private javax.swing.JLabel sizeLabel;
    private javax.swing.JLabel sizeValue;
    private javax.swing.JLabel timeZoneLabel;
    private javax.swing.JLabel timeZoneValue;
    private javax.swing.JLabel unallocatedSizeLabel;
    private javax.swing.JLabel unallocatedSizeValue;
    // End of variables declaration//GEN-END:variables
}
