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
package org.sleuthkit.autopsy.corecomponents;

import java.awt.Cursor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
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
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResult;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.datamodel.NodeSelectionInfo;

/**
 * A Swing JPanel with a JTabbedPane child component. The tabbed pane contains
 * result viewers.
 *
 * The "main" DataResultPanel for the desktop application has a table viewer
 * (DataResultViewerTable) and a thumbnail viewer (DataResultViewerThumbnail),
 * plus zero to many additional DataResultViewers, since the DataResultViewer
 * interface is an extension point.
 *
 * The "main" DataResultPanel resides in the "main" results view
 * (DataResultTopComponent) that is normally docked into the upper right hand
 * side of the main window of the desktop application.
 *
 * The result viewers in the "main panel" are used to view the child nodes of a
 * node selected in the tree view (DirectoryTreeTopComponent) that is normally
 * docked into the left hand side of the main window of the desktop application.
 *
 * Nodes selected in the child results viewers of a DataResultPanel are
 * displayed in a content view (implementation of the DataContent interface)
 * supplied the panel. The default content view is (DataContentTopComponent) is
 * normally docked into the lower right hand side of the main window, underneath
 * the results view. A custom content view may be specified instead.
 */
public class DataResultPanel extends javax.swing.JPanel implements DataResult, ChangeListener, ExplorerManager.Provider {

    private static final long serialVersionUID = 1L;
    private static final int NO_TAB_SELECTED = -1;
    private static final String PLEASE_WAIT_NODE_DISPLAY_NAME = NbBundle.getMessage(DataResultPanel.class, "DataResultPanel.pleasewaitNodeDisplayName");
    private final List<DataResultViewer> resultViewers = new ArrayList<>();
    private boolean isMain;
    private ExplorerManager explorerManager;
    private ExplorerManagerNodeSelectionListener emNodeSelectionListener;
    private Node rootNode;
    private final RootNodeListener rootNodeListener = new RootNodeListener();
    private boolean listeningToTabbedPane;
    private DataContent contentView;

    /**
     * Constructs and opens a DataResultPanel with the given initial data, and
     * the default DataContent.
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
        DataResultPanel resultPanel = new DataResultPanel(title, false);
        createInstanceCommon(title, pathText, rootNode, totalMatches, resultPanel);
        resultPanel.open();
        return resultPanel;
    }

    /**
     * Constructs and opens a DataResultPanel with the given initial data, and a
     * custom DataContent.
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
        DataResultPanel resultPanel = new DataResultPanel(title, customContentView);
        createInstanceCommon(title, pathText, rootNode, totalMatches, resultPanel);
        resultPanel.open();
        return resultPanel;
    }

    /**
     * Constructs a DataResultPanel with the given initial data, and a custom
     * DataContent. The panel is NOT opened; the client of this method must call
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
        DataResultPanel resultPanel = new DataResultPanel(title, customContentView);
        createInstanceCommon(title, pathText, rootNode, totalMatches, resultPanel);
        return resultPanel;
    }

    /**
     * Executes code common to all of the DataSreultPanel factory methods.
     *
     * @param title           The title for the panel.
     * @param pathText        Descriptive text about the source of the nodes
     *                        displayed.
     * @param rootNode        The new root node.
     * @param totalMatches    Cardinality of root node's children
     * @param resultViewPanel A content view to use in place of the default
     *                        content view.
     */
    private static void createInstanceCommon(String title, String pathText, Node rootNode, int totalMatches, DataResultPanel resultViewPanel) {
        resultViewPanel.setTitle(title);
        resultViewPanel.setName(title);
        resultViewPanel.setNumMatches(totalMatches);
        resultViewPanel.setNode(rootNode);
        resultViewPanel.setPath(pathText);
    }

    /**
     * Constructs a DataResultPanel with the default DataContent
     *
     * @param title  The title for the panel.
     * @param isMain True if the DataResultPanel being constructed is the "main"
     *               DataResultPanel.
     */
    DataResultPanel(String title, boolean isMain) {
        this(isMain, Lookup.getDefault().lookup(DataContent.class));
        setTitle(title);
    }

    private DataResultPanel(boolean isMain, DataContent contentView) {
        this.isMain = isMain;
        this.contentView = contentView;
        initComponents();
    }

