/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-19 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.Lighting;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import static javafx.scene.layout.Region.USE_PREF_SIZE;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Callback;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import jfxtras.scene.control.LocalDateTimePicker;
import jfxtras.scene.control.LocalDateTimeTextField;
import jfxtras.scene.control.ToggleGroupValue;
import org.controlsfx.control.NotificationPane;
import org.controlsfx.control.Notifications;
import org.controlsfx.control.RangeSlider;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.EventsModel;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ViewMode;
import org.sleuthkit.autopsy.timeline.actions.AddManualEvent;
import org.sleuthkit.autopsy.timeline.actions.Back;
import org.sleuthkit.autopsy.timeline.actions.ResetFilters;
import org.sleuthkit.autopsy.timeline.actions.SaveSnapshotAsReport;
import org.sleuthkit.autopsy.timeline.actions.ZoomIn;
import org.sleuthkit.autopsy.timeline.actions.ZoomOut;
import org.sleuthkit.autopsy.timeline.actions.ZoomToEvents;
import org.sleuthkit.autopsy.timeline.events.RefreshRequestedEvent;
import org.sleuthkit.autopsy.timeline.events.TagsUpdatedEvent;
import org.sleuthkit.autopsy.timeline.ui.countsview.CountsViewPane;
import org.sleuthkit.autopsy.timeline.ui.detailview.DetailViewPane;
import org.sleuthkit.autopsy.timeline.ui.detailview.tree.EventsTree;
import org.sleuthkit.autopsy.timeline.ui.listvew.ListViewPane;
import org.sleuthkit.autopsy.timeline.utils.RangeDivision;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A container for an AbstractTimelineView. Has a Toolbar on top to hold
 * settings widgets supplied by contained AbstractTimelineView, and the
 * histogram / time selection on bottom. The time selection Toolbar has default
 * controls that can be replaced by ones supplied by the current view.
 *
 * TODO: Refactor common code out of histogram and CountsView? -jm
 */
final public class ViewFrame extends BorderPane {

    private static final Logger logger = Logger.getLogger(ViewFrame.class.getName());

    private static final Image WARNING = new Image("org/sleuthkit/autopsy/timeline/images/warning_triangle.png", 16, 16, true, true); //NON-NLS
    private static final Image REFRESH = new Image("org/sleuthkit/autopsy/timeline/images/arrow-circle-double-135.png"); //NON-NLS
    private static final Background GRAY_BACKGROUND = new Background(new BackgroundFill(Color.GREY, CornerRadii.EMPTY, Insets.EMPTY));

    /**
     * Region that will be stacked in between the no-events "dialog" and the
     * hosted AbstractTimelineView in order to gray out the
     * AbstractTimelineView.
     */
    private final static Region NO_EVENTS_BACKGROUND = new Region() {
        {
            setBackground(GRAY_BACKGROUND);
            setOpacity(.3);
        }
    };

    /**
     * The scene graph Nodes for the current view's settings will be inserted
     * into the toolbar at this index.
     */
    private static final int SETTINGS_TOOLBAR_INSERTION_INDEX = 2;

    /**
     * The scene graph Nodes for the current view's time navigation controls
     * will be inserted into the toolbar at this index.
     */
    private static final int TIME_TOOLBAR_INSERTION_INDEX = 2;

    @GuardedBy("this")
    private LoggedTask<Void> histogramTask;

    private final EventsTree eventsTree;
    private AbstractTimeLineView hostedView;

    /*
     * HBox that contains the histogram bars.
     *
     * //TODO: Abstract this into a seperate class, and/or use a real bar
     * chart? -jm
     */
    @FXML
    private HBox histogramBox;
    /*
     * Stack pane that superimposes rangeslider over histogram
     */
    @FXML
    private StackPane rangeHistogramStack;

    private final RangeSlider rangeSlider = new RangeSlider(0, 1.0, .25, .75);

    /**
     * The lower tool bar that has controls to adjust the viewed timerange.
     */
    @FXML
    private ToolBar timeRangeToolBar;

    /**
     * Parent for the default zoom in/out buttons that can be replaced in some
     * views(eg List View)
     */
    @FXML
    private HBox zoomInOutHBox;

