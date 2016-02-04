/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-15 Basis Technology Corp.
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

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.HBox;
import javax.swing.SortOrder;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.FXMLConstructor;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.actions.CategorizeGroupAction;
import org.sleuthkit.autopsy.imagegallery.actions.TagGroupAction;
import org.sleuthkit.autopsy.imagegallery.datamodel.Category;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupSortBy;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupViewState;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Controller for the ToolBar
 */
public class Toolbar extends ToolBar {

    private static final Logger LOGGER = Logger.getLogger(Toolbar.class.getName());

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

    @FXML
    private HBox sortControlGroup;

    @FXML
    private SplitMenuButton catGroupMenuButton;

    @FXML
    private SplitMenuButton tagGroupMenuButton;

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
    private final ImageGalleryController controller;

    synchronized public SortOrder getSortOrder() {
        return orderProperty.get();
    }

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
        assert catGroupMenuButton != null : "fx:id=\"catSelectedMenubutton\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert descRadio != null : "fx:id=\"descRadio\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert groupByBox != null : "fx:id=\"groupByBox\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert onlyAnalyzedCheckBox != null : "fx:id=\"onlyAnalyzedCheckBox\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert orderGroup != null : "fx:id=\"orderGroup\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert sizeSlider != null : "fx:id=\"sizeSlider\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert sortByBox != null : "fx:id=\"sortByBox\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert sortControlGroup != null : "fx:id=\"sortControlGroup\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert tagGroupMenuButton != null : "fx:id=\"tagSelectedMenubutton\" was not injected: check your FXML file 'Toolbar.fxml'.";

        controller.viewState().addListener((observable, oldViewState, newViewState) -> {
            Platform.runLater(() -> syncGroupControlsEnabledState(newViewState));
        });
        syncGroupControlsEnabledState(controller.viewState().get());

        try {
            TagGroupAction followUpGroupAction = new TagGroupAction(controller.getTagsManager().getFollowUpTagName(), controller);
            tagGroupMenuButton.setOnAction(followUpGroupAction);
            tagGroupMenuButton.setText(followUpGroupAction.getText());
            tagGroupMenuButton.setGraphic(followUpGroupAction.getGraphic());
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Could create follow up tag menu item", ex); //NON-NLS
        }
        tagGroupMenuButton.showingProperty().addListener(showing -> {
            if (tagGroupMenuButton.isShowing()) {
                List<MenuItem> selTagMenues = Lists.transform(controller.getTagsManager().getNonCategoryTagNames(),
                        tn -> GuiUtils.createAutoAssigningMenuItem(tagGroupMenuButton, new TagGroupAction(tn, controller)));
                tagGroupMenuButton.getItems().setAll(selTagMenues);
            }
        });

        CategorizeGroupAction cat5GroupAction = new CategorizeGroupAction(Category.FIVE, controller);
        catGroupMenuButton.setOnAction(cat5GroupAction);
        catGroupMenuButton.setText(cat5GroupAction.getText());
        catGroupMenuButton.setGraphic(cat5GroupAction.getGraphic());
        catGroupMenuButton.showingProperty().addListener(showing -> {
            if (catGroupMenuButton.isShowing()) {
                List<MenuItem> categoryMenues = Lists.transform(Arrays.asList(Category.values()),
                        cat -> GuiUtils.createAutoAssigningMenuItem(catGroupMenuButton, new CategorizeGroupAction(cat, controller)));
                catGroupMenuButton.getItems().setAll(categoryMenues);
            }
        });

        groupByBox.setItems(FXCollections.observableList(DrawableAttribute.getGroupableAttrs()));
        groupByBox.getSelectionModel().select(DrawableAttribute.PATH);
        groupByBox.getSelectionModel().selectedItemProperty().addListener(queryInvalidationListener);
        groupByBox.disableProperty().bind(ImageGalleryController.getDefault().regroupDisabled());
        groupByBox.setCellFactory(listView -> new AttributeListCell());
        groupByBox.setButtonCell(new AttributeListCell());

        sortByBox.setCellFactory(listView -> new SortByListCell());
        sortByBox.setButtonCell(new SortByListCell());
        sortByBox.setItems(GroupSortBy.getValues());

        sortByBox.getSelectionModel().selectedItemProperty().addListener(queryInvalidationListener);

        sortByBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            final boolean orderEnabled = newValue == GroupSortBy.NONE || newValue == GroupSortBy.PRIORITY;
            ascRadio.setDisable(orderEnabled);
            descRadio.setDisable(orderEnabled);

        });
        sortByBox.getSelectionModel().select(GroupSortBy.PRIORITY);

        orderGroup.selectedToggleProperty().addListener(queryInvalidationListener);
    }

    private void syncGroupControlsEnabledState(GroupViewState newViewState) {
        boolean noGroupSelected = newViewState == null
                ? true
                : newViewState.getGroup() == null;

        tagGroupMenuButton.setDisable(noGroupSelected);
        catGroupMenuButton.setDisable(noGroupSelected);
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
        FXMLConstructor.construct(this, "Toolbar.fxml"); //NON-NLS
    }

}
