/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2019 Basis Technology Corp.
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
import static com.google.common.collect.Lists.transform;
import com.google.common.util.concurrent.ListeningExecutorService;
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
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.effect.DropShadow;
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
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import javax.swing.SwingUtilities;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.GridCell;
import org.controlsfx.control.GridView;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.action.ActionUtils;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.actions.Presenter;
import org.openide.windows.TopComponent;
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
import org.sleuthkit.autopsy.imagegallery.actions.AddTagAction;
import org.sleuthkit.autopsy.imagegallery.actions.Back;
import org.sleuthkit.autopsy.imagegallery.actions.CategorizeAction;
import org.sleuthkit.autopsy.imagegallery.actions.CategorizeSelectedFilesAction;
import org.sleuthkit.autopsy.imagegallery.actions.Forward;
import org.sleuthkit.autopsy.imagegallery.actions.NextUnseenGroup;
import org.sleuthkit.autopsy.imagegallery.actions.RedoAction;
import org.sleuthkit.autopsy.imagegallery.actions.SwingMenuItemAdapter;
import org.sleuthkit.autopsy.imagegallery.actions.TagSelectedFilesAction;
import org.sleuthkit.autopsy.imagegallery.actions.UndoAction;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.DrawableGroup;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupViewMode;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupViewState;
import static org.sleuthkit.autopsy.imagegallery.gui.GuiUtils.createAutoAssigningMenuItem;
import org.sleuthkit.autopsy.imagegallery.utils.TaskUtils;
import static org.sleuthkit.autopsy.imagegallery.utils.TaskUtils.addFXCallback;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A GroupPane displays the contents of a DrawableGroup. It supports both
 * GridView and SlideShowView modes by swapping out its internal components.
 *
 * TODO: Extract the The GridView instance to a separate class analogous to the
 * SlideShow.
 *
 * TODO: Move selection model into controlsfx GridView and submit pull request
 * to them.
 * https://bitbucket.org/controlsfx/controlsfx/issue/4/add-a-multipleselectionmodel-to-gridview
 */
public class GroupPane extends BorderPane {

    private static final Logger logger = Logger.getLogger(GroupPane.class.getName());

    private static final BorderWidths BORDER_WIDTHS_2 = new BorderWidths(2);
    private static final CornerRadii CORNER_RADII_2 = new CornerRadii(2);

    private static final DropShadow DROP_SHADOW = new DropShadow(10, Color.BLUE);

    private static final Timeline flashAnimation = new Timeline(
            new KeyFrame(Duration.millis(400), new KeyValue(DROP_SHADOW.radiusProperty(), 1, Interpolator.LINEAR)),
            new KeyFrame(Duration.millis(400), new KeyValue(DROP_SHADOW.radiusProperty(), 15, Interpolator.LINEAR))
    );

    private static final List<KeyCode> categoryKeyCodes = Arrays.asList(
            NUMPAD0, NUMPAD1, NUMPAD2, NUMPAD3, NUMPAD4, NUMPAD5,
            DIGIT0, DIGIT1, DIGIT2, DIGIT3, DIGIT4, DIGIT5);

    @FXML
    private Button undoButton;
    @FXML
    private Button redoButton;

    @FXML
    private SplitMenuButton catSelectedSplitMenu;
    @FXML
    private SplitMenuButton tagSelectedSplitMenu;
    @FXML
    private ToolBar headerToolBar;
    @FXML
    private SegmentedButton segButton;

    @FXML
    private ToggleButton slideShowToggle;
    @FXML
    private ToggleButton tileToggle;

    private SlideShowView slideShowPane;

    @FXML
    private GridView<Long> gridView;
    @FXML
    private Button nextButton;
    @FXML
    private AnchorPane nextButtonPane;
    @FXML
    private CheckBox seenByOtherExaminersCheckBox;
    @FXML
    private Button backButton;
    @FXML
    private Button forwardButton;
    @FXML
    private Label groupLabel;
    @FXML
    private Label bottomLabel;
    @FXML
    private Label headerLabel;
    @FXML
    private Label catContainerLabel;
    @FXML
    private HBox catSplitMenuContainer;

    private final ListeningExecutorService exec = TaskUtils.getExecutorForClass(GroupPane.class);

