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
package org.sleuthkit.autopsy.imagegallery.gui.drawableviews;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.IntStream;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import static javafx.scene.input.KeyCode.DIGIT0;
import static javafx.scene.input.KeyCode.DIGIT1;
import static javafx.scene.input.KeyCode.DIGIT2;
import static javafx.scene.input.KeyCode.DIGIT3;
import static javafx.scene.input.KeyCode.DIGIT4;
import static javafx.scene.input.KeyCode.DIGIT5;
import static javafx.scene.input.KeyCode.DOWN;
import static javafx.scene.input.KeyCode.LEFT;
import static javafx.scene.input.KeyCode.NUMPAD0;
import static javafx.scene.input.KeyCode.NUMPAD1;
import static javafx.scene.input.KeyCode.NUMPAD2;
import static javafx.scene.input.KeyCode.NUMPAD3;
import static javafx.scene.input.KeyCode.NUMPAD4;
import static javafx.scene.input.KeyCode.NUMPAD5;
import static javafx.scene.input.KeyCode.RIGHT;
import static javafx.scene.input.KeyCode.UP;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.GridCell;
import org.controlsfx.control.GridView;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.action.ActionUtils;
import org.openide.util.Lookup;
import org.openide.util.actions.Presenter;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.ContextMenuActionsProvider;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.coreutils.ThreadConfined.ThreadType;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.imagegallery.FXMLConstructor;
import org.sleuthkit.autopsy.imagegallery.FileIDSelectionModel;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryTopComponent;
import org.sleuthkit.autopsy.imagegallery.actions.AddDrawableTagAction;
import org.sleuthkit.autopsy.imagegallery.actions.Back;
import org.sleuthkit.autopsy.imagegallery.actions.CategorizeAction;
import org.sleuthkit.autopsy.imagegallery.actions.CategorizeSelectedFilesAction;
import org.sleuthkit.autopsy.imagegallery.actions.Forward;
import org.sleuthkit.autopsy.imagegallery.actions.NextUnseenGroup;
import org.sleuthkit.autopsy.imagegallery.actions.SwingMenuItemAdapter;
import org.sleuthkit.autopsy.imagegallery.actions.TagSelectedFilesAction;
import org.sleuthkit.autopsy.imagegallery.datamodel.Category;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.DrawableGroup;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupViewMode;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupViewState;
import org.sleuthkit.autopsy.imagegallery.gui.GuiUtils;
import org.sleuthkit.autopsy.imagegallery.gui.Toolbar;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A GroupPane displays the contents of a {@link DrawableGroup}. It supports
 * both a {@link  GridView} based view and a {@link  SlideShowView} view by
 * swapping out its internal components.
 *
 *
 * TODO: Extract the The GridView instance to a separate class analogous to the
 * SlideShow.
 *
 * TODO: Move selection model into controlsfx GridView and submit pull request
 * to them.
 * https://bitbucket.org/controlsfx/controlsfx/issue/4/add-a-multipleselectionmodel-to-gridview
 */
public class GroupPane extends BorderPane {

    private static final Logger LOGGER = Logger.getLogger(GroupPane.class.getName());

    private static final DropShadow DROP_SHADOW = new DropShadow(10, Color.BLUE);

    private static final Timeline flashAnimation = new Timeline(new KeyFrame(Duration.millis(400), new KeyValue(DROP_SHADOW.radiusProperty(), 1, Interpolator.LINEAR)),
            new KeyFrame(Duration.millis(400), new KeyValue(DROP_SHADOW.radiusProperty(), 15, Interpolator.LINEAR))
    );

    private static final FileIDSelectionModel globalSelectionModel = FileIDSelectionModel.getInstance();
    private static final List<KeyCode> categoryKeyCodes = Arrays.asList(KeyCode.NUMPAD0, KeyCode.NUMPAD1, KeyCode.NUMPAD2, KeyCode.NUMPAD3, KeyCode.NUMPAD4, KeyCode.NUMPAD5,
            KeyCode.DIGIT0, KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4, KeyCode.DIGIT5);

    private final Back backAction;

    private final Forward forwardAction;

