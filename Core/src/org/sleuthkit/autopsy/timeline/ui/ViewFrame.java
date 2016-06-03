/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-16 Basis Technology Corp.
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

import com.google.common.eventbus.Subscribe;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
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
import org.controlsfx.control.RangeSlider;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.events.DataSourceAddedEvent;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.events.DataSourceAnalysisCompletedEvent;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ViewMode;
import org.sleuthkit.autopsy.timeline.actions.Back;
import org.sleuthkit.autopsy.timeline.actions.ResetFilters;
import org.sleuthkit.autopsy.timeline.actions.SaveSnapshotAsReport;
import org.sleuthkit.autopsy.timeline.actions.UpdateDB;
import org.sleuthkit.autopsy.timeline.actions.ZoomIn;
import org.sleuthkit.autopsy.timeline.actions.ZoomOut;
import org.sleuthkit.autopsy.timeline.actions.ZoomToEvents;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.events.DBUpdatedEvent;
import org.sleuthkit.autopsy.timeline.events.RefreshRequestedEvent;
import org.sleuthkit.autopsy.timeline.events.TagsUpdatedEvent;
import org.sleuthkit.autopsy.timeline.ui.countsview.CountsViewPane;
import org.sleuthkit.autopsy.timeline.ui.detailview.DetailViewPane;
import org.sleuthkit.autopsy.timeline.ui.detailview.tree.EventsTree;
import org.sleuthkit.autopsy.timeline.ui.listvew.ListViewPane;
import org.sleuthkit.autopsy.timeline.utils.RangeDivisionInfo;

/**
 * A container for an AbstractTimelineView. Has a Toolbar on top to hold
 * settings widgets supplied by contained AbstractTimelineView, and the
 * histogram / time selection on bottom.
 *
 * TODO: Refactor common code out of histogram and CountsView? -jm
 */
final public class ViewFrame extends BorderPane {

    private static final Logger LOGGER = Logger.getLogger(ViewFrame.class.getName());

    private static final Image INFORMATION = new Image("org/sleuthkit/autopsy/timeline/images/information.png", 16, 16, true, true); // NON-NLS
    private static final Image WARNING = new Image("org/sleuthkit/autopsy/timeline/images/warning_triangle.png", 16, 16, true, true); // NON-NLS
    private static final Image REFRESH = new Image("org/sleuthkit/autopsy/timeline/images/arrow-circle-double-135.png"); // NON-NLS
    private static final Background GRAY_BACKGROUND = new Background(new BackgroundFill(Color.GREY, CornerRadii.EMPTY, Insets.EMPTY));

    /**
     * Region that will be stacked in between the no-events "dialog" and the
     * hosted AbstractTimelineView in order to gray out the AbstractTimelineView.
     */
    private final static Region NO_EVENTS_BACKGROUND = new Region() {
        {
            setBackground(GRAY_BACKGROUND);
            setOpacity(.3);
        }
    };

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
    private Button snapShotButton;
    @FXML
    private Button refreshButton;
    @FXML
    private Button updateDBButton;

    /*
     * Wraps contained AbstractTimelineView so that we can show notifications over it.
     */
    private final NotificationPane notificationPane = new NotificationPane();

    private final TimeLineController controller;
    private final FilteredEventsModel filteredEvents;

