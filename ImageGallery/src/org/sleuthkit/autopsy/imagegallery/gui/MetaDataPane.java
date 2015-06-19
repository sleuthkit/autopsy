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

import com.google.common.eventbus.Subscribe;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import static java.util.Objects.nonNull;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import static javafx.scene.layout.Region.USE_COMPUTED_SIZE;
import javafx.scene.text.Text;
import javafx.util.Pair;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.Category;
import org.sleuthkit.autopsy.imagegallery.datamodel.CategoryChangeEvent;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Shows details of the selected file.
 */
public class MetaDataPane extends AnchorPane implements DrawableView {

    private static final Logger LOGGER = Logger.getLogger(MetaDataPane.class.getName());

    private final ImageGalleryController controller;

    @Override
    public ImageGalleryController getController() {
        return controller;
    }

    @FXML
    private ImageView imageView;

    @FXML
    private TableColumn<Pair<DrawableAttribute<?>, ? extends Object>, DrawableAttribute<?>> attributeColumn;

    @FXML
    private TableView<Pair<DrawableAttribute<?>, ? extends Object>> tableView;

    @FXML
    private TableColumn<Pair<DrawableAttribute<?>, ? extends Object>, String> valueColumn;

    @FXML
    private BorderPane imageBorder;

    @FXML
    @SuppressWarnings("unchecked")
    void initialize() {
        assert attributeColumn != null : "fx:id=\"attributeColumn\" was not injected: check your FXML file 'MetaDataPane.fxml'.";
        assert imageView != null : "fx:id=\"imageView\" was not injected: check your FXML file 'MetaDataPane.fxml'.";
        assert tableView != null : "fx:id=\"tableView\" was not injected: check your FXML file 'MetaDataPane.fxml'.";
        assert valueColumn != null : "fx:id=\"valueColumn\" was not injected: check your FXML file 'MetaDataPane.fxml'.";
        getController().getTagsManager().registerListener(this);
        getController().getCategoryManager().registerListener(this);

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new Label("Select a file to show its details here."));

        attributeColumn.setCellValueFactory((param) -> new SimpleObjectProperty<>(param.getValue().getKey()));
        attributeColumn.setCellFactory((param) -> new TableCell<Pair<DrawableAttribute<?>, ? extends Object>, DrawableAttribute<?>>() {
            @Override
            protected void updateItem(DrawableAttribute<?> item, boolean empty) {
                super.updateItem(item, empty); //To change body of generated methods, choose Tools | Templates.
                if (item != null) {
                    setText(item.getDisplayName());
                    setGraphic(new ImageView(item.getIcon()));
                } else {
                    setGraphic(null);
                    setText(null);
                }
            }
        });

        attributeColumn.setPrefWidth(USE_COMPUTED_SIZE);

        valueColumn.setCellValueFactory((p) -> {
            return (p.getValue().getKey() == DrawableAttribute.TAGS)
                    ? new SimpleStringProperty(((Collection<TagName>) p.getValue().getValue()).stream()
                            .map(TagName::getDisplayName)
                            .filter(Category::isNotCategoryName)
                            .collect(Collectors.joining(" ; ")))
                    : new SimpleStringProperty(StringUtils.join((Iterable<?>) p.getValue().getValue(), " ; "));
        });
        valueColumn.setPrefWidth(USE_COMPUTED_SIZE);
        valueColumn.setCellFactory((p) -> new TableCell<Pair<DrawableAttribute<?>, ? extends Object>, String>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (!isEmpty()) {
                    Text text = new Text(item);
                    text.wrappingWidthProperty().bind(getTableColumn().widthProperty());
                    setGraphic(text);
                } else {
                    setGraphic(null);
                }
            }
        });
        tableView.getColumns().setAll(Arrays.asList(attributeColumn, valueColumn));

        //listen for selection change
        controller.getSelectionModel().lastSelectedProperty().addListener((observable, oldFileID, newFileID) -> {
            setFile(newFileID);
        });
    }

    volatile private Optional<DrawableFile<?>> fileOpt = Optional.empty();

    volatile private Optional<Long> fileIDOpt = Optional.empty();

    @Override
    public Optional<Long> getFileID() {
        return fileIDOpt;
    }

    @Override
    public Optional<DrawableFile<?>> getFile() {
        if (fileIDOpt.isPresent()) {
            if (fileOpt.isPresent() && fileOpt.get().getId() == fileIDOpt.get()) {
                return fileOpt;
            } else {
                try {
                    fileOpt = Optional.of(ImageGalleryController.getDefault().getFileFromId(fileIDOpt.get()));
                } catch (TskCoreException ex) {
                    Logger.getAnonymousLogger().log(Level.WARNING, "failed to get DrawableFile for obj_id" + fileIDOpt.get(), ex);
                    fileOpt = Optional.empty();
                }
                return fileOpt;
            }
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void setFile(Long newFileID) {

        if (fileIDOpt.isPresent()) {
            if (Objects.equals(newFileID, fileIDOpt.get()) == false) {
                setFileHelper(newFileID);
            }
        } else {
            if (nonNull(newFileID)) {
                setFileHelper(newFileID);
            }
        }
        setFileHelper(newFileID);
    }

    private void setFileHelper(Long newFileID) {
        fileIDOpt = Optional.of(newFileID);
        if (newFileID == null) {
            Platform.runLater(() -> {
                imageView.setImage(null);
                tableView.getItems().clear();
                getCategoryBorderRegion().setBorder(null);

            });
        } else {
            updateUI();
        }
    }

    public MetaDataPane(ImageGalleryController controller) {
        this.controller = controller;

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("MetaDataPane.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public void updateUI() {
        getFile().ifPresent(file -> {
            final Image icon = file.getThumbnail();
            final ObservableList<Pair<DrawableAttribute<?>, ? extends Object>> attributesList = file.getAttributesList();

            Platform.runLater(() -> {
                imageView.setImage(icon);
                tableView.getItems().setAll(attributesList);
            });

            updateCategoryBorder();
        });

    }

    @Override
    public Region getCategoryBorderRegion() {
        return imageBorder;
    }

    /** {@inheritDoc } */
    @Subscribe
    @Override
    public void handleCategoryChanged(CategoryChangeEvent evt) {
        getFileID().ifPresent(fileID -> {
            if (evt.getFileIDs().contains(fileID)) {
                updateUI();
            }
        });
    }

    @Override
    public void handleTagAdded(ContentTagAddedEvent evt) {
        handleTagChanged(evt.getAddedTag().getContent().getId());
    }

    @Override
    public void handleTagDeleted(ContentTagDeletedEvent evt) {
        handleTagChanged(evt.getDeletedTag().getContent().getId());
    }

    private void handleTagChanged(Long tagFileID) {
        getFileID().ifPresent(fileID -> {
            if (Objects.equals(tagFileID, fileID)) {
                updateUI();
            }
        });
    }
}
