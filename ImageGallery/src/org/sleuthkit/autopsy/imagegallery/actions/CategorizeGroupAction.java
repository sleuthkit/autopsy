/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-18 Basis Technology Corp.
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
import java.util.logging.Level;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import static org.apache.commons.lang.ObjectUtils.notEqual;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryPreferences;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An action that categorizes all the files in the currently active group with
 * the given category.
 */
public class CategorizeGroupAction extends CategorizeAction {

    private final static Logger LOGGER = Logger.getLogger(CategorizeGroupAction.class.getName());

    public CategorizeGroupAction(TagName newCat, ImageGalleryController controller) {
        super(controller, newCat, null);
        setEventHandler(actionEvent -> {
            controller.getViewState().getGroup().ifPresent(group -> {
                ObservableList<Long> fileIDs = group.getFileIDs();

                if (ImageGalleryPreferences.isGroupCategorizationWarningDisabled()) {
                    //if they have preveiously disabled the warning, just go ahead and apply categories.
                    addCatToFiles(ImmutableSet.copyOf(fileIDs));
                } else {
                    final Map<TagName, Long> catCountMap = new HashMap<>();

                    for (Long fileID : fileIDs) {
                        try {
                            TagName category = controller.getFileFromID(fileID).getCategory();
                            if (category != null && newCat.equals(category) == false) {
                                catCountMap.merge(category, 1L, Long::sum);
                            }
                        } catch (TskCoreException ex) {
                            LOGGER.log(Level.SEVERE, "Failed to categorize files.", ex);
                        }
                    }

                    if (catCountMap.isEmpty()) {
                        //if there are not going to be any categories overwritten, skip the warning.
                        addCatToFiles(ImmutableSet.copyOf(fileIDs));
                    } else {
                        showConfirmationDialog(catCountMap, newCat, fileIDs);
                    }
                }
            });

        });
    }

    @NbBundle.Messages({"CategorizeGroupAction.OverwriteButton.text=Overwrite",
        "# {0} - number of files with the category", "# {1} - the name of the category",
        "CategorizeGroupAction.fileCountMessage={0} with {1}",
        "CategorizeGroupAction.dontShowAgain=Don't show this message again",
        "CategorizeGroupAction.fileCountHeader=Files in the following categories will have their categories overwritten: "})
    private void showConfirmationDialog(final Map<TagName, Long> catCountMap, TagName newCat, ObservableList<Long> fileIDs) {

        ButtonType categorizeButtonType
                = new ButtonType(Bundle.CategorizeGroupAction_OverwriteButton_text(), ButtonBar.ButtonData.APPLY);

        VBox textFlow = new VBox();

        for (Map.Entry<TagName, Long> entry : catCountMap.entrySet()) {

            if (entry != null && entry.getValue() > 0
                    && notEqual(entry.getKey(), newCat)) {
                Label label = new Label(Bundle.CategorizeGroupAction_fileCountMessage(entry.getValue(), entry.getKey().getDisplayName()),
                        getGraphic(entry.getKey()));
                label.setContentDisplay(ContentDisplay.RIGHT);
                textFlow.getChildren().add(label);
            }
        }

        CheckBox checkBox = new CheckBox(Bundle.CategorizeGroupAction_dontShowAgain());
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", categorizeButtonType, ButtonType.CANCEL); //NON-NLS
        Separator separator = new Separator(Orientation.HORIZONTAL);
        separator.setPrefHeight(30);
        separator.setValignment(VPos.BOTTOM);
        VBox.setVgrow(separator, Priority.ALWAYS);
        VBox vBox = new VBox(5, textFlow, separator, checkBox);
        alert.getDialogPane().setContent(vBox);
        alert.setHeaderText(Bundle.CategorizeGroupAction_fileCountHeader());
        alert.showAndWait()
                .filter(categorizeButtonType::equals)
                .ifPresent(button -> {
                    //if they accept the overwrites, then apply them.
                    addCatToFiles(ImmutableSet.copyOf(fileIDs));
                    if (checkBox.isSelected()) {
                        //do we want to save this even on cancel also?
                        ImageGalleryPreferences.setGroupCategorizationWarningDisabled(true);
                    }
                });
    }

    public Node getGraphic(TagName tagName) {
        return null;
    }
}
