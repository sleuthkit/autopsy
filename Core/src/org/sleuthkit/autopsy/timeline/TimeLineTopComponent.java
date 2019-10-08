/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2019 Basis Technology Corp.
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

import com.google.common.collect.ImmutableList;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
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
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import static javax.swing.SwingUtilities.isDescendingFrom;
import org.controlsfx.control.Notifications;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormatter;
import org.openide.explorer.ExplorerManager;
import static org.openide.explorer.ExplorerUtils.createLookup;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.windows.Mode;
import org.openide.windows.RetainLocation;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.actions.AddBookmarkTagAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;
import org.sleuthkit.autopsy.corecomponents.DataContentPanel;
import org.sleuthkit.autopsy.corecomponents.DataResultPanel;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.directorytree.ExternalViewerShortcutAction;
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
import org.sleuthkit.datamodel.VersionNumber;

/**
 * TopComponent for the Timeline feature.
 */
@TopComponent.Description(
        preferredID = "TimeLineTopComponent",
        //iconBase="SET/PATH/TO/ICON/HERE", //use this to put icon in window title area,
        persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "timeline", openAtStartup = false)
@RetainLocation("timeline")
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public final class TimeLineTopComponent extends TopComponent implements ExplorerManager.Provider {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(TimeLineTopComponent.class.getName());

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private final DataContentExplorerPanel contentViewerPanel;

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private final DataResultPanel dataResultPanel;

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private final ExplorerManager explorerManager = new ExplorerManager();

    private TimeLineController controller;

    /**
     * Lookup that will be exposed through the (Global Actions Context)
     */
    private final ModifiableProxyLookup proxyLookup = new ModifiableProxyLookup();

    private final PropertyChangeListener focusPropertyListener = new PropertyChangeListener() {
        /**
         * Listener that keeps the proxyLookup in sync with the focused area of
         * the UI.
         *
         * Since the embedded MessageContentViewer (attachments panel) inside
         * the DataContentPanel is not in its own TopComponenet, its selection
         * does not get proxied into the Global Actions Context (GAC)
         * automatically, and many of the available actions don't work on it.
         * Further, we can't put the selection from both the Result table and
         * the Attachments table in the GAC because they could bouth include
         * AbstractFiles, muddling the selection seen by the actions. Instead,
         * depending on where the focus is in the window, we want to put
         * different Content in the Global Actions Context to be picked up by,
         * e.g., the tagging actions. The best way I could figure to do this was
         * to listen to all focus events and swap out what is in the lookup
         * appropriately. An alternative to this would be to investigate using
         * the ContextAwareAction interface.
         *
         * @see org.sleuthkit.autopsy.communications.MessageBrowser for a
         * similar situation and a similar solution.
         *
         * @param focusEvent The focus change event.
         */
        @Override
        public void propertyChange(final PropertyChangeEvent focusEvent) {
            if (focusEvent.getPropertyName().equalsIgnoreCase("focusOwner")) {
                final Component newFocusOwner = (Component) focusEvent.getNewValue();

                if (newFocusOwner == null) {
                    return;
                }
                if (isDescendingFrom(newFocusOwner, contentViewerPanel)) {
                    //if the focus owner is within the MessageContentViewer (the attachments table)
                    proxyLookup.setNewLookups(createLookup(contentViewerPanel.getExplorerManager(), getActionMap()));
                } else if (isDescendingFrom(newFocusOwner, TimeLineTopComponent.this)) {
                    //... or if it is within the Results table.
                    proxyLookup.setNewLookups(createLookup(explorerManager, getActionMap()));

                }
            }
        }
    };

    @NbBundle.Messages({"TimelineTopComponent.selectedEventListener.errorMsg=There was a problem getting the content for the selected event."})
    private final InvalidationListener selectedEventsListener = new InvalidationListener() {
        /**
         * Listener that drives the result viewer or content viewer (depending
         * on view mode) according to the controller's selected event IDs
         *
         * @param observable Observable that was invalidated. Usually
         *                   irrelevant.
         */
        @Override
        public void invalidated(Observable observable) {
            // make a copy because this list gets updated as the user navigates around
            // and causes concurrent access exceptions
            List<Long> selectedEventIDs = ImmutableList.copyOf(controller.getSelectedEventIDs());

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
                            explorerManager.setRootContext(new AbstractNode(children));
                            try {
                                //set selected nodes for actions
                                explorerManager.setSelectedNodes(childArray);
                            } catch (PropertyVetoException ex) {
                                //I don't know why this would ever happen.
                                logger.log(Level.SEVERE, "Selecting the event node was vetoed.", ex); // NON-NLS
                            }
                            //if there is only one event selected push it into content viewer.
                            if (childArray.length == 1) {
                                contentViewerPanel.setNode(childArray[0]);
                            } else {
                                contentViewerPanel.setNode(null);
                            }
                        });
                    } catch (TskCoreException ex) {
                        logger.log(Level.SEVERE, "Failed to lookup Sleuthkit object backing a SingleEvent.", ex); // NON-NLS
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
                    if (horizontalSplitPane.getParent() != splitYPane) {
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
     * Constructs a "shell" version of the top component for this Timeline feature
     * which has only Swing components, no controller, and no listeners.
     * This constructor conforms to the NetBeans window system requirements that
     * all top components have a public, no argument constructor.
     *
     */
    public TimeLineTopComponent() {
        initComponents();
        associateLookup(proxyLookup);
        setName(NbBundle.getMessage(TimeLineTopComponent.class, "CTL_TimeLineTopComponent"));

        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(AddBookmarkTagAction.BOOKMARK_SHORTCUT, "addBookmarkTag"); //NON-NLS
        getActionMap().put("addBookmarkTag", new AddBookmarkTagAction()); //NON-NLS
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ExternalViewerShortcutAction.EXTERNAL_VIEWER_SHORTCUT, "useExternalViewer"); //NON-NLS 
        getActionMap().put("useExternalViewer", ExternalViewerShortcutAction.getInstance()); //NON-NLS

        //create linked result and content views
        contentViewerPanel = new DataContentExplorerPanel();
        dataResultPanel = DataResultPanel.createInstanceUninitialized("", "", Node.EMPTY, 0, contentViewerPanel);

        //add them to bottom splitpane
        horizontalSplitPane.setLeftComponent(dataResultPanel);
        horizontalSplitPane.setRightComponent(contentViewerPanel);

        dataResultPanel.open(); //get the explorermanager
        contentViewerPanel.initialize();
    }
    
    /**
     * Constructs a full functional top component for the Timeline feature.
     * 
     * @param controller The TimeLineController for ths top compenent.
     */
    public TimeLineTopComponent(TimeLineController controller) {
        this();
        
        this.controller = controller;
        
        Platform.runLater(this::initFXComponents);

        //set up listeners 
        TimeLineController.timeZoneProperty().addListener(timeZone -> dataResultPanel.setPath(getResultViewerSummaryString()));
        controller.getSelectedEventIDs().addListener(selectedEventsListener);

        //Listen to ViewMode and adjust GUI componenets as needed.
        controller.viewModeProperty().addListener(viewMode -> syncViewMode());
        syncViewMode();

        //add listener that maintains correct selection in the Global Actions Context
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addPropertyChangeListener("focusOwner", focusPropertyListener);
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

        // There is a bug in the sort of the EventsTree, it doesn't seem to 
        //sort anything.  For now removing from tabpane until fixed
        final TabPane leftTabPane = new TabPane(filterTab);
        VBox.setVgrow(leftTabPane, Priority.ALWAYS);
        controller.viewModeProperty().addListener(viewMode -> {
            if (controller.getViewMode() != ViewMode.DETAIL) {
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
            } else if (new KeyCodeCombination(KeyCode.RIGHT, KeyCodeCombination.ALT_DOWN).match(keyEvent)) {
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

    @NbBundle.Messages ({
        "Timeline.old.version= This Case was created with an older version of Autopsy.\nThe Timeline with not show events from data sources added with the older version of Autopsy"
    })
    @Override
    public void componentOpened() {
        super.componentOpened();
        WindowManager.getDefault().setTopComponentFloating(this, true);

        //add listener that maintains correct selection in the Global Actions Context
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addPropertyChangeListener("focusOwner", focusPropertyListener);
        
        VersionNumber version = Case.getCurrentCase().getSleuthkitCase().getDBSchemaCreationVersion();
        int major = version.getMajor();
        int minor = version.getMinor();

        if(major < 8 || (major == 8 && minor <= 2)) {
            Platform.runLater(() -> {
                Notifications.create()
                        .owner(jFXViewPanel.getScene().getWindow())
                        .text(Bundle.Timeline_old_version()).showInformation();
            });
        }
    }

    @Override
    protected void componentClosed() {
        super.componentClosed();
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removePropertyChangeListener("focusOwner", focusPropertyListener);
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
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

    /**
     * Panel that wraps a DataContentPanel and implements
     * ExplorerManager.Provider. This allows the explorer manager found by the
     * DataContentPanel to be controlled easily.
     *
     * @see org.sleuthkit.autopsy.communications.MessageDataContent for another
     * solution to a very similar problem.
     */
    final private static class DataContentExplorerPanel extends JPanel implements ExplorerManager.Provider, DataContent {

        private final ExplorerManager explorerManager = new ExplorerManager();
        private final DataContentPanel wrapped;

        private DataContentExplorerPanel() {
            super(new BorderLayout());
            wrapped = DataContentPanel.createInstance();
        }

        @Override
        public ExplorerManager getExplorerManager() {
            return explorerManager;
        }

        @Override
        public void setNode(Node selectedNode) {
            wrapped.setNode(selectedNode);
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            wrapped.propertyChange(evt);
        }

        /**
         * Initialize the contents of this panel for use. Specifically add the
         * wrapped DataContentPanel to the AWT/Swing containment hierarchy. This
         * will trigger the addNotify() method of the embeded Message
         * MessageContentViewer causing it to look for a ExplorerManager; it
         * should find the one provided by this DataContentExplorerPanel.
         */
        private void initialize() {
            add(wrapped, BorderLayout.CENTER);
        }
    }
}
