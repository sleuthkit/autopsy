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

import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.action.ActionUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import static org.sleuthkit.autopsy.timeline.ui.detailview.EventBundleNodeBase.configureLoDButton;

/**
 * Node used in {@link EventDetailsChart} to represent an EventStripe.
 */
final public class EventStripeNode extends EventBundleNodeBase<EventStripe, EventCluster, EventClusterNode> {

    private static final Logger LOGGER = Logger.getLogger(EventStripeNode.class.getName());
    final Button hideButton;
    /**
     * Pane that contains EventStripeNodes for any 'subevents' if they are
     * displayed
     *
     * //TODO: move more of the control of subnodes/events here and out of
     * EventDetail Chart
     */
//    private final HBox clustersHBox = new HBox();
    private final ImageView eventTypeImageView = new ImageView();

    public EventStripeNode(EventDetailsChart chart, EventStripe eventStripe, EventClusterNode parentNode) {
        super(chart, eventStripe, parentNode);

        setMinHeight(48);

        EventDetailsChart.HideDescriptionAction hideClusterAction = chart.new HideDescriptionAction(getDescription(), eventBundle.getDescriptionLoD());
        hideButton = ActionUtils.createButton(hideClusterAction, ActionUtils.ActionTextBehavior.HIDE);
        configureLoDButton(hideButton);

        infoHBox.getChildren().add(hideButton);
        //setup description label
        eventTypeImageView.setImage(getEventType().getFXImage());
        descrLabel.setPrefWidth(USE_COMPUTED_SIZE);
        descrLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        descrLabel.setGraphic(eventTypeImageView);

        setAlignment(subNodePane, Pos.BOTTOM_LEFT);
        for (EventCluster cluster : eventStripe.getClusters()) {
            EventClusterNode clusterNode = new EventClusterNode(chart, cluster, this);
            subNodes.add(clusterNode);
            subNodePane.getChildren().addAll(clusterNode);
        }

        getChildren().addAll(new VBox(infoHBox, subNodePane));
        setOnMouseClicked(new MouseClickHandler());
    }

    @Override
    void showHoverControls(final boolean showControls) {
        super.showHoverControls(showControls);
        show(hideButton, showControls);
    }

    public EventStripe getEventStripe() {
        return getEventBundle();
    }

    /**
     * @param w the maximum width the description label should have
     */
    @Override
    public void setDescriptionWidth(double w) {
        descrLabel.setMaxWidth(w);
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
    void setDescriptionVisibiltiyImpl(DescriptionVisibility descrVis) {
        final int size = getEventStripe().getEventIDs().size();

        switch (descrVis) {
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

                    EventDetailsChart.HideDescriptionAction hideClusterAction = chart.new HideDescriptionAction(getDescription(), eventBundle.getDescriptionLoD());
                    MenuItem hideDescriptionMenuItem = ActionUtils.createMenuItem(hideClusterAction);
                    contextMenu.getItems().addAll(hideDescriptionMenuItem);
                    contextMenu.getItems().addAll(chartContextMenu.getItems());
                }
                contextMenu.show(EventStripeNode.this, t.getScreenX(), t.getScreenY());
                t.consume();
            }
        }
    }


}
