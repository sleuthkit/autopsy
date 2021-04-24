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
package org.sleuthkit.autopsy.discovery.ui;

import org.sleuthkit.autopsy.discovery.search.AbstractFilter;
import com.google.common.eventbus.Subscribe;
import java.awt.Cursor;
import java.awt.Image;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.discovery.search.DiscoveryAttributes;
import org.sleuthkit.autopsy.discovery.search.DiscoveryEventUtils;
import org.sleuthkit.autopsy.discovery.search.DiscoveryKeyUtils.GroupKey;
import org.sleuthkit.autopsy.discovery.search.DomainSearch;
import org.sleuthkit.autopsy.discovery.search.DomainSearchThumbnailRequest;
import org.sleuthkit.autopsy.discovery.search.Group;
import org.sleuthkit.autopsy.discovery.search.FileSearch;
import org.sleuthkit.autopsy.discovery.search.SearchData;
import org.sleuthkit.autopsy.discovery.search.ResultsSorter;
import org.sleuthkit.autopsy.discovery.search.Result;
import org.sleuthkit.autopsy.discovery.search.ResultDomain;
import org.sleuthkit.autopsy.discovery.search.ResultFile;
import static org.sleuthkit.autopsy.discovery.search.SearchData.Type.DOMAIN;
import org.sleuthkit.autopsy.textsummarizer.TextSummary;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Panel for displaying of Discovery results and handling the paging of those
 * results.
 */
