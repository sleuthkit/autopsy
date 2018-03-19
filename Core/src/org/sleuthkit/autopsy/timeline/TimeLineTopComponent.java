/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline;

import java.beans.PropertyVetoException;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.controlsfx.control.Notifications;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormatter;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.windows.Mode;
import org.openide.windows.RetainLocation;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.actions.AddBookmarkTagAction;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponents.DataContentPanel;
import org.sleuthkit.autopsy.corecomponents.DataResultPanel;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.actions.Back;
import org.sleuthkit.autopsy.timeline.actions.Forward;
import org.sleuthkit.autopsy.timeline.explorernodes.EventNode;
import org.sleuthkit.autopsy.timeline.explorernodes.EventRootNode;
import org.sleuthkit.autopsy.timeline.ui.HistoryToolBar;
import org.sleuthkit.autopsy.timeline.ui.StatusBar;
import org.sleuthkit.autopsy.timeline.ui.TimeZonePanel;
import org.sleuthkit.autopsy.timeline.ui.ViewFrame;
import org.sleuthkit.autopsy.timeline.ui.detailview.tree.EventsTree;
import org.sleuthkit.autopsy.timeline.ui.filtering.FilterSetPanel;
import org.sleuthkit.autopsy.timeline.zooming.ZoomSettingsPane;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * TopComponent for the Timeline feature.
 */
@TopComponent.Description(
        preferredID = "TimeLineTopComponent",
        //iconBase="SET/PATH/TO/ICON/HERE", //use this to put icon in window title area,
        persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "timeline", openAtStartup = false)
@RetainLocation("timeline")
public final class TimeLineTopComponent extends TopComponent implements ExplorerManager.Provider {

    private static final Logger LOGGER = Logger.getLogger(TimeLineTopComponent.class.getName());

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private final DataContentPanel contentViewerPanel;

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private DataResultPanel dataResultPanel;

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private final ExplorerManager em = new ExplorerManager();

    private final TimeLineController controller;

    /**
     * Listener that drives the result viewer or content viewer (depending on
     * view mode) according to the controller's selected event IDs
     */
    @NbBundle.Messages({"TimelineTopComponent.selectedEventListener.errorMsg=There was a problem getting the content for the selected event."})
    private final InvalidationListener selectedEventsListener = new InvalidationListener() {
        @Override
        public void invalidated(Observable observable) {
            List<Long> selectedEventIDs = controller.getSelectedEventIDs();

            //depending on the active view mode, we either update the dataResultPanel, or update the contentViewerPanel directly.
            switch (controller.getViewMode()) {
                case LIST:
                    //make an array of EventNodes for the selected events
                    EventNode[] childArray = new EventNode[selectedEventIDs.size()];
                    try {
                        for (int i = 0; i < selectedEventIDs.size(); i++) {
                            childArray[i] = EventNode.createEventNode(selectedEventIDs.get(i), controller.getEventsModel());
                        }
                        Children children = new Children.Array();
                        children.add(childArray);

                        SwingUtilities.invokeLater(() -> {
                            //set generic container node as root context 
                            em.setRootContext(new AbstractNode(children));
                            try {
                                //set selected nodes for actions
                                em.setSelectedNodes(childArray);
                            } catch (PropertyVetoException ex) {
                                //I don't know why this would ever happen.
                                LOGGER.log(Level.SEVERE, "Selecting the event node was vetoed.", ex); // NON-NLS
                            }
                            //if there is only one event selected push it into content viewer.
                            if (childArray.length == 1) {
                                contentViewerPanel.setNode(childArray[0]);
                            } else {
                                contentViewerPanel.setNode(null);
                            }
                        });
                    } catch (NoCurrentCaseException ex) {
                        //Since the case is closed, the user probably doesn't care about this, just log it as a precaution.
                        LOGGER.log(Level.SEVERE, "There was no case open to lookup the Sleuthkit object backing a SingleEvent.", ex); // NON-NLS
                    } catch (TskCoreException ex) {
                        LOGGER.log(Level.SEVERE, "Failed to lookup Sleuthkit object backing a SingleEvent.", ex); // NON-NLS
                        Platform.runLater(() -> {
                            Notifications.create()
                                    .owner(jFXViewPanel.getScene().getWindow())
                                    .text(Bundle.TimelineTopComponent_selectedEventListener_errorMsg())
                                    .showError();
                        });
                    }
                    break;
                case COUNTS:
                case DETAIL:
                    //make a root node with nodes for the selected events as children and push it to the result viewer.
                    EventRootNode rootNode = new EventRootNode(selectedEventIDs, controller.getEventsModel());
                    TableFilterNode tableFilterNode = new TableFilterNode(rootNode, true, "Event");
                    SwingUtilities.invokeLater(() -> {
                        dataResultPanel.setPath(getResultViewerSummaryString());
                        dataResultPanel.setNode(tableFilterNode);
                    });
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown view mode: " + controller.getViewMode());
            }
        }
    };

