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
 * 9
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.experimental.autoingest;

import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;
import org.sleuthkit.autopsy.casemodule.multiusercasesbrowser.MultiUserCaseBrowserCustomizer;

/**
 * A customizer for the multi-user case browser panel used in the administrative
 * dashboard for auto ingest cases to present a tabular view of the multi-user
 * cases known to the coordination service.
 */
final class CasesDashboardCustomizer implements MultiUserCaseBrowserCustomizer {

    private final DeleteCaseAction deleteCaseAction;
    private final DeleteCaseInputAction deleteCaseInputAction;
    private final DeleteCaseOutputAction deleteCaseOutputAction;
    private final DeleteCaseInputAndOutputAction deleteCaseInputAndOutputAction;

    /**
     * Constructs a customizer for the multi-user case browser panel used in the
     * administrative dashboard for auto ingest cases to present a tabular view
     * of the multi-user cases known to the coordination service.
     *
     * @param executor An executor for tasks for actions that do work in the
     *                 background.
     */
    CasesDashboardCustomizer() {
        /*
         * These actions are shared by all nodes in order to support multiple
         * selection.
         */
        deleteCaseAction = new DeleteCaseAction();
        deleteCaseInputAction = new DeleteCaseInputAction();
        deleteCaseOutputAction = new DeleteCaseOutputAction();
        deleteCaseInputAndOutputAction = new DeleteCaseInputAndOutputAction();
    }

    @Override
    public List<Column> getColumns() {
        List<Column> properties = new ArrayList<>();
        properties.add(Column.CREATE_DATE);
        properties.add(Column.LAST_ACCESS_DATE);
        properties.add(Column.DIRECTORY);
        properties.add(Column.MANIFEST_FILE_ZNODES_DELETE_STATUS);
        if (AutoIngestDashboard.extendedFeaturesAreEnabled()) {
            properties.add(Column.DATA_SOURCES_DELETE_STATUS);
        }
        properties.add(Column.TEXT_INDEX_DELETE_STATUS);
        properties.add(Column.CASE_DB_DELETE_STATUS);
        properties.add(Column.CASE_DIR_DELETE_STATUS);
        return properties;
    }

    @Override
    public List<SortColumn> getSortColumns() {
        List<SortColumn> sortColumns = new ArrayList<>();
        sortColumns.add(new SortColumn(Column.DISPLAY_NAME, true, 1));
        return sortColumns;
    }

    @Override
    public boolean allowMultiSelect() {
        return true;
    }

    @Override
    public List<Action> getActions(CaseNodeData nodeData) {
        List<Action> actions = new ArrayList<>();
        actions.add(new OpenCaseAction(nodeData));
        actions.add(new OpenAutoIngestLogAction(nodeData));
        if (AutoIngestDashboard.extendedFeaturesAreEnabled()) {
            actions.add(deleteCaseInputAction);
            actions.add(deleteCaseOutputAction);
            actions.add(deleteCaseInputAndOutputAction);
        } else {
            actions.add(deleteCaseAction);
        }
        return actions;
    }

    @Override
    public Action getPreferredAction(CaseNodeData nodeData) {
        return new OpenCaseAction(nodeData);
    }

}
