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

import java.util.ArrayList;
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

/**
 * Top component which displays results (top-right editor mode by default).
 *
 * There is a main tc instance that responds to directory tree selections.
 * Others can also create an additional result viewer tc using one of the
 * factory methods, that can be:
 *
 * - added to top-right corner as an additional, closeable viewer - added to a
 * different, custom mode, - linked to a custom content viewer that responds to
 * selections from this top component.
 *
 * For embedding custom data result in other top components window, use
 * DataResultPanel component instead, since we cannot nest top components.
 *
 * Encapsulates the internal DataResultPanel and delegates to it.
 *
 * Implements DataResult interface by delegating to the encapsulated
 * DataResultPanel.
 */
@RetainLocation("editor")
public class DataResultTopComponent extends TopComponent implements DataResult, ExplorerManager.Provider {

    private static final Logger logger = Logger.getLogger(DataResultTopComponent.class.getName());
    private final ExplorerManager explorerManager = new ExplorerManager();
    private final DataResultPanel dataResultPanel; //embedded component with all the logic
    private boolean isMain;
    private String customModeName;

    //keep track of tcs opened for menu presenters
    private static final List<String> activeComponentIds = Collections.synchronizedList(new ArrayList<String>());

    /**
     * Create a new data result top component
     *
     * @param isMain whether it is the main, application default result viewer,
     *               there can be only 1 main result viewer
     * @param title  title of the data result window
     */
    public DataResultTopComponent(boolean isMain, String title) {
        associateLookup(ExplorerUtils.createLookup(explorerManager, getActionMap()));
        this.dataResultPanel = new DataResultPanel(title, isMain);
        initComponents();
        customizeComponent(isMain, title);
    }

    /**
     * Create a new, custom data result top component, in addition to the
     * application main one
     *
     * @param name                unique name of the data result window, also
     *                            used as title
     * @param mode                custom mode to dock into
     * @param customContentViewer custom content viewer to send selection events
     *                            to
     */
    DataResultTopComponent(String name, String mode, DataContentTopComponent customContentViewer) {
        associateLookup(ExplorerUtils.createLookup(explorerManager, getActionMap()));
        this.customModeName = mode;
        dataResultPanel = new DataResultPanel(name, customContentViewer);
        initComponents();
        customizeComponent(isMain, name);
    }

    private void customizeComponent(boolean isMain, String title) {
        this.isMain = isMain;
        this.customModeName = null;

        setToolTipText(NbBundle.getMessage(DataResultTopComponent.class, "HINT_NodeTableTopComponent"));

        setTitle(title); // set the title
        setName(title);
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(AddBookmarkTagAction.BOOKMARK_SHORTCUT, "addBookmarkTag"); //NON-NLS
        getActionMap().put("addBookmarkTag", new AddBookmarkTagAction()); //NON-NLS

        putClientProperty(TopComponent.PROP_CLOSING_DISABLED, isMain); // set option to close compoment in GUI
        putClientProperty(TopComponent.PROP_MAXIMIZATION_DISABLED, true);
        putClientProperty(TopComponent.PROP_DRAGGING_DISABLED, true);

        activeComponentIds.add(title);
    }

    /**
     * Initialize previously created tc instance with additional data
     *
     * @param pathText
     * @param givenNode
     * @param totalMatches
     * @param newDataResult previously created with createInstance()
     *                      uninitialized instance
     */
    public static void initInstance(String pathText, Node givenNode, int totalMatches, DataResultTopComponent newDataResult) {
        newDataResult.setNumMatches(totalMatches);

        newDataResult.open(); // open it first so the component can be initialized

        // set the tree table view
        newDataResult.setNode(givenNode);
        newDataResult.setPath(pathText);

        newDataResult.requestActive();
    }

    /**
     * Creates a new non-default DataResult component and initializes it
     *
     * @param title        Title of the component window
     * @param pathText     Descriptive text about the source of the nodes
     *                     displayed
     * @param givenNode    The new root node
     * @param totalMatches Cardinality of root node's children
     *
     * @return a new, not default, initialized DataResultTopComponent instance
     */
    public static DataResultTopComponent createInstance(String title, String pathText, Node givenNode, int totalMatches) {
        DataResultTopComponent newDataResult = new DataResultTopComponent(false, title);

        initInstance(pathText, givenNode, totalMatches, newDataResult);

        return newDataResult;
    }

    /**
     * Creates a new non-default DataResult component linked with a custom data
     * content, and initializes it.
     *
     *
     * @param title             Title of the component window
     * @param mode              custom mode to dock this custom TopComponent to
     * @param pathText          Descriptive text about the source of the nodes
     *                          displayed
     * @param givenNode         The new root node
     * @param totalMatches      Cardinality of root node's children
     * @param dataContentWindow a handle to data content top component window to
     *
     * @return a new, not default, initialized DataResultTopComponent instance
     */
    public static DataResultTopComponent createInstance(String title, final String mode, String pathText, Node givenNode, int totalMatches, DataContentTopComponent dataContentWindow) {
        DataResultTopComponent newDataResult = new DataResultTopComponent(title, mode, dataContentWindow);

        initInstance(pathText, givenNode, totalMatches, newDataResult);
        return newDataResult;
    }

    /**
     * Creates a new non-default DataResult component. You probably want to use
     * initInstance after it
     *
     * @param title
     *
     * @return a new, not default, not fully initialized DataResultTopComponent
     *         instance
     */
    public static DataResultTopComponent createInstance(String title) {
        final DataResultTopComponent newDataResult = new DataResultTopComponent(false, title);

        return newDataResult;
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
    }

    /**
     * Get a list with names of active windows ids, e.g. for the menus
     *
     * @return
     */
    public static List<String> getActiveComponentIds() {
        return new ArrayList<>(activeComponentIds);
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
        setCustomMode();
        super.open(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<DataResultViewer> getViewers() {
        return dataResultPanel.getViewers();
    }

    private void setCustomMode() {
        if (customModeName != null) {
            Mode mode = WindowManager.getDefault().findMode(customModeName);
            if (mode != null) {
                logger.log(Level.INFO, "Found custom mode, setting: {0}", customModeName);//NON-NLS
                mode.dockInto(this);
            } else {
                logger.log(Level.WARNING, "Could not find mode: {0}, will dock into the default one", customModeName);//NON-NLS
            }
        }
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
         * Syncronize the data content viewer to show the currently selected
         * item in the data results if only one is selected, or show nothing
         * otherwise.
         */
        final DataContentTopComponent dataContentTopComponent = DataContentTopComponent.findInstance();
        final Node[] nodeList = explorerManager.getSelectedNodes();

        if (nodeList.length == 1) {
            dataContentTopComponent.setNode(nodeList[0]);
        } else {
            dataContentTopComponent.setNode(null);
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
        /*
         * If this is the results top component in the upper right of the main
         * window, only allow it to be closed when there's no case opened or no
         * data sources in the open case.
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
     * Resets the tabs based on the selected Node. If the selected node is null
     * or not supported, disable that tab as well.
     *
     * @param selectedNode the selected content Node
     */
    public void resetTabs(Node selectedNode) {

        dataResultPanel.resetTabs(selectedNode);
    }

    public void setSelectedNodes(Node[] selected) {
        dataResultPanel.setSelectedNodes(selected);
    }

    public Node getRootNode() {
        return dataResultPanel.getRootNode();
    }

    void setNumMatches(int matches) {
        this.dataResultPanel.setNumMatches(matches);
    }
}
