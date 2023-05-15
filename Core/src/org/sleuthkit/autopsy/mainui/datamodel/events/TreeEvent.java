/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.datamodel.events;

import java.util.Objects;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;

/**
 * An event to signal that an item in the tree has been 
 * added or changed. 
 */
public class TreeEvent implements DAOEvent {

    private final TreeItemDTO<?> itemRecord;  // the updated item
    private final boolean refreshRequired;  // true if tree should request new data from DAO

    /**
     * @param itemRecord The updated item
     * @param rereshRequired True if the tree should go to the DAO for updated data
     */
    public TreeEvent(TreeItemDTO<?> itemRecord, boolean refreshRequired) {
        this.itemRecord = itemRecord;
        this.refreshRequired = refreshRequired;
    }

    public TreeItemDTO<?> getItemRecord() {
        return itemRecord;
    }

    public boolean isRefreshRequired() {
        return refreshRequired;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + Objects.hashCode(this.itemRecord);
        hash = 89 * hash + (this.refreshRequired ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TreeEvent other = (TreeEvent) obj;
        if (this.refreshRequired != other.refreshRequired) {
            return false;
        }
        if (!Objects.equals(this.itemRecord, other.itemRecord)) {
            return false;
        }
        return true;
    }

    

    @Override
    public Type getType() {
        return Type.TREE;
    }
}
