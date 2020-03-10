/*
 * Autopsy
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
import java.awt.Component;
import java.awt.Image;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionListener;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.autopsy.filequery.FileSearch.GroupKey;
import org.sleuthkit.autopsy.modules.hashdatabase.AddContentToHashDbAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.textsummarizer.TextSummary;

/**
 * Panel for displaying of file discovery results and handling the paging of
 * those results.
 */
public class ResultsPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private final static Logger logger = Logger.getLogger(ResultsPanel.class.getName());
    private final VideoThumbnailViewer videoThumbnailViewer;
    private final ImageThumbnailViewer imageThumbnailViewer;
    private final DocumentPreviewViewer documentPreviewViewer;
    private List<FileSearchFiltering.FileFilter> searchFilters;
    private FileSearch.AttributeType groupingAttribute;
    private FileGroup.GroupSortingAlgorithm groupSort;
    private FileSorter.SortingMethod fileSortMethod;
    private GroupKey selectedGroupKey;
    private int currentPage = 0;
    private int previousPageSize = 10;
    private FileSearchData.FileType resultType;
    private int groupSize = 0;
    private PageWorker pageWorker;
    private final List<SwingWorker<Void, Void>> resultContentWorkers = new ArrayList<>();
    private final DefaultListModel<AbstractFile> instancesListModel = new DefaultListModel<>();
    private ListSelectionListener listener = null;

    /**
     * Creates new form ResultsPanel.
     */
    @Messages({"ResultsPanel.viewFileInDir.name=View File in Directory",
        "ResultsPanel.openInExternalViewer.name=Open in External Viewer"})
    public ResultsPanel() {
        initComponents();
        imageThumbnailViewer = new ImageThumbnailViewer();
        videoThumbnailViewer = new VideoThumbnailViewer();
        documentPreviewViewer = new DocumentPreviewViewer();
        videoThumbnailViewer.addListSelectionListener((e) -> {
            if (resultType == FileSearchData.FileType.VIDEO) {
                if (!e.getValueIsAdjusting()) {
                    populateInstancesList();
                } else {
                    instancesList.clearSelection();
                }
            }
        });
        imageThumbnailViewer.addListSelectionListener((e) -> {
            if (resultType == FileSearchData.FileType.IMAGE) {
                if (!e.getValueIsAdjusting()) {
                    populateInstancesList();
                } else {
                    instancesList.clearSelection();
                }
            }
        });
        documentPreviewViewer.addListSelectionListener((e) -> {
            if (resultType == FileSearchData.FileType.DOCUMENTS) {
                if (!e.getValueIsAdjusting()) {
                    populateInstancesList();
                } else {
                    instancesList.clearSelection();
                }
            }
        });
        //Add the context menu when right clicking
        instancesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    SwingUtilities.invokeLater(() -> {
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
                    });
                }
            }
        });

    }

    /**
     * Add a list selection listener to the instances list.
     *
     * @param listener The ListSelectionListener to add to the instances list.
     */
    void addListSelectionListener(ListSelectionListener newListener) {
        instancesList.removeListSelectionListener(listener);
        listener = newListener;
        instancesList.addListSelectionListener(listener);
    }

    /**
     * Populate the instances list.
     */
    synchronized void populateInstancesList() {
        SwingUtilities.invokeLater(() -> {
            List<AbstractFile> files = getInstancesForSelected();
            if (files.isEmpty()) {
                //if there are no files currently remove the current items without removing listener to cause content viewer to reset
                instancesListModel.removeAllElements();
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
            }
        });
    }

    /**
     * Get the AbstractFile for the item currently selected in the instances
     * list.
     *
     * @return The AbstractFile which is currently selected.
     */
    synchronized AbstractFile getSelectedFile() {
        if (instancesList.getSelectedIndex() == -1) {
            return null;
        } else {
            return instancesListModel.getElementAt(instancesList.getSelectedIndex());
        }
    }

    /**
     * Get the list of all instances for the the currently selected item in the
     * results viewer area.
     *
     * @return The list of AbstractFiles which are represented by the item
     *         selected in the results viewer area.
     */
    private List<AbstractFile> getInstancesForSelected() {
        if (null != resultType) {
            switch (resultType) {
                case VIDEO:
                    return videoThumbnailViewer.getInstancesForSelected();
                case IMAGE:
                    return imageThumbnailViewer.getInstancesForSelected();
                case DOCUMENTS:
                    return documentPreviewViewer.getInstancesForSelected();
                default:
                    break;
            }
        }
        return new ArrayList<>();
    }

    /**
     * Subscribe and respond to PageRetrievedEvents.
     *
     * @param pageRetrievedEvent The PageRetrievedEvent received.
     */
    @Subscribe
    void handlePageRetrievedEvent(DiscoveryEventUtils.PageRetrievedEvent pageRetrievedEvent) {
        SwingUtilities.invokeLater(() -> {
            populateInstancesList();
            currentPage = pageRetrievedEvent.getPageNumber();
            updateControls();
            resetResultViewer();
            if (null != pageRetrievedEvent.getType()) {
                switch (pageRetrievedEvent.getType()) {
                    case IMAGE:
                        populateImageViewer(pageRetrievedEvent.getSearchResults());
                        resultsViewerPanel.add(imageThumbnailViewer);
                        break;
                    case VIDEO:
                        populateVideoViewer(pageRetrievedEvent.getSearchResults());
                        resultsViewerPanel.add(videoThumbnailViewer);
                        break;
                    case DOCUMENTS:
                        populateDocumentViewer(pageRetrievedEvent.getSearchResults());
                        resultsViewerPanel.add(documentPreviewViewer);
                        break;
                    default:
                        break;
                }
            }
            resultsViewerPanel.revalidate();
            resultsViewerPanel.repaint();
        }
        );
    }

    /**
     * Reset the result viewer and any associate workers to a default empty
     * state.
     */
    synchronized void resetResultViewer() {
        resultsViewerPanel.remove(imageThumbnailViewer);
        resultsViewerPanel.remove(videoThumbnailViewer);
        resultsViewerPanel.remove(documentPreviewViewer);
        //cancel any unfished thumb workers
        for (SwingWorker<Void, Void> thumbWorker : resultContentWorkers) {
            if (!thumbWorker.isDone()) {
                thumbWorker.cancel(true);
            }
        }
        //clear old thumbnails
        resultContentWorkers.clear();
        videoThumbnailViewer.clearViewer();
        imageThumbnailViewer.clearViewer();
        documentPreviewViewer.clearViewer();
    }

    /**
     * Populate the video thumbnail viewer, cancelling any thumbnails which are
     * currently being created first.
     *
     * @param files The list of ResultFiles to populate the video viewer with.
     */
    synchronized void populateVideoViewer(List<ResultFile> files) {
        for (ResultFile file : files) {
            VideoThumbnailWorker thumbWorker = new VideoThumbnailWorker(file);
            thumbWorker.execute();
            //keep track of thumb worker for possible cancelation 
            resultContentWorkers.add(thumbWorker);
        }
    }

    /**
     * Populate the image thumbnail viewer, cancelling any thumbnails which are
     * currently being created first.
     *
     * @param files The list of ResultFiles to populate the image viewer with.
     */
    synchronized void populateImageViewer(List<ResultFile> files) {
        for (ResultFile file : files) {
            ImageThumbnailWorker thumbWorker = new ImageThumbnailWorker(file);
            thumbWorker.execute();
            //keep track of thumb worker for possible cancelation 
            resultContentWorkers.add(thumbWorker);
        }
    }

    /**
     * Populate the document preview viewer, cancelling any content which is
     * currently being created first.
     *
     * @param files The list of ResultFiles to populate the image viewer with.
     */
    synchronized void populateDocumentViewer(List<ResultFile> files) {
        for (ResultFile file : files) {
            DocumentPreviewWorker documentWorker = new DocumentPreviewWorker(file);
            documentWorker.execute();
            //keep track of thumb worker for possible cancelation 
            resultContentWorkers.add(documentWorker);
        }
    }

    /**
     * Subscribe and respond to GroupSelectedEvents.
     *
     * @param groupSelectedEvent The GroupSelectedEvent received.
     */
    @Subscribe
    void handleGroupSelectedEvent(DiscoveryEventUtils.GroupSelectedEvent groupSelectedEvent) {
        SwingUtilities.invokeLater(() -> {
            searchFilters = groupSelectedEvent.getFilters();
            groupingAttribute = groupSelectedEvent.getGroupingAttr();
            groupSort = groupSelectedEvent.getGroupSort();
            fileSortMethod = groupSelectedEvent.getFileSort();
            selectedGroupKey = groupSelectedEvent.getGroupKey();
            resultType = groupSelectedEvent.getResultType();
            groupSize = groupSelectedEvent.getGroupSize();
            setPage(0);
        });
    }

    /**
     * Handle and respond to NoResultsEvent, updating the panel to reflect that
     * there were no results.
     *
     * @param noResultsEvent the NoResultsEvent received.
     */
    @Subscribe
    void handleNoResultsEvent(DiscoveryEventUtils.NoResultsEvent noResultsEvent) {
        SwingUtilities.invokeLater(() -> {
            groupSize = 0;
            currentPage = 0;
            updateControls();
            videoThumbnailViewer.clearViewer();
            imageThumbnailViewer.clearViewer();
            documentPreviewViewer.clearViewer();
            resultsViewerPanel.revalidate();
            resultsViewerPanel.repaint();
        });
    }

    /**
     * Set the page number and retrieve its contents.
     *
     * @param startingEntry The index of the first file in the group to include
     *                      in this page.
     */
    private synchronized void setPage(int startingEntry) {
        int pageSize = pageSizeComboBox.getItemAt(pageSizeComboBox.getSelectedIndex());
        synchronized (this) {
            if (pageWorker != null && !pageWorker.isDone()) {
                pageWorker.cancel(true);
            }
            CentralRepository centralRepo = null;
            if (CentralRepository.isEnabled()) {
                try {
                    centralRepo = CentralRepository.getInstance();
                } catch (CentralRepoException ex) {
                    centralRepo = null;
                    logger.log(Level.SEVERE, "Error loading central repository database, no central repository options will be available for File Discovery", ex);
                }
            }
            pageWorker = new PageWorker(searchFilters, groupingAttribute, groupSort, fileSortMethod, selectedGroupKey, startingEntry, pageSize, resultType, centralRepo);
            pageWorker.execute();
        }
    }

    /**
     * Enable the paging controls based on what exists in the page.
     */
    @Messages({"# {0} - currentPage",
        "# {1} - totalPages",
        "ResultsPanel.currentPage.displayValue=Page: {0} of {1}"})
    private void updateControls() {
        previousPageSize = pageSizeComboBox.getItemAt(pageSizeComboBox.getSelectedIndex());
        int pageSize = pageSizeComboBox.getItemAt(pageSizeComboBox.getSelectedIndex());
        //handle edge case where group size is 0 and we want the empty results to be labeled paged 1 of 1 not page 1 of 0
        double maxPageDouble = groupSize == 0 ? 1 : Math.ceil((double) groupSize / pageSize);
        currentPageLabel.setText(Bundle.ResultsPanel_currentPage_displayValue(currentPage + 1, maxPageDouble));
        previousPageButton.setEnabled(currentPage != 0);
        nextPageButton.setEnabled(groupSize > ((currentPage + 1) * pageSize));
        gotoPageField.setEnabled(groupSize > pageSize);
        pageSizeComboBox.setEnabled(true);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.JPanel pagingPanel = new javax.swing.JPanel();
        previousPageButton = new javax.swing.JButton();
        currentPageLabel = new javax.swing.JLabel();
        nextPageButton = new javax.swing.JButton();
        javax.swing.JLabel pageControlsLabel = new javax.swing.JLabel();
        javax.swing.JLabel gotoPageLabel = new javax.swing.JLabel();
        gotoPageField = new javax.swing.JTextField();
        javax.swing.JLabel pageSizeLabel = new javax.swing.JLabel();
        pageSizeComboBox = new javax.swing.JComboBox<>();
        javax.swing.JSplitPane resultsSplitPane = new javax.swing.JSplitPane();
        javax.swing.JPanel instancesPanel = new javax.swing.JPanel();
        javax.swing.JScrollPane instancesScrollPane = new javax.swing.JScrollPane();
        instancesList = new javax.swing.JList<>();
        resultsViewerPanel = new javax.swing.JPanel();

        setPreferredSize(new java.awt.Dimension(777, 475));

        pagingPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        previousPageButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_back.png"))); // NOI18N
        previousPageButton.setBorder(null);
        previousPageButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_back_disabled.png"))); // NOI18N
        previousPageButton.setEnabled(false);
        previousPageButton.setFocusable(false);
        previousPageButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_back_hover.png"))); // NOI18N
        previousPageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                previousPageButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(currentPageLabel, org.openide.util.NbBundle.getMessage(ResultsPanel.class, "ResultsPanel.currentPageLabel.text")); // NOI18N
        currentPageLabel.setMaximumSize(new java.awt.Dimension(90, 23));
        currentPageLabel.setMinimumSize(new java.awt.Dimension(90, 23));
        currentPageLabel.setPreferredSize(new java.awt.Dimension(90, 23));

        nextPageButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_forward.png"))); // NOI18N
        nextPageButton.setBorder(null);
        nextPageButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_forward_disabled.png"))); // NOI18N
        nextPageButton.setEnabled(false);
        nextPageButton.setFocusable(false);
        nextPageButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_forward_hover.png"))); // NOI18N
        nextPageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextPageButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(pageControlsLabel, org.openide.util.NbBundle.getMessage(ResultsPanel.class, "ResultsPanel.pageControlsLabel.text")); // NOI18N
        pageControlsLabel.setMaximumSize(new java.awt.Dimension(33, 23));
        pageControlsLabel.setMinimumSize(new java.awt.Dimension(33, 23));
        pageControlsLabel.setPreferredSize(new java.awt.Dimension(33, 23));

        org.openide.awt.Mnemonics.setLocalizedText(gotoPageLabel, org.openide.util.NbBundle.getMessage(ResultsPanel.class, "ResultsPanel.gotoPageLabel.text")); // NOI18N
        gotoPageLabel.setMaximumSize(new java.awt.Dimension(70, 23));
        gotoPageLabel.setMinimumSize(new java.awt.Dimension(70, 23));
        gotoPageLabel.setPreferredSize(new java.awt.Dimension(70, 23));

        gotoPageField.setEnabled(false);
        gotoPageField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                gotoPageFieldActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(pageSizeLabel, org.openide.util.NbBundle.getMessage(ResultsPanel.class, "ResultsPanel.pageSizeLabel.text")); // NOI18N
        pageSizeLabel.setMaximumSize(new java.awt.Dimension(60, 23));
        pageSizeLabel.setMinimumSize(new java.awt.Dimension(60, 23));
        pageSizeLabel.setPreferredSize(new java.awt.Dimension(60, 23));

        pageSizeComboBox.setModel(new DefaultComboBoxModel<Integer>(new Integer[] {25,50,75,100,125,150,175,200}));
        pageSizeComboBox.setSelectedIndex(3);
        pageSizeComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                pageSizeChanged(evt);
            }
        });

        javax.swing.GroupLayout pagingPanelLayout = new javax.swing.GroupLayout(pagingPanel);
        pagingPanel.setLayout(pagingPanelLayout);
        pagingPanelLayout.setHorizontalGroup(
            pagingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pagingPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(currentPageLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(pageControlsLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(previousPageButton)
                .addGap(0, 0, 0)
                .addComponent(nextPageButton)
                .addGap(18, 18, 18)
                .addComponent(gotoPageLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(gotoPageField, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(pageSizeLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 52, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pageSizeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 55, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        pagingPanelLayout.setVerticalGroup(
            pagingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pagingPanelLayout.createSequentialGroup()
                .addGap(4, 4, 4)
                .addGroup(pagingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(nextPageButton, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pagingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(previousPageButton, javax.swing.GroupLayout.Alignment.TRAILING)
                        .addComponent(currentPageLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(pageControlsLabel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pagingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(gotoPageLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(gotoPageField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(pageSizeLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(pageSizeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(4, 4, 4))
        );

        resultsSplitPane.setDividerLocation(380);
        resultsSplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        resultsSplitPane.setResizeWeight(1.0);
        resultsSplitPane.setToolTipText(org.openide.util.NbBundle.getMessage(ResultsPanel.class, "ResultsPanel.resultsSplitPane.toolTipText")); // NOI18N
        resultsSplitPane.setLastDividerLocation(180);
        resultsSplitPane.setOpaque(false);
        resultsSplitPane.setPreferredSize(new java.awt.Dimension(777, 440));

        instancesPanel.setPreferredSize(new java.awt.Dimension(775, 68));

        instancesScrollPane.setPreferredSize(new java.awt.Dimension(775, 60));

        instancesList.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(ResultsPanel.class, "ResultsPanel.instancesList.border.title"))); // NOI18N
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
            .addGap(0, 68, Short.MAX_VALUE)
            .addGroup(instancesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, instancesPanelLayout.createSequentialGroup()
                    .addGap(0, 0, 0)
                    .addComponent(instancesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );

        resultsSplitPane.setRightComponent(instancesPanel);

        resultsViewerPanel.setPreferredSize(new java.awt.Dimension(0, 380));
        resultsViewerPanel.setLayout(new java.awt.BorderLayout());
        resultsSplitPane.setLeftComponent(resultsViewerPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pagingPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(resultsSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(pagingPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(resultsSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Action to perform when previous button is clicked.
     *
     * @param evt Event which occurs when button is clicked.
     */
    private void previousPageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_previousPageButtonActionPerformed
        if (currentPage > 0) {
            disablePagingControls();
            int previousPage = currentPage - 1;
            int pageSize = pageSizeComboBox.getItemAt(pageSizeComboBox.getSelectedIndex());
            setPage(previousPage * pageSize);
        }
    }//GEN-LAST:event_previousPageButtonActionPerformed

    /**
     * Action to perform when next button is clicked.
     *
     * @param evt Event which occurs when button is clicked.
     */
    private void nextPageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextPageButtonActionPerformed
        disablePagingControls();
        int nextPage = currentPage + 1;
        int pageSize = pageSizeComboBox.getItemAt(pageSizeComboBox.getSelectedIndex());
        setPage(nextPage * pageSize);
    }//GEN-LAST:event_nextPageButtonActionPerformed

    /**
     * Navigate to the page number specified in the field
     *
     * @param evt The event which happens to field is used.
     */
    @Messages({"# {0} - selectedPage",
        "# {1} - maxPage",
        "ResultsPanel.invalidPageNumber.message=The selected page number {0} does not exist. Please select a value from 1 to {1}.",
        "ResultsPanel.invalidPageNumber.title=Invalid Page Number"})
    private void gotoPageFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_gotoPageFieldActionPerformed
        int newPage;
        try {
            newPage = Integer.parseInt(gotoPageField.getText());
        } catch (NumberFormatException e) {
            //ignore input
            return;
        }
        int pageSize = pageSizeComboBox.getItemAt(pageSizeComboBox.getSelectedIndex());
        if ((newPage - 1) < 0 || groupSize <= ((newPage - 1) * pageSize)) {
            JOptionPane.showMessageDialog(this,
                    Bundle.ResultsPanel_invalidPageNumber_message(newPage, Math.ceil((double) groupSize / pageSize)),
                    Bundle.ResultsPanel_invalidPageNumber_title(),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        disablePagingControls();
        setPage((newPage - 1) * pageSize);
    }//GEN-LAST:event_gotoPageFieldActionPerformed

    private void pageSizeChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_pageSizeChanged
        if (evt.getStateChange() == ItemEvent.SELECTED) {
            disablePagingControls();
            int previousPage = currentPage - 1;
            int pageSize = pageSizeComboBox.getItemAt(pageSizeComboBox.getSelectedIndex());
            if (previousPageSize != pageSize) {
                previousPage = 0;
            }
            setPage(previousPage * pageSize);
        }
    }//GEN-LAST:event_pageSizeChanged

    /**
     * Disable all the paging controls.
     */
    private void disablePagingControls() {
        nextPageButton.setEnabled(false);
        previousPageButton.setEnabled(false);
        gotoPageField.setEnabled(false);
        pageSizeComboBox.setEnabled(false);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel currentPageLabel;
    private javax.swing.JTextField gotoPageField;
    private javax.swing.JList<AbstractFile> instancesList;
    private javax.swing.JButton nextPageButton;
    private javax.swing.JComboBox<Integer> pageSizeComboBox;
    private javax.swing.JButton previousPageButton;
    private javax.swing.JPanel resultsViewerPanel;
    // End of variables declaration//GEN-END:variables

    /**
     * Swing worker to handle the retrieval of video thumbnails and population
     * of the Video Thumbnail Viewer.
     */
    private class VideoThumbnailWorker extends SwingWorker<Void, Void> {

        private final VideoThumbnailsWrapper thumbnailWrapper;

        /**
         * Construct a new VideoThumbnailWorker.
         *
         * @param file The ResultFile which represents the video file thumbnails
         *             are being retrieved for.
         */
        VideoThumbnailWorker(ResultFile file) {
            thumbnailWrapper = new VideoThumbnailsWrapper(file);
            videoThumbnailViewer.addVideo(thumbnailWrapper);
        }

        @Override
        protected Void doInBackground() throws Exception {
            FileSearch.getVideoThumbnails(thumbnailWrapper);
            return null;
        }

        @Override
        protected void done() {
            try {
                get();
            } catch (InterruptedException | ExecutionException ex) {
                logger.log(Level.WARNING, "Video Worker Exception for file: " + thumbnailWrapper.getResultFile().getFirstInstance().getId(), ex);
            } catch (CancellationException ignored) {
                //we want to do nothing in response to this since we allow it to be cancelled
            }
            videoThumbnailViewer.repaint();
        }
    }

    /**
     * Swing worker to handle the retrieval of image thumbnails and population
     * of the Image Thumbnail Viewer.
     */
    private class ImageThumbnailWorker extends SwingWorker<Void, Void> {

        private final ImageThumbnailWrapper thumbnailWrapper;

        /**
         * Construct a new ImageThumbnailWorker.
         *
         * @param file The ResultFile which represents the image file thumbnails
         *             are being retrieved for.
         */
        ImageThumbnailWorker(ResultFile file) {
            thumbnailWrapper = new ImageThumbnailWrapper(file);
            imageThumbnailViewer.addImage(thumbnailWrapper);
        }

        @Override
        protected Void doInBackground() throws Exception {
            Image thumbnail = ImageUtils.getThumbnail(thumbnailWrapper.getResultFile().getFirstInstance(), ImageUtils.ICON_SIZE_LARGE);
            if (thumbnail != null) {
                thumbnailWrapper.setImageThumbnail(thumbnail);
            }
            return null;
        }

        @Override
        protected void done() {
            try {
                get();
            } catch (InterruptedException | ExecutionException ex) {
                logger.log(Level.WARNING, "Image Worker Exception for file: " + thumbnailWrapper.getResultFile().getFirstInstance().getId(), ex);
            } catch (CancellationException ignored) {
                //we want to do nothing in response to this since we allow it to be cancelled
            }
            imageThumbnailViewer.repaint();
        }

    }

    /**
     * Swing worker to handle the retrieval of document previews and population
     * of the Document Preview Viewer.
     */
    private class DocumentPreviewWorker extends SwingWorker<Void, Void> {

        private final DocumentWrapper documentWrapper;

        /**
         * Construct a new DocumentPreviewWorker.
         *
         * @param file The ResultFile which represents the document file a
         *             preview is being retrieved for.
         */
        DocumentPreviewWorker(ResultFile file) {
            documentWrapper = new DocumentWrapper(file);
            documentPreviewViewer.addDocument(documentWrapper);
        }

        @Messages({"ResultsPanel.unableToCreate.text=Unable to create summary."})
        @Override
        protected Void doInBackground() throws Exception {
            TextSummary preview = FileSearch.summarize(documentWrapper.getResultFile().getFirstInstance());
            if (preview == null) {
                preview = new TextSummary(Bundle.ResultsPanel_unableToCreate_text(), null, 0);
            }
            documentWrapper.setPreview(preview.getSummaryText());
            return null;
        }

        @Messages({"ResultsPanel.documentPreview.text=Document preview creation cancelled."})
        @Override
        protected void done() {
            try {
                get();
            } catch (InterruptedException | ExecutionException ex) {
                documentWrapper.setPreview(ex.getMessage());
                logger.log(Level.WARNING, "Document Worker Exception", ex);
            } catch (CancellationException ignored) {
                documentWrapper.setPreview(Bundle.ResultsPanel_documentPreview_text());
                //we want to do nothing in response to this since we allow it to be cancelled
            }
            documentPreviewViewer.repaint();
        }

    }

    /**
     * Cell renderer for the instances list.
     */
    private class InstancesCellRenderer extends DefaultListCellRenderer {

        private static final long serialVersionUID = 1L;

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
