/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSetDefsPanel;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSetDefsPanel.PANEL_TYPE;

/**
 * Global options panel for keyword searching.
 */
class IngestOptionsPanel extends IngestModuleGlobalSettingsPanel implements OptionsPanel {
@NbBundle.Messages({"IngestOptionsPanel.settingsTab.text=Settings",
  "IngestOptionsPanel.settingsTab.toolTipText=Settings regarding resources available to ingest.",
  "IngestOptionsPanel.fileFiltersTab.text=File Filters",
  "IngestOptionsPanel.fileFiltersTab.toolTipText=Settings for creating and editing ingest file filters.",
  "IngestOptionsPanel.profilesTab.text=Profiles",
  "IngestOptionsPanel.profilesTab.toolTipText=Settings for creating and editing profiles.",
  "IngestOptionsPanel.title.text=Ingest Options"
})
    private FilesSetDefsPanel filterPanel;
    private IngestSettingsPanel settingsPanel;
    private ProfileSettingsPanel profilePanel;
    /**
     * This panel implements a property change listener that listens to ingest
     * job events so it can disable the buttons on the panel if ingest is
     * running. This is done to prevent changes to user-defined types while the
     * type definitions are in use.
     */
    IngestJobEventPropertyChangeListener ingestJobEventsListener;
    
    IngestOptionsPanel() {
        initComponents();
        customizeComponents();
    }

    private void customizeComponents() {
        setName(NbBundle.getMessage(IngestOptionsPanel.class, "IngestOptionsPanel.title.text")); 
        filterPanel = new FilesSetDefsPanel(PANEL_TYPE.FILE_INGEST_FILTERS);
        settingsPanel = new IngestSettingsPanel();
        profilePanel = new ProfileSettingsPanel();
        tabbedPane.insertTab(NbBundle.getMessage(IngestOptionsPanel.class, "IngestOptionsPanel.settingsTab.text") , null, 
                settingsPanel, NbBundle.getMessage(IngestOptionsPanel.class, "IngestOptionsPanel.settingsTab.toolTipText"), 0); 
        tabbedPane.insertTab(NbBundle.getMessage(IngestOptionsPanel.class, "IngestOptionsPanel.fileFiltersTab.text"), null, 
                filterPanel, NbBundle.getMessage(IngestOptionsPanel.class, "IngestOptionsPanel.fileFiltersTab.toolTipText"), 1); 
        tabbedPane.insertTab(NbBundle.getMessage(IngestOptionsPanel.class, "IngestOptionsPanel.profilesTab.text"), null, 
                profilePanel, NbBundle.getMessage(IngestOptionsPanel.class, "IngestOptionsPanel.profilesTab.toolTipText"), 2); 
        addIngestJobEventsListener();
    }
    
       /**
     * Add a property change listener that listens to ingest job events to
     * disable the buttons on the panel if ingest is running. This is done to
     * prevent changes to user-defined types while the type definitions are in
     * use.
     */
    // TODO: Disabling during ingest would not be necessary if the file ingest
    // modules obtained and shared a per data source ingest job snapshot of the
    // file type definitions.    
    private void addIngestJobEventsListener() {
        ingestJobEventsListener = new IngestJobEventPropertyChangeListener();
        IngestManager.getInstance().addIngestJobEventListener(ingestJobEventsListener);
    }

    /**
     * A property change listener that listens to ingest job events.
     */
    private class IngestJobEventPropertyChangeListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    enableTabs();
                }
            });
        }
    }
    
    private void enableTabs(){
        boolean ingestIsRunning = IngestManager.getInstance().isIngestRunning();
        tabbedPane.setEnabled(!ingestIsRunning);
        settingsPanel.enableButtons(!ingestIsRunning);
        profilePanel.enableButtons(!ingestIsRunning);
        filterPanel.enableButtons(!ingestIsRunning);
    
    }
    
    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        filterPanel.addPropertyChangeListener(l);
        settingsPanel.addPropertyChangeListener(l);
        profilePanel.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        filterPanel.removePropertyChangeListener(l);
        settingsPanel.removePropertyChangeListener(l);
        profilePanel.removePropertyChangeListener(l);
    }

    @Override
    public void saveSettings() {
        filterPanel.store();
        settingsPanel.store();
        profilePanel.store();
    }

    @Override
    public void store() {
        saveSettings();
    }

    @Override
    public void load() {
        filterPanel.load();
        settingsPanel.load();
        profilePanel.load();
    }

    boolean valid() {
        return true;
    }
    
    void cancel(){
    }
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        tabbedPane = new javax.swing.JTabbedPane();

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(tabbedPane, javax.swing.GroupLayout.DEFAULT_SIZE, 824, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(tabbedPane, javax.swing.GroupLayout.DEFAULT_SIZE, 543, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTabbedPane tabbedPane;
    // End of variables declaration//GEN-END:variables
}
