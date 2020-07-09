/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2018 Basis Technology Corp.
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

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import javax.swing.JOptionPane;
import javax.swing.ListSelectionModel;
import javax.swing.SortOrder;
import javax.swing.SwingWorker;
import org.apache.commons.lang3.StringUtils;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.NodeEvent;
import org.openide.nodes.NodeListener;
import org.openide.nodes.NodeMemberEvent;
import org.openide.nodes.NodeReorderEvent;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import static org.sleuthkit.autopsy.corecomponents.Bundle.*;
import org.sleuthkit.autopsy.corecomponents.ResultViewerPersistence.SortCriterion;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.guiutils.WrapLayout;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A thumbnail result viewer, with paging support, that displays the children of
 * the given root node using an IconView. The paging is intended to reduce
 * memory footprint by loading no more than two humdred images at a time.
 *
 * Instances of this class should use the explorer manager of an ancestor top
 * component to connect the lookups of the nodes displayed in the IconView to
 * the actions global context. The explorer manager can be supplied during
 * construction, but the typical use case is for the result viewer to find the
 * ancestor top component's explorer manager at runtime.
 */
@ServiceProvider(service = DataResultViewer.class)
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public final class DataResultViewerThumbnail extends AbstractDataResultViewer {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DataResultViewerThumbnail.class.getName());
    private final PageUpdater pageUpdater = new PageUpdater();
    private TableFilterNode rootNode;
    private ThumbnailViewChildren rootNodeChildren;
    private NodeSelectionListener selectionListener;
    private int currentPage;
    private int totalPages;
    private int currentPageImages;
    private int thumbSize = ImageUtils.ICON_SIZE_MEDIUM;

    /**
     * Constructs a thumbnail result viewer, with paging support, that displays
     * the children of the given root node using an IconView. The viewer should
     * have an ancestor top component to connect the lookups of the nodes
     * displayed in the IconView to the actions global context. The explorer
     * manager will be discovered at runtime.
     */
    public DataResultViewerThumbnail() {
        this(null);
    }

    /**
     * Constructs a thumbnail result viewer, with paging support, that displays
     * the children of the given root node using an IconView. The viewer should
     * have an ancestor top component to connect the lookups of the nodes
     * displayed in the IconView to the actions global context.
     *
     * @param explorerManager The explorer manager of the ancestor top
     *                        component.
     */
    @NbBundle.Messages({
        "DataResultViewerThumbnail.thumbnailSizeComboBox.small=Small Thumbnails",
        "DataResultViewerThumbnail.thumbnailSizeComboBox.medium=Medium Thumbnails",
        "DataResultViewerThumbnail.thumbnailSizeComboBox.large=Large Thumbnails"
    })
    public DataResultViewerThumbnail(ExplorerManager explorerManager) {
        super(explorerManager);
        initComponents();
        iconView.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        thumbnailSizeComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{
            Bundle.DataResultViewerThumbnail_thumbnailSizeComboBox_small(),
            Bundle.DataResultViewerThumbnail_thumbnailSizeComboBox_medium(),
            Bundle.DataResultViewerThumbnail_thumbnailSizeComboBox_large()}));
        thumbnailSizeComboBox.setSelectedIndex(1);
        currentPage = -1;
        totalPages = 0;
        currentPageImages = 0;
        
        // The GUI builder is using FlowLayout therefore this change so have no
        // impact on the initally designed layout.  This change will just effect
        // how the components are laid out as size of the window changes.
        buttonBarPanel.setLayout(new WrapLayout());
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        buttonBarPanel = new javax.swing.JPanel();
        pagesPanel = new javax.swing.JPanel();
        pageNumberPane = new javax.swing.JPanel();
        pageLabel = new javax.swing.JLabel();
        pageNumLabel = new javax.swing.JLabel();
        pageButtonPanel = new javax.swing.JPanel();
        pagesLabel = new javax.swing.JLabel();
        pagePrevButton = new javax.swing.JButton();
        pageNextButton = new javax.swing.JButton();
        pageGotoPane = new javax.swing.JPanel();
        goToPageLabel = new javax.swing.JLabel();
        goToPageField = new javax.swing.JTextField();
        imagePane = new javax.swing.JPanel();
        imagesLabel = new javax.swing.JLabel();
        imagesRangeLabel = new javax.swing.JLabel();
        thumbnailSizeComboBox = new javax.swing.JComboBox<>();
        sortPane = new javax.swing.JPanel();
        sortLabel = new javax.swing.JLabel();
        sortButton = new javax.swing.JButton();
        filePathLabel = new javax.swing.JLabel();
        iconView = new org.openide.explorer.view.IconView();

        setLayout(new java.awt.BorderLayout());

        buttonBarPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

        pagesPanel.setLayout(new java.awt.GridBagLayout());

        pageNumberPane.setLayout(new java.awt.GridBagLayout());

        pageLabel.setText(org.openide.util.NbBundle.getMessage(DataResultViewerThumbnail.class, "DataResultViewerThumbnail.pageLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 9);
        pageNumberPane.add(pageLabel, gridBagConstraints);

        pageNumLabel.setText(org.openide.util.NbBundle.getMessage(DataResultViewerThumbnail.class, "DataResultViewerThumbnail.pageNumLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 15);
        pageNumberPane.add(pageNumLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        pagesPanel.add(pageNumberPane, gridBagConstraints);

        buttonBarPanel.add(pagesPanel);

        pageButtonPanel.setLayout(new java.awt.GridBagLayout());

        pagesLabel.setText(org.openide.util.NbBundle.getMessage(DataResultViewerThumbnail.class, "DataResultViewerThumbnail.pagesLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 9);
        pageButtonPanel.add(pagesLabel, gridBagConstraints);

        pagePrevButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_back.png"))); // NOI18N
        pagePrevButton.setText(org.openide.util.NbBundle.getMessage(DataResultViewerThumbnail.class, "DataResultViewerThumbnail.pagePrevButton.text")); // NOI18N
        pagePrevButton.setBorder(null);
        pagePrevButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_back_disabled.png"))); // NOI18N
        pagePrevButton.setFocusable(false);
        pagePrevButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        pagePrevButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        pagePrevButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_back_hover.png"))); // NOI18N
        pagePrevButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        pagePrevButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pagePrevButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        pageButtonPanel.add(pagePrevButton, gridBagConstraints);

        pageNextButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_forward.png"))); // NOI18N
        pageNextButton.setText(org.openide.util.NbBundle.getMessage(DataResultViewerThumbnail.class, "DataResultViewerThumbnail.pageNextButton.text")); // NOI18N
        pageNextButton.setBorder(null);
        pageNextButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_forward_disabled.png"))); // NOI18N
        pageNextButton.setFocusable(false);
        pageNextButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        pageNextButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        pageNextButton.setMaximumSize(new java.awt.Dimension(27, 23));
        pageNextButton.setMinimumSize(new java.awt.Dimension(27, 23));
        pageNextButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_forward_hover.png"))); // NOI18N
        pageNextButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        pageNextButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pageNextButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 15);
        pageButtonPanel.add(pageNextButton, gridBagConstraints);

        buttonBarPanel.add(pageButtonPanel);

        pageGotoPane.setLayout(new java.awt.GridBagLayout());

        goToPageLabel.setText(org.openide.util.NbBundle.getMessage(DataResultViewerThumbnail.class, "DataResultViewerThumbnail.goToPageLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 9);
        pageGotoPane.add(goToPageLabel, gridBagConstraints);

        goToPageField.setText(org.openide.util.NbBundle.getMessage(DataResultViewerThumbnail.class, "DataResultViewerThumbnail.goToPageField.text")); // NOI18N
        goToPageField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                goToPageFieldActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.ipadx = 75;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 15);
        pageGotoPane.add(goToPageField, gridBagConstraints);

        buttonBarPanel.add(pageGotoPane);

        imagePane.setLayout(new java.awt.GridBagLayout());

        imagesLabel.setText(org.openide.util.NbBundle.getMessage(DataResultViewerThumbnail.class, "DataResultViewerThumbnail.imagesLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 9);
        imagePane.add(imagesLabel, gridBagConstraints);

        imagesRangeLabel.setText(org.openide.util.NbBundle.getMessage(DataResultViewerThumbnail.class, "DataResultViewerThumbnail.imagesRangeLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 15);
        imagePane.add(imagesRangeLabel, gridBagConstraints);

        buttonBarPanel.add(imagePane);

        thumbnailSizeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                thumbnailSizeComboBoxActionPerformed(evt);
            }
        });
        buttonBarPanel.add(thumbnailSizeComboBox);

        sortPane.setLayout(new java.awt.GridBagLayout());

        sortLabel.setText(org.openide.util.NbBundle.getMessage(DataResultViewerThumbnail.class, "DataResultViewerThumbnail.sortLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weighty = 1.0;
        sortPane.add(sortLabel, gridBagConstraints);

        sortButton.setText(org.openide.util.NbBundle.getMessage(DataResultViewerThumbnail.class, "DataResultViewerThumbnail.sortButton.text")); // NOI18N
        sortButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sortButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 0, 9);
        sortPane.add(sortButton, gridBagConstraints);

        buttonBarPanel.add(sortPane);

        add(buttonBarPanel, java.awt.BorderLayout.NORTH);

        filePathLabel.setText(org.openide.util.NbBundle.getMessage(DataResultViewerThumbnail.class, "DataResultViewerThumbnail.filePathLabel.text")); // NOI18N
        add(filePathLabel, java.awt.BorderLayout.SOUTH);
        add(iconView, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void pagePrevButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pagePrevButtonActionPerformed
        previousPage();
    }//GEN-LAST:event_pagePrevButtonActionPerformed

    private void pageNextButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pageNextButtonActionPerformed
        nextPage();
    }//GEN-LAST:event_pageNextButtonActionPerformed

    private void goToPageFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_goToPageFieldActionPerformed
        goToPage(goToPageField.getText());
    }//GEN-LAST:event_goToPageFieldActionPerformed

    private void thumbnailSizeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_thumbnailSizeComboBoxActionPerformed
        int newIconSize;
        switch (thumbnailSizeComboBox.getSelectedIndex()) {
            case 0:
                newIconSize = ImageUtils.ICON_SIZE_SMALL;
                break;
            case 2:
                newIconSize = ImageUtils.ICON_SIZE_LARGE;
                break;
            case 1:
            default:
                newIconSize = ImageUtils.ICON_SIZE_MEDIUM;   //default size
                break;
        }

        if (thumbSize != newIconSize) {
            thumbSize = newIconSize;
            Node root = this.getExplorerManager().getRootContext();
            ((ThumbnailViewChildren) root.getChildren()).setThumbsSize(thumbSize);

            // Temporarily set the explored context to the root, instead of a child node.
            // This is a workaround hack to convince org.openide.explorer.ExplorerManager to
            // update even though the new and old Node values are identical. This in turn
            // will cause the entire view to update completely. After this we 
            // immediately set the node back to the current child by calling switchPage().        
            this.getExplorerManager().setExploredContext(root);
            switchPage();
        }
    }//GEN-LAST:event_thumbnailSizeComboBoxActionPerformed

    private void sortButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sortButtonActionPerformed
        List<Node.Property<?>> childProperties = ResultViewerPersistence.getAllChildProperties(this.getExplorerManager().getRootContext(), 100);
        SortChooser sortChooser = new SortChooser(childProperties, ResultViewerPersistence.loadSortCriteria(rootNode));
        DialogDescriptor dialogDescriptor = new DialogDescriptor(sortChooser, sortChooser.getDialogTitle());
        Dialog createDialog = DialogDisplayer.getDefault().createDialog(dialogDescriptor);
        createDialog.setVisible(true);
        final Object dialogReturnValue = dialogDescriptor.getValue();
        if (DialogDescriptor.OK_OPTION == dialogReturnValue) {
            //apply new sort
            List<SortCriterion> criteria = sortChooser.getCriteria();
            final Preferences preferences = NbPreferences.forModule(DataResultViewerThumbnail.class);

            Map<Node.Property<?>, SortCriterion> criteriaMap = criteria.stream()
                    .collect(Collectors.toMap(SortCriterion::getProperty,
                            Function.identity(),
                            (u, v) -> u)); //keep first criteria if property is selected multiple times.

            //store the sorting information
            int numProperties = childProperties.size();
            for (int i = 0; i < numProperties; i++) {
                Node.Property<?> prop = childProperties.get(i);
                String propName = prop.getName();
                SortCriterion criterion = criteriaMap.get(prop);
                final String columnSortOrderKey = ResultViewerPersistence.getColumnSortOrderKey(rootNode, propName);
                final String columnSortRankKey = ResultViewerPersistence.getColumnSortRankKey(rootNode, propName);

                if (criterion != null) {
                    preferences.putBoolean(columnSortOrderKey, criterion.getSortOrder() == SortOrder.ASCENDING);
                    preferences.putInt(columnSortRankKey, criterion.getSortRank() + 1);
                } else {
                    preferences.remove(columnSortOrderKey);
                    preferences.remove(columnSortRankKey);
                }
            }
            setNode(rootNode); //this is just to force a refresh
        }
    }//GEN-LAST:event_sortButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel buttonBarPanel;
    private javax.swing.JLabel filePathLabel;
    private javax.swing.JTextField goToPageField;
    private javax.swing.JLabel goToPageLabel;
    private org.openide.explorer.view.IconView iconView;
    private javax.swing.JPanel imagePane;
    private javax.swing.JLabel imagesLabel;
    private javax.swing.JLabel imagesRangeLabel;
    private javax.swing.JPanel pageButtonPanel;
    private javax.swing.JPanel pageGotoPane;
    private javax.swing.JLabel pageLabel;
    private javax.swing.JButton pageNextButton;
    private javax.swing.JLabel pageNumLabel;
    private javax.swing.JPanel pageNumberPane;
    private javax.swing.JButton pagePrevButton;
    private javax.swing.JLabel pagesLabel;
    private javax.swing.JPanel pagesPanel;
    private javax.swing.JButton sortButton;
    private javax.swing.JLabel sortLabel;
    private javax.swing.JPanel sortPane;
    private javax.swing.JComboBox<String> thumbnailSizeComboBox;
    // End of variables declaration//GEN-END:variables

    @Override
    public boolean isSupported(Node selectedNode) {
        return (selectedNode != null);
    }

    @Override
    public void setNode(Node givenNode) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        if (selectionListener == null) {
            this.getExplorerManager().addPropertyChangeListener(new NodeSelectionListener());
        }
        if (rootNodeChildren != null) {
            rootNodeChildren.cancelLoadingThumbnails();
        }
        try {
            // There is an issue with ThumbnailViewChildren
            // addNotify, that it's call to getChildren.getNodes() does not cause the 
            // children nodes to be created.  Adding a call to getChildren.getNodesCount()
            // here will assure that the children nodes are created particularly in the
            // case where the DataResultViewerThumbnail stands along from the 
            // DataResultViewer.  See DataResultViewer setNode for more information.
            if (givenNode != null && givenNode.getChildren().getNodesCount() > 0) {
                rootNode = (TableFilterNode) givenNode;
                /*
                 * Wrap the given node in a ThumbnailViewChildren that will
                 * produce ThumbnailPageNodes with ThumbnailViewNode children
                 * from the child nodes of the given node.
                 */
                rootNodeChildren = new ThumbnailViewChildren(givenNode, thumbSize);
                final Node root = new AbstractNode(rootNodeChildren);

                pageUpdater.setRoot(root);
                root.addNodeListener(pageUpdater);
                this.getExplorerManager().setRootContext(root);
            } else {
                rootNode = null;
                rootNodeChildren = null;
                Node emptyNode = new AbstractNode(Children.LEAF);
                this.getExplorerManager().setRootContext(emptyNode);
                iconView.setBackground(Color.BLACK);
            }
        } finally {
            this.setCursor(null);
        }
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(this.getClass(), "DataResultViewerThumbnail.title");
    }

    @Override
    public DataResultViewer createInstance() {
        return new DataResultViewerThumbnail();
    }

    @Override
    public void resetComponent() {
        super.resetComponent();
        this.totalPages = 0;
        this.currentPage = -1;
        currentPageImages = 0;
        updateControls();
    }

    @Override
    public void clearComponent() {
        this.iconView.removeAll();
        this.iconView = null;
        super.clearComponent();
    }

    private void nextPage() {
        if (currentPage < totalPages) {
            currentPage++;
            switchPage();
        }
    }

    private void previousPage() {
        if (currentPage > 1) {
            currentPage--;
            switchPage();
        }
    }

    private void goToPage(String pageNumText) {
        int newPage;
        try {
            newPage = Integer.parseInt(pageNumText);
        } catch (NumberFormatException e) {
            //ignore input
            return;
        }

        if (newPage > totalPages || newPage < 1) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(this.getClass(), "DataResultViewerThumbnail.goToPageTextField.msgDlg", totalPages),
                    NbBundle.getMessage(this.getClass(), "DataResultViewerThumbnail.goToPageTextField.err"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        currentPage = newPage;
        switchPage();
    }

    private void switchPage() {

        EventQueue.invokeLater(() -> {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        });

        //Note the nodes factories are likely creating nodes in EDT anyway, but worker still helps 
        new SwingWorker<Object, Void>() {
            private ProgressHandle progress;

            @Override
            protected Object doInBackground() throws Exception {
                pagePrevButton.setEnabled(false);
                pageNextButton.setEnabled(false);
                goToPageField.setEnabled(false);
                progress = ProgressHandle.createHandle(
                        NbBundle.getMessage(this.getClass(), "DataResultViewerThumbnail.genThumbs"));
                progress.start();
                progress.switchToIndeterminate();
                ExplorerManager explorerManager = DataResultViewerThumbnail.this.getExplorerManager();
                Node root = explorerManager.getRootContext();
                Node pageNode = root.getChildren().getNodeAt(currentPage - 1);
                explorerManager.setExploredContext(pageNode);
                currentPageImages = pageNode.getChildren().getNodesCount();
                return null;
            }

            @Override
            protected void done() {
                progress.finish();
                setCursor(null);
                updateControls();
                // see if any exceptions were thrown
                try {
                    get();
                } catch (InterruptedException | ExecutionException ex) {
                    NotifyDescriptor d
                            = new NotifyDescriptor.Message(
                                    NbBundle.getMessage(this.getClass(), "DataResultViewerThumbnail.switchPage.done.errMsg",
                                            ex.getMessage()),
                                    NotifyDescriptor.ERROR_MESSAGE);
                    DialogDisplayer.getDefault().notify(d);
                    logger.log(Level.SEVERE, "Error making thumbnails: {0}", ex.getMessage()); //NON-NLS
                }
                catch (java.util.concurrent.CancellationException ex) {
                    // catch and ignore if we were cancelled
                }
            }
        }.execute();

    }

    @NbBundle.Messages({
        "# {0} - sort criteria", "DataResultViewerThumbnail.sortLabel.textTemplate=Sorted by: {0}",
        "DataResultViewerThumbnail.sortLabel.text=Sorted by: ---"})
    private void updateControls() {
        if (totalPages == 0) {
            pagePrevButton.setEnabled(false);
            pageNextButton.setEnabled(false);
            goToPageField.setEnabled(false);
            pageNumLabel.setText("");
            imagesRangeLabel.setText("");
            thumbnailSizeComboBox.setEnabled(false);
            sortButton.setEnabled(false);
            sortLabel.setText(DataResultViewerThumbnail_sortLabel_text());

        } else {
            pageNumLabel.setText(NbBundle.getMessage(this.getClass(), "DataResultViewerThumbnail.pageNumbers.curOfTotal",
                    Integer.toString(currentPage), Integer.toString(totalPages)));
            final int imagesFrom = (currentPage - 1) * ThumbnailViewChildren.IMAGES_PER_PAGE + 1;
            final int imagesTo = currentPageImages + (currentPage - 1) * ThumbnailViewChildren.IMAGES_PER_PAGE;
            imagesRangeLabel.setText(imagesFrom + "-" + imagesTo);

            pageNextButton.setEnabled(!(currentPage == totalPages));
            pagePrevButton.setEnabled(!(currentPage == 1));
            goToPageField.setEnabled(totalPages > 1);
            sortButton.setEnabled(true);
            thumbnailSizeComboBox.setEnabled(true);
            if (rootNode != null) {
                String sortString = ResultViewerPersistence.loadSortCriteria(rootNode).stream()
                        .map(SortCriterion::toString)
                        .collect(Collectors.joining(" "));
                sortString = StringUtils.defaultIfBlank(sortString, "---");
                sortLabel.setText(Bundle.DataResultViewerThumbnail_sortLabel_textTemplate(sortString));
            } else {
                sortLabel.setText(DataResultViewerThumbnail_sortLabel_text());
            }
        }
    }

    /**
     * Listens for root change updates and updates the paging controls
     */
    private class PageUpdater implements NodeListener {

        private Node root;

        void setRoot(Node root) {
            this.root = root;
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
        }

        @Override
        public void childrenAdded(NodeMemberEvent nme) {
            totalPages = root.getChildren().getNodesCount();

            if (totalPages == 0) {
                currentPage = -1;
                updateControls();
                return;
            }

            if (currentPage == -1 || currentPage > totalPages) {
                currentPage = 1;
            }

            //force load the curPage node
            final Node pageNode = root.getChildren().getNodeAt(currentPage - 1);

            //em.setSelectedNodes(new Node[]{pageNode});
            if (pageNode != null) {
                pageNode.addNodeListener(new NodeListener() {
                    @Override
                    public void childrenAdded(NodeMemberEvent nme) {
                        currentPageImages = pageNode.getChildren().getNodesCount();
                        updateControls();
                    }

                    @Override
                    public void childrenRemoved(NodeMemberEvent nme) {
                        currentPageImages = 0;
                        updateControls();
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
                });

                DataResultViewerThumbnail.this.getExplorerManager().setExploredContext(pageNode);
            }

            updateControls();
        }

        @Override
        public void childrenRemoved(NodeMemberEvent nme) {
            totalPages = 0;
            currentPage = -1;
            updateControls();
        }

        @Override
        public void childrenReordered(NodeReorderEvent nre) {
        }

        @Override
        public void nodeDestroyed(NodeEvent ne) {
        }
    }

    private class NodeSelectionListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals(ExplorerManager.PROP_SELECTED_NODES)) {
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                try {
                    Node[] selectedNodes = DataResultViewerThumbnail.this.getExplorerManager().getSelectedNodes();
                    if (selectedNodes.length == 1) {
                        AbstractFile af = selectedNodes[0].getLookup().lookup(AbstractFile.class);
                        if (af == null) {
                            filePathLabel.setText("");
                        } else {
                            try {
                                String uPath = af.getUniquePath();
                                filePathLabel.setText(uPath);
                                filePathLabel.setToolTipText(uPath);
                            } catch (TskCoreException e) {
                                logger.log(Level.WARNING, "Could not get unique path for content: {0}", af.getName()); //NON-NLS
                            }
                        }
                    } else {
                        filePathLabel.setText("");
                    }
                } finally {
                    setCursor(null);
                }
            }
        }
    } 
}
