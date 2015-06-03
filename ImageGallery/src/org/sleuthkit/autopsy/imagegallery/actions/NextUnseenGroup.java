/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-14 Basis Technology Corp.
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
import javafx.event.ActionEvent;
import javafx.scene.image.ImageView;
import org.controlsfx.control.action.Action;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.grouping.GroupViewState;

/**
 *
 */
public class NextUnseenGroup extends Action {
    
    private final ImageGalleryController controller;
    
    public NextUnseenGroup(ImageGalleryController controller) {
        super("Next Unseen group");
        this.controller = controller;
        setGraphic(new ImageView("/org/sleuthkit/autopsy/imagegallery/images/control-double.png"));
        
        controller.getGroupManager().getUnSeenGroups().addListener((Observable observable) -> {
            updateDisabledStatus();
        });
        
        setEventHandler((ActionEvent t) -> {
            Optional.ofNullable(controller.viewState())
                    .map(ObjectExpression<GroupViewState>::getValue)
                    .map(GroupViewState::getGroup)
                    .ifPresent(controller.getGroupManager()::markGroupSeen);
            
            if (controller.getGroupManager().getUnSeenGroups().size() <= 1) {
                setText("Mark Group Seen");
                setGraphic(new ImageView("/org/sleuthkit/autopsy/imagegallery/images/control-stop.png"));
                if (!controller.getGroupManager().getUnSeenGroups().isEmpty()) {
                    controller.advance(GroupViewState.tile(controller.getGroupManager().getUnSeenGroups().get(0)));
                }
            } else {
                setText("Next Unseen group");
                setGraphic(new ImageView("/org/sleuthkit/autopsy/imagegallery/images/control-double.png"));
                setDisabled(false);
                controller.advance(GroupViewState.tile(controller.getGroupManager().getUnSeenGroups().get(0)));
            }
            updateDisabledStatus();
        });
        
        updateDisabledStatus();
    }
    
    private void updateDisabledStatus() {
        disabledProperty().set(controller.getGroupManager().getUnSeenGroups().isEmpty());
    }
}
