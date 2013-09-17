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
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResult;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;

/**
 * Instances of this class use child DataResultViewers to present views of the 
 * child nodes of a selected node. The DataResultViewer interface is
 * an extension point for developers wishing to create additional viewers.
 * 
 * Instances of this class are usually expected to be children of a TopComponent
 * that will push a selected "root" node to it; the panel then pushes selected
 * children of the root node to an implementer of the DataContent interface.
 * This DataContent object will the "main" content viewer, and the panel is
 * expected to be the "main" (directory listing) result panel. It is, however,
 * possible to create additional "non-main" panels with private content viewers.
 */
// @@@ We should reconsider the decision to make DataContent an extension point
// since we expect there to be only one of these and have provided for the 
// possibility of a "custom" implementation that bypasses the default Lookup.
public class DataResultPanel extends javax.swing.JPanel implements DataResult, ChangeListener {

    private ExplorerManager explorerManager;
    private Node currentNode;
    private boolean listeningToTabbedPane = false;    
    private PropertyChangeSupport resultPanelListeners;    
    private final List<DataResultViewer> resultViewers = new ArrayList<>();
    private DataContent customContentViewer; 
    private boolean isMain;
    
    private DataResultPanel() {
        this.isMain = true;
        resultPanelListeners = new PropertyChangeSupport(this);
        initComponents();
    }

    DataResultPanel(boolean isMain, String title) {
        this();
        this.isMain = isMain;
        setName(title);
    }

    DataResultPanel(String title, DataContent customContentViewer) {
        this(false, title);
        setName(title);
        this.customContentViewer = customContentViewer; 
    }
    
    /**
     * Creates and opens a "non-main" result panel.
     *
     * @param title Title of the result panel
     * @param pathText Descriptive text about the source of the nodes displayed
     * @param givenNode The new root node
     * @param totalMatches Cardinality of root node's children
     * @return a new DataResultPanel instance representing a custom data result viewer
     */
    public static DataResultPanel createInstance(String title, String pathText, Node givenNode, int totalMatches) {
        DataResultPanel newDataResult = new DataResultPanel(false, title);
        createInstanceCommon(pathText, givenNode, totalMatches, newDataResult);
        newDataResult.open();
        return newDataResult;
    }

    /**
     * Creates and opens a "non-main" result panel with a private DataContent 
     * object to which push selected nodes. The private DataContent object takes 
     * the place of the one that would normally be obtained via the default 
     * Lookup.
     *
     * @param title Title of the component window
     * @param pathText Descriptive text about the source of the nodes displayed
     * @param givenNode The new root node
     * @param totalMatches Cardinality of root node's children
     * @param dataContent An implementer of DataContent to which to push node selections
     * @return a new DataResultPanel instance representing a custom data result viewer
     */
    public static DataResultPanel createInstance(String title, String pathText, Node givenNode, int totalMatches, DataContent dataContent) {
        DataResultPanel newDataResult = new DataResultPanel(title, dataContent);
        createInstanceCommon(pathText, givenNode, totalMatches, newDataResult);
        newDataResult.open();
        return newDataResult;
    }
 
    private static void createInstanceCommon(String pathText, Node givenNode, int totalMatches, DataResultPanel newDataResult) {
        newDataResult.numberMatchLabel.setText(Integer.toString(totalMatches));
        newDataResult.setNode(givenNode);
        newDataResult.setPath(pathText);
    }

    @Override
    public void setTitle(String title) {
        setName(title);
    }

    @Override
    public void setPath(String pathText) {
        directoryTablePath.setText(pathText);
    }

    @Override
    public boolean isMain() {
        return isMain;
    }
    
    @Override
    public String getPreferredID() {
        return getName();
    }
            
