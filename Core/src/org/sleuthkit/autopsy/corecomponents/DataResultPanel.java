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
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResult;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;

/**
 * Data result panel component with its viewer tabs.
 *
 * The component is a generic JPanel and it can be reused in other swing
 * components or in a TopComponent.
 * 
 * Use the static factory methods to instantiate and customize the component.
 * One option is to link a custom data content viewer to link to this viewer.
 * 
 */
public class DataResultPanel extends javax.swing.JPanel implements DataResult, ChangeListener {

    private Node rootNode;
    private PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    
    // Different DataResultsViewers
    private final List<UpdateWrapper> viewers = new ArrayList<UpdateWrapper>();
    //custom content viewer to send selections to, or null if the main one
    private DataContent customContentViewer;
    private boolean isMain;
    private String title;

    /**
     * Creates new DataResultPanel
     * Default constructor, needed mostly  for the palette/UI builder
     * Use overrides or factory methods for more customization.
     */
    public DataResultPanel() {
        initComponents();
        
        setName(title);

        this.isMain = false;
        this.title = "";

        this.dataResultTabbedPanel.addChangeListener(this);
    }

    /**
     * Creates data result panel
     * 
     * @param isMain whether it is the main panel associated with the main window, 
     * clients will almost always use false
     * @param title title string to be displayed
     */
    DataResultPanel(boolean isMain, String title) {
        this();
        
        setName(title);

        this.isMain = isMain;
        this.title = title;

    }

    /**
     * Create a new, custom data result panel, in addition to the application
     * main one and links with a custom data content panel.
     *
     * @param name unique name of the data result window, also used as title
     * @param customContentViewer custom content viewer to send selection events
     * to
     */
    DataResultPanel(String title, DataContent customContentViewer) {
        this(false, title);
        
        setName(title);

        //custom content viewer tc to setup for every result viewer
        this.customContentViewer = customContentViewer; 
    }
    
    public void setContentViewer(DataContent contentViewer) {
        this.customContentViewer = contentViewer;
    }

    /**
     * Factory method to create, customize and open a new custom data result panel.
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
     * Factory method to create, customize and open a new custom data result panel.
     *
     * @param title Title of the component window
     * @param pathText Descriptive text about the source of the nodes displayed
     * @param givenNode The new root node
     * @param totalMatches Cardinality of root node's children
     * @param dataContent a handle to data content to send selection events to
     * @return a new DataResultPanel instance representing a custom data result viewer
     */
    public static DataResultPanel createInstance(String title, String pathText, Node givenNode, int totalMatches, DataContent dataContent) {
        DataResultPanel newDataResult = new DataResultPanel(title, dataContent);

        createInstanceCommon(pathText, givenNode, totalMatches, newDataResult);
        newDataResult.open();
        return newDataResult;
    }

    /**
     * Common code for factory helper methods
     * @param pathText
     * @param givenNode
     * @param totalMatches
     * @param newDataResult 
     */
    private static void createInstanceCommon(String pathText, Node givenNode, int totalMatches, DataResultPanel newDataResult) {
        newDataResult.numberMatchLabel.setText(Integer.toString(totalMatches));

        // set the tree table view
        newDataResult.setNode(givenNode);
        newDataResult.setPath(pathText);
    }

    /**
     * Initializes the panel internals and activates it.
     * Call it within your top component when it is opened.
     * Do not use if used one of the factory methods to create and open the component.
     */
    public void open() {
        // Add all the DataContentViewer to the tabbed pannel.
        // (Only when the it's opened at the first time: tabCount = 0)
        int totalTabs = this.dataResultTabbedPanel.getTabCount();
        if (totalTabs == 0) {
            // find all dataContentViewer and add them to the tabbed pane
            for (DataResultViewer factory : Lookup.getDefault().lookupAll(DataResultViewer.class)) {
                DataResultViewer drv = factory.getInstance();
                UpdateWrapper resultViewer = new UpdateWrapper(drv);
                if (customContentViewer != null) {
                    //set custom content viewer to respond to events from this result viewer
                    resultViewer.setContentViewer(customContentViewer);
                }
                this.viewers.add(resultViewer);
                this.dataResultTabbedPanel.addTab(drv.getTitle(), drv.getComponent());

            }
        }

        if (isMain) {
            // if no node selected on DataExplorer, clear the field
            if (rootNode == null) {
                setNode(rootNode);
            }
        }


        this.setVisible(true);


    }

