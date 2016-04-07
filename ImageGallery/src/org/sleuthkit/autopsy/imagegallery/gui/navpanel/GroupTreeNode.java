/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-16 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.gui.navpanel;

import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.DrawableGroup;

/**
 *
 */
class GroupTreeNode {

    private final String path;
    private DrawableGroup group;

    public String getPath() {
        return path;
    }

    public DrawableGroup getGroup() {
        return group;
    }

    GroupTreeNode(String path, DrawableGroup group) {
        this.path = path;
        this.group = group;
    }

    void setGroup(DrawableGroup g) {
        group = g;
    }
}
