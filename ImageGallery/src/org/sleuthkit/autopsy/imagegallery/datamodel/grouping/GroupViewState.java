
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
 *
 */
public class GroupViewState {

    private final DrawableGroup group;

    private final GroupViewMode mode;

    private final Optional<Long> slideShowfileID;

    public DrawableGroup getGroup() {
        return group;
    }

    public GroupViewMode getMode() {
        return mode;
    }

    public Optional<Long> getSlideShowfileID() {
        return slideShowfileID;
    }

    private GroupViewState(DrawableGroup g, GroupViewMode mode, Long slideShowfileID) {
        this.group = g;
        this.mode = mode;
        this.slideShowfileID = Optional.ofNullable(slideShowfileID);
    }

    public static GroupViewState tile(DrawableGroup g) {
        return new GroupViewState(g, GroupViewMode.TILE, null);
    }

    public static GroupViewState slideShow(DrawableGroup g, Long fileID) {
        return new GroupViewState(g, GroupViewMode.SLIDE_SHOW, fileID);
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
        if (!Objects.equals(this.slideShowfileID, other.slideShowfileID)) {
            return false;
        }
        return true;
    }

}