    private final ImageGalleryController controller;

    private final FileIDSelectionModel selectionModel;
    private Integer selectionAnchorIndex;

    private final UndoAction undoAction;
    private final RedoAction redoAction;
    private final Back backAction;
    private final Forward forwardAction;
    private final NextUnseenGroup nextGroupAction;

    private final KeyboardHandler tileKeyboardNavigationHandler = new KeyboardHandler();

    private ContextMenu contextMenu;

    /**
     * the current GroupViewMode of this GroupPane
     */
    private final SimpleObjectProperty<GroupViewMode> groupViewMode = new SimpleObjectProperty<>(GroupViewMode.TILE);

    /**
     * the grouping this pane is currently the view for
     */
    private final ReadOnlyObjectWrapper<DrawableGroup> grouping = new ReadOnlyObjectWrapper<>();

    private final Map<String, ToggleButton> toggleButtonMap = new HashMap<>();

    /**
     * Map from fileIDs to their assigned cells in the tile view. This is used
     * to determine whether fileIDs are visible or are offscreen. No entry
     * indicates the given fileID is not displayed on screen. DrawableCells are
     * responsible for adding and removing themselves from this map.
     */
    @ThreadConfined(type = ThreadType.JFX)
    private final Map<Long, DrawableCell> cellMap = new HashMap<>();
    
    private final InvalidationListener filesSyncListener = (observable) -> {
        final String header = getHeaderString();
        final List<Long> fileIds = getGroup().getFileIDs();
        Platform.runLater(() -> {
            slideShowToggle.setDisable(fileIds.isEmpty());
            gridView.getItems().setAll(fileIds);
            groupLabel.setText(header);
        });
    };

    public GroupPane(ImageGalleryController controller) {
        this.controller = controller;
        this.selectionModel = controller.getSelectionModel();
        nextGroupAction = new NextUnseenGroup(controller);
        backAction = new Back(controller);
        forwardAction = new Forward(controller);
        undoAction = new UndoAction(controller);
        redoAction = new RedoAction(controller);

        FXMLConstructor.construct(this, "GroupPane.fxml"); //NON-NLS        
    }

    GroupViewMode getGroupViewMode() {
        return groupViewMode.get();
    }

    @ThreadConfined(type = ThreadType.JFX)
    public void activateSlideShowViewer(Long slideShowFileID) {
        groupViewMode.set(GroupViewMode.SLIDE_SHOW);
        slideShowToggle.setSelected(true);
        //make a new slideShowPane if necessary
        if (slideShowPane == null) {
            slideShowPane = new SlideShowView(this, controller);
        }

        //assign last selected file or if none first file in group
        if (slideShowFileID == null || getGroup().getFileIDs().contains(slideShowFileID) == false) {
            slideShowPane.setFile(getGroup().getFileIDs().get(0));
        } else {
            slideShowPane.setFile(slideShowFileID);
        }

        setCenter(slideShowPane);
        slideShowPane.requestFocus();

    }

    void syncCatToggle(DrawableFile file) {
        TagName tagName = file.getCategory();
        if (tagName != null) {
            getToggleForCategory(tagName).setSelected(true);
        }
    }

    /**
     * Returns a toggle button for the given TagName.
     *
     * @param tagName TagName to create a button for.
     *
     * @return A new instance of a ToggleButton.
     */
    private ToggleButton getToggleForCategory(TagName tagName) {

        ToggleButton button = toggleButtonMap.get(tagName.getDisplayName());

        if (button == null) {
            String[] split = tagName.getDisplayName().split(":");
            split = split[0].split("-");

            int category = Integer.parseInt(split[1]);

            button = new ToggleButton();
            button.setText(Integer.toString(category));

            toggleButtonMap.put(tagName.getDisplayName(), button);
        }
        return button;
    }

    public void activateTileViewer() {
        groupViewMode.set(GroupViewMode.TILE);
        tileToggle.setSelected(true);
        setCenter(gridView);
        gridView.requestFocus();
        if (slideShowPane != null) {
            slideShowPane.disposeContent();
        }
        slideShowPane = null;
        this.scrollToFileID(selectionModel.lastSelectedProperty().get());
    }

