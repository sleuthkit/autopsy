/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.ListCellRenderer;
import javax.swing.event.DocumentEvent;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datasourceprocessors.RawDSProcessor;

/**
 * visual component for the first panel of add image wizard. Allows the user to
 * choose the data source type and then select the data source
 *
 */
final class AddImageWizardChooseDataSourceVisual extends JPanel {

    static final Logger logger = Logger.getLogger(AddImageWizardChooseDataSourceVisual.class.getName());

    private AddImageWizardChooseDataSourcePanel wizPanel;

    private JPanel currentPanel;

    private Map<String, DataSourceProcessor> datasourceProcessorsMap = new HashMap<>();

    List<String> coreDSPTypes = new ArrayList<>();

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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void customInit() {

        typePanel.setLayout(new BorderLayout());

        discoverDataSourceProcessors();

        // set up the DSP type combobox
        typeComboBox.removeAllItems();

        Set<String> dspTypes = datasourceProcessorsMap.keySet();

        // make a list of core DSPs
        // ensure that the core DSPs are at the top and in a fixed order
        coreDSPTypes.add(ImageDSProcessor.getType());
        // Local disk processing is not allowed for multi-user cases
        if (Case.getCurrentCase().getCaseType() != Case.CaseType.MULTI_USER_CASE) {
            coreDSPTypes.add(LocalDiskDSProcessor.getType());
        } else {
            // remove LocalDiskDSProcessor from list of DSPs
            datasourceProcessorsMap.remove(LocalDiskDSProcessor.getType());
        }
        coreDSPTypes.add(LocalFilesDSProcessor.getType());
        coreDSPTypes.add(RawDSProcessor.getType());
        
        for (String dspType : coreDSPTypes) {
            typeComboBox.addItem(dspType);
        }

        // now add any addtional DSPs that haven't already been added
        for (String dspType : dspTypes) {
            if (!coreDSPTypes.contains(dspType)) {
                typeComboBox.addItem(dspType);
            }
        }

        typeComboBox.setRenderer(new ComboboxSeparatorRenderer(typeComboBox.getRenderer()) {

            @Override
            protected boolean addSeparatorAfter(JList list, Object value, int index) {
                return (index == coreDSPTypes.size() - 1);
            }
        });

        //add actionlistner to listen for change
        ActionListener cbActionListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dspSelectionChanged();
            }
        };
        typeComboBox.addActionListener(cbActionListener);
        typeComboBox.setSelectedIndex(0);
    }

    private void discoverDataSourceProcessors() {

        for (DataSourceProcessor dsProcessor : Lookup.getDefault().lookupAll(DataSourceProcessor.class)) {

            if (!datasourceProcessorsMap.containsKey(dsProcessor.getDataSourceType())) {
                datasourceProcessorsMap.put(dsProcessor.getDataSourceType(), dsProcessor);
            } else {
                logger.log(Level.SEVERE, "discoverDataSourceProcessors(): A DataSourceProcessor already exists for type = {0}", dsProcessor.getDataSourceType()); //NON-NLS
            }
        }
    }

    private void dspSelectionChanged() {
        // update the current panel to selection
        currentPanel = getCurrentDSProcessor().getPanel();
        updateCurrentPanel(currentPanel);
    }

    /**
     * Changes the current panel to the given panel.
     *
     * @param panel instance of ImageTypePanel to change to
     */
    @SuppressWarnings("deprecation")
    private void updateCurrentPanel(JPanel panel) {
        currentPanel = panel;
        typePanel.removeAll();
        typePanel.add(currentPanel, BorderLayout.CENTER);
        typePanel.validate();
        typePanel.repaint();
        currentPanel.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString())) {
                    updateUI(null);
                }
                if (evt.getPropertyName().equals(DataSourceProcessor.DSP_PANEL_EVENT.FOCUS_NEXT.toString())) {
                    wizPanel.moveFocusToNext();
                }
            }
        });

        updateUI(null);
    }

    /**
     * Returns the currently selected DS Processor
     *
     * @return DataSourceProcessor the DataSourceProcessor corresponding to the
     *         data source type selected in the combobox
     */
    protected DataSourceProcessor getCurrentDSProcessor() {
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
        return NbBundle.getMessage(this.getClass(), "AddImageWizardChooseDataSourceVisual.getName.text");
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
        inputPanel = new javax.swing.JPanel();
        typeTabel = new javax.swing.JLabel();
        typePanel = new javax.swing.JPanel();
        typeComboBox = new javax.swing.JComboBox<String>();

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(AddImageWizardChooseDataSourceVisual.class, "AddImageWizardChooseDataSourceVisual.jLabel2.text")); // NOI18N

        setPreferredSize(new java.awt.Dimension(588, 328));

        org.openide.awt.Mnemonics.setLocalizedText(typeTabel, org.openide.util.NbBundle.getMessage(AddImageWizardChooseDataSourceVisual.class, "AddImageWizardChooseDataSourceVisual.typeTabel.text")); // NOI18N

        typePanel.setMinimumSize(new java.awt.Dimension(0, 65));
        typePanel.setPreferredSize(new java.awt.Dimension(521, 65));

        javax.swing.GroupLayout typePanelLayout = new javax.swing.GroupLayout(typePanel);
        typePanel.setLayout(typePanelLayout);
        typePanelLayout.setHorizontalGroup(
            typePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 548, Short.MAX_VALUE)
        );
        typePanelLayout.setVerticalGroup(
            typePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 225, Short.MAX_VALUE)
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
                        .addGap(0, 119, Short.MAX_VALUE))
                    .addComponent(typePanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 548, Short.MAX_VALUE))
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
                .addComponent(typePanel, javax.swing.GroupLayout.PREFERRED_SIZE, 225, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(inputPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(inputPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(44, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JPanel inputPanel;
    private javax.swing.JLabel jLabel2;
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
        this.wizPanel.enableNextButton(getCurrentDSProcessor().isPanelValid());
    }

    @SuppressWarnings("rawtypes")
    public abstract class ComboboxSeparatorRenderer implements ListCellRenderer {

        private ListCellRenderer delegate;

        private JPanel separatorPanel = new JPanel(new BorderLayout());

        private JSeparator separator = new JSeparator();

        public ComboboxSeparatorRenderer(ListCellRenderer delegate) {
            this.delegate = delegate;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component comp = delegate.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (index != -1 && addSeparatorAfter(list, value, index)) {
                separatorPanel.removeAll();
                separatorPanel.add(comp, BorderLayout.CENTER);
                separatorPanel.add(separator, BorderLayout.SOUTH);
                return separatorPanel;
            } else {
                return comp;
            }
        }

        protected abstract boolean addSeparatorAfter(JList list, Object value, int index);
    }
}
