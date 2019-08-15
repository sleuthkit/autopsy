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

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.corecomponents.DataContentPanel;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Create a dialog for displaying the file discovery tool
 */
class FileDiscoveryDialog extends javax.swing.JDialog {

    private static final long serialVersionUID = 1L;
    private final FileSearchPanel fileSearchPanel;
    private final GroupListPanel groupListPanel;
    private final DataContentPanel dataContentPanel;
    private final ResultsPanel resultsPanel;
    private final ExplorerManager explorerManager;

    /**
     * Creates new form FileDiscoveryDialog
     */
    FileDiscoveryDialog(java.awt.Frame parent, boolean modal, SleuthkitCase caseDb, EamDb centralRepoDb) {
        super((JFrame) WindowManager.getDefault().getMainWindow(), Bundle.FileSearchPanel_dialogTitle_text(), modal);
        initComponents();
        explorerManager = new ExplorerManager();
        fileSearchPanel = new FileSearchPanel(caseDb, centralRepoDb);
        dataContentPanel = DataContentPanel.createInstance();
        resultsPanel = new ResultsPanel(explorerManager, centralRepoDb);
        DiscoveryEvents.getDiscoveryEventBus().register(resultsPanel);
        groupListPanel = new GroupListPanel();
        DiscoveryEvents.getDiscoveryEventBus().register(groupListPanel);
        leftSplitPane.setLeftComponent(fileSearchPanel);
        leftSplitPane.setRightComponent(groupListPanel);
        rightSplitPane.setTopComponent(resultsPanel);
        rightSplitPane.setBottomComponent(dataContentPanel);
        resultsPanel.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    SwingUtilities.invokeLater(() -> {
                        AbstractFile file = resultsPanel.getSelectedFile();
                        if (file != null) {
                            System.out.println("setup tabs with file");
                            dataContentPanel.setNode(new TableFilterNode(new FileNode(file), false));
                        } else {
                            System.out.println("setup tabs");
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
     * Show the dialog
     */
    void display() {
        this.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        setVisible(true);
    }

    @Override
    public void dispose() {
        fileSearchPanel.cancelSearch();
        FileSearch.clearCache();
        DiscoveryEvents.getDiscoveryEventBus().unregister(groupListPanel);
        DiscoveryEvents.getDiscoveryEventBus().unregister(resultsPanel);
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

        leftSplitPane.setDividerLocation(430);
        leftSplitPane.setToolTipText("");
        leftSplitPane.setLastDividerLocation(430);
        leftSplitPane.setPreferredSize(new java.awt.Dimension(530, 25));
        mainSplitPane.setLeftComponent(leftSplitPane);

        rightSplitPane.setDividerLocation(400);
        rightSplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        rightSplitPane.setResizeWeight(0.5);
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
