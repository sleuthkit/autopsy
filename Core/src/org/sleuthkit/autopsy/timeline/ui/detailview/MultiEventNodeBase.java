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

import java.util.List;
import java.util.Set;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import org.joda.time.DateTime;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.MultiEvent;
import org.sleuthkit.datamodel.TimelineLevelOfDetail;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Base class for nodes that represent multiple events in the Details View.
 */
@NbBundle.Messages({"EventBundleNodeBase.toolTip.loading=loading..."})
abstract class MultiEventNodeBase< BundleType extends MultiEvent<ParentType>, ParentType extends MultiEvent<BundleType>, ParentNodeType extends MultiEventNodeBase<
        ParentType, BundleType, ?>> extends EventNodeBase<BundleType> {

    static final CornerRadii CORNER_RADII_3 = new CornerRadii(3);
    static final CornerRadii CORNER_RADII_1 = new CornerRadii(1);

    final ObservableList<EventNodeBase<?>> subNodes = FXCollections.observableArrayList();
    final Pane subNodePane = new Pane();

    private final ReadOnlyObjectWrapper<TimelineLevelOfDetail> descLOD = new ReadOnlyObjectWrapper<>();

    MultiEventNodeBase(DetailsChartLane<?> chartLane, BundleType event, ParentNodeType parentNode) {
        super(event, parentNode, chartLane);
        setDescriptionLOD(event.getDescriptionLevel());

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
        heightProperty().addListener(heightProp -> chartLane.requestLayout());
        Platform.runLater(()
                -> setLayoutX(chartLane.getXAxis().getDisplayPosition(new DateTime(event.getStartMillis())) - getLayoutXCompensation())
        );

        //initialize info hbox
        infoHBox.setPadding(new Insets(2, 3, 2, 3));
        infoHBox.setAlignment(Pos.TOP_LEFT);

        Bindings.bindContent(subNodePane.getChildren(), subNodes);
    }

    public ReadOnlyObjectProperty<TimelineLevelOfDetail> descriptionLoDProperty() {
        return descLOD.getReadOnlyProperty();
    }

    final TimelineLevelOfDetail getDescriptionLevel() {
        return descLOD.get();
    }

    /**
     *
     */
    final void setDescriptionLOD(final TimelineLevelOfDetail descriptionLoD) {
        descLOD.set(descriptionLoD);
    }

    @Override
    public List<EventNodeBase<?>> getSubNodes() {
        return subNodes;
    }

    @Override
    final String getDescription() {
        return getEvent().getDescription();
    }

    @Override
    final Set<Long> getEventIDs() {
        return getEvent().getEventIDs();
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected void layoutChildren() {
        chartLane.layoutEventBundleNodes(subNodes, 0);
        super.layoutChildren();
    }

    abstract EventNodeBase<?> createChildNode(ParentType rawChild) throws TskCoreException;

    @Override
    abstract EventHandler<MouseEvent> getDoubleClickHandler();
}
