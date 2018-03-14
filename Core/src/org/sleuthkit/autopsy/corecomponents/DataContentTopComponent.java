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

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JTabbedPane;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Top component that organizes all of the data content viewers. Doing a lookup
 * on this class will always return the default instance (which is created at
 * startup).
 */
// Registered as a service provider in layer.xml
//@TopComponent.Description(preferredID = "DataContentTopComponent")
//@TopComponent.Registration(mode = "output", openAtStartup = true)
//@TopComponent.OpenActionRegistration(displayName = "#CTL_DataContentAction", preferredID = "DataContentTopComponent")
public final class DataContentTopComponent extends TopComponent implements DataContent, ExplorerManager.Provider {

    private static final Logger logger = Logger.getLogger(DataContentTopComponent.class.getName());

    // reference to the "default" TC that always stays open
    private static DataContentTopComponent defaultInstance;
    private static final long serialVersionUID = 1L;
    // set to true if this is the TC that always stays open and is the default place to display content
    private final boolean isDefault;
    // the content panel holding tabs with content viewers
    private final DataContentPanel dataContentPanel;
    private final ExplorerManager explorerManager = new ExplorerManager();

    // contains a list of the undocked TCs
    private static final ArrayList<DataContentTopComponent> newWindowList = new ArrayList<>();
    private static final String PREFERRED_ID = "DataContentTopComponent"; //NON-NLS
    private static final String DEFAULT_NAME = NbBundle.getMessage(DataContentTopComponent.class, "CTL_DataContentTopComponent");
    private static final String TOOLTIP_TEXT = NbBundle.getMessage(DataContentTopComponent.class, "HINT_DataContentTopComponent");

    private DataContentTopComponent(boolean isDefault, String name) {
        initComponents();
        setName(name);
        setToolTipText(TOOLTIP_TEXT);

        this.isDefault = isDefault;

        dataContentPanel = new DataContentPanel(isDefault);
        add(dataContentPanel);

        associateLookup(ExplorerUtils.createLookup(explorerManager, getActionMap()));

        putClientProperty(TopComponent.PROP_CLOSING_DISABLED, isDefault); // prevent option to close compoment in GUI
        logger.log(Level.INFO, "Created DataContentTopComponent instance: {0}", this); //NON-NLS
    }

    /**
     * This createInstance method is used to create an undocked instance for the
     * "View in New Window" feature.
     *
     * @param filePath  path of given file node
     * @param givenNode node to view content of
     *
     * @return newly undocked instance
     */
    public static DataContentTopComponent createUndocked(String filePath, Node givenNode) {

        DataContentTopComponent dctc = new DataContentTopComponent(false, filePath);
        dctc.componentOpened();
        dctc.setNode(givenNode);

        newWindowList.add(dctc);

        return dctc;
    }

    /**
     * Gets default instance. Do not use directly: reserved for *.settings files
     * only, i.e. deserialization routines; otherwise you could get a
     * non-deserialized defaultInstance. To obtain the singleton instance, use
     * findInstance.
     *
     * @return
     */
    public static synchronized DataContentTopComponent getDefault() {
        if (defaultInstance == null) {
            defaultInstance = new DataContentTopComponent(true, DEFAULT_NAME);
        }
        return defaultInstance;
    }

    /**
     * Obtain the default DataContentTopComponent default instance. Never call
     * getDefault directly!
     *
     * @return The default DataContentTopComponent.
     */
    public static synchronized DataContentTopComponent findInstance() {
        TopComponent win = WindowManager.getDefault().findTopComponent(PREFERRED_ID);
        if (win == null) {
            logger.warning("Cannot find " + PREFERRED_ID + " component. It will not be located properly in the window system."); //NON-NLS
            return getDefault();
        }
        if (win instanceof DataContentTopComponent) {
            return (DataContentTopComponent) win;
        }
        logger.warning(
                "There seem to be multiple components with the '" + PREFERRED_ID //NON-NLS
                + "' ID. That is a potential source of errors and unexpected behavior."); //NON-NLS
        return getDefault();
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_NEVER;
    }

    @Override
    public void componentOpened() {
    }

    @Override
    public void componentClosed() {

        dataContentPanel.setNode(null);

        if (!this.isDefault) {
            newWindowList.remove(this);
        }
    }

    @Override
    protected String preferredID() {
        if (this.isDefault) {
            return PREFERRED_ID;
        } else {
            return this.getName();
        }
    }

    @Override
    public void setNode(Node selectedNode) {
        dataContentPanel.setNode(selectedNode);
    }

    @Override
    public boolean canClose() {
        /*
         * If this is the main content viewers top component in the bottom of
         * the main window, only it to be closed when there's no case opened or
         * no data sources in the open case.
         */
        Case openCase;
        try {
            openCase = Case.getOpenCase();
        } catch (NoCurrentCaseException ex) {
            return true;
        }
        return (!this.isDefault) || openCase.hasData() == false;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
    }

    /**
     * Get the tab pane
     *
     * @return tab pane with individual DataContentViewers
     */
    public JTabbedPane getTabPanels() {
        return dataContentPanel.getTabPanels();
    }

    /**
     * Returns a list of the non-default (main) TopComponents
     *
     * @return
     */
    public static List<DataContentTopComponent> getNewWindowList() {
        return newWindowList;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables

}