    //// time range selection components
    @FXML
    private MenuButton zoomMenuButton;
    @FXML
    private Button zoomOutButton;
    @FXML
    private Button zoomInButton;
    @FXML
    private LocalDateTimeTextField startPicker;
    @FXML
    private LocalDateTimeTextField endPicker;
    @FXML
    private Label startLabel;
    @FXML
    private Label endLabel;

    //// header toolbar componenets
    @FXML
    private ToolBar toolBar;

    private ToggleGroupValue<ViewMode> viewModeToggleGroup;
    @FXML
    private Label viewModeLabel;
    @FXML
    private SegmentedButton modeSegButton;
    @FXML
    private ToggleButton countsToggle;
    @FXML
    private ToggleButton detailsToggle;
    @FXML
    private ToggleButton listToggle;

    @FXML
    private Button addEventButton;
    @FXML
    private Button snapShotButton;
    @FXML
    private Button refreshButton;

    /*
     * Default zoom in/out buttons provided by the ViewFrame, some views replace
     * these with other nodes (eg, list view)
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private ImmutableList<Node> defaultTimeNavigationNodes;

    /*
     * The settings nodes for the current view.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final ObservableList<Node> settingsNodes = FXCollections.observableArrayList();

    /*
     * The time nagivation nodes for the current view.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final ObservableList<Node> timeNavigationNodes = FXCollections.observableArrayList();

    /**
     * Wraps the contained AbstractTimelineView so that we can show
     * notifications over it.
     */
    private final NotificationPane notificationPane = new NotificationPane();

    private final TimeLineController controller;
    private final EventsModel filteredEvents;

    /**
     * Listen to changes in the range slider selection and forward to the
     * controller. Waits until the user releases thumb to send to controller.
     */
    @NbBundle.Messages({
        "ViewFrame.rangeSliderListener.errorMessage=Error responding to range slider."})
    private final InvalidationListener rangeSliderListener = new InvalidationListener() {
        @Override
        public void invalidated(Observable observable) {
            if (rangeSlider.isHighValueChanging() == false
                && rangeSlider.isLowValueChanging() == false) {
                try {
                    Long minTime = RangeDivision.getRangeDivision(filteredEvents.getSpanningInterval(), TimeLineController.getJodaTimeZone()).getLowerBound();
                    if (false == controller.pushTimeRange(new Interval(
                            (long) (rangeSlider.getLowValue() + minTime),
                            (long) (rangeSlider.getHighValue() + minTime + 1000)))) {
                        refreshTimeUI();
                    }
                } catch (TskCoreException ex) {
                    Notifications.create().owner(getScene().getWindow())
                            .text(Bundle.ViewFrame_rangeSliderListener_errorMessage())
                            .showError();
                    logger.log(Level.SEVERE, "Error responding to range slider.", ex);
                }
            }
        }
    };

    /**
     * hides the notification pane on any event
     */
    private final InvalidationListener zoomListener = any -> handleRefreshRequested(null);

    /**
     * listen to change in end time picker and push to controller
     */
    private final InvalidationListener endListener = new PickerListener(() -> endPicker, Interval::withEndMillis);

    /**
     * listen to change in start time picker and push to controller
     */
    private final InvalidationListener startListener = new PickerListener(() -> startPicker, Interval::withStartMillis);

    /**
     * Convert the given LocalDateTime to epoch millis USING THE CURRENT
     * TIMEZONE FROM THE TIMELINECONTROLLER
     *
     * @param localDateTime The LocalDateTime to convert to millis since the
     *                      Unix epoch.
     *
     * @return the given LocalDateTime as epoch millis
     */
    private static long localDateTimeToEpochMilli(LocalDateTime localDateTime) {
        return localDateTime.atZone(TimeLineController.getTimeZoneID()).toInstant().toEpochMilli();
    }