    private void syncViewMode() {
        switch (controller.getViewMode()) {
            case COUNTS:
            case DETAIL:
                /*
                 * For counts and details mode, restore the result table at the
                 * bottom left.
                 */
                SwingUtilities.invokeLater(() -> {
                    splitYPane.remove(contentViewerPanel);
                    if ((horizontalSplitPane.getParent() == splitYPane) == false) {
                        splitYPane.setBottomComponent(horizontalSplitPane);
                        horizontalSplitPane.setRightComponent(contentViewerPanel);
                    }
                });
                break;
            case LIST:
                /*
                 * For list mode, remove the result table, and let the content
                 * viewer expand across the bottom.
                 */
                SwingUtilities.invokeLater(() -> splitYPane.setBottomComponent(contentViewerPanel));
                break;
            default:
                throw new UnsupportedOperationException("Unknown ViewMode: " + controller.getViewMode());
        }
    }

    /**
     * Constructor
     *
     * @param controller The TimeLineController for this topcomponent.
     */
    public TimeLineTopComponent(TimeLineController controller) {
        initComponents();
        associateLookup(ExplorerUtils.createLookup(em, getActionMap()));
        setName(NbBundle.getMessage(TimeLineTopComponent.class, "CTL_TimeLineTopComponent"));

        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(AddBookmarkTagAction.BOOKMARK_SHORTCUT, "addBookmarkTag"); //NON-NLS
        getActionMap().put("addBookmarkTag", new AddBookmarkTagAction()); //NON-NLS

        this.controller = controller;

        //create linked result and content views
        contentViewerPanel = DataContentPanel.createInstance();
        dataResultPanel = DataResultPanel.createInstanceUninitialized("", "", Node.EMPTY, 0, contentViewerPanel);

        //add them to bottom splitpane
        horizontalSplitPane.setLeftComponent(dataResultPanel);
        horizontalSplitPane.setRightComponent(contentViewerPanel);

        dataResultPanel.open(); //get the explorermanager

        Platform.runLater(this::initFXComponents);

        //set up listeners 
        TimeLineController.getTimeZone().addListener(timeZone -> dataResultPanel.setPath(getResultViewerSummaryString()));
        controller.getSelectedEventIDs().addListener(selectedEventsListener);

        //Listen to ViewMode and adjust GUI componenets as needed.
        controller.viewModeProperty().addListener(viewMode -> syncViewMode());
        syncViewMode();
    }

    /**
     * Create and wire up JavaFX components of the interface
     */
    @NbBundle.Messages({
        "TimeLineTopComponent.eventsTab.name=Events",
        "TimeLineTopComponent.filterTab.name=Filters"})
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void initFXComponents() {
        /////init componenets of left most column from top to bottom
        final TimeZonePanel timeZonePanel = new TimeZonePanel();
        VBox.setVgrow(timeZonePanel, Priority.SOMETIMES);
        HistoryToolBar historyToolBar = new HistoryToolBar(controller);
        final ZoomSettingsPane zoomSettingsPane = new ZoomSettingsPane(controller);

        //set up filter tab
        final Tab filterTab = new Tab(Bundle.TimeLineTopComponent_filterTab_name(), new FilterSetPanel(controller));
        filterTab.setClosable(false);
        filterTab.setGraphic(new ImageView("org/sleuthkit/autopsy/timeline/images/funnel.png")); // NON-NLS

        //set up events tab
        final EventsTree eventsTree = new EventsTree(controller);
        final Tab eventsTreeTab = new Tab(Bundle.TimeLineTopComponent_eventsTab_name(), eventsTree);
        eventsTreeTab.setClosable(false);
        eventsTreeTab.setGraphic(new ImageView("org/sleuthkit/autopsy/timeline/images/timeline_marker.png")); // NON-NLS
        eventsTreeTab.disableProperty().bind(controller.viewModeProperty().isNotEqualTo(ViewMode.DETAIL));

        final TabPane leftTabPane = new TabPane(filterTab, eventsTreeTab);
        VBox.setVgrow(leftTabPane, Priority.ALWAYS);
        controller.viewModeProperty().addListener(viewMode -> {
            if (controller.getViewMode().equals(ViewMode.DETAIL) == false) {
                //if view mode is not details, switch back to the filter tab
                leftTabPane.getSelectionModel().select(filterTab);
            }
        });

        //assemble left column
        final VBox leftVBox = new VBox(5, timeZonePanel, historyToolBar, zoomSettingsPane, leftTabPane);
        SplitPane.setResizableWithParent(leftVBox, Boolean.FALSE);

        final ViewFrame viewFrame = new ViewFrame(controller, eventsTree);
        final SplitPane mainSplitPane = new SplitPane(leftVBox, viewFrame);
        mainSplitPane.setDividerPositions(0);

        final Scene scene = new Scene(mainSplitPane);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyEvent -> {
            if (new KeyCodeCombination(KeyCode.LEFT, KeyCodeCombination.ALT_DOWN).match(keyEvent)) {
                new Back(controller).handle(null);
            } else if (new KeyCodeCombination(KeyCode.BACK_SPACE).match(keyEvent)) {
                new Back(controller).handle(null);
            } else if (new KeyCodeCombination(KeyCode.RIGHT, KeyCodeCombination.ALT_DOWN).match(keyEvent)) {
                new Forward(controller).handle(null);
            } else if (new KeyCodeCombination(KeyCode.BACK_SPACE, KeyCodeCombination.SHIFT_DOWN).match(keyEvent)) {
                new Forward(controller).handle(null);
            }
        });

