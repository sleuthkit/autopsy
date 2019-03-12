/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JComponent;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.windows.Mode;
import org.openide.windows.RetainLocation;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.actions.AddBookmarkTagAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResult;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.directorytree.ExternalViewerShortcutAction;

/**
 * A DataResultTopComponent object is a NetBeans top component that provides
 * multiple views of the application data represented by a NetBeans Node. It is
 * a result view component (implements DataResult) that contains a result view
 * panel (DataResultPanel), which is also a result view component. The result
 * view panel is a JPanel with a JTabbedPane child component that contains a
 * collection of result viewers. Each result viewer (implements
 * DataResultViewer) presents a different view of the current node. Result
 * viewers are usually JPanels that display the child nodes of the current node
 * using a NetBeans explorer view child component. The result viewers are either
 * supplied during construction of the result view top component or provided by
 * the result viewer extension point (service providers that implement
 * DataResultViewer).
 *
 * Result view top components are typically docked into the upper right hand
 * side of the main application window (editor mode), and are linked to the
 * content view in the lower right hand side of the main application window
 * (output mode) by the result view panel. The panel passes single node
 * selections in the active result viewer to the content view.
 *
 * The "main" result view top component receives its current node as a selection
 * from the case tree view in the top component (DirectoryTreeYopComponent)
 * docked into the left hand side of the main application window.
 *
 * Result view top components are explorer manager providers to connect the
 * lookups of the nodes displayed in the NetBeans explorer views of the result
 * viewers to the actions global context.
 */
