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
import java.util.logging.Level;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalDirectory;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.UnsupportedContent;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.Volume;

/**
 * Children implementation for the root node of a ContentNode tree. Accepts a
 * list of root Content objects for the tree.
 */
public class RootContentChildren extends Children.Keys<Object> {
    private static final Logger logger = Logger.getLogger(RootContentChildren.class.getName());
    private final Collection<? extends Object> contentKeys;

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
        Node node = createNode(key);
        if (node != null) {
            return new Node[]{node};
        } else {
            logger.log(Level.WARNING, "AbstractContentChildren.CreateTSKNodeVisitor.exception.noNodeMsg");
            return new Node[0];
        }
    }

    /**
     * Creates a node for one of the known object keys that is not a sleuthkit
     * item.
     *
     * @param key The node key.
     *
     * @return The generated node or null if no match found.
     */
    public static Node createNode(Object key) {
        if (key instanceof Tags) {
            Tags tagsNodeKey = (Tags) key;
            return new Tags.RootNode(tagsNodeKey.filteringDataSourceObjId());
        } else if (key instanceof DataSources) {
            DataSources dataSourcesKey = (DataSources) key;
            return new DataSourceFilesNode(dataSourcesKey.filteringDataSourceObjId());
        } else if (key instanceof DataSourceGrouping) {
            DataSourceGrouping dataSourceGrouping = (DataSourceGrouping) key;
            return new DataSourceGroupingNode(dataSourceGrouping.getDataSource());
        } else if (key instanceof Views) {
            Views v = (Views) key;
            return new ViewsNode(v.filteringDataSourceObjId());
        } else if (key instanceof Reports) {
            return new Reports.ReportsListNode();
        } else if (key instanceof OsAccounts) {
            OsAccounts osAccountsItem = (OsAccounts) key;
            return osAccountsItem.new OsAccountListNode();
        } else if (key instanceof PersonGrouping) {
            PersonGrouping personGrouping = (PersonGrouping) key;
            return new PersonNode(personGrouping.getPerson());
        } else if (key instanceof HostDataSources) {
            HostDataSources hosts = (HostDataSources) key;
            return new HostNode(hosts);
        } else if (key instanceof HostGrouping) {
            HostGrouping hostGrouping = (HostGrouping) key;
            return new HostNode(hostGrouping);
        } else if (key instanceof DataSourcesByType) {
            return new DataSourcesNode();
        } else if (key instanceof AnalysisResults) {
            AnalysisResults analysisResults = (AnalysisResults) key;
            return new AnalysisResults.RootNode(
                    analysisResults.getFilteringDataSourceObjId());
        } else if (key instanceof Directory) {
            Directory drctr = (Directory) key;
            return new DirectoryNode(drctr);
        } else if (key instanceof File) {
            File file = (File) key;
            return new FileNode(file);
        } else if (key instanceof Image) {
            Image image = (Image) key;
            return new ImageNode(image);
        } else if (key instanceof Volume) {
            Volume volume = (Volume) key;
            return new VolumeNode(volume);
        } else if (key instanceof Pool) {
            Pool pool = (Pool) key;
            return new PoolNode(pool);
        } else if (key instanceof LayoutFile) {
            LayoutFile lf = (LayoutFile) key;
            return new LayoutFileNode(lf);
        } else if (key instanceof DerivedFile) {
            DerivedFile df = (DerivedFile) key;
            return new LocalFileNode(df);
        } else if (key instanceof LocalFile) {
            LocalFile lf = (LocalFile) key;
            return new LocalFileNode(lf);
        } else if (key instanceof VirtualDirectory) {
            VirtualDirectory ld = (VirtualDirectory) key;
            return new VirtualDirectoryNode(ld);
        } else if (key instanceof LocalDirectory) {
            LocalDirectory ld = (LocalDirectory) key;
            return new LocalDirectoryNode(ld);
        } else if (key instanceof SlackFile) {
            SlackFile sf = (SlackFile) key;
            return new SlackFileNode(sf);
        } else if (key instanceof BlackboardArtifact) {
            BlackboardArtifact art = (BlackboardArtifact) key;
            return new BlackboardArtifactNode(art);
        } else if (key instanceof UnsupportedContent) {
            UnsupportedContent uc = (UnsupportedContent) key;
            return new UnsupportedContentNode(uc);
        } else if (key instanceof LocalFilesDataSource) {
            LocalFilesDataSource ld = (LocalFilesDataSource) key;
            return new LocalFilesDataSourceNode(ld);
        } else if (key instanceof DataArtifacts) {
            DataArtifacts dataArtifacts = (DataArtifacts) key;
            return new DataArtifacts.RootNode(
                    dataArtifacts.getFilteringDataSourceObjId());
        } else {
            return null;
        }
    }
}
