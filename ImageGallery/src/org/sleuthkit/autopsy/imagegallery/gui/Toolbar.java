/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-18 Basis Technology Corp.
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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.util.StringConverter;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import org.controlsfx.control.PopOver;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import static org.sleuthkit.autopsy.casemodule.Case.Events.DATA_SOURCE_ADDED;
import static org.sleuthkit.autopsy.casemodule.Case.Events.DATA_SOURCE_DELETED;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.imagegallery.FXMLConstructor;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.actions.CategorizeGroupAction;
import org.sleuthkit.autopsy.imagegallery.actions.TagGroupAction;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.DrawableGroup;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupSortBy;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupViewState;
import org.sleuthkit.autopsy.imagegallery.utils.TaskUtils;
import static org.sleuthkit.autopsy.imagegallery.utils.TaskUtils.addFXCallback;
import org.sleuthkit.datamodel.DataSource;

/**
 * Controller for the ToolBar
 */
public class Toolbar extends ToolBar {

    private static final Logger logger = Logger.getLogger(Toolbar.class.getName());
    private static final int SIZE_SLIDER_DEFAULT = 100;

    @FXML
    private ComboBox<Optional<DataSource>> dataSourceComboBox;
    @FXML
    private ImageView sortHelpImageView;
    @FXML
    private ComboBox<DrawableAttribute<?>> groupByBox;
    @FXML
    private Slider sizeSlider;
    @FXML
    private SplitMenuButton catGroupMenuButton;
    @FXML
    private SplitMenuButton tagGroupMenuButton;
    @FXML
    private Label groupByLabel;
    @FXML
    private Label tagImageViewLabel;
    @FXML
    private Label categoryImageViewLabel;
    @FXML
    private Label thumbnailSizeLabel;
    private SortChooser<DrawableGroup, GroupSortBy> sortChooser;

    private final ListeningExecutorService exec = TaskUtils.getExecutorForClass(Toolbar.class);

    private final ImageGalleryController controller;

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final ObservableList<Optional<DataSource>> dataSources = FXCollections.observableArrayList();
    private SingleSelectionModel<Optional<DataSource>> dataSourceSelectionModel;
    private final Map<DataSource, Boolean> dataSourcesViewable = new HashMap<>();

    private final InvalidationListener queryInvalidationListener = new InvalidationListener() {
        @Override
        public void invalidated(Observable invalidated) {
            DataSource selectedDataSource = getSelectedDataSource();

            controller.getGroupManager().regroup(selectedDataSource,
                    groupByBox.getSelectionModel().getSelectedItem(),
                    sortChooser.getComparator(),
                    sortChooser.getSortOrder(),
                    false);
        }
    };

