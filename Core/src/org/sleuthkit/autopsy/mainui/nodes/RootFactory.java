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
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.CasePreferences;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.mainui.datamodel.HostPersonDAO;
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
import org.sleuthkit.datamodel.Person;

/**
 *
 * Root tree view factories.
 */
public class RootFactory {

    public static Children getRootChildren() {
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

        private PropertyChangeListener weakPcl;

        @Override
        protected void addNotify() {
            weakPcl = WeakListeners.propertyChange(pcl, MainDAO.getInstance().getTreeEventsManager());
            MainDAO.getInstance().getTreeEventsManager().addPropertyChangeListener(weakPcl);
            super.addNotify();
        }

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
        @SuppressWarnings("unchecked")
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

        private static final String NAME_ID = "ALL_DATA_SOURCES";

        /**
         * Returns the name identifier of this node.
         *
         * @return The name identifier.
         */
        public static final String getNameIdentifier() {
            return NAME_ID;
        }

        public AllDataSourcesNode() {
            super(NAME_ID,
                    Bundle.RootFactory_AllDataSourcesNode_displayName(),
                    "org/sleuthkit/autopsy/images/image.png",
                    new AllHostsFactory());
        }
    }

    @Messages(value = {"PersonNode_unknownPersonNode_title=Unknown Persons"})
    public static class PersonNode extends TreeNode<PersonSearchParams> {

        /**
         * Returns the name prefix of this node type.
         *
         * @return The name prefix.
         */
        public static final String getNamePrefix() {
            return PersonSearchParams.getTypeId();
        }

        /**
         * Returns the id of an unknown persons node. This can be used with a
         * node lookup.
         *
         * @return The id of an unknown persons node.
         */
        public static String getUnknownPersonId() {
            return Bundle.PersonNode_unknownPersonNode_title();
        }

        public PersonNode(TreeResultsDTO.TreeItemDTO<? extends PersonSearchParams> itemData) {
            super(PersonSearchParams.getTypeId() + getLongString(
                    itemData.getSearchParams().getPerson() == null 
                            ? 0 
                            : itemData.getSearchParams().getPerson().getPersonId()),
                    "org/sleuthkit/autopsy/images/person.png",
                    itemData,
                    Children.create(new HostFactory(itemData.getSearchParams().getPerson()), true),
                    itemData.getSearchParams().getPerson() != null
                    ? Lookups.fixed(itemData.getSearchParams(), itemData.getSearchParams().getPerson())
                    : Lookups.fixed(itemData.getSearchParams(), HostPersonDAO.getUnknownPersonsName()));
        }
    }

    public static class AllHostsFactory extends BaseHostFactory {

