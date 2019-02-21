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
 * A customizer for a multi-user case browser panel that presents a tabular view
 * of the multi-user cases known to the coordination service in the
 * administrative dashboard for auto ingest cases.
 */
final class AutoIngestCasesDashboardNodeCustomizer implements MultiUserCaseBrowserCustomizer {

    private final DeleteCaseInputDirectoriesAction deleteCaseInputAction;
    private final DeleteCasesForReprocessingAction deleteCaseOutputAction;
    private final DeleteCasesAction deleteCaseAction;

    /**
     * Constructs a customizer for a multi-user case browser panel that presents
     * a tabular view of the multi-user cases known to the coordination service
     * in the administrative dashboard for auto ingest cases.
     */
    AutoIngestCasesDashboardNodeCustomizer() {
        /*
         * These actions are shared by all nodes in order to support multiple
         * selection.
         */
        deleteCaseInputAction = new DeleteCaseInputDirectoriesAction();
        deleteCaseOutputAction = new DeleteCasesForReprocessingAction();
        deleteCaseAction = new DeleteCasesAction();
    }

    @Override
    public List<Column> getColumns() {
        List<Column> properties = new ArrayList<>();
        properties.add(Column.CREATE_DATE);
        properties.add(Column.DIRECTORY);
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
        actions.add(deleteCaseInputAction);
        actions.add(deleteCaseOutputAction);
        actions.add(deleteCaseAction);
        actions.add(new ShowCaseDeletionStatusAction(nodeData));
        return actions;
    }

    @Override
    public Action getPreferredAction(CaseNodeData nodeData) {
        return new OpenCaseAction(nodeData);
    }

}
