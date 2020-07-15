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
package org.sleuthkit.autopsy.discovery;

import com.google.common.eventbus.Subscribe;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JSplitPane;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.Mode;
import org.openide.windows.RetainLocation;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.discovery.FileSearchFiltering.FileFilter;

/**
 * Create a dialog for displaying the Discovery results.
 */
@TopComponent.Description(preferredID = "DiscoveryTopComponent", persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "discovery", openAtStartup = false)
@RetainLocation("discovery")
@NbBundle.Messages("DiscoveryTopComponent.name= Discovery")
public final class DiscoveryTopComponent extends TopComponent {

    private static final long serialVersionUID = 1L;
    private static final String PREFERRED_ID = "DiscoveryTopComponent"; // NON-NLS
    private final GroupListPanel groupListPanel;
    private final DetailsPanel detailsPanel;
    private final ResultsPanel resultsPanel;
    private int dividerLocation = -1;

    private static final int ANIMATION_INCREMENT = 10;
    private static final int RESULTS_AREA_SMALL_SIZE = 250;

    private SwingAnimator animator = null;

    /**
     * Creates new form DiscoveryTopComponent.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public DiscoveryTopComponent() {
        initComponents();
        setName(Bundle.DiscoveryTopComponent_name());
        groupListPanel = new GroupListPanel();
        resultsPanel = new ResultsPanel();
        detailsPanel = new DetailsPanel();
        mainSplitPane.setLeftComponent(groupListPanel);
        rightSplitPane.setTopComponent(resultsPanel);
        rightSplitPane.setBottomComponent(detailsPanel);
        //set color of divider
        rightSplitPane.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDividerImpl(this);

            }
        });
    }

    /**
     * Private class for replacing the divider for the results split pane.
     */
    private final class BasicSplitPaneDividerImpl extends BasicSplitPaneDivider {

        /**
         * Construct a new BasicSplitPaneDividerImpl.
         *
         * @param ui The component which contains the split pane this divider is
         *           in.
         */
        BasicSplitPaneDividerImpl(BasicSplitPaneUI ui) {
            super(ui);
            this.setLayout(new BorderLayout());
            this.add(new ResultsSplitPaneDivider());
        }

        private static final long serialVersionUID = 1L;
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

    @Override
    public void componentOpened() {
        super.componentOpened();
        WindowManager.getDefault().setTopComponentFloating(this, true);
        DiscoveryEventUtils.getDiscoveryEventBus().register(this);
        DiscoveryEventUtils.getDiscoveryEventBus().register(resultsPanel);
        DiscoveryEventUtils.getDiscoveryEventBus().register(groupListPanel);
        DiscoveryEventUtils.getDiscoveryEventBus().register(detailsPanel);
    }

