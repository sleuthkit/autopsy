/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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

/**
 * A Swing JPanel with a JTabbedPane child component that contains result
 * viewers (implementations of the DataResultViewer interface). The "main"
 * DataResultPanel for the desktop application has a table viewer
 * (DataResultViewerTable) and a thumbnail viewer (DataResultViewerThumbnail).
 * The "main" panel and zero to many additional DataResultPanel instances are
 * presented as tabs in the results view, the top component
 * (DataResultTopComponent) normally docked into the upper right hand side of
 * the main window of the desktop application. The result viewers in the "main
 * panel" are used to view the child nodes of a node selected in the tree view
 * (DirectoryTreeTopComponent) that is normally docked into the left hand side
 * of the main window.
 *
 * Nodes selected in the results view are displayed in a content view
 * (implementation of the DataContent interface). The default content view is
 * the DataContentTopComponent, normally docked into the lower right hand side
 * of the main window. A custom content view may be specified instead.
 */
public class DataResultPanel extends javax.swing.JPanel implements DataResult, ChangeListener {

    private static final long serialVersionUID = 1L;
    private static final String PLEASE_WAIT_NODE_DISPLAY_NAME = NbBundle.getMessage(DataResultPanel.class, "DataResultPanel.pleasewaitNodeDisplayName");
    private final List<DataResultViewer> resultViewers = new ArrayList<>();
    private ExplorerManager explorerManager;
    private ExplorerManagerNodeSelectionListener emNodeSelectionListener;
    private Node rootNode;
    private final RootNodeListener rootNodeListener = new RootNodeListener();
    private DataContent customContentView;
    private boolean isMain;
    private String title;
    private boolean listeningToTabbedPane;

    /**
     * Constructs and opens a Swing JPanel with a JTabbedPane child component
     * that contains result viewers (implementations of the DataResultViewer
     * interface).
     *
     * @param title        The title for the panel.
     * @param pathText     Descriptive text about the source of the nodes
     *                     displayed.
     * @param rootNode     The new root node.
     * @param totalMatches Cardinality of root node's children
     *
     * @return A DataResultPanel instance.
     */
    public static DataResultPanel createInstance(String title, String pathText, Node rootNode, int totalMatches) {
        DataResultPanel newDataResult = new DataResultPanel(false, title);
        createInstanceCommon(pathText, rootNode, totalMatches, newDataResult);
        newDataResult.open();
        return newDataResult;
    }

    /**
     * Constructs and opens a Swing JPanel with a JTabbedPane child component
     * that contains result viewers (implementations of the DataResultViewer
     * interface).
     *
     * @param title             The title for the panel.
     * @param pathText          Descriptive text about the source of the nodes
     *                          displayed.
     * @param rootNode          The new root node.
     * @param totalMatches      Cardinality of root node's children
     * @param customContentView A content view to use in place of the default
     *                          content view.
     *
     * @return A DataResultPanel instance.
     */
    public static DataResultPanel createInstance(String title, String pathText, Node rootNode, int totalMatches, DataContent customContentView) {
        DataResultPanel newDataResult = new DataResultPanel(title, customContentView);
        createInstanceCommon(pathText, rootNode, totalMatches, newDataResult);
        newDataResult.open();
        return newDataResult;
    }

    /**
     * Constructs a Swing JPanel with a JTabbedPane child component that
     * contains result viewers (implementations of the DataResultViewer
     * interface). The panel is NOT opened; the client of this method must call
     * open on the panel that is returned.
     *
     * @param title             The title for the panel.
     * @param pathText          Descriptive text about the source of the nodes
     *                          displayed.
     * @param rootNode          The new root node.
     * @param totalMatches      Cardinality of root node's children
     * @param customContentView A content view to use in place of the default
     *                          content view.
     *
     * @return A DataResultPanel instance.
     */
    public static DataResultPanel createInstanceUninitialized(String title, String pathText, Node rootNode, int totalMatches, DataContent customContentView) {
        DataResultPanel newDataResult = new DataResultPanel(title, customContentView);
        createInstanceCommon(pathText, rootNode, totalMatches, newDataResult);
        return newDataResult;
    }