    /**
     * Convert the given "millis from the Unix Epoch" to a LocalDateTime USING
     * THE CURRENT TIMEZONE FROM THE TIMELINECONTROLLER
     *
     * @param millis The milliseconds to convert.
     *
     * @return The given epoch millis as a LocalDateTime
     */
    private static LocalDateTime epochMillisToLocalDateTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), TimeLineController.getTimeZoneID());
    }

    /**
     * Constructor
     *
     * @param controller The TimeLineController for this ViewFrame
     * @param eventsTree The EventsTree this ViewFrame hosts.
     */
    public ViewFrame(@Nonnull TimeLineController controller, @Nonnull EventsTree eventsTree) {
        this.controller = controller;
        this.filteredEvents = controller.getEventsModel();
        this.eventsTree = eventsTree;
        FXMLConstructor.construct(this, "ViewFrame.fxml"); //NON-NLS

    }

    @FXML
    @NbBundle.Messages({
        "ViewFrame.viewModeLabel.text=View Mode:",
        "ViewFrame.startLabel.text=Start:",
        "ViewFrame.endLabel.text=End:",
        "ViewFrame.countsToggle.text=Counts",
        "ViewFrame.detailsToggle.text=Details",
        "ViewFrame.listToggle.text=List",
        "ViewFrame.zoomMenuButton.text=Zoom in/out to",
        "ViewFrame.zoomMenuButton.errorMessage=Error pushing time range.",
        "ViewFrame.tagsAddedOrDeleted=Tags have been created and/or deleted.  The view may not be up to date."
    })
    void initialize() {
        assert endPicker != null : "fx:id=\"endPicker\" was not injected: check your FXML file 'ViewWrapper.fxml'."; //NON-NLS
        assert histogramBox != null : "fx:id=\"histogramBox\" was not injected: check your FXML file 'ViewWrapper.fxml'."; //NON-NLS
        assert startPicker != null : "fx:id=\"startPicker\" was not injected: check your FXML file 'ViewWrapper.fxml'."; //NON-NLS
        assert rangeHistogramStack != null : "fx:id=\"rangeHistogramStack\" was not injected: check your FXML file 'ViewWrapper.fxml'."; //NON-NLS
        assert countsToggle != null : "fx:id=\"countsToggle\" was not injected: check your FXML file 'VisToggle.fxml'."; //NON-NLS
        assert detailsToggle != null : "fx:id=\"eventsToggle\" was not injected: check your FXML file 'VisToggle.fxml'."; //NON-NLS

        defaultTimeNavigationNodes = ImmutableList.of(zoomInOutHBox, zoomMenuButton);
        timeNavigationNodes.setAll(defaultTimeNavigationNodes);

        //configure notification pane 
        notificationPane.getStyleClass().add(NotificationPane.STYLE_CLASS_DARK);

        notificationPane.setGraphic(new ImageView(WARNING));
        setCenter(notificationPane);

        //configure view mode toggle
        viewModeLabel.setText(Bundle.ViewFrame_viewModeLabel_text());
        countsToggle.setText(Bundle.ViewFrame_countsToggle_text());
        detailsToggle.setText(Bundle.ViewFrame_detailsToggle_text());
        listToggle.setText(Bundle.ViewFrame_listToggle_text());
        viewModeToggleGroup = new ToggleGroupValue<>();
        viewModeToggleGroup.add(listToggle, ViewMode.LIST);
        viewModeToggleGroup.add(detailsToggle, ViewMode.DETAIL);
        viewModeToggleGroup.add(countsToggle, ViewMode.COUNTS);
        modeSegButton.setToggleGroup(viewModeToggleGroup);
        viewModeToggleGroup.valueProperty().addListener((observable, oldViewMode, newViewVode)
                -> controller.setViewMode(newViewVode != null ? newViewVode : (oldViewMode != null ? oldViewMode : ViewMode.COUNTS))
        );

        controller.viewModeProperty().addListener(viewMode -> syncViewMode());
        syncViewMode();

        ActionUtils.configureButton(new AddManualEvent(controller), addEventButton);
        ActionUtils.configureButton(new SaveSnapshotAsReport(controller, notificationPane::getContent), snapShotButton);

        /////configure start and end pickers
        startLabel.setText(Bundle.ViewFrame_startLabel_text());
        endLabel.setText(Bundle.ViewFrame_endLabel_text());

        //suppress stacktraces on malformed input
        //TODO: should we do anything else? show a warning?
        startPicker.setParseErrorCallback(throwable -> null);
        endPicker.setParseErrorCallback(throwable -> null);

        //disable dates outside scope of case
        LocalDateDisabler localDateDisabler = new LocalDateDisabler();
        startPicker.setLocalDateTimeRangeCallback(localDateDisabler);
        endPicker.setLocalDateTimeRangeCallback(localDateDisabler);

        //prevent selection of (date/)times outside the scope of this case
        startPicker.setValueValidationCallback(new LocalDateTimeValidator(startPicker));
        endPicker.setValueValidationCallback(new LocalDateTimeValidator(endPicker));

        //setup rangeslider
        rangeSlider.setOpacity(.7);
        rangeSlider.setMin(0);
        rangeSlider.setBlockIncrement(1);
        rangeHistogramStack.getChildren().add(rangeSlider);

        /*
         * This padding attempts to compensates for the fact that the
         * rangeslider track doesn't extend to edge of node,and so the
         * histrogram doesn't quite line up with the rangeslider
         */
        histogramBox.setStyle("   -fx-padding: 0,0.5em,0,.5em; "); //NON-NLS

        //configure zoom buttons
        zoomMenuButton.getItems().clear();
        for (ZoomRanges zoomRange : ZoomRanges.values()) {
            zoomMenuButton.getItems().add(ActionUtils.createMenuItem(
                    new Action(zoomRange.getDisplayName(), actionEvent -> {
                        try {
                            controller.pushPeriod(zoomRange.getPeriod());
                        } catch (TskCoreException ex) {
                            Notifications.create().owner(getScene().getWindow())
                                    .text(Bundle.ViewFrame_zoomMenuButton_errorMessage())
                                    .showError();
                            logger.log(Level.SEVERE, "Error pushing a time range.", ex);
                        }
                    })
            ));
        }
        zoomMenuButton.setText(Bundle.ViewFrame_zoomMenuButton_text());
        ActionUtils.configureButton(new ZoomOut(controller), zoomOutButton);
        ActionUtils.configureButton(new ZoomIn(controller), zoomInButton);

        //register for EventBus events (tags)
        filteredEvents.registerForEvents(this);

        //listen for changes in the time range / zoom params
        TimeLineController.timeZoneProperty().addListener(timeZoneProp -> refreshTimeUI());
        filteredEvents.timeRangeProperty().addListener(timeRangeProp -> refreshTimeUI());
        filteredEvents.modelParamsProperty().addListener(zoomListener);
        refreshTimeUI(); //populate the view

        refreshHistorgram();
    }

    /**
     * Handle TagsUpdatedEvents by marking that the view needs to be refreshed.
     *
     * NOTE: This ViewFrame must be registered with the filteredEventsModel's
     * EventBus in order for this handler to be invoked.
     *
     * @param event The TagsUpdatedEvent to handle.
     */
    @Subscribe
    public void handleTimeLineTagUpdate(TagsUpdatedEvent event) {
        Platform.runLater(() -> {
            hostedView.setNeedsRefresh();
            notificationPane.show(Bundle.ViewFrame_tagsAddedOrDeleted());
        });
    }

    /**
     * Handle a RefreshRequestedEvent from the events model by clearing the
     * refresh notification.
     *
     * NOTE: This ViewFrame must be registered with the filteredEventsModel's
     * EventBus in order for this handler to be invoked.
     *
     * @param event The RefreshRequestedEvent to handle.
     */
    @Subscribe
    public void handleRefreshRequested(RefreshRequestedEvent event) {
        Platform.runLater(() -> {
            notificationPane.hide();
            refreshHistorgram();
        });
    }

    /**
     * NOTE: This ViewFrame must be registered with the filteredEventsModel's
     * EventBus in order for this handler to be invoked.
     *
     * @param event The CacheInvalidatedEvent to handle.
     */
    @Subscribe
    @NbBundle.Messages({
        "ViewFrame.notification.cacheInvalidated=The event data has been updated, the visualization may be out of date."})
    public void handleCacheInvalidated(EventsModel.CacheInvalidatedEvent event) {
        Platform.runLater(() -> {
            if (hostedView.needsRefresh() == false) {
                hostedView.setNeedsRefresh();
                notificationPane.show(Bundle.ViewFrame_notification_cacheInvalidated());
            }
        });
    }

    /**
     * Refresh the Histogram to represent the current state of the DB.
     */
    @NbBundle.Messages({"ViewFrame.histogramTask.title=Rebuilding Histogram",
        "ViewFrame.histogramTask.preparing=Preparing",
        "ViewFrame.histogramTask.resetUI=Resetting UI",
        "ViewFrame.histogramTask.queryDb=Querying FB",
        "ViewFrame.histogramTask.updateUI2=Updating UI"})
    synchronized private void refreshHistorgram() {
        if (histogramTask != null) {
            histogramTask.cancel(true);
        }

        histogramTask = new LoggedTask<Void>(Bundle.ViewFrame_histogramTask_title(), true) {
            private final Lighting lighting = new Lighting();

            @Override
            protected Void call() throws Exception {

                updateMessage(Bundle.ViewFrame_histogramTask_preparing());

                long max = 0;
                final RangeDivision rangeInfo = RangeDivision.getRangeDivision(filteredEvents.getSpanningInterval(), TimeLineController.getJodaTimeZone());
                final long lowerBound = rangeInfo.getLowerBound();
                final long upperBound = rangeInfo.getUpperBound();
                Interval timeRange = new Interval(new DateTime(lowerBound, TimeLineController.getJodaTimeZone()), new DateTime(upperBound, TimeLineController.getJodaTimeZone()));

                //extend range to block bounderies (ie day, month, year)
                int p = 0; // progress counter

                //clear old data, and reset ranges and series
                Platform.runLater(() -> {
                    updateMessage(Bundle.ViewFrame_histogramTask_resetUI());

                });

                ArrayList<Long> bins = new ArrayList<>();

                DateTime start = timeRange.getStart();
                while (timeRange.contains(start)) {
                    if (isCancelled()) {
                        return null;
                    }
                    DateTime end = start.plus(rangeInfo.getPeriodSize().toUnitPeriod());
                    final Interval interval = new Interval(start, end);
                    //increment for next iteration

                    start = end;

                    updateMessage(Bundle.ViewFrame_histogramTask_queryDb());
                    //query for current range
                    long count = filteredEvents.getEventCounts(interval).values().stream().mapToLong(Long::valueOf).sum();
                    bins.add(count);

                    max = Math.max(count, max);

                    final double fMax = Math.log(max);
                    final ArrayList<Long> fbins = new ArrayList<>(bins);
                    Platform.runLater(() -> {
                        updateMessage(Bundle.ViewFrame_histogramTask_updateUI2());

                        histogramBox.getChildren().clear();

                        for (Long bin : fbins) {
                            if (isCancelled()) {
                                break;
                            }
                            Region bar = new Region();
                            //scale them to fit in histogram height
                            bar.prefHeightProperty().bind(histogramBox.heightProperty().multiply(Math.log(bin)).divide(fMax));
                            bar.setMaxHeight(USE_PREF_SIZE);
                            bar.setMinHeight(USE_PREF_SIZE);
                            bar.setBackground(GRAY_BACKGROUND);
                            bar.setOnMouseEntered((MouseEvent event) -> {
                                Tooltip.install(bar, new Tooltip(bin.toString()));
                            });
                            bar.setEffect(lighting);
                            //they each get equal width to fill the histogram horizontally
                            HBox.setHgrow(bar, Priority.ALWAYS);
                            histogramBox.getChildren().add(bar);
                        }
                    });
                }
                return null;
            }

        };
        new Thread(histogramTask).start();
        controller.monitorTask(histogramTask);
    }

    /**
     * Refresh the time selection UI to match the current zoom parameters.
     */
    @NbBundle.Messages({
        "ViewFrame.refreshTimeUI.errorMessage=Error gettig the spanning interval."})
    private void refreshTimeUI() {
        try {
            RangeDivision rangeDivisionInfo = RangeDivision.getRangeDivision(filteredEvents.getSpanningInterval(), TimeLineController.getJodaTimeZone());
            final long minTime = rangeDivisionInfo.getLowerBound();
            final long maxTime = rangeDivisionInfo.getUpperBound();

            long startMillis = filteredEvents.getTimeRange().getStartMillis();
            long endMillis = filteredEvents.getTimeRange().getEndMillis();

            if ( maxTime > minTime) {
                Platform.runLater(() -> {
                    startPicker.localDateTimeProperty().removeListener(startListener);
                    endPicker.localDateTimeProperty().removeListener(endListener);
                    rangeSlider.highValueChangingProperty().removeListener(rangeSliderListener);
                    rangeSlider.lowValueChangingProperty().removeListener(rangeSliderListener);

                    rangeSlider.setMax((maxTime - minTime));

                    rangeSlider.setLowValue(startMillis - minTime);
                    rangeSlider.setHighValue(endMillis - minTime);
                    startPicker.setLocalDateTime(epochMillisToLocalDateTime(startMillis));
                    endPicker.setLocalDateTime(epochMillisToLocalDateTime(endMillis));

                    rangeSlider.highValueChangingProperty().addListener(rangeSliderListener);
                    rangeSlider.lowValueChangingProperty().addListener(rangeSliderListener);
                    startPicker.localDateTimeProperty().addListener(startListener);
                    endPicker.localDateTimeProperty().addListener(endListener);
                });
            }
        } catch (TskCoreException ex) {
            Notifications.create().owner(getScene().getWindow())
                    .text(Bundle.ViewFrame_refreshTimeUI_errorMessage())
                    .showError();
            logger.log(Level.SEVERE, "Error gettig the spanning interval.", ex);
        }
    }

    /**
     * Sync up the view shown in the UI to the one currently active according to
     * the controller. Swaps out the hosted AbstractTimelineView for a new one
     * of the correct type.
     */
    private void syncViewMode() {
        ViewMode newViewMode = controller.getViewMode();

        //clear out old view.
        if (hostedView != null) {
            hostedView.dispose();
        }

        //Set a new AbstractTimeLineView as the one hosted by this ViewFrame.
        switch (newViewMode) {
            case LIST:
                hostedView = new ListViewPane(controller);
                //TODO: should remove listeners from events tree
                break;
            case COUNTS:
                hostedView = new CountsViewPane(controller);
                //TODO: should remove listeners from events tree
                break;
            case DETAIL:
                DetailViewPane detailViewPane = new DetailViewPane(controller);
                //link events tree to detailview instance.
                detailViewPane.setHighLightedEvents(eventsTree.getSelectedEvents());
                eventsTree.setDetailViewPane(detailViewPane);
                hostedView = detailViewPane;
                break;
            default:
                throw new IllegalArgumentException("Unknown ViewMode: " + newViewMode.toString());//NON-NLS
        }
        notificationPane.getActions().setAll(new Refresh());
        controller.registerForEvents(hostedView);
        controller.getAutopsyCase().getSleuthkitCase().registerForEvents(this);

        viewModeToggleGroup.setValue(newViewMode); //this selects the right toggle automatically

        //configure settings and time navigation nodes
        setViewSettingsControls(hostedView.getSettingsControls());
        setTimeNavigationControls(hostedView.hasCustomTimeNavigationControls()
                ? hostedView.getTimeNavigationControls()
                : defaultTimeNavigationNodes);

        //do further setup of  new view.
        ActionUtils.unconfigureButton(refreshButton);
        ActionUtils.configureButton(new Refresh(), refreshButton);//configure new refresh action for new view
        hostedView.refresh();
        notificationPane.setContent(hostedView);
        //listen to has events property and show "dialog" if it is false.
        hostedView.hasVisibleEventsProperty().addListener(hasEvents -> {
            notificationPane.setContent(hostedView.hasVisibleEvents()
                    ? hostedView
                    : new StackPane(hostedView,
                            NO_EVENTS_BACKGROUND,
                            new NoEventsDialog(() -> notificationPane.setContent(hostedView))
                    )
            );
        });
    }

    /**
     * Show the given List of Nodes in the top ToolBar. Replaces any settings
     * Nodes that may have previously been set with the given List of Nodes.
     *
     * @param newSettingsNodes The Nodes to show in the ToolBar.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void setViewSettingsControls(List<Node> newSettingsNodes) {
        toolBar.getItems().removeAll(this.settingsNodes); //remove old nodes
        this.settingsNodes.setAll(newSettingsNodes);
        toolBar.getItems().addAll(SETTINGS_TOOLBAR_INSERTION_INDEX, settingsNodes);
    }

    /**
     * Show the given List of Nodes in the time range ToolBar. Replaces any
     * Nodes that may have previously been set with the given List of Nodes.
     *
     * @param timeNavigationNodes The Nodes to show in the time range ToolBar.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void setTimeNavigationControls(List<Node> timeNavigationNodes) {
        timeRangeToolBar.getItems().removeAll(this.timeNavigationNodes); //remove old nodes
        this.timeNavigationNodes.setAll(timeNavigationNodes);
        timeRangeToolBar.getItems().addAll(TIME_TOOLBAR_INSERTION_INDEX, timeNavigationNodes);

    }

    @NbBundle.Messages("NoEventsDialog.titledPane.text=No Visible Events")
    private class NoEventsDialog extends StackPane {

        @FXML
        private TitledPane titledPane;
        @FXML
        private Button backButton;
        @FXML
        private Button resetFiltersButton;
        @FXML
        private Button dismissButton;
        @FXML
        private Button zoomButton;
        @FXML
        private Label noEventsDialogLabel;

        private final Runnable closeCallback;

        private NoEventsDialog(Runnable closeCallback) {
            this.closeCallback = closeCallback;
            FXMLConstructor.construct(this, "NoEventsDialog.fxml"); //NON-NLS
        }

        @FXML
        @NbBundle.Messages("ViewFrame.noEventsDialogLabel.text=There are no events visible with the current zoom / filter settings.")
        void initialize() {
            assert resetFiltersButton != null : "fx:id=\"resetFiltersButton\" was not injected: check your FXML file 'NoEventsDialog.fxml'."; //NON-NLS
            assert dismissButton != null : "fx:id=\"dismissButton\" was not injected: check your FXML file 'NoEventsDialog.fxml'."; //NON-NLS
            assert zoomButton != null : "fx:id=\"zoomButton\" was not injected: check your FXML file 'NoEventsDialog.fxml'."; //NON-NLS

            titledPane.setText(Bundle.NoEventsDialog_titledPane_text());
            noEventsDialogLabel.setText(Bundle.ViewFrame_noEventsDialogLabel_text());

            dismissButton.setOnAction(actionEvent -> closeCallback.run());

            ActionUtils.configureButton(new ZoomToEvents(controller), zoomButton);
            ActionUtils.configureButton(new Back(controller), backButton);
            ActionUtils.configureButton(new ResetFilters(controller), resetFiltersButton);
        }
    }

    /**
     * Base class for listeners that listen to a LocalDateTimeTextField and push
     * the selected LocalDateTime as start/end to the timelinecontroller.
     */
    private class PickerListener implements InvalidationListener {

        private final BiFunction< Interval, Long, Interval> intervalMapper;
        private final Supplier<LocalDateTimeTextField> pickerSupplier;

        PickerListener(Supplier<LocalDateTimeTextField> pickerSupplier, BiFunction<Interval, Long, Interval> intervalMapper) {
            this.pickerSupplier = pickerSupplier;
            this.intervalMapper = intervalMapper;
        }

        @NbBundle.Messages({"ViewFrame.pickerListener.errorMessage=Error responding to date/time picker change."})
        @Override
        public void invalidated(Observable observable) {
            LocalDateTime pickerTime = pickerSupplier.get().getLocalDateTime();
            if (pickerTime != null) {
                try {
                    controller.pushTimeRange(intervalMapper.apply(filteredEvents.getTimeRange(), localDateTimeToEpochMilli(pickerTime)));
                } catch (TskCoreException ex) {
                    Notifications.create().owner(getScene().getWindow())
                            .text(Bundle.ViewFrame_pickerListener_errorMessage())
                            .showError();
                    logger.log(Level.WARNING, "Error responding to date/time picker change.", ex); //NON-NLS
                } catch (IllegalArgumentException ex ) {
                    logger.log(Level.INFO, "Timeline: User supplied invalid time range."); //NON-NLS
                }
                
                Platform.runLater(ViewFrame.this::refreshTimeUI);
            }
        }
    }

    /**
     * Callback that disabled date/times outside the span of the current case.
     */
    private class LocalDateDisabler implements Callback<LocalDateTimePicker.LocalDateTimeRange, Void> {

        @NbBundle.Messages({
            "ViewFrame.localDateDisabler.errorMessage=Error getting spanning interval."})
        @Override
        public Void call(LocalDateTimePicker.LocalDateTimeRange viewedRange) {

            startPicker.disabledLocalDateTimes().clear();
            endPicker.disabledLocalDateTimes().clear();
            try {
                //all events in the case are contained in this interval
                Interval spanningInterval = filteredEvents.getSpanningInterval();
                long spanStartMillis = spanningInterval.getStartMillis();
                long spaneEndMillis = spanningInterval.getEndMillis();

                LocalDate rangeStartLocalDate = viewedRange.getStartLocalDateTime().toLocalDate();
                LocalDate rangeEndLocalDate = viewedRange.getEndLocalDateTime().toLocalDate().plusDays(1);
                //iterate over days of the displayed range and disable ones not in spanning interval
                for (LocalDate dt = rangeStartLocalDate; false == dt.isAfter(rangeEndLocalDate); dt = dt.plusDays(1)) {
                    long startOfDay = dt.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
                    long endOfDay = dt.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
                    //if no part of day is within spanning interval, add that date the list of disabled dates.
                    if (endOfDay < spanStartMillis || startOfDay > spaneEndMillis) {
                        startPicker.disabledLocalDateTimes().add(dt.atStartOfDay());
                        endPicker.disabledLocalDateTimes().add(dt.atStartOfDay());
                    }
                }

            } catch (TskCoreException ex) {
                Notifications.create().owner(getScene().getWindow())
                        .text(Bundle.ViewFrame_localDateDisabler_errorMessage())
                        .showError();
                logger.log(Level.SEVERE, "Error getting spanning interval.", ex);
            }
            return null;
        }
    }

    /**
     * Callback that validates that selected date/times are in the spanning
     * interval for this case, and resets the textbox if invalid date/time was
     * entered.
     */
    private class LocalDateTimeValidator implements Callback<LocalDateTime, Boolean> {

        /**
         * picker to reset if invalid info was entered
         */
        private final LocalDateTimeTextField picker;

        LocalDateTimeValidator(LocalDateTimeTextField picker) {
            this.picker = picker;
        }

        @NbBundle.Messages({
            "ViewFrame.dateTimeValidator.errorMessage=Error getting spanning interval."})
        @Override
        public Boolean call(LocalDateTime param) {
            long epochMilli = localDateTimeToEpochMilli(param);
            try {
                if (filteredEvents.getSpanningInterval().contains(epochMilli)) {
                    return true;
                } else {
                    if (picker.isPickerShowing() == false) {
                        //if the user typed an in valid date, reset the text box to the selected date.
                        picker.setDisplayedLocalDateTime(picker.getLocalDateTime());
                    }
                    return false;
                }
            } catch (TskCoreException ex) {
                Notifications.create().owner(getScene().getWindow())
                        .text(Bundle.ViewFrame_dateTimeValidator_errorMessage())
                        .showError();
                logger.log(Level.SEVERE, "Error getting spanning interval.", ex);
                return false;
            }
        }
    }

    /**
     * Action that refreshes the View.
     */
    private class Refresh extends Action {

        @NbBundle.Messages({
            "ViewFrame.refresh.text=Refresh View",
            "ViewFrame.refresh.longText=Refresh the view to include information that is in the DB but not displayed, such as newly updated tags."})
        Refresh() {
            super(Bundle.ViewFrame_refresh_text());
            setLongText(Bundle.ViewFrame_refresh_longText());
            setGraphic(new ImageView(REFRESH));
            setEventHandler(actionEvent -> filteredEvents.postRefreshRequest());
            disabledProperty().bind(hostedView.needsRefreshProperty().not());
        }
    }
}
