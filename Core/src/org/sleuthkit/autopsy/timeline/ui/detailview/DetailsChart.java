/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-16 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.detailview;

import java.util.Arrays;
import java.util.MissingResourceException;
import java.util.function.Predicate;
import javafx.beans.Observable;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.event.ActionEvent;
import javafx.geometry.Side;
import javafx.scene.chart.Axis;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import org.controlsfx.control.MasterDetailPane;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.python.google.common.collect.Iterables;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.filters.DescriptionFilter;
import org.sleuthkit.autopsy.timeline.ui.IntervalSelector;
import org.sleuthkit.autopsy.timeline.ui.TimeLineChart;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;

public final class DetailsChart extends Control implements TimeLineChart<DateTime> {

    private final DateAxis detailsChartDateAxis;
    private final DateAxis pinnedDateAxis;

    private final Axis<EventStripe> verticalAxis;

    private final SimpleObjectProperty<  IntervalSelector<? extends DateTime>> intervalSelector = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<Predicate<EventNodeBase<?>>> highlightPredicate = new SimpleObjectProperty<>((x) -> false);
    private final ObservableList<EventNodeBase<?>> selectedNodes;
    private final DetailsChartLayoutSettings layoutSettings = new DetailsChartLayoutSettings();
    private final TimeLineController controller;
    private final ObservableList<EventStripe> nestedEventStripes = FXCollections.observableArrayList();

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final ObservableList<EventStripe> eventStripes = FXCollections.observableArrayList();

    DetailsChart(TimeLineController controller, DateAxis detailsChartDateAxis, DateAxis pinnedDateAxis, Axis<EventStripe> verticalAxis, ObservableList<EventNodeBase<?>> selectedNodes) {
        this.controller = controller;
        this.detailsChartDateAxis = detailsChartDateAxis;
        this.verticalAxis = verticalAxis;
        this.pinnedDateAxis = pinnedDateAxis;
        this.selectedNodes = selectedNodes;

        getController().getPinnedEvents().addListener((SetChangeListener.Change<? extends TimeLineEvent> change) -> {
            layoutSettings.setPinnedLaneShowing(change.getSet().isEmpty() == false);
        });

        if (getController().getPinnedEvents().isEmpty() == false) {
            layoutSettings.setPinnedLaneShowing(true);
        }

        getController().getEventsModel().timeRangeProperty().addListener(o -> {
            clearGuideLines();
            clearIntervalSelector();
        });

        getController().getEventsModel().zoomParametersProperty().addListener(o -> {
            getSelectedNodes().clear();
        });
    }

