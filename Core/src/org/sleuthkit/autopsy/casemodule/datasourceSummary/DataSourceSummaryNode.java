/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.datasourceSummary;

import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.DataSource;

final class DataSourceSummaryNode extends AbstractNode {

    DataSourceSummaryNode(List<DataSourceSummary> dataSourceList) {
        super(Children.create(new DataSourceSummaryChildren(dataSourceList), true));
    }

    static final class DataSourceSummaryChildren extends ChildFactory<DataSourceSummary> {

        private final List<DataSourceSummary> dataSourceList;

        DataSourceSummaryChildren(List<DataSourceSummary> dataSourceList) {
            this.dataSourceList = dataSourceList;
        }

        @Override
        protected boolean createKeys(List<DataSourceSummary> list) {
            if (dataSourceList != null && dataSourceList.size() > 0) {
                list.addAll(dataSourceList);
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(DataSourceSummary key) {
            return new DataSourceSummaryEntryNode(key);
        }
    }

    /**
     * A node which represents a single multi user case.
     */
    static final class DataSourceSummaryEntryNode extends AbstractNode {

        private final DataSource dataSource;
        private final String type;
        private final long filesCount;
        private final long resultsCount;
        private final long tagsCount;

        DataSourceSummaryEntryNode(DataSourceSummary dataSourceSummary) {
            super(Children.LEAF);
            dataSource = dataSourceSummary.getDataSource();
            type = dataSourceSummary.getType();
            filesCount = dataSourceSummary.getFilesCount();
            resultsCount = dataSourceSummary.getResultsCount();
            tagsCount = dataSourceSummary.getTagsCount();
            super.setName(dataSource.getName());
            setName(dataSource.getName());
            setDisplayName(dataSource.getName());
        }

        DataSource getDataSource() {
            return dataSource;
        }

//        /**
//         * Returns action to open the Case represented by this node
//         *
//         * @return an action which will open the current case
//         */
//        @Override
//        public Action getPreferredAction() {
//            return new OpenMultiUserCaseAction(caseMetadataFilePath);
//        }
        @Messages({"DataSourceSummaryNode.column.dataSourceName.header=Data Source Name",
            "DataSourceSummaryNode.column.type.header=Type",
            "DataSourceSummaryNode.column.files.header=Files",
            "DataSourceSummaryNode.column.results.header=Results",
            "DataSourceSummaryNode.column.tags.header=Tags"})
        @Override
        protected Sheet createSheet() {
            Sheet sheet = super.createSheet();
            Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
            if (sheetSet == null) {
                sheetSet = Sheet.createPropertiesSet();
                sheet.put(sheetSet);
            }
            sheetSet.put(new NodeProperty<>(Bundle.DataSourceSummaryNode_column_dataSourceName_header(), Bundle.DataSourceSummaryNode_column_dataSourceName_header(), Bundle.DataSourceSummaryNode_column_dataSourceName_header(),
                    dataSource));
            sheetSet.put(new NodeProperty<>(Bundle.DataSourceSummaryNode_column_type_header(), Bundle.DataSourceSummaryNode_column_type_header(), Bundle.DataSourceSummaryNode_column_type_header(),
                    type));
            sheetSet.put(new NodeProperty<>(Bundle.DataSourceSummaryNode_column_files_header(), Bundle.DataSourceSummaryNode_column_files_header(), Bundle.DataSourceSummaryNode_column_files_header(),
                    filesCount));
            sheetSet.put(new NodeProperty<>(Bundle.DataSourceSummaryNode_column_results_header(), Bundle.DataSourceSummaryNode_column_results_header(), Bundle.DataSourceSummaryNode_column_results_header(),
                    resultsCount));
            sheetSet.put(new NodeProperty<>(Bundle.DataSourceSummaryNode_column_tags_header(), Bundle.DataSourceSummaryNode_column_tags_header(), Bundle.DataSourceSummaryNode_column_tags_header(),
                    tagsCount));
            return sheet;
        }

        @Override
        public Action[] getActions(boolean context) {
            List<Action> actions = new ArrayList<>();
//                actions.add(new OpenMultiUserCaseAction(caseMetadataFilePath));  //open case context menu option
//                actions.add(new OpenCaseLogAction(caseLogFilePath));
            return actions.toArray(new Action[actions.size()]);
        }
    }
}
