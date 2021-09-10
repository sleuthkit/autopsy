/*
 * Central Repository
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
package org.sleuthkit.autopsy.centralrepository.contentviewer;

import org.sleuthkit.autopsy.centralrepository.application.NodeData;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;

/**
 * Panel for displaying other occurrence details.
 */
final class OccurrencePanel extends javax.swing.JPanel {

    private static final Logger LOGGER = Logger.getLogger(OccurrencePanel.class.getName());
    private static final int LEFT_INSET = 10;
    private static final int RIGHT_INSET = 10;
    private static final int TOP_INSET = 10;
    private static final int BOTTOM_INSET = 10;
    private static final int VERTICAL_GAP = 6;
    private static final int HORIZONTAL_GAP = 4;
    private static final long serialVersionUID = 1L;

    private int gridY = 0;
    private final List<NodeData> nodeDataList;
    private final Map<String, String> caseNamesAndDates = new HashMap<>();
    private final Set<String> dataSourceNames = new HashSet<>();
    private final Set<String> filePaths = new HashSet<>();

    /**
     * Construct an empty OccurrencePanel
     */
    OccurrencePanel() {
        nodeDataList = new ArrayList<>();
        customizeComponents();
    }

    /**
     * Construct an OccurrencePanel which will display only Case information
     *
     * @param caseName        the name of the case
     * @param caseCreatedDate the date the case was created
     */
    OccurrencePanel(String caseName, String caseCreatedDate) {
        nodeDataList = new ArrayList<>();
        caseNamesAndDates.put(caseName, caseCreatedDate);
        customizeComponents();
    }

    /**
     * Construct an OccurrencePanel which will display only case and data source
     * information
     *
     * @param caseName        the name of the case
     * @param caseCreatedDate the date the case was created
     * @param dataSourceName  the name of the data source
     */
    OccurrencePanel(String caseName, String caseCreatedDate, String dataSourceName) {
        nodeDataList = new ArrayList<>();
        caseNamesAndDates.put(caseName, caseCreatedDate);
        dataSourceNames.add(dataSourceName);
        customizeComponents();
    }

    /**
     * Construct a OccurrencePanel which will display details for all other
     * occurrences associated with a file
     *
     * @param nodeDataList the list of OtherOccurrenceNodeData representing
     *                     common properties for the file
     */
    OccurrencePanel(List<NodeData> nodeDataList) {
        this.nodeDataList = nodeDataList;
        customizeComponents();
    }

