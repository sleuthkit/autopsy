/*
 * Autopsy Forensic Browser
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

import com.google.common.eventbus.Subscribe;
import javax.swing.JFrame;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Node;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.corecomponents.DataContentPanel;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerThumbnail;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;
import org.sleuthkit.autopsy.filequery.FileSearchData.FileType;
import org.sleuthkit.datamodel.SleuthkitCase;

class FileDiscoveryDialog extends javax.swing.JDialog {

    private static final long serialVersionUID = 1L;
    private final FileSearchPanel fileSearchPanel;
    private final GroupListPanel groupListPanel;
    private final DataContentPanel dataContentPanel;
    private final DataResultViewerThumbnail thumbnailViewer;
    private final DataResultViewerTable tableViewer;
    private final ExplorerManager explorerManager;

    /**
     * Creates new form FileDiscoveryDialog
     */
    FileDiscoveryDialog(java.awt.Frame parent, boolean modal, SleuthkitCase caseDb, EamDb centralRepoDb) {
        super((JFrame) WindowManager.getDefault().getMainWindow(), Bundle.FileSearchPanel_dialogTitle_text(), modal);
        initComponents();
        explorerManager = new ExplorerManager();
        //create results callback and pass it into the search panel
        fileSearchPanel = new FileSearchPanel(caseDb, centralRepoDb);
        groupListPanel = new GroupListPanel();
        DiscoveryEvents.getDiscoveryEventBus().register(groupListPanel);
        dataContentPanel = DataContentPanel.createInstance();
        thumbnailViewer = new DataResultViewerThumbnail(explorerManager);
        tableViewer = new DataResultViewerTable(explorerManager);
        leftSplitPane.setLeftComponent(fileSearchPanel);
        leftSplitPane.setRightComponent(groupListPanel);
        rightSplitPane.setTopComponent(tableViewer);
        rightSplitPane.setBottomComponent(dataContentPanel);
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
                if (selectedNodes.length == 1) {
                    dataContentPanel.setNode(selectedNodes[0]);
                } else {
                    dataContentPanel.setNode(null);
                }
            }
        });
    }

    /**
     * Show the dialog
     */
    void display() {
        this.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
//        runAnotherSearch = false;
        setVisible(true);
    }

    @Subscribe
    void handleGroupSelectedEvent(DiscoveryEvents.GroupSelectedEvent groupSelectedEvent) {
        if (groupSelectedEvent.getType() == FileType.IMAGE || groupSelectedEvent.getType() == FileType.VIDEO) {
            rightSplitPane.setTopComponent(thumbnailViewer);
            if (groupSelectedEvent.getFiles().size() > 0) {
                thumbnailViewer.setNode(new TableFilterNode(new DataResultFilterNode(new AbstractNode(new DiscoveryThumbnailChild(groupSelectedEvent.getFiles()))), true));
            } else {
                thumbnailViewer.setNode(new TableFilterNode(new DataResultFilterNode(Node.EMPTY), true));
            }
        } else {
            rightSplitPane.setTopComponent(tableViewer);
            if (groupSelectedEvent.getFiles().size() > 0) {
                tableViewer.setNode(new TableFilterNode(new DataResultFilterNode(new AbstractNode(new SearchChildren(false, groupSelectedEvent.getFiles()))), true));
            } else {
                tableViewer.setNode(new TableFilterNode(new DataResultFilterNode(Node.EMPTY), true));
            }
        }
    }

    @Subscribe
    void handleSearchStartedEvent(DiscoveryEvents.SearchStartedEvent searchStartedEvent) {
        if (searchStartedEvent.getType() == FileType.IMAGE || searchStartedEvent.getType() == FileType.VIDEO) {
            thumbnailViewer.setNode(new TableFilterNode(new DataResultFilterNode(Node.EMPTY), true));
            dataContentPanel.setNode(null);
        } else {
            tableViewer.setNode(new TableFilterNode(new DataResultFilterNode(Node.EMPTY), true));
            dataContentPanel.setNode(null);
        }
    }

    @Override
    public void dispose() {
        DiscoveryEvents.getDiscoveryEventBus().unregister(groupListPanel);
        DiscoveryEvents.getDiscoveryEventBus().unregister(this);
        super.dispose();
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

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setPreferredSize(new java.awt.Dimension(1100, 700));

        mainSplitPane.setDividerLocation(550);
        mainSplitPane.setResizeWeight(0.2);

        leftSplitPane.setDividerLocation(420);
        leftSplitPane.setToolTipText(org.openide.util.NbBundle.getMessage(FileDiscoveryDialog.class, "FileDiscoveryDialog.leftSplitPane.toolTipText")); // NOI18N
        leftSplitPane.setLastDividerLocation(420);
        leftSplitPane.setPreferredSize(new java.awt.Dimension(520, 25));
        mainSplitPane.setLeftComponent(leftSplitPane);

        rightSplitPane.setDividerLocation(400);
        rightSplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        rightSplitPane.setResizeWeight(0.7);
        mainSplitPane.setRightComponent(rightSplitPane);

        getContentPane().add(mainSplitPane, java.awt.BorderLayout.CENTER);

        pack();
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSplitPane leftSplitPane;
    private javax.swing.JSplitPane mainSplitPane;
    private javax.swing.JSplitPane rightSplitPane;
    // End of variables declaration//GEN-END:variables
}