    @Override
    public synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        resultPanelListeners.addPropertyChangeListener(listener);
    }

    @Override
    public synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
        resultPanelListeners.removePropertyChangeListener(listener);
    }
        
    /**
     * Initializes the panel internals and activates it.
     */
    void open() {
        if (null == explorerManager) {
            getExplorerManager();
        }
        
        if (dataResultTabbedPanel.getTabCount() == 0) {
            addDataResultViewers();
        }

        // @@@ Variations in behavior for the "main" (directory listing) panel may be obsolete.    
        if (isMain) {
            // if no node selected on DataExplorer, clear the field
            if (currentNode == null) {
                setNode(currentNode);
            }
        }

        this.setVisible(true);
    }

    private void getExplorerManager() {
        if (null == explorerManager) {
            // Get an ExplorerManager to pass to the child DataResultViewers. If the application
            // components are put together as expected, this will be an ExplorerManager owned
            // by an ancestor TopComponent. The TopComponent will have put this ExplorerManager
            // in a Lookup that is set as the action global context when the TopComponent has 
            // focus. This makes Node selections available to Actions without coupling the
            // actions to a particular Component. Note that getting the ExplorerManager in the
            // constructor would be too soon, since the object has no ancestor TopComponent at
            // that point.
            explorerManager = ExplorerManager.find(this);

            // A DataResultPanel listens for Node selections in its DataResultViewers so it 
            // can push the selections both to its child DataResultViewers and to a DataContent object. 
            // The default DataContent object is a DataContentTopComponent in the data content mode (area),
            // and is the parent of a DataContentPanel that hosts a set of DataContentViewers. 
            explorerManager.addPropertyChangeListener(new ExplorerManagerNodeSelectionListener());
        }        
    }
    
    private void addDataResultViewers() {
        // Add the default DataResultViewer service providers to the tabbed pane.
        addDataResultViewer(new DataResultViewerTable(this.explorerManager));
        addDataResultViewer(new DataResultViewerThumbnail(this.explorerManager));

        // Find additional DataResultViewer service providers and add them to the tabbed pane.
        // @@@ DataResultViewers using this extension point do not currently get a reference
        // to the DataResultTopComponent's ExplorerManager, so any node selection events in
        // them or their child components are not available to Actions via the action global
        // context lookup. One way to fix this would be to change the DataResultViewer.createInstance()
        // method called below to accept an ExplorerManager argument.
        for (DataResultViewer viewer : Lookup.getDefault().lookupAll(DataResultViewer.class)) {
            if (isMain) {
                // This only works if there is truly only one "main" panel!
                addDataResultViewer(viewer);
            }
            else {
                // This is a necessary consequence of having a "main" panel and
                // "non-main" panels. The panels need their own DataResultViewer
                // instances.
                addDataResultViewer(viewer.createInstance());
            }
        }        
    }
    
    private class ExplorerManagerNodeSelectionListener implements PropertyChangeListener {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (!Case.isCaseOpen()) {
                // Handle the in-between condition when case is being closed
                // and legacy selection events are pumped.
                return;
            }

            String propertyChanged = evt.getPropertyName();
            if (propertyChanged.equals(ExplorerManager.PROP_SELECTED_NODES)) {
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                // If a custom DataContent object has not been specified, get the default instance.
                DataContent contentViewer = customContentViewer;
                if (null == contentViewer) {
                    contentViewer = Lookup.getDefault().lookup(DataContent.class);
                }

                try {
                    Node[] selectedNodes = explorerManager.getSelectedNodes();
                    for (DataResultViewer drv : resultViewers) {
                        drv.setSelectedNodes(selectedNodes);
                    }                                

                    // Passing null signals that either multiple nodes are selected, or no nodes are selected. 
                    // This is important to the DataContent object, since the content mode (area) of the app is designed 
                    // to show only the content underlying a single Node.                                
                    if (selectedNodes.length == 1) {
                        contentViewer.setNode(selectedNodes[0]);
                    } 
                    else {                                    
                        contentViewer.setNode(null);
                    }
                } 
                finally {
                    setCursor(null);
                }
            }
        }
    }
        
    private void addDataResultViewer(DataResultViewer dataResultViewer) {
        if (null != customContentViewer) {
            dataResultViewer.setContentViewer(customContentViewer);
        }
        resultViewers.add(dataResultViewer);
        dataResultTabbedPanel.addTab(dataResultViewer.getTitle(), dataResultViewer.getComponent());        
    }

    /**
     * Tears down the component.
     * Use within your outer container (such as a top component) when it goes away to tear
     * down this component and detach its listeners.
     */
    // @@@ This is problematic. It is package private, so it cannot be called by 
    // clients that use the createInstance() factory methods that call open(), 
    // also package private.
    void close() {
        PropertyChangeListener[] pcl = resultPanelListeners.getPropertyChangeListeners();
        for (int i = 0; i < pcl.length; i++) {
            resultPanelListeners.removePropertyChangeListener(pcl[i]);
        }

        // clear all set nodes
        for (DataResultViewer drv : resultViewers) {
            drv.setNode(null);
        }

        if (!this.isMain) {
            for (DataResultViewer drv : resultViewers) {
                drv.clearComponent();
            }
            directoryTablePath.removeAll();
            directoryTablePath = null;
            numberMatchLabel.removeAll();
            numberMatchLabel = null;
            matchLabel.removeAll();
            matchLabel = null;
            setLayout(null);
            resultPanelListeners = null;
            removeAll();
            setVisible(false);
        }
    }

    @Override
    public List<DataResultViewer> getViewers() {
        List<DataResultViewer> viewers = new ArrayList<>();
        for (DataResultViewer viewer : resultViewers) {
            viewers.add(viewer);
        }
        
        return viewers;
    }
        
    /**
     * Sets the number of matches to be displayed in the top right corner.
     */
    // @@@ This appears to be obsolete, given that setNode() handles this.
    public void setNumMatches(int numMatches) {
        numberMatchLabel.setText(Integer.toString(numMatches));
    }
        
    @Override
    public void setNode(Node selectedNode) {                
        // Deferring becoming a listener to the tabbed pane until this point
        // eliminates handling a superfluous stateChanged event during construction.
        if (listeningToTabbedPane == false) {
            dataResultTabbedPanel.addChangeListener(this);        
            listeningToTabbedPane = true;
        }

        // The selected node is cached since it is only pushed to each 
        // result viewer as the corresponding tab is activated.
        currentNode = selectedNode;
        
        if (selectedNode != null) {
            int childrenCount = selectedNode.getChildren().getNodesCount(true);
            numberMatchLabel.setText(Integer.toString(childrenCount));
        }
        numberMatchLabel.setVisible(true);

        // Enable the tabs corresponding to each viewer capable of displaying
        // the selected node.
        resetTabs(selectedNode);
        int currentTab = 0;
        for (DataResultViewer drv : resultViewers) {
            if (drv.isSupported(selectedNode)) {
                dataResultTabbedPanel.setEnabledAt(currentTab, true);
            } 
            else {
                dataResultTabbedPanel.setEnabledAt(currentTab, false);
            }
            ++currentTab;
        }

        // Push the node to the result viewer corresponding to the active tab.
        // Note that the standard viewers will take this opportunity to reset
        // the root context and explored context of the shared ExplorerManager.
        int currentActiveTab = this.dataResultTabbedPanel.getSelectedIndex();
        if (currentActiveTab != -1) {
            DataResultViewer drv = resultViewers.get(currentActiveTab);
            drv.setNode(selectedNode);
        }
    }

    // @@@ Is this concept obsolete - the method does nothing with the selected node.
    public void resetTabs(Node selectedNode) {
        for (DataResultViewer drv : resultViewers) {
            drv.resetComponent();
        }        
    }

    // @@@ Is this concept obsolete?
    public void setSelectedNodes(Node[] selected) {
        for (DataResultViewer drv : resultViewers) {
            drv.setSelectedNodes(selected);
        }
    }

    // @@@ Is this concept obsolete?
    public Node getRootNode() {
        return currentNode;
    }
        
    @Override
    public void stateChanged(ChangeEvent e) {
        // Push the current node to the viewer corresponding to the selected tab
        // in the child tabbed pane. Note that this must be done every time,
        // even when the selected viewer has previously received the node. This
        // is true because the standard Autopsy viewers change the root context
        // and the explored context of the ExplorerManager they share.
        int currentTab = ((JTabbedPane)e.getSource()).getSelectedIndex();
        if (currentTab != -1) {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            try {
                DataResultViewer drv = resultViewers.get(currentTab);
                drv.setNode(currentNode);
            } 
            finally {
                setCursor(null);
            }
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

        directoryTablePath = new javax.swing.JLabel();
        numberMatchLabel = new javax.swing.JLabel();
        matchLabel = new javax.swing.JLabel();
        dataResultTabbedPanel = new javax.swing.JTabbedPane();

        setMinimumSize(new java.awt.Dimension(5, 5));
        setPreferredSize(new java.awt.Dimension(5, 5));

        org.openide.awt.Mnemonics.setLocalizedText(directoryTablePath, org.openide.util.NbBundle.getMessage(DataResultPanel.class, "DataResultPanel.directoryTablePath.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(numberMatchLabel, org.openide.util.NbBundle.getMessage(DataResultPanel.class, "DataResultPanel.numberMatchLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(matchLabel, org.openide.util.NbBundle.getMessage(DataResultPanel.class, "DataResultPanel.matchLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(directoryTablePath)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 518, Short.MAX_VALUE)
                .addComponent(numberMatchLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(matchLabel))
            .addComponent(dataResultTabbedPanel)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(numberMatchLabel)
                        .addComponent(matchLabel))
                    .addComponent(directoryTablePath))
                .addGap(0, 0, 0)
                .addComponent(dataResultTabbedPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 340, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTabbedPane dataResultTabbedPanel;
    private javax.swing.JLabel directoryTablePath;
    private javax.swing.JLabel matchLabel;
    private javax.swing.JLabel numberMatchLabel;
    // End of variables declaration//GEN-END:variables

}
