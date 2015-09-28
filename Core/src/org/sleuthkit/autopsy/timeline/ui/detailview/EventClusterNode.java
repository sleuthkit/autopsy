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
package org.sleuthkit.autopsy.timeline.ui.detailview;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ColorUtilities;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Represents an {@link EventCluster} in a {@link EventDetailChart}.
 */
public class EventClusterNode extends AbstractDetailViewNode<EventCluster, EventClusterNode> {

    private static final Logger LOGGER = Logger.getLogger(EventClusterNode.class.getName());

    /**
     * the region that represents the time span of this node's event
     */
    private final Region spanRegion = new Region();

    /**
     * the context menu that with the slider that controls subnode/event display
     *
     * //TODO: move more of the control of subnodes/events here and out of
     * EventDetail Chart
     */
    private final SimpleObjectProperty<ContextMenu> contextMenu = new SimpleObjectProperty<>();

    private Tooltip tooltip;

    public EventClusterNode(final EventCluster eventCluster, EventClusterNode parentEventNode, EventDetailChart chart) {
        super(chart, eventCluster, parentEventNode);
        minWidthProperty().bind(spanRegion.widthProperty());
        header.setPrefWidth(USE_COMPUTED_SIZE);

        final BorderPane borderPane = new BorderPane(getSubNodePane(), header, null, null, null);
        BorderPane.setAlignment(getSubNodePane(), Pos.TOP_LEFT);
        borderPane.setPrefWidth(USE_COMPUTED_SIZE);

        getChildren().addAll(spanRegion, borderPane);

        //setup backgrounds
        spanRegion.setStyle("-fx-border-width:2 0 2 2; -fx-border-radius: 2; -fx-border-color: " + ColorUtilities.getRGBCode(evtColor) + ";"); // NON-NLS
        spanRegion.setBackground(getBackground());

    }

    @Override
    synchronized void installTooltip() {
        //TODO: all this work should probably go on a background thread...
        if (tooltip == null) {
            HashMap<String, Long> hashSetCounts = new HashMap<>();
            if (!getEventCluster().getEventIDsWithHashHits().isEmpty()) {
                hashSetCounts = new HashMap<>();
                try {
                    for (TimeLineEvent tle : getEventsModel().getEventsById(getEventCluster().getEventIDsWithHashHits())) {
                        Set<String> hashSetNames = getSleuthkitCase().getAbstractFileById(tle.getFileID()).getHashSetNames();
                        for (String hashSetName : hashSetNames) {
                            hashSetCounts.merge(hashSetName, 1L, Long::sum);
                        }
                    }
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "Error getting hashset hit info for event.", ex);
                }
            }

            Map<String, Long> tagCounts = new HashMap<>();
            if (!getEventCluster().getEventIDsWithTags().isEmpty()) {
                tagCounts.putAll(getEventsModel().getTagCountsByTagName(getEventCluster().getEventIDsWithTags()));

            }

            String hashSetCountsString = hashSetCounts.entrySet().stream()
                    .map((Map.Entry<String, Long> t) -> t.getKey() + " : " + t.getValue())
                    .collect(Collectors.joining("\n"));
            String tagCountsString = tagCounts.entrySet().stream()
                    .map((Map.Entry<String, Long> t) -> t.getKey() + " : " + t.getValue())
                    .collect(Collectors.joining("\n"));

            tooltip = new Tooltip(
                    NbBundle.getMessage(this.getClass(), "AggregateEventNode.installTooltip.text",
                            getEventCluster().getEventIDs().size(), getEventCluster().getEventType(), getEventCluster().getDescription(),
                            getEventCluster().getSpan().getStart().toString(TimeLineController.getZonedFormatter()),
                            getEventCluster().getSpan().getEnd().toString(TimeLineController.getZonedFormatter()))
                    + (hashSetCountsString.isEmpty() ? "" : "\n\nHash Set Hits\n" + hashSetCountsString)
                    + (tagCountsString.isEmpty() ? "" : "\n\nTags\n" + tagCountsString)
            );
            Tooltip.install(EventClusterNode.this, tooltip);
        }
    }

    synchronized public EventCluster getEventCluster() {
        return getEventBundle();
    }

    /**
     * sets the width of the {@link Region} with border and background used to
     * indicate the temporal span of this aggregate event
     *
     * @param w
     */
    private void setSpanWidth(double w) {
        spanRegion.setPrefWidth(w);
        spanRegion.setMaxWidth(w);
        spanRegion.setMinWidth(Math.max(2, w));
    }

    @Override
    public void setSpanWidths(List<Double> spanWidths) {
        setSpanWidth(spanWidths.get(0));

    }

    @Override
    Region getSpanFillNode() {
        return spanRegion;
    }

    /**
     * @return the contextMenu
     */
    public ContextMenu getContextMenu() {
        return contextMenu.get();
    }

    /**
     * @param contextMenu the contextMenu to set
     */
    public void setContextMenu(ContextMenu contextMenu) {
        this.contextMenu.set(contextMenu);
    }

    @Override
    void showSpans(boolean showSpans) {
        //no-op for now
    }

    @Override
    List<EventCluster> makeBundlesFromClusters(List<EventCluster> eventClusters) {
        return eventClusters;
    }

    @Override
    EventClusterNode getNodeForBundle(EventCluster cluster) {
        return new EventClusterNode(cluster, this, getChart());
    }

}
