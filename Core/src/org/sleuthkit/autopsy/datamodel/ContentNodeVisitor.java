/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

/**
 * Interface for visitor pattern on ContentNodes
 * @param <T> visit method return type
 */
public interface ContentNodeVisitor<T> {

    T visit(DirectoryNode dn);

    T visit(FileNode fn);

    T visit(ImageNode in);

    T visit(VolumeNode vn);
    
    T visit(LayoutFileNode lcn);
    
    T visit(DerivedFileNode dfn);
    
    T visit(VirtualDirectoryNode lcn);

    /**
     * Visitor with an implementable default behavior for all types. Override
     * specific visit types to not use the default behavior.
     * @param <T>
     */
    static abstract class Default<T> implements ContentNodeVisitor<T> {

        /**
         * Default visit for all types
         * @param c
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
        public T visit(LayoutFileNode lcn) {
            return defaultVisit(lcn);
        }
        
        @Override
        public T visit(DerivedFileNode dfn) {
            return defaultVisit(dfn);
        }
        
        @Override
        public T visit(VirtualDirectoryNode ldn) {
            return defaultVisit(ldn);
        }
    }
}
