/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.filesearch;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.DefaultListModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Subpanel with controls for data source filtering.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class DataSourcePanel extends javax.swing.JPanel {

    private static final Logger logger = Logger.getLogger(DataSourcePanel.class.getName());
    private static final long serialVersionUID = 1L;
    private final Map<Long, String> dataSourceMap = new HashMap<>();

    /**
     * Creates new form DataSourcePanel
     */
    public DataSourcePanel() {
        initComponents();
        resetDataSourcePanel();
        
         Case.addEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_ADDED), (PropertyChangeEvent evt) -> {
           if(evt.getPropertyName().equals(Case.Events.DATA_SOURCE_ADDED.toString())) {
               List<String> strings = getDataSourceArray();
               for (String dataSource : strings) {
                   DefaultListModel<String> model = (DefaultListModel<String>) dataSourceList.getModel();
                   if(!model.contains(dataSource)) {
                       model.addElement(dataSource);
                   }
                } 
            }
        });
    }

    /**
     * Reset the data source panel to be up to date with the current case.
     *
     */
    final void resetDataSourcePanel() {
        dataSourceList.clearSelection();
        //remove all list selection listeners
        for (ListSelectionListener listener :  dataSourceList.getListSelectionListeners()){
            dataSourceList.removeListSelectionListener(listener);
        }    
        ((DefaultListModel<String>) dataSourceList.getModel()).clear();
        List<String> strings = getDataSourceArray();
        for (String dataSource : strings) {
            ((DefaultListModel<String>) dataSourceList.getModel()).addElement(dataSource);
        }
        dataSourceList.setEnabled(false);
        dataSourceCheckBox.setSelected(false);
        dataSourceNoteLabel.setEnabled(false);
        if (dataSourceList.getModel().getSize() > 1) {
            dataSourceList.addListSelectionListener((ListSelectionEvent evt) -> {
                firePropertyChange(FileSearchPanel.EVENT.CHECKED.toString(), null, null);
            });
            dataSourceCheckBox.setEnabled(true);
        } else {
            /*
             * Disable data source filtering since there aren't multiple data
             * sources to choose from.
             */
            this.dataSourceCheckBox.setEnabled(false);
        }
    }

    /**
     * Get dataSourceMap with object id and data source display name.
     *
     * @return The list of data source name
     */
    private List<String> getDataSourceArray() {
        List<String> dsList = new ArrayList<>();
        try {
            Case currentCase = Case.getCurrentCaseThrows();
            SleuthkitCase tskDb = currentCase.getSleuthkitCase();
            List<DataSource> dataSources = tskDb.getDataSources();
            Collections.sort(dataSources, (DataSource ds1, DataSource ds2) -> ds1.getName().compareTo(ds2.getName()));
            for (DataSource ds : dataSources) {
                String dsName = ds.getName();
                File dataSourceFullName = new File(dsName);
                String displayName = dataSourceFullName.getName();
                dataSourceMap.put(ds.getId(), displayName);
                dsList.add(displayName);
            }
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Unable to get current open case.", ex);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get data source info from database.", ex);
        }
        return dsList;
    }

    /**
     * Get a set of data source object ids that are selected.
     *
     * @return A set of selected object ids.
     */
    Set<Long> getDataSourcesSelected() {
        Set<Long> dataSourceObjIdSet = new HashSet<>();
        List<String> dataSources = dataSourceList.getSelectedValuesList();
        for (Long key : dataSourceMap.keySet()) {
            String value = dataSourceMap.get(key);
            for (String dataSource : dataSources) {
                if (value.equals(dataSource)) {
                    dataSourceObjIdSet.add(key);
                }
            }
        }
        return dataSourceObjIdSet;
    }

    /**
     * Is dataSourceCheckBox selected
     *
     * @return true if the dataSoureCheckBox is selected
     */
    boolean isSelected() {
        return this.dataSourceCheckBox.isSelected();
    }

    /**
     * Enable the dsList and dataSourceNoteLable if the dataSourceCheckBox is
     * checked.
     */
    final void setComponentsEnabled() {
        boolean enabled = this.isSelected();
        this.dataSourceList.setEnabled(enabled);
        this.dataSourceNoteLabel.setEnabled(enabled);
    }

    /**
     * Set the data source initially selected in this filter.
     *
     * @param dataSourceId - The object ID of the data source which will be
     *                     selected.
     */
    void setDataSourceSelected(long dataSourceId) {
        this.dataSourceCheckBox.setSelected(true);
        setComponentsEnabled();
        String dataSourceName = dataSourceMap.get(dataSourceId);
        dataSourceList.setSelectedValue(dataSourceName, true);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        dataSourceList = new javax.swing.JList<>();
        dataSourceCheckBox = new javax.swing.JCheckBox();
        dataSourceNoteLabel = new javax.swing.JLabel();

        setMinimumSize(new java.awt.Dimension(150, 150));
        setPreferredSize(new java.awt.Dimension(150, 150));

        dataSourceList.setModel(new DefaultListModel<String>());
        dataSourceList.setEnabled(false);
        dataSourceList.setMinimumSize(new java.awt.Dimension(0, 200));
        jScrollPane1.setViewportView(dataSourceList);

        org.openide.awt.Mnemonics.setLocalizedText(dataSourceCheckBox, org.openide.util.NbBundle.getMessage(DataSourcePanel.class, "DataSourcePanel.dataSourceCheckBox.text")); // NOI18N
        dataSourceCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSourceCheckBoxActionPerformed(evt);
            }
        });

        dataSourceNoteLabel.setFont(dataSourceNoteLabel.getFont().deriveFont(dataSourceNoteLabel.getFont().getSize()-1f));
        org.openide.awt.Mnemonics.setLocalizedText(dataSourceNoteLabel, org.openide.util.NbBundle.getMessage(DataSourcePanel.class, "DataSourcePanel.dataSourceNoteLabel.text")); // NOI18N
        dataSourceNoteLabel.setEnabled(false);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(dataSourceCheckBox)
                .addGap(0, 0, Short.MAX_VALUE))
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(dataSourceNoteLabel)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(dataSourceCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 103, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(dataSourceNoteLabel)
                .addContainerGap())
        );

        dataSourceCheckBox.getAccessibleContext().setAccessibleName("");
    }// </editor-fold>//GEN-END:initComponents

    private void dataSourceCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSourceCheckBoxActionPerformed
        setComponentsEnabled();
        firePropertyChange(FileSearchPanel.EVENT.CHECKED.toString(), null, null);
        this.dataSourceList.setSelectedIndices(new int[0]);
    }//GEN-LAST:event_dataSourceCheckBoxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox dataSourceCheckBox;
    private javax.swing.JList<String> dataSourceList;
    private javax.swing.JLabel dataSourceNoteLabel;
    private javax.swing.JScrollPane jScrollPane1;
    // End of variables declaration//GEN-END:variables
}
