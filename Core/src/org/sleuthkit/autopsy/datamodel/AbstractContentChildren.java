/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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

import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children.Keys;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.datamodel.FileTypes.FileTypesNode;
import org.sleuthkit.autopsy.datamodel.accounts.Accounts;
import org.sleuthkit.autopsy.datamodel.accounts.Accounts.AccountsRootNode;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.LocalDirectory;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.SleuthkitItemVisitor;
import org.sleuthkit.datamodel.SleuthkitVisitableItem;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.Volume;

/**
 * Abstract subclass for ContentChildren and RootContentChildren implementations
 * that handles creating Nodes from Content objects.
 */
abstract class AbstractContentChildren<T> extends Keys<T> {

    private final CreateSleuthkitNodeVisitor createSleuthkitNodeVisitor = new CreateSleuthkitNodeVisitor();
    private final CreateAutopsyNodeVisitor createAutopsyNodeVisitor = new CreateAutopsyNodeVisitor();

    /**
     * Uses lazy Content.Keys
     */
    AbstractContentChildren() {
        /*
         * This was turned off because we were getting out of memory errors when
         * the filter nodes were hiding nodes. Turning this off seemed to help
         */
        super(false); //don't use lazy behavior
    }

    @Override
    protected Node[] createNodes(T key) {
        if (key instanceof SleuthkitVisitableItem) {
            return new Node[]{((SleuthkitVisitableItem) key).accept(createSleuthkitNodeVisitor)};
        } else {
            return new Node[]{((AutopsyVisitableItem) key).accept(createAutopsyNodeVisitor)};
        }
    }

    /**
     * Creates appropriate Node for each sub-class of Content
     */
    public static class CreateSleuthkitNodeVisitor extends SleuthkitItemVisitor.Default<AbstractContentNode<? extends Content>> {

        @Override
        public AbstractContentNode<? extends Content> visit(Directory drctr) {
            return new DirectoryNode(drctr);
        }

        @Override
        public AbstractContentNode<? extends Content> visit(File file) {
            return new FileNode(file);
        }

        @Override
        public AbstractContentNode<? extends Content> visit(Image image) {
            return new ImageNode(image);
        }

        @Override
        public AbstractContentNode<? extends Content> visit(Volume volume) {
            return new VolumeNode(volume);
        }

        @Override
        public AbstractContentNode<? extends Content> visit(LayoutFile lf) {
            return new LayoutFileNode(lf);
        }

        @Override
        public AbstractContentNode<? extends Content> visit(DerivedFile df) {
            return new LocalFileNode(df);
        }

        @Override
        public AbstractContentNode<? extends Content> visit(LocalFile lf) {
            return new LocalFileNode(lf);
        }

        @Override
        public AbstractContentNode<? extends Content> visit(VirtualDirectory ld) {
            return new VirtualDirectoryNode(ld);
        }

        @Override
        public AbstractContentNode<? extends Content> visit(LocalDirectory ld) {
            return new LocalDirectoryNode(ld);
        }

        @Override
        public AbstractContentNode<? extends Content> visit(SlackFile sf) {
            return new SlackFileNode(sf);
        }

        @Override
        public AbstractContentNode<? extends Content> visit(BlackboardArtifact art) {
            return new BlackboardArtifactNode(art);
        }

        @Override
        protected AbstractContentNode<? extends Content> defaultVisit(SleuthkitVisitableItem di) {
            throw new UnsupportedOperationException(NbBundle.getMessage(this.getClass(),
                    "AbstractContentChildren.CreateTSKNodeVisitor.exception.noNodeMsg"));
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
        public ExtractedContent.RootNode visit(ExtractedContent ec) {
            return ec.new RootNode(ec.getSleuthkitCase());
        }

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
            return tagsNodeKey.new RootNode();
        }

        @Override
        public AbstractNode visit(DataSources i) {
            return new DataSourcesNode(i.filteringDataSourceObjId());
        }

        @Override
        public AbstractNode visit(DataSourceGrouping datasourceGrouping) {
            return new DataSourceGroupingNode(datasourceGrouping.getgDataSource());
        }
        
        @Override
        public AbstractNode visit(Views v) {
            return new ViewsNode(v.getSleuthkitCase(), v.filteringDataSourceObjId());
        }

        @Override
        public AbstractNode visit(Results results) {
            return new ResultsNode(results.getSleuthkitCase(), results.filteringDataSourceObjId() );
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
        protected AbstractNode defaultVisit(AutopsyVisitableItem di) {
            throw new UnsupportedOperationException(
                    NbBundle.getMessage(this.getClass(),
                            "AbstractContentChildren.createAutopsyNodeVisitor.exception.noNodeMsg"));
        }

        @Override
        public AbstractNode visit(FileTypesByMimeType ftByMimeTypeItem) {
            return ftByMimeTypeItem.new ByMimeTypeNode();
        }
    }
}
