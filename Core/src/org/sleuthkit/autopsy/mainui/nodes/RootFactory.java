/*
 * Autopsy Forensic Browser
 *
 * Copyright 2022 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.nodes;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemContentSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.OsAccountsSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.PersonSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.nodes.FileSystemFactory.DataSourceFactory;
import org.sleuthkit.autopsy.mainui.nodes.TreeNode.StaticTreeNode;

/**
 *
 * @author gregd
 */
public class RootFactory {

    public Children getRootChildren() {

    }
    
    private static String getLongString(Long l) {
        return l == null ? "" : l.toString();
    }

    public static class HostPersonRootFactory extends TreeChildFactory<PersonSearchParams> {

    }
    
    public static class DefaultViewRootFactory extends Children.Array {
        public DefaultViewRootFactory() {
            super(Arrays.asList(
                    new DataSourcesNode(),
                    new ViewsRootNode(null),
                    new DataArtifactsRootNode(null),
                    new AnalysisResultsRootNode(null),
                    new OsAccountsRootNode(null),
                    new TagsRootNode(null),
                    new ReportsRootNode()
            ));
        }
    }

    public static class HostFactory extends TreeChildFactory<HostSearchParams> {

        private final boolean groupByPersonHost;

    }

    public static class DataSourceGroupedNode extends StaticTreeNode {
        public DataSourceGroupedNode(long dataSourceObjId) {
            super("DATA_SOURCE_GROUPED_" + dataSourceObjId,
                    dataSourceDisplayName,
                    ICON,
                    new DataSourceGroupedFactory(dataSourceObjId));
        }
    }

    // shows all content related to data sources
    public static class DataSourceGroupedFactory extends Children.Array {

        public DataSourceGroupedFactory(long dataSourceObjId) {
            super(Arrays.asList(
                    new DataSourcesByTypeNode(dataSourceObjId),
                    new ViewsRootNode(dataSourceObjId),
                    new DataArtifactsRootNode(dataSourceObjId),
                    new AnalysisResultsRootNode(dataSourceObjId),
                    new OsAccountsRootNode(dataSourceObjId),
                    new TagsRootNode(dataSourceObjId),
                    new ReportsRootNode()
            ));
        }
    }

    public static class DataSourcesByTypeNode extends StaticTreeNode {

        public DataSourcesByTypeNode(long dataSourceObjId) {
            super("DATA_SOURCE_BY_TYPE_" + dataSourceObjId,
                    dataSourceDisplayName,
                    ICON,
                    new FileSystemFactory(dataSourceObjId));
        }
    }

    @Messages({"RootFactory_ViewsRootNode_displayName=Views"})
    public static class ViewsRootNode extends StaticTreeNode {

        public ViewsRootNode(Long dataSourceObjId) {
            super("VIEWS_" + getLongString(dataSourceObjId),
                    Bundle.RootFactory_ViewsRootNode_displayName(),
                    "org/sleuthkit/autopsy/images/views.png",
                    new ViewsTypeFactory.ViewsChildren(dataSourceObjId));
        }
    }

    @Messages({"RootFactory_DataArtifactsRootNode_displayName=Data Artifacts"})
    public static class DataArtifactsRootNode extends StaticTreeNode {

        public DataArtifactsRootNode(Long dataSourceObjId) {
            super("DATA_ARTIFACT_" + getLongString(dataSourceObjId),
                    Bundle.RootFactory_DataArtifactsRootNode_displayName(),
                    "org/sleuthkit/autopsy/images/extracted_content.png",
                    new DataArtifactTypeFactory(dataSourceObjId));
        }
    }
    
    @Messages({"RootFactory_AnalysisResultsRootNode_displayName=Analysis Results"})
    public static class AnalysisResultsRootNode extends StaticTreeNode {

        public AnalysisResultsRootNode(Long dataSourceObjId) {
            super("DATA_SOURCE_BY_TYPE_" + getLongString(dataSourceObjId),
                    Bundle.RootFactory_AnalysisResultsRootNode_displayName(),
                    "org/sleuthkit/autopsy/images/analysis_result.png",
                    new AnalysisResultTypeFactory(dataSourceObjId));
        }
    }

    @Messages({"RootFactory_OsAccountsRootNode_displayName=OS Accounts"})
    public static class OsAccountsRootNode extends StaticTreeNode {

        private final Long dataSourceObjId;

        public OsAccountsRootNode(Long dataSourceObjId) {
            super("DATA_SOURCE_BY_TYPE_" + getLongString(dataSourceObjId),
                    Bundle.RootFactory_OsAccountsRootNode_displayName(),
                    "org/sleuthkit/autopsy/images/os-account.png");
            
            this.dataSourceObjId = dataSourceObjId;
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayOsAccounts(new OsAccountsSearchParams(dataSourceObjId));
        }
        
        
    }

    @Messages({"RootFactory_TagsRootNode_displayName=Tags"})
    public static class TagsRootNode extends StaticTreeNode {
        public TagsRootNode(long dataSourceObjId) {
            super("DATA_SOURCE_BY_TYPE_" + getLongString(dataSourceObjId),
                    Bundle.RootFactory_TagsRootNode_displayName(),
                    "org/sleuthkit/autopsy/images/tag-folder-blue-icon-16.png",
                    new Ta(dataSourceObjId));
        }
    }

    @Messages({"RootFactory_ReportsRootNode_displayName=Reports"})
    public static class ReportsRootNode extends StaticTreeNode {

        public ReportsRootNode() {
            super("REPORTS",
                    Bundle.RootFactory_ReportsRootNode_displayName(),
                    "org/sleuthkit/autopsy/images/report_16.png");
        }
    }
    
    
    
    

//                        new DataSourcesByType(),
//                        new Views(Case.getCurrentCaseThrows().getSleuthkitCase()),
//                        new DataArtifacts(),
//                        new AnalysisResults(),
//                        new OsAccounts(),
//                        new Tags(),
//                        new Reports()
    /**
     *
     * if (Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(),
     * true)) { PersonManager personManager = tskCase.getPersonManager();
     * List<Person> persons = personManager.getPersons(); // show persons level
     * if there are persons to be shown if (!CollectionUtils.isEmpty(persons)) {
     * nodes = persons.stream() .map(PersonGrouping::new) .sorted()
     * .collect(Collectors.toList());
     *
     * if (CollectionUtils.isNotEmpty(personManager.getHostsWithoutPersons())) {
     * nodes.add(new PersonGrouping(null)); } } else { // otherwise, just show
     * host level nodes = tskCase.getHostManager().getAllHosts().stream()
     * .map(HostGrouping::new) .sorted() .collect(Collectors.toList()); }
     *
     * // either way, add in reports node nodes.add(new Reports()); } else { //
     * data source by type view nodes = Arrays.asList( new DataSourcesByType(),
     * new Views(Case.getCurrentCaseThrows().getSleuthkitCase()), new
     * DataArtifacts(), new AnalysisResults(), new OsAccounts(), new Tags(), new
     * Reports() ); }
     *
     */
}
