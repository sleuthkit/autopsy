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

import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.openide.util.NbBundle;
import org.openide.windows.Mode;
import org.openide.windows.RetainLocation;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.corecomponents.DataContentPanel;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.datamodel.AbstractFile;

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

    /**
     * Creates new form FileDiscoveryDialog
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    DiscoveryTopComponent() {
        initComponents();
        setName(Bundle.DiscoveryTopComponent_name());
        fileSearchPanel = new FileSearchPanel();
        dataContentPanel = DataContentPanel.createInstance();
        resultsPanel = new ResultsPanel();
        groupListPanel = new GroupListPanel();
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
    }

    /**
     * Open the instance of the DiscoveryTopComponent which exists.
     */
    static void openTopComponent() {
        final DiscoveryTopComponent tc = (DiscoveryTopComponent) WindowManager.getDefault().findTopComponent(PREFERRED_ID);
        if (tc != null) {
            WindowManager.getDefault().isTopComponentFloating(tc);
            if (tc.isOpened() == false) {
                Mode mode = WindowManager.getDefault().findMode("discovery"); // NON-NLS
                if (mode != null) {
                    mode.dockInto(tc);
                }
                tc.open();
                tc.updateSearchSettings();
            }
            tc.toFront();

        }
    }

    /**
     * Get the current DiscoveryTopComponent if it is open.
     *
     * @return The open DiscoveryTopComponent or null if it has not been opened.
     */
    public static DiscoveryTopComponent getTopComponent() {
        return (DiscoveryTopComponent) WindowManager.getDefault().findTopComponent(PREFERRED_ID);
    }

    /**
     * Reset the top component so it isn't displaying any results.
     */
    public void resetTopComponent() {
        resultsPanel.resetResultViewer();
        groupListPanel.resetGroupList();
    }

    /**
     * Update the search settings to a default state.
     */
    private void updateSearchSettings() {
        fileSearchPanel.resetPanel();
        imagesButton.setSelected(true);
        imagesButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/tick.png")));
        imagesButton.setBackground(Color.blue);
        videosButton.setIcon(null);
        videosButton.setSelected(false);
        videosButton.setBackground(new Color(240, 240, 240));
        fileSearchPanel.setSelectedType(FileSearchData.FileType.IMAGE);
    }

    @Override
    public void componentOpened() {
        super.componentOpened();
        WindowManager.getDefault().setTopComponentFloating(this, true);
        DiscoveryEvents.getDiscoveryEventBus().register(this);
        DiscoveryEvents.getDiscoveryEventBus().register(resultsPanel);
        DiscoveryEvents.getDiscoveryEventBus().register(groupListPanel);
        DiscoveryEvents.getDiscoveryEventBus().register(fileSearchPanel);
    }

    @Override
    protected void componentClosed() {
        fileSearchPanel.cancelSearch();
        DiscoveryEvents.getDiscoveryEventBus().unregister(this);
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

        javax.swing.JSplitPane mainSplitPane = new javax.swing.JSplitPane();
        leftSplitPane = new javax.swing.JSplitPane();
        rightSplitPane = new javax.swing.JSplitPane();
        javax.swing.JPanel toolBarPanel = new javax.swing.JPanel();
        imagesButton = new javax.swing.JButton();
        videosButton = new javax.swing.JButton();

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

        org.openide.awt.Mnemonics.setLocalizedText(imagesButton, org.openide.util.NbBundle.getMessage(DiscoveryTopComponent.class, "DiscoveryTopComponent.imagesButton.text")); // NOI18N
        imagesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                imagesButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(videosButton, org.openide.util.NbBundle.getMessage(DiscoveryTopComponent.class, "DiscoveryTopComponent.videosButton.text")); // NOI18N
        videosButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                videosButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout toolBarPanelLayout = new javax.swing.GroupLayout(toolBarPanel);
        toolBarPanel.setLayout(toolBarPanelLayout);
        toolBarPanelLayout.setHorizontalGroup(
            toolBarPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(toolBarPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(imagesButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(videosButton)
                .addContainerGap(954, Short.MAX_VALUE))
        );
        toolBarPanelLayout.setVerticalGroup(
            toolBarPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(toolBarPanelLayout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addGroup(toolBarPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(imagesButton)
                    .addComponent(videosButton))
                .addGap(6, 6, 6))
        );

        add(toolBarPanel, java.awt.BorderLayout.PAGE_START);
    }// </editor-fold>//GEN-END:initComponents

    private void imagesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_imagesButtonActionPerformed
        imagesButton.setSelected(true);
        imagesButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/tick.png")));
        imagesButton.setBackground(Color.blue);
        videosButton.setIcon(null);
        videosButton.setSelected(false);
        videosButton.setBackground(new Color(240, 240, 240));
        fileSearchPanel.setSelectedType(FileSearchData.FileType.IMAGE);
    }//GEN-LAST:event_imagesButtonActionPerformed

    private void videosButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_videosButtonActionPerformed
        imagesButton.setSelected(false);
        imagesButton.setIcon(null);
        imagesButton.setBackground(new Color(240, 240, 240));
        videosButton.setSelected(true);
        videosButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/tick.png")));
        videosButton.setBackground(Color.blue);
        fileSearchPanel.setSelectedType(FileSearchData.FileType.VIDEO);
    }//GEN-LAST:event_videosButtonActionPerformed

    /**
     * Update the user interface in response to a search being cancelled.
     *
     * @param searchCancelledEvent The SearchCancelledEvent received.
     */
    @Subscribe
    void handleSearchCancelledEvent(DiscoveryEvents.SearchCancelledEvent searchCancelledEvent) {
        SwingUtilities.invokeLater(() -> {
            imagesButton.setEnabled(true);
            videosButton.setEnabled(true);
        });
    }

    /**
     * Update the user interface in response to a search being completed.
     *
     * @param searchCompletedEvent The SearchCompletedEvent received.
     */
    @Subscribe
    void handleSearchCompletedEvent(DiscoveryEvents.SearchCompleteEvent searchCompletedEvent) {
        SwingUtilities.invokeLater(() -> {
            imagesButton.setEnabled(true);
            videosButton.setEnabled(true);
        });
    }

    /**
     * Update the user interface in response to a search being started.
     *
     * @param searchStartedEvent The SearchStartedEvent received.
     */
    @Subscribe
    void handleSearchStartedEvent(DiscoveryEvents.SearchStartedEvent searchStartedEvent) {
        SwingUtilities.invokeLater(() -> {
            imagesButton.setEnabled(false);
            videosButton.setEnabled(false);
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton imagesButton;
    private javax.swing.JSplitPane leftSplitPane;
    private javax.swing.JSplitPane rightSplitPane;
    private javax.swing.JButton videosButton;
    // End of variables declaration//GEN-END:variables

}