    /**
     * Executes code common to all of the DataSreultPanel factory methods.
     *
     * @param pathText          Descriptive text about the source of the nodes
     *                          displayed.
     * @param rootNode          The new root node.
     * @param totalMatches      Cardinality of root node's children
     * @param customContentView A content view to use in place of the default
     *                          content view.
     */
    private static void createInstanceCommon(String pathText, Node rootNode, int totalMatches, DataResultPanel customContentView) {
        customContentView.numberMatchLabel.setText(Integer.toString(totalMatches));
        customContentView.setNode(rootNode);
        customContentView.setPath(pathText);
    }

    /**
     * Constructs a Swing JPanel with a JTabbedPane child component that
     * contains result viewers (implementations of the DataResultViewer
     * interface).
     */
    private DataResultPanel() {
        this.isMain = true;
        initComponents();
        this.title = "";
        setName(title);
    }

    /**
     * Constructs a Swing JPanel with a JTabbedPane child component that
     * contains result viewers (implementations of the DataResultViewer
     * interface).
     *
     * @param isMain True if the DataResultPanel being constructed is the "main"
     *               DataResultPanel.
     * @param title  The title for the panel.
     */
    DataResultPanel(boolean isMain, String title) {
        this();
        this.isMain = isMain;
        this.title = title;
        setName(title);
    }

    /**
     * Constructs a Swing JPanel with a JTabbedPane child component that
     * contains result viewers (implementations of the DataResultViewer
     * interface).
     *
     * @param title             The title for the panel.
     * @param customContentView A content view to use in place of the default
     *                          content view.
     */
    DataResultPanel(String title, DataContent customContentView) {
        this(false, title);
        setName(title);
        this.customContentView = customContentView;
    }

    /**
     * Gets the preferred identifier for this panel in the window system.
     *
     * @return The preferred identifier.
     */
    @Override
    public String getPreferredID() {
        return getName();
    }

    /**
     * Gets whether or not this panel is the "main" panel used to view the child
     * nodes of a node selected in the tree view (DirectoryTreeTopComponent)
     * that is normally docked into the left hand side of the main window.
     *
     * @return True or false.
     */
    @Override
    public boolean isMain() {
        return this.isMain;
    }

    /**
     * Sets the title of this panel.
     *
     * @param title The title.
     */
    @Override
    public void setTitle(String title) {
        setName(title);
    }

    /**
     * Sets the descriptive text about the source of the nodes displayed in this
     * panel.
     *
     * @param pathText The text to display.
     */
    @Override
    public void setPath(String pathText) {
        this.directoryTablePath.setText(pathText);
    }

    /**
     * Sets the content view for this panel. Needs to be called before the first
     * call to open.
     *
     * @param customContentView A content view to use in place of the default
     *                          content view.
     */
    public void setContentViewer(DataContent customContentView) {
        this.customContentView = customContentView;
    }

    /**
     * Initializes this panel. Intended to be called by a parent top component
     * when the top component is opened.
     */
    public void open() {
        if (null == explorerManager) {
            /*
             * Get an explorer manager to pass to the child result viewers. If
             * the application components are put together as expected, this
             * will be an explorer manager owned by a parent top component, and
             * placed by the top component in the look up that is proxied as the
             * action global context when the top component has focus. The
             * sharing of this explorer manager enables the same child node
             * selections to be made in all of the result viewers.
             */
            explorerManager = ExplorerManager.find(this);
            emNodeSelectionListener = new ExplorerManagerNodeSelectionListener();
            explorerManager.addPropertyChangeListener(emNodeSelectionListener);
        }

        /*
         * Load the child result viewers into the tabbed pane.
         */
        if (0 == dataResultTabbedPanel.getTabCount()) {
            /*
             * TODO (JIRA-2658): Fix the DataResultViewer extension point. When
             * this is done, restore the implementation of DataResultViewerTable
             * and DataREsultViewerThumbnail as DataResultViewer service
             * providers.
             */
            addResultViewer(new DataResultViewerTable(this.explorerManager));
            addResultViewer(new DataResultViewerThumbnail(this.explorerManager));
            for (DataResultViewer factory : Lookup.getDefault().lookupAll(DataResultViewer.class)) {
                DataResultViewer resultViewer;
                if (isMain) {
                    resultViewer = factory;
                } else {
                    resultViewer = factory.createInstance();
                }
                addResultViewer(resultViewer);
            }
        }

        if (isMain && null == rootNode) {
            setNode(rootNode);
        }

        this.setVisible(true);
    }

