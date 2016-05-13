/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.listvew;

import java.util.Arrays;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.util.Callback;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.SingleEvent;
import org.sleuthkit.autopsy.timeline.ui.IntervalSelector;
import org.sleuthkit.autopsy.timeline.ui.TimeLineChart;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;

/**
 *
 */
class ListChart extends TableView<Long> implements TimeLineChart<Long> {

    Callback<TableColumn.CellDataFeatures<Long, Long>, ObservableValue<Long>> cellValueFactory = param -> new SimpleObjectProperty<>(param.getValue());

    private final TimeLineController controller;
    private final TableColumn<Long, Long> idColumn = new TableColumn<>("Event ID");
    private final TableColumn<Long, Long> millisColumn = new TableColumn<>("Date/Time");
    private final TableColumn<Long, Long> iconColumn = new TableColumn<>("Icon");
    private final TableColumn<Long, Long> descriptionColumn = new TableColumn<>("Description");
    private final TableColumn<Long, Long> baseTypeColumn = new TableColumn<>("Base Type");
    private final TableColumn<Long, Long> subTypeColumn = new TableColumn<>("Sub Type");
    private final TableColumn<Long, Long> knownColumn = new TableColumn<>("Known");

    ListChart(TimeLineController controller) {
        this.controller = controller;
        setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY);
        getColumns().addAll(Arrays.asList(idColumn, millisColumn, iconColumn, descriptionColumn, baseTypeColumn, subTypeColumn, knownColumn));

        setRowFactory(tableView -> new EventRow());

        idColumn.setCellValueFactory(cellValueFactory);
        idColumn.setSortable(false);

        millisColumn.setCellValueFactory(cellValueFactory);
        millisColumn.setCellFactory(col -> new EpochMillisCell());
        millisColumn.setSortable(false);

        iconColumn.setCellValueFactory(cellValueFactory);
        iconColumn.setCellFactory(col -> new ImageCell());
        iconColumn.setSortable(false);

        descriptionColumn.setCellValueFactory(cellValueFactory);
        descriptionColumn.setCellFactory(col -> new DescriptionCell());
        descriptionColumn.setSortable(false);

        baseTypeColumn.setCellValueFactory(cellValueFactory);
        baseTypeColumn.setCellFactory(col -> new BaseTypeCell());
        baseTypeColumn.setSortable(false);

        subTypeColumn.setCellValueFactory(cellValueFactory);
        subTypeColumn.setCellFactory(col -> new EventTypeCell());
        subTypeColumn.setSortable(false);

        knownColumn.setCellValueFactory(cellValueFactory);
        knownColumn.setCellFactory(col -> new KnownCell());
        knownColumn.setSortable(false);
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

    private static class ImageCell extends TableCell<Long, Long> {

        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                EventRow tableRow = (EventRow) getTableRow();
                if (tableRow != null) {
                    setGraphic(new ImageView(tableRow.getEvent().getEventType().getFXImage()));
                }
            }
        }
    }

    private static class DescriptionCell extends TableCell<Long, Long> {

        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText("");
            } else {
                EventRow tableRow = (EventRow) getTableRow();
                if (tableRow != null) {
                    setText(tableRow.getEvent().getDescription(DescriptionLoD.FULL));
                }
            }
        }
    }

    private static class BaseTypeCell extends TableCell<Long, Long> {

        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText("");
            } else {
                EventRow tableRow = (EventRow) getTableRow();
                if (tableRow != null) {
                    setText(tableRow.getEvent().getEventType().getBaseType().getDisplayName());
                }
            }
        }
    }

    private static class EventTypeCell extends TableCell<Long, Long> {

        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText("");
            } else {
                EventRow tableRow = (EventRow) getTableRow();
                if (tableRow != null) {
                    setText(tableRow.getEvent().getEventType().getDisplayName());
                }
            }
        }
    }

    private static class KnownCell extends TableCell<Long, Long> {

        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText("");
            } else {
                EventRow tableRow = (EventRow) getTableRow();
                if (tableRow != null) {
                    setText(tableRow.getEvent().getKnown().getName());
                }
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

                EventRow tableRow = (EventRow) getTableRow();
                if (tableRow != null) {
                    setText(TimeLineController.getZonedFormatter().print(tableRow.getEvent().getStartMillis()));
                }
            }
        }
    }

    private class EventRow extends TableRow<Long> {

        private SingleEvent event;

        SingleEvent getEvent() {
            return event;
        }

        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                event = null;
            } else {
                event = controller.getEventsModel().getEventById(item);
            }
        }
    }
}