    @FXML
    private SplitMenuButton catSelectedSplitMenu;

    @FXML
    private SplitMenuButton tagSelectedSplitMenu;

    @FXML
    private ToolBar headerToolBar;

    @FXML
    private ToggleButton cat0Toggle;
    @FXML
    private ToggleButton cat1Toggle;
    @FXML
    private ToggleButton cat2Toggle;
    @FXML
    private ToggleButton cat3Toggle;
    @FXML
    private ToggleButton cat4Toggle;
    @FXML
    private ToggleButton cat5Toggle;

    @FXML
    private SegmentedButton segButton;

    private SlideShowView slideShowPane;

    @FXML
    private ToggleButton slideShowToggle;

    @FXML
    private Region spacer;

    @FXML
    private GridView<Long> gridView;

    @FXML
    private ToggleButton tileToggle;

    @FXML
    private Button nextButton;

    @FXML
    private Button backButton;

    @FXML
    private Button forwardButton;

    @FXML
    private Label groupLabel;

    private final KeyboardHandler tileKeyboardNavigationHandler = new KeyboardHandler();

    private final NextUnseenGroup nextGroupAction;

    private final ImageGalleryController controller;

    private ContextMenu contextMenu;

    private Integer selectionAnchorIndex;

    GroupViewMode getGroupViewMode() {
        return groupViewMode.get();
    }

    /**
     * the current GroupViewMode of this GroupPane
     */
    private final SimpleObjectProperty<GroupViewMode> groupViewMode = new SimpleObjectProperty<>(GroupViewMode.TILE);

    /**
     * the grouping this pane is currently the view for
     */
    private final ReadOnlyObjectWrapper<DrawableGroup> grouping = new ReadOnlyObjectWrapper<>();

    /**
     * map from fileIDs to their assigned cells in the tile view. This is used
     * to determine whether fileIDs are visible or are offscreen. No entry
     * indicates the given fileID is not displayed on screen. DrawableCells are
     * responsible for adding and removing themselves from this map.
     */
    @ThreadConfined(type = ThreadType.JFX)
    private final Map<Long, DrawableCell> cellMap = new HashMap<>();

    private final InvalidationListener filesSyncListener = (observable) -> {
        final String header = getHeaderString();
        final List<Long> fileIds = getGroup().fileIds();
        Platform.runLater(() -> {
            slideShowToggle.setDisable(fileIds.isEmpty());
            gridView.getItems().setAll(fileIds);
            groupLabel.setText(header);
        });
    };
    @FXML
    private HBox catSegmentedContainer;

    public GroupPane(ImageGalleryController controller) {
        this.controller = controller;
        nextGroupAction = new NextUnseenGroup(controller);
        backAction = new Back(controller);
        forwardAction = new Forward(controller);
        FXMLConstructor.construct(this, "GroupPane.fxml");
    }

    @ThreadConfined(type = ThreadType.JFX)
    public void activateSlideShowViewer(Long slideShowFileID) {
        groupViewMode.set(GroupViewMode.SLIDE_SHOW);
        catSelectedSplitMenu.setVisible(false);
        catSelectedSplitMenu.setManaged(false);
        catSegmentedContainer.setVisible(true);
        catSegmentedContainer.setManaged(true);
        //make a new slideShowPane if necessary
        if (slideShowPane == null) {
            slideShowPane = new SlideShowView(this, controller);
        }

        //assign last selected file or if none first file in group
        if (slideShowFileID == null || getGroup().fileIds().contains(slideShowFileID) == false) {
            slideShowPane.setFile(getGroup().fileIds().get(0));
        } else {
            slideShowPane.setFile(slideShowFileID);
        }

        setCenter(slideShowPane);
        slideShowPane.requestFocus();

    }

    void syncCatToggle(DrawableFile<?> file) {
        getToggleForCategory(file.getCategory()).setSelected(true);
    }

