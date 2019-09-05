/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016-18 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.gui;

import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javax.swing.SortOrder;
import org.sleuthkit.autopsy.imagegallery.FXMLConstructor;

/**
 *
 */
public class SortChooser<X, Y extends Comparator<X>> extends HBox {

    @FXML
    private RadioButton ascRadio;
    @FXML
    private RadioButton descRadio;
    @FXML
    private ToggleGroup orderGroup;
    @FXML
    private ComboBox<Y> sortByBox;

    private final ObservableList<Y> comparators;

    private final ReadOnlyObjectWrapper<SortOrder> sortOrder = new ReadOnlyObjectWrapper<>(SortOrder.ASCENDING);
    private final SimpleBooleanProperty sortOrderDisabled = new SimpleBooleanProperty(false);
    private final SimpleObjectProperty<ValueType> valueType = new SimpleObjectProperty<>(ValueType.LEXICOGRAPHIC);

    public SortChooser(ObservableList<Y> comps) {
        this.comparators = comps;
        FXMLConstructor.construct(this, "SortChooser.fxml");
    }

    @FXML
    void initialize() {
        assert ascRadio != null : "fx:id=\"ascRadio\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert descRadio != null : "fx:id=\"descRadio\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert orderGroup != null : "fx:id=\"orderGroup\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert sortByBox != null : "fx:id=\"sortByBox\" was not injected: check your FXML file 'Toolbar.fxml'.";

        ascRadio.getStyleClass().remove("radio-button");
        ascRadio.getStyleClass().add("toggle-button");
        descRadio.getStyleClass().remove("radio-button");
        descRadio.getStyleClass().add("toggle-button");

        valueType.addListener(observable -> setValueTypeIcon(valueType.getValue()));
        setValueTypeIcon(valueType.getValue());

        ascRadio.disableProperty().bind(sortOrderDisabled);
        descRadio.disableProperty().bind(sortOrderDisabled);
        ascRadio.selectedProperty().addListener(selectedToggle -> {
            sortOrder.set(ascRadio.isSelected() ? SortOrder.ASCENDING : SortOrder.DESCENDING);
        });

        sortByBox.setItems(comparators);
        sortByBox.setCellFactory(listView -> new ComparatorCell());
        sortByBox.setButtonCell(new ComparatorCell());
    }

    private void setValueTypeIcon(ValueType newValue) {
        ascRadio.setGraphic(new ImageView(newValue.getAscendingImage()));
        descRadio.setGraphic(new ImageView(newValue.getDescendingImage()));
    }

    public ValueType getValueType() {
        return valueType.get();
    }

    public void setValueType(ValueType type) {
        valueType.set(type);
    }

    public SimpleObjectProperty<ValueType> valueTypeProperty() {
        return valueType;
    }

    public void setSortOrderDisabled(boolean disabled) {
        sortOrderDisabled.set(disabled);
    }

    public boolean isSortOrderDisabled() {
        return sortOrderDisabled.get();
    }

    public SimpleBooleanProperty sortOrderDisabledProperty() {
        return sortOrderDisabled;
    }

    public SortOrder getSortOrder() {
        return sortOrder.get();
    }

    public ReadOnlyObjectProperty<SortOrder> sortOrderProperty() {
        return sortOrder.getReadOnlyProperty();
    }

    public Y getComparator() {
        return sortByBox.getSelectionModel().getSelectedItem();
    }

    public void setComparator(Y selected) {
        sortByBox.getSelectionModel().select(selected);
    }

    public ReadOnlyObjectProperty<Y> comparatorProperty() {
        return sortByBox.getSelectionModel().selectedItemProperty();
    }

    public enum ValueType {

        LEXICOGRAPHIC("sort_asc_az.png", "sort_desc_az.png"),
        NUMERIC("sort_ascending.png", "sort_descending.png");

        private final Image ascImage;
        private final Image descImage;

        private ValueType(String ascImageName, String descImageName) {
            this.ascImage = new Image("/org/sleuthkit/autopsy/imagegallery/images/" + ascImageName);
            this.descImage = new Image("/org/sleuthkit/autopsy/imagegallery/images/" + descImageName);
        }

        private Image getAscendingImage() {
            return ascImage;
        }

        private Image getDescendingImage() {
            return descImage;
        }
    }

    private class ComparatorCell extends ListCell<Y> {

        @Override
        protected void updateItem(Y item, boolean empty) {
            super.updateItem(item, empty); //To change body of generated methods, choose Tools | Templates.

            if (empty || null == item) {
                setText(null);
                setGraphic(null);
            } else {
                try {
                    String displayName = (String) item.getClass().getMethod("getDisplayName").invoke(item);
                    setText(displayName);
                    Image icon = (Image) item.getClass().getMethod("getIcon").invoke(item);
                    setGraphic(new ImageView(icon));
                } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    setText(item.toString());
                    setGraphic(null);
                }
            }
        }
    }
}