@RetainLocation("editor")
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public final class DataResultTopComponent extends TopComponent implements DataResult, ExplorerManager.Provider {

    private static final Logger logger = Logger.getLogger(DataResultTopComponent.class.getName());
    private static final List<String> activeComponentIds = Collections.synchronizedList(new ArrayList<String>());
    private final boolean isMain;
    private final String customModeName;
    private final ExplorerManager explorerManager;
    private final DataResultPanel dataResultPanel;

    /**
     * Creates a result view top component that provides multiple views of the
     * application data represented by a NetBeans Node. The result view will be
     * docked into the upper right hand side of the main application window
     * (editor mode) and will be linked to the content view in the lower right
     * hand side of the main application window (output mode). Its result
     * viewers are provided by the result viewer extension point (service
     * providers that implement DataResultViewer).
     *
     * @param title          The title for the top component, appears on the top
     *                       component's tab.
     * @param description    Descriptive text about the node displayed, appears
     *                       on the top component's tab
     * @param node           The node to display.
     * @param childNodeCount The cardinality of the node's children.
     *
     * @return The result view top component.
     */
    public static DataResultTopComponent createInstance(String title, String description, Node node, int childNodeCount) {
        DataResultTopComponent resultViewTopComponent = new DataResultTopComponent(false, title, null, Collections.emptyList(), DataContentTopComponent.findInstance());
        initInstance(description, node, childNodeCount, resultViewTopComponent);
        return resultViewTopComponent;
    }

    /**
     * Creates a result view top component that provides multiple views of the
     * application data represented by a NetBeans Node. The result view will be
     * docked into the upper right hand side of the main application window
     * (editor mode) and will be linked to the content view in the lower right
     * hand side of the main application window (output mode).
     *
     * @param title          The title for the top component, appears on the top
     *                       component's tab.
     * @param description    Descriptive text about the node displayed, appears
     *                       on the top component's tab
     * @param node           The node to display.
     * @param childNodeCount The cardinality of the node's children.
     * @param viewers        A collection of result viewers to use instead of
     *                       the result viewers provided by the results viewer
     *                       extension point.
     *
     * @return The result view top component.
     */
    public static DataResultTopComponent createInstance(String title, String description, Node node, int childNodeCount, Collection<DataResultViewer> viewers) {
        DataResultTopComponent resultViewTopComponent = new DataResultTopComponent(false, title, null, viewers, DataContentTopComponent.findInstance());
        initInstance(description, node, childNodeCount, resultViewTopComponent);
        return resultViewTopComponent;
    }

    /**
     * Creates a partially initialized result view top component that provides
     * multiple views of the application data represented by a NetBeans Node.
     * The result view will be docked into the upper right hand side of the main
     * application window (editor mode) and will be linked to the content view
     * in the lower right hand side of the main application window (output
     * mode). Its result viewers are provided by the result viewer extension
     * point (service providers that implement DataResultViewer).
     *
     * IMPORTANT: Initialization MUST be completed by calling initInstance.
     *
     * @param title The title for the result view top component, appears on the
     *              top component's tab.
     *
     * @return The partially initialized result view top component.
     */
    public static DataResultTopComponent createInstance(String title) {
        DataResultTopComponent resultViewTopComponent = new DataResultTopComponent(false, title, null, Collections.emptyList(), DataContentTopComponent.findInstance());
        return resultViewTopComponent;
    }

    /**
     * Initializes a partially initialized result view top component.
     *
     * @param description            Descriptive text about the node displayed,
     *                               appears on the top component's tab
     * @param node                   The node to display.
     * @param childNodeCount         The cardinality of the node's children.
     * @param resultViewTopComponent The partially initialized result view top
     *                               component.
     */
    public static void initInstance(String description, Node node, int childNodeCount, DataResultTopComponent resultViewTopComponent) {
        resultViewTopComponent.setNumberOfChildNodes(childNodeCount);
        resultViewTopComponent.open();
        resultViewTopComponent.setNode(node);
        resultViewTopComponent.setPath(description);
        resultViewTopComponent.requestActive();
    }

    /**
     * Creates a result view top component that provides multiple views of the
     * application data represented by a NetBeans Node. The result view will be
     * docked into a custom mode and linked to the supplied content view. Its
     * result viewers are provided by the result viewer extension point (service
     * providers that implement DataResultViewer).
     *
     * @param title                   The title for the top component, appears
     *                                on the top component's tab.
     * @param mode                    The NetBeans Window system mode into which
     *                                this top component should be docked.
     * @param description             Descriptive text about the node displayed,
     *                                appears on the top component's tab
     * @param node                    The node to display.
     * @param childNodeCount          The cardinality of the node's children.
     * @param contentViewTopComponent A content view to which this result view
     *                                will be linked.
     *
     * @return The result view top component.
     */
    public static DataResultTopComponent createInstance(String title, String mode, String description, Node node, int childNodeCount, DataContentTopComponent contentViewTopComponent) {
        DataResultTopComponent newDataResult = new DataResultTopComponent(false, title, mode, Collections.emptyList(), contentViewTopComponent);
        initInstance(description, node, childNodeCount, newDataResult);
        return newDataResult;
    }

    /**
     * Creates a result view top component that provides multiple views of the
     * application data represented by a NetBeans Node. The result view will be
     * the "main" result view and will docked into the upper right hand side of
     * the main application window (editor mode) and will be linked to the
     * content view in the lower right hand side of the main application window
     * (output mode). Its result viewers are provided by the result viewer
     * extension point (service providers that implement DataResultViewer).
     *
     * IMPORTANT: The "main" result view top component receives its current node
     * as a selection from the case tree view in the top component
     * (DirectoryTreeTopComponent) docked into the left hand side of the main
     * application window. This constructor is RESERVED for the use of the
     * DirectoryTreeTopComponent singleton only. DO NOT USE OTHERWISE.
     *
     * @param title The title for the top component, appears on the top
     *              component's tab.
     */
    public DataResultTopComponent(String title) {
        this(true, title, null, Collections.emptyList(), DataContentTopComponent.findInstance());
    }

    /**
     * Constructs a result view top component that provides multiple views of
     * the application data represented by a NetBeans Node.
     *
     * @param isMain                  Whether or not this is the "main" result
     *                                view top component.
     * @param title                   The title for the top component, appears
     *                                on the top component's tab.
     * @param mode                    The NetBeans Window system mode into which
     *                                this top component should be docked. If
     *                                null, the editor mode will be used by
     *                                default.
     * @param viewers                 A collection of result viewers. If empty,
     *                                the result viewers provided by the results
     *                                viewer extension point will be used.
     * @param contentViewTopComponent A content view to which this result view
     *                                will be linked, possibly null.
     */
    private DataResultTopComponent(boolean isMain, String title, String mode, Collection<DataResultViewer> viewers, DataContentTopComponent contentViewTopComponent) {
        this.isMain = isMain;
        this.explorerManager = new ExplorerManager();
        associateLookup(ExplorerUtils.createLookup(explorerManager, getActionMap()));
        this.customModeName = mode;
        this.dataResultPanel = new DataResultPanel(title, isMain, viewers, contentViewTopComponent);
        initComponents();
        customizeComponent(title);
    }

    private void customizeComponent(String title) {
        setToolTipText(NbBundle.getMessage(DataResultTopComponent.class, "HINT_NodeTableTopComponent"));  //NON-NLS
        setTitle(title);
        setName(title);
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(AddBookmarkTagAction.BOOKMARK_SHORTCUT, "addBookmarkTag"); //NON-NLS
        getActionMap().put("addBookmarkTag", new AddBookmarkTagAction()); //NON-NLS
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ExternalViewerShortcutAction.EXTERNAL_VIEWER_SHORTCUT, "useExternalViewer"); //NON-NLS 
        getActionMap().put("useExternalViewer", ExternalViewerShortcutAction.getInstance()); //NON-NLS
        putClientProperty(TopComponent.PROP_CLOSING_DISABLED, isMain);
        putClientProperty(TopComponent.PROP_MAXIMIZATION_DISABLED, true);
        putClientProperty(TopComponent.PROP_DRAGGING_DISABLED, true);
        activeComponentIds.add(title);
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
    }

    /**
     * Get a listing of the preferred identifiers of all the result view top
     * components that have been created.
     *
     * @return The listing.
     */
    public static List<String> getActiveComponentIds() {
        return new ArrayList<>(activeComponentIds);
    }

    @Override
    public int getPersistenceType() {
        if (customModeName == null) {
            return TopComponent.PERSISTENCE_NEVER;
        } else {
            return TopComponent.PERSISTENCE_ALWAYS;
        }
    }

    @Override
    public void open() {
        if (customModeName != null) {
            Mode mode = WindowManager.getDefault().findMode(customModeName);
            if (mode != null) {
                logger.log(Level.INFO, "Found custom mode, setting: {0}", customModeName);//NON-NLS
                mode.dockInto(this);
            } else {
                logger.log(Level.WARNING, "Could not find mode: {0}, will dock into the default one", customModeName);//NON-NLS
            }
        }
        super.open();
    }

    @Override
    public List<DataResultViewer> getViewers() {
        return dataResultPanel.getViewers();
    }

    @Override
    public void componentOpened() {
        super.componentOpened();
        this.dataResultPanel.open();
    }

    @Override
    public void componentActivated() {
        super.componentActivated();

        /*
         * Determine which node the content viewer should be using. If multiple
         * results are selected, the node used by the content viewer should be
         * null so no content gets displayed.
         */
        final DataContentTopComponent dataContentTopComponent = DataContentTopComponent.findInstance();
        final Node[] nodeList = explorerManager.getSelectedNodes();

        Node selectedNode;
        if (nodeList.length == 1) {
            selectedNode = nodeList[0];
        } else {
            selectedNode = null;
        }

        /*
         * If the selected node of the content viewer is different than that of
         * the result viewer, the content viewer needs to be updated. Otherwise,
         * don't perform the update. This check will ensure that clicking the
         * column headers and scroll bars of the DataResultTopComponent will not
         * needlessly refresh the content view and cause the tab selection to
         * change to the default.
         */
        if (selectedNode != dataContentTopComponent.getNode()) {
            dataContentTopComponent.setNode(selectedNode);
        }
    }

    @Override
    public void componentClosed() {
        super.componentClosed();
        activeComponentIds.remove(this.getName());
        dataResultPanel.close();
    }

    @Override
    protected String preferredID() {
        return getName();
    }

    @Override
    public String getPreferredID() {
        return getName();
    }

    @Override
    public void setNode(Node selectedNode) {
        dataResultPanel.setNode(selectedNode);
    }

    @Override
    public void setTitle(String title) {
        setName(title);
    }

    @Override
    public void setPath(String pathText) {
        dataResultPanel.setPath(pathText);
    }

    @Override
    public boolean isMain() {
        return isMain;
    }

    @Override
    public boolean canClose() {
        Case openCase;
        try {
            openCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException unused) {
            return true;
        }
        return (!this.isMain) || openCase.hasData() == false;
    }

    public void setSelectedNodes(Node[] selected) {
        dataResultPanel.setSelectedNodes(selected);
    }

    public Node getRootNode() {
        return dataResultPanel.getRootNode();
    }

    /**
     * Sets the cardinality of the current node's children
     *
     * @param childNodeCount The cardinality of the node's children.
     */
    private void setNumberOfChildNodes(int childNodeCount) {
        this.dataResultPanel.setNumberOfChildNodes(childNodeCount);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        org.sleuthkit.autopsy.corecomponents.DataResultPanel dataResultPanelLocal = dataResultPanel;

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(dataResultPanelLocal, javax.swing.GroupLayout.DEFAULT_SIZE, 967, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(dataResultPanelLocal, javax.swing.GroupLayout.DEFAULT_SIZE, 579, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables

    /**
     * Creates a partially initialized result view top component that provides
     * multiple views of the application data represented by a NetBeans Node.
     * The result view will be docked into the upper right hand side of the main
     * application window (editor mode) and will be linked to the content view
     * in the lower right hand side of the main application window (output
     * mode). Its result viewers are provided by the result viewer extension
     * point (service providers that implement DataResultViewer).
     *
     * IMPORTANT: Initialization MUST be completed by calling initInstance.
     *
     * @param isMain Ignored.
     * @param title  The title for the top component, appears on the top
     *               component's tab.
     *
     * @deprecated Use an appropriate overload of createIntance instead.
     */
    @Deprecated
    public DataResultTopComponent(boolean isMain, String title) {
        this(false, title, null, Collections.emptyList(), DataContentTopComponent.findInstance());
    }

    /**
     * Sets the node for which this result view component should provide
     * multiple views of the underlying application data.
     *
     * @param node The node, may be null. If null, the call to this method is
     *             equivalent to a call to resetComponent on this result view
     *             component's result viewers.
     *
     * @deprecated Use setNode instead.
     */
    @Deprecated
    public void resetTabs(Node node) {
        dataResultPanel.setNode(node);
    }

}