    /**
     * Do all the construction of gui elements and adding of the appropriate
     * elements to the gridbaglayout
     */
    private void customizeComponents() {
        initComponents();
        if (!this.nodeDataList.isEmpty()) {
            //if addInstanceDetails is going to be called it should be called 
            // before addFileDetails, addDataSourceDetails, and addCaseDetails 
            //because it also collects the information they display
            addInstanceDetails();
            if (!filePaths.isEmpty()) {
                addFileDetails();
            }
        }
        if (!dataSourceNames.isEmpty()) {
            addDataSourceDetails();
        }
        if (!caseNamesAndDates.keySet().isEmpty()) {
            addCaseDetails();
        }
        //add filler to keep everything else at the top
        addItemToBag(gridY, 0, 0, 0, new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 32767)));
    }

    @Messages({
        "OccurrencePanel.commonProperties.text=Common Properties",
        "OccurrencePanel.commonPropertyTypeLabel.text=Type:",
        "OccurrencePanel.commonPropertyValueLabel.text=Value:",
        "OccurrencePanel.commonPropertyKnownStatusLabel.text=Known Status:",
        "OccurrencePanel.commonPropertyCommentLabel.text=Comment:"
    })
    /**
     * Add the Common Property instance details to the gridbaglayout supports
     * adding multiple common properties
     *
     * Also collects the case, data source, and file path information to be
     * displayed
     */
    private void addInstanceDetails() {
        javax.swing.JLabel commonPropertiesLabel = new javax.swing.JLabel();
        org.openide.awt.Mnemonics.setLocalizedText(commonPropertiesLabel, Bundle.OccurrencePanel_commonProperties_text());
        commonPropertiesLabel.setFont(commonPropertiesLabel.getFont().deriveFont(Font.BOLD, commonPropertiesLabel.getFont().getSize()));
        addItemToBag(gridY, 0, TOP_INSET, 0, commonPropertiesLabel);
        gridY++;
        //for each other occurrence
        for (NodeData occurrence : nodeDataList) {
            if (occurrence instanceof NodeData) {
                String type = occurrence.getType();
                if (!type.isEmpty()) {
                    javax.swing.JLabel typeLabel = new javax.swing.JLabel();
                    org.openide.awt.Mnemonics.setLocalizedText(typeLabel, Bundle.OccurrencePanel_commonPropertyTypeLabel_text());
                    addItemToBag(gridY, 0, VERTICAL_GAP, 0, typeLabel);
                    javax.swing.JLabel typeFieldValue = new javax.swing.JLabel();
                    typeFieldValue.setText(type);
                    addItemToBag(gridY, 1, VERTICAL_GAP, 0, typeFieldValue);
                    gridY++;
                }
                String value = occurrence.getValue();
                if (!value.isEmpty()) {
                    javax.swing.JLabel valueLabel = new javax.swing.JLabel();
                    org.openide.awt.Mnemonics.setLocalizedText(valueLabel, Bundle.OccurrencePanel_commonPropertyValueLabel_text());
                    addItemToBag(gridY, 0, 0, 0, valueLabel);
                    javax.swing.JLabel valueFieldValue = new javax.swing.JLabel();
                    valueFieldValue.setText(value);
                    addItemToBag(gridY, 1, 0, 0, valueFieldValue);
                    gridY++;
                }
                TskData.FileKnown knownStatus = occurrence.getKnown();
                javax.swing.JLabel knownStatusLabel = new javax.swing.JLabel();
                org.openide.awt.Mnemonics.setLocalizedText(knownStatusLabel, Bundle.OccurrencePanel_commonPropertyKnownStatusLabel_text());
                addItemToBag(gridY, 0, 0, 0, knownStatusLabel);
                javax.swing.JLabel knownStatusValue = new javax.swing.JLabel();
                knownStatusValue.setText(knownStatus.getName());
                if (knownStatus == TskData.FileKnown.BAD) {
                    knownStatusValue.setForeground(Color.RED);
                }
                addItemToBag(gridY, 1, 0, 0, knownStatusValue);
                gridY++;
                String comment = occurrence.getComment();
                if (!comment.isEmpty()) {
                    javax.swing.JLabel commentLabel = new javax.swing.JLabel();
                    org.openide.awt.Mnemonics.setLocalizedText(commentLabel, Bundle.OccurrencePanel_commonPropertyCommentLabel_text());
                    addItemToBag(gridY, 0, 0, VERTICAL_GAP, commentLabel);
                    javax.swing.JTextArea commentValue = new javax.swing.JTextArea();
                    commentValue.setText(comment);
                    commentValue.setEditable(false);
                    commentValue.setColumns(20);
                    commentValue.setLineWrap(true);
                    commentValue.setRows(3);
                    commentValue.setTabSize(4);
                    commentValue.setWrapStyleWord(true);
                    commentValue.setBorder(javax.swing.BorderFactory.createEtchedBorder());
                    commentValue.setBackground(javax.swing.UIManager.getDefaults().getColor("TextArea.disabledBackground"));
                    addItemToBag(gridY, 1, 0, VERTICAL_GAP, commentValue);
                    gridY++;
                }
                String caseDate = "";
                try {
                    if (CentralRepository.isEnabled()) {
                        CorrelationCase partialCase = occurrence.getCorrelationAttributeInstance().getCorrelationCase();
                        caseDate = CentralRepository.getInstance().getCaseByUUID(partialCase.getCaseUUID()).getCreationDate();
                    }
                } catch (CentralRepoException ex) {
                    LOGGER.log(Level.WARNING, "Error getting case created date for other occurrence content viewer", ex);
                }
                //Collect the data that is necessary for the other sections 
                caseNamesAndDates.put(occurrence.getCaseName(), caseDate);
                dataSourceNames.add(occurrence.getDataSourceName());
                filePaths.add(occurrence.getFilePath());
            }
        }
        //end for each
    }

    @Messages({
        "OccurrencePanel.fileDetails.text=File Details",
        "OccurrencePanel.filePathLabel.text=File Path:"
    })
    /**
     * Add the File specific details such as file path to the gridbaglayout
     */
    private void addFileDetails() {
        String filePath = filePaths.size() > 1 ? "" : filePaths.iterator().next();
        if (!filePath.isEmpty()) {
            javax.swing.JLabel fileDetailsLabel = new javax.swing.JLabel();
            org.openide.awt.Mnemonics.setLocalizedText(fileDetailsLabel, Bundle.OccurrencePanel_fileDetails_text());
            fileDetailsLabel.setFont(fileDetailsLabel.getFont().deriveFont(Font.BOLD, fileDetailsLabel.getFont().getSize()));
            addItemToBag(gridY, 0, TOP_INSET, 0, fileDetailsLabel);
            gridY++;
            javax.swing.JLabel filePathLabel = new javax.swing.JLabel();
            org.openide.awt.Mnemonics.setLocalizedText(filePathLabel, Bundle.OccurrencePanel_filePathLabel_text());
            addItemToBag(gridY, 0, VERTICAL_GAP, VERTICAL_GAP, filePathLabel);
            javax.swing.JTextArea filePathValue = new javax.swing.JTextArea();
            filePathValue.setText(filePath);
            filePathValue.setEditable(false);
            filePathValue.setColumns(20);
            filePathValue.setLineWrap(true);
            filePathValue.setRows(3);
            filePathValue.setTabSize(4);
            filePathValue.setWrapStyleWord(true);
            filePathValue.setBorder(javax.swing.BorderFactory.createEtchedBorder());
            filePathValue.setBackground(javax.swing.UIManager.getDefaults().getColor("TextArea.disabledBackground"));
            addItemToBag(gridY, 1, VERTICAL_GAP, VERTICAL_GAP, filePathValue);
            gridY++;
        }
    }

    @Messages({
        "OccurrencePanel.dataSourceDetails.text=Data Source Details",
        "OccurrencePanel.dataSourceNameLabel.text=Name:"
    })
    /**
     * Add the data source specific details such as data source name to the
     * gridbaglayout
     */
    private void addDataSourceDetails() {
        String dataSourceName = dataSourceNames.size() > 1 ? "" : dataSourceNames.iterator().next();
        if (!dataSourceName.isEmpty()) {
            javax.swing.JLabel dataSourceDetailsLabel = new javax.swing.JLabel();
            org.openide.awt.Mnemonics.setLocalizedText(dataSourceDetailsLabel, Bundle.OccurrencePanel_dataSourceDetails_text());
            dataSourceDetailsLabel.setFont(dataSourceDetailsLabel.getFont().deriveFont(Font.BOLD, dataSourceDetailsLabel.getFont().getSize()));
            addItemToBag(gridY, 0, TOP_INSET, 0, dataSourceDetailsLabel);
            gridY++;
            javax.swing.JLabel dataSourceNameLabel = new javax.swing.JLabel();
            org.openide.awt.Mnemonics.setLocalizedText(dataSourceNameLabel, Bundle.OccurrencePanel_dataSourceNameLabel_text());
            addItemToBag(gridY, 0, VERTICAL_GAP, VERTICAL_GAP, dataSourceNameLabel);
            javax.swing.JLabel dataSourceNameValue = new javax.swing.JLabel();
            dataSourceNameValue.setText(dataSourceName);
            addItemToBag(gridY, 1, VERTICAL_GAP, VERTICAL_GAP, dataSourceNameValue);
            gridY++;
        }
    }

    @Messages({
        "OccurrencePanel.caseDetails.text=Case Details",
        "OccurrencePanel.caseNameLabel.text=Name:",
        "OccurrencePanel.caseCreatedDateLabel.text=Created Date:"
    })
    /**
     * Add the case specific details such as case name to the gridbaglayout
     */
    private void addCaseDetails() {
        javax.swing.JLabel caseDetailsLabel = new javax.swing.JLabel();
        org.openide.awt.Mnemonics.setLocalizedText(caseDetailsLabel, Bundle.OccurrencePanel_caseDetails_text());
        caseDetailsLabel.setFont(caseDetailsLabel.getFont().deriveFont(Font.BOLD, caseDetailsLabel.getFont().getSize()));
        addItemToBag(gridY, 0, TOP_INSET, 0, caseDetailsLabel);
        gridY++;
        String caseName = caseNamesAndDates.keySet().size() > 1 ? "" : caseNamesAndDates.keySet().iterator().next();
        if (!caseName.isEmpty()) {
            javax.swing.JLabel caseNameLabel = new javax.swing.JLabel();
            org.openide.awt.Mnemonics.setLocalizedText(caseNameLabel, Bundle.OccurrencePanel_caseNameLabel_text());
            addItemToBag(gridY, 0, VERTICAL_GAP, 0, caseNameLabel);
            javax.swing.JLabel caseNameValue = new javax.swing.JLabel();
            caseNameValue.setText(caseName);
            addItemToBag(gridY, 1, VERTICAL_GAP, 0, caseNameValue);
            gridY++;
        }
        String caseCreatedDate = caseNamesAndDates.keySet().size() > 1 ? "" : caseNamesAndDates.get(caseName);
        if (caseCreatedDate != null && !caseCreatedDate.isEmpty()) {
            javax.swing.JLabel caseCreatedLabel = new javax.swing.JLabel();
            org.openide.awt.Mnemonics.setLocalizedText(caseCreatedLabel, Bundle.OccurrencePanel_caseCreatedDateLabel_text());
            addItemToBag(gridY, 0, 0, BOTTOM_INSET, caseCreatedLabel);
            javax.swing.JLabel caseCreatedValue = new javax.swing.JLabel();
            caseCreatedValue.setText(caseCreatedDate);
            addItemToBag(gridY, 1, 0, BOTTOM_INSET, caseCreatedValue);
            gridY++;
        }
    }

    /**
     * Add a JComponent to the gridbaglayout
     *
     * @param gridYLocation the row number the item should be added at
     * @param gridXLocation the column number the item should be added at
     * @param topInset      the gap from the top of the cell which should exist
     * @param bottomInset   the gap from the bottom of the cell which should
     *                      exist
     * @param item          the JComponent to add to the gridbaglayout
     */
    private void addItemToBag(int gridYLocation, int gridXLocation, int topInset, int bottomInset, javax.swing.JComponent item) {
        java.awt.GridBagConstraints gridBagConstraints;
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = gridXLocation;
        gridBagConstraints.gridy = gridYLocation;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        int leftInset = LEFT_INSET;
        int rightInset = HORIZONTAL_GAP;
        //change formating a bit if it is the value instead of the label
        if (gridXLocation == 1) {
            leftInset = 0;
            rightInset = RIGHT_INSET;
            gridBagConstraints.weightx = 0.1;
            gridBagConstraints.gridwidth = 2;
        }
        gridBagConstraints.insets = new java.awt.Insets(topInset, leftInset, bottomInset, rightInset);
        //if the item is a filler item ensure it will resize vertically
        if (item instanceof javax.swing.Box.Filler) {
            gridBagConstraints.weighty = 0.1;
        }
        add(item, gridBagConstraints);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setMinimumSize(new java.awt.Dimension(50, 30));
        setPreferredSize(null);
        setLayout(new java.awt.GridBagLayout());
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
