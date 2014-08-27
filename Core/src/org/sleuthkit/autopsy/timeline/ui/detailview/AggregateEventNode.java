/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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

import org.sleuthkit.autopsy.timeline.events.AggregateEvent;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.coreutils.ColorUtilities;

/** Represents an {@link AggregateEvent} in a {@link EventDetailChart}. */
public class AggregateEventNode extends StackPane {

    private static final CornerRadii CORNER_RADII = new CornerRadii(3);

    /** the border to apply when this node is 'selected' */
    private static final Border selectionBorder = new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CORNER_RADII, new BorderWidths(2)));

    /** The event this AggregateEventNode represents visually */
    private final AggregateEvent event;

    private final AggregateEventNode parentEventNode;

    /** the region that represents the time span of this node's event */
    private final Region spanRegion = new Region();

    /** The label used to display this node's event's description */
    private final Label descrLabel = new Label();

    /** The IamgeView used to show the icon for this node's event's type */
    private final ImageView eventTypeImageView = new ImageView();

    /** Pane that contains AggregateEventNodes of any 'subevents' if they are
     * displayed
     *
     * //TODO: move more of the control of subnodes/events here and out of
     * EventDetail Chart */
    private final Pane subNodePane = new Pane();

    /** the context menu that with the slider that controls subnode/event
     * display
     *
     * //TODO: move more of the control of subnodes/events here and out
     * of EventDetail Chart */
    private final SimpleObjectProperty<ContextMenu> contextMenu = new SimpleObjectProperty<>();

    /** the Background used to fill the spanRegion, this varies epending on the
     * selected/highlighted state of this node in its parent EventDetailChart */
    private Background spanFill;

    public AggregateEventNode(final AggregateEvent event, AggregateEventNode parentEventNode) {
        this.event = event;
        this.parentEventNode = parentEventNode;
        //set initial properties
        getChildren().addAll(spanRegion, subNodePane, descrLabel);

        setAlignment(Pos.TOP_LEFT);
        setMinHeight(24);
        minWidthProperty().bind(spanRegion.widthProperty());
        setPrefHeight(USE_COMPUTED_SIZE);
        setMaxHeight(USE_PREF_SIZE);
        setMargin(descrLabel, new Insets(2, 5, 2, 5));

        //set up subnode pane sizing contraints
        subNodePane.setPrefHeight(USE_COMPUTED_SIZE);
        subNodePane.setMinHeight(USE_PREF_SIZE);
        subNodePane.setMinWidth(USE_PREF_SIZE);
        subNodePane.setMaxHeight(USE_PREF_SIZE);
        subNodePane.setMaxWidth(USE_PREF_SIZE);
        subNodePane.setPickOnBounds(false);

        //setup description label
        eventTypeImageView.setImage(event.getType().getFXImage());
        descrLabel.setGraphic(eventTypeImageView);
        descrLabel.setPrefWidth(USE_COMPUTED_SIZE);
        descrLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);

        descrLabel.setMouseTransparent(true);
        setDescriptionVisibility(DescriptionVisibility.SHOWN);

        //setup backgrounds
        final Color evtColor = event.getType().getColor();
        spanFill = new Background(new BackgroundFill(evtColor.deriveColor(0, 1, 1, .1), CORNER_RADII, Insets.EMPTY));
        setBackground(new Background(new BackgroundFill(evtColor.deriveColor(0, 1, 1, .1), CORNER_RADII, Insets.EMPTY)));
        setCursor(Cursor.HAND);
        spanRegion.setStyle("-fx-border-width:2 0 2 2; -fx-border-radius: 2; -fx-border-color: " + ColorUtilities.getRGBCode(evtColor) + ";");
        spanRegion.setBackground(spanFill);

        //set up mouse hover effect and tooltip
        setOnMouseEntered((MouseEvent e) -> {
            //defer tooltip creation till needed, this had a surprisingly large impact on speed of loading the chart
            installTooltip();
            spanRegion.setEffect(new DropShadow(10, evtColor));
        });

        setOnMouseExited((MouseEvent e) -> {
            spanRegion.setEffect(null);
        });

    }

    private void installTooltip() {
        Tooltip.install(AggregateEventNode.this, new Tooltip(getEvent().getEventIDs().size() + " " + getEvent().getType() + " events\n"
                        + getEvent().getDescription()
                        + "\nbetween " + getEvent().getSpan().getStart().toString(TimeLineController.getZonedFormatter())
                        + "\nand      " + getEvent().getSpan().getEnd().toString(TimeLineController.getZonedFormatter())
                        + "\nright-click to adjust local description zoom."));
    }

    public Pane getSubNodePane() {
        return subNodePane;
    }

    public AggregateEvent getEvent() {
        return event;
    }

    /**
     * sets the width of the {@link Region} with border and background used to
     * indicate the temporal span of this aggregate event
     *
     * @param w
     */
    public void setSpanWidth(double w) {
        spanRegion.setPrefWidth(w);
        spanRegion.setMaxWidth(w);
        spanRegion.setMinWidth(Math.max(2, w));
    }

    /**
     *
     * @param w the maximum width the description label should have
     */
    public void setDescriptionLabelMaxWidth(double w) {
        descrLabel.setMaxWidth(w);
    }

    /** @param descrVis the level of description that should be displayed */
    final public void setDescriptionVisibility(DescriptionVisibility descrVis) {
        switch (descrVis) {
            case SHOWN:
                descrLabel.setText(event.getDescription() + " (" + event.getEventIDs().size() + ")");
                break;
            case COUNT_ONLY:
                descrLabel.setText("(" + event.getEventIDs().size() + ")");
                break;
            case HIDDEN:
                descrLabel.setText("");
                break;
        }
    }

    /** apply the 'effect' to visually indicate selection
     *
     * @param applied true to apply the selection 'effect', false to remove it
     */
    void applySelectionEffect(final boolean applied) {
        Platform.runLater(() -> {
            if (applied) {
                setBorder(selectionBorder);
            } else {
                setBorder(null);
            }
        });
    }

    /** apply the 'effect' to visually indicate highlighted nodes
     *
     * @param applied true to apply the highlight 'effect', false to remove it
     */
    void applyHighlightEffect(boolean applied) {

        if (applied) {
            descrLabel.setStyle("-fx-font-weight: bold;");
            spanFill = new Background(new BackgroundFill(getEvent().getType().getColor().deriveColor(0, 1, 1, .5), CORNER_RADII, Insets.EMPTY));
            spanRegion.setBackground(spanFill);
            setBackground(new Background(new BackgroundFill(getEvent().getType().getColor().deriveColor(0, 1, 1, .3), CORNER_RADII, Insets.EMPTY)));
        } else {
            descrLabel.setStyle("-fx-font-weight: normal;");
            spanFill = new Background(new BackgroundFill(getEvent().getType().getColor().deriveColor(0, 1, 1, .1), CORNER_RADII, Insets.EMPTY));
            spanRegion.setBackground(spanFill);
            setBackground(new Background(new BackgroundFill(getEvent().getType().getColor().deriveColor(0, 1, 1, .1), CORNER_RADII, Insets.EMPTY)));
        }
    }

    /** set the span background and description label visible or not
     * (the span background is not visible when this node is displaying
     * subnodes)
     * //TODO: move more of the control of subnodes/events here and out
     * of EventDetail Chart
     *
     * @param applied true to set the span fill visible, false to hide it
     */
    void setEventDetailsVisible(boolean b) {
        if (b) {
            spanRegion.setBackground(spanFill);
        } else {
            spanRegion.setBackground(null);
        }
        descrLabel.setVisible(b);
    }

    String getDisplayedDescription() {
        return descrLabel.getText();
    }

    double getLayoutXCompensation() {
        if (parentEventNode != null) {
            return parentEventNode.getLayoutXCompensation() + getBoundsInParent().getMinX();
        } else {
            return getBoundsInParent().getMinX();
        }
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

}