        //add ui componenets to JFXPanels
        jFXViewPanel.setScene(scene);
        jFXstatusPanel.setScene(new Scene(new StatusBar(controller)));
    }

    @Override
    public List<Mode> availableModes(List<Mode> modes) {
        /*
         * This looks like the right thing to do, but online discussions seems
         * to indicate this method is effectively deprecated. A break point
         * placed here was never hit.
         */
        return modes.stream().filter(mode -> mode.getName().equals("timeline") || mode.getName().equals("ImageGallery"))
                .collect(Collectors.toList());
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jFXstatusPanel = new javafx.embed.swing.JFXPanel();
        splitYPane = new javax.swing.JSplitPane();
        jFXViewPanel = new javafx.embed.swing.JFXPanel();
        horizontalSplitPane = new javax.swing.JSplitPane();
        leftFillerPanel = new javax.swing.JPanel();
        rightfillerPanel = new javax.swing.JPanel();

        jFXstatusPanel.setPreferredSize(new java.awt.Dimension(100, 16));

        splitYPane.setDividerLocation(420);
        splitYPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        splitYPane.setResizeWeight(0.9);
        splitYPane.setPreferredSize(new java.awt.Dimension(1024, 400));
        splitYPane.setLeftComponent(jFXViewPanel);

        horizontalSplitPane.setDividerLocation(600);
        horizontalSplitPane.setResizeWeight(0.5);
        horizontalSplitPane.setPreferredSize(new java.awt.Dimension(1200, 300));
        horizontalSplitPane.setRequestFocusEnabled(false);

        javax.swing.GroupLayout leftFillerPanelLayout = new javax.swing.GroupLayout(leftFillerPanel);
        leftFillerPanel.setLayout(leftFillerPanelLayout);
        leftFillerPanelLayout.setHorizontalGroup(
            leftFillerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 599, Short.MAX_VALUE)
        );
        leftFillerPanelLayout.setVerticalGroup(
            leftFillerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 54, Short.MAX_VALUE)
        );

        horizontalSplitPane.setLeftComponent(leftFillerPanel);

        javax.swing.GroupLayout rightfillerPanelLayout = new javax.swing.GroupLayout(rightfillerPanel);
        rightfillerPanel.setLayout(rightfillerPanelLayout);
        rightfillerPanelLayout.setHorizontalGroup(
            rightfillerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 364, Short.MAX_VALUE)
        );
        rightfillerPanelLayout.setVerticalGroup(
            rightfillerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 54, Short.MAX_VALUE)
        );

        horizontalSplitPane.setRightComponent(rightfillerPanel);

        splitYPane.setRightComponent(horizontalSplitPane);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(splitYPane, javax.swing.GroupLayout.DEFAULT_SIZE, 972, Short.MAX_VALUE)
            .addComponent(jFXstatusPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(splitYPane, javax.swing.GroupLayout.DEFAULT_SIZE, 482, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(jFXstatusPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSplitPane horizontalSplitPane;
    private javafx.embed.swing.JFXPanel jFXViewPanel;
    private javafx.embed.swing.JFXPanel jFXstatusPanel;
    private javax.swing.JPanel leftFillerPanel;
    private javax.swing.JPanel rightfillerPanel;
    private javax.swing.JSplitPane splitYPane;
    // End of variables declaration//GEN-END:variables

    @Override
    public void componentOpened() {
        super.componentOpened();
        WindowManager.getDefault().setTopComponentFloating(this, true);
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return em;
    }

    /**
     * Get the string that should be used as the label above the result table.
     * It displays the time range spanned by the selected events.
     *
     * @return A String representation of all the events displayed.
     */
    @NbBundle.Messages({
        "# {0} - start of date range",
        "# {1} - end of date range",
        "TimeLineResultView.startDateToEndDate.text={0} to {1}"})
    private String getResultViewerSummaryString() {
        Interval selectedTimeRange = controller.getSelectedTimeRange();
        if (selectedTimeRange == null) {
            return "";
        } else {
            final DateTimeFormatter zonedFormatter = TimeLineController.getZonedFormatter();
            String start = selectedTimeRange.getStart()
                    .withZone(TimeLineController.getJodaTimeZone())
                    .toString(zonedFormatter);
            String end = selectedTimeRange.getEnd()
                    .withZone(TimeLineController.getJodaTimeZone())
                    .toString(zonedFormatter);
            return Bundle.TimeLineResultView_startDateToEndDate_text(start, end);
        }
    }
}
