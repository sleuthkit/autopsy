/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.detailview;

import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.geometry.Orientation;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 *
 */
class ScrollingLaneWrapper extends BorderPane {

    private static final double LINE_SCROLL_PERCENTAGE = .10;
    private static final double PAGE_SCROLL_PERCENTAGE = .70;
    private final ScrollBar vertScrollBar = new ScrollBar();
    private final Region scrollBarSpacer = new Region();
    private final DetailsChartLane<?> chart;

    ScrollingLaneWrapper(DetailsChartLane<?> center) {
        super(center);
        this.chart = center;

        scrollBarSpacer.minHeightProperty().bind(chart.getXAxis().heightProperty());

        //configure scrollbar
        vertScrollBar.setOrientation(Orientation.VERTICAL);
        vertScrollBar.maxProperty().bind(chart.maxVScrollProperty().subtract(chart.heightProperty()));
        vertScrollBar.visibleAmountProperty().bind(chart.heightProperty());
        vertScrollBar.visibleProperty().bind(vertScrollBar.visibleAmountProperty().greaterThanOrEqualTo(0));
        VBox.setVgrow(vertScrollBar, Priority.ALWAYS);
        setRight(new VBox(vertScrollBar, scrollBarSpacer));

        //scrollbar value change handler.  This forwards changes in scroll bar to chart
        this.vertScrollBar.valueProperty().addListener((Observable observable) -> {
            chart.setVScroll(vertScrollBar.getValue());
        });
        //request focus for keyboard scrolling
        setOnMouseClicked(mouseEvent -> requestFocus());

        //interpret scroll events to the scrollBar
        this.setOnScroll(scrollEvent ->
                vertScrollBar.valueProperty().set(clampScroll(vertScrollBar.getValue() - scrollEvent.getDeltaY())));

        //interpret scroll related keys to scrollBar
        this.setOnKeyPressed((KeyEvent t) -> {
            switch (t.getCode()) {
                case PAGE_UP:
                    incrementScrollValue(-PAGE_SCROLL_PERCENTAGE);
                    t.consume();
                    break;
                case PAGE_DOWN:
                    incrementScrollValue(PAGE_SCROLL_PERCENTAGE);
                    t.consume();
                    break;
                case KP_UP:
                case UP:
                    incrementScrollValue(-LINE_SCROLL_PERCENTAGE);
                    t.consume();
                    break;
                case KP_DOWN:
                case DOWN:
                    incrementScrollValue(LINE_SCROLL_PERCENTAGE);
                    t.consume();
                    break;
            }
        });
    }

    void reset() {
        Platform.runLater(() -> {
            vertScrollBar.setValue(0);
        });
    }

    private void incrementScrollValue(double factor) {
        vertScrollBar.valueProperty().set(clampScroll(vertScrollBar.getValue() + factor * chart.getHeight()));
    }

    private Double clampScroll(Double value) {
        return Math.max(0, Math.min(vertScrollBar.getMax() + 50, value));
    }
}
