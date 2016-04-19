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

import com.google.common.collect.Iterables;
import java.util.Arrays;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.OverrunStyle;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import static org.sleuthkit.autopsy.timeline.ui.detailview.EventNodeBase.configureActionButton;

/**
 * Node used in {@link EventDetailsChart} to represent an EventStripe.
 */
final public class EventStripeNode extends MultiEventNodeBase<EventStripe, EventCluster, EventClusterNode> {

    private static final Logger LOGGER = Logger.getLogger(EventStripeNode.class.getName());

    private Action newHideAction() {
        return new HideDescriptionAction(getDescription(), getEvent().getDescriptionLoD(), chartLane.getParentChart());
    }
    private Button hideButton;

    EventStripeNode(DetailsChartLane<?> chartLane, EventStripe eventStripe, EventClusterNode parentNode) {
        super(chartLane, eventStripe, parentNode);
        setMinHeight(24);
        //setup description label
        descrLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        descrLabel.setPrefWidth(USE_COMPUTED_SIZE);

        setAlignment(subNodePane, Pos.BOTTOM_LEFT);

        if (eventStripe.getClusters().size() > 1) {
            for (EventCluster cluster : eventStripe.getClusters()) {
                subNodes.add(createChildNode(cluster.withParent(eventStripe)));
            }
            getChildren().addAll(new VBox(infoHBox, subNodePane));
        } else {
            EventNodeBase<?> childNode;
            EventCluster cluster = Iterables.getOnlyElement(eventStripe.getClusters()).withParent(eventStripe);
            if (cluster.getEventIDs().size() == 1) {
                childNode = createChildNode(cluster);
            } else {
                EventClusterNode eventClusterNode = (EventClusterNode) createChildNode(cluster);
                eventClusterNode.installActionButtons();
                controlsHBox.getChildren().addAll(eventClusterNode.getNewCollapseButton(), eventClusterNode.getNewExpandButton());
                eventClusterNode.infoHBox.getChildren().remove(eventClusterNode.countLabel);
                childNode = eventClusterNode;
            }

            childNode.setDescriptionVisibility(DescriptionVisibility.HIDDEN);
            subNodes.add(childNode);
            getChildren().addAll(infoHBox, subNodePane);
        }
    }

    public EventStripe getEventStripe() {
        return getEvent();
    }

    @Override
    void installActionButtons() {
        super.installActionButtons();
        if (chartLane.quickHideFiltersEnabled() && hideButton == null) {
            hideButton = ActionUtils.createButton(newHideAction(),
                    ActionUtils.ActionTextBehavior.HIDE);
            configureActionButton(hideButton);

            controlsHBox.getChildren().add(hideButton);
        }
    }

    @Override
    EventNodeBase<?> createChildNode(EventCluster cluster) {
        if (cluster.getEventIDs().size() == 1) {
            return new SingleEventNode(getChartLane(), getChartLane().getController().getEventsModel().getEventById(Iterables.getOnlyElement(cluster.getEventIDs())).withParent(cluster), this);
        } else {
            return new EventClusterNode(getChartLane(), cluster, this);
        }
    }

    @Override
    EventHandler<MouseEvent> getDoubleClickHandler() {
        return mouseEvent -> {
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
