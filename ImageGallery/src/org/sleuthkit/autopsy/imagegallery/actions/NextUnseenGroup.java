/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-18 Basis Technology Corp.
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

import java.util.Optional;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.controlsfx.control.action.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.DrawableGroup;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupViewState;

/**
 * Marks the currently displayed group as "seen" and advances to the next unseen
 * group
 */
@NbBundle.Messages({"NextUnseenGroup.markGroupSeen=Mark Group Seen",
    "NextUnseenGroup.nextUnseenGroup=Next Unseen group"})
public class NextUnseenGroup extends Action {

    private static final String IMAGE_PATH = "/org/sleuthkit/autopsy/imagegallery/images/"; //NON-NLS
    private static final Image END_IMAGE = new Image(NextUnseenGroup.class.getResourceAsStream(
            IMAGE_PATH + "control-stop.png")); //NON-NLS
    private static final Image ADVANCE_IMAGE = new Image(NextUnseenGroup.class.getResourceAsStream(
            IMAGE_PATH + "control-double.png")); //NON-NLS

    private static final String MARK_GROUP_SEEN = Bundle.NextUnseenGroup_markGroupSeen();
    private static final String NEXT_UNSEEN_GROUP = Bundle.NextUnseenGroup_nextUnseenGroup();

    private final ImageGalleryController controller;
    private final ObservableList<DrawableGroup> unSeenGroups;

    public NextUnseenGroup(ImageGalleryController controller) {
        super(NEXT_UNSEEN_GROUP);
        setGraphic(new ImageView(ADVANCE_IMAGE));

        this.controller = controller;
        GroupManager groupManager = controller.getGroupManager();
        unSeenGroups = groupManager.getUnSeenGroups();
        unSeenGroups.addListener((Observable observable) -> this.updateButton());

        setEventHandler(event -> {    //on fx-thread
            //if there is a group assigned to the view, mark it as seen
            Optional.ofNullable(controller.viewState())
                    .map(ObservableValue<GroupViewState>::getValue)
                    .map(GroupViewState::getGroup)
                    .ifPresent(group -> {
                        groupManager.setGroupSeen(group, true)
                                .addListener(this::advanceToNextUnseenGroup, Platform::runLater);
                    });
        });
        updateButton();
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void advanceToNextUnseenGroup() {
        if (unSeenGroups.isEmpty() == false) {
            controller.advance(GroupViewState.tile(unSeenGroups.get(0)), true);
        }
        updateButton();
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void updateButton() {
        setDisabled(unSeenGroups.isEmpty());
        if (unSeenGroups.size() <= 1) {
            setText(MARK_GROUP_SEEN);
            setGraphic(new ImageView(END_IMAGE));
        } else {
            setText(NEXT_UNSEEN_GROUP);
            setGraphic(new ImageView(ADVANCE_IMAGE));
        }
    }
}
