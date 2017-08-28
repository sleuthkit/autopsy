/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-16 Basis Technology Corp.
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
import com.google.common.collect.ImmutableSet;
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
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
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
import org.openide.util.NbBundle;
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
import org.sleuthkit.autopsy.imagegallery.datamodel.Category;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.DrawableGroup;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupViewMode;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupViewState;
import org.sleuthkit.autopsy.imagegallery.gui.GuiUtils;
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
    private static final BorderWidths BORDER_WIDTHS_2 = new BorderWidths(2);
    private static final CornerRadii CORNER_RADII_2 = new CornerRadii(2);
    
    private static final DropShadow DROP_SHADOW = new DropShadow(10, Color.BLUE);
    
    private static final Timeline flashAnimation = new Timeline(new KeyFrame(Duration.millis(400), new KeyValue(DROP_SHADOW.radiusProperty(), 1, Interpolator.LINEAR)),
            new KeyFrame(Duration.millis(400), new KeyValue(DROP_SHADOW.radiusProperty(), 15, Interpolator.LINEAR))
    );
    
    private final FileIDSelectionModel selectionModel;
    private static final List<KeyCode> categoryKeyCodes = Arrays.asList(KeyCode.NUMPAD0, KeyCode.NUMPAD1, KeyCode.NUMPAD2, KeyCode.NUMPAD3, KeyCode.NUMPAD4, KeyCode.NUMPAD5,
            KeyCode.DIGIT0, KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4, KeyCode.DIGIT5);
    
    private final Back backAction;
    
    private final Forward forwardAction;
    
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
    @FXML
    private Label bottomLabel;
    @FXML
    private Label headerLabel;
    @FXML
    private Label catContainerLabel;
    @FXML
    private Label catHeadingLabel;
    
    @FXML
    private HBox catSegmentedContainer;
    @FXML
    private HBox catSplitMenuContainer;
    
    private final KeyboardHandler tileKeyboardNavigationHandler = new KeyboardHandler();
    
    private final NextUnseenGroup nextGroupAction;
    
    private final ImageGalleryController controller;
    
    private ContextMenu contextMenu;
    
    private Integer selectionAnchorIndex;
    private final UndoAction undoAction;
    private final RedoAction redoAction;
    
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
        getToggleForCategory(file.getCategory()).setSelected(true);
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
     * create the string to display in the group header
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
        
        for (Category cat : Category.values()) {
            ToggleButton toggleForCategory = getToggleForCategory(cat);
            toggleForCategory.setBorder(new Border(new BorderStroke(cat.getColor(), BorderStrokeStyle.SOLID, CORNER_RADII_2, BORDER_WIDTHS_2)));
            toggleForCategory.getStyleClass().remove("radio-button");
            toggleForCategory.getStyleClass().add("toggle-button");
            toggleForCategory.selectedProperty().addListener((ov, wasSelected, toggleSelected) -> {
                if (toggleSelected && slideShowPane != null) {
                    slideShowPane.getFileID().ifPresent(fileID -> {
                        selectionModel.clearAndSelect(fileID);
                        new CategorizeAction(controller, cat, ImmutableSet.of(fileID)).handle(null);
                    });
                }
            });
        }

        //configure flashing glow animation on next unseen group button
        flashAnimation.setCycleCount(Timeline.INDEFINITE);
        flashAnimation.setAutoReverse(true);

        //configure gridView cell properties
        DoubleBinding cellSize = controller.thumbnailSizeProperty().add(75);
        gridView.cellHeightProperty().bind(cellSize);
        gridView.cellWidthProperty().bind(cellSize);
        gridView.setCellFactory((GridView<Long> param) -> new DrawableCell());
        
        BooleanBinding isSelectionEmpty = Bindings.isEmpty(selectionModel.getSelected());
        catSelectedSplitMenu.disableProperty().bind(isSelectionEmpty);
        tagSelectedSplitMenu.disableProperty().bind(isSelectionEmpty);
        
        Platform.runLater(() -> {
            try {
                TagSelectedFilesAction followUpSelectedACtion = new TagSelectedFilesAction(controller.getTagsManager().getFollowUpTagName(), controller);
                tagSelectedSplitMenu.setText(followUpSelectedACtion.getText());
                tagSelectedSplitMenu.setGraphic(followUpSelectedACtion.getGraphic());
                tagSelectedSplitMenu.setOnAction(followUpSelectedACtion);
            } catch (TskCoreException tskCoreException) {
                LOGGER.log(Level.WARNING, "failed to load FollowUpTagName", tskCoreException); //NON-NLS
            }
            tagSelectedSplitMenu.showingProperty().addListener(showing -> {
                if (tagSelectedSplitMenu.isShowing()) {
                    List<MenuItem> selTagMenues = Lists.transform(controller.getTagsManager().getNonCategoryTagNames(),
                            tagName -> GuiUtils.createAutoAssigningMenuItem(tagSelectedSplitMenu, new TagSelectedFilesAction(tagName, controller)));
                    tagSelectedSplitMenu.getItems().setAll(selTagMenues);
                }
            });
            
        });
        
        CategorizeSelectedFilesAction cat5SelectedAction = new CategorizeSelectedFilesAction(Category.FIVE, controller);
        catSelectedSplitMenu.setOnAction(cat5SelectedAction);
        catSelectedSplitMenu.setText(cat5SelectedAction.getText());
        catSelectedSplitMenu.setGraphic(cat5SelectedAction.getGraphic());
        catSelectedSplitMenu.showingProperty().addListener(showing -> {
            if (catSelectedSplitMenu.isShowing()) {
                List<MenuItem> categoryMenues = Lists.transform(Arrays.asList(Category.values()),
                        cat -> GuiUtils.createAutoAssigningMenuItem(catSelectedSplitMenu, new CategorizeSelectedFilesAction(cat, controller)));
                catSelectedSplitMenu.getItems().setAll(categoryMenues);
            }
        });
        
        slideShowToggle.getStyleClass().remove("radio-button");
        slideShowToggle.getStyleClass().add("toggle-button");
        tileToggle.getStyleClass().remove("radio-button");
        tileToggle.getStyleClass().add("toggle-button");
        
        bottomLabel.setText(Bundle.GroupPane_bottomLabel_displayText());
        headerLabel.setText(Bundle.GroupPane_hederLabel_displayText());
        catContainerLabel.setText(Bundle.GroupPane_catContainerLabel_displayText());
        catHeadingLabel.setText(Bundle.GroupPane_catHeadingLabel_displayText());
        //show categorization controls depending on group view mode
        headerToolBar.getItems().remove(catSegmentedContainer);
        groupViewMode.addListener((ObservableValue<? extends GroupViewMode> observable, GroupViewMode oldValue, GroupViewMode newValue) -> {
            if (newValue == GroupViewMode.SLIDE_SHOW) {
                headerToolBar.getItems().remove(catSplitMenuContainer);
                headerToolBar.getItems().add(catSegmentedContainer);
            } else {
                headerToolBar.getItems().remove(catSegmentedContainer);
                headerToolBar.getItems().add(catSplitMenuContainer);
            }
        });

        //listen to toggles and update view state
        slideShowToggle.setOnAction(onAction -> activateSlideShowViewer(selectionModel.lastSelectedProperty().get()));
        tileToggle.setOnAction(onAction -> activateTileViewer());
        
        controller.viewState().addListener((observable, oldViewState, newViewState) -> setViewState(newViewState));
        
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

        //listen to tile selection and make sure it is visible in scroll area
        selectionModel.lastSelectedProperty().addListener((observable, oldFileID, newFileId) -> {
            if (groupViewMode.get() == GroupViewMode.SLIDE_SHOW
                    && slideShowPane != null) {
                slideShowPane.setFile(newFileId);
            } else {
                scrollToFileID(newFileId);
            }
        });
        
        setViewState(controller.viewState().get());
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
     * @param grouping the new grouping assigned to this group
     */
    void setViewState(GroupViewState viewState) {
        
        if (isNull(viewState) || isNull(viewState.getGroup())) {
            if (nonNull(getGroup())) {
                getGroup().getFileIDs().removeListener(filesSyncListener);
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
                    getGroup().getFileIDs().removeListener(filesSyncListener);
                }
                this.grouping.set(viewState.getGroup());
                
                getGroup().getFileIDs().addListener(filesSyncListener);
                
                final String header = getHeaderString();
                
                Platform.runLater(() -> {
                    gridView.getItems().setAll(getGroup().getFileIDs());
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
                ObservableSet<Long> selected = selectionModel.getSelected();
                if (selected.isEmpty() == false) {
                    Category cat = keyCodeToCat(t.getCode());
                    if (cat != null) {
                        new CategorizeAction(controller, cat, selected).handle(null);
                    }
                }
            }
        }
        
        private Category keyCodeToCat(KeyCode t) {
            if (t != null) {
                switch (t) {
                    case NUMPAD0:
                    case DIGIT0:
                        return Category.ZERO;
                    case NUMPAD1:
                    case DIGIT1:
                        return Category.ONE;
                    case NUMPAD2:
                    case DIGIT2:
                        return Category.TWO;
                    case NUMPAD3:
                    case DIGIT3:
                        return Category.THREE;
                    case NUMPAD4:
                    case DIGIT4:
                        return Category.FOUR;
                    case NUMPAD5:
                    case DIGIT5:
                        return Category.FIVE;
                }
            }
            return null;
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
            menuItems.add(AddTagAction.getTagMenu(controller));


            Collection<? extends ContextMenuActionsProvider> menuProviders = Lookup.getDefault().lookupAll(ContextMenuActionsProvider.class);
            
            for (ContextMenuActionsProvider provider : menuProviders) {
                for (final Action act : provider.getActions()) {
                    if (act instanceof Presenter.Popup) {
                        Presenter.Popup aact = (Presenter.Popup) act;
                        menuItems.add(SwingMenuItemAdapter.create(aact.getPopupPresenter()));
                    }
                }
            }
            final MenuItem extractMenuItem = new MenuItem(Bundle.GroupPane_gridViewContextMenuItem_extractFiles());
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
                        selectionModel.clearSelection();
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
                    if (selectionModel.getSelected().isEmpty() == false) {
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