    /**
     * Adds a result viewer to this panel.
     *
     * @param resultViewer The result viewer.
     */
    private void addResultViewer(DataResultViewer resultViewer) {
        if (null != customContentView) {
            resultViewer.setContentViewer(customContentView);
        }
        resultViewers.add(resultViewer);
        dataResultTabbedPanel.addTab(resultViewer.getTitle(), resultViewer.getComponent());
    }

    /**
     * Gets the result viewers for this panel.
     *
     * @return A list of result viewers.
     */
    @Override
    public List<DataResultViewer> getViewers() {
        List<DataResultViewer> viewers = new ArrayList<>();
        for (DataResultViewer viewer : resultViewers) {
            viewers.add(viewer);
        }
        return viewers;
    }

    /**
     * Sets the root node for this panel. The child nodes of the root node will
     * be displayed in the result viewers. For the "main" panel, the root node
     * is the currently selected node in the tree view docked into the left side
     * of the main application window.
     *
     * @param rootNode The root node for this panel.
     */
    @Override
    public void setNode(Node rootNode) {
        if (this.rootNode != null) {
            this.rootNode.removeNodeListener(rootNodeListener);
        }

        /*
         * Deferring becoming a listener to the tabbed pane until this point
         * eliminates handling a superfluous stateChanged event during
         * construction.
         */
        if (listeningToTabbedPane == false) {
            dataResultTabbedPanel.addChangeListener(this);
            listeningToTabbedPane = true;
        }

        this.rootNode = rootNode;
        if (this.rootNode != null) {
            rootNodeListener.reset();
            this.rootNode.addNodeListener(rootNodeListener);
        }

        resetTabs(this.rootNode);
        setupTabs(this.rootNode);

        if (null != this.rootNode) {
            int childrenCount = this.rootNode.getChildren().getNodesCount();
            this.numberMatchLabel.setText(Integer.toString(childrenCount));
        }
        this.numberMatchLabel.setVisible(true);
    }

    /**
     * Gets the root node of this panel. For the "main" panel, the root node is
     * the currently selected node in the tree view docked into the left side of
     * the main application window.
     *
     * @return The root node.
     */
    public Node getRootNode() {
        return rootNode;
    }

    /**
     * Set number of child nodes displayed for the current root node.
     *
     * @param numberOfChildNodes
     */
    public void setNumMatches(Integer numberOfChildNodes) {
        if (this.numberMatchLabel != null) {
            this.numberMatchLabel.setText(Integer.toString(numberOfChildNodes));
        }
    }

    /**
     * Sets the children of the root node that should be currently selected in
     * this panel's result viewers.
     *
     * @param selectedNodes The nodes to be selected.
     */
    public void setSelectedNodes(Node[] selectedNodes) {
        for (DataResultViewer viewer : this.resultViewers) {
            viewer.setSelectedNodes(selectedNodes);
        }
    }

