/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.filequery;

import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.windows.RetainLocation;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.corecomponents.DataContentPanel;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Create a dialog for displaying the file discovery tool
 */
@TopComponent.Description(preferredID = "DiscoveryTopComponent", persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "discovery", openAtStartup = false)
@RetainLocation("discovery")
@NbBundle.Messages("DiscoveryTopComponent.name= File Discovery")
public final class DiscoveryTopComponent extends TopComponent {

    private static final long serialVersionUID = 1L;
    private static final String PREFERRED_ID = "DiscoveryTopComponent"; // NON-NLS
    private final FileSearchPanel fileSearchPanel;
    private final GroupListPanel groupListPanel;
    private final DataContentPanel dataContentPanel;
    private final ResultsPanel resultsPanel;
    private final ExplorerManager explorerManager;

    /**
     * Creates new form FileDiscoveryDialog
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public DiscoveryTopComponent(SleuthkitCase caseDb, EamDb centralRepoDb) {
        initComponents();
        setName(Bundle.DiscoveryTopComponent_name());
        explorerManager = new ExplorerManager();
        fileSearchPanel = new FileSearchPanel(caseDb, centralRepoDb);
        DiscoveryEvents.getDiscoveryEventBus().register(fileSearchPanel);
        dataContentPanel = DataContentPanel.createInstance();
        resultsPanel = new ResultsPanel(explorerManager, centralRepoDb);
        DiscoveryEvents.getDiscoveryEventBus().register(resultsPanel);
        groupListPanel = new GroupListPanel();
        DiscoveryEvents.getDiscoveryEventBus().register(groupListPanel);
        leftSplitPane.setLeftComponent(fileSearchPanel);
        leftSplitPane.setRightComponent(groupListPanel);
        rightSplitPane.setTopComponent(resultsPanel);
        rightSplitPane.setBottomComponent(dataContentPanel);
        //add list selection listener so the content viewer will be updated with the selected file
        //when a file is selected in the results panel
        resultsPanel.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    SwingUtilities.invokeLater(() -> {
                        AbstractFile file = resultsPanel.getSelectedFile();
                        if (file != null) {
                            dataContentPanel.setNode(new TableFilterNode(new FileNode(file), false));
                        } else {
                            dataContentPanel.setNode(null);
                        }
                    });
                }
            }
        });
        this.explorerManager.addPropertyChangeListener((evt) -> {
            if (evt.getPropertyName().equals(ExplorerManager.PROP_SELECTED_NODES) && dataContentPanel != null) {
                /*
                 * Pass a single node selection in a result viewer to the
                 * content view. Note that passing null to the content view
                 * signals that either multiple nodes are selected, or a
                 * previous selection has been cleared. This is important to the
                 * content view, since its child content viewers only work for a
                 * single node.
                 */
                Node[] selectedNodes = explorerManager.getSelectedNodes();
                SwingUtilities.invokeLater(() -> {
                    if (selectedNodes.length == 1) {
                        dataContentPanel.setNode(selectedNodes[0]);
                    } else {
                        dataContentPanel.setNode(null);
                    }
                });
            }
        });
    }

    /**
     * Gets the singleton Image Gallery top component. Note that calling this
     * method will cause the top component to be constructed if it does not
     * already exist.
     *
     * @return The top component.
     */
    public static DiscoveryTopComponent getTopComponent() {
        return (DiscoveryTopComponent) WindowManager.getDefault().findTopComponent(PREFERRED_ID);
    }

    @Override
    public void componentOpened() {
        super.componentOpened();
        WindowManager.getDefault().setTopComponentFloating(this, true);
    }

    @Override
    protected void componentClosed() {
        fileSearchPanel.cancelSearch();
        FileSearch.clearCache();
        DiscoveryEvents.getDiscoveryEventBus().unregister(fileSearchPanel);
        DiscoveryEvents.getDiscoveryEventBus().unregister(groupListPanel);
        DiscoveryEvents.getDiscoveryEventBus().unregister(resultsPanel);
        super.componentClosed();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainSplitPane = new javax.swing.JSplitPane();
        leftSplitPane = new javax.swing.JSplitPane();
        rightSplitPane = new javax.swing.JSplitPane();

        setPreferredSize(new java.awt.Dimension(1100, 700));
        setLayout(new java.awt.BorderLayout());

        mainSplitPane.setDividerLocation(550);
        mainSplitPane.setResizeWeight(0.2);

        leftSplitPane.setDividerLocation(430);
        leftSplitPane.setToolTipText("");
        leftSplitPane.setLastDividerLocation(430);
        leftSplitPane.setPreferredSize(new java.awt.Dimension(530, 25));
        mainSplitPane.setLeftComponent(leftSplitPane);

        rightSplitPane.setDividerLocation(400);
        rightSplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        rightSplitPane.setResizeWeight(0.5);
        mainSplitPane.setRightComponent(rightSplitPane);

        add(mainSplitPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSplitPane leftSplitPane;
    private javax.swing.JSplitPane mainSplitPane;
    private javax.swing.JSplitPane rightSplitPane;
    // End of variables declaration//GEN-END:variables
}