    DateTime getDateTimeForPosition(double layoutX) {
        return ((DetailsChartSkin) getSkin()).getDateTimeForPosition(layoutX);
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void addStripe(EventStripe stripe) {
        eventStripes.add(stripe);
        nestedEventStripes.add(stripe);
    }

    void clearGuideLines() {
        guideLines.clear();
    }

    void clearGuideLine(GuideLine guideLine) {
        guideLines.remove(guideLine);
    }

    public ObservableList<EventNodeBase<?>> getSelectedNodes() {
        return selectedNodes;
    }

    DetailsChartLayoutSettings getLayoutSettings() {
        return layoutSettings;
    }

    void setHighlightPredicate(Predicate<EventNodeBase<?>> highlightPredicate) {
        this.highlightPredicate.set(highlightPredicate);
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void reset() {
        eventStripes.clear();
        nestedEventStripes.clear();
    }

    public ObservableList<EventStripe> getAllNestedEventStripes() {
        return nestedEventStripes;
    }

    private static class DetailIntervalSelector extends IntervalSelector<DateTime> {

        DetailIntervalSelector(IntervalSelectorProvider<DateTime> chart) {
            super(chart);
        }

        @Override
        protected String formatSpan(DateTime date) {
            return date.toString(TimeLineController.getZonedFormatter());
        }

        @Override
        protected Interval adjustInterval(Interval i) {
            return i;
        }

        @Override
        protected DateTime parseDateTime(DateTime date) {
            return date;
        }
    }

    private final ObservableSet<GuideLine> guideLines = FXCollections.observableSet();

    private void addGuideLine(GuideLine guideLine) {
        guideLines.add(guideLine);
    }

    static private class PlaceMarkerAction extends Action {

        private static final Image MARKER = new Image("/org/sleuthkit/autopsy/timeline/images/marker.png", 16, 16, true, true, true); //NON-NLS
        private GuideLine guideLine;

        @NbBundle.Messages({"PlaceMArkerAction.name=Place Marker"})
        PlaceMarkerAction(DetailsChart chart, MouseEvent clickEvent) {
            super(Bundle.PlaceMArkerAction_name());

            setGraphic(new ImageView(MARKER)); // NON-NLS
            setEventHandler(actionEvent -> {
                if (guideLine == null) {
                    guideLine = new GuideLine(chart);
                    guideLine.relocate(chart.sceneToLocal(clickEvent.getSceneX(), 0).getX(), 0);
                    chart.addGuideLine(guideLine);

                } else {
                    guideLine.relocate(chart.sceneToLocal(clickEvent.getSceneX(), 0).getX(), 0);
                }
            });
        }
    }

    /**
     *
     * @param chartLane           the value of chartLane
     * @param mouseClickedHandler the value of mouseClickedHandler
     * @param chartDragHandler1   the value of chartDragHandler1
     */
    static private void configureMouseListeners(final DetailsChartLane<?> chartLane, final TimeLineChart.MouseClickedHandler<DateTime, DetailsChart> mouseClickedHandler, final TimeLineChart.ChartDragHandler<DateTime, DetailsChart> chartDragHandler) {
        chartLane.setOnMousePressed(chartDragHandler);
        chartLane.setOnMouseReleased(chartDragHandler);
        chartLane.setOnMouseDragged(chartDragHandler);
        chartLane.setOnMouseClicked(chartDragHandler);
        chartLane.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseClickedHandler);
    }

    public void clearIntervalSelector() {
        intervalSelector.set(null);
    }

    @Override
    public IntervalSelector<DateTime> newIntervalSelector() {
        return new DetailIntervalSelector(this);
    }

    @Override
    public IntervalSelector<? extends DateTime> getIntervalSelector() {
        return intervalSelector.get();
    }

    public SimpleObjectProperty<IntervalSelector<? extends DateTime>> intervalSelector() {
        return intervalSelector;
    }

    @Override
    public void setIntervalSelector(IntervalSelector<? extends DateTime> newIntervalSelector) {
        intervalSelector.set(newIntervalSelector);
    }

    @Override
    public Axis<DateTime> getXAxis() {
        return detailsChartDateAxis;
    }

    @Override
    public TimeLineController getController() {
        return controller;
    }

    @Override
    public void clearContextMenu() {
        setContextMenu(null);
    }

    public ContextMenu getContextMenu(MouseEvent mouseEvent) throws MissingResourceException {
        ContextMenu contextMenu = getContextMenu();
        if (contextMenu != null) {
            contextMenu.hide();
        }
        setContextMenu(ActionUtils.createContextMenu(Arrays.asList(new PlaceMarkerAction(this, mouseEvent),
                ActionUtils.ACTION_SEPARATOR,
                TimeLineChart.newZoomHistoyActionGroup(getController())))
        );
        return getContextMenu();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new DetailsChartSkin(this);
    }

    ObservableList<EventStripe> getRootEventStripes() {
        return eventStripes;
    }

    @NbBundle.Messages({"HideDescriptionAction.displayName=Hide",
        "HideDescriptionAction.displayMsg=Hide this group from the details view."})
    static class HideDescriptionAction extends Action {

        static final Image HIDE = new Image("/org/sleuthkit/autopsy/timeline/images/eye--minus.png"); // NON-NLS

        HideDescriptionAction(String description, DescriptionLoD descriptionLoD, DetailsChart chart) {
            super(Bundle.HideDescriptionAction_displayName());
            setLongText(Bundle.HideDescriptionAction_displayMsg());
            setGraphic(new ImageView(HIDE));
            setEventHandler((ActionEvent t) -> {
                final DescriptionFilter testFilter = new DescriptionFilter(
                        descriptionLoD,
                        description,
                        DescriptionFilter.FilterMode.EXCLUDE);

                DescriptionFilter descriptionFilter = chart.getController().getQuickHideFilters().stream()
                        .filter(testFilter::equals)
                        .findFirst().orElseGet(() -> {
                    testFilter.selectedProperty().addListener(observable -> chart.requestLayout());
                            chart.getController().getQuickHideFilters().add(testFilter);
                            return testFilter;
                        });
                descriptionFilter.setSelected(true);
            });
        }
    }

    @NbBundle.Messages({"UnhideDescriptionAction.displayName=Unhide"})
    static class UnhideDescriptionAction extends Action {

        static final Image SHOW = new Image("/org/sleuthkit/autopsy/timeline/images/eye--plus.png"); // NON-NLS

        UnhideDescriptionAction(String description, DescriptionLoD descriptionLoD, DetailsChart chart) {
            super(Bundle.UnhideDescriptionAction_displayName());
            setGraphic(new ImageView(SHOW));
            setEventHandler((ActionEvent t) ->
                    chart.getController().getQuickHideFilters().stream()
                    .filter(descriptionFilter -> descriptionFilter.getDescriptionLoD().equals(descriptionLoD)
                            && descriptionFilter.getDescription().equals(description))
                    .forEach(descriptionfilter -> descriptionfilter.setSelected(false))
            );
        }
    }

    static private class DetailsChartSkin extends SkinBase<DetailsChart> {

        private static final int MIN_PINNED_LANE_HEIGHT = 50;

        private final PrimaryDetailsChartLane primaryLane;
        private final ScrollingLaneWrapper mainView;
        private final PinnedEventsChartLane pinnedLane;
        private final ScrollingLaneWrapper pinnedView;
        private final MasterDetailPane masterDetailPane;
        private final Pane rootPane;

        private double dividerPosition = .1;

        private IntervalSelector<? extends DateTime> intervalSelector;

        @NbBundle.Messages("DetailViewPane.pinnedLaneLabel.text=Pinned Events")
        DetailsChartSkin(DetailsChart chart) {
            super(chart);
            //initialize chart;
            primaryLane = new PrimaryDetailsChartLane(chart, getSkinnable().detailsChartDateAxis, getSkinnable().verticalAxis);

            mainView = new ScrollingLaneWrapper(primaryLane);

            pinnedLane = new PinnedEventsChartLane(chart, getSkinnable().pinnedDateAxis, new EventAxis<>(Bundle.DetailViewPane_pinnedLaneLabel_text()));
            pinnedView = new ScrollingLaneWrapper(pinnedLane);
            pinnedLane.setMinHeight(MIN_PINNED_LANE_HEIGHT);
            pinnedLane.maxVScrollProperty().addListener((Observable observable) -> syncPinnedHeight());
            syncPinnedHeight();

            masterDetailPane = new MasterDetailPane(Side.TOP, mainView, pinnedView, false);
            masterDetailPane.setDividerPosition(dividerPosition);
            masterDetailPane.prefHeightProperty().bind(getSkinnable().heightProperty());
            masterDetailPane.prefWidthProperty().bind(getSkinnable().widthProperty());

            rootPane = new Pane(masterDetailPane);
            getChildren().add(rootPane);

            //maintain highlighted effect on correct nodes
            getSkinnable().highlightPredicate.addListener((observable, oldPredicate, newPredicate) -> {
                getAllEventNodes().forEach(eNode ->
                        eNode.applyHighlightEffect(newPredicate.test(eNode)));
            });

            TimeLineChart.MouseClickedHandler<DateTime, DetailsChart> mouseClickedHandler = new TimeLineChart.MouseClickedHandler<>(getSkinnable());
            TimeLineChart.ChartDragHandler<DateTime, DetailsChart> chartDragHandler = new TimeLineChart.ChartDragHandler<>(getSkinnable());
            configureMouseListeners(primaryLane, mouseClickedHandler, chartDragHandler);
            configureMouseListeners(pinnedLane, mouseClickedHandler, chartDragHandler);

            getSkinnable().getLayoutSettings().pinnedLaneShowing().addListener(observable -> {
                boolean selected = getSkinnable().getLayoutSettings().isPinnedLaneShowing();
                if (selected == false) {
                    dividerPosition = masterDetailPane.getDividerPosition();
                }
                masterDetailPane.setShowDetailNode(selected);
                if (selected) {
                    syncPinnedHeight();
                    masterDetailPane.setDividerPosition(dividerPosition);
                }
            });

            getSkinnable().intervalSelector().addListener(observable -> {
                if (getSkinnable().getIntervalSelector() == null) {
                    rootPane.getChildren().remove(intervalSelector);
                    intervalSelector = null;
                } else {
                    rootPane.getChildren().add(getSkinnable().getIntervalSelector());
                    intervalSelector = getSkinnable().getIntervalSelector();
                }
            });

            getSkinnable().guideLines.addListener((SetChangeListener.Change<? extends GuideLine> change) -> {
                if (change.wasRemoved()) {
                    rootPane.getChildren().remove(change.getElementRemoved());
                }
                if (change.wasAdded()) {
                    rootPane.getChildren().add(change.getElementAdded());
                }
            });
        }

        private Iterable<EventNodeBase<?>> getAllEventNodes() {
            return Iterables.concat(primaryLane.getAllNodes(), pinnedLane.getAllNodes());
        }

        /**
         * get the DateTime along the x-axis that corresponds to the given
         * x-coordinate in the coordinate system of this
         * {@link PrimaryDetailsChart}
         *
         * @param x a x-coordinate in the space of this
         *          {@link PrimaryDetailsChart}
         *
         * @return the DateTime along the x-axis corresponding to the given x
         *         value (in the space of this {@link PrimaryDetailsChart}
         */
        public DateTime getDateTimeForPosition(double x) {
            return getSkinnable().getXAxis().getValueForDisplay(getSkinnable().getXAxis().parentToLocal(x, 0).getX());
        }

        private void syncPinnedHeight() {
            pinnedView.setMinHeight(MIN_PINNED_LANE_HEIGHT);
            pinnedView.setMaxHeight(pinnedLane.maxVScrollProperty().get() + 30);
        }
    }
}
