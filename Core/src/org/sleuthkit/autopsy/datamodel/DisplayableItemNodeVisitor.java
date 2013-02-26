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

import org.sleuthkit.autopsy.datamodel.EmailExtracted.EmailExtractedAccountNode;
import org.sleuthkit.autopsy.datamodel.EmailExtracted.EmailExtractedFolderNode;
import org.sleuthkit.autopsy.datamodel.EmailExtracted.EmailExtractedRootNode;
import org.sleuthkit.autopsy.datamodel.HashsetHits.HashsetHitsRootNode;
import org.sleuthkit.autopsy.datamodel.HashsetHits.HashsetHitsSetNode;
import org.sleuthkit.autopsy.datamodel.KeywordHits.KeywordHitsKeywordNode;
import org.sleuthkit.autopsy.datamodel.KeywordHits.KeywordHitsListNode;
import org.sleuthkit.autopsy.datamodel.KeywordHits.KeywordHitsRootNode;
import org.sleuthkit.autopsy.datamodel.Tags.TagNodeRoot;
import org.sleuthkit.autopsy.datamodel.Tags.TagsNodeRoot;
import org.sleuthkit.autopsy.datamodel.Tags.TagsRootNode;

/**
 * Visitor pattern for DisplayableItemNodes
 */
public interface DisplayableItemNodeVisitor<T> {

    T visit(DirectoryNode dn);
    T visit(FileNode fn);
    T visit(ImageNode in);
    T visit(VolumeNode vn);
    T visit(BlackboardArtifactNode ban);
    T visit(ArtifactTypeNode atn);
    T visit(ExtractedContentNode ecn);
    T visit(FileSearchFilterNode fsfn);
    T visit(SearchFiltersNode sfn);
    T visit(RecentFilesNode rfn);
    T visit(RecentFilesFilterNode rffn);
    T visit(KeywordHitsRootNode khrn);
    T visit(KeywordHitsListNode khsn);
    T visit(KeywordHitsKeywordNode khmln);
    T visit(HashsetHitsRootNode hhrn);
    T visit(HashsetHitsSetNode hhsn);
    T visit(EmailExtractedRootNode eern);
    T visit(EmailExtractedAccountNode eean);
    T visit(EmailExtractedFolderNode eefn);
    T visit(TagsRootNode bksrn);
    T visit(TagsNodeRoot bksrn);
    T visit(TagNodeRoot tnr);
    T visit(ViewsNode vn);
    T visit(ResultsNode rn);
    T visit(ImagesNode in);
    T visit(LayoutFileNode lfn);
    T visit(DerivedFileNode dfn);
    T visit(VirtualDirectoryNode ldn);

    /**
     * Visitor with an implementable default behavior for all types. Override
     * specific visit types to not use the default behavior.
     * @param <T>
     */
    static abstract class Default<T> implements DisplayableItemNodeVisitor<T> {

        /**
         * Default visit for all types
         * @param c
         * @return
         */
        protected abstract T defaultVisit(DisplayableItemNode c);

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
        public T visit(BlackboardArtifactNode ban){
            return defaultVisit(ban);
        }

        @Override
        public T visit(ArtifactTypeNode atn){
            return defaultVisit(atn);
        }

        @Override
        public T visit(ExtractedContentNode ecn){
            return defaultVisit(ecn);
        }

        @Override
        public T visit(FileSearchFilterNode fsfn){
            return defaultVisit(fsfn);
        }

        @Override
        public T visit(SearchFiltersNode sfn){
            return defaultVisit(sfn);
        }
        
        @Override
        public T visit(RecentFilesNode rfn) {
            return defaultVisit(rfn);
        }
        
        @Override
        public T visit(RecentFilesFilterNode rffn) {
            return defaultVisit(rffn);
        }
        
        @Override
        public T visit(KeywordHitsRootNode khrn) {
            return defaultVisit(khrn);
        }
        
        @Override
        public T visit(KeywordHitsListNode khsn) {
            return defaultVisit(khsn);
        }
        
        @Override
        public T visit(KeywordHitsKeywordNode khmln) {
            return defaultVisit(khmln);
        }
        
        @Override
        public T visit(ViewsNode vn) {
            return defaultVisit(vn);
        }
        
        @Override
        public T visit(ResultsNode rn) {
            return defaultVisit(rn);
        }
        
        @Override
        public T visit(ImagesNode in) {
            return defaultVisit(in);
        }
        
        @Override
        public T visit(HashsetHitsRootNode hhrn) {
            return defaultVisit(hhrn);
        }
        
        @Override
        public T visit(HashsetHitsSetNode hhsn) {
            return defaultVisit(hhsn);
        }
        
        @Override
        public T visit(EmailExtractedRootNode eern) {
            return defaultVisit(eern);
        }
        
        @Override
        public T visit(EmailExtractedAccountNode eean) {
            return defaultVisit(eean);
        }
        
        @Override
        public T visit(EmailExtractedFolderNode eefn) {
            return defaultVisit(eefn);
        }
        
        @Override
        public T visit(LayoutFileNode lfn) {
            return defaultVisit(lfn);
        }
        
        @Override
        public T visit(DerivedFileNode dfn) {
            return defaultVisit(dfn);
        }
        
        @Override
        public T visit(VirtualDirectoryNode ldn) {
            return defaultVisit(ldn);
        }
        
        
        @Override
        public T visit(TagsRootNode bksrn) {
            return defaultVisit(bksrn);
        }
        
        @Override
        public T visit(TagsNodeRoot bksnr) {
            return defaultVisit(bksnr);
        }

        @Override
        public T visit(TagNodeRoot tnr) {
            return defaultVisit(tnr);
        }
        
        
    }
}
