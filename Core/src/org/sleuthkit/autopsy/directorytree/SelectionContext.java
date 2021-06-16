/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.directorytree;

import java.util.Objects;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.CasePreferences;
import org.sleuthkit.autopsy.datamodel.DataSourceFilesNode;
import org.sleuthkit.autopsy.datamodel.DataSourcesNode;
import static org.sleuthkit.autopsy.directorytree.Bundle.*;

@NbBundle.Messages({"SelectionContext.dataSources=Data Sources",
    "SelectionContext.dataSourceFiles=Data Source Files",
    "SelectionContext.views=Views"})
enum SelectionContext {
    DATA_SOURCES(SelectionContext_dataSources()),
    VIEWS(SelectionContext_views()),
    OTHER(""), // Subnode of another node.
    DATA_SOURCE_FILES(SelectionContext_dataSourceFiles());

    private final String displayName;

    private SelectionContext(String displayName) {
        this.displayName = displayName;
    }

    public static SelectionContext getContextFromName(String name) {
        if (name.equals(DATA_SOURCES.getName()) || name.equals(DATA_SOURCE_FILES.getName())) {
            return DATA_SOURCES;
        } else if (name.equals(VIEWS.getName())) {
            return VIEWS;
        } else {
            return OTHER;
        }
    }

    private String getName() {
        return displayName;
    }

    /**
     * Get the selection context of a Node in the DirectoryTree.
     *
     * @param n
     *
     * @return
     */
    public static SelectionContext getSelectionContext(Node n) {
        if (n == null || n.getParentNode() == null) {
            // Parent of root node or root node. Occurs during case open / close.
            return SelectionContext.OTHER;
        } else if ((!Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true) && DataSourcesNode.getNameIdentifier().equals(n.getParentNode().getName()))
                || (Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true) && DataSourceFilesNode.getNameIdentifier().equals(n.getParentNode().getName()))) {
            // if group by data type and root is the DataSourcesNode or
            // if group by persons/hosts and parent of DataSourceFilesNode
            // then it is a data source node
            return SelectionContext.DATA_SOURCES;
        } else if (n.getParentNode().getParentNode() == null) {
            // One level below root node. Should be one of DataSources, Views, or Results
            return SelectionContext.getContextFromName(n.getDisplayName());
        } else {
            // In Group by Data Source mode, the node under root is the data source name, and
            // under that is Data Source Files, Views, or Results. Before moving up the tree, check
            // if one of those applies.
            if (n.getParentNode().getParentNode().getParentNode() == null) {
                SelectionContext context = SelectionContext.getContextFromName(n.getDisplayName());
                if (context != SelectionContext.OTHER) {
                    return context;
                }
            }

            return getSelectionContext(n.getParentNode());
        }
    }

}
