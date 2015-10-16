/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-15 Basis Technology Corp.
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
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.Lighting;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import static javafx.scene.layout.Region.USE_PREF_SIZE;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javax.annotation.concurrent.GuardedBy;
import jfxtras.scene.control.LocalDateTimeTextField;
import org.controlsfx.control.NotificationPane;
import org.controlsfx.control.RangeSlider;
import org.controlsfx.control.action.Action;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.TimeLineView;
import org.sleuthkit.autopsy.timeline.VisualizationMode;
import org.sleuthkit.autopsy.timeline.actions.ResetFilters;
import org.sleuthkit.autopsy.timeline.actions.SaveSnapshot;
import org.sleuthkit.autopsy.timeline.actions.ZoomOut;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.events.TagsUpdatedEvent;
import org.sleuthkit.autopsy.timeline.filters.TagsFilter;
import static org.sleuthkit.autopsy.timeline.ui.Bundle.VisualizationPanel_refresh;
import static org.sleuthkit.autopsy.timeline.ui.Bundle.VisualizationPanel_tagsAddedOrDeleted;
import org.sleuthkit.autopsy.timeline.ui.countsview.CountsViewPane;
import org.sleuthkit.autopsy.timeline.ui.detailview.DetailViewPane;
import org.sleuthkit.autopsy.timeline.ui.detailview.tree.NavPanel;
import org.sleuthkit.autopsy.timeline.utils.RangeDivisionInfo;

/**
 * A container for an {@link AbstractVisualizationPane}, has a toolbar on top to
 * hold settings widgets supplied by contained {@link AbstAbstractVisualization}
 * and the histogram / timeselection on bottom. Also supplies containers for
 * replacement axis to contained {@link AbstractAbstractVisualization}
 *
 * TODO: refactor common code out of histogram and CountsView? -jm
 */
public class VisualizationPanel extends BorderPane implements TimeLineView {

    private static final Image INFORMATION = new Image("org/sleuthkit/autopsy/timeline/images/information.png", 16, 16, true, true); // NON-NLS
    private static final Image REFRESH = new Image("org/sleuthkit/autopsy/timeline/images/arrow-circle-double-135.png"); // NON-NLS

    private static final Logger LOGGER = Logger.getLogger(VisualizationPanel.class.getName());

    @GuardedBy("this")
    private LoggedTask<Void> histogramTask;

    private final NavPanel navPanel;

    private AbstractVisualizationPane<?, ?, ?, ?> visualization;

    //// range slider and histogram componenets
    @FXML
    private HBox histogramBox;

    @FXML
    private StackPane rangeHistogramStack;

    private final RangeSlider rangeSlider = new RangeSlider(0, 1.0, .25, .75);

    //// time range selection components
    @FXML
    private MenuButton zoomMenuButton;

    @FXML
    private Separator rightSeperator;

    @FXML
    private Separator leftSeperator;

    @FXML
    private Button zoomOutButton;

    @FXML
    private Button zoomInButton;

    @FXML
    private LocalDateTimeTextField startPicker;

    @FXML
    private LocalDateTimeTextField endPicker;

    //// replacemetn axis label componenets
    @FXML
    private Pane partPane;

    @FXML
    private Pane contextPane;

    @FXML
    private Region spacer;

    //// header toolbar componenets
    @FXML
    private ToolBar toolBar;

    @FXML
    private ToggleButton countsToggle;

    @FXML
    private ToggleButton detailsToggle;

    @FXML
    private Button snapShotButton;
    @FXML
    private Label visualizationModeLabel;
    @FXML
    private Label startLabel;

    @FXML
    private Label endLabel;

    private final NotificationPane notificationPane = new NotificationPane();

    private TimeLineController controller;

    private FilteredEventsModel filteredEvents;

