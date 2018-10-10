
/* Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.imagegallery.datamodel.grouping;

import java.util.Objects;
import java.util.Optional;

/**
 * Encapsulate information about the state of the group section of the UI.
 */
public final class GroupViewState {

    // what group is being represented
    private final DrawableGroup group;

    // Tile, Slide show, etc.
    private final GroupViewMode mode;

    private final Optional<Long> slideShowfileID;

    private GroupViewState(DrawableGroup group, GroupViewMode mode, Long slideShowfileID) {
        this.group = group;
        this.mode = mode;
        this.slideShowfileID = Optional.ofNullable(slideShowfileID);
    }

    public static GroupViewState createTile(DrawableGroup group) {
        return new GroupViewState(group, GroupViewMode.TILE, null);
    }

    public static GroupViewState createSlideShow(DrawableGroup group, Long fileID) {
        return new GroupViewState(group, GroupViewMode.SLIDE_SHOW, fileID);
    }
    
    public Optional<DrawableGroup> getGroup() {
        return Optional.ofNullable(group);
    }

    public GroupViewMode getMode() {
        return mode;
    }

    public Optional<Long> getSlideShowfileID() {
        return slideShowfileID;
    }

    

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + Objects.hashCode(this.group);
        hash = 17 * hash + Objects.hashCode(this.mode);
        hash = 17 * hash + Objects.hashCode(this.slideShowfileID);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final GroupViewState other = (GroupViewState) obj;
        if (!Objects.equals(this.group, other.group)) {
            return false;
        }
        if (this.mode != other.mode) {
            return false;
        }
        return Objects.equals(this.slideShowfileID, other.slideShowfileID);
    }

}
