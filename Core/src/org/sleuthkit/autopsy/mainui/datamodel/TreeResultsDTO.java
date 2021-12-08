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
     * Captures the count to be displayed in the UI.
     */
    public static class TreeDisplayCount {

        public enum Type {
            DETERMINATE,
            INDETERMINATE,
            NOT_SHOWN,
            UNSPECIFIED
        }

        private final Type type;
        private final long count;

        public static final TreeDisplayCount INDETERMINATE = new TreeDisplayCount(Type.INDETERMINATE, -1);
        public static final TreeDisplayCount NOT_SHOWN = new TreeDisplayCount(Type.NOT_SHOWN, -1);
        public static final TreeDisplayCount UNSPECIFIED = new TreeDisplayCount(Type.UNSPECIFIED, -1);

        public static TreeDisplayCount getDeterminate(long count) {
            return new TreeDisplayCount(Type.DETERMINATE, count);
        }

        private TreeDisplayCount(Type type, long count) {
            this.type = type;
            this.count = count;
        }

        public Type getType() {
            return type;
        }

        public long getCount() {
            return count;
        }

        /**
         * Returns the suffix to be added to a display string when displaying
         * this count.
         *
         * NOTE: If this code changes, regex code in
         * DirectoryTreeTopComponent.respondSelection will need to be updated as
         * well.
         *
         * @return The suffix to be added to a display string when displaying
         *         this count.
         */
        public String getDisplaySuffix() {
            switch (this.type) {
                case DETERMINATE:
                    return " (" + count + ")";
                case INDETERMINATE:
                    return " (...)";
                case NOT_SHOWN:
                default:
                    return "";
            }
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 97 * hash + Objects.hashCode(this.type);
            hash = 97 * hash + (int) (this.count ^ (this.count >>> 32));
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
            final TreeDisplayCount other = (TreeDisplayCount) obj;
            if (this.count != other.count) {
                return false;
            }
            if (this.type != other.type) {
                return false;
            }
            return true;
        }

    }

    /**
     * A result providing a category and a count for that category. Equals and
     * hashCode are based on id, type id, and type data.
     */
    public static class TreeItemDTO<T> {

        private final String displayName;
        private final String typeId;
        private final TreeDisplayCount count;
        private final T searchParams;
        private final Object id;

        /**
         * Main constructor.
         *
         * @param typeId       The id of this item type.
         * @param searchParams Search params for this tree item that can be used
         *                     to display results.
         * @param id           The id of this row. Can be any object that
         *                     implements equals and hashCode.
         * @param displayName  The display name of this row.
         * @param count        The count of results for this row or null if not
         *                     applicable.
         */
        public TreeItemDTO(String typeId, T searchParams, Object id, String displayName, TreeDisplayCount count) {
            this.typeId = typeId;
            this.id = id;
            this.displayName = displayName;
            this.count = count;
            this.searchParams = searchParams;
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
        public TreeDisplayCount getDisplayCount() {
            return count;
        }

        /**
         *
         * @return Search params for this tree item that can be used to display
         *         results.
         */
        public T getSearchParams() {
            return searchParams;
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
