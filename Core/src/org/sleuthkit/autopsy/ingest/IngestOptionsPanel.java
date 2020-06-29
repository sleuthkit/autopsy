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
package org.sleuthkit.autopsy.ingest;

import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSetDefsPanel;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSetDefsPanel.PANEL_TYPE;

/**
 * Global options panel for keyword searching.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class IngestOptionsPanel extends IngestModuleGlobalSettingsPanel implements OptionsPanel {

    @NbBundle.Messages({"IngestOptionsPanel.settingsTab.text=Settings",
        "IngestOptionsPanel.settingsTab.toolTipText=Settings regarding resources available to ingest.",
        "IngestOptionsPanel.fileFiltersTab.text=File Filters",
        "IngestOptionsPanel.fileFiltersTab.toolTipText=Settings for creating and editing ingest file filters.",
        "IngestOptionsPanel.profilesTab.text=Profiles",
        "IngestOptionsPanel.profilesTab.toolTipText=Settings for creating and editing profiles."})

    private FilesSetDefsPanel filterPanel;
    private final static int INDEX_OF_FILTER_PANEL = 0;
    private IngestSettingsPanel settingsPanel;
    private final static int INDEX_OF_SETTINGS_PANEL = 2;
    private ProfileSettingsPanel profilePanel;
    private final static int INDEX_OF_PROFILE_PANEL = 1;
    private int indexOfPreviousTab;
    /**
     * This panel implements a property change listener that listens to ingest
     * job events so it can disable the buttons on the panel if ingest is
     * running. This is done to prevent changes to user-defined types while the
     * type definitions are in use.
     */
    IngestJobEventPropertyChangeListener ingestJobEventsListener;

    public IngestOptionsPanel() {
        initComponents();
        customizeComponents();
        indexOfPreviousTab = tabbedPane.getSelectedIndex();
    }

    private void customizeComponents() {
        filterPanel = new FilesSetDefsPanel(PANEL_TYPE.FILE_INGEST_FILTERS);
        settingsPanel = new IngestSettingsPanel();
        profilePanel = new ProfileSettingsPanel();

        tabbedPane.insertTab(NbBundle.getMessage(IngestOptionsPanel.class, "IngestOptionsPanel.fileFiltersTab.text"), null,
                filterPanel, NbBundle.getMessage(IngestOptionsPanel.class, "IngestOptionsPanel.fileFiltersTab.toolTipText"), INDEX_OF_FILTER_PANEL);
        tabbedPane.insertTab(NbBundle.getMessage(IngestOptionsPanel.class, "IngestOptionsPanel.profilesTab.text"), null,
                profilePanel, NbBundle.getMessage(IngestOptionsPanel.class, "IngestOptionsPanel.profilesTab.toolTipText"), INDEX_OF_PROFILE_PANEL);
        tabbedPane.insertTab(NbBundle.getMessage(IngestOptionsPanel.class, "IngestOptionsPanel.settingsTab.text"), null,
                settingsPanel, NbBundle.getMessage(IngestOptionsPanel.class, "IngestOptionsPanel.settingsTab.toolTipText"), INDEX_OF_SETTINGS_PANEL);
        //Listener for when tabbed panes are switched, because we can have two file filter definitions panels open at the same time
        //we may wind up in a situation where the user has created and saved one in the profiles panel
        //so we need to refresh the filterPanel in those cases before proceeding.
        tabbedPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (e.getSource() instanceof JTabbedPane) {
                    //If we are switching to a filter panel we should load 
                    //load the filter panel to ensure it is up to date
                    //incase a filter was addded through the Profile->new->create new filter manner
                    if (tabbedPane.getSelectedIndex() == INDEX_OF_FILTER_PANEL && tabbedPane.getSelectedIndex() != indexOfPreviousTab) {
                        filterPanel.load();
                    }
                    //save the contents of whichever Tab we just switched from
                    saveTabByIndex(indexOfPreviousTab);
                    //save the index of the current tab for the next time we switch
                    indexOfPreviousTab = tabbedPane.getSelectedIndex();
                }
            }
        });

        addIngestJobEventsListener();
        enableTabs();
    }

    /**
     * Add a property change listener that listens to ingest job events to
     * disable the buttons on the panel if ingest is running. This is done to
     * prevent changes to user-defined types while the type definitions are in
     * use.
     */
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

    /**
     * Disables tabs and options inside of tabs during Ingest, and re-enables
     * them after Ingest is complete or cancelled.
     */
    private void enableTabs() {
        boolean ingestIsRunning = IngestManager.getInstance().isIngestRunning();
        tabbedPane.setEnabled(!ingestIsRunning);
        settingsPanel.enableButtons(!ingestIsRunning);
        profilePanel.enableButtons(!ingestIsRunning);
        filterPanel.enableButtons();

    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        super.addPropertyChangeListener(l);
        /*
         * There is at least one look and feel library that follows the bad
         * practice of calling overrideable methods in a constructor, e.g.:
         *
         * at
         * javax.swing.plaf.synth.SynthPanelUI.installListeners(SynthPanelUI.java:83)
         * at
         * javax.swing.plaf.synth.SynthPanelUI.installUI(SynthPanelUI.java:63)
         * at javax.swing.JComponent.setUI(JComponent.java:666) at
         * javax.swing.JPanel.setUI(JPanel.java:153) at
         * javax.swing.JPanel.updateUI(JPanel.java:126) at
         * javax.swing.JPanel.<init>(JPanel.java:86) at
         * javax.swing.JPanel.<init>(JPanel.java:109) at
         * javax.swing.JPanel.<init>(JPanel.java:117)
         *
         * When this happens, the following child components of this JPanel
         * subclass have not been constructed yet, since this panel's
         * constructor has not been called yet.
         */
        if (null != filterPanel) {
            filterPanel.addPropertyChangeListener(l);
        }
        if (null != settingsPanel) {
            settingsPanel.addPropertyChangeListener(l);
        }
        if (null != profilePanel) {
            profilePanel.addPropertyChangeListener(l);
        }
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        filterPanel.removePropertyChangeListener(l);
        settingsPanel.removePropertyChangeListener(l);
        profilePanel.removePropertyChangeListener(l);
    }

    @Override
    public void saveSettings() {
        saveTabByIndex(tabbedPane.getSelectedIndex());
    }

    /**
     * Save the panel which is in the tab corresponding to the specified index.
     *
     * @param index - the index of the tab you wish to save the contents of
     */
    private void saveTabByIndex(int index) {
        //Because we can create filters in two seperate windows here we need 
        //to be careful not to save an out of date list over the current list
        switch (index) {
            case (INDEX_OF_FILTER_PANEL):
                filterPanel.saveSettings();
                break;
            case (INDEX_OF_PROFILE_PANEL):
                profilePanel.saveSettings();
                break;
            case (INDEX_OF_SETTINGS_PANEL):
                settingsPanel.saveSettings();
                break;
            default:
            //don't save anything if it wasn't a tab index that should exist
        }
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

    /**
     * Method called when the cancel button is clicked.
     */
    void cancel() {
        //doesn't need to do anything
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
