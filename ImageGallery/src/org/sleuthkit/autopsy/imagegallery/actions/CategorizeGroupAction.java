/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-16 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.actions;

import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.Map;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryPreferences;
import org.sleuthkit.autopsy.imagegallery.datamodel.Category;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An action that categorizes all the files in the currently active group with
 * the given category.
 */
public class CategorizeGroupAction extends CategorizeAction {

    private final static ButtonType categorizeButtonType = new ButtonType("Overwrite", ButtonBar.ButtonData.APPLY);

    public CategorizeGroupAction(Category newCat, ImageGalleryController controller) {
        super(controller, newCat, null);
        setEventHandler(actionEvent -> {
            ObservableList<Long> fileIDs = controller.viewState().get().getGroup().getFileIDs();

            if (ImageGalleryPreferences.isGroupCategorizationWarningDisabled()) {
                //if they have preveiously disabled the warning, just go ahead and apply categories.
                addCatToFiles(ImmutableSet.copyOf(fileIDs));
            } else {
                final Map<Category, Long> catCountMap = new HashMap<>();

                for (Long fileID : fileIDs) {
                    try {
                        Category category = controller.getFileFromId(fileID).getCategory();
                        if (false == Category.ZERO.equals(category) && newCat.equals(category) == false) {
                            catCountMap.merge(category, 1L, Long::sum);
                        }
                    } catch (TskCoreException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }

                if (catCountMap.isEmpty()) {
                    //if there are not going to be any categories overwritten, skip the warning.
                    addCatToFiles(ImmutableSet.copyOf(fileIDs));
                } else {
                    VBox textFlow = new VBox();

                    for (Map.Entry<Category, Long> entry
                            : catCountMap.entrySet()) {
                        if (entry.getKey().equals(newCat) == false) {
                            if (entry.getValue() > 0) {
                                Label label = new Label(entry.getValue() + " with " + entry.getKey().getDisplayName(), entry.getKey().getGraphic());
                                label.setContentDisplay(ContentDisplay.RIGHT);
                                textFlow.getChildren().add(label);
                            }
                        }
                    }

                    CheckBox checkBox = new CheckBox("Don't show this message again");
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", categorizeButtonType, ButtonType.CANCEL);
                    Separator separator = new Separator(Orientation.HORIZONTAL);
                    separator.setPrefHeight(30);
                    separator.setValignment(VPos.BOTTOM);
                    VBox.setVgrow(separator, Priority.ALWAYS);
                    VBox vBox = new VBox(5, textFlow, separator, checkBox);
                    alert.getDialogPane().setContent(vBox);
                    alert.setHeaderText("Files in the folowing categories will have their categories overwritten: ");
                    alert.showAndWait()
                            .filter(categorizeButtonType::equals)
                            .ifPresent(button -> {
                                //if they accept the overwrites, then apply them.
                                addCatToFiles(ImmutableSet.copyOf(fileIDs));
                        if (checkBox.isSelected()) {
                            //do we want to save this even on cancel?
                                    ImageGalleryPreferences.setGroupCategorizationWarningDisabled(true);
                                }
                            });
                }
            }
        });
    }
}
