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
package org.sleuthkit.autopsy.imagegallery.actions;

import java.util.Optional;
import javafx.beans.Observable;
import javafx.beans.binding.ObjectExpression;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.controlsfx.control.action.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupViewState;

/**
 * Marks the currently fisplayed group as "seen" and advances to the next unseen
 * group
 */
@NbBundle.Messages({"NextUnseenGroup.markGroupSeen=Mark Group Seen",
        "NextUnseenGroup.nextUnseenGroup=Next Unseen group"})
public class NextUnseenGroup extends Action {

    private static final Image END =
            new Image(NextUnseenGroup.class.getResourceAsStream("/org/sleuthkit/autopsy/imagegallery/images/control-stop.png")); //NON-NLS
    private static final Image ADVANCE =
            new Image(NextUnseenGroup.class.getResourceAsStream("/org/sleuthkit/autopsy/imagegallery/images/control-double.png")); //NON-NLS

    private static final String MARK_GROUP_SEEN = Bundle.NextUnseenGroup_markGroupSeen();
    private static final String NEXT_UNSEEN_GROUP = Bundle.NextUnseenGroup_nextUnseenGroup();

    private final ImageGalleryController controller;

    public NextUnseenGroup(ImageGalleryController controller) {
        super(NEXT_UNSEEN_GROUP);
        this.controller = controller;
        setGraphic(new ImageView(ADVANCE));

        //TODO: do we need both these listeners?
        controller.getGroupManager().getAnalyzedGroups().addListener((Observable observable) -> {
            updateButton();

        });
        controller.getGroupManager().getUnSeenGroups().addListener((Observable observable) -> {
            updateButton();
        });

        setEventHandler((ActionEvent t) -> {
            //fx-thread
            //if there is a group assigned to the view, mark it as seen
            Optional.ofNullable(controller.viewState())
                    .map(ObjectExpression<GroupViewState>::getValue)
                    .map(GroupViewState::getGroup)
                    .ifPresent(group -> controller.getGroupManager().markGroupSeen(group, true));
            controller.execute(new Task<Void>() {

                @Override
                protected Void call() throws Exception {
                    if (false == controller.getGroupManager().getUnSeenGroups().isEmpty()) {
                        controller.advance(GroupViewState.tile(controller.getGroupManager().getUnSeenGroups().get(0)), true);
                    }
                    return null;
                }

                @Override
                protected void succeeded() {
                    super.succeeded();
                    updateButton();
                }
            });
        });

        updateButton();
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void updateButton() {
        setDisabled(controller.getGroupManager().getUnSeenGroups().isEmpty());
        if (controller.getGroupManager().getUnSeenGroups().size() <= 1) {
            setText(MARK_GROUP_SEEN);
            setGraphic(new ImageView(END));
        } else {
            setText(NEXT_UNSEEN_GROUP);
            setGraphic(new ImageView(ADVANCE));
        }
    }
}