    /**
     * Tears down the component.
     * Use within your outer container (such as a top component) when it goes away to tear
     * down this component and detach its listeners.
     */
    void close() {
        // try to remove any references to this class
        PropertyChangeListener[] pcl = pcs.getPropertyChangeListeners();
        for (int i = 0; i < pcl.length; i++) {
            pcs.removePropertyChangeListener(pcl[i]);
        }

        // clear all set nodes
        for (UpdateWrapper drv : this.viewers) {
            drv.setNode(null);
        }

        if (!this.isMain) {
            for (UpdateWrapper drv : this.viewers) {
                drv.clearComponent();
            }
            this.directoryTablePath.removeAll();
            this.directoryTablePath = null;
            this.numberMatchLabel.removeAll();
            this.numberMatchLabel = null;
            this.matchLabel.removeAll();
            this.matchLabel = null;
            this.setLayout(null);
            this.pcs = null;
            this.removeAll();
            this.setVisible(false);
        }
        
        

    }


    @Override
    public synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }

    @Override
    public synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.removePropertyChangeListener(listener);
    }

    @Override
    public String getPreferredID() {
        return getName();
    }

    @Override
    public void setNode(Node selectedNode) {
        this.rootNode = selectedNode;
        if (selectedNode != null) {
            int childrenCount = selectedNode.getChildren().getNodesCount(true);
            this.numberMatchLabel.setText(Integer.toString(childrenCount));
        }

        this.numberMatchLabel.setVisible(true);
        this.numberMatchLabel.setVisible(true);

        resetTabs(selectedNode);

        //update/disable tabs based on if supported for this node
        int drvC = 0;
        for (UpdateWrapper drv : viewers) {

            if (drv.isSupported(selectedNode)) {
                dataResultTabbedPanel.setEnabledAt(drvC, true);
            } else {
                dataResultTabbedPanel.setEnabledAt(drvC, false);
            }
            ++drvC;
        }

        // set the display on the current active tab
        int currentActiveTab = this.dataResultTabbedPanel.getSelectedIndex();
        if (currentActiveTab != -1) {
            UpdateWrapper drv = viewers.get(currentActiveTab);
            drv.setNode(selectedNode);
        }
    }

    @Override
    public void setTitle(String title) {
        setName(title);
        
    }

    @Override
    public void setPath(String pathText) {
        this.directoryTablePath.setText(pathText);
    }

    @Override
    public boolean isMain() {
        return this.isMain;
    }

    public boolean canClose() {
        return (!this.isMain) || !Case.existsCurrentCase() || Case.getCurrentCase().getRootObjectsCount() == 0; // only allow this window to be closed when there's no case opened or no image in this case
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        JTabbedPane pane = (JTabbedPane) e.getSource();

        // Get and set current selected tab
        int currentTab = pane.getSelectedIndex();
        if (currentTab != -1) {
            UpdateWrapper drv = this.viewers.get(currentTab);
            if (drv.isOutdated()) {
                // change the cursor to "waiting cursor" for this operation
                this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                try {
                    drv.setNode(rootNode);
                } finally {
                    this.setCursor(null);
                }
            }
        }
    }

    /**
     * Resets the tabs based on the selected Node. If the selected node is null
     * or not supported, disable that tab as well.
     *
     * @param selectedNode the selected content Node
     */
    public void resetTabs(Node selectedNode) {

        for (UpdateWrapper drv : this.viewers) {
            drv.resetComponent();
        }
    }

    public void setSelectedNodes(Node[] selected) {
        for (UpdateWrapper drv : this.viewers) {
            drv.setSelectedNodes(selected);
        }
    }

    public Node getRootNode() {
        return this.rootNode;
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

    private static class UpdateWrapper {

        private DataResultViewer wrapped;
        private boolean outdated;

        UpdateWrapper(DataResultViewer wrapped) {
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

        void clearComponent() {
            this.wrapped.clearComponent();
            this.outdated = true;
        }

        boolean isOutdated() {
            return this.outdated;
        }

        void setSelectedNodes(Node[] selected) {
            this.wrapped.setSelectedNodes(selected);
        }

        boolean isSupported(Node selectedNode) {
            return this.wrapped.isSupported(selectedNode);
        }

        void setContentViewer(DataContent contentViewer) {
            this.wrapped.setContentViewer(contentViewer);
        }
    }

    /**
     * Set number of matches to be displayed in the top right
     * @param numMatches
     */
    public void setNumMatches(int numMatches) {
        this.numberMatchLabel.setText(Integer.toString(numMatches));
    }
}
