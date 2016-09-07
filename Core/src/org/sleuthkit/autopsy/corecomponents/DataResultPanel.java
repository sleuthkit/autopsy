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
package org.sleuthkit.autopsy.corecomponents;

import java.awt.Cursor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;
import org.openide.nodes.NodeEvent;
import org.openide.nodes.NodeListener;
import org.openide.nodes.NodeMemberEvent;
import org.openide.nodes.NodeReorderEvent;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResult;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.coreutils.Logger;

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

    private ExplorerManager explorerManager;
    private ExplorerManagerNodeSelectionListener emNodeSelectionListener;
    
    private Node rootNode;

    // Different DataResultsViewers
    private final List<UpdateWrapper> viewers = new ArrayList<>();
    //custom content viewer to send selections to, or null if the main one
    private DataContent customContentViewer;
    private boolean isMain;
    private String title;
    private final RootNodeListener rootNodeListener = new RootNodeListener();

    private static final Logger logger = Logger.getLogger(DataResultPanel.class.getName());
    private boolean listeningToTabbedPane = false;
    private static final String DUMMY_NODE_DISPLAY_NAME = NbBundle.getMessage(DataResultPanel.class,
            "DataResultPanel.dummyNodeDisplayName");

    /**
     * Creates new DataResultPanel Default constructor, needed mostly for the
     * palette/UI builder Use overrides or factory methods for more
     * customization.
     */
    private DataResultPanel() {
        this.isMain = true;
        initComponents();

        setName(title);

        this.title = "";
    }

    /**
     * Creates data result panel
     *
     * @param isMain whether it is the main panel associated with the main
     *               window, clients will almost always use false
     * @param title  title string to be displayed
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
     * @param name                unique name of the data result window, also
     *                            used as title
     * @param customContentViewer custom content viewer to send selection events
     *                            to
     */
    DataResultPanel(String title, DataContent customContentViewer) {
        this(false, title);
        setName(title);

        //custom content viewer tc to setup for every result viewer
        this.customContentViewer = customContentViewer;
    }

    /**
     * Factory method to create, customize and open a new custom data result
     * panel.
     *
     * @param title        Title of the result panel
     * @param pathText     Descriptive text about the source of the nodes
     *                     displayed
     * @param givenNode    The new root node
     * @param totalMatches Cardinality of root node's children
     *
     * @return a new DataResultPanel instance representing a custom data result
     *         viewer
     */
    public static DataResultPanel createInstance(String title, String pathText, Node givenNode, int totalMatches) {
        DataResultPanel newDataResult = new DataResultPanel(false, title);

        createInstanceCommon(pathText, givenNode, totalMatches, newDataResult);
        newDataResult.open();
        return newDataResult;
    }

    /**
     * Factory method to create, customize and open a new custom data result
     * panel.
     *
     * @param title        Title of the component window
     * @param pathText     Descriptive text about the source of the nodes
     *                     displayed
     * @param givenNode    The new root node
     * @param totalMatches Cardinality of root node's children
     * @param dataContent  a handle to data content to send selection events to
     *
     * @return a new DataResultPanel instance representing a custom data result
     *         viewer
     */
    public static DataResultPanel createInstance(String title, String pathText, Node givenNode, int totalMatches, DataContent dataContent) {
        DataResultPanel newDataResult = new DataResultPanel(title, dataContent);

        createInstanceCommon(pathText, givenNode, totalMatches, newDataResult);
        newDataResult.open();
        return newDataResult;
    }

    /**
     * Factory method to create, customize and open a new custom data result
     * panel. Does NOT call open(). Client must manually initialize by calling
     * open().
     *
     * @param title        Title of the component window
     * @param pathText     Descriptive text about the source of the nodes
     *                     displayed
     * @param givenNode    The new root node
     * @param totalMatches Cardinality of root node's children
     * @param dataContent  a handle to data content to send selection events to
     *
     * @return a new DataResultPanel instance representing a custom data result
     *         viewer
     */
    public static DataResultPanel createInstanceUninitialized(String title, String pathText, Node givenNode, int totalMatches, DataContent dataContent) {
        DataResultPanel newDataResult = new DataResultPanel(title, dataContent);

        createInstanceCommon(pathText, givenNode, totalMatches, newDataResult);
        return newDataResult;
    }

    /**
     * Common code for factory helper methods
     *
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
     * Sets content viewer to the custom one. Needs to be done before the first
     * call to open()
     *
     * @param customContentViewer
     */
    public void setContentViewer(DataContent customContentViewer) {
        this.customContentViewer = customContentViewer;
    }

    /**
     * Initializes the panel internals and activates it. Call it within your top
     * component when it is opened. Do not use if used one of the factory
     * methods to create and open the component.
     */
    public void open() {
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
            emNodeSelectionListener = new ExplorerManagerNodeSelectionListener();
            explorerManager.addPropertyChangeListener(emNodeSelectionListener);
        }

        // Add all the DataContentViewer to the tabbed pannel.
        // (Only when the it's opened at the first time: tabCount = 0)
        int totalTabs = this.dataResultTabbedPanel.getTabCount();
        if (totalTabs == 0) {
            // @@@ Restore the implementation of DataResultViewerTable and DataResultViewerThumbnail
            // as DataResultViewer service providers when DataResultViewers are updated
            // to better handle the ExplorerManager sharing implemented to support actions that operate on 
            // multiple selected nodes.
            addDataResultViewer(new DataResultViewerTable(this.explorerManager));
            addDataResultViewer(new DataResultViewerThumbnail(this.explorerManager));

            // Find all DataResultViewer service providers and add them to the tabbed pane.
            for (DataResultViewer factory : Lookup.getDefault().lookupAll(DataResultViewer.class)) {
                // @@@ Revist this isMain condition, it may be obsolete. If not, 
                // document the intent of DataResultViewer.createInstance() in the
                // DataResultViewer interface defintion.
                DataResultViewer drv;
                if (isMain) {
                    //for main window, use the instance in the lookup
                    drv = factory;
                } else {
                    //create a new instance of the viewer for non-main window
                    drv = factory.createInstance();
                }
                addDataResultViewer(drv);
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

    private class ExplorerManagerNodeSelectionListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (!Case.isCaseOpen()) {
                // Handle the in-between condition when case is being closed
                // and legacy selection events are pumped.
                return;
            }

            if (evt.getPropertyName().equals(ExplorerManager.PROP_SELECTED_NODES)) {
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                // If a custom DataContent object has not been specified, get the default instance.
                DataContent contentViewer = customContentViewer;
                if (contentViewer == null) {
                    contentViewer = Lookup.getDefault().lookup(DataContent.class);
                }

                try {
                    if (contentViewer != null) {
                        Node[] selectedNodes = explorerManager.getSelectedNodes();
                        for (UpdateWrapper drv : viewers) {
                            drv.setSelectedNodes(selectedNodes);
                        }

                        // Passing null signals that either multiple nodes are selected, or no nodes are selected. 
                        // This is important to the DataContent object, since the content mode (area) of the app is designed 
                        // to show only the content underlying a single Node.                                
                        if (selectedNodes.length == 1) {
                            contentViewer.setNode(selectedNodes[0]);
                        } else {
                            contentViewer.setNode(null);
                        }
                    }
                } finally {
                    setCursor(null);
                }
            }
        }
    }

    private void addDataResultViewer(DataResultViewer dataResultViewer) {
        UpdateWrapper viewerWrapper = new UpdateWrapper(dataResultViewer);
        if (null != this.customContentViewer) {
            viewerWrapper.setContentViewer(this.customContentViewer);
        }
        this.viewers.add(viewerWrapper);
        this.dataResultTabbedPanel.addTab(dataResultViewer.getTitle(), dataResultViewer.getComponent());
    }

    /**
     * Tears down the component. Use within your outer container (such as a top
     * component) when it goes away to tear down this component and detach its
     * listeners.
     */
    void close() {

        if (null != explorerManager && null != emNodeSelectionListener) {
            explorerManager.removePropertyChangeListener(emNodeSelectionListener);
            explorerManager = null;
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
            this.removeAll();
            this.setVisible(false);
        }
    }

    @Override
    public String getPreferredID() {
        return getName();
    }

    @Override
    public void setNode(Node selectedNode) {
        if (this.rootNode != null) {
            this.rootNode.removeNodeListener(rootNodeListener);
        }
        // Deferring becoming a listener to the tabbed pane until this point
        // eliminates handling a superfluous stateChanged event during construction.
        if (listeningToTabbedPane == false) {
            dataResultTabbedPanel.addChangeListener(this);
            listeningToTabbedPane = true;
        }

        this.rootNode = selectedNode;
        if (this.rootNode != null) {
            rootNodeListener.reset();
            this.rootNode.addNodeListener(rootNodeListener);
        }

        resetTabs(selectedNode);
        setupTabs(selectedNode);

        if (selectedNode != null) {
            int childrenCount = selectedNode.getChildren().getNodesCount();
            this.numberMatchLabel.setText(Integer.toString(childrenCount));
        }
        this.numberMatchLabel.setVisible(true);
    }

    private void setupTabs(Node selectedNode) {
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

        // if the current tab is no longer enabled, then find one that is
        boolean hasViewerEnabled = true;
        int currentActiveTab = dataResultTabbedPanel.getSelectedIndex();
        if ((currentActiveTab == -1) || (dataResultTabbedPanel.isEnabledAt(currentActiveTab) == false)) {
            hasViewerEnabled = false;
            for (int i = 0; i < dataResultTabbedPanel.getTabCount(); i++) {
                if (dataResultTabbedPanel.isEnabledAt(i)) {
                    currentActiveTab = i;
                    hasViewerEnabled = true;
                    break;
                }
            }

            if (hasViewerEnabled) {
                dataResultTabbedPanel.setSelectedIndex(currentActiveTab);
            }
        }

        if (hasViewerEnabled) {
            viewers.get(currentActiveTab).setNode(selectedNode);
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

    @Override
    public List<DataResultViewer> getViewers() {
        List<DataResultViewer> ret = new ArrayList<DataResultViewer>();
        for (UpdateWrapper w : viewers) {
            ret.add(w.getViewer());
        }

        return ret;
    }

    public boolean canClose() {
        return (!this.isMain) || !Case.isCaseOpen() || Case.getCurrentCase().hasData() == false; // only allow this window to be closed when there's no case opened or no image in this case
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        JTabbedPane pane = (JTabbedPane) e.getSource();

        // Get and set current selected tab
        int currentTab = pane.getSelectedIndex();
        if (currentTab != -1) {
            UpdateWrapper drv = this.viewers.get(currentTab);
            // @@@ Restore commented out isOutDated() check after DataResultViewers are updated
            // to better handle the ExplorerManager sharing implemented to support actions that operate on 
            // multiple selected nodes.
            //if (drv.isOutdated()) {
            // change the cursor to "waiting cursor" for this operation
            this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            try {
                drv.setNode(rootNode);
            } finally {
                this.setCursor(null);
            }
            //}
        }
    }

    /**
     * why does this take a Node as parameter and then ignore it?
     *
     *
     *
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

        setMinimumSize(new java.awt.Dimension(0, 5));
        setPreferredSize(new java.awt.Dimension(5, 5));

        org.openide.awt.Mnemonics.setLocalizedText(directoryTablePath, org.openide.util.NbBundle.getMessage(DataResultPanel.class, "DataResultPanel.directoryTablePath.text")); // NOI18N
        directoryTablePath.setMinimumSize(new java.awt.Dimension(5, 14));

        org.openide.awt.Mnemonics.setLocalizedText(numberMatchLabel, org.openide.util.NbBundle.getMessage(DataResultPanel.class, "DataResultPanel.numberMatchLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(matchLabel, org.openide.util.NbBundle.getMessage(DataResultPanel.class, "DataResultPanel.matchLabel.text")); // NOI18N

        dataResultTabbedPanel.setMinimumSize(new java.awt.Dimension(0, 5));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(directoryTablePath, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(numberMatchLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(matchLabel))
            .addComponent(dataResultTabbedPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(numberMatchLabel)
                        .addComponent(matchLabel))
                    .addComponent(directoryTablePath, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, 0)
                .addComponent(dataResultTabbedPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
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

        DataResultViewer getViewer() {
            return wrapped;
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
     *
     * @param numMatches
     */
    public void setNumMatches(Integer numMatches) {
        if (this.numberMatchLabel != null) {
            this.numberMatchLabel.setText(Integer.toString(numMatches));
        }
    }

    private class RootNodeListener implements NodeListener {

        private volatile boolean waitingForData = true;

        public void reset() {
            waitingForData = true;
        }

        @Override
        public void childrenAdded(final NodeMemberEvent nme) {
            Node[] delta = nme.getDelta();
            updateMatches();

            /*
             * There is a known issue in this code whereby we will only call
             * setupTabs() once even though childrenAdded could be called
             * multiple times. That means that each panel may not have access to
             * all of the children when they decide if they support the content
             */
            if (waitingForData && containsReal(delta)) {
                waitingForData = false;
                if (SwingUtilities.isEventDispatchThread()) {
                    setupTabs(nme.getNode());
                } else {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            setupTabs(nme.getNode());
                        }
                    });
                }
            }
        }

        private boolean containsReal(Node[] delta) {
            for (Node n : delta) {
                if (!n.getDisplayName().equals(DUMMY_NODE_DISPLAY_NAME)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Updates the Number of Matches label on the DataResultPanel.
         *
         */
        private void updateMatches() {
            if (rootNode != null && rootNode.getChildren() != null) {
                setNumMatches(rootNode.getChildren().getNodesCount());
            }
        }

        @Override
        public void childrenRemoved(NodeMemberEvent nme) {
            updateMatches();
        }

        @Override
        public void childrenReordered(NodeReorderEvent nre) {
        }

        @Override
        public void nodeDestroyed(NodeEvent ne) {
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
        }
    }
}
