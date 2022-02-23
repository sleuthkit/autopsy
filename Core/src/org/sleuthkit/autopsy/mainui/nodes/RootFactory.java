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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.util.WeakListeners;
import org.sleuthkit.autopsy.casemodule.CasePreferences;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.mainui.datamodel.HostSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.OsAccountsSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.PersonSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.ReportsSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOAggregateEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.HostPersonEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.nodes.TreeNode.StaticTreeNode;
import static org.sleuthkit.autopsy.mainui.nodes.TreeNode.getDefaultLookup;
import org.sleuthkit.datamodel.Person;

/**
 *
 * Root tree view factories.
 */
@Messages({"RootFactory_unknownPersons_displayName=Unknown Persons"})
public class RootFactory {

    public Children getRootChildren() {
        // GVDTODO integrate
        if (Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true)) {
            return Children.create(new HostPersonRootFactory(), true);
        } else {
            return new DefaultViewRootFactory();
        }
    }

    private static String getLongString(Long l) {
        return l == null ? "" : l.toString();
    }

    /**
     * Factory for populating child nodes in a tree based on TreeResultsDTO
     */
    static class HostPersonRootFactory extends ChildFactory.Detachable<TreeItemDTO<?>> {

        private static final Logger logger = Logger.getLogger(HostPersonRootFactory.class.getName());

        private final PropertyChangeListener pcl = (PropertyChangeEvent evt) -> {
            if (evt.getNewValue() instanceof DAOAggregateEvent) {
                for (DAOEvent daoEvt : ((DAOAggregateEvent) evt.getNewValue()).getEvents()) {
                    if (daoEvt instanceof HostPersonEvent) {
                        HostPersonRootFactory.this.refresh(false);
                        return;
                    }
                }
            }
        };

        private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(pcl, MainDAO.getInstance().getTreeEventsManager());

        @Override
        protected boolean createKeys(List<TreeItemDTO<?>> toPopulate) {
            try {
                TreeResultsDTO<? extends PersonSearchParams> persons = MainDAO.getInstance().getHostPersonDAO().getAllPersons();
                if (persons.getItems().isEmpty() || (persons.getItems().size() == 1 && persons.getItems().get(0).getSearchParams().getPerson() == null)) {
                    toPopulate.addAll(MainDAO.getInstance().getHostPersonDAO().getAllHosts().getItems());
                } else {
                    toPopulate.addAll(persons.getItems());
                }
            } catch (ExecutionException | IllegalArgumentException ex) {
                logger.log(Level.WARNING, "Error acquiring top-level host/person data", ex);
            }

            return true;
        }

        @Override
        protected Node createNodeForKey(TreeItemDTO<?> key) {
            if (key.getSearchParams() instanceof HostSearchParams) {
                return new HostNode((TreeItemDTO<? extends HostSearchParams>) key);
            } else if (key.getSearchParams() instanceof PersonSearchParams) {
                return new PersonNode((TreeItemDTO<? extends PersonSearchParams>) key);
            } else {
                return null;
            }
        }
    }

    public static class DefaultViewRootFactory extends Children.Array {

        public DefaultViewRootFactory() {
            super(Arrays.asList(
                    new AllDataSourcesNode(),
                    new ViewsRootNode(null),
                    new DataArtifactsRootNode(null),
                    new AnalysisResultsRootNode(null),
                    new OsAccountsRootNode(null),
                    new TagsRootNode(null),
                    new ReportsRootNode()
            ));
        }
    }

    @Messages({"RootFactory_AllDataSourcesNode_displayName=Data Sources"})
    public static class AllDataSourcesNode extends StaticTreeNode {

        public AllDataSourcesNode() {
            super("ALL_DATA_SOURCES",
                    Bundle.RootFactory_AllDataSourcesNode_displayName(),
                    "org/sleuthkit/autopsy/images/image.png",
                    new HostFactory(Optional.empty()));
        }
    }

    public static class PersonNode extends TreeNode<PersonSearchParams> {

        public PersonNode(TreeResultsDTO.TreeItemDTO<? extends PersonSearchParams> itemData) {
            super(PersonSearchParams.getTypeId(),
                    "org/sleuthkit/autopsy/images/person.png",
                    itemData,
                    Children.create(new HostFactory(Optional.of(itemData.getSearchParams().getPerson())), true),
                    getDefaultLookup(itemData));
        }
    }

    public static class HostFactory extends TreeChildFactory<HostSearchParams> {

        private final Optional<Person> parentPerson;

        public HostFactory(Optional<Person> parentPerson) {
            this.parentPerson = parentPerson;
        }

        @Override
        protected TreeNode<HostSearchParams> createNewNode(TreeResultsDTO.TreeItemDTO<? extends HostSearchParams> rowData) {
            return new HostNode(rowData);
        }

        @Override
        protected TreeResultsDTO<? extends HostSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            if (parentPerson.isPresent()) {
                return MainDAO.getInstance().getHostPersonDAO().getHosts(parentPerson.get());
            } else {
                return MainDAO.getInstance().getHostPersonDAO().getAllHosts();
            }
        }

        @Override
        protected void handleDAOAggregateEvent(DAOAggregateEvent aggEvt) {
            for (DAOEvent evt : aggEvt.getEvents()) {
                if (evt instanceof HostPersonEvent) {
                    super.update();
                    return;
                }
            }
        }

        @Override
        protected TreeResultsDTO.TreeItemDTO<? extends HostSearchParams> getOrCreateRelevantChild(TreeEvent treeEvt) {
            return null;
        }

        @Override
        public int compare(TreeResultsDTO.TreeItemDTO<? extends HostSearchParams> o1, TreeResultsDTO.TreeItemDTO<? extends HostSearchParams> o2) {
            return Comparator.comparing((TreeResultsDTO.TreeItemDTO<? extends HostSearchParams> h) -> h.getSearchParams().getHost().getName()).compare(o1, o2);
        }
    }

    public static class HostNode extends TreeNode<HostSearchParams> {

        public HostNode(TreeResultsDTO.TreeItemDTO<? extends HostSearchParams> itemData) {
            super(HostSearchParams.getTypeId(),
                    "org/sleuthkit/autopsy/images/host.png",
                    itemData,
                    Children.create(new FileSystemFactory(itemData.getSearchParams().getHost()), true),
                    getDefaultLookup(itemData));
        }
    }

    public static class DataSourceGroupedNode extends StaticTreeNode {

        public DataSourceGroupedNode(long dataSourceObjId, String dsName, boolean isImage) {
            super("DATA_SOURCE_GROUPED_" + dataSourceObjId,
                    dsName,
                    isImage ? "org/sleuthkit/autopsy/images/image.png" : "org/sleuthkit/autopsy/images/fileset-icon-16.png",
                    new DataSourceGroupedFactory(dataSourceObjId));
        }
    }

    // shows all content related to data sources
    public static class DataSourceGroupedFactory extends Children.Array {

        public DataSourceGroupedFactory(long dataSourceObjId) {
            super(Arrays.asList(
                    new DataSourceFilesNode(dataSourceObjId),
                    new ViewsRootNode(dataSourceObjId),
                    new DataArtifactsRootNode(dataSourceObjId),
                    new AnalysisResultsRootNode(dataSourceObjId),
                    new OsAccountsRootNode(dataSourceObjId),
                    new TagsRootNode(dataSourceObjId),
                    new ReportsRootNode()
            ));
        }
    }

    @Messages({"RootFactory_DataSourceFilesNode_displayName=Data Source Files"})
    public static class DataSourceFilesNode extends StaticTreeNode {

        public DataSourceFilesNode(long dataSourceObjId) {
            super("DATA_SOURCE_FILES_" + dataSourceObjId,
                    Bundle.RootFactory_DataSourceFilesNode_displayName(),
                    "org/sleuthkit/autopsy/images/image.png",
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

        public TagsRootNode(Long dataSourceObjId) {
            super("DATA_SOURCE_BY_TYPE_" + getLongString(dataSourceObjId),
                    Bundle.RootFactory_TagsRootNode_displayName(),
                    "org/sleuthkit/autopsy/images/tag-folder-blue-icon-16.png",
                    new TagNameFactory(dataSourceObjId));
        }
    }

    @Messages({"RootFactory_ReportsRootNode_displayName=Reports"})
    public static class ReportsRootNode extends StaticTreeNode {

        public ReportsRootNode() {
            super("REPORTS",
                    Bundle.RootFactory_ReportsRootNode_displayName(),
                    "org/sleuthkit/autopsy/images/report_16.png");
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayReports(ReportsSearchParams.getInstance());
        }
    }
}