    public void activateTileViewer() {
        groupViewMode.set(GroupViewMode.TILE);
        catSelectedSplitMenu.setVisible(true);
        catSelectedSplitMenu.setManaged(true);
        catSegmentedContainer.setVisible(false);
        catSegmentedContainer.setManaged(false);
        setCenter(gridView);
        gridView.requestFocus();
        if (slideShowPane != null) {
            slideShowPane.disposeContent();
        }
        slideShowPane = null;
        this.scrollToFileID(globalSelectionModel.lastSelectedProperty().get());
    }

    public DrawableGroup getGroup() {
        return grouping.get();
    }

    private void selectAllFiles() {
        globalSelectionModel.clearAndSelectAll(getGroup().fileIds());
    }

    /**
     * create the string to display in the group header
     */
    protected String getHeaderString() {
        return isNull(getGroup()) ? ""
                : StringUtils.defaultIfBlank(getGroup().getGroupByValueDislpayName(), DrawableGroup.getBlankGroupName()) + " -- "
                + getGroup().getHashSetHitsCount() + " hash set hits / " + getGroup().getSize() + " files";
    }

    ContextMenu getContextMenu() {
        return contextMenu;
    }

    ReadOnlyObjectProperty<DrawableGroup> grouping() {
        return grouping.getReadOnlyProperty();
    }

    private ToggleButton getToggleForCategory(Category category) {
        switch (category) {
            case ZERO:
                return cat0Toggle;
            case ONE:
                return cat1Toggle;
            case TWO:
                return cat2Toggle;
            case THREE:
                return cat3Toggle;
            case FOUR:
                return cat4Toggle;
            case FIVE:
                return cat5Toggle;
            default:
                throw new IllegalArgumentException(category.name());
        }
    }

    private class CategorizeToggleHandler implements ChangeListener<Boolean> {

        private final Category cat;

        public CategorizeToggleHandler(Category cat) {
            this.cat = cat;
        }

        @Override
        public void changed(ObservableValue<? extends Boolean> ov, Boolean oldValue, Boolean newValue) {
            if (slideShowPane != null) {
                slideShowPane.getFileID().ifPresent(fileID -> {
                    if (newValue) {
                        FileIDSelectionModel.getInstance().clearAndSelect(fileID);
                        new CategorizeAction(controller).addTag(controller.getTagsManager().getTagName(cat), "");
                    }
                });
            }
        }
    }

