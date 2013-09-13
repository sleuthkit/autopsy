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
import java.util.logging.Level;
import java.util.prefs.Preferences;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this class use child DataContentViewers to present one or more 
 * views of the content underlying a Node. The DataContentViewers interface is
 * an extension point for developers wishing to create additional viewers.
 */
public class DataContentPanel extends javax.swing.JPanel implements DataContent, ChangeListener {
    
    private static Logger logger = Logger.getLogger(DataContentPanel.class.getName());
    private final List<UpdateWrapper> viewers = new ArrayList<>();;
    private Node currentNode;

    public DataContentPanel() {
        initComponents();
        
        // Get all implementers of DataContentViewer, decorate them (partially) 
        // with an update manager, and add their UI component to the child 
        // tabbed pane.
        Collection<? extends DataContentViewer> views = Lookup.getDefault().lookupAll(DataContentViewer.class);
        for (DataContentViewer view : views) {
            viewers.add(new UpdateWrapper(view));
            jTabbedPane1.addTab(view.getTitle(), null, view.getComponent(), view.getToolTip());
        }
        
        // Disable all of the tabs until a node is received.
        int numTabs = jTabbedPane1.getTabCount();
        for (int tab = 0; tab < numTabs; ++tab) {
            jTabbedPane1.setEnabledAt(tab, false);
        }
        
        // Listen to the tabbed pane to be able to push the current node to the
        // selected viewer. 
        jTabbedPane1.addChangeListener(this);
    }
        
    public JTabbedPane getTabPanels() {
        return jTabbedPane1;
    }

    @Override
    public void setNode(Node selectedNode) {
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            String defaultName = NbBundle.getMessage(DataContentTopComponent.class, "CTL_DataContentTopComponent");
            // set the file path
            if (selectedNode == null) {
                setName(defaultName);
            } 
            else {
                Content content = selectedNode.getLookup().lookup(Content.class);
                if (content != null) {
                    String path = defaultName;
                    try {
                        path = content.getUniquePath();
                    } 
                    catch (TskCoreException ex) {
                        logger.log(Level.SEVERE, "Exception while calling Content.getUniquePath() for " + content);
                    }
                    setName(path);
                } 
                else {
                    setName(defaultName);
                }
            }

            // 
            currentNode = selectedNode;
            setupTabs(selectedNode);
        } 
        finally {
            this.setCursor(null);
        }
    }
    
    /**
     * Resets the tabs based on the selected Node. If the selected node is null
     * or not supported, disable that tab as well.
     *
     * @param selectedNode  the selected content Node
     */
    public void setupTabs(Node selectedNode) {    
        // get the preference for the preferred viewer
        Preferences pref = NbPreferences.forModule(GeneralPanel.class);
        boolean keepCurrentViewer = pref.getBoolean("keepPreferredViewer", false);

        int currTabIndex = jTabbedPane1.getSelectedIndex();
        int totalTabs = jTabbedPane1.getTabCount();
        int maxPreferred = 0;
        int preferredViewerIndex = 0;
        for (int i = 0; i < totalTabs; ++i) {
            UpdateWrapper dcv = viewers.get(i);
            dcv.resetComponent(); // Marks each viewer as "dirty."

            // disable an unsupported tab (ex: picture viewer)
            if ((selectedNode == null) || (dcv.isSupported(selectedNode) == false)) {
                jTabbedPane1.setEnabledAt(i, false);
            } else {
                jTabbedPane1.setEnabledAt(i, true);
                
                // remember the viewer with the highest preference value
                int currentPreferred = dcv.isPreferred(selectedNode, true);
                if (currentPreferred > maxPreferred) {
                    preferredViewerIndex = i;
                    maxPreferred = currentPreferred;
                }
            }
        }
        
        // let the user decide if we should stay with the current viewer
        int tabIndex = keepCurrentViewer ? currTabIndex : preferredViewerIndex;

        // set the tab to the one the user wants, then set that viewer's node.
        jTabbedPane1.setSelectedIndex(tabIndex);
        UpdateWrapper dcv = viewers.get(tabIndex);
        // this is really only needed if no tabs were enabled 
        if (jTabbedPane1.isEnabledAt(tabIndex) == false) {
            dcv.resetComponent();
        }
        else {
            dcv.setNode(selectedNode);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
    }

    @Override
    public void stateChanged(ChangeEvent evt) {
        // Push the current node to the viewer corresponding to the active tab
        // in the child tabbed pane.
        int currentTab = ((JTabbedPane)evt.getSource()).getSelectedIndex();
        if (currentTab != -1) {
            UpdateWrapper viewer = viewers.get(currentTab);
            if (viewer.isOutdated()) {
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

    private static class UpdateWrapper {

        private DataContentViewer wrapped;
        private boolean outdated;

        UpdateWrapper(DataContentViewer wrapped) {
            this.wrapped = wrapped;
            this.outdated = true;
        }

        void setNode(Node selectedNode) {
            this.wrapped.setNode(selectedNode);
            this.outdated = false;
        }

        void resetComponent() {
            this.wrapped.resetComponent();
            this.outdated = true;
        }

        boolean isOutdated() {
            return this.outdated;
        }

        boolean isSupported(Node node) {
            return this.wrapped.isSupported(node);
        }
        
        int isPreferred(Node node, boolean isSupported) {
            return this.wrapped.isPreferred(node, isSupported);
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
