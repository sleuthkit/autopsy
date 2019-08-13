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

import com.google.common.util.concurrent.ListeningExecutorService;
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
import org.sleuthkit.autopsy.imagegallery.utils.TaskUtils;

/**
 * Marks the currently displayed group as "seen" and advances to the next unseen
 * group
 */
@NbBundle.Messages({
    "NextUnseenGroup.markGroupSeen=Mark Group Seen",
    "NextUnseenGroup.nextUnseenGroup=Next Unseen Group",
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
    
    private boolean isLoading = false; // set to true when we are marking current group as seen and loading new
    
    private final ListeningExecutorService exec = TaskUtils.getExecutorForClass(NextUnseenGroup.class);


    public NextUnseenGroup(ImageGalleryController controller) {
        super(NEXT_UNSEEN_GROUP);
        setGraphic(new ImageView(ADVANCE_IMAGE));

        this.controller = controller;
        groupManager = controller.getGroupManager();
        
        // Get reference to the list of unseen groups, that GroupManager will continue to manage
        unSeenGroups = groupManager.getUnSeenGroupsForCurrentGroupBy();
        unSeenGroups.addListener((Observable observable) -> unSeenGroupListener());
        controller.viewStateProperty().addListener((Observable observable) -> updateButton());

        setEventHandler(event -> {    //on fx-thread
            isLoading = true; // make sure button stays disabled until we are done loading
            setDisabled(true);
            
            //if there is a group assigned to the view, mark it as seen and move on to the next one
            GroupViewState viewState = controller.getViewState();
            if (viewState != null) {
                Optional<DrawableGroup> group = viewState.getGroup();
            
                if (group.isPresent()) {
                    // NOTE: We need to wait for current group to be marked as seen because the 'advance' 
                    // method grabs the top of the unseen list
                    groupManager.markGroupSeen(group.get())
                        .addListener(this::advanceToNextUnseenGroup, MoreExecutors.newDirectExecutorService());
                    return;
                }
            }
            
            // otherwise, just move on to the next one
            exec.submit(this::advanceToNextUnseenGroup);
        });
        
        // initial button state
        updateButton();
    }

    /**
     * Listener that updates UI based on changes to the unseen group list
     */
    private void unSeenGroupListener() {
        // set the group if there is no visible group.
        // NOTE: it could be argued that this should be done in another listner
        if (controller.getViewState() == null) {
            advanceToNextUnseenGroup();
        // do not update the button if it is supposed to be disabled during loading of the next group
        } else if (isLoading == false) {
            // NOTE: should we get a lock on groupManager here like advanceToNextUnseenGroup does?
            updateButton();
        }
    }
    
    // update UI based on button being pressed
    private void advanceToNextUnseenGroup() {
        synchronized (groupManager) {
            if (CollectionUtils.isNotEmpty(unSeenGroups)) {
                // NOTE: We keep the group in the unSeenGroup list until the user presses the 
                // button again mark it as seen
                controller.advance(GroupViewState.createTile(unSeenGroups.get(0)));
            }
            
            updateButton();
        }
    }

    /**
     * Update button based on currently displayed group and queues.
     */
    private void updateButton() {

        isLoading = false;
        
        int unSeenSize = unSeenGroups.size();

        // NOTE: The currently displayed group is still in the unSeenGroups list until the user presses
        // the button again and then we'll mark it as seen.
        
        // disable button if no unseen groups
        if (unSeenSize < 1) {
            Platform.runLater(() -> {
                setDisabled(true);
                setText(ALL_GROUPS_SEEN);
                setGraphic(null);
            });
        } else {
            DrawableGroup groupOnList = unSeenGroups.get(0);
            DrawableGroup groupInView = Optional.ofNullable(controller.getViewState()).flatMap(GroupViewState::getGroup).orElse(null);

            //The only unseen group is the one that is being viewed.
            if (unSeenSize == 1 & groupOnList.equals(groupInView)) {
                Platform.runLater(() -> {
                    setDisabled(true);
                    setText(MARK_GROUP_SEEN);
                    setGraphic(new ImageView(END_IMAGE));
                });
            } else {
                //there are more unseen groups after this one
                Platform.runLater(() -> {
                    setDisabled(false);
                    setText(NEXT_UNSEEN_GROUP);
                    setGraphic(new ImageView(ADVANCE_IMAGE));
                });
            }
        }
    }
}