    private final ChangeListener<Object> rangeSliderListener =
            (observable1, oldValue, newValue) -> {
                if (rangeSlider.isHighValueChanging() == false && rangeSlider.isLowValueChanging() == false) {
                    Long minTime = filteredEvents.getMinTime() * 1000;
                    controller.pushTimeRange(new Interval(
                                    new Double(rangeSlider.getLowValue() + minTime).longValue(),
                                    new Double(rangeSlider.getHighValue() + minTime).longValue(),
                                    DateTimeZone.UTC));
                }
            };

    private final InvalidationListener endListener = (Observable observable) -> {
        if (endPicker.getLocalDateTime() != null) {
            controller.pushTimeRange(VisualizationPanel.this.filteredEvents.timeRangeProperty().get().withEndMillis(
                    ZonedDateTime.of(endPicker.getLocalDateTime(), TimeLineController.getTimeZoneID()).toInstant().toEpochMilli()));
        }
    };

    private final InvalidationListener startListener = (Observable observable) -> {
        if (startPicker.getLocalDateTime() != null) {
            controller.pushTimeRange(VisualizationPanel.this.filteredEvents.timeRangeProperty().get().withStartMillis(
                    ZonedDateTime.of(startPicker.getLocalDateTime(), TimeLineController.getTimeZoneID()).toInstant().toEpochMilli()));
        }
    };

    static private final Background background = new Background(new BackgroundFill(Color.GREY, CornerRadii.EMPTY, Insets.EMPTY));

    static private final Lighting lighting = new Lighting();

    public VisualizationPanel(NavPanel navPanel) {
        this.navPanel = navPanel;
        FXMLConstructor.construct(this, "VisualizationPanel.fxml"); // NON-NLS
    }

