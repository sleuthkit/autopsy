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

import java.util.Observable;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.mainui.nodes.ViewsTypeFactory.FileMimePrefixFactory;

/**
 * Class which contains the Nodes for the 'By Mime Type' view located in the
 * File Types view, shows all files with a mime type. Will initially be empty
 * until file type identification has been performed. Contains a Property Change
 * Listener which is checking for changes in IngestJobEvent Completed or
 * Canceled and IngestModuleEvent Content Changed.
 */
public final class FileTypesByMimeType extends Observable implements AutopsyVisitableItem {

    private final static Logger logger = Logger.getLogger(FileTypesByMimeType.class.getName());
 
    /**
     * Root of the File Types tree. Used to provide single answer to question:
     * Should the child counts be shown next to the nodes?
     */
    private final FileTypes typesRoot;

    FileTypesByMimeType(FileTypes typesRoot) {
        this.typesRoot = typesRoot;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }

    long filteringDataSourceObjId() {
        return typesRoot.filteringDataSourceObjId();
    }

    /**
     * Class which represents the root node of the "By MIME Type" tree, will
     * have children of each media type present in the database or no children
     * when the file detection module has not been run and MIME type is
     * currently unknown.
     */
    public static class ByMimeTypeNode extends DisplayableItemNode {

        @NbBundle.Messages({"FileTypesByMimeType.name.text=By MIME Type"})

        final String NAME = Bundle.FileTypesByMimeType_name_text();
        
        private final long dataSourceId;

        ByMimeTypeNode(long dataSourceId) {
            super(Children.create(new FileMimePrefixFactory(
                    dataSourceId > 0
                    ? dataSourceId
                    : null), true), Lookups.singleton(Bundle.FileTypesByMimeType_name_text()));
            super.setName(NAME);
            super.setDisplayName(NAME);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file_types.png");
            this.dataSourceId = dataSourceId;
        }
        
        public Node clone() {
            return new ByMimeTypeNode(dataSourceId);
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }

        boolean isEmpty() {
            return this.getChildren().getNodesCount(true) <= 0;
        }
    }
}