    /**
     * Sets the state of the child result viewers, based on a selected root
     * node.
     *
     * @param selectedNode The selected node.
     */
    private void setupTabs(Node selectedNode) {
        /*
         * Enable or disable the child tabs based on whether or not the
         * corresponding results viewer supports display of the selected node.
         */
        int tabIndex = 0;
        for (DataResultViewer viewer : resultViewers) {
            if (viewer.isSupported(selectedNode)) {
                dataResultTabbedPanel.setEnabledAt(tabIndex, true);
            } else {
                dataResultTabbedPanel.setEnabledAt(tabIndex, false);
            }
            ++tabIndex;
        }

        /*
         * If the current tab is not enabled for the selected node, try to
         * select a tab that is enabled.
         */
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

        /*
         * Push the node to the selected results viewer.
         */
        if (hasViewerEnabled) {
            resultViewers.get(currentActiveTab).setNode(selectedNode);
        }

        /*
         * Now that the selected node has been pushed to the selected results
         * viewer and it has had an opportunity to act on the child selection
         * info of the node, if any, clear the child selection info.
         */
        ((TableFilterNode) selectedNode).setChildNodeSelectionInfo(null);
    }

    /**
     * Resets the state of the child result viewers, based on a selected root
     * node.
     *
     * @param unusedSelectedNode The selected node.
     */
    public void resetTabs(Node unusedSelectedNode) {
        for (DataResultViewer viewer : this.resultViewers) {
            viewer.resetComponent();
        }
    }

    /**
     * Responds to a tab selection changed event by setting the root node of the
     * corresponding result viewer.
     *
     * @param event The change event.
     */
    @Override
    public void stateChanged(ChangeEvent event) {
        JTabbedPane pane = (JTabbedPane) event.getSource();
        int currentTab = pane.getSelectedIndex();
        if (-1 != currentTab) {
            DataResultViewer currentViewer = this.resultViewers.get(currentTab);
            this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            try {
                currentViewer.setNode(rootNode);
            } finally {
                this.setCursor(null);
            }
        }
    }

    /**
     * Indicates whether or not this panel can be closed at the time of the
     * call.
     *
     * @return True or false.
     */
    public boolean canClose() {
        /*
         * If this is the "main" panel, only allow it to be closed when no case
         * is open or no there are no data sources in the current case.
         */
        return (!this.isMain) || !Case.isCaseOpen() || Case.getCurrentCase().hasData() == false;
    }

    /**
     * Closes down the component. Intended to be called by the parent top
     * component when it is closed.
     */
    void close() {
        if (null != explorerManager && null != emNodeSelectionListener) {
            explorerManager.removePropertyChangeListener(emNodeSelectionListener);
            explorerManager = null;
        }

        for (DataResultViewer viewer : this.resultViewers) {
            viewer.setNode(null);
        }

        if (!this.isMain) {
            for (DataResultViewer viewer : this.resultViewers) {
                viewer.clearComponent();
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

    /**
     * Responds to node selection change events from the explorer manager.
     */
    private class ExplorerManagerNodeSelectionListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            try {
                Case.getCurrentCase();
            } catch (IllegalStateException ex) {
                return;
            }

            /*
             * Only interested in node selection events.
             */
            if (evt.getPropertyName().equals(ExplorerManager.PROP_SELECTED_NODES)) {
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                /*
                 * Use either the custom content view or the default view, if no
                 * custom view has been set. The default content view is the
                 * DataContentTopComponent docked into the lower right hand side
                 * of the main window of the application.
                 */
                DataContent contentViewer;
                if (null != customContentView) {
                    contentViewer = customContentView;
                } else {
                    contentViewer = Lookup.getDefault().lookup(DataContent.class);
                }

                try {
                    if (contentViewer != null) {
                        Node[] selectedNodes = explorerManager.getSelectedNodes();

                        /*
                         * Pass the selected nodes to all of the result viewers
                         * sharing this explorer manager.
                         */
                        resultViewers.forEach((viewer) -> {
                            viewer.setSelectedNodes(selectedNodes);
                        });

                        /*
                         * Passing null signals that either multiple nodes are
                         * selected, or no nodes are selected. This is important
                         * to the content view, since content views only work
                         * for a single node..
                         */
                        if (1 == selectedNodes.length) {
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

    /**
     * Responds to changes in the root node due to asynchronous child node
     * creation.
     */
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
                if (!n.getDisplayName().equals(PLEASE_WAIT_NODE_DISPLAY_NAME)) {
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

}