    @FXML // This method is called by the FXMLLoader when initialization is complete
    @NbBundle.Messages("VisualizationPanel.refresh=refresh")
    protected void initialize() {
        assert endPicker != null : "fx:id=\"endPicker\" was not injected: check your FXML file 'ViewWrapper.fxml'."; // NON-NLS
        assert histogramBox != null : "fx:id=\"histogramBox\" was not injected: check your FXML file 'ViewWrapper.fxml'."; // NON-NLS
        assert startPicker != null : "fx:id=\"startPicker\" was not injected: check your FXML file 'ViewWrapper.fxml'."; // NON-NLS
        assert rangeHistogramStack != null : "fx:id=\"rangeHistogramStack\" was not injected: check your FXML file 'ViewWrapper.fxml'."; // NON-NLS
        assert countsToggle != null : "fx:id=\"countsToggle\" was not injected: check your FXML file 'VisToggle.fxml'."; // NON-NLS
        assert detailsToggle != null : "fx:id=\"eventsToggle\" was not injected: check your FXML file 'VisToggle.fxml'."; // NON-NLS

        notificationPane.getStyleClass().add(NotificationPane.STYLE_CLASS_DARK);
        notificationPane.getActions().setAll(new Action(VisualizationPanel_refresh()) {
            {
                setGraphic(new ImageView(REFRESH));
                setEventHandler((ActionEvent t) -> {
                    filteredEvents.refresh();
                    notificationPane.hide();
                });
            }
        });
        setCenter(notificationPane);
        visualizationModeLabel.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.visualizationModeLabel.text")); // NON-NLS
        startLabel.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.startLabel.text")); // NON-NLS
        endLabel.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.endLabel.text")); // NON-NLS

        HBox.setHgrow(leftSeperator, Priority.ALWAYS);
        HBox.setHgrow(rightSeperator, Priority.ALWAYS);
        ChangeListener<Toggle> toggleListener = (ObservableValue<? extends Toggle> observable,
                Toggle oldValue,
                Toggle newValue) -> {
                    if (newValue == null) {
                        countsToggle.getToggleGroup().selectToggle(oldValue != null ? oldValue : countsToggle);
                    } else if (newValue == countsToggle && oldValue != null) {
                        controller.setViewMode(VisualizationMode.COUNTS);
                    } else if (newValue == detailsToggle && oldValue != null) {
                        controller.setViewMode(VisualizationMode.DETAIL);
                    }
                };

        if (countsToggle.getToggleGroup() != null) {
            countsToggle.getToggleGroup().selectedToggleProperty().addListener(toggleListener);
        } else {
            countsToggle.toggleGroupProperty().addListener((Observable observable) -> {
                countsToggle.getToggleGroup().selectedToggleProperty().addListener(toggleListener);
            });
        }
        countsToggle.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.countsToggle.text")); // NON-NLS
        detailsToggle.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.detailsToggle.text")); // NON-NLS

        //setup rangeslider
        rangeSlider.setOpacity(.7);
        rangeSlider.setMin(0);
        rangeSlider.setBlockIncrement(1);

        rangeHistogramStack.getChildren().add(rangeSlider);

        /*
         * this padding attempts to compensates for the fact that the
         * rangeslider track doesn't extend to edge of node,and so the
         * histrogram doesn't quite line up with the rangeslider
         */
        histogramBox.setStyle("   -fx-padding: 0,0.5em,0,.5em; "); // NON-NLS

        zoomMenuButton.getItems().clear();
        for (ZoomRanges b : ZoomRanges.values()) {
            MenuItem menuItem = new MenuItem(b.getDisplayName());
            menuItem.setOnAction((event) -> {
                if (b != ZoomRanges.ALL) {
                    controller.pushPeriod(b.getPeriod());
                } else {
                    controller.showFullRange();
                }
            });
            zoomMenuButton.getItems().add(menuItem);
        }
        zoomMenuButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomMenuButton.text")); // NON-NLS

        zoomOutButton.setOnAction(e -> {
            controller.pushZoomOutTime();
        });
        zoomInButton.setOnAction(e -> {
            controller.pushZoomInTime();
        });

        snapShotButton.setOnAction((ActionEvent event) -> {
            //take snapshot
            final SnapshotParameters snapshotParameters = new SnapshotParameters();
            snapshotParameters.setViewport(new Rectangle2D(visualization.getBoundsInParent().getMinX(), visualization.getBoundsInParent().getMinY(),
                    visualization.getBoundsInParent().getWidth(),
                    contextPane.getLayoutBounds().getHeight() + visualization.getLayoutBounds().getHeight() + partPane.getLayoutBounds().getHeight()
            ));
            WritableImage snapshot = this.snapshot(snapshotParameters, null);
            //pass snapshot to save action
            new SaveSnapshot(controller, snapshot).handle(event);
        });

        snapShotButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.snapShotButton.text")); // NON-NLS
    }

