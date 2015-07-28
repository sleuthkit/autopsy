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
package org.sleuthkit.autopsy.imagegallery.gui;

import java.util.ArrayList;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javax.swing.SortOrder;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.imagegallery.FXMLConstructor;
import org.sleuthkit.autopsy.imagegallery.FileIDSelectionModel;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.actions.CategorizeAction;
import org.sleuthkit.autopsy.imagegallery.datamodel.Category;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupSortBy;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Controller for the ToolBar
 */
public class Toolbar extends ToolBar {

    private static final int SIZE_SLIDER_DEFAULT = 100;

    @FXML
    private ComboBox<DrawableAttribute<?>> groupByBox;

    @FXML
    private CheckBox onlyAnalyzedCheckBox;

    @FXML
    private Slider sizeSlider;

    @FXML
    private ComboBox<GroupSortBy> sortByBox;

    @FXML
    private RadioButton ascRadio;

    @FXML
    private RadioButton descRadio;

    @FXML
    private ToggleGroup orderGroup;

//    @FXML
//    private ToggleButton metaDataToggle;
    @FXML
    private HBox sortControlGroup;

    @FXML
    private SplitMenuButton catSelectedMenuButton;

    @FXML
    private SplitMenuButton tagSelectedMenuButton;

    private static Toolbar instance;

    private final SimpleObjectProperty<SortOrder> orderProperty = new SimpleObjectProperty<>(SortOrder.ASCENDING);

    private final InvalidationListener queryInvalidationListener = (Observable o) -> {
        if (orderGroup.getSelectedToggle() == ascRadio) {
            orderProperty.set(SortOrder.ASCENDING);
        } else {
            orderProperty.set(SortOrder.DESCENDING);
        }

        ImageGalleryController.getDefault().getGroupManager().regroup(groupByBox.getSelectionModel().getSelectedItem(), sortByBox.getSelectionModel().getSelectedItem(), getSortOrder(), false);
    };
    private ImageGalleryController controller;

    synchronized public SortOrder getSortOrder() {
        return orderProperty.get();
    }

//    public ReadOnlyBooleanProperty showMetaDataProperty() {
//        return metaDataToggle.selectedProperty();
//    }
    public DoubleProperty sizeSliderValue() {
        return sizeSlider.valueProperty();
    }

    static synchronized public Toolbar getDefault(ImageGalleryController controller) {
        if (instance == null) {
            instance = new Toolbar(controller);
        }
        return instance;
    }

