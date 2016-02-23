/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-16 Basis Technology Corp.
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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import org.controlsfx.control.action.Action;
import org.joda.time.DateTime;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.MultiEvent;
import org.sleuthkit.autopsy.timeline.datamodel.SingleEvent;
import org.sleuthkit.autopsy.timeline.ui.AbstractVisualizationPane;
import static org.sleuthkit.autopsy.timeline.ui.detailview.EventNodeBase.show;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
@NbBundle.Messages({"EventBundleNodeBase.toolTip.loading=loading..."})
public abstract class MultiEventNodeBase< BundleType extends MultiEvent<ParentType>, ParentType extends MultiEvent<BundleType>, ParentNodeType extends MultiEventNodeBase<
        ParentType, BundleType, ?>> extends EventNodeBase<BundleType> {

    private static final Logger LOGGER = Logger.getLogger(MultiEventNodeBase.class.getName());
//    private static final Image HASH_PIN = new Image("/org/sleuthkit/autopsy/images/hashset_hits.png"); //NOI18N NON-NLS
//    private static final Image TAG = new Image("/org/sleuthkit/autopsy/images/green-tag-icon-16.png"); // NON-NLS //NOI18N

    static final CornerRadii CORNER_RADII_3 = new CornerRadii(3);
    static final CornerRadii CORNER_RADII_1 = new CornerRadii(1);

    final SleuthkitCase sleuthkitCase;
    final FilteredEventsModel eventsModel;

    final ObservableList<EventNodeBase<?>> subNodes = FXCollections.observableArrayList();
    final Pane subNodePane = new Pane();

    private final Tooltip tooltip = new Tooltip(Bundle.EventBundleNodeBase_toolTip_loading());
    private Timeline timeline;

    MultiEventNodeBase(DetailsChart chart, BundleType eventBundle, ParentNodeType parentNode) {
        super(eventBundle, parentNode, chart);
        this.descLOD.set(eventBundle.getDescriptionLoD());
        sleuthkitCase = chart.getController().getAutopsyCase().getSleuthkitCase();
        eventsModel = chart.getController().getEventsModel();

        if (eventBundle.getEventIDsWithHashHits().isEmpty()) {
            show(hashIV, false);
        }
        if (eventBundle.getEventIDsWithTags().isEmpty()) {
            show(tagIV, false);
        }

        setAlignment(Pos.TOP_LEFT);
        setMaxWidth(USE_PREF_SIZE);
        infoHBox.setMaxWidth(USE_PREF_SIZE);
        subNodePane.setPrefWidth(USE_COMPUTED_SIZE);
        subNodePane.setMinWidth(USE_PREF_SIZE);
        subNodePane.setMaxWidth(USE_PREF_SIZE);
        /*
         * This triggers the layout when a mousover causes the action buttons to
         * interesect with another node, forcing it down.
         */
        heightProperty().addListener(heightProp -> chart.requestTimelineChartLayout());
        Platform.runLater(() ->
                setLayoutX(chart.getXAxis().getDisplayPosition(new DateTime(eventBundle.getStartMillis())) - getLayoutXCompensation())
        );

        //initialize info hbox
        infoHBox.setPadding(new Insets(2, 3, 2, 3));
        infoHBox.setAlignment(Pos.TOP_LEFT);

        Tooltip.install(this, this.tooltip);

        //set up mouse hover effect and tooltip
        setOnMouseEntered((MouseEvent e) -> {
            Tooltip.uninstall(chart.asNode(), AbstractVisualizationPane.getDefaultTooltip());
            showHoverControls(true);
            toFront();
        });
        setOnMouseExited((MouseEvent event) -> {
            showHoverControls(false);
            if (parentNode != null) {
                parentNode.showHoverControls(true);
            } else {
                Tooltip.install(chart.asNode(), AbstractVisualizationPane.getDefaultTooltip());
            }
        });
        setOnMouseClicked(new ClickHandler());

        Bindings.bindContent(subNodePane.getChildren(), subNodes);
    }

    @Override
    void requestChartLayout() {
        getChart().requestTimelineChartLayout();
    }

    public DetailsChart getChart() {
        return chart;
    }

    final DescriptionLoD getDescriptionLoD() {
        return descLOD.get();
    }

    public final BundleType getEventBundle() {
        return tlEvent;
    }

    /**
     * defer tooltip content creation till needed, this had a surprisingly large
     * impact on speed of loading the chart
     */
    @NbBundle.Messages({"# {0} - counts",
        "# {1} - event type",
        "# {2} - description",
        "# {3} - start date/time",
        "# {4} - end date/time",
        "EventBundleNodeBase.tooltip.text={0} {1} events\n{2}\nbetween\t{3}\nand   \t{4}",
        "EventBundleNodeBase.toolTip.loading2=loading tooltip",
        "# {0} - hash set count string",
        "EventBundleNodeBase.toolTip.hashSetHits=\n\nHash Set Hits\n{0}",
        "# {0} - tag count string",
        "EventBundleNodeBase.toolTip.tags=\n\nTags\n{0}"})
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void installTooltip() {
        if (tooltip.getText().equalsIgnoreCase(Bundle.EventBundleNodeBase_toolTip_loading())) {
            final Task<String> tooltTipTask = new Task<String>() {
                {
                    updateTitle(Bundle.EventBundleNodeBase_toolTip_loading2());
                }

                @Override
                protected String call() throws Exception {
                    HashMap<String, Long> hashSetCounts = new HashMap<>();
                    if (tlEvent.getEventIDsWithHashHits().isEmpty() == false) {
                        try {
                            //TODO:push this to DB
                            for (SingleEvent tle : eventsModel.getEventsById(tlEvent.getEventIDsWithHashHits())) {
                                Set<String> hashSetNames = sleuthkitCase.getAbstractFileById(tle.getFileID()).getHashSetNames();
                                for (String hashSetName : hashSetNames) {
                                    hashSetCounts.merge(hashSetName, 1L, Long::sum);
                                }
                            }
                        } catch (TskCoreException ex) {
                            LOGGER.log(Level.SEVERE, "Error getting hashset hit info for event.", ex); //NON-NLS
                        }
                    }
                    String hashSetCountsString = hashSetCounts.entrySet().stream()
                            .map((Map.Entry<String, Long> t) -> t.getKey() + " : " + t.getValue())
                            .collect(Collectors.joining("\n"));

                    Map<String, Long> tagCounts = new HashMap<>();
                    if (tlEvent.getEventIDsWithTags().isEmpty() == false) {
                        tagCounts.putAll(eventsModel.getTagCountsByTagName(tlEvent.getEventIDsWithTags()));
                    }
                    String tagCountsString = tagCounts.entrySet().stream()
                            .map((Map.Entry<String, Long> t) -> t.getKey() + " : " + t.getValue())
                            .collect(Collectors.joining("\n"));

                    return Bundle.EventBundleNodeBase_tooltip_text(getEventIDs().size(), getEventType(), getDescription(),
                            TimeLineController.getZonedFormatter().print(getStartMillis()),
                            TimeLineController.getZonedFormatter().print(getEndMillis() + 1000))
                            + (hashSetCountsString.isEmpty() ? "" : Bundle.EventBundleNodeBase_toolTip_hashSetHits(hashSetCountsString))
                            + (tagCountsString.isEmpty() ? "" : Bundle.EventBundleNodeBase_toolTip_tags(tagCountsString));
                }

                @Override
                protected void succeeded() {
                    super.succeeded();
                    try {
                        tooltip.setText(get());
                        tooltip.setGraphic(null);
                    } catch (InterruptedException | ExecutionException ex) {
                        LOGGER.log(Level.SEVERE, "Tooltip generation failed.", ex); //NON-NLS
                    }
                }
            };
            new Thread(tooltTipTask).start();
            chart.getController().monitorTask(tooltTipTask);
        }
    }

    /**
     * apply the 'effect' to visually indicate highlighted nodes
     *
     * @param applied true to apply the highlight 'effect', false to remove it
     */
    abstract void applyHighlightEffect(boolean applied);

    @SuppressWarnings("unchecked")
    public List<EventNodeBase<?>> getSubNodes() {
        return subNodes;
    }

    final String getDescription() {
        return getEventBundle().getDescription();
    }

    final long getStartMillis() {
        return getEventBundle().getStartMillis();
    }

    final long getEndMillis() {
        return getEventBundle().getEndMillis();
    }

    final Set<Long> getEventIDs() {
        return getEventBundle().getEventIDs();
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected void layoutChildren() {
        chart.layoutEventBundleNodes(subNodes, 0);
        super.layoutChildren();
    }

    abstract EventNodeBase<?> createChildNode(ParentType rawChild);

    void animateTo(double xLeft, double yTop) {
        if (timeline != null) {
            timeline.stop();
            Platform.runLater(chart::requestTimelineChartLayout);
        }
        timeline = new Timeline(new KeyFrame(Duration.millis(100),
                new KeyValue(layoutXProperty(), xLeft),
                new KeyValue(layoutYProperty(), yTop))
        );
        timeline.setOnFinished(finished -> Platform.runLater(chart::requestTimelineChartLayout));
        timeline.play();
    }

    abstract EventHandler<MouseEvent> getDoubleClickHandler();

    abstract Collection<? extends Action> getActions();

}
