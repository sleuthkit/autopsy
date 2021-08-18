/*
 * Autopsy
 *
 * Copyright 2019-2021 Basis Technology Corp.
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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.Mode;
import org.openide.windows.RetainLocation;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.discovery.search.DiscoveryEventUtils;
import org.sleuthkit.autopsy.discovery.search.SearchData.Type;
import static org.sleuthkit.autopsy.discovery.search.SearchData.Type.DOMAIN;
import org.sleuthkit.autopsy.discovery.search.SearchFiltering.ArtifactTypeFilter;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;

/**
 * Create a dialog for displaying the Discovery results.
 */
@TopComponent.Description(preferredID = "DiscoveryTc", persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "discovery", openAtStartup = false)
@RetainLocation("discovery")
@NbBundle.Messages("DiscoveryTopComponent.name= Discovery")
public final class DiscoveryTopComponent extends TopComponent {

    private static final long serialVersionUID = 1L;
    private static final String PREFERRED_ID = "DiscoveryTc"; // NON-NLS
    private static final int ANIMATION_INCREMENT = 30;
    private volatile static int previousDividerLocation = 250;
    private final GroupListPanel groupListPanel;
    private final ResultsPanel resultsPanel;
    private JPanel detailsPanel = new JPanel();
    private String selectedDomainTabName;
    private Type searchType;
    private int dividerLocation = JSplitPane.UNDEFINED_CONDITION;

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
        mainSplitPane.setLeftComponent(groupListPanel);
        //set color of divider
        rightSplitPane.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDividerImpl(this);

            }
        });
        rightSplitPane.setTopComponent(resultsPanel);
        resetBottomComponent();
        rightSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equalsIgnoreCase(JSplitPane.DIVIDER_LOCATION_PROPERTY)
                        && ((animator == null || !animator.isRunning())
                        && evt.getNewValue() instanceof Integer
                        && evt.getOldValue() instanceof Integer
                        && ((int) evt.getNewValue() + 5) < (rightSplitPane.getHeight() - rightSplitPane.getDividerSize())
                        && (JSplitPane.UNDEFINED_CONDITION != (int) evt.getNewValue())
                        && ((int) evt.getOldValue() != JSplitPane.UNDEFINED_CONDITION))) {
                    //Only change the saved location when it was a manual change by the user and not the animation or the window opening initially
                    previousDividerLocation = (int) evt.getNewValue();
                }
            }
        }
        );

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
        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
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
        DiscoveryTopComponent discoveryTopComp = (DiscoveryTopComponent) WindowManager.getDefault().findTopComponent(PREFERRED_ID);
        return discoveryTopComp;
    }

    /**
     * Reset the top component so it isn't displaying any results.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public void resetTopComponent() {
        resultsPanel.resetResultViewer();
        groupListPanel.resetGroupList();
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    @Override
    public void componentOpened() {
        super.componentOpened();
        WindowManager.getDefault().setTopComponentFloating(this, true);
        DiscoveryEventUtils.getDiscoveryEventBus().register(this);
        DiscoveryEventUtils.getDiscoveryEventBus().register(resultsPanel);
        DiscoveryEventUtils.getDiscoveryEventBus().register(groupListPanel);
    }

    private void resetBottomComponent() {
        rightSplitPane.setBottomComponent(new JPanel());
        rightSplitPane.setDividerLocation(JSplitPane.UNDEFINED_CONDITION);
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    @Override
    protected void componentClosed() {
        DiscoveryDialog.getDiscoveryDialogInstance().cancelSearch();
        DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.ClearInstanceSelectionEvent());
        DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.CancelBackgroundTasksEvent());
        DiscoveryEventUtils.getDiscoveryEventBus().unregister(this);
        DiscoveryEventUtils.getDiscoveryEventBus().unregister(groupListPanel);
        DiscoveryEventUtils.getDiscoveryEventBus().unregister(resultsPanel);
        DiscoveryEventUtils.getDiscoveryEventBus().unregister(detailsPanel);
        if (detailsPanel instanceof DomainDetailsPanel) {
            ((DomainDetailsPanel) detailsPanel).unregister();
            selectedDomainTabName = ((DomainDetailsPanel) detailsPanel).getSelectedTabName();
        }
        resetBottomComponent();
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

        org.openide.awt.Mnemonics.setLocalizedText(newSearchButton, Bundle.DiscoveryTopComponent_cancelButton_text());
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
        SwingUtilities.invokeLater(() -> {
            if (animator != null && animator.isRunning()) {
                animator.stop();
                animator = null;
            }
            dividerLocation = rightSplitPane.getDividerLocation();
            if (detailsVisibleEvent.isShowDetailsArea()) {
                animator = new SwingAnimator(new ShowDetailsAreaCallback());
            } else {
                animator = new SwingAnimator(new HideDetailsAreaCallback());
            }
            animator.start();
        });
    }

    /**
     * Subscribe to the SearchStartedEvent for updating the UI accordingly.
     *
     * @param searchStartedEvent The event which indicates the start of a
     *                           search.
     */
    @Messages({"DiscoveryTopComponent.cancelButton.text=Cancel Search",
        "# {0} - searchType",
        "DiscoveryTopComponent.searchInProgress.text=Performing search for results of type {0}. Please wait.",
        "DiscoveryTopComponent.searchError.text=Error no type specified for search."})
    @Subscribe
    void handleSearchStartedEvent(DiscoveryEventUtils.SearchStartedEvent searchStartedEvent) {
        SwingUtilities.invokeLater(() -> {
            newSearchButton.setText(Bundle.DiscoveryTopComponent_cancelButton_text());
            progressMessageTextArea.setForeground(Color.red);
            searchType = searchStartedEvent.getType();
            progressMessageTextArea.setText(Bundle.DiscoveryTopComponent_searchInProgress_text(searchType.name()));
        });
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
        "DiscoveryTopComponent.searchComplete.text=Results with {0}",
        "DiscoveryTopComponent.domainSearch.text=Type: Domain",
        "DiscoveryTopComponent.additionalFilters.text=; "})
    void handleSearchCompleteEvent(DiscoveryEventUtils.SearchCompleteEvent searchCompleteEvent) {
        SwingUtilities.invokeLater(() -> {
            newSearchButton.setText(Bundle.DiscoveryTopComponent_newSearch_text());
            progressMessageTextArea.setForeground(Color.black);
            String descriptionText = "";
            if (searchType == DOMAIN) {
                //domain does not have a file type filter to add the type information so it is manually added
                descriptionText = Bundle.DiscoveryTopComponent_domainSearch_text();
                if (!searchCompleteEvent.getFilters().isEmpty()) {
                    descriptionText += Bundle.DiscoveryTopComponent_additionalFilters_text();
                }
                selectedDomainTabName = validateLastSelectedType(searchCompleteEvent);
                DomainDetailsPanel domainDetailsPanel = new DomainDetailsPanel();
                domainDetailsPanel.configureArtifactTabs(selectedDomainTabName);
                detailsPanel = domainDetailsPanel;
            } else {
                detailsPanel = new FileDetailsPanel();
            }
            rightSplitPane.setBottomComponent(detailsPanel);
            DiscoveryEventUtils.getDiscoveryEventBus().register(detailsPanel);
            descriptionText += searchCompleteEvent.getFilters().stream().map(AbstractFilter::getDesc).collect(Collectors.joining("; "));
            progressMessageTextArea.setText(Bundle.DiscoveryTopComponent_searchComplete_text(descriptionText));
            progressMessageTextArea.setCaretPosition(0);
        });
    }

    /**
     * Get the name of the tab which was last selected unless the tab last
     * selected would not be included in the types currently being displayed or
     * was not previously set.
     *
     * @return The name of the tab which should be selected in the new
     *         DomainDetailsPanel.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private String validateLastSelectedType(DiscoveryEventUtils.SearchCompleteEvent searchCompleteEvent) {
        String typeFilteredOn = selectedDomainTabName;

        for (AbstractFilter filter : searchCompleteEvent.getFilters()) {
            if (filter instanceof ArtifactTypeFilter) {
                for (ARTIFACT_TYPE type : ((ArtifactTypeFilter) filter).getTypes()) {
                    typeFilteredOn = type.getDisplayName();
                    if (selectedDomainTabName == null || typeFilteredOn.equalsIgnoreCase(selectedDomainTabName)) {
                        break;
                    }
                }
                break;
            }
        }
        return typeFilteredOn;
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
        SwingUtilities.invokeLater(() -> {
            newSearchButton.setText(Bundle.DiscoveryTopComponent_newSearch_text());
            progressMessageTextArea.setForeground(Color.red);
            progressMessageTextArea.setText(Bundle.DiscoveryTopComponent_searchCancelled_text());
        });

    }

    /**
     * Callback for animating the details area into a visible position.
     */
    private final class ShowDetailsAreaCallback implements SwingAnimatorCallback {

        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        @Override
        public void callback(Object caller) {
            dividerLocation -= ANIMATION_INCREMENT;
            repaint();
        }

        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        @Override
        public boolean hasTerminated() {
            if (dividerLocation != JSplitPane.UNDEFINED_CONDITION && dividerLocation < previousDividerLocation) {
                dividerLocation = previousDividerLocation;
                animator = null;
                return true;
            }
            return false;
        }

    }

    /**
     * Callback for animating the details area into a hidden position.
     */
    private final class HideDetailsAreaCallback implements SwingAnimatorCallback {

        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        @Override
        public void callback(Object caller) {
            dividerLocation += ANIMATION_INCREMENT;
            repaint();
        }

        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        @Override
        public boolean hasTerminated() {
            if (dividerLocation > rightSplitPane.getHeight() || dividerLocation == JSplitPane.UNDEFINED_CONDITION) {
                dividerLocation = rightSplitPane.getHeight();
                animator = null;
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

        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        @Override
        public void paintComponent(Graphics g) {
            if (animator != null && animator.isRunning() && (dividerLocation == JSplitPane.UNDEFINED_CONDITION
                    || (dividerLocation <= getHeight() && dividerLocation >= previousDividerLocation))) {
                setDividerLocation(dividerLocation);
            }
            super.paintComponent(g);
        }

    }

}
