/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 - 2021 Basis Technology Corp.
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

import org.sleuthkit.autopsy.commonpropertiessearch.CentralRepoCommonAttributeInstanceNode;
import org.sleuthkit.autopsy.commonpropertiessearch.CommonAttributeSearchResultRootNode;
import org.sleuthkit.autopsy.commonpropertiessearch.InstanceCountNode;
import org.sleuthkit.autopsy.commonpropertiessearch.InstanceCaseNode;
import org.sleuthkit.autopsy.commonpropertiessearch.CommonAttributeValueNode;
import org.sleuthkit.autopsy.commonpropertiessearch.CaseDBCommonAttributeInstanceNode;
import org.sleuthkit.autopsy.commonpropertiessearch.InstanceDataSourceNode;
import org.sleuthkit.autopsy.allcasessearch.CorrelationAttributeInstanceNode;

/**
 * Visitor pattern that goes over all nodes in the directory tree. This includes
 * extracted content, reports, and the data sources area.
 */
public interface DisplayableItemNodeVisitor<T> {

    /*
     * Data Sources Area
     */
    T visit(LayoutFileNode lfn);

    T visit(LocalFileNode dfn);

    T visit(VirtualDirectoryNode ldn);

    T visit(LocalDirectoryNode ldn);

    T visit(DirectoryNode dn);

    T visit(FileNode fn);

    T visit(ImageNode in);

    T visit(VolumeNode vn);

    T visit(PoolNode pn);

    T visit(SlackFileNode sfn);


    /*
     * Views Area
     */
    T visit(BlackboardArtifactNode ban);

    T visit(CommonAttributeValueNode cavn);

    T visit(CommonAttributeSearchResultRootNode cfn);

    T visit(CaseDBCommonAttributeInstanceNode fin);

    T visit(CentralRepoCommonAttributeInstanceNode crfin);

    T visit(InstanceCountNode icn);

    T visit(InstanceCaseNode icn);

    T visit(InstanceDataSourceNode icn);

    T visit(CorrelationAttributeInstanceNode cain);

    /*
     * Tags
     */
    T visit(Tags.RootNode node);


    /*
     * Reports
     */
    T visit(EmptyNode.MessageNode emptyNode);

    /*
     * Attachments
     */
    T visit(AttachmentNode node);

    /*
     * Unsupported node
     */
    T visit(UnsupportedContentNode ucn);

    T visit(LocalFilesDataSourceNode lfdsn);
    

    /**
     * Visitor with an implementable default behavior for all types. Override
     * specific visit types to not use the default behavior.
     *
     * @param <T>
     */
    static abstract class Default<T> implements DisplayableItemNodeVisitor<T> {

        /**
         * Default visit for all types
         *
         * @param c
         *
         * @return
         */
        protected abstract T defaultVisit(DisplayableItemNode c);

        @Override
        public T visit(CaseDBCommonAttributeInstanceNode fin) {
            return defaultVisit(fin);
        }

        @Override
        public T visit(CommonAttributeValueNode cavn) {
            return defaultVisit(cavn);
        }

        @Override
        public T visit(CommonAttributeSearchResultRootNode cfn) {
            return defaultVisit(cfn);
        }

        @Override
        public T visit(InstanceCountNode icn) {
            return defaultVisit(icn);
        }

        @Override
        public T visit(InstanceCaseNode icn) {
            return defaultVisit(icn);
        }

        @Override
        public T visit(InstanceDataSourceNode icn) {
            return defaultVisit(icn);
        }

        @Override
        public T visit(CorrelationAttributeInstanceNode cain) {
            return defaultVisit(cain);
        }

        @Override
        public T visit(CentralRepoCommonAttributeInstanceNode crfin) {
            return defaultVisit(crfin);
        }

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
        public T visit(PoolNode pn) {
            return defaultVisit(pn);
        }

        @Override
        public T visit(VolumeNode vn) {
            return defaultVisit(vn);
        }

        @Override
        public T visit(SlackFileNode sfn) {
            return defaultVisit(sfn);
        }

        @Override
        public T visit(BlackboardArtifactNode ban) {
            return defaultVisit(ban);
        }

        @Override
        public T visit(EmptyNode.MessageNode ftByMimeTypeEmptyNode) {
            return defaultVisit(ftByMimeTypeEmptyNode);
        }

        @Override
        public T visit(LayoutFileNode lfn) {
            return defaultVisit(lfn);
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
        public T visit(Tags.RootNode node) {
            return defaultVisit(node);
        }

        @Override
        public T visit(AttachmentNode node) {
            return defaultVisit(node);
        }

        @Override
        public T visit(UnsupportedContentNode node) {
            return defaultVisit(node);
        }

        @Override
        public T visit(LocalFilesDataSourceNode node) {
            return defaultVisit(node);
        }
    }
}
