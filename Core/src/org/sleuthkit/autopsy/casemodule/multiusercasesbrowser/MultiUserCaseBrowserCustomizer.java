/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.multiusercasesbrowser;

import java.util.List;
import javax.swing.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;

/**
 * The interface for defining a customizer for a multi-user case browser panel
 * that presents a tabular view of the multi-user cases known to the
 * coordination service.
 */
public interface MultiUserCaseBrowserCustomizer {

    /**
     * Gets the columns the tabular view in the browser should display. The
     * columns will be displayed in the order in which they appear in the list.
     * NOTE THAT THE DISPLAY_NAME COLUMN IS ADDED AS THE FIRST COLUMN
     * AUTOMATICALLY AND SHOULD NOT BE INCLUDED IN THIS LIST.
     *
     * @return A Column object.
     */
    List<Column> getColumns();

    /**
     * Gets a specification of the columns, if any, that define the default
     * sorting of the MultiUserCaseNodes in the browser.
     *
     * @return A list, possibly empty, of SortColumn objects.
     */
    List<SortColumn> getSortColumns();

    /**
     * Whether or not the browser should allow multiple selection of the
     * MultiUserCaseNodes displayed in the browser.
     *
     * @return True or false.
     */
    boolean allowMultiSelect();

    /**
     * Gets the context menu Actions for the MultiUserCaseNodes diplayed in the
     * browser. Can include actions that work with multiple node selection.
     *
     * @param nodeData The coordination service node data for a given
     *                 MultiUserCaseNode. Ignored for multi-select actions.
     *
     * @return A list of Actions.
     */
    List<Action> getActions(CaseNodeData nodeData);

    /**
     * Gets the preferred action for the MultiUserCaseNodes displayed in the
     * browser, i.e., the action to be performed when a node is double clicked.
     *
     * @param nodeData The coordination service node data for a given
     *                 MultiUserCaseNode.
     *
     * @return The preferred action.
     */
    Action getPreferredAction(CaseNodeData nodeData);

    /**
     * A specification of the sorting details for a column in the browser's
     * OutlineView.
     */
    final class SortColumn {

        private final Column column;
        private final boolean sortAscending;
        private final int sortRank;

        /**
         * Constucts a specification of the sorting details for a column in the
         * browser's OutlineView.
         *
         * @param column
         * @param sortAscending
         * @param sortRank
         */
        public SortColumn(Column column, boolean sortAscending, int sortRank) {
            this.column = column;
            this.sortAscending = sortAscending;
            this.sortRank = sortRank;
        }

        /**
         * Gets the column to be sorted.
         *
         * @return The column.
         */
        public Column column() {
            return column;
        }

        /**
         * Indicates whether or not the sort should be ascending.
         *
         * @return True or false.
         */
        public boolean sortAscending() {
            return sortAscending;
        }

        /**
         * Gets the rank of the sort, i.e., should it be the first column to be
         * sorted, the second column, etc.
         *
         * @return The sort rank.
         */
        public int sortRank() {
            return sortRank;
        }

    }

    /**
     * An enumeration of the columns that can be added to the browser's tabular
     * view. NOTE THAT THE DISPLAY_NAME COLUMN IS ADDED AUTOMATICALLY BY THE
     * BROWSER AND ALL OTHER COLUMNS ARE OPTIONAL.
     */
    @NbBundle.Messages({
        "MultiUserCaseBrowserCustomizer.column.displayName=Name",
        "MultiUserCaseBrowserCustomizer.column.createTime=Create Time",
        "MultiUserCaseBrowserCustomizer.column.directory=Directory",
        "MultiUserCaseBrowserCustomizer.column.lastAccessTime=Last Access Time",
        "MultiUserCaseBrowserCustomizer.column.manifestFileZNodesDeleteStatus=Manifest Znodes Deleted",
        "MultiUserCaseBrowserCustomizer.column.dataSourcesDeleteStatus=Data Sources Deleted",
        "MultiUserCaseBrowserCustomizer.column.textIndexDeleteStatus=Text Index Deleted",
        "MultiUserCaseBrowserCustomizer.column.caseDbDeleteStatus=Case Database Deleted",
        "MultiUserCaseBrowserCustomizer.column.caseDirDeleteStatus=Case Directory Deleted"
    })
    public enum Column {
        DISPLAY_NAME(Bundle.MultiUserCaseBrowserCustomizer_column_displayName()),
        CREATE_DATE(Bundle.MultiUserCaseBrowserCustomizer_column_createTime()),
        DIRECTORY(Bundle.MultiUserCaseBrowserCustomizer_column_directory()),
        LAST_ACCESS_DATE(Bundle.MultiUserCaseBrowserCustomizer_column_lastAccessTime()),
        MANIFEST_FILE_ZNODES_DELETE_STATUS(Bundle.MultiUserCaseBrowserCustomizer_column_manifestFileZNodesDeleteStatus()),
        DATA_SOURCES_DELETE_STATUS(Bundle.MultiUserCaseBrowserCustomizer_column_dataSourcesDeleteStatus()),
        TEXT_INDEX_DELETE_STATUS(Bundle.MultiUserCaseBrowserCustomizer_column_textIndexDeleteStatus()),
        CASE_DB_DELETE_STATUS(Bundle.MultiUserCaseBrowserCustomizer_column_caseDbDeleteStatus()),
        CASE_DIR_DELETE_STATUS(Bundle.MultiUserCaseBrowserCustomizer_column_caseDirDeleteStatus());

        private final String displayName;

        private Column(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

}
