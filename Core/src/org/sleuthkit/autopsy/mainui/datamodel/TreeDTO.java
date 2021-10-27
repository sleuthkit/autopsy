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
package org.sleuthkit.autopsy.mainui.datamodel;

import java.util.List;
import java.util.Objects;

/**
 * A list of items to display in the tree.
 */
public class TreeDTO<T> {

    private final List<TreeItemDTO<T>> items;

    /**
     * Main constructor.
     *
     * @param items The items to display.
     */
    public TreeDTO(List<TreeItemDTO<T>> items) {
        this.items = items;
    }

    /**
     * @return The items to display.
     */
    public List<TreeItemDTO<T>> getItems() {
        return items;
    }

    /**
     * A result providing a category and a count for that category. Equals and
     * hashCode are based on id, type id, and type data.
     */
    public static class TreeItemDTO<T> {

        private final String displayName;
        private final String typeId;
        private final Long count;
        private final T typeData;
        private final long id;

        /**
         * Main constructor.
         *
         * @param typeId      The id of this item type.
         * @param typeData    Data for this particular row's type (i.e.
         *                    BlackboardArtifact.Type for counts of a particular
         *                    artifact type).
         * @param id          The numerical id of this row.
         * @param displayName The display name of this row.
         * @param count       The count of results for this row or null if not
         *                    applicable.
         */
        public TreeItemDTO(String typeId, T typeData, long id, String displayName, Long count) {
            this.typeId = typeId;
            this.id = id;
            this.displayName = displayName;
            this.count = count;
            this.typeData = typeData;
        }

        /**
         * @return The display name of this row.
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * @return The count of results for this row or null if not applicable.
         */
        public Long getCount() {
            return count;
        }

        /**
         *
         * @return Data for this particular row's type (i.e.
         *         BlackboardArtifact.Type for counts of a particular artifact
         *         type).
         */
        public T getTypeData() {
            return typeData;
        }

        /**
         * @return The numerical id for this item.
         */
        public long getId() {
            return id;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 29 * hash + Objects.hashCode(this.typeId);
            hash = 29 * hash + Objects.hashCode(this.typeData);
            hash = 29 * hash + (int) (this.id ^ (this.id >>> 32));
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
            final TreeItemDTO<?> other = (TreeItemDTO<?>) obj;
            if (this.id != other.id) {
                return false;
            }
            if (!Objects.equals(this.typeId, other.typeId)) {
                return false;
            }
            if (!Objects.equals(this.typeData, other.typeData)) {
                return false;
            }
            return true;
        }

        
    }
}
