/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.detailview;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import static javafx.scene.layout.Region.USE_COMPUTED_SIZE;
import static javafx.scene.layout.Region.USE_PREF_SIZE;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.sleuthkit.autopsy.timeline.datamodel.EventBundle;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import static org.sleuthkit.autopsy.timeline.ui.detailview.EventStripeNode.configureLoDButton;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 *
 */
public abstract class EventBundleNodeBase<BundleType extends EventBundle<?>, ParentBundleType extends EventBundle<?>, ParentNodeType extends EventBundleNodeBase<ParentBundleType, BundleType, ?>> extends VBox {

    static final CornerRadii CORNER_RADII_3 = new CornerRadii(3);
    static final CornerRadii CORNER_RADII_1 = new CornerRadii(1);
    protected final EventDetailChart chart;
    final SimpleObjectProperty<DescriptionLoD> descLOD = new SimpleObjectProperty<>();
    protected final BundleType eventBundle;

    protected final ParentNodeType parentNode;

    final SleuthkitCase sleuthkitCase;
    final FilteredEventsModel eventsModel;

    final Background highlightedBackground;
    final Background defaultBackground;
    final Color evtColor;
//    final Button plusButton = ActionUtils.createButton(new EventStripeNode.ExpandClusterAction(), ActionUtils.ActionTextBehavior.HIDE);
//    final Button minusButton = ActionUtils.createButton(new EventStripeNode.CollapseClusterAction(), ActionUtils.ActionTextBehavior.HIDE);
    final Button hideButton;
    final List<ParentNodeType> subNodes = new ArrayList<>();
    final Pane subNodePane = new Pane();

    public EventBundleNodeBase(EventDetailChart chart, BundleType eventBundle, ParentNodeType parentNode) {
        this.eventBundle = eventBundle;
        this.parentNode = parentNode;
        this.chart = chart;

        this.descLOD.set(eventBundle.getDescriptionLoD());
        sleuthkitCase = chart.getController().getAutopsyCase().getSleuthkitCase();
        eventsModel = chart.getController().getEventsModel();
        evtColor = getEventType().getColor();
        defaultBackground = new Background(new BackgroundFill(evtColor.deriveColor(0, 1, 1, .1), CORNER_RADII_3, Insets.EMPTY));
        highlightedBackground = new Background(new BackgroundFill(evtColor.deriveColor(0, 1.1, 1.1, .3), CORNER_RADII_3, Insets.EMPTY));

        setBackground(defaultBackground);
        EventDetailChart.HideDescriptionAction hideClusterAction = chart.new HideDescriptionAction(getDescription(), eventBundle.getDescriptionLoD());
        hideButton = ActionUtils.createButton(hideClusterAction, ActionUtils.ActionTextBehavior.HIDE);
        configureLoDButton(hideButton);
//        configureLoDButton(plusButton);
//        configureLoDButton(minusButton);

        setAlignment(Pos.TOP_LEFT);
        setMinHeight(24);
        setPrefHeight(USE_COMPUTED_SIZE);
        setMaxHeight(USE_PREF_SIZE);
        setMaxWidth(USE_PREF_SIZE);
        setLayoutX(chart.getXAxis().getDisplayPosition(new DateTime(eventBundle.getStartMillis())) - getLayoutXCompensation());
    }

    final DescriptionLoD getDescriptionLoD() {
        return descLOD.get();
    }

    final public BundleType getEventBundle() {
        return eventBundle;
    }

    final double getLayoutXCompensation() {
        return (parentNode != null ? parentNode.getLayoutXCompensation() : 0)
                + getBoundsInParent().getMinX();
    }

    final EventType getEventType() {
        return getEventBundle().getEventType();
    }

    final String getDescription() {
        return getEventBundle().getDescription();
    }

    final long getStartMillis() {
        return getEventBundle().getStartMillis();
    }

    final Set<Long> getEventsIDs() {
        return getEventBundle().getEventIDs();
    }

    abstract void installTooltip();

    abstract void applySelectionEffect(boolean selected);

    /**
     * apply the 'effect' to visually indicate highlighted nodes
     *
     * @param applied true to apply the highlight 'effect', false to remove it
     */
    abstract void applyHighlightEffect(boolean applied);

    @SuppressWarnings("unchecked")
    public List<ParentNodeType> getSubNodes() {
        return subNodes;
    }

    abstract void setDescriptionVisibility(DescriptionVisibility get);
}
