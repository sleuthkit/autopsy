/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import java.util.Collection;
import java.util.Collections;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.datamodel.accounts.Accounts;
import org.sleuthkit.datamodel.SleuthkitVisitableItem;

/**
 * Children implementation for the root node of a ContentNode tree. Accepts a
 * list of root Content objects for the tree.
 */
public class RootContentChildren extends Children.Keys<Object> {

    private final Collection<? extends Object> contentKeys;
    private final CreateAutopsyNodeVisitor createAutopsyNodeVisitor = new CreateAutopsyNodeVisitor();
    private final CreateSleuthkitNodeVisitor createSleuthkitNodeVisitor = new CreateSleuthkitNodeVisitor();

    /**
     * @param contentKeys root Content objects for the Node tree
     */
    public RootContentChildren(Collection<? extends Object> contentKeys) {
        super();
        this.contentKeys = contentKeys;
    }

    @Override
    protected void addNotify() {
        setKeys(contentKeys);
    }

    @Override
    protected void removeNotify() {
        setKeys(Collections.<Object>emptySet());
    }

    /**
     * Refresh all content keys This creates new nodes of keys have changed.
     *
     * TODO ideally, nodes would respond to event from wrapped content object
     * but we are not ready for this.
     */
    public void refreshContentKeys() {
        contentKeys.forEach(this::refreshKey);
    }

    @Override
    protected Node[] createNodes(Object key) {
        if (key instanceof AutopsyVisitableItem) {
            return new Node[]{((AutopsyVisitableItem) key).accept(createAutopsyNodeVisitor)};
        } else {
            return new Node[]{((SleuthkitVisitableItem) key).accept(createSleuthkitNodeVisitor)};
        }
    }

    /**
     * Gets a DisplayableItemNode for use as a subtree root node for the Autopsy
     * tree view from each type of AutopsyVisitableItem visited. There are
     * AutopsyVisitableItems for the Data Sources, Views, Results, and Reports
     * subtrees, and for the subtrees of Results (e.g., Extracted Content, Hash
     * Set Hits, etc.).
     */
    static class CreateAutopsyNodeVisitor extends AutopsyItemVisitor.Default<AbstractNode> {

        @Override
        public AbstractNode visit(FileTypesByExtension sf) {
            return sf.new FileTypesByExtNode(sf.getSleuthkitCase(), null);
        }

        @Override
        public AbstractNode visit(RecentFiles rf) {
            return new RecentFilesNode(rf.getSleuthkitCase());
        }

        @Override
        public AbstractNode visit(DeletedContent dc) {
            return new DeletedContent.DeletedContentsNode(dc.getSleuthkitCase(), dc.filteringDataSourceObjId());
        }

        @Override
        public AbstractNode visit(FileSize dc) {
            return new FileSize.FileSizeRootNode(dc.getSleuthkitCase(), dc.filteringDataSourceObjId());
        }

        @Override
        public AbstractNode visit(KeywordHits kh) {
            return kh.new RootNode();
        }

        @Override
        public AbstractNode visit(HashsetHits hh) {
            return hh.new RootNode();
        }

        @Override
        public AbstractNode visit(InterestingHits ih) {
            return ih.new RootNode();
        }

        @Override
        public AbstractNode visit(EmailExtracted ee) {
            return ee.new RootNode();
        }

        @Override
        public AbstractNode visit(Tags tagsNodeKey) {
            return tagsNodeKey.new RootNode(tagsNodeKey.filteringDataSourceObjId());
        }

        @Override
        public AbstractNode visit(DataSources i) {
            return new DataSourceFilesNode(i.filteringDataSourceObjId());
        }

        @Override
        public AbstractNode visit(DataSourceGrouping datasourceGrouping) {
            return new DataSourceGroupingNode(datasourceGrouping.getDataSource());
        }

        @Override
        public AbstractNode visit(Views v) {
            return new ViewsNode(v.getSleuthkitCase(), v.filteringDataSourceObjId());
        }

        @Override
        public AbstractNode visit(FileTypes ft) {
            return ft.new FileTypesNode();
        }

        @Override
        public AbstractNode visit(Reports reportsItem) {
            return new Reports.ReportsListNode();
        }

        @Override
        public AbstractNode visit(Accounts accountsItem) {
            return accountsItem.new AccountsRootNode();
        }

        @Override
        public AbstractNode visit(OsAccounts osAccountsItem) {
            return osAccountsItem.new OsAccountListNode();
        }

        @Override
        protected AbstractNode defaultVisit(AutopsyVisitableItem di) {
            throw new UnsupportedOperationException(
                    NbBundle.getMessage(this.getClass(),
                            "AbstractContentChildren.createAutopsyNodeVisitor.exception.noNodeMsg"));
        }

        @Override
        public AbstractNode visit(FileTypesByMimeType ftByMimeTypeItem) {
            return ftByMimeTypeItem.new ByMimeTypeNode();
        }

        @Override
        public AbstractNode visit(PersonGrouping personGrouping) {
            return new PersonNode(personGrouping.getPerson());
        }

        @Override
        public AbstractNode visit(HostDataSources hosts) {
            return new HostNode(hosts);
        }

        @Override
        public AbstractNode visit(HostGrouping hostGrouping) {
            return new HostNode(hostGrouping);
        }

        @Override
        public AbstractNode visit(DataSourcesByType dataSourceHosts) {
            return new DataSourcesNode();
        }

        @Override
        public AbstractNode visit(AnalysisResults analysisResults) {
            return new AnalysisResults.RootNode(
                    analysisResults.getFilteringDataSourceObjId());
        }

        @Override
        public AbstractNode visit(DataArtifacts dataArtifacts) {
            return new DataArtifacts.RootNode(
                    dataArtifacts.getFilteringDataSourceObjId());
        }
    }
}