        @Override
        protected TreeResultsDTO<? extends HostSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return MainDAO.getInstance().getHostPersonDAO().getAllHosts();
        }
    }

    public static class HostFactory extends BaseHostFactory {

        private final Person parentPerson;

        public HostFactory(Person parentPerson) {
            this.parentPerson = parentPerson;
        }

        @Override
        protected TreeResultsDTO<? extends HostSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return MainDAO.getInstance().getHostPersonDAO().getHosts(parentPerson);
        }
    }

    public abstract static class BaseHostFactory extends TreeChildFactory<HostSearchParams> {

        @Override
        protected TreeNode<HostSearchParams> createNewNode(TreeResultsDTO.TreeItemDTO<? extends HostSearchParams> rowData) {
            return new HostNode(rowData);
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
        
        /**
         * Returns the name prefix of this node.
         *
         * @return The name prefix.
         */
        public static final String getNamePrefix() {
            return HostSearchParams.getTypeId();
        }
        
        public HostNode(TreeResultsDTO.TreeItemDTO<? extends HostSearchParams> itemData) {
            super(HostSearchParams.getTypeId() + "_" + getLongString(itemData.getSearchParams().getHost().getHostId()),
                    "org/sleuthkit/autopsy/images/host.png",
                    itemData,
                    Children.create(new FileSystemFactory(itemData.getSearchParams().getHost()), true),
                    Lookups.fixed(itemData.getSearchParams(), itemData.getSearchParams().getHost()));
        }
    }

    public static class DataSourceGroupedNode extends StaticTreeNode {

        private static final String NAME_PREFIX = "DATA_SOURCE_GROUPED";

        /**
         * Returns the name prefix of this node.
         *
         * @return The name prefix.
         */
        public static final String getNamePrefix() {
            return NAME_PREFIX;
        }
        
        public DataSourceGroupedNode(long dataSourceObjId, String dsName, boolean isImage) {
            super(NAME_PREFIX + "_" + dataSourceObjId,
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

        private static final String NAME_PREFIX = "DATA_SOURCE_FILES";

        /**
         * Returns the name prefix of this node.
         *
         * @return The name prefix.
         */
        public static final String getNamePrefix() {
            return NAME_PREFIX;
        }
        
        public DataSourceFilesNode(long dataSourceObjId) {
            super(NAME_PREFIX + "_" + getLongString(dataSourceObjId),
                    Bundle.RootFactory_DataSourceFilesNode_displayName(),
                    "org/sleuthkit/autopsy/images/image.png",
                    new FileSystemFactory(dataSourceObjId));
        }
    }

    @Messages({"RootFactory_ViewsRootNode_displayName=Views"})
    public static class ViewsRootNode extends StaticTreeNode {

        private static final String NAME_PREFIX = "VIEWS";

        /**
         * Returns the name prefix of this node.
         *
         * @return The name prefix.
         */
        public static final String getNamePrefix() {
            return NAME_PREFIX;
        }
        
        public ViewsRootNode(Long dataSourceObjId) {
            super(NAME_PREFIX + "_" + getLongString(dataSourceObjId),
                    Bundle.RootFactory_ViewsRootNode_displayName(),
                    "org/sleuthkit/autopsy/images/views.png",
                    new ViewsTypeFactory.ViewsChildren(dataSourceObjId));
        }
    }

    @Messages({"RootFactory_DataArtifactsRootNode_displayName=Data Artifacts"})
    public static class DataArtifactsRootNode extends StaticTreeNode {

        private static final String NAME_PREFIX = "DATA_ARTIFACT";

        /**
         * Returns the name prefix of this node.
         *
         * @return The name prefix.
         */
        public static final String getNamePrefix() {
            return NAME_PREFIX;
        }
        
        public DataArtifactsRootNode(Long dataSourceObjId) {
            super(NAME_PREFIX + "_" + getLongString(dataSourceObjId),
                    Bundle.RootFactory_DataArtifactsRootNode_displayName(),
                    "org/sleuthkit/autopsy/images/extracted_content.png",
                    new DataArtifactTypeFactory(dataSourceObjId));
        }
    }

    @Messages({"RootFactory_AnalysisResultsRootNode_displayName=Analysis Results"})
    public static class AnalysisResultsRootNode extends StaticTreeNode {

        private static final String NAME_PREFIX = "DATA_SOURCE_BY_TYPE";

        /**
         * Returns the name identifier of this node.
         *
         * @return The name identifier.
         */
        public static final String getNamePrefix() {
            return NAME_PREFIX;
        }
        
        public AnalysisResultsRootNode(Long dataSourceObjId) {
            super(NAME_PREFIX + "_" + getLongString(dataSourceObjId),
                    Bundle.RootFactory_AnalysisResultsRootNode_displayName(),
                    "org/sleuthkit/autopsy/images/analysis_result.png",
                    new AnalysisResultTypeFactory(dataSourceObjId));
        }
    }

    @Messages({"RootFactory_OsAccountsRootNode_displayName=OS Accounts"})
    public static class OsAccountsRootNode extends StaticTreeNode {

        private static final String NAME_PREFIX = "OS_ACCOUNTS";

        /**
         * Returns the name prefix of this node.
         *
         * @return The name prefix.
         */
        public static final String getNamePrefix() {
            return NAME_PREFIX;
        }
        
        private final Long dataSourceObjId;

        public OsAccountsRootNode(Long dataSourceObjId) {
            super(NAME_PREFIX + "_" + getLongString(dataSourceObjId),
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

        private static final String NAME_PREFIX = "DATA_SOURCE_BY_TYPE";

        /**
         * Returns the name prefix of this node.
         *
         * @return The name prefix.
         */
        public static final String getNamePrefix() {
            return NAME_PREFIX;
        }
        
        public TagsRootNode(Long dataSourceObjId) {
            super(NAME_PREFIX + "_" + getLongString(dataSourceObjId),
                    Bundle.RootFactory_TagsRootNode_displayName(),
                    "org/sleuthkit/autopsy/images/tag-folder-blue-icon-16.png",
                    new TagNameFactory(dataSourceObjId));
        }
    }

    @Messages({"RootFactory_ReportsRootNode_displayName=Reports"})
    public static class ReportsRootNode extends StaticTreeNode {

        private static final String NAME_ID = "REPORTS";

        /**
         * Returns the name identifier of this node.
         *
         * @return The name identifier.
         */
        public static final String getNameIdentifier() {
            return NAME_ID;
        }
        
        public ReportsRootNode() {
            super(NAME_ID,
                    Bundle.RootFactory_ReportsRootNode_displayName(),
                    "org/sleuthkit/autopsy/images/report_16.png");
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayReports(ReportsSearchParams.getInstance());
        }
    }
}
