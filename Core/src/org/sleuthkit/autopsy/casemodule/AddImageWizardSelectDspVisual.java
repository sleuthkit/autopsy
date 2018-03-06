/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.AbstractButton;
import javax.swing.Box.Filler;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.datasourceprocessors.RawDSProcessor;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Panel which displays the available DataSourceProcessors and allows selection
 * of one
 */
final class AddImageWizardSelectDspVisual extends JPanel {

    private static final Logger logger = Logger.getLogger(AddImageWizardSelectDspVisual.class.getName());
    private String selectedDsp;

    /**
     * Creates new form SelectDataSourceProcessorPanel
     */
    AddImageWizardSelectDspVisual(String lastDspUsed) {
        initComponents();
        selectedDsp = lastDspUsed;
        //if the last selected DSP was the Local Disk DSP and it would be disabled then we want to select a different DSP
        try {
            if ((Case.getOpenCase().getCaseType() == Case.CaseType.MULTI_USER_CASE) && selectedDsp.equals(LocalDiskDSProcessor.getType())) {
                selectedDsp = ImageDSProcessor.getType();
            }
            createDataSourceProcessorButtons();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex);
        }
        
        //add actionlistner to listen for change
    }

    /**
     * Find the DSP which is currently selected and save it as the selected
     * DataSourceProcessor.
     *
     */
    private void updateSelectedDsp() {
        Enumeration<AbstractButton> buttonGroup = buttonGroup1.getElements();
        while (buttonGroup.hasMoreElements()) {
            AbstractButton dspButton = buttonGroup.nextElement();
            if (dspButton.isSelected()) {
                selectedDsp = dspButton.getName();
                break;
            }
        }
    }

    /**
     * Get the DataSourceProcessor which is currently selected in this panel
     *
     * @return selectedDsp the DataSourceProcessor which is selected in this
     *         panel
     */
    String getSelectedDsp() {
        return selectedDsp;
    }

    @NbBundle.Messages("AddImageWizardSelectDspVisual.multiUserWarning.text=This type of Data Source Processor is not available in multi-user mode")
    /**
     * Create the a button for each DataSourceProcessor that should exist as an
     * option.
     */
    private void createDataSourceProcessorButtons() throws NoCurrentCaseException {
        //Listener for button selection
        ActionListener cbActionListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateSelectedDsp();
            }
        };
        List<String> dspList = getListOfDsps();
        //Set up the constraints for the panel layout
        GridBagLayout gridBagLayout = new GridBagLayout();
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weighty = 0;
        constraints.anchor = GridBagConstraints.LINE_START;
        Dimension spacerBlockDimension = new Dimension(6, 4); // Space between left edge and button, Space between rows 
        for (String dspType : dspList) {
            boolean shouldAddMultiUserWarning = false;
            constraints.weightx = 1;
            //Add a spacer
            Filler spacer = new Filler(spacerBlockDimension, spacerBlockDimension, spacerBlockDimension);
            gridBagLayout.setConstraints(spacer, constraints);
            jPanel1.add(spacer);
            constraints.gridx++;
            constraints.gridy++;
            //Add the button
            JToggleButton dspButton = createDspButton(dspType);
            dspButton.addActionListener(cbActionListener);
            if ((Case.getOpenCase().getCaseType() == Case.CaseType.MULTI_USER_CASE) && dspType.equals(LocalDiskDSProcessor.getType())){
                dspButton.setEnabled(false); //disable the button for local disk DSP when this is a multi user case
                dspButton.setSelected(false);
                shouldAddMultiUserWarning = true;
            }
            jPanel1.add(dspButton);
            buttonGroup1.add(dspButton);
            gridBagLayout.setConstraints(dspButton, constraints);
            constraints.gridx++;
            //Add space between the button and text 
            Filler buttonTextSpacer = new Filler(spacerBlockDimension, spacerBlockDimension, spacerBlockDimension);
            gridBagLayout.setConstraints(buttonTextSpacer, constraints);
            jPanel1.add(buttonTextSpacer);
            constraints.gridx++;
            //Add the text area serving as a label to the right of the button

            JTextArea myLabel = new JTextArea();
            if (shouldAddMultiUserWarning) {
                myLabel.setText(dspType + " - " + NbBundle.getMessage(this.getClass(), "AddImageWizardSelectDspVisual.multiUserWarning.text"));
                myLabel.setEnabled(false);  //gray out the text
            } else {
                myLabel.setText(dspType);
            }
            myLabel.setBackground(new Color(240, 240, 240));//matches background of panel
            myLabel.setEditable(false);
            myLabel.setWrapStyleWord(true);
            myLabel.setLineWrap(true);
            jPanel1.add(myLabel);
            gridBagLayout.setConstraints(myLabel, constraints);
            constraints.weightx = 0;
            constraints.gridy++;
            constraints.gridx = 0;
        }
        Component vertGlue = javax.swing.Box.createVerticalGlue();
        jPanel1.add(vertGlue);
        constraints.gridy++;
        constraints.gridx = 0;
        constraints.weighty = 1;
        gridBagLayout.setConstraints(vertGlue, constraints);
        jPanel1.setLayout(gridBagLayout);
    }

    /**
     * Create a list of the DataSourceProcessors which should exist as options
     * on this panel. The default Autopsy DataSourceProcessors will appear at
     * the beggining of the list in the same order.
     *
     * @return dspList a list of DataSourceProcessors which can be chose in this
     *         panel
     */
    private List<String> getListOfDsps() {
        List<String> dspList = new ArrayList<>();
        final Map<String, DataSourceProcessor> datasourceProcessorsMap = new HashMap<>();
        for (DataSourceProcessor dsProcessor : Lookup.getDefault().lookupAll(DataSourceProcessor.class)) {
            if (!datasourceProcessorsMap.containsKey(dsProcessor.getDataSourceType())) {
                datasourceProcessorsMap.put(dsProcessor.getDataSourceType(), dsProcessor);
            } else {
                logger.log(Level.SEVERE, "discoverDataSourceProcessors(): A DataSourceProcessor already exists for type = {0}", dsProcessor.getDataSourceType()); //NON-NLS
            }
        }
        dspList.add(ImageDSProcessor.getType());
        dspList.add(LocalDiskDSProcessor.getType());
        dspList.add(LocalFilesDSProcessor.getType());
        dspList.add(RawDSProcessor.getType());
        // now add any addtional DSPs that haven't already been added
        for (String dspType : datasourceProcessorsMap.keySet()) {
            if (!dspList.contains(dspType)) {
                dspList.add(dspType);
            }
        }
        return dspList;
    }

    /**
     * Create a single button for a DataSourceProcessor
     *
     * @param dspType - the name of the DataSourceProcessor
     *
     * @return dspButton a JToggleButton for the specified dspType
     */
    private JToggleButton createDspButton(String dspType) {
        JToggleButton dspButton = new JToggleButton();
        dspButton.setMaximumSize(new java.awt.Dimension(48, 48));
        dspButton.setMinimumSize(new java.awt.Dimension(48, 48));
        dspButton.setPreferredSize(new java.awt.Dimension(48, 48));
        dspButton.setName(dspType);
        dspButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/modules/fileextmismatch/options-icon.png")));
        dspButton.setSelectedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/checkbox32.png")));
        dspButton.setFocusable(false);
        if (dspType.equals(selectedDsp)) {
            dspButton.setSelected(true);
        } else {
            dspButton.setSelected(false);
        }
        return dspButton;
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        jScrollPane1 = new javax.swing.JScrollPane();
        jPanel1 = new javax.swing.JPanel();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(6, 8), new java.awt.Dimension(6, 8), new java.awt.Dimension(6, 8));

        jPanel1.setLayout(new java.awt.GridBagLayout());
        jPanel1.add(filler1, new java.awt.GridBagConstraints());

        jScrollPane1.setViewportView(jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 588, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 588, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 328, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 328, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.Box.Filler filler1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    // End of variables declaration//GEN-END:variables
}
