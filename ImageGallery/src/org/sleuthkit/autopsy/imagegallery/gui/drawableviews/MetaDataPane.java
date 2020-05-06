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
package org.sleuthkit.autopsy.imagegallery.gui.drawableviews;

import com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import static java.util.Collections.singletonMap;
import java.util.List;
import java.util.Objects;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.util.Pair;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.FXMLConstructor;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.CategoryManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.datamodel.TagName;

/**
 * Shows details of the selected file.
 */
@NbBundle.Messages({"MetaDataPane.tableView.placeholder=Select a file to show its details here.",
    "MetaDataPane.copyMenuItem.text=Copy",
    "MetaDataPane.titledPane.displayName=Details",
    "MetaDataPane.attributeColumn.headingName=Attribute",
    "MetaDataPane.valueColumn.headingName=Value"})
public class MetaDataPane extends DrawableUIBase {

    private static final Logger logger = Logger.getLogger(MetaDataPane.class.getName());

    private static final KeyCodeCombination COPY_KEY_COMBINATION = new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN);

    @FXML
    private TitledPane titledPane;

    @FXML
    private TableColumn<Pair<DrawableAttribute<?>, Collection<?>>, DrawableAttribute<?>> attributeColumn;

    @FXML
    private TableView<Pair<DrawableAttribute<?>, Collection<?>>> tableView;

    @FXML
    private TableColumn<Pair<DrawableAttribute<?>, Collection<?>>, String> valueColumn;

    private final MenuItem copyMenuItem = new MenuItem(Bundle.MetaDataPane_copyMenuItem_text());
    private final ContextMenu contextMenu = new ContextMenu(copyMenuItem);

    public MetaDataPane(ImageGalleryController controller) {
        super(controller);
        FXMLConstructor.construct(this, "MetaDataPane.fxml"); //NON-NLS
    }

    @FXML
    void initialize() {
        assert attributeColumn != null : "fx:id=\"attributeColumn\" was not injected: check your FXML file 'MetaDataPane.fxml'.";
        assert imageView != null : "fx:id=\"imageView\" was not injected: check your FXML file 'MetaDataPane.fxml'.";
        assert tableView != null : "fx:id=\"tableView\" was not injected: check your FXML file 'MetaDataPane.fxml'.";
        assert valueColumn != null : "fx:id=\"valueColumn\" was not injected: check your FXML file 'MetaDataPane.fxml'.";
        getController().getTagsManager().registerListener(this);
        getController().getCategoryManager().registerListener(this);

        //listen for selection change
        getController().getSelectionModel().lastSelectedProperty().addListener((observable, oldFileID, newFileID) -> {
            setFile(newFileID);
        });

        copyMenuItem.setAccelerator(COPY_KEY_COMBINATION);
        copyMenuItem.setOnAction(actionEvent -> copyValueToClipBoard());

        tableView.setContextMenu(contextMenu);
        tableView.setOnKeyPressed((KeyEvent event) -> {
            if (COPY_KEY_COMBINATION.match(event)) {
                contextMenu.hide();
                copyMenuItem.fire();
                event.consume();
            }
        });

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new Label(Bundle.MetaDataPane_tableView_placeholder()));
        tableView.getColumns().setAll(Arrays.asList(attributeColumn, valueColumn));

        attributeColumn.setPrefWidth(USE_COMPUTED_SIZE);
        attributeColumn.setText(Bundle.MetaDataPane_attributeColumn_headingName());
        attributeColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getKey()));
        attributeColumn.setCellFactory(param -> new TableCell<Pair<DrawableAttribute<?>, Collection<?>>, DrawableAttribute<?>>() {
            @Override
            protected void updateItem(DrawableAttribute<?> item, boolean empty) {
                super.updateItem(item, empty);
                if (isNull(item) || empty) {
                    setGraphic(null);
                    setText(null);
                } else {
                    setText(item.getDisplayName());
                    setGraphic(new ImageView(item.getIcon()));
                }
            }
        });

        valueColumn.setPrefWidth(USE_COMPUTED_SIZE);
        valueColumn.setText(Bundle.MetaDataPane_valueColumn_headingName());
        valueColumn.setCellValueFactory(p -> new SimpleStringProperty(getValueDisplayString(p.getValue())));
        valueColumn.setCellFactory(p -> new TableCell<Pair<DrawableAttribute<?>, Collection<?>>, String>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (isNull(item) || empty) {
                    setGraphic(null);
                } else {
                    Text text = new Text(item);
                    text.wrappingWidthProperty().bind(getTableColumn().widthProperty());
                    setGraphic(text);
                }
            }
        });

        titledPane.setText(Bundle.MetaDataPane_titledPane_displayName());
    }

    /**
     * Returns the display string for the given pair.
     *
     * @param p A DrawableAttribute and its collection.
     *
     * @return The string to display.
     */
    @SuppressWarnings("unchecked")
    private String getValueDisplayString(Pair<DrawableAttribute<?>, Collection<?>> p) {
        if (p.getKey() == DrawableAttribute.TAGS || p.getKey() == DrawableAttribute.CATEGORY) {
            return getTagDisplayNames((Collection<TagName>) p.getValue(), p.getKey());
        } else {
            return p.getValue().stream()
                    .map(value -> Objects.toString(value, ""))
                    .collect(Collectors.joining(" ; "));

        }
    }

    /**
     * Create the list of TagName displayNames for either Tags or Categories.
     *
     * @param tagNameList List of TagName values
     * @param attribute   A DrawableAttribute value either CATEGORY or TAGS
     *
     * @return A list of TagNames separated by ; or an empty string.
     */
    private String getTagDisplayNames(Collection<TagName> tagNameList, DrawableAttribute<?> attribute) {
        String displayStr = "";
        CategoryManager controller = getController().getCategoryManager();
        List<String> nameList = new ArrayList<>();
        if (tagNameList != null && !tagNameList.isEmpty()) {
            for (TagName tagName : tagNameList) {
                if ((attribute == DrawableAttribute.CATEGORY && controller.isCategoryTagName(tagName))
                        || (attribute == DrawableAttribute.TAGS && !controller.isCategoryTagName(tagName))) {
                    nameList.add(tagName.getDisplayName());
                }
            }
            displayStr = String.join(";", nameList);
        }

        return displayStr;
    }

    @Override
    synchronized protected void setFileHelper(Long newFileID) {
        setFileIDOpt(Optional.ofNullable(newFileID));
        disposeContent();
        if (nonNull(newFileID)) {
            updateAttributesTable();
            updateCategory();
            updateContent();
        }
    }

    @Override
    protected synchronized void disposeContent() {
        super.disposeContent();
        Platform.runLater(() -> {
            tableView.getItems().clear();
            getCategoryBorderRegion().setBorder(null);
        });
    }

    @Override
    Task<Image> newReadImageTask(DrawableFile file) {
        return getController().getThumbsCache().getThumbnailTask(file);
    }

    public void updateAttributesTable() {
        getFile().ifPresent(file -> {
            final List<Pair<DrawableAttribute<?>, Collection<?>>> attributesList = file.getAttributesList();
            Platform.runLater(() -> {
                tableView.getItems().clear();
                tableView.getItems().setAll(attributesList);
            });
        });
    }

    @Override
    public Region getCategoryBorderRegion() {
        return imageBorder;
    }

    @Subscribe
    @Override
    public void handleCategoryChanged(CategoryManager.CategoryChangeEvent evt) {
        getFileID().ifPresent(fileID -> {
            if (evt.getFileIDs().contains(fileID)) {
                updateCategory();
                updateAttributesTable();
            }
        });
    }

    @Subscribe
    @Override
    public void handleTagAdded(ContentTagAddedEvent evt) {
        getFileID().ifPresent((fileID) -> {
            if (Objects.equals(evt.getAddedTag().getContent().getId(), fileID)) {
                updateAttributesTable();
            }
        });
    }

    @Override
    public void handleTagDeleted(ContentTagDeletedEvent evt) {
        getFileID().ifPresent((fileID) -> {
            if (Objects.equals(evt.getDeletedTagInfo().getContentID(), fileID)) {
                updateAttributesTable();
            }
        });
    }

    private void copyValueToClipBoard() {
        Pair<DrawableAttribute<?>, Collection<?>> selectedItem = tableView.getSelectionModel().getSelectedItem();
        if (nonNull(selectedItem)) {
            Clipboard.getSystemClipboard().setContent(
                    singletonMap(DataFormat.PLAIN_TEXT, getValueDisplayString(selectedItem))
            );
        }
    }
}
