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

import com.google.common.util.concurrent.MoreExecutors;
import java.util.Optional;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.apache.commons.collections4.CollectionUtils;
import org.controlsfx.control.action.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.DrawableGroup;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupViewState;

/**
 * Marks the currently displayed group as "seen" and advances to the next unseen
 * group
 */
@NbBundle.Messages({
    "NextUnseenGroup.markGroupSeen=Mark Group Seen",
    "NextUnseenGroup.nextUnseenGroup=Next Unseen group",
    "NextUnseenGroup.allGroupsSeen=All Groups Have Been Seen"})
public class NextUnseenGroup extends Action {

    private static final String IMAGE_PATH = "/org/sleuthkit/autopsy/imagegallery/images/"; //NON-NLS
    private static final Image END_IMAGE = new Image(NextUnseenGroup.class.getResourceAsStream(
            IMAGE_PATH + "control-stop.png")); //NON-NLS
    private static final Image ADVANCE_IMAGE = new Image(NextUnseenGroup.class.getResourceAsStream(
            IMAGE_PATH + "control-double.png")); //NON-NLS

    private static final String MARK_GROUP_SEEN = Bundle.NextUnseenGroup_markGroupSeen();
    private static final String NEXT_UNSEEN_GROUP = Bundle.NextUnseenGroup_nextUnseenGroup();
    private static final String ALL_GROUPS_SEEN = Bundle.NextUnseenGroup_allGroupsSeen();

    private final ImageGalleryController controller;
    private final ObservableList<DrawableGroup> unSeenGroups;
    private final GroupManager groupManager;

    public NextUnseenGroup(ImageGalleryController controller) {
        super(NEXT_UNSEEN_GROUP);
        setGraphic(new ImageView(ADVANCE_IMAGE));

        this.controller = controller;
        groupManager = controller.getGroupManager();
        unSeenGroups = groupManager.getUnSeenGroups();
        unSeenGroups.addListener((Observable observable) -> updateButton());
        controller.viewStateProperty().addListener((Observable observable) -> updateButton());

        setEventHandler(event -> {    //on fx-thread
            //if there is a group assigned to the view, mark it as seen
            Optional.ofNullable(controller.getViewState())
                    .flatMap(GroupViewState::getGroup)
                    .ifPresent(group -> {
                        setDisabled(true);
                        groupManager.markGroupSeen(group, true)
                                .addListener(this::advanceToNextUnseenGroup, MoreExecutors.newDirectExecutorService());
                    });
        });
        updateButton();
    }

    private void advanceToNextUnseenGroup() {
        synchronized (groupManager) {
            if (CollectionUtils.isNotEmpty(unSeenGroups)) {
                controller.advance(GroupViewState.tile(unSeenGroups.get(0)));
            }

            updateButton();
        }
    }

    private void updateButton() {
        int size = unSeenGroups.size();

        if (size < 1) {
            //there are no unseen groups.
            Platform.runLater(() -> {
                setDisabled(true);
                setText(ALL_GROUPS_SEEN);
                setGraphic(null);
            });
        } else {
            DrawableGroup get = unSeenGroups.get(0);
            DrawableGroup orElse = Optional.ofNullable(controller.getViewState()).flatMap(GroupViewState::getGroup).orElse(null);
            boolean equals = get.equals(orElse);
            if (size == 1 & equals) {
                //The only unseen group is the one that is being viewed.
                Platform.runLater(() -> {
                    setDisabled(false);
                    setText(MARK_GROUP_SEEN);
                    setGraphic(new ImageView(END_IMAGE));
                });
            } else {
                //there are more unseen groups.
                Platform.runLater(() -> {
                    setDisabled(false);
                    setText(NEXT_UNSEEN_GROUP);
                    setGraphic(new ImageView(ADVANCE_IMAGE));
                });
            }
        }
    }
}
