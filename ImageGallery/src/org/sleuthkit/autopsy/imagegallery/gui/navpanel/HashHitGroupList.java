/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.gui.navpanel;

import com.google.common.eventbus.Subscribe;
import java.util.Comparator;
import java.util.Optional;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.imagegallery.FXMLConstructor;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.CategoryManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.DrawableGroup;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupViewState;

/**
 *
 */
public class HashHitGroupList extends BorderPane {

    @FXML
    private ComboBox<GroupComparators<?>> sortByBox;
    @FXML
    private ToggleGroup orderGroup;
    @FXML
    private RadioButton ascRadio;
    @FXML
    private RadioButton descRadio;

    ListView<DrawableGroup> groupList = new ListView<>();

    private final ImageGalleryController controller;
    private SortedList<DrawableGroup> sorted;

    @Subscribe
    public void handleCategoryChange(CategoryManager.CategoryChangeEvent event) {
        resortGroupList();
    }

    public HashHitGroupList(ImageGalleryController controller) {
        this.controller = controller;
        FXMLConstructor.construct(this, "NavPanel.fxml");
    }

    private void updateControllersGroup() {
        Optional.ofNullable(groupList.getSelectionModel().getSelectedItem())
                .ifPresent(group -> controller.advance(GroupViewState.tile(group), false));
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void resortGroupList() {
//        DrawableGroup selectedItem = groupList.getSelectionModel().getSelectedItem();
//        hashTreeRoot.resortChildren(comparator);
        sorted.setComparator(getComparator());
//        groupList.getSelectionModel().select(selectedItem);
    }

    private Comparator<DrawableGroup> getComparator() {
        Comparator<DrawableGroup> treeNodeComparator = sortByBox.getSelectionModel().getSelectedItem();
        return (orderGroup.getSelectedToggle() == ascRadio)
                ? treeNodeComparator
                : treeNodeComparator.reversed();
    }

    @FXML
    void initialize() {
        assert sortByBox != null : "fx:id=\"sortByBox\" was not injected: check your FXML file 'HashHitGroupList.fxml'.";
        assert ascRadio != null : "fx:id=\"ascRadio\" was not injected: check your FXML file 'HashHitGroupList.fxml'.";
        assert orderGroup != null : "fx:id=\"orderGroup\" was not injected: check your FXML file 'HashHitGroupList.fxml'.";
        assert descRadio != null : "fx:id=\"descRadio\" was not injected: check your FXML file 'HashHitGroupList.fxml'.";
        assert groupList != null : "fx:id=\"groupList\" was not injected: check your FXML file 'HashHitGroupList.fxml'.";
        setCenter(groupList);
        sorted = controller.getGroupManager().getAnalyzedGroups().filtered((DrawableGroup t) -> t.getHashSetHitsCount() > 0).sorted();

        sortByBox.getItems().setAll(GroupComparators.getValues());
        sortByBox.getSelectionModel().select(GroupComparators.ALPHABETICAL);

        sortByBox.getSelectionModel().selectedItemProperty().addListener(observable -> {
            if (sortByBox.getSelectionModel().getSelectedItem() == GroupComparators.UNCATEGORIZED_COUNT) {
                controller.getCategoryManager().registerListener(HashHitGroupList.this);
            } else {
                controller.getCategoryManager().unregisterListener(HashHitGroupList.this);
            }

            sorted.setComparator(getComparator());
        });
        orderGroup.selectedToggleProperty().addListener(observable -> resortGroupList());

        groupList.setCellFactory(treeView -> new GroupListCell(sortByBox.getSelectionModel().selectedItemProperty()));
        groupList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        groupList.getSelectionModel().selectedItemProperty().addListener(o -> updateControllersGroup());
        groupList.setItems(sorted);
        controller.viewState().addListener(observable -> {
            Optional.ofNullable(controller.viewState().get())
                    .map(GroupViewState::getGroup)
                    .ifPresent(this::setFocusedGroup);
        });

    }

    /**
     * Set the tree to the passed in group
     *
     * @param grouping
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void setFocusedGroup(DrawableGroup grouping) {
        groupList.getSelectionModel().select(grouping);
    }
}
