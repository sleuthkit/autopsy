/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.detailview;

import com.google.common.collect.Range;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import static javafx.scene.layout.Region.USE_PREF_SIZE;
import javafx.scene.layout.VBox;
import org.sleuthkit.autopsy.coreutils.ColorUtilities;
import org.sleuthkit.autopsy.timeline.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;
import static org.sleuthkit.autopsy.timeline.ui.detailview.AbstractDetailViewNode.show;

/**
 *
 */
public class EventStripeNode extends AbstractDetailViewNode<EventStripe, EventStripeNode> {

    private final HBox rangesHBox = new HBox();

    EventStripeNode(EventStripe eventStripe, EventStripeNode parentNode, EventDetailChart chart) {
        super(chart, eventStripe, parentNode);
        minWidthProperty().bind(rangesHBox.widthProperty());
        final VBox internalVBox = new VBox(header, getSubNodePane());
        internalVBox.setAlignment(Pos.CENTER_LEFT);

        for (Range<Long> range : eventStripe.getRanges()) {
            Region rangeRegion = new Region();
            rangeRegion.setStyle("-fx-border-width:2 1 2 1; -fx-border-radius: 1; -fx-border-color: " + ColorUtilities.getRGBCode(evtColor.deriveColor(0, 1, 1, .3)) + ";"); // NON-NLS
            rangeRegion.setBackground(new Background(new BackgroundFill(evtColor.deriveColor(0, 1, 1, .2), CORNER_RADII, Insets.EMPTY)));
            rangesHBox.getChildren().addAll(rangeRegion, new Region());
        }
        rangesHBox.getChildren().remove(rangesHBox.getChildren().size() - 1);
        rangesHBox.setMaxWidth(USE_PREF_SIZE);
        setMaxWidth(USE_PREF_SIZE);
        getChildren().addAll(rangesHBox, internalVBox);
    }

    /**
     *
     * @param showControls the value of par
     */
    @Override
    void showDescriptionLoDControls(final boolean showControls) {
        super.showDescriptionLoDControls(showControls);
        show(getSpacer(), showControls);
    }

    @Override
    public void setSpanWidths(List<Double> spanWidths) {
        for (int i = 0; i < spanWidths.size(); i++) {
            Region spanRegion = (Region) rangesHBox.getChildren().get(i);
            Double w = spanWidths.get(i);
            spanRegion.setPrefWidth(w);
            spanRegion.setMaxWidth(w);
            spanRegion.setMinWidth(Math.max(2, w));
        }
    }

    EventStripe getStripe() {
        return getEventBundle();
    }

    @Override
    HBox getSpanFillNode() {
        return rangesHBox;
    }

    @Override
    Collection<EventStripe> makeBundlesFromClusters(List<EventCluster> eventClusters) {
        return eventClusters.stream().collect(
                Collectors.toMap(
                        EventCluster::getDescription, //key
                        EventStripe::new, //value
                        EventStripe::merge)//merge method
        ).values();
    }

    /**
     *
     * @param showSpans the value of showSpans
     */
    @Override
    void showSpans(final boolean showSpans) {
        rangesHBox.setVisible(showSpans);
    }

    @Override
    void installTooltip() {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    EventStripeNode getNodeForBundle(EventStripe cluster) {
        return new EventStripeNode(cluster, this, getChart());
    }
}
