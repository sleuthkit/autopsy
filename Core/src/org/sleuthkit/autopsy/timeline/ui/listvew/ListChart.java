/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.listvew;

import java.util.Arrays;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ui.IntervalSelector;
import org.sleuthkit.autopsy.timeline.ui.TimeLineChart;
import org.sleuthkit.datamodel.TskData;

/**
 *
 */
class ListChart extends TableView<Long> implements TimeLineChart<Long> {

    private final TimeLineController controller;
    private final TableColumn<Long, Long> idColumn = new TableColumn<>();
    private final TableColumn<Long, Long> millisColumn = new TableColumn<>();
    private final TableColumn<Long, Image> iconColumn = new TableColumn<>();
    private final TableColumn<Long, String> descriptionColumn = new TableColumn<>();
    private final TableColumn<Long, EventType> baseTypeColumn = new TableColumn<>();
    private final TableColumn<Long, EventType> subTypeColumn = new TableColumn<>();
    private final TableColumn<Long, TskData.FileKnown> knownColumn = new TableColumn<>();

    ListChart(TimeLineController controller) {
        this.controller = controller;
        getColumns().addAll(Arrays.asList(idColumn, iconColumn, millisColumn));

        idColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));

        millisColumn.setCellValueFactory(param -> {
            return new SimpleObjectProperty<>(controller.getEventsModel().getEventById(param.getValue()).getStartMillis());
        });
        millisColumn.setCellFactory(col -> new EpochMillisCell());
        iconColumn.setCellValueFactory(param -> {
            return new SimpleObjectProperty<>(controller.getEventsModel().getEventById(param.getValue()).getEventType().getFXImage());
        });
        iconColumn.setCellFactory(col -> new ImageCell());

        millisColumn.setSortType(TableColumn.SortType.DESCENDING);
        millisColumn.setSortable(true);
        millisColumn.setComparator(Long::compare);
        getSortOrder().setAll(Arrays.asList(millisColumn));

    }

    @Override
    public ObservableList<? extends Node> getSelectedNodes() {
        return FXCollections.observableArrayList();
    }

    @Override
    public IntervalSelector<? extends Long> getIntervalSelector() {
        return null;
    }

    @Override
    public void setIntervalSelector(IntervalSelector<? extends Long> newIntervalSelector) {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public IntervalSelector<Long> newIntervalSelector() {
        return null;
    }

    @Override
    public void clearIntervalSelector() {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Axis<Long> getXAxis() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public TimeLineController getController() {
        return controller;
    }

    @Override
    public void clearContextMenu() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ContextMenu getContextMenu(MouseEvent m) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private static class ImageCell extends TableCell<Long, Image> {

        @Override
        protected void updateItem(Image item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                setGraphic(new ImageView(item));
            }
        }
    }

    private class EpochMillisCell extends TableCell<Long, Long> {

        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText("");
            } else {
                setText(TimeLineController.getZonedFormatter().print(item));
            }
        }
    }

}