final class ResultsPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private final static Logger logger = Logger.getLogger(ResultsPanel.class.getName());
    private final VideoThumbnailViewer videoThumbnailViewer;
    private final ImageThumbnailViewer imageThumbnailViewer;
    private final DocumentPreviewViewer documentPreviewViewer;
    private final DomainSummaryViewer domainSummaryViewer;
    private List<AbstractFilter> searchFilters;
    private DiscoveryAttributes.AttributeType groupingAttribute;
    private Group.GroupSortingAlgorithm groupSort;
    private ResultsSorter.SortingMethod fileSortMethod;
    private GroupKey selectedGroupKey;
    private int currentPage = 0;
    private int previousPageSize = 10;
    private SearchData.Type resultType;
    private int groupSize = 0;
    private PageWorker pageWorker;
    private final List<SwingWorker<Void, Void>> resultContentWorkers = new ArrayList<>();

    /**
     * Creates new form ResultsPanel.
     */
    @Messages({"ResultsPanel.viewFileInDir.name=View File in Directory",
        "ResultsPanel.openInExternalViewer.name=Open in External Viewer"})
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    ResultsPanel() {
        initComponents();
        imageThumbnailViewer = new ImageThumbnailViewer();
        videoThumbnailViewer = new VideoThumbnailViewer();
        documentPreviewViewer = new DocumentPreviewViewer();
        domainSummaryViewer = new DomainSummaryViewer();
        videoThumbnailViewer.addListSelectionListener((e) -> {
            if (resultType == SearchData.Type.VIDEO) {
                if (!e.getValueIsAdjusting()) {
                    //send populateMesage
                    DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.PopulateInstancesListEvent(getInstancesForSelected()));
                } else {
                    //send clearSelection message
                    DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.ClearInstanceSelectionEvent());
                }
            }
        });
        imageThumbnailViewer.addListSelectionListener((e) -> {
            if (resultType == SearchData.Type.IMAGE) {
                if (!e.getValueIsAdjusting()) {
                    //send populateMesage
                    DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.PopulateInstancesListEvent(getInstancesForSelected()));
                } else {
                    //send clearSelection message
                    DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.ClearInstanceSelectionEvent());
                }

            }
        });
        documentPreviewViewer.addListSelectionListener((e) -> {
            if (resultType == SearchData.Type.DOCUMENT) {
                if (!e.getValueIsAdjusting()) {
                    //send populateMesage
                    DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.PopulateInstancesListEvent(getInstancesForSelected()));
                } else {
                    //send clearSelection message
                    DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.ClearInstanceSelectionEvent());
                }
            }
        });
        domainSummaryViewer.addListSelectionListener((e) -> {
            if (resultType == SearchData.Type.DOMAIN && !e.getValueIsAdjusting()) {
                domainSummaryViewer.sendPopulateEvent();
            }
        });
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    SearchData.Type getActiveType() {
        return resultType;
    }

    /**
     * Get the list of all instances for the the currently selected item in the
     * results viewer area.
     *
     * @return The list of AbstractFiles which are represented by the item
     *         selected in the results viewer area.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private List<AbstractFile> getInstancesForSelected() {
        if (null != resultType) {
            switch (resultType) {
                case VIDEO:
                    return videoThumbnailViewer.getInstancesForSelected();
                case IMAGE:
                    return imageThumbnailViewer.getInstancesForSelected();
                case DOCUMENT:
                    return documentPreviewViewer.getInstancesForSelected();
                default:
                    break;
            }
        }
        return new ArrayList<>();
    }

    /**
     * Subscribe to and reset the panel in response to SearchStartedEvents.
     *
     * @param searchStartedEvent The SearchStartedEvent which was received.
     */
    @Subscribe
    void handleSearchStartedEvent(DiscoveryEventUtils.SearchStartedEvent searchStartedEvent) {
        SwingUtilities.invokeLater(() -> {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        });
    }

    /**
     * Subscribe and respond to PageRetrievedEvents.
     *
     * @param pageRetrievedEvent The PageRetrievedEvent received.
     */
    @Subscribe
    void handlePageRetrievedEvent(DiscoveryEventUtils.PageRetrievedEvent pageRetrievedEvent) {
        //send populateMesage
        if (pageRetrievedEvent.getType() != DOMAIN) {
            DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.PopulateInstancesListEvent(getInstancesForSelected()));
        }
        currentPage = pageRetrievedEvent.getPageNumber();
        SwingUtilities.invokeLater(() -> {
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
                    case DOCUMENT:
                        populateDocumentViewer(pageRetrievedEvent.getSearchResults());
                        resultsViewerPanel.add(documentPreviewViewer);
                        break;
                    case DOMAIN:
                        populateDomainViewer(pageRetrievedEvent.getSearchResults());
                        resultsViewerPanel.add(domainSummaryViewer);
                        break;
                    default:
                        break;
                }
            }
            resultsViewerPanel.revalidate();
            resultsViewerPanel.repaint();
        });
    }

    @Subscribe
    void handleCancelBackgroundTasksEvent(DiscoveryEventUtils.CancelBackgroundTasksEvent cancelEvent) {
        for (SwingWorker<Void, Void> thumbWorker : resultContentWorkers) {
            if (!thumbWorker.isDone()) {
                thumbWorker.cancel(true);
            }
        }
        resultContentWorkers.clear();
    }

    /**
     * Reset the result viewer and any associate workers to a default empty
     * state.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void resetResultViewer() {
        resultsViewerPanel.remove(imageThumbnailViewer);
        resultsViewerPanel.remove(videoThumbnailViewer);
        resultsViewerPanel.remove(documentPreviewViewer);
        resultsViewerPanel.remove(domainSummaryViewer);
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
        domainSummaryViewer.clearViewer();
    }

    /**
     * Populate the video thumbnail viewer, cancelling any thumbnails which are
     * currently being created first.
     *
     * @param results The list of ResultFiles to populate the video viewer with.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void populateVideoViewer(List<Result> results) {
        for (Result result : results) {
            VideoThumbnailWorker thumbWorker = new VideoThumbnailWorker((ResultFile) result);
            thumbWorker.execute();
            //keep track of thumb worker for possible cancelation 
            resultContentWorkers.add(thumbWorker);
        }
    }

    /**
     * Populate the image thumbnail viewer, cancelling any thumbnails which are
     * currently being created first.
     *
     * @param results The list of ResultFiles to populate the image viewer with.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void populateImageViewer(List<Result> results) {
        for (Result result : results) {
            ImageThumbnailWorker thumbWorker = new ImageThumbnailWorker((ResultFile) result);
            thumbWorker.execute();
            //keep track of thumb worker for possible cancelation 
            resultContentWorkers.add(thumbWorker);
        }
    }

    /**
     * Populate the document preview viewer, cancelling any content which is
     * currently being created first.
     *
     * @param results The list of ResultFiles to populate the document viewer
     *                with.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void populateDocumentViewer(List<Result> results) {
        for (Result result : results) {
            DocumentPreviewWorker documentWorker = new DocumentPreviewWorker((ResultFile) result);
            documentWorker.execute();
            //keep track of thumb worker for possible cancelation 
            resultContentWorkers.add(documentWorker);
        }
    }

    /**
     * Populate the domain summary viewer, cancelling any content which is
     * currently being created first.
     *
     * @param results The list of ResultDomains to populate the domain summary
     *                viewer with.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void populateDomainViewer(List<Result> results) {
        SleuthkitCase currentCase;
        try {
            currentCase = Case.getCurrentCaseThrows().getSleuthkitCase();
        } catch (NoCurrentCaseException ex) {
            // Do nothing, case has been closed.
            return;
        }

        for (Result result : results) {
            DomainThumbnailWorker domainWorker = new DomainThumbnailWorker(
                    currentCase, (ResultDomain) result);
            domainWorker.execute();
            //keep track of thumb worker for possible cancelation 
            resultContentWorkers.add(domainWorker);
        }
    }

    /**
     * Subscribe and respond to GroupSelectedEvents.
     *
     * @param groupSelectedEvent The GroupSelectedEvent received.
     */
    @Subscribe
    void handleGroupSelectedEvent(DiscoveryEventUtils.GroupSelectedEvent groupSelectedEvent) {
        searchFilters = groupSelectedEvent.getFilters();
        groupingAttribute = groupSelectedEvent.getGroupingAttr();
        groupSort = groupSelectedEvent.getGroupSort();
        fileSortMethod = groupSelectedEvent.getResultSort();
        selectedGroupKey = groupSelectedEvent.getGroupKey();
        resultType = groupSelectedEvent.getResultType();
        groupSize = groupSelectedEvent.getGroupSize();
        SwingUtilities.invokeLater(() -> {
            resetResultViewer();
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
        groupSize = 0;
        currentPage = 0;
        SwingUtilities.invokeLater(() -> {
            updateControls();
            videoThumbnailViewer.clearViewer();
            imageThumbnailViewer.clearViewer();
            documentPreviewViewer.clearViewer();
            domainSummaryViewer.clearViewer();
            resultsViewerPanel.revalidate();
            resultsViewerPanel.repaint();
        });
    }

    /**
     * Subscribe to and update cursor in response to SearchCompleteEvents.
     *
     * @param searchCompleteEvent The SearchCompleteEvent which was received.
     */
    @Subscribe
    void handleSearchCompleteEvent(DiscoveryEventUtils.SearchCompleteEvent searchCompleteEvent) {
        SwingUtilities.invokeLater(() -> {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        });
    }

    /**
     * Set the page number and retrieve its contents.
     *
     * @param startingEntry The index of the first file in the group to include
     *                      in this page.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void setPage(int startingEntry) {
        int pageSize = pageSizeComboBox.getItemAt(pageSizeComboBox.getSelectedIndex());
        if (pageWorker != null && !pageWorker.isDone()) {
            pageWorker.cancel(true);
        }
        CentralRepository centralRepo = null;
        if (CentralRepository.isEnabled()) {
            try {
                centralRepo = CentralRepository.getInstance();
            } catch (CentralRepoException ex) {
                centralRepo = null;
                logger.log(Level.SEVERE, "Error loading central repository database, no central repository options will be available for Discovery", ex);
            }
        }
        if (groupSize != 0) {
            pageWorker = new PageWorker(searchFilters, groupingAttribute, groupSort, fileSortMethod, selectedGroupKey, startingEntry, pageSize, resultType, centralRepo);
            pageWorker.execute();
        } else {
            pageSizeComboBox.setEnabled(true);
        }
    }

    /**
     * Enable the paging controls based on what exists in the page.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
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
        java.awt.GridBagConstraints gridBagConstraints;

        javax.swing.JPanel pagingPanel = new javax.swing.JPanel();
        previousPageButton = new javax.swing.JButton();
        currentPageLabel = new javax.swing.JLabel();
        nextPageButton = new javax.swing.JButton();
        javax.swing.JLabel pageControlsLabel = new javax.swing.JLabel();
        javax.swing.JLabel gotoPageLabel = new javax.swing.JLabel();
        gotoPageField = new javax.swing.JTextField();
        javax.swing.JLabel pageSizeLabel = new javax.swing.JLabel();
        pageSizeComboBox = new javax.swing.JComboBox<>();
        javax.swing.Box.Filler filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));
        javax.swing.Box.Filler filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));
        javax.swing.Box.Filler filler3 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));
        javax.swing.Box.Filler filler4 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));
        resultsViewerPanel = new javax.swing.JPanel();

        setMinimumSize(new java.awt.Dimension(300, 60));
        setPreferredSize(new java.awt.Dimension(700, 700));
        setLayout(new java.awt.BorderLayout());

        pagingPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        pagingPanel.setMinimumSize(new java.awt.Dimension(400, 39));
        pagingPanel.setPreferredSize(new java.awt.Dimension(700, 39));
        pagingPanel.setLayout(new java.awt.GridBagLayout());

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
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(6, 12, 6, 0);
        pagingPanel.add(previousPageButton, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(currentPageLabel, org.openide.util.NbBundle.getMessage(ResultsPanel.class, "ResultsPanel.currentPageLabel.text")); // NOI18N
        currentPageLabel.setMaximumSize(new java.awt.Dimension(190, 23));
        currentPageLabel.setMinimumSize(new java.awt.Dimension(90, 23));
        currentPageLabel.setPreferredSize(new java.awt.Dimension(100, 23));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(6, 14, 6, 0);
        pagingPanel.add(currentPageLabel, gridBagConstraints);

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
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(6, 0, 6, 0);
        pagingPanel.add(nextPageButton, gridBagConstraints);

        pageControlsLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        org.openide.awt.Mnemonics.setLocalizedText(pageControlsLabel, org.openide.util.NbBundle.getMessage(ResultsPanel.class, "ResultsPanel.pageControlsLabel.text")); // NOI18N
        pageControlsLabel.setMaximumSize(new java.awt.Dimension(133, 23));
        pageControlsLabel.setMinimumSize(new java.awt.Dimension(33, 23));
        pageControlsLabel.setPreferredSize(new java.awt.Dimension(60, 23));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.insets = new java.awt.Insets(6, 18, 6, 0);
        pagingPanel.add(pageControlsLabel, gridBagConstraints);

        gotoPageLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        org.openide.awt.Mnemonics.setLocalizedText(gotoPageLabel, org.openide.util.NbBundle.getMessage(ResultsPanel.class, "ResultsPanel.gotoPageLabel.text")); // NOI18N
        gotoPageLabel.setMaximumSize(new java.awt.Dimension(170, 23));
        gotoPageLabel.setMinimumSize(new java.awt.Dimension(70, 23));
        gotoPageLabel.setPreferredSize(new java.awt.Dimension(100, 23));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.insets = new java.awt.Insets(6, 18, 6, 0);
        pagingPanel.add(gotoPageLabel, gridBagConstraints);

        gotoPageField.setEnabled(false);
        gotoPageField.setMinimumSize(new java.awt.Dimension(30, 22));
        gotoPageField.setName(""); // NOI18N
        gotoPageField.setPreferredSize(new java.awt.Dimension(26, 22));
        gotoPageField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                gotoPageFieldActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 7;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(6, 5, 0, 0);
        pagingPanel.add(gotoPageField, gridBagConstraints);

        pageSizeLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        org.openide.awt.Mnemonics.setLocalizedText(pageSizeLabel, org.openide.util.NbBundle.getMessage(ResultsPanel.class, "ResultsPanel.pageSizeLabel.text")); // NOI18N
        pageSizeLabel.setMaximumSize(new java.awt.Dimension(160, 23));
        pageSizeLabel.setMinimumSize(new java.awt.Dimension(60, 23));
        pageSizeLabel.setPreferredSize(new java.awt.Dimension(90, 23));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 9;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.insets = new java.awt.Insets(6, 18, 6, 0);
        pagingPanel.add(pageSizeLabel, gridBagConstraints);

        pageSizeComboBox.setModel(new DefaultComboBoxModel<Integer>(new Integer[] {25,50,75,100,125,150,175,200}));
        pageSizeComboBox.setSelectedIndex(3);
        pageSizeComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                pageSizeChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 10;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(6, 5, 0, 277);
        pagingPanel.add(pageSizeComboBox, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 11;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.1;
        pagingPanel.add(filler1, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        pagingPanel.add(filler2, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        pagingPanel.add(filler3, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 8;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        pagingPanel.add(filler4, gridBagConstraints);

        add(pagingPanel, java.awt.BorderLayout.PAGE_START);

        resultsViewerPanel.setMinimumSize(new java.awt.Dimension(0, 60));
        resultsViewerPanel.setPreferredSize(new java.awt.Dimension(700, 700));
        resultsViewerPanel.setLayout(new java.awt.BorderLayout());
        add(resultsViewerPanel, java.awt.BorderLayout.CENTER);
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
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void disablePagingControls() {
        nextPageButton.setEnabled(false);
        previousPageButton.setEnabled(false);
        gotoPageField.setEnabled(false);
        pageSizeComboBox.setEnabled(false);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel currentPageLabel;
    private javax.swing.JTextField gotoPageField;
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
        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        VideoThumbnailWorker(ResultFile file) {
            thumbnailWrapper = new VideoThumbnailsWrapper(file);
            videoThumbnailViewer.addVideo(thumbnailWrapper);
        }

        @Override
        protected Void doInBackground() throws Exception {
            DiscoveryUiUtils.getVideoThumbnails(thumbnailWrapper);
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
        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
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
        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
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
            documentWrapper.setSummary(preview);
            return null;
        }

        @Messages({"ResultsPanel.documentPreview.text=Document preview creation cancelled."})
        @Override
        protected void done() {
            try {
                get();
            } catch (InterruptedException | ExecutionException ex) {
                documentWrapper.setSummary(new TextSummary(ex.getMessage(), null, 0));
                logger.log(Level.WARNING, "Document Worker Exception", ex);
            } catch (CancellationException ignored) {
                documentWrapper.setSummary(new TextSummary(Bundle.ResultsPanel_documentPreview_text(), null, 0));
                //we want to do nothing in response to this since we allow it to be cancelled
            }
            documentPreviewViewer.repaint();
        }

    }

    /**
     * Swing worker to handle the retrieval of domain thumbnails and population
     * of the Domain Summary Viewer.
     */
    private class DomainThumbnailWorker extends SwingWorker<Void, Void> {

        private final DomainWrapper domainWrapper;
        private final SleuthkitCase caseDb;

        /**
         * Construct a new DomainThumbnailWorker.
         *
         * @param file The ResultFile which represents the domain attribute the
         *             preview is being retrieved for.
         */
        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        DomainThumbnailWorker(SleuthkitCase caseDb, ResultDomain domain) {
            this.caseDb = caseDb;
            domainWrapper = new DomainWrapper(domain);
            domainSummaryViewer.addDomain(domainWrapper);
        }

        @Override
        protected Void doInBackground() throws Exception {
            DomainSearch domainSearch = new DomainSearch();
            DomainSearchThumbnailRequest request = new DomainSearchThumbnailRequest(
                    caseDb,
                    domainWrapper.getResultDomain().getDomain(),
                    ImageUtils.ICON_SIZE_MEDIUM
            );

            Image thumbnail = domainSearch.getThumbnail(request);
            domainWrapper.setThumbnail(thumbnail);
            return null;
        }

        @Override
        protected void done() {
            try {
                get();
            } catch (ExecutionException ex) {
                domainWrapper.setThumbnail(null);
                logger.log(Level.WARNING, "Fatal error getting thumbnail for domain.", ex);
            } catch (InterruptedException | CancellationException ignored) {
                domainWrapper.setThumbnail(null);
                //we want to do nothing in response to this since we allow it to be cancelled
            }
            domainSummaryViewer.repaint();
        }

    }
}