    @Override
    protected void componentClosed() {
        DiscoveryDialog.getDiscoveryDialogInstance().cancelSearch();
        DiscoveryEventUtils.getDiscoveryEventBus().unregister(this);
        DiscoveryEventUtils.getDiscoveryEventBus().unregister(groupListPanel);
        DiscoveryEventUtils.getDiscoveryEventBus().unregister(resultsPanel);
        DiscoveryEventUtils.getDiscoveryEventBus().unregister(detailsPanel);
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
        rightSplitPane = new AnimatedSplitPane();
        javax.swing.JPanel searchDetailsPanel = new javax.swing.JPanel();
        newSearchButton = new javax.swing.JButton();
        javax.swing.JScrollPane progressMessageScrollPane = new javax.swing.JScrollPane();
        progressMessageTextArea = new javax.swing.JTextArea();

        setMinimumSize(new java.awt.Dimension(199, 200));
        setPreferredSize(new java.awt.Dimension(1100, 700));
        setLayout(new java.awt.BorderLayout());

        mainSplitPane.setDividerLocation(250);
        mainSplitPane.setPreferredSize(new java.awt.Dimension(1100, 700));

        rightSplitPane.setDividerSize(35);
        rightSplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        rightSplitPane.setResizeWeight(1.0);
        rightSplitPane.setPreferredSize(new java.awt.Dimension(800, 700));
        mainSplitPane.setRightComponent(rightSplitPane);

        add(mainSplitPane, java.awt.BorderLayout.CENTER);

        org.openide.awt.Mnemonics.setLocalizedText(newSearchButton, org.openide.util.NbBundle.getMessage(DiscoveryTopComponent.class, "FileSearchDialog.cancelButton.text")); // NOI18N
        newSearchButton.setMaximumSize(new java.awt.Dimension(110, 26));
        newSearchButton.setMinimumSize(new java.awt.Dimension(110, 26));
        newSearchButton.setPreferredSize(new java.awt.Dimension(110, 26));
        newSearchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newSearchButtonActionPerformed(evt);
            }
        });

        progressMessageScrollPane.setBorder(null);

        progressMessageTextArea.setBackground(new java.awt.Color(240, 240, 240));
        progressMessageTextArea.setColumns(20);
        progressMessageTextArea.setLineWrap(true);
        progressMessageTextArea.setRows(2);
        progressMessageTextArea.setWrapStyleWord(true);
        progressMessageTextArea.setBorder(null);
        progressMessageScrollPane.setViewportView(progressMessageTextArea);

        javax.swing.GroupLayout searchDetailsPanelLayout = new javax.swing.GroupLayout(searchDetailsPanel);
        searchDetailsPanel.setLayout(searchDetailsPanelLayout);
        searchDetailsPanelLayout.setHorizontalGroup(
            searchDetailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(searchDetailsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(newSearchButton, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(progressMessageScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 954, Short.MAX_VALUE)
                .addContainerGap())
        );
        searchDetailsPanelLayout.setVerticalGroup(
            searchDetailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(searchDetailsPanelLayout.createSequentialGroup()
                .addGroup(searchDetailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(searchDetailsPanelLayout.createSequentialGroup()
                        .addGap(8, 8, 8)
                        .addComponent(progressMessageScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 44, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(searchDetailsPanelLayout.createSequentialGroup()
                        .addGap(17, 17, 17)
                        .addComponent(newSearchButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );

        add(searchDetailsPanel, java.awt.BorderLayout.PAGE_START);
    }// </editor-fold>//GEN-END:initComponents

    private void newSearchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newSearchButtonActionPerformed
        close();
        final DiscoveryDialog discDialog = DiscoveryDialog.getDiscoveryDialogInstance();
        discDialog.cancelSearch();
        discDialog.setVisible(true);
        discDialog.validateDialog();
    }//GEN-LAST:event_newSearchButtonActionPerformed

    @Override
    public List<Mode> availableModes(List<Mode> modes) {
        /*
         * This looks like the right thing to do, but online discussions seems
         * to indicate this method is effectively deprecated. A break point
         * placed here was never hit.
         */
        return modes.stream().filter(mode -> mode.getName().equals("discovery"))
                .collect(Collectors.toList());
    }

    /**
     * Subscribe to the DetailsVisible event and animate the panel as it changes
     * visibility.
     *
     * @param detailsVisibleEvent The DetailsVisibleEvent which indicates if the
     *                            panel should be made visible or hidden.
     */
    @Subscribe
    void handleDetailsVisibleEvent(DiscoveryEventUtils.DetailsVisibleEvent detailsVisibleEvent) {
        if (animator != null && animator.isRunning()) {
            animator.stop();
        }
        dividerLocation = rightSplitPane.getDividerLocation();
        if (detailsVisibleEvent.isShowDetailsArea()) {
            animator = new SwingAnimator(new ShowDetailsAreaCallback());
        } else {
            animator = new SwingAnimator(new HideDetailsAreaCallback());
        }
        animator.start();
    }

    /**
     * Subscribe to the SearchStartedEvent for updating the UI accordingly.
     *
     * @param searchStartedEvent The event which indicates the start of a
     *                           search.
     */
    @Messages({"DiscoveryTopComponent.cancelButton.text=Cancel Search",
        "# {0} - searchType",
        "DiscoveryTopComponent.searchInProgress.text=Performing search for results of type {0}. Please wait."})
    @Subscribe
    void handleSearchStartedEvent(DiscoveryEventUtils.SearchStartedEvent searchStartedEvent) {
        newSearchButton.setText(Bundle.DiscoveryTopComponent_cancelButton_text());
        progressMessageTextArea.setForeground(Color.red);
        progressMessageTextArea.setText(Bundle.DiscoveryTopComponent_searchInProgress_text(searchStartedEvent.getType().name()));
    }

    /**
     * Subscribe to the SearchCompleteEvent for updating the UI accordingly.
     *
     * @param searchCompleteEvent The event which indicates the completion of a
     *                            search.
     */
    @Subscribe
    @Messages({"DiscoveryTopComponent.newSearch.text=New Search",
        "# {0} - search",
        "DiscoveryTopComponent.searchComplete.text=Results with {0}"})
    void handleSearchCompleteEvent(DiscoveryEventUtils.SearchCompleteEvent searchCompleteEvent) {
        newSearchButton.setText(Bundle.DiscoveryTopComponent_newSearch_text());
        progressMessageTextArea.setForeground(Color.black);
        progressMessageTextArea.setText(Bundle.DiscoveryTopComponent_searchComplete_text(searchCompleteEvent.getFilters().stream().map(FileFilter::getDesc).collect(Collectors.joining("; "))));
        progressMessageTextArea.setCaretPosition(0);
    }

    /**
     * Subscribe to the SearchCancelledEvent for updating the UI accordingly.
     *
     * @param searchCancelledEvent The event which indicates the cancellation of
     *                             a search.
     */
    @Messages({"DiscoveryTopComponent.searchCancelled.text=Search has been cancelled."})
    @Subscribe
    void handleSearchCancelledEvent(DiscoveryEventUtils.SearchCancelledEvent searchCancelledEvent) {
        newSearchButton.setText(Bundle.DiscoveryTopComponent_newSearch_text());
        progressMessageTextArea.setForeground(Color.red);
        progressMessageTextArea.setText(Bundle.DiscoveryTopComponent_searchCancelled_text());

    }

    /**
     * Callback for animating the details area into a visible position.
     */
    private final class ShowDetailsAreaCallback implements SwingAnimatorCallback {

        @Override
        public void callback(Object caller) {
            dividerLocation -= ANIMATION_INCREMENT;
            repaint();
        }

        @Override
        public boolean hasTerminated() {
            if (dividerLocation != JSplitPane.UNDEFINED_CONDITION && dividerLocation < RESULTS_AREA_SMALL_SIZE) {
                dividerLocation = RESULTS_AREA_SMALL_SIZE;
                return true;
            }
            return false;
        }

    }

    /**
     * Callback for animating the details area into a hidden position.
     */
    private final class HideDetailsAreaCallback implements SwingAnimatorCallback {

        @Override
        public void callback(Object caller) {
            dividerLocation += ANIMATION_INCREMENT;
            repaint();
        }

        @Override
        public boolean hasTerminated() {
            if (dividerLocation > rightSplitPane.getHeight() || dividerLocation == JSplitPane.UNDEFINED_CONDITION) {
                dividerLocation = rightSplitPane.getHeight();
                return true;
            }
            return false;
        }

    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSplitPane mainSplitPane;
    private javax.swing.JButton newSearchButton;
    private javax.swing.JTextArea progressMessageTextArea;
    private javax.swing.JSplitPane rightSplitPane;
    // End of variables declaration//GEN-END:variables

    /**
     * Class for the split pane which will be animated.
     */
    private final class AnimatedSplitPane extends JSplitPane {

        private static final long serialVersionUID = 1L;

        @Override
        public void paintComponent(Graphics g) {
            if ((dividerLocation == JSplitPane.UNDEFINED_CONDITION) || (dividerLocation <= rightSplitPane.getHeight() && dividerLocation >= RESULTS_AREA_SMALL_SIZE)) {
                rightSplitPane.setDividerLocation(dividerLocation);
            }
            super.paintComponent(g);
        }

    }

}
