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
import java.util.List;
import java.util.Set;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import org.sleuthkit.autopsy.timeline.datamodel.MultiEvent;
import org.sleuthkit.autopsy.timeline.ui.AbstractVisualizationPane;
import static org.sleuthkit.autopsy.timeline.ui.detailview.EventNodeBase.show;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;

/**
 *
 */
@NbBundle.Messages({"EventBundleNodeBase.toolTip.loading=loading..."})
public abstract class MultiEventNodeBase< BundleType extends MultiEvent<ParentType>, ParentType extends MultiEvent<BundleType>, ParentNodeType extends MultiEventNodeBase<
        ParentType, BundleType, ?>> extends EventNodeBase<BundleType> {

    private static final Logger LOGGER = Logger.getLogger(MultiEventNodeBase.class.getName());

    static final CornerRadii CORNER_RADII_3 = new CornerRadii(3);
    static final CornerRadii CORNER_RADII_1 = new CornerRadii(1);



    final ObservableList<EventNodeBase<?>> subNodes = FXCollections.observableArrayList();
    final Pane subNodePane = new Pane();

    
    private Timeline timeline;

    MultiEventNodeBase(DetailsChart chart, BundleType eventBundle, ParentNodeType parentNode) {
        super(eventBundle, parentNode, chart);
        this.descLOD.set(eventBundle.getDescriptionLoD());
  

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

   

    @SuppressWarnings("unchecked")
    public List<EventNodeBase<?>> getSubNodes() {
        return subNodes;
    }

    final String getDescription() {
        return getEventBundle().getDescription();
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
