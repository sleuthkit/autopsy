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
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.SelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javax.swing.SortOrder;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.CategoryManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.DrawableGroup;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupViewState;
import org.sleuthkit.autopsy.imagegallery.gui.SortChooser;

/**
 * Base class for Tabs in the left hand Navigation/Context area.
 */
abstract class NavPanel<X> extends Tab {

    @FXML
    private BorderPane borderPane;

    @FXML
    private ToolBar toolBar;

    private final ImageGalleryController controller;
    private final GroupManager groupManager;
    private final CategoryManager categoryManager;
    private SortChooser<DrawableGroup, GroupComparators<?>> sortChooser;

    NavPanel(ImageGalleryController controller) {
        this.controller = controller;
        this.groupManager = controller.getGroupManager();
        this.categoryManager = controller.getCategoryManager();
    }

    public ReadOnlyObjectProperty<GroupComparators<?>> comparatorProperty() {
        return sortChooser.comparatorProperty();
    }

    @FXML
    @NbBundle.Messages({"NavPanel.ascRadio.text=Ascending",
        "NavPanel.descRadio.text=Descending",
        "NavPanel.sortByBoxLabel.text=Sort By:"})
    void initialize() {
        assert borderPane != null : "fx:id=\"borderPane\" was not injected: check your FXML file 'NavPanel.fxml'.";
        assert toolBar != null : "fx:id=\"toolBar\" was not injected: check your FXML file 'NavPanel.fxml'.";

        sortChooser = new SortChooser<>(GroupComparators.getValues());
        sortChooser.setComparator(getDefaultComparator());
        sortChooser.sortOrderProperty().addListener(order -> sortGroups());
        sortChooser.comparatorProperty().addListener((observable, oldComparator, newComparator) -> {
            sortGroups();
            //only need to listen to changes in category if we are sorting by/ showing the uncategorized count
            if (newComparator == GroupComparators.UNCATEGORIZED_COUNT) {
                categoryManager.registerListener(NavPanel.this);
            } else {
                categoryManager.unregisterListener(NavPanel.this);
            }

            final SortChooser.ValueType valueType = newComparator == GroupComparators.ALPHABETICAL ? SortChooser.ValueType.LEXICOGRAPHIC : SortChooser.ValueType.NUMERIC;
            sortChooser.setValueType(valueType);
        });
        toolBar.getItems().add(sortChooser);

        //keep selection in sync with controller
        controller.viewState().addListener(observable -> {
            Optional.ofNullable(controller.viewState().get())
                    .map(GroupViewState::getGroup)
                    .ifPresent(this::setFocusedGroup);
        });

        getSelectionModel().selectedItemProperty().addListener(o -> updateControllersGroup());
    }

    /**
     * @return the default comparator used by this "view" to sort groups
     */
    abstract GroupComparators<?> getDefaultComparator();

    @Subscribe
    public void handleCategoryChange(CategoryManager.CategoryChangeEvent event) {
        sortGroups();
    }

    /**
     * @return the a comparator that will enforce the currently selected sorting
     *         options.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    Comparator<DrawableGroup> getComparator() {
        Comparator<DrawableGroup> comparator = sortChooser.getComparator();
        return (sortChooser.getSortOrder() == SortOrder.ASCENDING)
                ? comparator
                : comparator.reversed();
    }

    /**
     * notify controller about group selection in this view
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void updateControllersGroup() {
        Optional.ofNullable(getSelectionModel().getSelectedItem())
                .map(getDataItemMapper())
                .ifPresent(group -> controller.advance(GroupViewState.tile(group), false));
    }

    /**
     * Sort the groups in this view according to the currently selected sorting
     * options. Attempts to maintain selection.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void sortGroups() {
        X selectedItem = getSelectionModel().getSelectedItem();
        applyGroupComparator();
        Optional.ofNullable(selectedItem)
                .map(getDataItemMapper())
                .ifPresent(this::setFocusedGroup);
    }

    /**
     * @return a function that maps the "native" data type of this view to a
     *         DrawableGroup
     */
    abstract Function<X, DrawableGroup> getDataItemMapper();

    /**
     * Apply the currently selected sorting options.
     */
    abstract void applyGroupComparator();

    /**
     *
     * @return get the selection model
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    abstract SelectionModel<X> getSelectionModel();

    /**
     * attempt to set the given group as the selected/focused group in this
     * view.
     *
     * @param grouping the grouping to attempt to select
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    abstract void setFocusedGroup(DrawableGroup grouping);

    ////boring getters 
    BorderPane getBorderPane() {
        return borderPane;
    }

    ToolBar getToolBar() {
        return toolBar;
    }

    ImageGalleryController getController() {
        return controller;
    }

    GroupManager getGroupManager() {
        return groupManager;
    }

    CategoryManager getCategoryManager() {
        return categoryManager;
    }

}