    /**
     * Constructs a DataResultPanel with the a custom DataContent.
     *
     * @param title             The title for the panel.
     * @param customContentView A content view to use in place of the default
     *                          content view.
     */
    DataResultPanel(String title, DataContent customContentView) {
        this(false, customContentView);
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
     * Adds a result viewer to this panel.
     *
     * @param resultViewer The result viewer.
     */
    public void addResultViewer(DataResultViewer resultViewer) {
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
        return Collections.unmodifiableList(resultViewers);
    }

    /**
     * Sets the content view for this panel. Needs to be called before the first
     * call to open.
     *
     * @param customContentView A content view to use in place of the default
     *                          content view.
     */
    public void setContentViewer(DataContent customContentView) {
        this.contentView = customContentView;
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
        this.resultViewers.forEach((viewer) -> viewer.setSelectedNodes(selectedNodes));
    }

    /**
     * Sets the state of the child result viewers, based on a selected root
     * node.
     *
     * @param selectedNode The selected node.
     */
    private void setupTabs(Node selectedNode) {
        /*
         * Enable or disable the result viewer tabs based on whether or not the
         * corresponding results viewer supports display of the selected node.
         */
        for (int i = 0; i < dataResultTabbedPanel.getTabCount(); i++) {
            if (resultViewers.get(i).isSupported(selectedNode)) {
                dataResultTabbedPanel.setEnabledAt(i, true);
            } else {
                dataResultTabbedPanel.setEnabledAt(i, false);
            }
        }

        /*
         * If the selected node has a child to be selected, default the selected
         * tab to the table result viewer. Otherwise, use the last selected tab,
         * if it is enabled. If not, select the first enabled tab that can be
         * found.
         */
        int tabToSelect = NO_TAB_SELECTED;
        if (selectedNode instanceof TableFilterNode) {
            NodeSelectionInfo selectedChildInfo = ((TableFilterNode) selectedNode).getChildNodeSelectionInfo();
            if (null != selectedChildInfo) {
                for (int i = 0; i < resultViewers.size(); ++i) {
                    if (resultViewers.get(i) instanceof DataResultViewerTable && dataResultTabbedPanel.isEnabledAt(i)) {
                        tabToSelect = i;
                    }
                }
            }
        };
        if (NO_TAB_SELECTED == tabToSelect) {
            tabToSelect = dataResultTabbedPanel.getSelectedIndex();
            if ((NO_TAB_SELECTED == tabToSelect) || (!dataResultTabbedPanel.isEnabledAt(tabToSelect))) {
                for (int i = 0; i < dataResultTabbedPanel.getTabCount(); ++i) {
                    if (dataResultTabbedPanel.isEnabledAt(i)) {
                        tabToSelect = i;
                        break;
                    }
                }
            }
        }

        /*
         * If there is a tab to sele3ct, do so, and push the selected node to
         * the corresponding result viewer.
         */
        if (NO_TAB_SELECTED != tabToSelect) {
            dataResultTabbedPanel.setSelectedIndex(tabToSelect);
            resultViewers.get(tabToSelect).setNode(selectedNode);
        }
    }

    /**
     * Resets the state of the child result viewers, based on a selected root
     * node.
     *
     * @param unusedSelectedNode The selected node.
     */
    public void resetTabs(Node unusedSelectedNode) {
        this.resultViewers.forEach((viewer) -> {
            viewer.resetComponent();
        });
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
        Case openCase;
        try {
            openCase = Case.getOpenCase();
        } catch (NoCurrentCaseException ex) {
            return true;
        }
        return (!this.isMain) || openCase.hasData() == false;
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

        this.resultViewers.forEach((viewer) -> viewer.setNode(null));

        if (!this.isMain) {
            this.resultViewers.forEach(DataResultViewer::clearComponent);
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
    public ExplorerManager getExplorerManager() {
        return explorerManager;
    }

    /**
     * Responds to node selection change events from the explorer manager.
     */
    private class ExplorerManagerNodeSelectionListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            try {
                Case.getOpenCase();
            } catch (NoCurrentCaseException ex) {
                return;
            }

            /*
             * Only interested in node selection events.
             */
            if (evt.getPropertyName().equals(ExplorerManager.PROP_SELECTED_NODES)) {
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                try {
                    if (contentView != null) {
                        Node[] selectedNodes = explorerManager.getSelectedNodes();

                        /*
                         * Pass the selected nodes to all of the result viewers
                         * sharing this explorer manager.
                         */
                        resultViewers.forEach((viewer) -> viewer.setSelectedNodes(selectedNodes));

                        /*
                         * Passing null signals that either multiple nodes are
                         * selected, or no nodes are selected. This is important
                         * to the content view, since content views only work
                         * for a single node..
                         */
                        if (1 == selectedNodes.length) {
                            contentView.setNode(selectedNodes[0]);
                        } else {
                            contentView.setNode(null);
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
                    SwingUtilities.invokeLater(() -> {
                        setupTabs(nme.getNode());
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
