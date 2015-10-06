/*

 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.HBox;
import static javafx.scene.layout.Region.USE_PREF_SIZE;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Node used in {@link EventDetailChart} to represent an EventStripe.
 */
final public class EventStripeNode extends EventBundleNodeBase<EventStripe, EventCluster, EventClusterNode> {

    private static final Logger LOGGER = Logger.getLogger(EventStripeNode.class.getName());
    private static final Image HASH_PIN = new Image("/org/sleuthkit/autopsy/images/hashset_hits.png"); //NOI18N

    private static final Image TAG = new Image("/org/sleuthkit/autopsy/images/green-tag-icon-16.png"); // NON-NLS //NOI18N

    private final static Map<EventType, DropShadow> dropShadowMap = new ConcurrentHashMap<>();
    private static final Border SELECTION_BORDER = new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CORNER_RADII_3, new BorderWidths(2)));

    static void configureLoDButton(Button b) {
        b.setMinSize(16, 16);
        b.setMaxSize(16, 16);
        b.setPrefSize(16, 16);
        show(b, false);
    }

    static void show(Node b, boolean show) {
        b.setVisible(show);
        b.setManaged(show);
    }

    private DescriptionVisibility descrVis;
    private Tooltip tooltip;

    /**
     * Pane that contains EventStripeNodes for any 'subevents' if they are
     * displayed
     *
     * //TODO: move more of the control of subnodes/events here and out of
     * EventDetail Chart
     */
