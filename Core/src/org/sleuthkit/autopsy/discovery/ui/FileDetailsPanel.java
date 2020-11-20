/*
 * Autopsy
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.ui;

import com.google.common.eventbus.Subscribe;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.corecomponents.DataContentPanel;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.autopsy.discovery.search.DiscoveryEventUtils;
import org.sleuthkit.autopsy.modules.hashdatabase.AddContentToHashDbAction;
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel to display the details of the selected result.
 */
final class FileDetailsPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    private final DataContentPanel dataContentPanel;
    private final DefaultListModel<AbstractFile> instancesListModel = new DefaultListModel<>();
    private final ListSelectionListener listener;

    /**
     * Creates new form DetailsPanel.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    FileDetailsPanel() {
        initComponents();
        dataContentPanel = DataContentPanel.createInstance();
        detailsSplitPane.setBottomComponent(dataContentPanel);
        //Add the context menu when right clicking
        instancesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    instancesList.setSelectedIndex(instancesList.locationToIndex(e.getPoint()));
                    Set<AbstractFile> files = new HashSet<>();
                    files.add(instancesList.getSelectedValue());
                    JPopupMenu menu = new JPopupMenu();
                    menu.add(new ViewContextAction(Bundle.ResultsPanel_viewFileInDir_name(), instancesList.getSelectedValue()));
                    menu.add(new ExternalViewerAction(Bundle.ResultsPanel_openInExternalViewer_name(), new FileNode(instancesList.getSelectedValue())));
                    menu.add(ViewFileInTimelineAction.createViewFileAction(instancesList.getSelectedValue()));
                    menu.add(new DiscoveryExtractAction(files));
                    menu.add(AddContentTagAction.getInstance().getMenuForContent(files));
                    menu.add(DeleteFileContentTagAction.getInstance().getMenuForFiles(files));
                    menu.add(AddContentToHashDbAction.getInstance().getMenuForFiles(files));
                    menu.show(instancesList, e.getPoint().x, e.getPoint().y);
                }
            }
        });
        listener = new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    AbstractFile file = getSelectedFile();
                    if (file != null) {
                        dataContentPanel.setNode(new TableFilterNode(new FileNode(file), false));
                    } else {
                        dataContentPanel.setNode(null);
                    }
                }
            }
        };
        instancesList.addListSelectionListener(listener);
    }

    /**
     * Clears the instances list in response to the clear instance selection
     * event.
     *
     * @param clearEvent The ClearInstanceSelectionEvent which has been
     *                   received.
     */
    @Subscribe
    void handleClearSelectionListener(DiscoveryEventUtils.ClearInstanceSelectionEvent clearEvent) {
        SwingUtilities.invokeLater(() -> {
            instancesList.clearSelection();
        });
    }

    /**
     * Populate the instances list.
     *
     * @param populateEvent The PopulateInstancesListEvent which indicates the
     *                      instances list should be populated
     */
    @Subscribe
    void handlePopulateInstancesListEvent(DiscoveryEventUtils.PopulateInstancesListEvent populateEvent) {
        List<AbstractFile> files = populateEvent.getInstances();
        SwingUtilities.invokeLater(() -> {
            if (files.isEmpty()) {
                //if there are no files currently remove the current items without removing listener to cause content viewer to reset
                instancesListModel.removeAllElements();
                //send fade out event
                DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.DetailsVisibleEvent(false));
            } else {
                //remove listener so content viewer node is not set multiple times
                instancesList.removeListSelectionListener(listener);
                instancesListModel.removeAllElements();
                for (AbstractFile file : files) {
                    instancesListModel.addElement(file);
                }
                //add listener back to allow selection of first index to cause content viewer node to be set
                instancesList.addListSelectionListener(listener);
                if (!instancesListModel.isEmpty()) {
                    instancesList.setSelectedIndex(0);
                }
                //send fade in event
                DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.DetailsVisibleEvent(true));
            }
        });
    }

    /**
     * Get the AbstractFile for the item currently selected in the instances
     * list.
     *
     * @return The AbstractFile which is currently selected.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    AbstractFile getSelectedFile() {
        if (instancesList.getSelectedIndex() == -1) {
            return null;
        } else {
            return instancesListModel.getElementAt(instancesList.getSelectedIndex());
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

        detailsSplitPane = new javax.swing.JSplitPane();
        javax.swing.JPanel instancesPanel = new javax.swing.JPanel();
        javax.swing.JScrollPane instancesScrollPane = new javax.swing.JScrollPane();
        instancesList = new javax.swing.JList<>();

        detailsSplitPane.setDividerLocation(80);
        detailsSplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        detailsSplitPane.setMinimumSize(new java.awt.Dimension(200, 0));
        detailsSplitPane.setPreferredSize(new java.awt.Dimension(700, 500));

        instancesPanel.setMinimumSize(new java.awt.Dimension(0, 60));
        instancesPanel.setPreferredSize(new java.awt.Dimension(700, 80));

        instancesScrollPane.setPreferredSize(new java.awt.Dimension(775, 60));

        instancesList.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(FileDetailsPanel.class, "FileDetailsPanel.instancesList.border.title"))); // NOI18N
        instancesList.setModel(instancesListModel);
        instancesList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        instancesList.setCellRenderer(new InstancesCellRenderer());
        instancesList.setVisibleRowCount(2);
        instancesScrollPane.setViewportView(instancesList);

        javax.swing.GroupLayout instancesPanelLayout = new javax.swing.GroupLayout(instancesPanel);
        instancesPanel.setLayout(instancesPanelLayout);
        instancesPanelLayout.setHorizontalGroup(
            instancesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 775, Short.MAX_VALUE)
            .addGroup(instancesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(instancesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        instancesPanelLayout.setVerticalGroup(
            instancesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 79, Short.MAX_VALUE)
            .addGroup(instancesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, instancesPanelLayout.createSequentialGroup()
                    .addGap(0, 0, 0)
                    .addComponent(instancesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 79, Short.MAX_VALUE)))
        );

        detailsSplitPane.setTopComponent(instancesPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 777, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(detailsSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 402, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(detailsSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSplitPane detailsSplitPane;
    private javax.swing.JList<AbstractFile> instancesList;
    // End of variables declaration//GEN-END:variables

    /**
     * Cell renderer for the instances list.
     */
    private class InstancesCellRenderer extends DefaultListCellRenderer {

        private static final long serialVersionUID = 1L;

        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String name = "";
            if (value instanceof AbstractFile) {
                AbstractFile file = (AbstractFile) value;
                try {
                    name = file.getUniquePath();
                } catch (TskCoreException ingored) {
                    name = file.getParentPath() + "/" + file.getName();
                }

            }
            setText(name);
            return this;
        }

    }
}
