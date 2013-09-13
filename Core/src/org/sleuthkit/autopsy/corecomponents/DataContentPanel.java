/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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

package org.sleuthkit.autopsy.corecomponents;

import java.awt.Cursor;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Instances of this class use child DataContentViewers to present one or more 
 * views of the content underlying a Node. The DataContentViewers interface is
 * an extension point for developers wishing to create additional viewers.
 */
public class DataContentPanel extends javax.swing.JPanel implements DataContent, ChangeListener {
    
    private static Logger logger = Logger.getLogger(DataContentPanel.class.getName());
    private final List<DataContentViewerUpdateManager> contentViewers = new ArrayList<>();
    private Node currentNode;
    private boolean listeningToTabbedPane = false;

    public DataContentPanel() {
        initComponents();
        customizeComponents();
    }
    
    private void customizeComponents() {
        // Get all implementers of DataContentViewer, decorate them (partially) 
        // with an update manager, and add their UI component to the child 
        // tabbed pane.
        Collection<? extends DataContentViewer> viewers = Lookup.getDefault().lookupAll(DataContentViewer.class);
        for (DataContentViewer viewer : viewers) {
            contentViewers.add(new DataContentViewerUpdateManager(viewer));
            jTabbedPane1.addTab(viewer.getTitle(), null, viewer.getComponent(), viewer.getToolTip());
        }
        
        // Disable all of the tabs until a node is selected.
        int numTabs = jTabbedPane1.getTabCount();
        for (int tab = 0; tab < numTabs; ++tab) {
            jTabbedPane1.setEnabledAt(tab, false);
        }                
    }
        
    // @@@ Why does this component need to be publicly exposed?
    public JTabbedPane getTabbedPanel() {
        return jTabbedPane1;
    }

    @Override
    public void setNode(Node selectedNode) {
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {            
            // The selected node is cached since it is only pushed to each 
            // content viewer as the corresponding tab is activated.
            currentNode = selectedNode;
                        
            activateTabs(selectedNode);
        } 
        finally {
            this.setCursor(null);
        }
    }
    
    /**
     * Activates the tabs capable of displaying the content underlying the
     * selected node, sets the current tab to be that of the preferred viewer 
     * for that content, and passes the node to that tab.   
     */
    public void activateTabs(Node selectedNode) {   
        if (listeningToTabbedPane == false) {
            jTabbedPane1.addChangeListener(this);        
            listeningToTabbedPane = true;
        }
        
        // Determine which tabs to activate and which viewer thinks it is the 
        // preferred viewer for the content underlying the selected node.
        int currTabIndex = jTabbedPane1.getSelectedIndex();
        int totalTabs = jTabbedPane1.getTabCount();
        int maxPreferred = 0;
        int preferredViewerIndex = 0;
        for (int i = 0; i < totalTabs; ++i) {
            DataContentViewerUpdateManager contentViewer = contentViewers.get(i);
            
            // Since this is a new node, marks each viewer as needing an update.
            contentViewer.resetComponent(); 

            if ((selectedNode == null) || (contentViewer.isSupported(selectedNode) == false)) {
                jTabbedPane1.setEnabledAt(i, false);
            } 
            else {
                // Enable this viewer's tab.
                jTabbedPane1.setEnabledAt(i, true);
                
                // Let the viewer make its case for having its tab selected.
                int currentPreferred = contentViewer.isPreferred(selectedNode, true);
                if (currentPreferred > maxPreferred) {
                    preferredViewerIndex = i;
                    maxPreferred = currentPreferred;
                }
            }
        }
        
        // Get the user's preference for the selected tab. The user's preference
        // takes priority over the votes casr by the viewers. 
        Preferences pref = NbPreferences.forModule(GeneralPanel.class);
        boolean keepCurrentViewer = pref.getBoolean("keepPreferredViewer", false);
        int tabIndex = keepCurrentViewer ? currTabIndex : preferredViewerIndex;

        // Select the tab and push the node to the lucky viewer corresponding to 
        // the selected tab - if that viewer can handle the node.
        jTabbedPane1.setSelectedIndex(tabIndex);
        DataContentViewerUpdateManager viewer = contentViewers.get(tabIndex);        
        if (jTabbedPane1.isEnabledAt(tabIndex)) {
            viewer.setNode(selectedNode);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
    }

    @Override
    public void stateChanged(ChangeEvent evt) {
        // Push the current node to the viewer corresponding to the selected tab
        // in the child tabbed pane.
        int currentTab = ((JTabbedPane)evt.getSource()).getSelectedIndex();
        if (currentTab != -1) {
            DataContentViewerUpdateManager viewer = contentViewers.get(currentTab);
            if (viewer.needsUpdate()) {
                this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                try {
                    viewer.setNode(currentNode);
                } 
                finally {
                    this.setCursor(null);
                }
            }
        }
    }

    private static class DataContentViewerUpdateManager {

        private DataContentViewer dataContentViewer;
        private boolean updateNeeded;

        DataContentViewerUpdateManager(DataContentViewer wrapped) {
            dataContentViewer = wrapped;
            updateNeeded = true;
        }

        void setNode(Node selectedNode) {
            dataContentViewer.setNode(selectedNode);
            updateNeeded = false;
        }

        void resetComponent() {
            dataContentViewer.resetComponent();
            updateNeeded = true;
        }

        boolean needsUpdate() {
            return updateNeeded;
        }

        boolean isSupported(Node node) {
            return dataContentViewer.isSupported(node);
        }
        
        int isPreferred(Node node, boolean isSupported) {
            return dataContentViewer.isPreferred(node, isSupported);
        }
    } 
        
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jTabbedPane1 = new javax.swing.JTabbedPane();

        setMinimumSize(new java.awt.Dimension(5, 5));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTabbedPane jTabbedPane1;
    // End of variables declaration//GEN-END:variables

}
