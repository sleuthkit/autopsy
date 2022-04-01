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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CasePreferences;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemContentSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.HostPersonDAO;
import org.sleuthkit.autopsy.mainui.datamodel.HostSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.OsAccountsSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.PersonSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.ReportsSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeDisplayCount;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOAggregateEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.HostPersonEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.nodes.TreeNode.StaticTreeNode;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.Person;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/**
 *
 * Root tree view factories.
 */
public class RootFactory {

    /**
     * @return The root children to be displayed in the tree.
     */
    public static Children getRootChildren() {
        if (Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true)) {
            return Children.create(new HostPersonRootFactory(), true);
        } else {
            return new DefaultViewRootChildren();
        }
    }

    /**
     * Returns a string of the safely converted long to be used in a name id.
     *
     * @param l The number or null.
     *
     * @return The safely stringified number.
     */
    private static String getLongString(Long l) {
        return l == null ? "" : l.toString();
    }

    /**
     * Factory for populating child nodes in a tree based on TreeResultsDTO
     */
    static class HostPersonRootFactory extends TreeChildFactory<Object> {

        private static final Logger logger = Logger.getLogger(HostPersonRootFactory.class.getName());

        @Override
        protected void handleDAOAggregateEvent(DAOAggregateEvent aggEvt) {
            for (DAOEvent evt : aggEvt.getEvents()) {
                if (evt instanceof HostPersonEvent) {
                    super.update();
                    return;
                }
            }
        }

        @SuppressWarnings("unchecked")
        private TreeNode<Object> downCastNode(TreeNode<? extends Object> orig) {
            return (TreeNode<Object>) orig;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected TreeNode<Object> createNewNode(TreeItemDTO<? extends Object> rowData) {
            if (rowData.getSearchParams() instanceof HostSearchParams) {
                return downCastNode(HostNode.getPersonHostViewNode((TreeItemDTO<? extends HostSearchParams>) rowData));
            } else if (rowData.getSearchParams() instanceof PersonSearchParams) {
                return downCastNode(new PersonNode((TreeItemDTO<? extends PersonSearchParams>) rowData));
            } else if (rowData.getSearchParams() instanceof ReportsSearchParams) {
                return downCastNode(new ReportsRootNode());
            } else {
                return null;
            }
        }

        /**
         * This creates a modified tree item dto where the id has a prefix (to differentiate switches between person and host).
         * @param orig The original tree item.
         * @return The modified tree item
         */
        private TreeItemDTO<Object> getModifiedIdTreeItem(TreeItemDTO<? extends Object> orig) {
            return new TreeItemDTO<Object>(
                orig.getTypeId(), 
                orig.getSearchParams(), 
                orig.getTypeId() + "_" + orig.getId(), 
                orig.getDisplayName(), 
                orig.getDisplayCount()
            );
        }

        @Override
        protected TreeResultsDTO<? extends Object> getChildResults() throws IllegalArgumentException, ExecutionException {
            List<TreeItemDTO<Object>> toReturn = new ArrayList<>();
            try {
                TreeResultsDTO<? extends PersonSearchParams> persons = MainDAO.getInstance().getHostPersonDAO().getAllPersons();
                if (persons.getItems().isEmpty() || (persons.getItems().size() == 1 && persons.getItems().get(0).getSearchParams().getPerson() == null)) {
                    toReturn.addAll(MainDAO.getInstance().getHostPersonDAO().getAllHosts().getItems().stream()
                            .map(this::getModifiedIdTreeItem)
                            .collect(Collectors.toList()));
                } else {
                    toReturn.addAll(persons.getItems().stream()
                            .map(this::getModifiedIdTreeItem)
                            .collect(Collectors.toList()));
                }
            } catch (ExecutionException | IllegalArgumentException ex) {
                logger.log(Level.WARNING, "Error acquiring top-level host/person data", ex);
            }

            toReturn.add(new TreeItemDTO<Object>(
                    ReportsSearchParams.getTypeId(),
                    ReportsSearchParams.getInstance(),
                    ReportsSearchParams.class.getSimpleName(),
                    Bundle.RootFactory_ReportsRootNode_displayName(),
                    TreeDisplayCount.NOT_SHOWN));

            return new TreeResultsDTO<Object>(toReturn);
        }

        @Override
        protected TreeItemDTO<? extends Object> getOrCreateRelevantChild(TreeEvent treeEvt) {
            // all events will be handled with a full refresh
            return null;
        }

        @Override
        public int compare(TreeItemDTO<? extends Object> o1, TreeItemDTO<? extends Object> o2) {
            // all events will be handled with a full refresh
            return 0;
        }
    }

    /**
     * The root children for the default view preference.
     */
    public static class DefaultViewRootChildren extends Children.Array {

        /**
         * Main constructor.
         */
        public DefaultViewRootChildren() {
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

    /**
     * Node in default view displaying all hosts/data sources.
     */
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

        /**
         * Main constructor.
         */
        public AllDataSourcesNode() {
            super(NAME_ID,
                    Bundle.RootFactory_AllDataSourcesNode_displayName(),
                    "org/sleuthkit/autopsy/images/image.png",
                    new AllHostsFactory());
        }
    }

    /**
     * A person node.
     */
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

        /**
         * Main constructor.
         *
         * @param itemData The row data for the person.
         */
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

        @Override
        public Optional<Person> getPerson() {
            return Optional.ofNullable(getItemData().getSearchParams().getPerson());
        }
    }

    /**
     * Factory displaying all hosts in default view.
     */
    public static class AllHostsFactory extends BaseHostFactory {

        @Override
        protected TreeResultsDTO<? extends HostSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return MainDAO.getInstance().getHostPersonDAO().getAllHosts();
        }

        @Override
        protected TreeNode<HostSearchParams> createNewNode(TreeItemDTO<? extends HostSearchParams> rowData) {
            return HostNode.getDefaultViewNode(rowData);
        }
    }

    /**
     * Factory displaying hosts belonging to a person (or null).
     */
    public static class HostFactory extends BaseHostFactory {

        private final Person parentPerson;

        /**
         * Main constructor.
         *
         * @param parentPerson The person whose hosts will be shown. Null
         *                     indicates showing any host with no person
         *                     associated.
         */
        public HostFactory(Person parentPerson) {
            this.parentPerson = parentPerson;
        }

        @Override
        protected TreeResultsDTO<? extends HostSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return MainDAO.getInstance().getHostPersonDAO().getHosts(parentPerson);
        }

        @Override
        protected TreeNode<HostSearchParams> createNewNode(TreeItemDTO<? extends HostSearchParams> rowData) {
            return HostNode.getPersonHostViewNode(rowData);
        }
    }

    /**
     * Base factory for displaying hosts.
     */
    public abstract static class BaseHostFactory extends TreeChildFactory<HostSearchParams> {

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

    /**
     * Node for a host.
     */
    public static class HostNode extends TreeNode<HostSearchParams> {

        /**
         * Returns the name prefix of this node.
         *
         * @return The name prefix.
         */
        public static final String getNamePrefix() {
            return HostSearchParams.getTypeId();
        }

        /**
         * Returns a host node whose children will be used in the default view.
         *
         * @param itemData The data associated with the host.
         *
         * @return A host node.
         */
        public static HostNode getDefaultViewNode(TreeResultsDTO.TreeItemDTO<? extends HostSearchParams> itemData) {
            return new HostNode(itemData, Children.create(new FileSystemFactory(itemData.getSearchParams().getHost()), true));
        }

        /**
         * Returns a host node whose children will be used in the person/host
         * view.
         *
         * @param itemData The data associated with the host.
         *
         * @return A host node.
         */
        public static HostNode getPersonHostViewNode(TreeResultsDTO.TreeItemDTO<? extends HostSearchParams> itemData) {
            return new HostNode(itemData, Children.create(new DataSourceGroupedFactory(itemData.getSearchParams().getHost()), true));
        }

        /**
         * Private constructor.
         *
         * @param itemData The data for the host.
         * @param children The children to use with this host.
         */
        private HostNode(TreeResultsDTO.TreeItemDTO<? extends HostSearchParams> itemData, Children children) {
            super(HostSearchParams.getTypeId() + "_" + getLongString(itemData.getSearchParams().getHost().getHostId()),
                    "org/sleuthkit/autopsy/images/host.png",
                    itemData,
                    children,
                    Lookups.fixed(itemData.getSearchParams(), itemData.getSearchParams().getHost()));
        }

        @Override
        public Optional<Host> getHost() {
            return Optional.of(getItemData().getSearchParams().getHost());
        }
    }

    /**
     * The factory to use to create data source grouping nodes to display in the
     * host/person view.
     */
    public static class DataSourceGroupedFactory extends FileSystemFactory {

        /**
         * Main constructor.
         *
         * @param host The parent host.
         */
        public DataSourceGroupedFactory(Host host) {
            super(host);
        }

        @Override
        protected TreeNode<FileSystemContentSearchParam> createNewNode(TreeItemDTO<? extends FileSystemContentSearchParam> rowData) {
            return DataSourceGroupedNode.getInstance(rowData);
        }

    }

    /**
     * A data source grouping node to display in host/person view.
     */
    public static class DataSourceGroupedNode extends TreeNode<FileSystemContentSearchParam> {

        private static final Logger logger = Logger.getLogger(DataSourceGroupedNode.class.getName());

        private static final String NAME_PREFIX = "DATA_SOURCE_GROUPED";

        /**
         * Returns the name prefix of this node.
         *
         * @return The name prefix.
         */
        public static final String getNamePrefix() {
            return NAME_PREFIX;
        }

        /**
         * Returns an instance of the data source grouping node.
         *
         * @param itemData The row data.
         *
         * @return The node.
         */
        public static DataSourceGroupedNode getInstance(TreeItemDTO<? extends FileSystemContentSearchParam> itemData) {
            long dataSourceId = itemData.getSearchParams().getContentObjectId();
            try {

                DataSource ds = Case.getCurrentCaseThrows().getSleuthkitCase().getDataSource(dataSourceId);
                return (ds == null) ? null : new DataSourceGroupedNode(itemData, ds);
            } catch (NoCurrentCaseException ex) {
                // Case is likely closing
                return null;
            } catch (TskCoreException | TskDataException ex) {
                logger.log(Level.SEVERE, "Error creating node from data source with ID: " + dataSourceId, ex);
                return null;
            }
        }

        /**
         * Private constructor.
         *
         * @param itemData   The row data.
         * @param dataSource The relevant data source instance.
         */
        private DataSourceGroupedNode(TreeItemDTO<? extends FileSystemContentSearchParam> itemData, DataSource dataSource) {
            super(NAME_PREFIX + "_" + getLongString(dataSource.getId()),
                    dataSource instanceof Image
                            ? "org/sleuthkit/autopsy/images/image.png"
                            : "org/sleuthkit/autopsy/images/fileset-icon-16.png",
                    itemData,
                    new DataSourceGroupedChildren(itemData, dataSource),
                    Lookups.singleton(dataSource));
        }
    }

    /**
     * Shows all content related to a data source in host/person view.
     */
    public static class DataSourceGroupedChildren extends Children.Array {

        /**
         * Main constructor.
         *
         * @param itemData   The row data.
         * @param dataSource The data source.
         */
        public DataSourceGroupedChildren(TreeItemDTO<? extends FileSystemContentSearchParam> itemData, DataSource dataSource) {
            this(itemData, dataSource, dataSource.getId());
        }

        private DataSourceGroupedChildren(TreeItemDTO<? extends FileSystemContentSearchParam> itemData, DataSource dataSource, long dataSourceObjId) {
            super(Arrays.asList(
                    new DataSourceFilesNode(itemData, dataSource),
                    new ViewsRootNode(dataSourceObjId),
                    new DataArtifactsRootNode(dataSourceObjId),
                    new AnalysisResultsRootNode(dataSourceObjId),
                    new OsAccountsRootNode(dataSourceObjId),
                    new TagsRootNode(dataSourceObjId)
            ));
        }
    }

    /**
     * Node for showing data source files in person/host view.
     */
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

        /**
         * Main constructor.
         *
         * @param itemData   The row data.
         * @param dataSource The data source.
         */
        public DataSourceFilesNode(TreeItemDTO<? extends FileSystemContentSearchParam> itemData, DataSource dataSource) {
            super(NAME_PREFIX + "_" + getLongString(dataSource.getId()),
                    Bundle.RootFactory_DataSourceFilesNode_displayName(),
                    "org/sleuthkit/autopsy/images/image.png",
                    new DataSourcesChildren(FileSystemFactory.getContentNode(itemData, dataSource))
            );
        }
    }

    /**
     * A children object with just the specified node.
     */
    public static class DataSourcesChildren extends Children.Array {

        /**
         * Main constructor.
         *
         * @param node The node of this children object.
         */
        public DataSourcesChildren(Node node) {
            super(Arrays.asList(node));
        }

    }

    /**
     * Root node for displaying "View" for file types.
     */
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

        /**
         * Main constructor.
         *
         * @param dataSourceObjId The data source object id or null for no
         *                        filter.
         */
        public ViewsRootNode(Long dataSourceObjId) {
            super(NAME_PREFIX + "_" + getLongString(dataSourceObjId),
                    Bundle.RootFactory_ViewsRootNode_displayName(),
                    "org/sleuthkit/autopsy/images/views.png",
                    new ViewsTypeFactory.ViewsChildren(dataSourceObjId));
        }
    }

    /**
     * Root node for "Data Artifacts" in the tree.
     */
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

        /**
         * Main constructor.
         *
         * @param dataSourceObjId The data source object id or null for no
         *                        filter.
         */
        public DataArtifactsRootNode(Long dataSourceObjId) {
            super(NAME_PREFIX + "_" + getLongString(dataSourceObjId),
                    Bundle.RootFactory_DataArtifactsRootNode_displayName(),
                    "org/sleuthkit/autopsy/images/extracted_content.png",
                    new DataArtifactTypeFactory(dataSourceObjId));
        }
    }

    /**
     * Root node for "Analysis Results" in the tree.
     */
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

        /**
         * Main constructor.
         *
         * @param dataSourceObjId The data source object id or null for no
         *                        filter.
         */
        public AnalysisResultsRootNode(Long dataSourceObjId) {
            super(NAME_PREFIX + "_" + getLongString(dataSourceObjId),
                    Bundle.RootFactory_AnalysisResultsRootNode_displayName(),
                    "org/sleuthkit/autopsy/images/analysis_result.png",
                    new AnalysisResultTypeFactory(dataSourceObjId));
        }
    }

    /**
     * Root node for OS accounts in the tree.
     */
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

        /**
         * Main constructor.
         *
         * @param dataSourceObjId The data source object id or null for no
         *                        filter.
         */
        public OsAccountsRootNode(Long dataSourceObjId) {
            super(NAME_PREFIX + "_" + getLongString(dataSourceObjId),
                    Bundle.RootFactory_OsAccountsRootNode_displayName(),
                    "org/sleuthkit/autopsy/images/os-account.png");

            this.dataSourceObjId = dataSourceObjId;
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayOsAccounts(new OsAccountsSearchParams(dataSourceObjId), getNodeSelectionInfo());
        }

    }

    /**
     * Root node for tags in the tree.
     */
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

        /**
         * Main constructor.
         *
         * @param dataSourceObjId The data source object id or null for no
         *                        filter.
         */
        public TagsRootNode(Long dataSourceObjId) {
            super(NAME_PREFIX + "_" + getLongString(dataSourceObjId),
                    Bundle.RootFactory_TagsRootNode_displayName(),
                    "org/sleuthkit/autopsy/images/tag-folder-blue-icon-16.png",
                    new TagNameFactory(dataSourceObjId));
        }
    }

    /**
     * Root node for reports in the tree.
     */
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

        /**
         * Main constructor.
         */
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
