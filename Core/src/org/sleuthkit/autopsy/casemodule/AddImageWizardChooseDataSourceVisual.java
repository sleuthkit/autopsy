/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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


import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.logging.Level;
import javax.swing.ComboBoxModel;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListDataListener;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;

/**
 * visual component for the first panel of add image wizard. Allows user to pick
 * data source and timezone.
 *
 */
final class AddImageWizardChooseDataSourceVisual extends JPanel {

    static final Logger logger = Logger.getLogger(AddImageWizardChooseDataSourceVisual.class.getName());
    
    enum EVENT {

        UPDATE_UI, FOCUS_NEXT
    };
   
    private AddImageWizardChooseDataSourcePanel wizPanel;
    private JPanel currentPanel;
    
    private Map<String, DataSourceProcessor> datasourceProcessorsMap = new HashMap<String, DataSourceProcessor>();
          


    /**
     * Creates new form AddImageVisualPanel1
     *
     * @param wizPanel corresponding WizardPanel to handle logic of wizard step
     */
    AddImageWizardChooseDataSourceVisual(AddImageWizardChooseDataSourcePanel wizPanel) {
        initComponents();
        this.wizPanel = wizPanel;
        
        customInit();
    }

    private void customInit() {
        
        discoverDataSourceProcessors();
        
        // set up the DSP type combobox
        typeComboBox.removeAllItems();
        Set<String> dspTypes = datasourceProcessorsMap.keySet();
        for(String dspType:dspTypes){
            typeComboBox.addItem(dspType);
        }
        
        //add actionlistner to listen for change
        ActionListener cbActionListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dspSelectionChanged();
            
            }
        };
                 
        typeComboBox.addActionListener(cbActionListener);
                 
        typeComboBox.setSelectedIndex(0);
        typePanel.setLayout(new BorderLayout());
        
        updateCurrentPanel(GetCurrentDSProcessor().getPanel());
    }

    private void discoverDataSourceProcessors() {
      
        for (DataSourceProcessor dsProcessor: Lookup.getDefault().lookupAll(DataSourceProcessor.class)) {
            if (!datasourceProcessorsMap.containsKey(dsProcessor.getType()) ) {
                if (!datasourceProcessorsMap.containsKey(dsProcessor.getType()) ) {
                    
                    dsProcessor.reset();
                    datasourceProcessorsMap.put(dsProcessor.getType(), dsProcessor);
                }
                else {
                    logger.log(Level.SEVERE, "discoverDataSourceProcessors(): A DataSourceProcessor already exists for type = " + dsProcessor.getType() );
                }      
            }  
        }
     } 

     private void dspSelectionChanged() {
         // update the current panel to selection
         currentPanel = GetCurrentDSProcessor().getPanel();
         updateCurrentPanel(currentPanel);
    }
     
    /**
     * Changes the current panel to the given panel.
     *
     * @param panel instance of ImageTypePanel to change to
     */
    private void updateCurrentPanel(JPanel panel) {
        currentPanel = panel;
        typePanel.removeAll();
        typePanel.add((JPanel) currentPanel, BorderLayout.CENTER);
        typePanel.validate();
        typePanel.repaint();
        currentPanel.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(AddImageWizardChooseDataSourceVisual.EVENT.UPDATE_UI.toString())) {
                    updateUI(null);
                }
                if (evt.getPropertyName().equals(AddImageWizardChooseDataSourceVisual.EVENT.FOCUS_NEXT.toString())) {
                    wizPanel.moveFocusToNext();
                }
            }
        });
      
        updateUI(null);
    }

     /**
     * Returns the currently selected DS Processor
     * @return DataSourceProcessor the DataSourceProcessor corresponding to the data source type selected in the combobox
     */
    public DataSourceProcessor GetCurrentDSProcessor() {
        // get the type of the currently selected panel and then look up 
        // the correspodning DS Handler in the map
        String dsType = (String) typeComboBox.getSelectedItem();
        DataSourceProcessor dsProcessor = datasourceProcessorsMap.get(dsType);
        
        return dsProcessor;
        
    }
            
    /**
     * Returns the name of the this panel. This name will be shown on the left
     * panel of the "Add Image" wizard panel.
     *
     * @return name the name of this panel
     */
    @Override
    public String getName() {
        return "Enter Data Source Information";
    }


    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        jLabel2 = new javax.swing.JLabel();
        nextLabel = new javax.swing.JLabel();
        inputPanel = new javax.swing.JPanel();
        typeTabel = new javax.swing.JLabel();
        typePanel = new javax.swing.JPanel();
        typeComboBox = new javax.swing.JComboBox<String>();
        imgInfoLabel = new javax.swing.JLabel();

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(AddImageWizardChooseDataSourceVisual.class, "AddImageWizardChooseDataSourceVisual.jLabel2.text")); // NOI18N

        setPreferredSize(new java.awt.Dimension(588, 328));

        org.openide.awt.Mnemonics.setLocalizedText(nextLabel, org.openide.util.NbBundle.getMessage(AddImageWizardChooseDataSourceVisual.class, "AddImageWizardChooseDataSourceVisual.nextLabel.text")); // NOI18N

        inputPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        org.openide.awt.Mnemonics.setLocalizedText(typeTabel, org.openide.util.NbBundle.getMessage(AddImageWizardChooseDataSourceVisual.class, "AddImageWizardChooseDataSourceVisual.typeTabel.text")); // NOI18N

        typePanel.setMinimumSize(new java.awt.Dimension(0, 65));
        typePanel.setPreferredSize(new java.awt.Dimension(521, 65));

        javax.swing.GroupLayout typePanelLayout = new javax.swing.GroupLayout(typePanel);
        typePanel.setLayout(typePanelLayout);
        typePanelLayout.setHorizontalGroup(
            typePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 544, Short.MAX_VALUE)
        );
        typePanelLayout.setVerticalGroup(
            typePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 173, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout inputPanelLayout = new javax.swing.GroupLayout(inputPanel);
        inputPanel.setLayout(inputPanelLayout);
        inputPanelLayout.setHorizontalGroup(
            inputPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(inputPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(inputPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(inputPanelLayout.createSequentialGroup()
                        .addComponent(typeTabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(typeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 292, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 115, Short.MAX_VALUE))
                    .addComponent(typePanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 544, Short.MAX_VALUE))
                .addContainerGap())
        );
        inputPanelLayout.setVerticalGroup(
            inputPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(inputPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(inputPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(typeTabel)
                    .addComponent(typeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(typePanel, javax.swing.GroupLayout.DEFAULT_SIZE, 173, Short.MAX_VALUE)
                .addContainerGap())
        );

        imgInfoLabel.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(imgInfoLabel, org.openide.util.NbBundle.getMessage(AddImageWizardChooseDataSourceVisual.class, "AddImageWizardChooseDataSourceVisual.imgInfoLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(inputPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(nextLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(imgInfoLabel))
                        .addGap(0, 54, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(imgInfoLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(inputPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 45, Short.MAX_VALUE)
                .addComponent(nextLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0))
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JLabel imgInfoLabel;
    private javax.swing.JPanel inputPanel;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel nextLabel;
    private javax.swing.JComboBox<String> typeComboBox;
    private javax.swing.JPanel typePanel;
    private javax.swing.JLabel typeTabel;
    // End of variables declaration//GEN-END:variables

    /**
     * The "listener" that updates the UI of this panel based on the changes of
     * fields on this panel. This is also the method to check whether all the
     * fields on this panel are correctly filled and decides whether to enable
     * the "Next" button or not.
     *
     * @param e the document event
     */
    public void updateUI(DocumentEvent e) {
        // Enable the Next button if the current DSP panel is valid
        String err = GetCurrentDSProcessor().validatePanel();
        if (null == err)
            this.wizPanel.enableNextButton(true);
        else
            this.wizPanel.enableNextButton(false);
    }

}
