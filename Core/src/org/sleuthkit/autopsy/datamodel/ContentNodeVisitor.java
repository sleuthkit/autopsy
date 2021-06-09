/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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

import org.sleuthkit.autopsy.datamodel.OsAccounts.OsAccountNode;

/**
 * Visitor Pattern interface that goes over Content nodes in the data source
 * area of the tree.
 *
 * The DisplayableItemNodeVisitor goes over all nodes in the tree.
 *
 * @param <T> visit method return type
 */
interface ContentNodeVisitor<T> {

    T visit(ImageNode in);

    T visit(VirtualDirectoryNode lcn);
    
    T visit(LocalDirectoryNode ldn);

    T visit(VolumeNode vn);
    
    T visit(PoolNode pn);

    T visit(DirectoryNode dn);

    T visit(FileNode fn);

    T visit(LayoutFileNode lcn);

    T visit(LocalFileNode dfn);
    
    T visit(SlackFileNode sfn);
    
    T visit(BlackboardArtifactNode bban);
    
    T visit(UnsupportedContentNode ucn);

    T visit(OsAccountNode bban);
    
    T visit(LocalFilesDataSourceNode lfdsn);

    /**
     * Visitor with an implementable default behavior for all types. Override
     * specific visit types to not use the default behavior.
     *
     * @param <T>
     */
    static abstract class Default<T> implements ContentNodeVisitor<T> {

        /**
         * Default visit for all types
         *
         * @param c
         *
         * @return
         */
        protected abstract T defaultVisit(ContentNode c);

        @Override
        public T visit(DirectoryNode dn) {
            return defaultVisit(dn);
        }

        @Override
        public T visit(FileNode fn) {
            return defaultVisit(fn);
        }

        @Override
        public T visit(ImageNode in) {
            return defaultVisit(in);
        }

        @Override
        public T visit(VolumeNode vn) {
            return defaultVisit(vn);
        }
        
        @Override
        public T visit(PoolNode pn) {
            return defaultVisit(pn);
        }

        @Override
        public T visit(LayoutFileNode lcn) {
            return defaultVisit(lcn);
        }

        @Override
        public T visit(LocalFileNode dfn) {
            return defaultVisit(dfn);
        }

        @Override
        public T visit(VirtualDirectoryNode ldn) {
            return defaultVisit(ldn);
        }

        @Override
        public T visit(LocalDirectoryNode ldn) {
            return defaultVisit(ldn);
        }

        @Override
        public T visit(SlackFileNode sfn) {
            return defaultVisit(sfn);
        }
        
        @Override
        public T visit(BlackboardArtifactNode bban) {
            return defaultVisit(bban);
        }
                
        @Override
        public T visit(UnsupportedContentNode ucn) {
            return defaultVisit(ucn);
        }
        
        @Override
        public T visit(OsAccountNode bban) {
            return defaultVisit(bban);
        }
        
        @Override
        public T visit(LocalFilesDataSourceNode lfdsn) {
            return defaultVisit(lfdsn);
        }
    }
}