    @FXML
    void initialize() {
        assert ascRadio != null : "fx:id=\"ascRadio\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert catSelectedMenuButton != null : "fx:id=\"catSelectedMenubutton\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert descRadio != null : "fx:id=\"descRadio\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert groupByBox != null : "fx:id=\"groupByBox\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert onlyAnalyzedCheckBox != null : "fx:id=\"onlyAnalyzedCheckBox\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert orderGroup != null : "fx:id=\"orderGroup\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert sizeSlider != null : "fx:id=\"sizeSlider\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert sortByBox != null : "fx:id=\"sortByBox\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert sortControlGroup != null : "fx:id=\"sortControlGroup\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert tagSelectedMenuButton != null : "fx:id=\"tagSelectedMenubutton\" was not injected: check your FXML file 'Toolbar.fxml'.";

        FileIDSelectionModel.getInstance().getSelected().addListener((Observable o) -> {
            Runnable r = () -> {
                tagSelectedMenuButton.setDisable(FileIDSelectionModel.getInstance().getSelected().isEmpty());
                catSelectedMenuButton.setDisable(FileIDSelectionModel.getInstance().getSelected().isEmpty());
            };
            if (Platform.isFxApplicationThread()) {
                r.run();
            } else {
                Platform.runLater(r);
            }
        });

        tagSelectedMenuButton.setOnAction((ActionEvent t) -> {
            try {
                GuiUtils.createSelTagMenuItem(getController().getTagsManager().getFollowUpTagName(), tagSelectedMenuButton, getController()).getOnAction().handle(t);
            } catch (TskCoreException ex) {
                Exceptions.printStackTrace(ex);
            }
        });

        tagSelectedMenuButton.setGraphic(new ImageView(DrawableAttribute.TAGS.getIcon()));
        tagSelectedMenuButton.showingProperty().addListener((ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) -> {
            if (t1) {
                ArrayList<MenuItem> selTagMenues = new ArrayList<>();
                for (final TagName tn : getController().getTagsManager().getNonCategoryTagNames()) {
                    MenuItem menuItem = GuiUtils.createSelTagMenuItem(tn, tagSelectedMenuButton, getController());
                    selTagMenues.add(menuItem);
                }
                tagSelectedMenuButton.getItems().setAll(selTagMenues);
            }
        });

        catSelectedMenuButton.setOnAction(createSelCatMenuItem(Category.FIVE, catSelectedMenuButton, getController()).getOnAction());
        catSelectedMenuButton.setText(Category.FIVE.getDisplayName());
        catSelectedMenuButton.setGraphic(new ImageView(DrawableAttribute.CATEGORY.getIcon()));
        catSelectedMenuButton.showingProperty().addListener((ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) -> {
            if (t1) {
                ArrayList<MenuItem> categoryMenues = new ArrayList<>();
                for (final Category cat : Category.values()) {
                    MenuItem menuItem = createSelCatMenuItem(cat, catSelectedMenuButton, getController());
                    categoryMenues.add(menuItem);
                }
                catSelectedMenuButton.getItems().setAll(categoryMenues);
            }
        });

        groupByBox.setItems(FXCollections.observableList(DrawableAttribute.getGroupableAttrs()));
        groupByBox.getSelectionModel().select(DrawableAttribute.PATH);
        groupByBox.getSelectionModel().selectedItemProperty().addListener(queryInvalidationListener);
        groupByBox.disableProperty().bind(ImageGalleryController.getDefault().regroupDisabled());
        groupByBox.setCellFactory((listView) -> new AttributeListCell());
        groupByBox.setButtonCell(new AttributeListCell());

        sortByBox.setCellFactory((listView) -> new SortByListCell());
        sortByBox.setButtonCell(new SortByListCell());
        sortByBox.setItems(GroupSortBy.getValues());

        sortByBox.getSelectionModel().selectedItemProperty().addListener(queryInvalidationListener);

        sortByBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            final boolean orderEnabled = newValue == GroupSortBy.NONE || newValue == GroupSortBy.PRIORITY;
            ascRadio.setDisable(orderEnabled);
            descRadio.setDisable(orderEnabled);

        });
        sortByBox.getSelectionModel().select(GroupSortBy.PRIORITY);
//        ascRadio.disableProperty().bind(sortByBox.getSelectionModel().selectedItemProperty().isEqualTo(GroupSortBy.NONE));
//        descRadio.disableProperty().bind(sortByBox.getSelectionModel().selectedItemProperty().isEqualTo(GroupSortBy.NONE));

        orderGroup.selectedToggleProperty().addListener(queryInvalidationListener);


    }

    public void reset() {
        Platform.runLater(() -> {
            groupByBox.getSelectionModel().select(DrawableAttribute.PATH);
            sortByBox.getSelectionModel().select(GroupSortBy.NONE);
            orderGroup.selectToggle(ascRadio);
            sizeSlider.setValue(SIZE_SLIDER_DEFAULT);
        });
    }

    private Toolbar(ImageGalleryController controller) {
        this.controller = controller;
        FXMLConstructor.construct(this, "Toolbar.fxml");
    }

    private static MenuItem createSelCatMenuItem(Category cat, final SplitMenuButton catSelectedMenuButton, ImageGalleryController controller) {
        final MenuItem menuItem = new MenuItem(cat.getDisplayName(), new ImageView(DrawableAttribute.CATEGORY.getIcon()));
        menuItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent t) {
                new CategorizeAction(controller).addTag(controller.getTagsManager().getTagName(cat), "");
                catSelectedMenuButton.setText(cat.getDisplayName());
                catSelectedMenuButton.setOnAction(this);
            }
        });
        return menuItem;
    }

    private ImageGalleryController getController() {
        return controller;
    }
}
