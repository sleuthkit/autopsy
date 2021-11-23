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

/**
 * A list of items to display in the tree.
 */
public class TreeResultsDTO<T> {

    private final List<TreeItemDTO<T>> items;

    /**
     * Main constructor.
     *
     * @param items The items to display.
     */
    public TreeResultsDTO(List<TreeItemDTO<T>> items) {
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
        private final Object id;

        /**
         * Main constructor.
         *
         * @param typeId      The id of this item type.
         * @param typeData    Data for this particular row's type (i.e.
         *                    BlackboardArtifact.Type for counts of a particular
         *                    artifact type).
         * @param id          The id of this row. Can be any object that
         *                    implements equals and hashCode.
         * @param displayName The display name of this row.
         * @param count       The count of results for this row or null if not
         *                    applicable.
         */
        public TreeItemDTO(String typeId, T typeData, Object id, String displayName, Long count) {
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
         * @return The id of this row. Can be any object that implements equals
         *         and hashCode.
         */
        public Object getId() {
            return id;
        }

        /**
         * @return The id of this item type.
         */
        public String getTypeId() {
            return typeId;
        }
        
        
    }
}