    public DrawableGroup getGroup() {
        return grouping.get();
    }

    private void selectAllFiles() {
        selectionModel.clearAndSelectAll(getGroup().getFileIDs());
    }

    /**
     * Create the string to display in the group header.
     *
     * @return The string to display in the group header.
     */
    @NbBundle.Messages({"# {0} - default group name",
        "# {1} - hashset hits count",
        "# {2} - group size",
        "GroupPane.headerString={0} -- {1} hash set hits / {2} files"})
    protected String getHeaderString() {
        return isNull(getGroup()) ? ""
                : Bundle.GroupPane_headerString(StringUtils.defaultIfBlank(getGroup().getGroupByValueDislpayName(), DrawableGroup.getBlankGroupName()),
                        getGroup().getHashSetHitsCount(), getGroup().getSize());
    }

    ContextMenu getContextMenu() {
        return contextMenu;
    }

    ReadOnlyObjectProperty<DrawableGroup> grouping() {
        return grouping.getReadOnlyProperty();
    }

    /**
     * called automatically during constructor by FXMLConstructor.
     *
     * checks that FXML loading went ok and performs additional setup
     */
    @FXML
    @NbBundle.Messages({"GroupPane.gridViewContextMenuItem.extractFiles=Extract File(s)",
        "GroupPane.bottomLabel.displayText=Group Viewing History: ",
        "GroupPane.hederLabel.displayText=Tag Selected Files:",
        "GroupPane.catContainerLabel.displayText=Categorize Selected File:",
        "GroupPane.catHeadingLabel.displayText=Category:"})
    void initialize() {
        assert gridView != null : "fx:id=\"tilePane\" was not injected: check your FXML file 'GroupPane.fxml'.";
        assert catSelectedSplitMenu != null : "fx:id=\"grpCatSplitMenu\" was not injected: check your FXML file 'GroupPane.fxml'.";
        assert tagSelectedSplitMenu != null : "fx:id=\"grpTagSplitMenu\" was not injected: check your FXML file 'GroupPane.fxml'.";
        assert headerToolBar != null : "fx:id=\"headerToolBar\" was not injected: check your FXML file 'GroupPane.fxml'.";
        assert segButton != null : "fx:id=\"previewList\" was not injected: check your FXML file 'GroupPane.fxml'.";
        assert slideShowToggle != null : "fx:id=\"segButton\" was not injected: check your FXML file 'GroupPane.fxml'.";
        assert tileToggle != null : "fx:id=\"tileToggle\" was not injected: check your FXML file 'GroupPane.fxml'.";
        assert seenByOtherExaminersCheckBox != null : "fx:id=\"seenByOtherExaminersCheckBox\" was not injected: check your FXML file 'GroupPane.fxml'.";

        //configure flashing glow animation on next unseen group button
        flashAnimation.setCycleCount(Timeline.INDEFINITE);
        flashAnimation.setAutoReverse(true);

        //configure gridView cell properties
        DoubleBinding cellSize = controller.thumbnailSizeProperty().add(75);
        gridView.cellHeightProperty().bind(cellSize);
        gridView.cellWidthProperty().bind(cellSize);
        gridView.setCellFactory(param -> new DrawableCell());

        BooleanBinding isSelectionEmpty = Bindings.isEmpty(selectionModel.getSelected());
        catSelectedSplitMenu.disableProperty().bind(isSelectionEmpty);
        tagSelectedSplitMenu.disableProperty().bind(isSelectionEmpty);

        addFXCallback(exec.submit(() -> controller.getTagsManager().getFollowUpTagName()),
                followUpTagName -> {
                    //on fx thread
                    TagSelectedFilesAction followUpSelectedAction = new TagSelectedFilesAction(followUpTagName, controller);
                    tagSelectedSplitMenu.setText(followUpSelectedAction.getText());
                    tagSelectedSplitMenu.setGraphic(followUpSelectedAction.getGraphic());
                    tagSelectedSplitMenu.setOnAction(followUpSelectedAction);
                },
                throwable -> logger.log(Level.SEVERE, "Error getting tag names.", throwable));

        addFXCallback(exec.submit(() -> controller.getTagsManager().getNonCategoryTagNames()),
                tagNames -> {
                    //on fx thread
                    List<MenuItem> menuItems = transform(tagNames,
                            tagName -> createAutoAssigningMenuItem(tagSelectedSplitMenu, new TagSelectedFilesAction(tagName, controller)));
                    tagSelectedSplitMenu.getItems().setAll(menuItems);
                },
                throwable -> logger.log(Level.SEVERE, "Error getting tag names.", throwable)//NON-NLS
        );
        CategorizeSelectedFilesAction cat5SelectedAction = new CategorizeSelectedFilesAction(controller.getCategoryManager().getCategories().get(0), controller);

        catSelectedSplitMenu.setOnAction(cat5SelectedAction);

        catSelectedSplitMenu.setText(cat5SelectedAction.getText());
        catSelectedSplitMenu.setGraphic(cat5SelectedAction.getGraphic());

        List<MenuItem> categoryMenues = transform(controller.getCategoryManager().getCategories(),
                cat -> createAutoAssigningMenuItem(catSelectedSplitMenu, new CategorizeSelectedFilesAction(cat, controller)));
        catSelectedSplitMenu.getItems().setAll(categoryMenues);

        slideShowToggle.getStyleClass().remove("radio-button");
        slideShowToggle.getStyleClass().add("toggle-button");
        tileToggle.getStyleClass().remove("radio-button");
        tileToggle.getStyleClass().add("toggle-button");

        bottomLabel.setText(Bundle.GroupPane_bottomLabel_displayText());
        headerLabel.setText(Bundle.GroupPane_hederLabel_displayText());
        catContainerLabel.setText(Bundle.GroupPane_catContainerLabel_displayText());
        
        // This seems to be the only way to make sure the when the user switches
        // to SLIDE_SHOW the first time that the undo\redo buttons are removed.
        headerToolBar.getItems().remove(undoButton);
        headerToolBar.getItems().remove(redoButton);
        headerToolBar.getItems().add(undoButton);
        headerToolBar.getItems().add(redoButton);

        groupViewMode.addListener((ObservableValue<? extends GroupViewMode> observable, GroupViewMode oldValue, GroupViewMode newValue) -> {
            if (newValue == GroupViewMode.SLIDE_SHOW) {
                headerToolBar.getItems().remove(undoButton);
                headerToolBar.getItems().remove(redoButton);
            } else {
                headerToolBar.getItems().add(undoButton);
                headerToolBar.getItems().add(redoButton);
            }
        });

        //listen to toggles and update view state
        slideShowToggle.setOnAction(onAction -> activateSlideShowViewer(selectionModel.lastSelectedProperty().get()));
        tileToggle.setOnAction(onAction -> activateTileViewer());

        controller.viewStateProperty().addListener((observable, oldViewState, newViewState) -> setViewState(newViewState));

        addEventFilter(KeyEvent.KEY_PRESSED, tileKeyboardNavigationHandler);
        gridView.addEventHandler(MouseEvent.MOUSE_CLICKED, new MouseHandler());

        ActionUtils.configureButton(undoAction, undoButton);
        ActionUtils.configureButton(redoAction, redoButton);
        ActionUtils.configureButton(forwardAction, forwardButton);
        ActionUtils.configureButton(backAction, backButton);
        ActionUtils.configureButton(nextGroupAction, nextButton);
        /*
         * the next button does stuff in the GroupPane that next action does'nt
         * know about, so do that stuff and then delegate to nextGroupAction
         */
        final EventHandler<ActionEvent> onAction = nextButton.getOnAction();
        nextButton.setOnAction(actionEvent -> {
            flashAnimation.stop();
            nextButton.setEffect(null);
            onAction.handle(actionEvent);
        });

        nextGroupAction.disabledProperty().addListener((Observable observable) -> {
            boolean newValue = nextGroupAction.isDisabled();
            nextButton.setEffect(newValue ? null : DROP_SHADOW);
            if (newValue) {//stop on disabled
                flashAnimation.stop();
            } else { //play when enabled
                flashAnimation.play();
            }
        });

        seenByOtherExaminersCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            nextButtonPane.setDisable(true);
            nextButtonPane.setCursor(Cursor.WAIT);
            exec.submit(() -> controller.getGroupManager().setCollaborativeMode(newValue))
                    .addListener(() -> {
                        nextButtonPane.setDisable(false);
                        nextButtonPane.setCursor(Cursor.DEFAULT);
                    }, Platform::runLater);
        });

        //listen to tile selection and make sure it is visible in scroll area
        selectionModel.lastSelectedProperty().addListener((observable, oldFileID, newFileId) -> {
            if (groupViewMode.get() == GroupViewMode.SLIDE_SHOW
                    && slideShowPane != null) {
                slideShowPane.setFile(newFileId);
            } else {
                scrollToFileID(newFileId);
            }
        });

        setViewState(controller.viewStateProperty().get());

    }

    //TODO: make sure we are testing complete visability not just bounds intersection
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
     * @param newViewState
     */
    void setViewState(GroupViewState newViewState) {

        if (isNull(newViewState) || isNull(newViewState.getGroup().orElse(null))) {
            if (nonNull(getGroup())) {
                getGroup().getFileIDs().removeListener(filesSyncListener);
            }
            this.grouping.set(null);

            Platform.runLater(() -> {
                gridView.getItems().setAll(Collections.emptyList());
                setCenter(new Label("No group selected"));
                slideShowToggle.setDisable(true);
                groupLabel.setText("");
                resetScrollBar();
                if (false == Case.isCaseOpen()) {
                    cellMap.values().stream().forEach(DrawableCell::resetItem);
                    cellMap.clear();
                }
            });

        } else {
            if (nonNull(getGroup()) && getGroup() != newViewState.getGroup().get()) {
                getGroup().getFileIDs().removeListener(filesSyncListener);
            }

            this.grouping.set(newViewState.getGroup().get());

            getGroup().getFileIDs().addListener(filesSyncListener);

            final String header = getHeaderString();

            Platform.runLater(() -> {
                gridView.getItems().setAll(getGroup().getFileIDs());
                boolean empty = gridView.getItems().isEmpty();
                slideShowToggle.setDisable(empty);

                groupLabel.setText(header);
                resetScrollBar();
                if (empty) {
                    setCenter(new Label("There are no files in the selected group."));
                } else if (newViewState.getMode() == GroupViewMode.TILE) {
                    activateTileViewer();
                } else {
                    activateSlideShowViewer(newViewState.getSlideShowfileID().orElse(null));
                }

            });
        }
    }

    @ThreadConfined(type = ThreadType.JFX)
    private void resetScrollBar() {
        getScrollBar().ifPresent(scrollBar -> scrollBar.setValue(0));
    }

    @ThreadConfined(type = ThreadType.JFX)
    private Optional<ScrollBar> getScrollBar() {
        if (gridView == null || gridView.getSkin() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable((ScrollBar) gridView.getSkin().getNode().lookup(".scroll-bar")); //NON-NLS
    }

    void makeSelection(Boolean shiftDown, Long newFileID) {
        if (shiftDown) {
            //TODO: do more hear to implement slicker multiselect
            int endIndex = grouping.get().getFileIDs().indexOf(newFileID);
            int startIndex = IntStream.of(grouping.get().getFileIDs().size(), selectionAnchorIndex, endIndex).min().getAsInt();
            endIndex = IntStream.of(0, selectionAnchorIndex, endIndex).max().getAsInt();
            List<Long> subList = grouping.get().getFileIDs().subList(Math.max(0, startIndex), Math.min(endIndex, grouping.get().getFileIDs().size()) + 1);

            selectionModel.clearAndSelectAll(subList.toArray(new Long[subList.size()]));
            selectionModel.select(newFileID);
        } else {
            selectionAnchorIndex = null;
            selectionModel.clearAndSelect(newFileID);
        }
    }

    private class DrawableCell extends GridCell<Long> implements AutoCloseable {

        private final DrawableTile tile = new DrawableTile(GroupPane.this, controller);
        
        /**
         * This stores the last non-null file id. So that only new file ids for
         * this item are tracked. This prevents an infinite render loop. See
         * https://github.com/controlsfx/controlsfx/issues/1241 for more
         * information
         */
        private Long oldItem = null;
        
        DrawableCell() {
            setGraphic(tile);
        }

        @Override
        protected void updateItem(Long item, boolean empty) {
            super.updateItem(item, empty);
            
            if (item != null && oldItem != item) {
                tile.setFile(item);
            }
            
            if (item != null) {
                cellMap.put(item, this);    
                oldItem = item;
            } else if (oldItem != null) {
                cellMap.remove(oldItem);
            }
        }

        void resetItem() {
            tile.setFile(null);
        }

        @Override
        public void close() throws Exception {
            resetItem();
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
                            selectionAnchorIndex = grouping.get().getFileIDs().indexOf(selectionModel.lastSelectedProperty().get());
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
                            activateSlideShowViewer(selectionModel.lastSelectedProperty().get());
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
            }
        }

        private void handleArrows(KeyEvent t) {
            Long lastSelectFileId = selectionModel.lastSelectedProperty().get();

            int lastSelectedIndex = lastSelectFileId != null
                    ? grouping.get().getFileIDs().indexOf(lastSelectFileId)
                    : Optional.ofNullable(selectionAnchorIndex).orElse(0);

            final int columns = Math.max((int) Math.floor((gridView.getWidth() - 18) / (gridView.getCellWidth() + gridView.getHorizontalCellSpacing() * 2)), 1);

            final Map<KeyCode, Integer> tileIndexMap = ImmutableMap.of(UP, -columns, DOWN, columns, LEFT, -1, RIGHT, 1);

            // implement proper keyboard based multiselect
            int indexOfToBeSelectedTile = lastSelectedIndex + tileIndexMap.get(t.getCode());
            final int size = grouping.get().getFileIDs().size();
            if (0 > indexOfToBeSelectedTile) {
                //don't select past begining of group
            } else if (0 <= indexOfToBeSelectedTile && indexOfToBeSelectedTile < size) {
                //normal selection within group
                makeSelection(t.isShiftDown(), grouping.get().getFileIDs().get(indexOfToBeSelectedTile));
            } else if (indexOfToBeSelectedTile <= size - 1 + columns - (size % columns)) {
                //selection last item if selection is empty space at end of group
                makeSelection(t.isShiftDown(), grouping.get().getFileIDs().get(size - 1));
            } else {
                //don't select past end of group
            }
        }
    }

    private class MouseHandler implements EventHandler<MouseEvent> {

        private ContextMenu buildContextMenu() {
            ArrayList<MenuItem> menuItems = new ArrayList<>();

            menuItems.add(CategorizeAction.getCategoriesMenu(controller));
            try {
                menuItems.add(AddTagAction.getTagMenu(controller));
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error building tagging context menu.", ex);
            }
            Lookup.getDefault().lookupAll(ContextMenuActionsProvider.class).stream()
                    .map(ContextMenuActionsProvider::getActions)
                    .flatMap(Collection::stream)
                    .filter(Presenter.Popup.class::isInstance)
                    .map(Presenter.Popup.class::cast)
                    .map(Presenter.Popup::getPopupPresenter)
                    .map(SwingMenuItemAdapter::create)
                    .forEachOrdered(menuItems::add);

            final MenuItem extractMenuItem = new MenuItem(Bundle.GroupPane_gridViewContextMenuItem_extractFiles());

            extractMenuItem.setOnAction(actionEvent -> {
                SwingUtilities.invokeLater(() -> {
                    TopComponent etc = ImageGalleryTopComponent.getTopComponent();
                    ExtractAction.getInstance().actionPerformed(new java.awt.event.ActionEvent(etc, 0, null));
                });
            });
            menuItems.add(extractMenuItem);

            ContextMenu contextMenu = new ContextMenu(menuItems.toArray(new MenuItem[]{}));

            contextMenu.setAutoHide(
                    true);
            return contextMenu;
        }

        @Override
        public void handle(MouseEvent t) {
            switch (t.getButton()) {
                case PRIMARY:
                    if (t.getClickCount() == 1) {
                        selectionModel.clearSelection();
                        if (contextMenu != null) {
                            contextMenu.hide();
                        }
                    }
                    t.consume();
                    break;
                case SECONDARY:
                    if (isNotEmpty(selectionModel.getSelected())) {
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
    }
}
