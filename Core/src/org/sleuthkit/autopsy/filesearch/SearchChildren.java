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
package org.sleuthkit.autopsy.filesearch;

import java.util.List;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.DirectoryNode;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Makes nodes for search results.
 */
class SearchChildren extends Children.Keys<AbstractFile> {

    SearchChildren(boolean lazy, List<AbstractFile> fileList) {
        super(lazy);
        this.setKeys(fileList);
    }

    @Override
    protected Node[] createNodes(AbstractFile t) {
        Node[] node = new Node[1];
        if (t.isDir()) {
            node[0] = new DataResultFilterNode(new DirectoryNode(t, false), DirectoryTreeTopComponent.findInstance().getExplorerManager());

            //node[0] = new DirectoryNode((Directory)t);
            return node;
        } else {
            node[0] = new DataResultFilterNode(new FileNode(t, false), DirectoryTreeTopComponent.findInstance().getExplorerManager());
            //node[0] = new FileNode((File)t);
            return node;
        }
    }
}
