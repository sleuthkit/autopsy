/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2020 Basis Technology Corp.
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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.ListCellRenderer;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.python.JythonModuleLoader;

/**
 * visual component for the first panel of add image wizard. Allows the user to
 * choose the data source type and then select the data source
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class AddImageWizardDataSourceSettingsVisual extends JPanel {

    private static final Logger logger = Logger.getLogger(AddImageWizardDataSourceSettingsVisual.class.getName());

    private final AddImageWizardDataSourceSettingsPanel wizPanel;

    private JPanel currentPanel;

    private final Map<String, DataSourceProcessor> datasourceProcessorsMap = new HashMap<>();

    private String currentDsp;

    /**
     * Creates new form AddImageVisualPanel1
     *
     * @param wizPanel corresponding WizardPanel to handle logic of wizard step
     */
    AddImageWizardDataSourceSettingsVisual(AddImageWizardDataSourceSettingsPanel wizPanel) {
        initComponents();
        this.wizPanel = wizPanel;
        typePanel.setLayout(new BorderLayout());
        discoverDataSourceProcessors();
        currentDsp = ImageDSProcessor.getType(); //default value to the ImageDSProcessor
    }

    /**
     * Populate the map of DataSourceProcessors which so they can be retrieved
     * by name.
     */
    private void discoverDataSourceProcessors() {
        for (DataSourceProcessor dsProcessor : Lookup.getDefault().lookupAll(DataSourceProcessor.class)) {
            if (!datasourceProcessorsMap.containsKey(dsProcessor.getDataSourceType())) {
                datasourceProcessorsMap.put(dsProcessor.getDataSourceType(), dsProcessor);
            } else {
                logger.log(Level.SEVERE, "discoverDataSourceProcessors(): A DataSourceProcessor already exists for type = {0}", dsProcessor.getDataSourceType()); //NON-NLS
            }
        }

        for (DataSourceProcessor dsProcessor : JythonModuleLoader.getDataSourceProcessorModules()) {
            if (!datasourceProcessorsMap.containsKey(dsProcessor.getDataSourceType())) {
                datasourceProcessorsMap.put(dsProcessor.getDataSourceType(), dsProcessor);
            } else {
                logger.log(Level.SEVERE, "discoverDataSourceProcessors(): A DataSourceProcessor already exists for type = {0}", dsProcessor.getDataSourceType()); //NON-NLS
            }
        }
    }

    /**
     * Set the current DataSourceProcessor and update the panel to reflect that
     * selection.
     *
     * @param dsType - the name of the DataSourceProcessor you wish to have this
     *               panel display settings for.
     */
    void setDspSelection(String dsType) {
        currentDsp = dsType;
        currentPanel = datasourceProcessorsMap.get(dsType).getPanel();
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
                    wizPanel.enableNextButton(getCurrentDSProcessor().isPanelValid());
                }
                if (evt.getPropertyName().equals(DataSourceProcessor.DSP_PANEL_EVENT.FOCUS_NEXT.toString())) {
                    wizPanel.moveFocusToNext();
                }
            }
        });
        this.wizPanel.enableNextButton(getCurrentDSProcessor().isPanelValid());
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
        DataSourceProcessor dsProcessor = datasourceProcessorsMap.get(currentDsp);
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

        typePanel = new javax.swing.JPanel();

        setPreferredSize(new java.awt.Dimension(588, 328));

        typePanel.setMinimumSize(new java.awt.Dimension(0, 65));
        typePanel.setPreferredSize(new java.awt.Dimension(521, 65));

        javax.swing.GroupLayout typePanelLayout = new javax.swing.GroupLayout(typePanel);
        typePanel.setLayout(typePanelLayout);
        typePanelLayout.setHorizontalGroup(
            typePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 588, Short.MAX_VALUE)
        );
        typePanelLayout.setVerticalGroup(
            typePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 328, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(typePanel, javax.swing.GroupLayout.DEFAULT_SIZE, 588, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(typePanel, javax.swing.GroupLayout.DEFAULT_SIZE, 328, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel typePanel;
    // End of variables declaration//GEN-END:variables

    @SuppressWarnings("rawtypes")
    public abstract class ComboboxSeparatorRenderer implements ListCellRenderer {

        private final ListCellRenderer delegate;

        private final JPanel separatorPanel = new JPanel(new BorderLayout());

        private final JSeparator separator = new JSeparator();

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