//    private final HBox clustersHBox = new HBox();
    private final ImageView eventTypeImageView = new ImageView();
    private final Label descrLabel = new Label("", eventTypeImageView);
    private final Label countLabel = new Label();

    private final ImageView hashIV = new ImageView(HASH_PIN);
    private final ImageView tagIV = new ImageView(TAG);
    private final HBox infoHBox;

    public EventStripeNode(EventDetailChart chart, EventStripe eventStripe, EventClusterNode parentNode) {
        super(chart, eventStripe, parentNode);
       
        minWidthProperty().bind(subNodePane.widthProperty());

        if (eventStripe.getEventIDsWithHashHits().isEmpty()) {
            show(hashIV, false);
        }
        if (eventStripe.getEventIDsWithTags().isEmpty()) {
            show(tagIV, false);
        }
        setMinHeight(24);
        infoHBox = new HBox(5, descrLabel, countLabel, hashIV, tagIV);

        //initialize info hbox
        infoHBox.getChildren().add(4, hideButton);
        infoHBox.setMinWidth(USE_PREF_SIZE);
        infoHBox.setPadding(new Insets(2, 5, 2, 5));
        infoHBox.setAlignment(Pos.TOP_LEFT);
        infoHBox.setPickOnBounds(false);
        //setup description label

        eventTypeImageView.setImage(getEventType().getFXImage());
        descrLabel.setPrefWidth(USE_COMPUTED_SIZE);
        descrLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        descrLabel.setMouseTransparent(true);

//        subNodePane.setPickOnBounds(false);
        setAlignment(subNodePane, Pos.BOTTOM_LEFT);
        for (EventCluster cluster : eventStripe.getClusters()) {
            EventClusterNode clusterNode = new EventClusterNode(chart, cluster, this);
            subNodes.add(clusterNode);
            subNodePane.getChildren().addAll(clusterNode);
        }

        getChildren().addAll(new VBox(infoHBox, subNodePane));

        setOnMouseClicked(new MouseClickHandler());

        //set up mouse hover effect and tooltip
        setOnMouseEntered((MouseEvent e) -> {
            /*
             * defer tooltip creation till needed, this had a surprisingly large
             * impact on speed of loading the chart
             */
            installTooltip();
            showDescriptionLoDControls(true);
//            toFront();
        });

        setOnMouseExited((MouseEvent e) -> {
            showDescriptionLoDControls(false);
        });

    }

    void showDescriptionLoDControls(final boolean showControls) {
        DropShadow dropShadow = dropShadowMap.computeIfAbsent(getEventType(),
                eventType -> new DropShadow(10, eventType.getColor()));
        subNodePane.setEffect(showControls ? dropShadow : null);
        show(hideButton, showControls);
    }

   

    public EventStripe getEventStripe() {
        return getEventBundle();
    }

    @NbBundle.Messages({"# {0} - counts",
        "# {1} - event type",
        "# {2} - description",
        "# {3} - start date/time",
        "# {4} - end date/time",
        "EventStripeNode.tooltip.text={0} {1} events\n{2}\nbetween\t{3}\nand   \t{4}"})
    @Override
    synchronized void installTooltip() {
        if (tooltip == null) {
            final Task<String> tooltTipTask = new Task<String>() {

                @Override
                protected String call() throws Exception {
                    HashMap<String, Long> hashSetCounts = new HashMap<>();
                    if (!getEventStripe().getEventIDsWithHashHits().isEmpty()) {
                        hashSetCounts = new HashMap<>();
                        try {
                            for (TimeLineEvent tle : eventsModel.getEventsById(getEventStripe().getEventIDsWithHashHits())) {
                                Set<String> hashSetNames = sleuthkitCase.getAbstractFileById(tle.getFileID()).getHashSetNames();
                                for (String hashSetName : hashSetNames) {
                                    hashSetCounts.merge(hashSetName, 1L, Long::sum);
                                }
                            }
                        } catch (TskCoreException ex) {
                            LOGGER.log(Level.SEVERE, "Error getting hashset hit info for event.", ex);
                        }
                    }

                    Map<String, Long> tagCounts = new HashMap<>();
                    if (getEventStripe().getEventIDsWithTags().isEmpty() == false) {
                        tagCounts.putAll(eventsModel.getTagCountsByTagName(getEventStripe().getEventIDsWithTags()));
                    }

                    String hashSetCountsString = hashSetCounts.entrySet().stream()
                            .map((Map.Entry<String, Long> t) -> t.getKey() + " : " + t.getValue())
                            .collect(Collectors.joining("\n"));
                    String tagCountsString = tagCounts.entrySet().stream()
                            .map((Map.Entry<String, Long> t) -> t.getKey() + " : " + t.getValue())
                            .collect(Collectors.joining("\n"));
                    return Bundle.EventStripeNode_tooltip_text(getEventStripe().getEventIDs().size(), getEventStripe().getEventType(), getEventStripe().getDescription(),
                            TimeLineController.getZonedFormatter().print(getEventStripe().getStartMillis()),
                            TimeLineController.getZonedFormatter().print(getEventStripe().getEndMillis() + 1000))
                            + (hashSetCountsString.isEmpty() ? "" : "\n\nHash Set Hits\n" + hashSetCountsString)
                            + (tagCountsString.isEmpty() ? "" : "\n\nTags\n" + tagCountsString);
                }

                @Override
                protected void succeeded() {
                    super.succeeded();
                    try {
                        tooltip = new Tooltip(get());
                        Tooltip.install(EventStripeNode.this, tooltip);
                    } catch (InterruptedException | ExecutionException ex) {
                        LOGGER.log(Level.SEVERE, "Tooltip generation failed.", ex);
                        Tooltip.uninstall(EventStripeNode.this, tooltip);
                        tooltip = null;
                    }
                }
            };

            chart.getController().monitorTask(tooltTipTask);
        }
    }

    /**
     * @param w the maximum width the description label should have
     */
    public void setDescriptionWidth(double w) {
        descrLabel.setMaxWidth(w);
    }

    /**
     * apply the 'effect' to visually indicate selection
     *
     * @param applied true to apply the selection 'effect', false to remove it
     */
    @Override
    public void applySelectionEffect(boolean applied) {
        setBorder(applied ? SELECTION_BORDER : null);
    }

    /**
     * apply the 'effect' to visually indicate highlighted nodes
     *
     * @param applied true to apply the highlight 'effect', false to remove it
     */
    @Override
    public synchronized void applyHighlightEffect(boolean applied) {
        if (applied) {
            descrLabel.setStyle("-fx-font-weight: bold;"); // NON-NLS
            setBackground(highlightedBackground);
        } else {
            descrLabel.setStyle("-fx-font-weight: normal;"); // NON-NLS
            setBackground(defaultBackground);
        }
    }

    @Override
    public void setDescriptionVisibility(DescriptionVisibility descrVis) {
        this.descrVis = descrVis;
        final int size = getEventStripe().getEventIDs().size();

        switch (this.descrVis) {
            case HIDDEN:
                countLabel.setText("");
                descrLabel.setText("");
                break;
            case COUNT_ONLY:
                descrLabel.setText("");
                countLabel.setText(String.valueOf(size));
                break;
            default:
            case SHOWN:
                String description = getEventStripe().getDescription();
                description = parentNode != null
                        ? "    ..." + StringUtils.substringAfter(description, parentNode.getDescription())
                        : description;
                descrLabel.setText(description);
                countLabel.setText(((size == 1) ? "" : " (" + size + ")")); // NON-NLS
                break;
        }
    }

    /**
     * event handler used for mouse events on {@link EventStripeNode}s
     */
    private class MouseClickHandler implements EventHandler<MouseEvent> {

        private ContextMenu contextMenu;

        @Override
        public void handle(MouseEvent t) {

            if (t.getButton() == MouseButton.PRIMARY) {
                t.consume();
                if (t.isShiftDown()) {
                    if (chart.selectedNodes.contains(EventStripeNode.this) == false) {
                        chart.selectedNodes.add(EventStripeNode.this);
                    }
                } else if (t.isShortcutDown()) {
                    chart.selectedNodes.removeAll(EventStripeNode.this);
                } else {
                    chart.selectedNodes.setAll(EventStripeNode.this);
                }
                t.consume();
            } else if (t.getButton() == MouseButton.SECONDARY) {
                ContextMenu chartContextMenu = chart.getChartContextMenu(t);
                if (contextMenu == null) {
                    contextMenu = new ContextMenu();
                    contextMenu.setAutoHide(true);

                    contextMenu.getItems().add(new SeparatorMenuItem());
                    contextMenu.getItems().addAll(chartContextMenu.getItems());
                }
                contextMenu.show(EventStripeNode.this, t.getScreenX(), t.getScreenY());
                t.consume();
            }
        }
    }

  
}