    @Override
    public synchronized void setController(TimeLineController controller) {
        this.controller = controller;
        setModel(controller.getEventsModel());
        setViewMode(controller.viewModeProperty().get());
        controller.getNeedsHistogramRebuild().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
            if (newValue) {
                refreshHistorgram();
            }
        });

        controller.viewModeProperty().addListener((ObservableValue<? extends VisualizationMode> ov, VisualizationMode t, VisualizationMode t1) -> {
            setViewMode(t1);
        });
        TimeLineController.getTimeZone().addListener(timeRangeInvalidationListener);
        refreshHistorgram();
    }

    @Override
    public void setModel(FilteredEventsModel filteredEvents) {
        if (this.filteredEvents != null && this.filteredEvents != filteredEvents) {
            this.filteredEvents.unRegisterForEvents(this);
            this.filteredEvents.timeRangeProperty().removeListener(timeRangeInvalidationListener);
            this.filteredEvents.zoomParametersProperty().removeListener(zoomListener);
        }
        if (this.filteredEvents != filteredEvents) {
            filteredEvents.registerForEvents(this);
            filteredEvents.timeRangeProperty().addListener(timeRangeInvalidationListener);
            filteredEvents.zoomParametersProperty().addListener(zoomListener);
        }

        this.filteredEvents = filteredEvents;

        refreshTimeUI(filteredEvents.timeRangeProperty().get());

    }

    private void setViewMode(VisualizationMode visualizationMode) {
        switch (visualizationMode) {
            case COUNTS:
                setVisualization(new CountsViewPane(partPane, contextPane, spacer));
                countsToggle.setSelected(true);
                break;
            case DETAIL:
                setVisualization(new DetailViewPane(partPane, contextPane, spacer));
                detailsToggle.setSelected(true);
                break;
        }
    }

    private synchronized void setVisualization(final AbstractVisualizationPane<?, ?, ?, ?> newViz) {
        Platform.runLater(() -> {
            synchronized (VisualizationPanel.this) {
                if (visualization != null) {
                    toolBar.getItems().removeAll(visualization.getSettingsNodes());
                    visualization.dispose();
                }

                visualization = newViz;
                toolBar.getItems().addAll(newViz.getSettingsNodes());

                visualization.setController(controller);
                notificationPane.setContent(visualization);
                if (visualization instanceof DetailViewPane) {
                    navPanel.setDetailViewPane((DetailViewPane) visualization);
                }
                visualization.hasEvents.addListener((observable, oldValue, newValue) -> {
                    if (newValue == false) {

                        notificationPane.setContent(new StackPane(visualization, new Region() {
                            {
                                setBackground(new Background(new BackgroundFill(Color.GREY, CornerRadii.EMPTY, Insets.EMPTY)));
                                setOpacity(.3);
                            }
                        }, new NoEventsDialog(() -> {
                            notificationPane.setContent(visualization);
                        })));
                    } else {
                        notificationPane.setContent(visualization);
                    }
                });
            }
        });
    }

    @Subscribe
    @NbBundle.Messages("VisualizationPanel.tagsAddedOrDeleted=Tags have been created and/or deleted.  The visualization may not be up to date.")
    public void handleTimeLineTagEvent(TagsUpdatedEvent event) {
        TagsFilter tagsFilter = filteredEvents.getFilter().getTagsFilter();
        if (tagsFilter.isSelected() && tagsFilter.isDisabled() == false) {
            Platform.runLater(() -> {
                notificationPane.show(VisualizationPanel_tagsAddedOrDeleted(), new ImageView(INFORMATION));
            });
        }
    }

    synchronized private void refreshHistorgram() {

        if (histogramTask != null) {
            histogramTask.cancel(true);
        }

        histogramTask = new LoggedTask<Void>(
                NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.histogramTask.title"), true) { // NON-NLS

                    @Override
                    protected Void call() throws Exception {

                        updateMessage(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.histogramTask.preparing")); // NON-NLS

                        long max = 0;
                        final RangeDivisionInfo rangeInfo = RangeDivisionInfo.getRangeDivisionInfo(filteredEvents.getSpanningInterval());
                        final long lowerBound = rangeInfo.getLowerBound();
                        final long upperBound = rangeInfo.getUpperBound();
                        Interval timeRange = new Interval(new DateTime(lowerBound, TimeLineController.getJodaTimeZone()), new DateTime(upperBound, TimeLineController.getJodaTimeZone()));

                        //extend range to block bounderies (ie day, month, year)
                        int p = 0; // progress counter

                        //clear old data, and reset ranges and series
                        Platform.runLater(() -> {
                            updateMessage(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.histogramTask.resetUI")); // NON-NLS

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

                            updateMessage(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.histogramTask.queryDb")); // NON-NLS
                            //query for current range
                            long count = filteredEvents.getEventCounts(interval).values().stream().mapToLong(Long::valueOf).sum();
                            bins.add(count);

                            max = Math.max(count, max);

                            final double fMax = Math.log(max);
                            final ArrayList<Long> fbins = new ArrayList<>(bins);
                            Platform.runLater(() -> {
                                updateMessage(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.histogramTask.updateUI2")); // NON-NLS

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
                                    bar.setBackground(background);
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

    private InvalidationListener timeRangeInvalidationListener = (Observable observable) -> {
        refreshTimeUI(filteredEvents.timeRangeProperty().get());
    };

    private InvalidationListener zoomListener = (Observable observable) -> {
        notificationPane.hide();
    };

    private void refreshTimeUI(Interval interval) {
        RangeDivisionInfo rangeDivisionInfo = RangeDivisionInfo.getRangeDivisionInfo(filteredEvents.getSpanningInterval());

        final Long minTime = rangeDivisionInfo.getLowerBound();
        final long maxTime = rangeDivisionInfo.getUpperBound();

        if (minTime > 0 && maxTime > minTime) {

            Platform.runLater(() -> {
                startPicker.localDateTimeProperty().removeListener(startListener);
                endPicker.localDateTimeProperty().removeListener(endListener);
                rangeSlider.highValueChangingProperty().removeListener(rangeSliderListener);
                rangeSlider.lowValueChangingProperty().removeListener(rangeSliderListener);

                rangeSlider.setMax((Long) (maxTime - minTime));
                rangeSlider.setHighValue(interval.getEndMillis() - minTime);
                rangeSlider.setLowValue(interval.getStartMillis() - minTime);
                endPicker.setLocalDateTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(interval.getEndMillis()), TimeLineController.getTimeZoneID()));
                startPicker.setLocalDateTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(interval.getStartMillis()), TimeLineController.getTimeZoneID()));

                rangeSlider.highValueChangingProperty().addListener(rangeSliderListener);
                rangeSlider.lowValueChangingProperty().addListener(rangeSliderListener);
                startPicker.localDateTimeProperty().addListener(startListener);
                endPicker.localDateTimeProperty().addListener(endListener);
            });
        }
    }

    private class NoEventsDialog extends TitledPane {

        private final Runnable closeCallback;

        @FXML // ResourceBundle that was given to the FXMLLoader
        private ResourceBundle resources;

        @FXML // URL location of the FXML file that was given to the FXMLLoader
        private URL location;

        @FXML
        private Button resetFiltersButton;

        @FXML
        private Button dismissButton;

        @FXML
        private Button zoomButton;

        @FXML
        private Label noEventsDialogLabel;

        private NoEventsDialog(Runnable closeCallback) {
            this.closeCallback = closeCallback;
            FXMLConstructor.construct(this, "NoEventsDialog.fxml"); // NON-NLS

        }

        @FXML
        void initialize() {
            assert resetFiltersButton != null : "fx:id=\"resetFiltersButton\" was not injected: check your FXML file 'NoEventsDialog.fxml'."; // NON-NLS
            assert dismissButton != null : "fx:id=\"dismissButton\" was not injected: check your FXML file 'NoEventsDialog.fxml'."; // NON-NLS
            assert zoomButton != null : "fx:id=\"zoomButton\" was not injected: check your FXML file 'NoEventsDialog.fxml'."; // NON-NLS

            noEventsDialogLabel.setText(NbBundle.getMessage(NoEventsDialog.class, "VisualizationPanel.noEventsDialogLabel.text")); // NON-NLS
            zoomButton.setText(NbBundle.getMessage(NoEventsDialog.class, "VisualizationPanel.zoomButton.text")); // NON-NLS

            Action zoomOutAction = new ZoomOut(controller);
            zoomButton.setOnAction(zoomOutAction);
            zoomButton.disableProperty().bind(zoomOutAction.disabledProperty());

            dismissButton.setOnAction(e -> {
                closeCallback.run();
            });
            Action defaultFiltersAction = new ResetFilters(controller);
            resetFiltersButton.setOnAction(defaultFiltersAction);
            resetFiltersButton.disableProperty().bind(defaultFiltersAction.disabledProperty());
            resetFiltersButton.setText(NbBundle.getMessage(NoEventsDialog.class, "VisualizationPanel.resetFiltersButton.text")); // NON-NLS
        }
    }
}
