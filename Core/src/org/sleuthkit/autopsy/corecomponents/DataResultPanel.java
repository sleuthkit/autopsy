/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2018 Basis Technology Corp.
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
import java.util.Collection;
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
 * A result view panel is a JPanel with a JTabbedPane child component that
 * contains a collection of result viewers and implements the DataResult
 * interface. The result viewers in a result view panel are either supplied
 * during construction of the panel or are obtained from the result viewer
 * extension point (DataResultViewer service providers).
 *
 * A result view panel provides an implementation of the setNode API of the the
 * DataResult interface that pushes a given NetBeans Node into its child result
 * viewers via the DataResultViewer.setNode API. The result viewers are
 * responsible for providing a view of the application data represented by the
 * node. A typical result viewer is a JPanel that displays the child nodes of
 * the given node using a NetBeans explorer view child component.
 *
 * All result view panels should be child components of top components that are
 * explorer manager providers. The parent top component is expected to expose a
 * lookup maintained by its explorer manager to the actions global context. The
 * child result view panel will then find the parent top component's explorer
 * manager at runtime, so that it can act as an explorer manager provider for
 * its child result viewers. This connects the nodes displayed in the result
 * viewers to the actions global context.
 *
 * Result view panels can be constructed so that they push single node
 * selections in the child result viewers to a content view (implements
 * DataContent). The content view could be the "main" content view
 * (DataContentTopComponent) that is normally docked into the lower right hand
 * side of the main application window, or it could be a custom content view.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class DataResultPanel extends javax.swing.JPanel implements DataResult, ChangeListener, ExplorerManager.Provider {

    private static final long serialVersionUID = 1L;
    private static final int NO_TAB_SELECTED = -1;
    private static final String PLEASE_WAIT_NODE_DISPLAY_NAME = NbBundle.getMessage(DataResultPanel.class, "DataResultPanel.pleasewaitNodeDisplayName");
    private final boolean isMain;
    private final List<DataResultViewer> resultViewers;
    private final ExplorerManagerListener explorerManagerListener;
    private final RootNodeListener rootNodeListener;
    private DataContent contentView;
    private ExplorerManager explorerManager;
    private Node currentRootNode;
    private boolean listeningToTabbedPane;

    /**
     * Creates and opens a Swing JPanel with a JTabbedPane child component that
     * contains instances of the result viewers (DataResultViewer) provided by
     * the result viewer extension point (service providers that implement
     * DataResultViewer). The result view panel will push single node selections
     * from its child result viewers to the "main" content view that is normally
     * docked into the lower right hand side of the main application window.
     *
     * @param title           The title for the result view panel.
     * @param description     Descriptive text about the source of the nodes
     *                        displayed.
     * @param currentRootNode The current root (parent) node for the nodes
     *                        displayed. May be changed by calling setNode.
     * @param childNodeCount  The cardinality of the root node's children.
     *
     * @return A result view panel.
     */
    public static DataResultPanel createInstance(String title, String description, Node currentRootNode, int childNodeCount) {
        DataResultPanel resultPanel = new DataResultPanel(title, false, Collections.emptyList(), DataContentTopComponent.findInstance());
        createInstanceCommon(title, description, currentRootNode, childNodeCount, resultPanel);
        resultPanel.open();
        return resultPanel;
    }

    /**
     * Creates and opens a Swing JPanel with a JTabbedPane child component that
     * contains a given collection of result viewers (DataResultViewer) instead
     * of the result viewers provided by the results viewer extension point. The
     * result view panel will push single node selections from its child result
     * viewers to the "main" content view that is normally docked into the lower
     * right hand side of the main application window.
     *
     * @param title           The title for the result view panel.
     * @param description     Descriptive text about the source of the nodes
     *                        displayed.
     * @param currentRootNode The current root (parent) node for the nodes
     *                        displayed. May be changed by calling setNode.
     * @param childNodeCount  The cardinality of the root node's children.
     * @param viewers         A collection of result viewers to use instead of
     *                        the result viewers provided by the results viewer
     *                        extension point.
     *
     * @return A result view panel.
     */
    public static DataResultPanel createInstance(String title, String description, Node currentRootNode, int childNodeCount, Collection<DataResultViewer> viewers) {
        DataResultPanel resultPanel = new DataResultPanel(title, false, viewers, DataContentTopComponent.findInstance());
        createInstanceCommon(title, description, currentRootNode, childNodeCount, resultPanel);
        resultPanel.open();
        return resultPanel;
    }

    /**
     * Creates and opens a Swing JPanel with a JTabbedPane child component that
     * contains instances of the result viewers (DataResultViewer) provided by
     * the result viewer extension point (service providers that implement
     * DataResultViewer). The result view panel will push single node selections
     * from its child result viewers to the supplied content view, which can be
     * null if a content view is not needed.
     *
     * @param title             The title for the result view panel.
     * @param description       Descriptive text about the source of the nodes
     *                          displayed.
     * @param currentRootNode   The current root (parent) node for the nodes
     *                          displayed. May be changed by calling setNode.
     * @param childNodeCount    The cardinality of the root node's children.
     * @param customContentView A custom content view to use instead of the
     *                          "main" content view that is normally docked into
     *                          the lower right hand side of the main
     *                          application window. May be null, if no content
     *                          view is needed.
     *
     * @return A result view panel.
     */
    public static DataResultPanel createInstance(String title, String description, Node currentRootNode, int childNodeCount, DataContent customContentView) {
        DataResultPanel resultPanel = new DataResultPanel(title, false, Collections.emptyList(), customContentView);
        createInstanceCommon(title, description, currentRootNode, childNodeCount, resultPanel);
        resultPanel.open();
        return resultPanel;
    }

    /**
     * Creates, but does not open, a Swing JPanel with a JTabbedPane child
     * component that contains instances of the result viewers
     * (DataResultViewer) provided by the result viewer extension point (service
     * providers that implement DataResultViewer). The result view panel will
     * push single node selections from its child result viewers to the supplied
     * custom content view.
     *
     * @param title             The title for the result view panel.
     * @param description       Descriptive text about the source of the nodes
     *                          displayed.
     * @param currentRootNode   The current root (parent) node for the nodes
     *                          displayed. May be changed by calling setNode.
     * @param childNodeCount    The cardinality of the root node's children.
     * @param customContentView A content view to use in place of the default
     *                          content view.
     *
     * @return A result view panel.
     */
    public static DataResultPanel createInstanceUninitialized(String title, String description, Node currentRootNode, int childNodeCount, DataContent customContentView) {
        DataResultPanel resultPanel = new DataResultPanel(title, false, Collections.emptyList(), customContentView);
        createInstanceCommon(title, description, currentRootNode, childNodeCount, resultPanel);
        return resultPanel;
    }

    /**
     * Executes code common to all of the result view panel factory methods.
     *
     * @param title           The title for the result view panel.
     * @param description     Descriptive text about the source of the nodes
     *                        displayed.
     * @param currentRootNode The current root (parent) node for the nodes
     *                        displayed. May be changed by calling setNode.
     * @param childNodeCount  The cardinality of the root node's children.
     * @param resultViewPanel A new results view panel.
     */
    private static void createInstanceCommon(String title, String description, Node currentRootNode, int childNodeCount, DataResultPanel resultViewPanel) {
        resultViewPanel.setTitle(title);
        resultViewPanel.setName(title);
        resultViewPanel.setNumberOfChildNodes(childNodeCount);
        resultViewPanel.setNode(currentRootNode);
        resultViewPanel.setPath(description);
    }

    /**
     * Constructs a Swing JPanel with a JTabbedPane child component that
     * contains a collection of result viewers that is either supplied or
     * provided by the result viewer extension point.
     *
     * @param title       The title of the result view panel.
     * @param isMain      Whether or not the result view panel is the "main"
     *                    instance of the panel that resides in the "main"
     *                    results view (DataResultTopComponent) that is normally
     *                    docked into the upper right hand side of the main
     *                    application window.
     * @param viewers     A collection of result viewers to use instead of the
     *                    result viewers provided by the results viewer
     *                    extension point, may be empty.
     * @param contentView A content view to into which to push single node
     *                    selections in the child result viewers, may be null.
     */
    DataResultPanel(String title, boolean isMain, Collection<DataResultViewer> viewers, DataContent contentView) {
        this.setTitle(title);
        this.isMain = isMain;
        this.contentView = contentView;
        this.resultViewers = new ArrayList<>(viewers);
        this.explorerManagerListener = new ExplorerManagerListener();
        this.rootNodeListener = new RootNodeListener();
        initComponents();
    }

    /**
     * Gets the preferred identifier for this result view panel in the window
     * system.
     *
     * @return The preferred identifier.
     */
    @Override
    public String getPreferredID() {
        return getName();
    }

    /**
     * Sets the title of this result view panel.
     *
     * @param title The title.
     */
    @Override
    public void setTitle(String title) {
        setName(title);
    }

    /**
     * Sets the descriptive text about the source of the nodes displayed in this
     * result view panel.
     *
     * @param description The text to display.
     */
    @Override
    public void setPath(String description) {
        this.descriptionLabel.setText(description);
    }

    /**
     * Adds a results viewer to this result view panel.
     *
     * @param resultViewer The results viewer.
     */
    public void addResultViewer(DataResultViewer resultViewer) {
        resultViewers.add(resultViewer);
        resultViewerTabs.addTab(resultViewer.getTitle(), resultViewer.getComponent());
    }

    /**
     * Gets the result viewers for this result view panel.
     *
     * @return A list of result viewers.
     */
    @Override
    public List<DataResultViewer> getViewers() {
        return Collections.unmodifiableList(resultViewers);
    }

    /**
     * Sets the content view for this result view panel. Needs to be called
     * before the first call to open.
     *
     * @param customContentView A content view to use in place of the default
     *                          content view.
     */
    public void setContentViewer(DataContent customContentView) {
        this.contentView = customContentView;
    }

    /**
     * Opens this result view panel. Should be called by a parent top component
     * when the top component is opened.
     */
    public void open() {
        /*
         * The parent top component is expected to be an explorer manager
         * provider that exposes a lookup maintained by its explorer manager to
         * the actions global context. The child result view panel will then
         * find the parent top component's explorer manager at runtime, so that
         * it can act as an explorer manager provider for its child result
         * viewers. This connects the nodes displayed in the result viewers to
         * the actions global context.
         */
        if (this.explorerManager == null) {
            this.explorerManager = ExplorerManager.find(this);
            this.explorerManager.addPropertyChangeListener(this.explorerManagerListener);
        }

        /*
         * Load either the supplied result viewers or the result viewers
         * provided by the result viewer extension point into the tabbed pane.
         * If loading from the extension point and distinct result viewer
         * instances MUST be created if this is not the "main" result view.
         */
        if (this.resultViewerTabs.getTabCount() == 0) {
            if (this.resultViewers.isEmpty()) {
                for (DataResultViewer resultViewer : Lookup.getDefault().lookupAll(DataResultViewer.class)) {
                    if (this.isMain) {
                        this.resultViewers.add(resultViewer);
                    } else {
                        this.resultViewers.add(resultViewer.createInstance());
                    }
                }
            }
            this.resultViewers.forEach((resultViewer) -> resultViewerTabs.addTab(resultViewer.getTitle(), resultViewer.getComponent()));
        }

        this.setVisible(true);
    }

    /**
     * Sets the current root node for this result view panel. The child nodes of
     * the current root node will be displayed in the child result viewers. For
     * the "main" panel, the root node is the currently selected node in the
     * application tree view docked into the left side of the main application
     * window.
     *
     * @param rootNode The root node for this panel, may be null if the panel is
     *                 to be reset.
     */
    @Override
    public void setNode(Node rootNode) {
        if (this.currentRootNode != null) {
            this.currentRootNode.removeNodeListener(rootNodeListener);
        }

        /*
         * Deferring becoming a listener to the tabbed pane until this point
         * eliminates handling a superfluous stateChanged event during
         * construction.
         */
        if (listeningToTabbedPane == false) {
            resultViewerTabs.addChangeListener(this);
            listeningToTabbedPane = true;
        }

        this.currentRootNode = rootNode;
        if (this.currentRootNode != null) {
            /*
             * The only place we reset the rootNodeListener allowing the
             * contents of the results tab represented by this node to be
             * changed a single time before it is necessary to reset it again.
             * Necessary when transitioning from "Please wait..." node to having
             * contents.
             */
            rootNodeListener.reset();
            this.currentRootNode.addNodeListener(rootNodeListener);
        }

        this.resultViewers.forEach((viewer) -> {
            viewer.resetComponent();
        });
        setupTabs(this.currentRootNode);

        if (this.currentRootNode != null) {
            int childrenCount = this.currentRootNode.getChildren().getNodesCount();
            this.numberOfChildNodesLabel.setText(Integer.toString(childrenCount));
        }
        this.numberOfChildNodesLabel.setVisible(true);
    }

    /**
     * Gets the root node of this result view panel. For the "main" panel, the
     * root node is the currently selected node in the application tree view
     * docked into the left side of the main application window.
     *
     * @return The root node.
     */
    public Node getRootNode() {
        return currentRootNode;
    }

    /**
     * Sets the label text that displays the number of the child nodes displayed
     * by this result view panel's result viewers.
     *
     * @param numberOfChildNodes The number of child nodes.
     */
    public void setNumberOfChildNodes(Integer numberOfChildNodes) {
        this.numberOfChildNodesLabel.setText(Integer.toString(numberOfChildNodes));
    }

    /**
     * Selects the given child nodes of the root node in this panel's result
     * viewers.
     *
     * @param selectedNodes The child nodes to be selected.
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
        for (int i = 0; i < resultViewerTabs.getTabCount(); i++) {
            if (resultViewers.get(i).isSupported(selectedNode)) {
                resultViewerTabs.setEnabledAt(i, true);
            } else {
                resultViewerTabs.setEnabledAt(i, false);
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
                    if (resultViewers.get(i) instanceof DataResultViewerTable && resultViewerTabs.isEnabledAt(i)) {
                        tabToSelect = i;
                    }
                }
            }
        }
        if (tabToSelect == NO_TAB_SELECTED) {
            if ((tabToSelect == NO_TAB_SELECTED) || (!resultViewerTabs.isEnabledAt(tabToSelect))) {
                for (int i = 0; i < resultViewerTabs.getTabCount(); ++i) {
                    if (resultViewerTabs.isEnabledAt(i)) {
                        tabToSelect = i;
                        break;
                    }
                }
            }
        }

        /*
         * If there is a tab to select, do so, and push the selected node to the
         * corresponding result viewer.
         */
        if (tabToSelect != NO_TAB_SELECTED) {
            resultViewerTabs.setSelectedIndex(tabToSelect);
            resultViewers.get(tabToSelect).setNode(selectedNode);
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
        if (currentTab != DataResultPanel.NO_TAB_SELECTED) {
            DataResultViewer currentViewer = this.resultViewers.get(currentTab);
            this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            try {
                currentViewer.setNode(currentRootNode);
            } finally {
                this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
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
            openCase = Case.getCurrentCaseThrows();
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
        if (explorerManager != null && explorerManagerListener != null) {
            explorerManager.removePropertyChangeListener(explorerManagerListener);
            explorerManager = null;
        }

        this.resultViewers.forEach((viewer) -> viewer.setNode(null));

        if (!this.isMain) {
            this.resultViewers.forEach(DataResultViewer::clearComponent);
            this.descriptionLabel.removeAll();
            this.numberOfChildNodesLabel.removeAll();
            this.matchLabel.removeAll();
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
     * Responds to node selection change events from the explorer manager of
     * this panel's parent top component. The selected nodes are passed to the
     * content view. This is how the results view and the content view are kept
     * in sync. It is therefore required that all of the result viewers in this
     * panel use the explorer manager of the parent top component. This supports
     * this way of passing the selection to the content view, plus the exposure
     * of the selection to through the actions global context, which is needed
     * for multiple selection.
     */
    private class ExplorerManagerListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals(ExplorerManager.PROP_SELECTED_NODES) && contentView != null) {
                /*
                 * Pass a single node selection in a result viewer to the
                 * content view. Note that passing null to the content view
                 * signals that either multiple nodes are selected, or a
                 * previous selection has been cleared. This is important to the
                 * content view, since its child content viewers only work for a
                 * single node.
                 */
                Node[] selectedNodes = explorerManager.getSelectedNodes();
                if (selectedNodes.length == 1) {
                    contentView.setNode(selectedNodes[0]);
                } else {
                    contentView.setNode(null);
                }
            }
        }
    }

    /**
     * Responds to changes in the root node due to asynchronous child node
     * creation. This listener allows for the tabs of the result viewer to be
     * set up again after the "Please wait..." node has ended and actual content
     * should be displayed in the table.
     */
    private class RootNodeListener implements NodeListener {

        //it is assumed we are still waiting for data when the node is initially constructed
        private volatile boolean waitingForData = true;

        public void reset() {
            waitingForData = true;
        }

        @Override
        public void childrenAdded(final NodeMemberEvent nme) {
            Node[] delta = nme.getDelta();
            updateMatches();

            /*
             * Ensures that after the initial call to setupTabs in the
             * DataResultPanel.setNode method that we only call setupTabs one
             * additional time. This is to account for the transition that is
             * possible from a "Please wait..." node or a tab with no results in
             * it and a tab containing data and thereby having all of it's
             * columns.
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
            if (currentRootNode != null && currentRootNode.getChildren() != null) {
                setNumMatches(currentRootNode.getChildren().getNodesCount());
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

        descriptionLabel = new javax.swing.JLabel();
        numberOfChildNodesLabel = new javax.swing.JLabel();
        matchLabel = new javax.swing.JLabel();
        resultViewerTabs = new javax.swing.JTabbedPane();

        setMinimumSize(new java.awt.Dimension(0, 5));
        setPreferredSize(new java.awt.Dimension(5, 5));

        org.openide.awt.Mnemonics.setLocalizedText(descriptionLabel, org.openide.util.NbBundle.getMessage(DataResultPanel.class, "DataResultPanel.descriptionLabel.text")); // NOI18N
        descriptionLabel.setMinimumSize(new java.awt.Dimension(5, 14));

        org.openide.awt.Mnemonics.setLocalizedText(numberOfChildNodesLabel, org.openide.util.NbBundle.getMessage(DataResultPanel.class, "DataResultPanel.numberOfChildNodesLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(matchLabel, org.openide.util.NbBundle.getMessage(DataResultPanel.class, "DataResultPanel.matchLabel.text")); // NOI18N

        resultViewerTabs.setMinimumSize(new java.awt.Dimension(0, 5));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(descriptionLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(numberOfChildNodesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(matchLabel))
            .addComponent(resultViewerTabs, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(numberOfChildNodesLabel)
                        .addComponent(matchLabel))
                    .addComponent(descriptionLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, 0)
                .addComponent(resultViewerTabs, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel descriptionLabel;
    private javax.swing.JLabel matchLabel;
    private javax.swing.JLabel numberOfChildNodesLabel;
    private javax.swing.JTabbedPane resultViewerTabs;
    // End of variables declaration//GEN-END:variables

    /**
     * Gets whether or not this result view panel is the "main" result view
     * panel used to view the child nodes of a node selected in the application
     * tree view (DirectoryTreeTopComponent) that is normally docked into the
     * left hand side of the main window.
     *
     * @return True or false.
     *
     * @deprecated This method has no valid use case.
     */
    @Deprecated
    @Override
    public boolean isMain() {
        return this.isMain;
    }

    /**
     * Sets the label text that displays the number of the child nodes displayed
     * by this result view panel's result viewers.
     *
     * @param numberOfChildNodes The number of child nodes.
     *
     * @deprecated Use setNumberOfChildNodes instead.
     */
    @Deprecated
    public void setNumMatches(Integer numberOfChildNodes) {
        this.setNumberOfChildNodes(numberOfChildNodes);
    }

    /**
     * Resets the state of this results panel.
     *
     * @param unusedSelectedNode Unused.
     *
     * @deprecated Use setNode(null) instead.
     */
    @Deprecated
    public void resetTabs(Node unusedSelectedNode) {
        this.setNode(null);
    }

}
