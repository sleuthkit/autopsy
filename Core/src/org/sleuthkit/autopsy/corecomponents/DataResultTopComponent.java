/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

import org.sleuthkit.autopsy.corecomponentinterfaces.DataResult;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.openide.nodes.Node;
import org.openide.windows.Mode;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Top component which displays results (top-right editor mode by default).
 * 
 * There is a main tc instance that responds to directory tree selections.
 * Others can also create an additional result viewer tc using one of the factory methods, that can be:
 * 
 * - added to top-right corner as an additional, closeable viewer
 * - added to a different, custom mode,
 * - linked to a custom content viewer that responds to selections from this top component.
 * 
 * For embedding custom data result in other top components window, use DataResultPanel component instead,
 * since we cannot nest top components.
 * 
 * Encapsulates the internal DataResultPanel and delegates to it.
 * 
 * Implements DataResult interface by delegating to the encapsulated DataResultPanel.
 */
public class DataResultTopComponent extends TopComponent implements DataResult {

    private static final Logger logger = Logger.getLogger(DataResultTopComponent.class.getName());
    private DataResultPanel dataResultPanel; //embedded component with all the logic
    private boolean isMain;
    private String customModeName;

    /**
     * Create a new data result top component
     *
     * @param isMain whether it is the main, application default result viewer,
     * there can be only 1 main result viewer
     * @param title title of the data result window
     */
    public DataResultTopComponent(boolean isMain, String title) {
        super();

        //dataResultPanel is added to this tc using UI builder
        this.dataResultPanel = new DataResultPanel(isMain, title);

        initComponents();
        customizeComponent(isMain, title);

    }

    /**
     * Create a new, custom data result top component, in addition to the
     * application main one
     *
     * @param name unique name of the data result window, also used as title
     * @param customModeName custom mode to dock into
     * @param customContentViewer custom content viewer to send selection events
     * to
     */
    DataResultTopComponent(String name, String mode, DataContentTopComponent customContentViewer) {
        super();
        this.customModeName = mode;

        //custom content viewer to link to this result viewer
        //dataResultPanel is added to this tc using UI builder
        dataResultPanel = new DataResultPanel(name, customContentViewer);

        initComponents();
        customizeComponent(isMain, name);;

    }

    private void customizeComponent(boolean isMain, String title) {
        this.isMain = isMain;
        this.customModeName = null;

        setToolTipText(NbBundle.getMessage(DataResultTopComponent.class, "HINT_NodeTableTopComponent"));

        setTitle(title); // set the title

        putClientProperty(TopComponent.PROP_CLOSING_DISABLED, Boolean.valueOf(isMain)); // set option to close compoment in GUI
        putClientProperty(TopComponent.PROP_MAXIMIZATION_DISABLED, true);
        putClientProperty(TopComponent.PROP_DRAGGING_DISABLED, true);

    }

    private static void createInstanceCommon(String pathText, Node givenNode, int totalMatches, DataResultTopComponent newDataResult) {
        newDataResult.setNumMatches(totalMatches);

        newDataResult.open(); // open it first so the component can be initialized

        // set the tree table view
        newDataResult.setNode(givenNode);
        newDataResult.setPath(pathText);
        
        newDataResult.requestActive();
    }

    /**
     * Creates a new non-default DataResult component
     *
     * @param title Title of the component window
     * @param pathText Descriptive text about the source of the nodes displayed
     * @param givenNode The new root node
     * @param totalMatches Cardinality of root node's children
     * @return
     */
    public static DataResultTopComponent createInstance(String title, String pathText, Node givenNode, int totalMatches) {
        DataResultTopComponent newDataResult = new DataResultTopComponent(false, title);

        createInstanceCommon(pathText, givenNode, totalMatches, newDataResult);

        return newDataResult;
    }

    /**
     * Creates a new non-default DataResult component
     *
     * @param title Title of the component window
     * @param customModeName custom mode to dock this custom TopComponent to
     * @param pathText Descriptive text about the source of the nodes displayed
     * @param givenNode The new root node
     * @param totalMatches Cardinality of root node's children
     * @param dataContentWindow a handle to data content top component window to
     * @return
     */
    public static DataResultTopComponent createInstance(String title, final String mode, String pathText, Node givenNode, int totalMatches, DataContentTopComponent dataContentWindow) {
        DataResultTopComponent newDataResult = new DataResultTopComponent(title, mode, dataContentWindow);

        createInstanceCommon(pathText, givenNode, totalMatches, newDataResult);
        return newDataResult;
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

    private void setCustomMode() {
        if (customModeName != null) {
            //putClientProperty("TopComponentAllowDockAnywhere", Boolean.TRUE);
            Mode mode = WindowManager.getDefault().findMode(customModeName);
            if (mode != null) {
                logger.log(Level.INFO, "Found custom mode, setting: " + customModeName);
                mode.dockInto(this);

            } else {
                logger.log(Level.WARNING, "Could not find mode: " + customModeName + ", will dock into the default one");
            }
        }

    }

    @Override
    public void componentOpened() {
        this.dataResultPanel.open();
    }

    @Override
    public void componentClosed() {
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
        return (!this.isMain) || !Case.existsCurrentCase() || Case.getCurrentCase().getRootObjectsCount() == 0; // only allow this window to be closed when there's no case opened or no image in this case
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
