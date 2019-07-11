/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-18 Basis Technology Corp.
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

import com.google.common.collect.Iterables;
import java.util.Arrays;
import java.util.Set;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.OverrunStyle;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import static org.sleuthkit.autopsy.timeline.ui.detailview.EventNodeBase.configureActionButton;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.SingleDetailsViewEvent;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TimelineEvent;

/**
 * Node used in DetailsChart to represent an EventStripe.
 */
final public class EventStripeNode extends MultiEventNodeBase<EventStripe, EventCluster, EventClusterNode> {

    /**
     * The button to expand hide stripes with this description, created lazily.
     */
    private Button hideButton;

    /**
     * Constructor
     *
     * @param chartLane   the DetailsChartLane this node belongs to
     * @param eventStripe the EventStripe represented by this node
     * @param parentNode  the EventClusterNode that is the parent of this node.
     */
    EventStripeNode(DetailsChartLane<?> chartLane, EventStripe eventStripe, EventClusterNode parentNode) throws TskCoreException {
        super(chartLane, eventStripe, parentNode);

        //setup description label
        descrLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        descrLabel.setPrefWidth(USE_COMPUTED_SIZE);

        setMinHeight(24);
        setAlignment(subNodePane, Pos.BOTTOM_LEFT);

        if (eventStripe.getClusters().size() > 1) {
            for (EventCluster cluster : eventStripe.getClusters()) {
                subNodes.add(createChildNode(cluster.withParent(eventStripe)));
            }
            //stack componenets vertically
            getChildren().addAll(new VBox(infoHBox, subNodePane));
        } else {
            //if the stripe only has one cluster, use alternate simpler layout
            EventNodeBase<?> childNode;
            EventCluster cluster = Iterables.getOnlyElement(eventStripe.getClusters()).withParent(eventStripe);
            if (cluster.getEventIDs().size() == 1) {
                childNode = createChildNode(cluster);
            } else {
                //if the cluster has more than one event, add the clusters controls to this stripe node directly.
                EventClusterNode eventClusterNode = (EventClusterNode) createChildNode(cluster);
                eventClusterNode.installActionButtons();
                controlsHBox.getChildren().addAll(eventClusterNode.getNewCollapseButton(), eventClusterNode.getNewExpandButton());
                eventClusterNode.infoHBox.getChildren().remove(eventClusterNode.countLabel);
                childNode = eventClusterNode;
            }

            //hide the cluster description
            childNode.setDescriptionVisibility(DescriptionVisibility.HIDDEN);

            subNodes.add(childNode);
            //stack componenet in z rather than vertically
            getChildren().addAll(infoHBox, subNodePane);
        }
    }

    /**
     * Get a new Action that hides stripes with the same description as this
     * one.
     *
     * @return a new Action that hides stripes with the same description as this
     *         one.
     */
    private Action newHideAction() {
        return new HideDescriptionAction(getDescription(), getEvent().getDescriptionLevel(), chartLane.getParentChart());
    }

    @Override
    protected void installActionButtons() {
        super.installActionButtons();
        if (chartLane.quickHideFiltersEnabled() && hideButton == null) {
            hideButton = ActionUtils.createButton(newHideAction(), ActionUtils.ActionTextBehavior.HIDE);
            configureActionButton(hideButton);

            controlsHBox.getChildren().add(hideButton);
        }
    }

    @Override
    protected EventNodeBase<?> createChildNode(EventCluster cluster) throws TskCoreException {
        Set<Long> eventIDs = cluster.getEventIDs();
        if (eventIDs.size() == 1) {
            TimelineEvent singleEvent = getController().getEventsModel().getEventById(Iterables.getOnlyElement(eventIDs));
            SingleDetailsViewEvent singleDetailEvent = new SingleDetailsViewEvent(singleEvent).withParent(cluster);
            return new SingleEventNode(getChartLane(), singleDetailEvent, this);
        } else {
            return new EventClusterNode(getChartLane(), cluster, this);
        }
    }

    @Override
    EventHandler<MouseEvent> getDoubleClickHandler() {
        return mouseEvent -> {
            //no-op
        };
    }

    @Override
    Iterable<? extends Action> getActions() {
        return Iterables.concat(
                super.getActions(),
                Arrays.asList(newHideAction())
        );
    }
}