    @FXML
    @NbBundle.Messages(
            {"Toolbar.groupByLabel=Group By:",
                "Toolbar.sortByLabel=Sort By:",
                "Toolbar.ascRadio=Ascending",
                "Toolbar.descRadio=Descending",
                "Toolbar.tagImageViewLabel=Tag Group's Files:",
                "Toolbar.categoryImageViewLabel=Categorize Group's Files:",
                "Toolbar.thumbnailSizeLabel=Thumbnail Size (px):",
                "Toolbar.sortHelp=The sort direction (ascending/descending) affects the queue of unseen groups that Image Gallery maintains, but changes to this queue aren't apparent until the \"Next Unseen Group\" button is pressed.",
                "Toolbar.sortHelpTitle=Group Sorting",
                "Toolbar.getDataSources.errMessage=Unable to get datasources for current case.",
                "Toolbar.nonPathGroupingWarning.content=Proceed with regrouping?",
                "Toolbar.nonPathGroupingWarning.header=Grouping by attributes other than path does not support the data source filter.\nFiles and groups from all data sources will be shown.",
                "Toolbar.nonPathGroupingWarning.title=Image Gallery"})
    void initialize() {
        assert groupByBox != null : "fx:id=\"groupByBox\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert dataSourceComboBox != null : "fx:id=\"dataSourceComboBox\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert sortHelpImageView != null : "fx:id=\"sortHelpImageView\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert tagImageViewLabel != null : "fx:id=\"tagImageViewLabel\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert tagGroupMenuButton != null : "fx:id=\"tagGroupMenuButton\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert categoryImageViewLabel != null : "fx:id=\"categoryImageViewLabel\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert catGroupMenuButton != null : "fx:id=\"catGroupMenuButton\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert thumbnailSizeLabel != null : "fx:id=\"thumbnailSizeLabel\" was not injected: check your FXML file 'Toolbar.fxml'.";
        assert sizeSlider != null : "fx:id=\"sizeSlider\" was not injected: check your FXML file 'Toolbar.fxml'.";
        this.dataSourceSelectionModel = dataSourceComboBox.getSelectionModel();

        //set internationalized label text
        groupByLabel.setText(Bundle.Toolbar_groupByLabel());
        tagImageViewLabel.setText(Bundle.Toolbar_tagImageViewLabel());
        categoryImageViewLabel.setText(Bundle.Toolbar_categoryImageViewLabel());
        thumbnailSizeLabel.setText(Bundle.Toolbar_thumbnailSizeLabel());
        sizeSlider.valueProperty().bindBidirectional(controller.thumbnailSizeProperty());
        controller.viewStateProperty().addListener((observable, oldViewState, newViewState)
                -> Platform.runLater(() -> syncGroupControlsEnabledState(newViewState))
        );
        syncGroupControlsEnabledState(controller.viewStateProperty().get());

        initDataSourceComboBox();
        groupByBox.setItems(FXCollections.observableList(DrawableAttribute.getGroupableAttrs()));
        groupByBox.getSelectionModel().select(DrawableAttribute.PATH);
        groupByBox.disableProperty().bind(controller.regroupDisabledProperty());
        groupByBox.setCellFactory(listView -> new AttributeListCell());
        groupByBox.setButtonCell(new AttributeListCell());
        groupByBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue == DrawableAttribute.PATH
                && newValue != DrawableAttribute.PATH
                && getSelectedDataSource() != null) {

                Alert alert = new Alert(Alert.AlertType.CONFIRMATION, Bundle.Toolbar_nonPathGroupingWarning_content());
                alert.setHeaderText(Bundle.Toolbar_nonPathGroupingWarning_header());
                alert.setTitle(Bundle.Toolbar_nonPathGroupingWarning_title());
                alert.initModality(Modality.APPLICATION_MODAL);
                alert.initOwner(getScene().getWindow());
                GuiUtils.setDialogIcons(alert);
                if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                    // Set the datasource selection to 'All', before switching group
                    controller.getGroupManager().setDataSource(null);
                    
                    queryInvalidationListener.invalidated(observable);
                } else {
                    Platform.runLater(() -> groupByBox.getSelectionModel().select(DrawableAttribute.PATH));
                }
            } else {
                queryInvalidationListener.invalidated(observable);
            }
        });

        sortChooser = new SortChooser<>(GroupSortBy.getValues());
        sortChooser.comparatorProperty().addListener((observable, oldComparator, newComparator) -> {
            final boolean orderDisabled = newComparator == GroupSortBy.NONE || newComparator == GroupSortBy.PRIORITY;
            sortChooser.setSortOrderDisabled(orderDisabled);

            final SortChooser.ValueType valueType = newComparator == GroupSortBy.GROUP_BY_VALUE ? SortChooser.ValueType.LEXICOGRAPHIC : SortChooser.ValueType.NUMERIC;
            sortChooser.setValueType(valueType);
            queryInvalidationListener.invalidated(observable);
        });
        sortChooser.setComparator(controller.getGroupManager().getSortBy());
        sortChooser.sortOrderProperty().addListener(queryInvalidationListener);
        getItems().add(2, sortChooser);

        sortHelpImageView.setCursor(Cursor.HAND);
        sortHelpImageView.setOnMouseClicked(clicked -> {
            Text text = new Text(Bundle.Toolbar_sortHelp());
            text.setWrappingWidth(480);  //This is a hack to fix the layout.
            showPopoverHelp(sortHelpImageView,
                    Bundle.Toolbar_sortHelpTitle(),
                    sortHelpImageView.getImage(), text);
        });
        initTagMenuButton();

        CategorizeGroupAction cat5GroupAction = new CategorizeGroupAction(controller.getCategoryManager().getCategories().get(0), controller);
        catGroupMenuButton.setOnAction(cat5GroupAction);
        catGroupMenuButton.setText(cat5GroupAction.getText());
        catGroupMenuButton.setGraphic(cat5GroupAction.getGraphic());
        catGroupMenuButton.showingProperty().addListener(showing -> {
            if (catGroupMenuButton.isShowing()) {
                List<MenuItem> categoryMenues = Lists.transform(controller.getCategoryManager().getCategories(),
                        cat -> GuiUtils.createAutoAssigningMenuItem(catGroupMenuButton, new CategorizeGroupAction(cat, controller)));
                catGroupMenuButton.getItems().setAll(categoryMenues);
            }
        });

    }

    private DataSource getSelectedDataSource() {
        Optional<DataSource> empty = Optional.empty();
        return defaultIfNull(dataSourceSelectionModel.getSelectedItem(), empty).orElse(null);
    }

    private void initDataSourceComboBox() {
        dataSourceComboBox.setCellFactory(param -> new DataSourceCell(dataSourcesViewable, new HashMap<>()));
        dataSourceComboBox.setButtonCell(new DataSourceCell(dataSourcesViewable, new HashMap<>()));
        dataSourceComboBox.setConverter(new StringConverter<Optional<DataSource>>() {
            @Override
            public String toString(Optional<DataSource> object) {
                return object.map(DataSource::getName).orElse("All");
            }

            @Override
            public Optional<DataSource> fromString(String string) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
        });
        dataSourceComboBox.setItems(dataSources);

        Case.addEventTypeSubscriber(ImmutableSet.of(DATA_SOURCE_ADDED, DATA_SOURCE_DELETED),
                evt -> {
                    Platform.runLater(() -> {
                        Optional<DataSource> selectedItem = dataSourceSelectionModel.getSelectedItem();
                        //restore selection once the sync is done.
                        syncDataSources().addListener(() -> dataSourceSelectionModel.select(selectedItem), Platform::runLater);
                    });
                });
        syncDataSources();

        controller.getGroupManager().getDataSourceProperty()
                .addListener((observable, oldDataSource, newDataSource)
                        -> dataSourceSelectionModel.select(Optional.ofNullable(newDataSource)));
        dataSourceSelectionModel.select(Optional.ofNullable(controller.getGroupManager().getDataSource()));
        dataSourceComboBox.disableProperty().bind(groupByBox.getSelectionModel().selectedItemProperty().isNotEqualTo(DrawableAttribute.PATH));
        dataSourceSelectionModel.selectedItemProperty().addListener(queryInvalidationListener);
    }

    private void initTagMenuButton() {
        addFXCallback(exec.submit(() -> new TagGroupAction(controller.getTagsManager().getFollowUpTagName(), controller)),
                followUpGroupAction -> {
                    //on fx thread
                    tagGroupMenuButton.setOnAction(followUpGroupAction);
                    tagGroupMenuButton.setText(followUpGroupAction.getText());
                    tagGroupMenuButton.setGraphic(followUpGroupAction.getGraphic());
                },
                throwable -> {
                    /*
                     * The problem appears to be a timing issue where a case is
                     * closed before this initialization is completed, which It
                     * appears to be harmless, so we are temporarily changing
                     * this log message to a WARNING.
                     *
                     * TODO (JIRA-3010): SEVERE error logged by image Gallery UI
                     */
                    if (Case.isCaseOpen()) {
                        logger.log(Level.WARNING, "Could not create Follow Up tag menu item", throwable); //NON-NLS
                    } else {
                        // don't add stack trace to log because it makes looking for real errors harder
                        logger.log(Level.INFO, "Unable to get tag name. Case is closed."); //NON-NLS
                    }
                }
        );

        tagGroupMenuButton.showingProperty().addListener(showing -> {
            if (tagGroupMenuButton.isShowing()) {
                ListenableFuture<List<MenuItem>> getTagsFuture = exec.submit(() -> {
                    return Lists.transform(controller.getTagsManager().getNonCategoryTagNames(),
                            tagName -> GuiUtils.createAutoAssigningMenuItem(tagGroupMenuButton, new TagGroupAction(tagName, controller)));
                });

                addFXCallback(getTagsFuture,
                        menuItems -> tagGroupMenuButton.getItems().setAll(menuItems),
                        throwable -> logger.log(Level.SEVERE, "Error getting non-gategory tag names.", throwable)
                );
            }
        });
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.ANY)
    private ListenableFuture<List<DataSource>> syncDataSources() {
        ListenableFuture<List<DataSource>> dataSourcesFuture = exec.submit(() -> {
            List<DataSource> dataSourcesInCase = controller.getCaseDatabase().getDataSources();
            synchronized (dataSourcesViewable) {
                dataSourcesViewable.clear();
                dataSourcesViewable.put(null, controller.hasTooManyFiles(null));
                for (DataSource ds : dataSourcesInCase) {
                    dataSourcesViewable.put(ds, controller.hasTooManyFiles(ds));
                }
            }
            return dataSourcesInCase;
        });
        addFXCallback(dataSourcesFuture,
                result -> {
                    //on fx thread
                    List<Optional<DataSource>> newDataSources = new ArrayList<>(Lists.transform(result, Optional::of));
                    newDataSources.add(0, Optional.empty());
                    dataSources.setAll(newDataSources);
                },
                throwable -> logger.log(Level.SEVERE, "Unable to get datasources for current case.", throwable) //NON-NLS

        );

        return dataSourcesFuture;
    }

    /**
     *
     * Static utility to to show a Popover with the given Node as owner.
     *
     * @param owner       The owner of the Popover
     * @param headerText  A short String that will be shown in the top-left
     *                    corner of the Popover.
     * @param headerImage An Image that will be shown at the top-right corner of
     *                    the Popover.
     * @param content     The main content of the Popover, shown in the
     *                    bottom-center
     *
     */
    private static void showPopoverHelp(final Node owner, final String headerText, final Image headerImage, final Node content) {
        Pane borderPane = new BorderPane(null, null, new ImageView(headerImage),
                content,
                new Label(headerText));
        borderPane.setPadding(new Insets(10));
        borderPane.setPrefWidth(500);

        PopOver popOver = new PopOver(borderPane);
        popOver.setDetachable(false);
        popOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER);

        popOver.show(owner);
    }

    /**
     * Disable the tag and catagory controls if and only if there is no group
     * selected.
     *
     * @param newViewState The GroupViewState to use as a source of the
     *                     selection.
     */
    private void syncGroupControlsEnabledState(GroupViewState newViewState) {
        boolean noGroupSelected = (null == newViewState) || (null == newViewState.getGroup());
        Platform.runLater(() -> {
            tagGroupMenuButton.setDisable(noGroupSelected);
            catGroupMenuButton.setDisable(noGroupSelected);
        });
    }

    public void reset() {
        Platform.runLater(() -> {
            groupByBox.getSelectionModel().select(DrawableAttribute.PATH);
            sizeSlider.setValue(SIZE_SLIDER_DEFAULT);
        });
    }

    public Toolbar(ImageGalleryController controller) {
        this.controller = controller;
        FXMLConstructor.construct(this, "Toolbar.fxml"); //NON-NLS
    }
}