    /**
     * called automatically during constructor by FXMLConstructor.
     *
     * checks that FXML loading went ok and performs additional setup
     */
    @FXML
    void initialize() {
        assert cat0Toggle != null : "fx:id=\"cat0Toggle\" was not injected: check your FXML file 'SlideShowView.fxml'.";
        assert cat1Toggle != null : "fx:id=\"cat1Toggle\" was not injected: check your FXML file 'SlideShowView.fxml'.";
        assert cat2Toggle != null : "fx:id=\"cat2Toggle\" was not injected: check your FXML file 'SlideShowView.fxml'.";
        assert cat3Toggle != null : "fx:id=\"cat3Toggle\" was not injected: check your FXML file 'SlideShowView.fxml'.";
        assert cat4Toggle != null : "fx:id=\"cat4Toggle\" was not injected: check your FXML file 'SlideShowView.fxml'.";
        assert cat5Toggle != null : "fx:id=\"cat5Toggle\" was not injected: check your FXML file 'SlideShowView.fxml'.";
        assert gridView != null : "fx:id=\"tilePane\" was not injected: check your FXML file 'GroupPane.fxml'.";
        assert catSelectedSplitMenu != null : "fx:id=\"grpCatSplitMenu\" was not injected: check your FXML file 'GroupHeader.fxml'.";
        assert tagSelectedSplitMenu != null : "fx:id=\"grpTagSplitMenu\" was not injected: check your FXML file 'GroupHeader.fxml'.";
        assert headerToolBar != null : "fx:id=\"headerToolBar\" was not injected: check your FXML file 'GroupHeader.fxml'.";
        assert segButton != null : "fx:id=\"previewList\" was not injected: check your FXML file 'GroupHeader.fxml'.";
        assert slideShowToggle != null : "fx:id=\"segButton\" was not injected: check your FXML file 'GroupHeader.fxml'.";
        assert tileToggle != null : "fx:id=\"tileToggle\" was not injected: check your FXML file 'GroupHeader.fxml'.";

        //configure category toggles
        cat0Toggle.setBorder(new Border(new BorderStroke(Category.ZERO.getColor(), BorderStrokeStyle.SOLID, new CornerRadii(1), new BorderWidths(1))));
        cat1Toggle.setBorder(new Border(new BorderStroke(Category.ONE.getColor(), BorderStrokeStyle.SOLID, new CornerRadii(1), new BorderWidths(1))));
        cat2Toggle.setBorder(new Border(new BorderStroke(Category.TWO.getColor(), BorderStrokeStyle.SOLID, new CornerRadii(1), new BorderWidths(1))));
        cat3Toggle.setBorder(new Border(new BorderStroke(Category.THREE.getColor(), BorderStrokeStyle.SOLID, new CornerRadii(1), new BorderWidths(1))));
        cat4Toggle.setBorder(new Border(new BorderStroke(Category.FOUR.getColor(), BorderStrokeStyle.SOLID, new CornerRadii(1), new BorderWidths(1))));
        cat5Toggle.setBorder(new Border(new BorderStroke(Category.FIVE.getColor(), BorderStrokeStyle.SOLID, new CornerRadii(1), new BorderWidths(1))));

        cat0Toggle.selectedProperty().addListener(new CategorizeToggleHandler(Category.ZERO));
        cat1Toggle.selectedProperty().addListener(new CategorizeToggleHandler(Category.ONE));
        cat2Toggle.selectedProperty().addListener(new CategorizeToggleHandler(Category.TWO));
        cat3Toggle.selectedProperty().addListener(new CategorizeToggleHandler(Category.THREE));
        cat4Toggle.selectedProperty().addListener(new CategorizeToggleHandler(Category.FOUR));
        cat5Toggle.selectedProperty().addListener(new CategorizeToggleHandler(Category.FIVE));

        cat0Toggle.toggleGroupProperty().addListener((o, oldGroup, newGroup) -> {
            newGroup.selectedToggleProperty().addListener((ov, oldToggle, newToggle) -> {
                if (newToggle == null) {
                    oldToggle.setSelected(true);
                }
            });
        });

        //configure flashing glow animation on next unseen group button
        flashAnimation.setCycleCount(Timeline.INDEFINITE);
        flashAnimation.setAutoReverse(true);

        //configure gridView cell properties
        gridView.cellHeightProperty().bind(Toolbar.getDefault(controller).sizeSliderValue().add(75));
        gridView.cellWidthProperty().bind(Toolbar.getDefault(controller).sizeSliderValue().add(75));
        gridView.setCellFactory((GridView<Long> param) -> new DrawableCell());

        //configure toolbar properties
        HBox.setHgrow(spacer, Priority.ALWAYS);
        spacer.setMinWidth(Region.USE_PREF_SIZE);

        FileIDSelectionModel.getInstance().getSelected().addListener((Observable o) -> {
            Platform.runLater(() -> {
                catSelectedSplitMenu.setDisable(FileIDSelectionModel.getInstance().getSelected().isEmpty());
                tagSelectedSplitMenu.setDisable(FileIDSelectionModel.getInstance().getSelected().isEmpty());
            });
        });

        try {
            tagSelectedSplitMenu.setText(controller.getTagsManager().getFollowUpTagName().getDisplayName());
        } catch (TskCoreException tskCoreException) {
            LOGGER.log(Level.WARNING, "failed to load FollowUpTagName", tskCoreException);
        }
        tagSelectedSplitMenu.setOnAction(actionEvent -> {
            try {
                new TagSelectedFilesAction(controller.getTagsManager().getFollowUpTagName(), controller).handle(actionEvent);
            } catch (TskCoreException tskCoreException) {
                LOGGER.log(Level.WARNING, "failed to load FollowUpTagName", tskCoreException);
            }
        });

        tagSelectedSplitMenu.setGraphic(new ImageView(DrawableAttribute.TAGS.getIcon()));
        tagSelectedSplitMenu.showingProperty().addListener(showing -> {
            if (tagSelectedSplitMenu.isShowing()) {
                List<MenuItem> selTagMenues = Lists.transform(controller.getTagsManager().getNonCategoryTagNames(),
                        tagName -> GuiUtils.createAutoAssigningMenuItem(tagSelectedSplitMenu, new TagSelectedFilesAction(tagName, controller)));
                tagSelectedSplitMenu.getItems().setAll(selTagMenues);
            }
        });

        List<MenuItem> grpCategoryMenues = Lists.transform(Arrays.asList(Category.values()),
                cat -> GuiUtils.createAutoAssigningMenuItem(catSelectedSplitMenu, new CategorizeSelectedFilesAction(cat, controller)));

        catSelectedSplitMenu.setText(Category.FIVE.getDisplayName());
        catSelectedSplitMenu.setGraphic(new ImageView(DrawableAttribute.CATEGORY.getIcon()));
        catSelectedSplitMenu.getItems().setAll(grpCategoryMenues);
        catSelectedSplitMenu.setOnAction(GuiUtils.createAutoAssigningMenuItem(catSelectedSplitMenu, new CategorizeSelectedFilesAction(Category.FIVE, controller)).getOnAction());

        Runnable syncMode = () -> {
            switch (groupViewMode.get()) {
                case SLIDE_SHOW:
                    slideShowToggle.setSelected(true);
                    break;
                case TILE:
                    tileToggle.setSelected(true);
                    break;
            }
        };
        syncMode.run();
        //make togle states match view state
        groupViewMode.addListener((o) -> {
            syncMode.run();
        });

        slideShowToggle.toggleGroupProperty().addListener((o) -> {
            slideShowToggle.getToggleGroup().selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
                if (newToggle == null) {
                    oldToggle.setSelected(true);
                }
            });
        });

        //listen to toggles and update view state
        slideShowToggle.setOnAction((ActionEvent t) -> {
            activateSlideShowViewer(globalSelectionModel.lastSelectedProperty().get());
        });

        tileToggle.setOnAction((ActionEvent t) -> {
            activateTileViewer();
        });

        controller.viewState().addListener((ObservableValue<? extends GroupViewState> observable, GroupViewState oldValue, GroupViewState newValue) -> {
            setViewState(newValue);
        });

        addEventFilter(KeyEvent.KEY_PRESSED, tileKeyboardNavigationHandler);
        gridView.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {

            private ContextMenu buildContextMenu() {
                ArrayList<MenuItem> menuItems = new ArrayList<>();

                menuItems.add(new CategorizeAction(controller).getPopupMenu());
                menuItems.add(new AddDrawableTagAction(controller).getPopupMenu());

                Collection<? extends ContextMenuActionsProvider> menuProviders = Lookup.getDefault().lookupAll(ContextMenuActionsProvider.class);

                for (ContextMenuActionsProvider provider : menuProviders) {

                    for (final Action act : provider.getActions()) {

                        if (act instanceof Presenter.Popup) {
                            Presenter.Popup aact = (Presenter.Popup) act;

                            menuItems.add(SwingMenuItemAdapter.create(aact.getPopupPresenter()));
                        }
                    }
                }
                final MenuItem extractMenuItem = new MenuItem("Extract File(s)");
                extractMenuItem.setOnAction((ActionEvent t) -> {
                    SwingUtilities.invokeLater(() -> {
                        TopComponent etc = WindowManager.getDefault().findTopComponent(ImageGalleryTopComponent.PREFERRED_ID);
                        ExtractAction.getInstance().actionPerformed(new java.awt.event.ActionEvent(etc, 0, null));
                    });
                });
                menuItems.add(extractMenuItem);

                ContextMenu contextMenu = new ContextMenu(menuItems.toArray(new MenuItem[]{}));
                contextMenu.setAutoHide(true);
                return contextMenu;
            }

            @Override
            public void handle(MouseEvent t) {
                switch (t.getButton()) {
                    case PRIMARY:
                        if (t.getClickCount() == 1) {
                            globalSelectionModel.clearSelection();
                            if (contextMenu != null) {
                                contextMenu.hide();
                            }
                        }
                        t.consume();
                        break;
                    case SECONDARY:
                        if (t.getClickCount() == 1) {
                            selectAllFiles();
                        }
                        if (globalSelectionModel.getSelected().isEmpty() == false) {
                            if (contextMenu == null) {
                                contextMenu = buildContextMenu();
                            }

                            contextMenu.hide();
                            contextMenu.show(GroupPane.this, t.getScreenX(), t.getScreenY());
                        }
                        t.consume();
                        break;
                }
            }
        });

        ActionUtils.configureButton(nextGroupAction, nextButton);
        final EventHandler<ActionEvent> onAction = nextButton.getOnAction();
        nextButton.setOnAction((ActionEvent event) -> {
            flashAnimation.stop();
            nextButton.setEffect(null);
            onAction.handle(event);
        });

        ActionUtils.configureButton(forwardAction, forwardButton);
        ActionUtils.configureButton(backAction, backButton);

        nextGroupAction.disabledProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
            nextButton.setEffect(newValue ? null : DROP_SHADOW);
            if (newValue == false) {
                flashAnimation.play();
            } else {
                flashAnimation.stop();
            }
        });

        //listen to tile selection and make sure it is visible in scroll area
        //TODO: make sure we are testing complete visability not just bounds intersection
        globalSelectionModel.lastSelectedProperty().addListener((observable, oldFileID, newFileId) -> {
            if (groupViewMode.get() == GroupViewMode.SLIDE_SHOW) {
                slideShowPane.setFile(newFileId);
            } else {
                scrollToFileID(newFileId);
            }
        });

        setViewState(controller.viewState().get());
    }

    @ThreadConfined(type = ThreadType.JFX)
    private void scrollToFileID(final Long newFileID) {
        if (newFileID == null) {
            return;   //scrolling to no file doesn't make sense, so abort.
        }

        final ObservableList<Long> fileIds = gridView.getItems();

        int selectedIndex = fileIds.indexOf(newFileID);
        if (selectedIndex == -1) {
            //somehow we got passed a file id that isn't in the curent group.
            //this should never happen, but if it does everything is going to fail, so abort.
            return;
        }

        getScrollBar().ifPresent(scrollBar -> {
            DrawableCell cell = cellMap.get(newFileID);

            //while there is no tile/cell for the given id, scroll based on index in group
            while (isNull(cell)) {
                //TODO:  can we maintain a cached mapping from fileID-> index to speed up performance
                //get the min and max index of files that are in the cellMap
                Integer minIndex = cellMap.keySet().stream()
                        .mapToInt(fileID -> fileIds.indexOf(fileID))
                        .min().getAsInt();
                Integer maxIndex = cellMap.keySet().stream()
                        .mapToInt(fileID -> fileIds.indexOf(fileID))
                        .max().getAsInt();

                //[minIndex, maxIndex] is the range of indexes in the fileIDs list that are currently displayed
                if (selectedIndex < minIndex) {
                    scrollBar.decrement();
                } else if (selectedIndex > maxIndex) {
                    scrollBar.increment();
                } else {
                    //sometimes the cellMap isn't up to date, so move the position arbitrarily to update the cellMap
                    //TODO: this is clunky and slow, find a better way to do this
                    scrollBar.adjustValue(.5);
                }
                cell = cellMap.get(newFileID);
            }

            final Bounds gridViewBounds = gridView.localToScene(gridView.getBoundsInLocal());
            Bounds tileBounds = cell.localToScene(cell.getBoundsInLocal());

            //while the cell is not within the visisble bounds of the gridview, scroll based on screen coordinates
            int i = 0;
            while (gridViewBounds.contains(tileBounds) == false && (i++ < 100)) {

                if (tileBounds.getMinY() < gridViewBounds.getMinY()) {
                    scrollBar.decrement();
                } else if (tileBounds.getMaxY() > gridViewBounds.getMaxY()) {
                    scrollBar.increment();
                }
                tileBounds = cell.localToScene(cell.getBoundsInLocal());
            }
        });
    }

    /**
     * assigns a grouping for this pane to represent and initializes grouping
     * specific properties and listeners
     *
     * @param grouping the new grouping assigned to this group
     */
    void setViewState(GroupViewState viewState) {

        if (isNull(viewState) || isNull(viewState.getGroup())) {
            if (nonNull(getGroup())) {
                getGroup().fileIds().removeListener(filesSyncListener);
            }
            this.grouping.set(null);

            Platform.runLater(() -> {
                gridView.getItems().setAll(Collections.emptyList());
                setCenter(null);
                slideShowToggle.setDisable(true);
                groupLabel.setText("");
                resetScrollBar();
                if (false == Case.isCaseOpen()) {
                    cellMap.values().stream().forEach(DrawableCell::resetItem);
                    cellMap.clear();
                }
            });

        } else {
            if (getGroup() != viewState.getGroup()) {
                if (nonNull(getGroup())) {
                    getGroup().fileIds().removeListener(filesSyncListener);
                }
                this.grouping.set(viewState.getGroup());

                getGroup().fileIds().addListener(filesSyncListener);

                final String header = getHeaderString();

                Platform.runLater(() -> {
                    gridView.getItems().setAll(getGroup().fileIds());
                    slideShowToggle.setDisable(gridView.getItems().isEmpty());
                    groupLabel.setText(header);
                    resetScrollBar();
                    if (viewState.getMode() == GroupViewMode.TILE) {
                        activateTileViewer();
                    } else {
                        activateSlideShowViewer(viewState.getSlideShowfileID().orElse(null));
                    }
                });
            }
        }
    }

    @ThreadConfined(type = ThreadType.JFX)
    private void resetScrollBar() {
        getScrollBar().ifPresent((scrollBar) -> {
            scrollBar.setValue(0);
        });
    }

    @ThreadConfined(type = ThreadType.JFX)
    private Optional<ScrollBar> getScrollBar() {
        if (gridView == null || gridView.getSkin() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable((ScrollBar) gridView.getSkin().getNode().lookup(".scroll-bar"));
    }

    void makeSelection(Boolean shiftDown, Long newFileID) {

        if (shiftDown) {
            //TODO: do more hear to implement slicker multiselect
            int endIndex = grouping.get().fileIds().indexOf(newFileID);
            int startIndex = IntStream.of(grouping.get().fileIds().size(), selectionAnchorIndex, endIndex).min().getAsInt();
            endIndex = IntStream.of(0, selectionAnchorIndex, endIndex).max().getAsInt();
            List<Long> subList = grouping.get().fileIds().subList(Math.max(0, startIndex), Math.min(endIndex, grouping.get().fileIds().size()) + 1);

            globalSelectionModel.clearAndSelectAll(subList.toArray(new Long[subList.size()]));
            globalSelectionModel.select(newFileID);
        } else {
            selectionAnchorIndex = null;
            globalSelectionModel.clearAndSelect(newFileID);
        }
    }

    private class DrawableCell extends GridCell<Long> {

        private final DrawableTile tile = new DrawableTile(GroupPane.this, controller);

        DrawableCell() {
            itemProperty().addListener((ObservableValue<? extends Long> observable, Long oldValue, Long newValue) -> {
                if (oldValue != null) {
                    cellMap.remove(oldValue, DrawableCell.this);
                    tile.setFile(null);
                }
                if (newValue != null) {
                    if (cellMap.containsKey(newValue)) {
                        if (tile != null) {
                            // Clear out the old value to prevent out-of-date listeners
                            // from activating.
                            cellMap.get(newValue).tile.setFile(null);
                        }
                    }
                    cellMap.put(newValue, DrawableCell.this);

                }
            });

            setGraphic(tile);
        }

        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);

            tile.setFile(item);
        }

        void resetItem() {
//            updateItem(null, true);
            tile.setFile(null);
        }
    }

    /**
     * implements the key handler for tile navigation ( up, down , left, right
     * arrows)
     */
    private class KeyboardHandler implements EventHandler<KeyEvent> {

        @Override
        public void handle(KeyEvent t) {

            if (t.getEventType() == KeyEvent.KEY_PRESSED) {
                switch (t.getCode()) {
                    case SHIFT:
                        if (selectionAnchorIndex == null) {
                            selectionAnchorIndex = grouping.get().fileIds().indexOf(globalSelectionModel.lastSelectedProperty().get());
                        }
                        t.consume();
                        break;
                    case UP:
                    case DOWN:
                    case LEFT:
                    case RIGHT:
                        if (groupViewMode.get() == GroupViewMode.TILE) {
                            handleArrows(t);
                            t.consume();
                        }
                        break;
                    case PAGE_DOWN:
                        getScrollBar().ifPresent((scrollBar) -> {
                            scrollBar.adjustValue(1);
                        });
                        t.consume();
                        break;
                    case PAGE_UP:
                        getScrollBar().ifPresent((scrollBar) -> {
                            scrollBar.adjustValue(0);
                        });
                        t.consume();
                        break;
                    case ENTER:
                        nextGroupAction.handle(null);
                        t.consume();
                        break;
                    case SPACE:
                        if (groupViewMode.get() == GroupViewMode.TILE) {
                            activateSlideShowViewer(globalSelectionModel.lastSelectedProperty().get());
                        } else {
                            activateTileViewer();
                        }
                        t.consume();
                        break;
                }

                if (groupViewMode.get() == GroupViewMode.TILE && categoryKeyCodes.contains(t.getCode()) && t.isAltDown()) {
                    selectAllFiles();
                    t.consume();
                }
                if (globalSelectionModel.getSelected().isEmpty() == false) {
                    switch (t.getCode()) {
                        case NUMPAD0:
                        case DIGIT0:
                            new CategorizeAction(controller).addTag(controller.getTagsManager().getTagName(Category.ZERO), "");
                            break;
                        case NUMPAD1:
                        case DIGIT1:
                            new CategorizeAction(controller).addTag(controller.getTagsManager().getTagName(Category.ONE), "");
                            break;
                        case NUMPAD2:
                        case DIGIT2:
                            new CategorizeAction(controller).addTag(controller.getTagsManager().getTagName(Category.TWO), "");
                            break;
                        case NUMPAD3:
                        case DIGIT3:
                            new CategorizeAction(controller).addTag(controller.getTagsManager().getTagName(Category.THREE), "");
                            break;
                        case NUMPAD4:
                        case DIGIT4:
                            new CategorizeAction(controller).addTag(controller.getTagsManager().getTagName(Category.FOUR), "");
                            break;
                        case NUMPAD5:
                        case DIGIT5:
                            new CategorizeAction(controller).addTag(controller.getTagsManager().getTagName(Category.FIVE), "");
                            break;
                    }
                }
            }
        }

        private void handleArrows(KeyEvent t) {
            Long lastSelectFileId = globalSelectionModel.lastSelectedProperty().get();

            int lastSelectedIndex = lastSelectFileId != null
                    ? grouping.get().fileIds().indexOf(lastSelectFileId)
                    : Optional.ofNullable(selectionAnchorIndex).orElse(0);

            final int columns = Math.max((int) Math.floor((gridView.getWidth() - 18) / (gridView.getCellWidth() + gridView.getHorizontalCellSpacing() * 2)), 1);

            final Map<KeyCode, Integer> tileIndexMap = ImmutableMap.of(UP, -columns, DOWN, columns, LEFT, -1, RIGHT, 1);

            // implement proper keyboard based multiselect
            int indexOfToBeSelectedTile = lastSelectedIndex + tileIndexMap.get(t.getCode());
            final int size = grouping.get().fileIds().size();
            if (0 > indexOfToBeSelectedTile) {
                //don't select past begining of group
            } else if (0 <= indexOfToBeSelectedTile && indexOfToBeSelectedTile < size) {
                //normal selection within group
                makeSelection(t.isShiftDown(), grouping.get().fileIds().get(indexOfToBeSelectedTile));
            } else if (indexOfToBeSelectedTile <= size - 1 + columns - (size % columns)) {
                //selection last item if selection is empty space at end of group
                makeSelection(t.isShiftDown(), grouping.get().fileIds().get(size - 1));
            } else {
                //don't select past end of group
            }
        }
    }

}