    /**
     * Listen to changes in the range slider selection and forward to the
     * controller. Waits until the user releases thumb to send to controller.
     */
    private final InvalidationListener rangeSliderListener = new InvalidationListener() {
        @Override
        public void invalidated(Observable observable) {
            if (rangeSlider.isHighValueChanging() == false
                    && rangeSlider.isLowValueChanging() == false) {
                Long minTime = RangeDivisionInfo.getRangeDivisionInfo(filteredEvents.getSpanningInterval()).getLowerBound();
                if (false == controller.pushTimeRange(new Interval(
                        (long) (rangeSlider.getLowValue() + minTime),
                        (long) (rangeSlider.getHighValue() + minTime + 1000)))) {
                    refreshTimeUI();
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
        FXMLConstructor.construct(this, "ViewFrame.fxml"); // NON-NLS
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
        "ViewFrame.tagsAddedOrDeleted=Tags have been created and/or deleted.  The view may not be up to date."
    })
    void initialize() {
        assert endPicker != null : "fx:id=\"endPicker\" was not injected: check your FXML file 'ViewWrapper.fxml'."; // NON-NLS
        assert histogramBox != null : "fx:id=\"histogramBox\" was not injected: check your FXML file 'ViewWrapper.fxml'."; // NON-NLS
        assert startPicker != null : "fx:id=\"startPicker\" was not injected: check your FXML file 'ViewWrapper.fxml'."; // NON-NLS
        assert rangeHistogramStack != null : "fx:id=\"rangeHistogramStack\" was not injected: check your FXML file 'ViewWrapper.fxml'."; // NON-NLS
        assert countsToggle != null : "fx:id=\"countsToggle\" was not injected: check your FXML file 'VisToggle.fxml'."; // NON-NLS
        assert detailsToggle != null : "fx:id=\"eventsToggle\" was not injected: check your FXML file 'VisToggle.fxml'."; // NON-NLS

        //configure notification pane 
        notificationPane.getStyleClass().add(NotificationPane.STYLE_CLASS_DARK);
        setCenter(notificationPane);

        //configure view mode toggle
        viewModeLabel.setText(Bundle.ViewFrame_viewModeLabel_text());
        countsToggle.setText(Bundle.ViewFrame_countsToggle_text());
        detailsToggle.setText(Bundle.ViewFrame_detailsToggle_text());
        listToggle.setText(Bundle.ViewFrame_listToggle_text());

        ToggleGroupValue<ViewMode> visModeToggleGroup = new ToggleGroupValue<>();
        visModeToggleGroup.add(listToggle, ViewMode.LIST);
        visModeToggleGroup.add(detailsToggle, ViewMode.DETAIL);
        visModeToggleGroup.add(countsToggle, ViewMode.COUNTS);

        modeSegButton.setToggleGroup(visModeToggleGroup);

        visModeToggleGroup.valueProperty().addListener((observable, oldVisMode, newValue) -> {
            controller.setViewMode(newValue != null ? newValue : (oldVisMode != null ? oldVisMode : ViewMode.COUNTS));
        });

        controller.viewModeProperty().addListener(viewMode -> syncViewMode());
        syncViewMode();

        ActionUtils.configureButton(new SaveSnapshotAsReport(controller, notificationPane::getContent), snapShotButton);
        ActionUtils.configureButton(new UpdateDB(controller), updateDBButton);

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
        histogramBox.setStyle("   -fx-padding: 0,0.5em,0,.5em; "); // NON-NLS

        //configure zoom buttons
        zoomMenuButton.getItems().clear();
        for (ZoomRanges zoomRange : ZoomRanges.values()) {
            zoomMenuButton.getItems().add(ActionUtils.createMenuItem(
                    new Action(zoomRange.getDisplayName(), event -> {
                        if (zoomRange != ZoomRanges.ALL) {
                            controller.pushPeriod(zoomRange.getPeriod());
                        } else {
                            controller.showFullRange();
                        }
                    })));
        }
        zoomMenuButton.setText(Bundle.ViewFrame_zoomMenuButton_text());
        ActionUtils.configureButton(new ZoomOut(controller), zoomOutButton);
        ActionUtils.configureButton(new ZoomIn(controller), zoomInButton);

        //register for EventBus events (tags)
        filteredEvents.registerForEvents(this);

        //listen for changes in the time range / zoom params
        TimeLineController.getTimeZone().addListener(timeZoneProp -> refreshTimeUI());
        filteredEvents.timeRangeProperty().addListener(timeRangeProp -> refreshTimeUI());
        filteredEvents.zoomParametersProperty().addListener(zoomListener);
        refreshTimeUI(); //populate the view

        refreshHistorgram();

    }

    /**
     * Handle TagsUpdatedEvents by marking that the view needs to be
     * refreshed.
     *
     * NOTE: This ViewFrame must be registered with the
     * filteredEventsModel's EventBus in order for this handler to be invoked.
     *
     * @param event The TagsUpdatedEvent to handle.
     */
    @Subscribe
    public void handleTimeLineTagUpdate(TagsUpdatedEvent event) {
        hostedView.setOutOfDate();
        Platform.runLater(() -> {
            if (notificationPane.isShowing() == false) {
                notificationPane.getActions().setAll(new Refresh());
                notificationPane.show(Bundle.ViewFrame_tagsAddedOrDeleted(), new ImageView(INFORMATION));
            }
        });
    }

    /**
     * Handle a RefreshRequestedEvent from the events model by clearing the
     * refresh notification.
     *
     * NOTE: This ViewFrame must be registered with the
     * filteredEventsModel's EventBus in order for this handler to be invoked.
     *
     * @param event The RefreshRequestedEvent to handle.
     */
    @Subscribe
    public void handleRefreshRequested(RefreshRequestedEvent event) {
        Platform.runLater(() -> {
            if (Bundle.ViewFrame_tagsAddedOrDeleted().equals(notificationPane.getText())) {
                notificationPane.hide();
            }
        });
    }

    /**
     * Handle a DBUpdatedEvent from the events model by refreshing the
     * view.
     *
     * NOTE: This ViewFrame must be registered with the
     * filteredEventsModel's EventBus in order for this handler to be invoked.
     *
     * @param event The DBUpdatedEvent to handle.
     */
    @Subscribe
    public void handleDBUpdated(DBUpdatedEvent event) {
        hostedView.refresh();
        refreshHistorgram();
        Platform.runLater(notificationPane::hide);
    }

    /**
     * Handle a DataSourceAddedEvent from the events model by showing a
     * notification.
     *
     * NOTE: This ViewFrame must be registered with the
     * filteredEventsModel's EventBus in order for this handler to be invoked.
     *
     * @param event The DataSourceAddedEvent to handle.
     */
    @Subscribe
    @NbBundle.Messages({
        "# {0} - datasource name",
        "ViewFrame.notification.newDataSource={0} has been added as a new datasource.  The Timeline DB may be out of date."})
    public void handlDataSourceAdded(DataSourceAddedEvent event) {
        Platform.runLater(() -> {
            notificationPane.getActions().setAll(new UpdateDB(controller));
            notificationPane.show(Bundle.ViewFrame_notification_newDataSource(event.getDataSource().getName()), new ImageView(WARNING));
        });
    }

    /**
     * Handle a DataSourceAnalysisCompletedEvent from the events modelby showing
     * a notification.
     *
     * NOTE: This ViewFrame must be registered with the
     * filteredEventsModel's EventBus in order for this handler to be invoked.
     *
     * @param event The DataSourceAnalysisCompletedEvent to handle.
     */
    @Subscribe
    @NbBundle.Messages({
        "# {0} - datasource name",
        "ViewFrame.notification.analysisComplete=Analysis has finished for {0}.  The Timeline DB may be out of date."})
    public void handleAnalysisCompleted(DataSourceAnalysisCompletedEvent event) {
        Platform.runLater(() -> {
            notificationPane.getActions().setAll(new UpdateDB(controller));
            notificationPane.show(Bundle.ViewFrame_notification_analysisComplete(event.getDataSource().getName()), new ImageView(WARNING));
        });
    }

    /**
     * Refresh the Histogram to represent the current state of the DB.
     */
    synchronized private void refreshHistorgram() {
        if (histogramTask != null) {
            histogramTask.cancel(true);
        }

        histogramTask = new LoggedTask<Void>(
                NbBundle.getMessage(ViewFrame.class, "ViewFrame.histogramTask.title"), true) { // NON-NLS
            private final Lighting lighting = new Lighting();

            @Override
            protected Void call() throws Exception {

                updateMessage(NbBundle.getMessage(ViewFrame.class, "ViewFrame.histogramTask.preparing")); // NON-NLS

                long max = 0;
                final RangeDivisionInfo rangeInfo = RangeDivisionInfo.getRangeDivisionInfo(filteredEvents.getSpanningInterval());
                final long lowerBound = rangeInfo.getLowerBound();
                final long upperBound = rangeInfo.getUpperBound();
                Interval timeRange = new Interval(new DateTime(lowerBound, TimeLineController.getJodaTimeZone()), new DateTime(upperBound, TimeLineController.getJodaTimeZone()));

                //extend range to block bounderies (ie day, month, year)
                int p = 0; // progress counter

                //clear old data, and reset ranges and series
                Platform.runLater(() -> {
                    updateMessage(NbBundle.getMessage(ViewFrame.class, "ViewFrame.histogramTask.resetUI")); // NON-NLS

                });

                ArrayList<Long> bins = new ArrayList<>();

                DateTime start = timeRange.getStart();
                while (timeRange.contains(start)) {
                    if (isCancelled()) {
                        return null;
                    }
                    DateTime end = start.plus(rangeInfo.getPeriodSize().getPeriod());
                    final Interval interval = new Interval(start, end);
                    //increment for next iteration

                    start = end;

                    updateMessage(NbBundle.getMessage(ViewFrame.class, "ViewFrame.histogramTask.queryDb")); // NON-NLS
                    //query for current range
                    long count = filteredEvents.getEventCounts(interval).values().stream().mapToLong(Long::valueOf).sum();
                    bins.add(count);

                    max = Math.max(count, max);

                    final double fMax = Math.log(max);
                    final ArrayList<Long> fbins = new ArrayList<>(bins);
                    Platform.runLater(() -> {
                        updateMessage(NbBundle.getMessage(ViewFrame.class, "ViewFrame.histogramTask.updateUI2")); // NON-NLS

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
     * Refresh the time selection UI to match the current zoome paramaters.
     */
    private void refreshTimeUI() {
        RangeDivisionInfo rangeDivisionInfo = RangeDivisionInfo.getRangeDivisionInfo(filteredEvents.getSpanningInterval());
        final long minTime = rangeDivisionInfo.getLowerBound();
        final long maxTime = rangeDivisionInfo.getUpperBound();

        long startMillis = filteredEvents.getTimeRange().getStartMillis();
        long endMillis = filteredEvents.getTimeRange().getEndMillis();

        if (minTime > 0 && maxTime > minTime) {
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
    }

    /**
     * Switch to the given ViewMode, by swapping out the hosted
     * AbstractTimelineView for one of the correct type.
     */
    private void syncViewMode() {
        AbstractTimeLineView view;
        ViewMode viewMode = controller.viewModeProperty().get();

        //make new view.
        switch (viewMode) {
            case LIST:
                view = new ListViewPane(controller);
                Platform.runLater(() -> {
                    listToggle.setSelected(true);
                    //TODO: should remove listeners from events tree
                });
                break;
            case COUNTS:
                view = new CountsViewPane(controller);
                Platform.runLater(() -> {
                    countsToggle.setSelected(true);
                    //TODO: should remove listeners from events tree
                });
                break;
            case DETAIL:
                DetailViewPane detailViewPane = new DetailViewPane(controller);
                Platform.runLater(() -> {
                    detailsToggle.setSelected(true);
                    detailViewPane.setHighLightedEvents(eventsTree.getSelectedEvents());
                    eventsTree.setDetailViewPane(detailViewPane);
                });
                view = detailViewPane;
                break;
            default:
                throw new IllegalArgumentException("Unknown ViewMode: " + viewMode.toString());
        }

        //Set the new AbstractTimeLineView as the one hosted by this ViewFrame.
        Platform.runLater(() -> {
            //clear out old view.
            if (hostedView != null) {
                toolBar.getItems().removeAll(hostedView.getSettingsNodes());
                hostedView.dispose();
            }

            hostedView = view;
            //setup new view.
            ActionUtils.configureButton(new Refresh(), refreshButton);//configure new refresh action for new view
            hostedView.refresh();
            toolBar.getItems().addAll(2, view.getSettingsNodes());
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
        });
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
            FXMLConstructor.construct(this, "NoEventsDialog.fxml"); // NON-NLS
        }

        @FXML
        void initialize() {
            assert resetFiltersButton != null : "fx:id=\"resetFiltersButton\" was not injected: check your FXML file 'NoEventsDialog.fxml'."; // NON-NLS
            assert dismissButton != null : "fx:id=\"dismissButton\" was not injected: check your FXML file 'NoEventsDialog.fxml'."; // NON-NLS
            assert zoomButton != null : "fx:id=\"zoomButton\" was not injected: check your FXML file 'NoEventsDialog.fxml'."; // NON-NLS

            titledPane.setText(Bundle.NoEventsDialog_titledPane_text());
            noEventsDialogLabel.setText(NbBundle.getMessage(NoEventsDialog.class, "ViewFrame.noEventsDialogLabel.text")); // NON-NLS

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

        @Override
        public void invalidated(Observable observable) {
            LocalDateTime pickerTime = pickerSupplier.get().getLocalDateTime();
            if (pickerTime != null) {
                controller.pushTimeRange(intervalMapper.apply(filteredEvents.timeRangeProperty().get(), localDateTimeToEpochMilli(pickerTime)));
                Platform.runLater(ViewFrame.this::refreshTimeUI);
            }
        }
    }

    /**
     * callback that disabled date/times outside the span of the current case.
     */
    private class LocalDateDisabler implements Callback<LocalDateTimePicker.LocalDateTimeRange, Void> {

        @Override
        public Void call(LocalDateTimePicker.LocalDateTimeRange viewedRange) {
            startPicker.disabledLocalDateTimes().clear();
            endPicker.disabledLocalDateTimes().clear();

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

        @Override
        public Boolean call(LocalDateTime param) {
            long epochMilli = localDateTimeToEpochMilli(param);
            if (filteredEvents.getSpanningInterval().contains(epochMilli)) {
                return true;
            } else {
                if (picker.isPickerShowing() == false) {
                    //if the user typed an in valid date, reset the text box to the selected date.
                    picker.setDisplayedLocalDateTime(picker.getLocalDateTime());
                }
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
            disabledProperty().bind(hostedView.outOfDateProperty().not());
        }
    }
}
